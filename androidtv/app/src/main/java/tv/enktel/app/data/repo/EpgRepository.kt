package tv.enktel.app.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.enktel.app.data.net.gunzipIfNeeded
import tv.enktel.app.data.db.AppDatabase
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.xtream.XtreamClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import tv.enktel.app.data.epg.XmltvParser

data class NowNext(val now: EpgProgram?, val next: EpgProgram?)

class EpgRepository(
    private val db: AppDatabase,
    private val xtream: XtreamClient,
    private val http: OkHttpClient,
    /**
     * Settings, for the viewer's EPG timezone correction.
     *
     * Nullable so the repository can still be built without one — the
     * correction then reads as zero, which is what it was before it existed.
     */
    private val settings: tv.enktel.app.data.prefs.SettingsStore? = null,
) {
    private val epg get() = db.epgDao()

    /**
     * The viewer's guide correction, in minutes.
     *
     * Read per call rather than cached, so changing the chip in Settings moves
     * the guide on the next redraw. It is one DataStore read against an
     * in-memory cache, against a query that is about to touch hundreds of rows.
     */
    private suspend fun offsetMin(): Int = settings?.epgOffsetMinNow() ?: 0

    suspend fun refresh(p: Profile): Int = withContext(Dispatchers.IO) {
        val url = when {
            p.kind == "xtream" -> XtreamClient.xmltvUrl(p)
            p.epgUrl.isNotBlank() -> p.epgUrl
            else -> return@withContext 0
        }
        val wanted = db.contentDao().channels(p.id).first()
            .mapNotNull { it.epgId.takeIf { id -> id.isNotBlank() } }
            .toHashSet()

        val now = System.currentTimeMillis()
        val from = now - TimeUnit.DAYS.toMillis(8)
        val to = now + TimeUnit.DAYS.toMillis(8)

        val req = Request.Builder().url(url).build()
        val total = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("EPG returned HTTP ${resp.code}")
            val raw = resp.body.byteStream()
            val stream = gunzipIfNeeded(raw)
            // Clear on the first batch, not before the parse.
            //
            // This used to wipe the guide the moment the response headers
            // looked right, so a panel that 200s and then dies mid-body — or
            // serves gzip that turns out to be an error page — left the
            // viewer with no guide at all until the next successful fetch.
            // That was survivable while the only refresh was a button someone
            // chose to press. It is not now the app refreshes on launch: a
            // flaky panel would blank the guide every time the app opened.
            //
            // Deferring it costs one boolean and means a failed refresh
            // leaves yesterday's guide in place, which is worth a great deal
            // more than an empty one.
            var cleared = false
            XmltvParser.parse(stream, p.id, wanted, from, to) { batch ->
                if (!cleared) {
                    epg.clear(p.id)
                    cleared = true
                }
                epg.insertAll(batch)
            }
        }
        epg.prune(now - TimeUnit.DAYS.toMillis(9))
        total
    }


    // Every read below applies the viewer's timezone correction: the bounds go
    // into the database's frame, the results come back into the viewer's. See
    // EpgShift for why it is done here and not at import, and for why doing
    // only half of it silently drops an hour of guide at the edges.

    /**
     * Snapshot of how well the loaded guide actually covers this line.
     *
     * The Guide screen asks "is my EPG healthy?" in indirect ways —
     * empty rows across channels, stale "Now" on a programme that ended
     * yesterday, no show titles in Catch-Up. The viewer's version of the
     * question is "how many channels on my line carry a programme guide
     * right now, and which ones don't?", and the answer is in the
     * catalogue + epg tables already. This helper is the one place the
     * Guide-side gap-finder is built from, so both the headline count
     * and the list of channel keys without coverage come from the same
     * SQL query rather than from two drifting ones.
     */
    data class Coverage(
        val totalChannels: Int,
        val channelsWithEpg: Int,
        val horizonMs: Long,
        /** Channel keys with blank epgId or no programmes in the active
         *  window. Capped so a 2 000-channel line's diagnostic screen
         *  stays small. */
        val missing: List<MissingChannel>,
    )

    data class MissingChannel(
        val key: String,
        val name: String,
        val epgId: String,
        val categoryName: String,
    )

    suspend fun coverage(profileId: Long, limit: Int = 60): Coverage = withContext(Dispatchers.IO) {
        val channels = db.contentDao().channels(profileId).first()
        val total = channels.size
        val now = System.currentTimeMillis()
        val to = now + TimeUnit.DAYS.toMillis(1)
        // The epg table is indexed by epgId; ask for the distinct ids
        // that have any programme crossing the next 24 h window, then
        // compare against the channels' declared epgIds. A channel with
        // a blank epgId is unambiguously missing. One with an id that
        // doesn't appear in the window is either stale or unmatched by
        // the XMLTV feed.
        val activeIds = channels.asSequence()
            .mapNotNull { it.epgId.takeIf { s -> s.isNotBlank() } }
            .distinct()
            .toList()
        val covered: Set<String> = if (activeIds.isEmpty()) emptySet() else {
            activeIds.chunked(400).flatMap { chunk ->
                epg.windowMany(profileId, chunk, now, to).asSequence()
                    .map { it.epgId }.distinct().toList()
            }.toSet()
        }
        val missing = channels.asSequence()
            .filter { it.epgId.isBlank() || it.epgId !in covered }
            .take(limit)
            .map { ch ->
                MissingChannel(
                    key = ch.key, name = ch.name,
                    epgId = ch.epgId, categoryName = ch.categoryName,
                )
            }
            .toList()
        Coverage(
            totalChannels = total,
            channelsWithEpg = covered.size,
            horizonMs = epg.horizonMs(profileId) ?: 0L,
            missing = missing,
        )
    }

    suspend fun nowNext(profileId: Long, epgId: String): NowNext {
        if (epgId.isBlank()) return NowNext(null, null)
        val off = offsetMin()
        val now = System.currentTimeMillis()
        val list = EpgShift.shift(epg.nowNext(profileId, epgId, EpgShift.toStored(now, off), 2), off)
        val current = list.firstOrNull { it.startMs <= now && it.endMs > now }
        val next = list.firstOrNull { it.startMs > now }
        return NowNext(current, next)
    }

    suspend fun upcoming(profileId: Long, epgId: String, n: Int = 8): List<EpgProgram> {
        if (epgId.isBlank()) return emptyList()
        val off = offsetMin()
        val now = EpgShift.toStored(System.currentTimeMillis(), off)
        return EpgShift.shift(epg.nowNext(profileId, epgId, now, n), off)
    }

    suspend fun window(profileId: Long, epgIds: List<String>, from: Long, to: Long): Map<String, List<EpgProgram>> {
        if (epgIds.isEmpty()) return emptyMap()
        val off = offsetMin()
        val f = EpgShift.toStored(from, off)
        val t = EpgShift.toStored(to, off)
        return epgIds.chunked(400).flatMap { chunk -> epg.windowMany(profileId, chunk, f, t) }
            .let { EpgShift.shift(it, off) }
            .groupBy { it.epgId }
    }

    suspend fun archive(profileId: Long, epgId: String, daysBack: Int): List<EpgProgram> {
        if (epgId.isBlank()) return emptyList()
        val off = offsetMin()
        val now = EpgShift.toStored(System.currentTimeMillis(), off)
        val from = now - TimeUnit.DAYS.toMillis(daysBack.toLong().coerceAtLeast(1))
        return EpgShift.shift(epg.archive(profileId, epgId, from, now), off)
    }

    suspend fun hasData(profileId: Long) = epg.count(profileId) > 0
}
