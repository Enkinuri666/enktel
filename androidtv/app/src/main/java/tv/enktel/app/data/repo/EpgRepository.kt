package tv.enktel.app.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.enktel.app.data.db.AppDatabase
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.xtream.XtreamClient
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
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
            val stream = maybeGunzip(raw, url, resp.header("Content-Type"), resp.header("Content-Encoding"))
            epg.clear(p.id)
            XmltvParser.parse(stream, p.id, wanted, from, to) { batch -> epg.insertAll(batch) }
        }
        epg.prune(now - TimeUnit.DAYS.toMillis(9))
        total
    }

    private fun maybeGunzip(input: InputStream, url: String, contentType: String?, encoding: String?): InputStream {
        val buffered = input.buffered(64 * 1024)
        if (encoding?.contains("gzip", true) == true) return buffered // OkHttp already decoded explicit gzip bodies
        buffered.mark(2)
        val b1 = buffered.read(); val b2 = buffered.read()
        buffered.reset()
        val isGz = b1 == 0x1f && b2 == 0x8b
        return if (isGz || url.endsWith(".gz") && isGz) GZIPInputStream(buffered) else buffered
    }

    // Every read below applies the viewer's timezone correction: the bounds go
    // into the database's frame, the results come back into the viewer's. See
    // EpgShift for why it is done here and not at import, and for why doing
    // only half of it silently drops an hour of guide at the edges.

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
