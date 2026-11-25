package tv.enktel.app.data.hub

import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.metadata.TitleSanitizer
import tv.enktel.app.data.repo.ContentRepository
import tv.enktel.app.data.repo.EpgRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Media Hub's data: what is on Live TV right now, each programme bridged to
 * the matching on-demand title in the viewer's own catalogue.
 *
 * This is the "ingest → match → enrich" loop, run on the device against local
 * data:
 *
 *  1. **Ingest** — read the now/next window for a set of channels from the EPG
 *     the app already syncs.
 *  2. **Match** — clean each programme title and find the best VOD / series row
 *     in the catalogue with [LiveVodMatch].
 *  3. **Enrich** — a matched row already carries the metadata the rest of the
 *     app enriched into it (poster, backdrop, genres, cast, TMDb id), so the
 *     card shows real art and the detail screen's "More Like This" works for
 *     free. An unmatched programme falls back to the channel's own logo rather
 *     than a wrong guess.
 *
 * No network call happens here: enrichment of the catalogue is MetadataEnrichmentWorker's
 * job and has already run. The hub only joins linear and on-demand together.
 */
class MediaHubRepository(
    private val content: ContentRepository,
    private val epg: EpgRepository,
) {
    /** One Live-TV programme, with its on-demand twin when the line carries it. */
    data class HubItem(
        val channel: Channel,
        val now: EpgProgram?,
        val next: EpgProgram?,
        /** Non-null when a VOD/series row matched — what "Watch on demand" opens. */
        val match: Match?,
    ) {
        val hasVod: Boolean get() = match != null
    }

    data class Match(
        /** `movie/<key>` or `seriesDetails/<key>` route target. */
        val key: String,
        val isSeries: Boolean,
        val title: String,
        val poster: String,
        val backdrop: String,
        val year: Int,
        val rating: Double,
        val confidence: Double,
    )

    /**
     * Build the hub for up to [limit] channels, favourites first.
     *
     * [channels] is passed in (rather than queried here) so the screen controls
     * which lineup it shows — favourites, a category, or the head of the list.
     */
    suspend fun onNow(
        profileId: Long,
        channels: List<Channel>,
        limit: Int = 24,
    ): List<HubItem> = withContext(Dispatchers.Default) {
        val lineup = channels.filter { it.epgId.isNotBlank() }.take(limit)
        if (lineup.isEmpty()) return@withContext emptyList()

        val now = System.currentTimeMillis()
        val windows = epg.window(
            profileId,
            lineup.map { it.epgId }.distinct(),
            now - 2 * 3_600_000L,
            now + 4 * 3_600_000L,
        )

        // One match per distinct cleaned title, reused across channels — a +1
        // timeshift channel and its main feed show the same programme, and the
        // catalogue search is the expensive part.
        val matchCache = HashMap<String, Match?>()

        lineup.map { ch ->
            val progs = windows[ch.epgId].orEmpty().sortedBy { it.startMs }
            val nowProg = progs.firstOrNull { now in it.startMs..it.endMs }
            val nextProg = progs.firstOrNull { it.startMs > now }
            val match = nowProg?.let { p ->
                val cleaned = TitleSanitizer.cleanProgramme(p.title).lowercase()
                if (cleaned.isBlank()) null
                else matchCache.getOrPut(cleaned) { matchProgramme(profileId, p) }
            }
            HubItem(channel = ch, now = nowProg, next = nextProg, match = match)
        }
    }

    /** Find the best catalogue row for one programme, or null. */
    /** The best on-demand twin for one programme, or null. Public so the EPG
     *  dock can enrich the highlighted programme the same way the hub does. */
    suspend fun match(profileId: Long, prog: EpgProgram): Match? = withContext(Dispatchers.Default) { matchProgramme(profileId, prog) }

    private suspend fun matchProgramme(profileId: Long, prog: EpgProgram): Match? {
        val cleaned = TitleSanitizer.clean(prog.title).trim()
        if (cleaned.length < 2) return null
        // A live listing with episode structure (S..E.. or "Episode") is a
        // series; otherwise treat it as a one-off (film / event).
        val isSeries = Regex("""s\d{1,2}\s*e\d{1,3}|episode|\bep\s*\d+""", RegexOption.IGNORE_CASE)
            .containsMatchIn(prog.title)

        // Query the catalogue with the longest title token, which is the most
        // selective and keeps the LIKE from returning the whole library.
        val probe = cleaned.split(Regex("\\s+")).maxByOrNull { it.length } ?: cleaned
        val year = yearOf(prog)

        val series = content.searchSeries(profileId, probe).map {
            LiveVodMatch.Candidate("series:${it.key}", it.name, it.year, isSeries = true) to it
        }
        val movies = content.searchMovies(profileId, probe).map {
            LiveVodMatch.Candidate("movie:${it.key}", it.name, it.year, isSeries = false) to it
        }
        val byId = (series + movies).associateBy { it.first.key }
        val best = LiveVodMatch.best(
            liveTitle = prog.title, liveYear = year, liveIsSeries = isSeries,
            candidates = byId.values.map { it.first },
        ) ?: return null

        return when (val row = byId[best.candidate.key]?.second) {
            is tv.enktel.app.data.db.Series -> Match(
                key = "seriesDetails/${row.key}", isSeries = true, title = row.name,
                poster = row.poster, backdrop = row.backdrop, year = row.year,
                rating = row.rating, confidence = best.score,
            )
            is tv.enktel.app.data.db.Movie -> Match(
                key = "movie/${row.key}", isSeries = false, title = row.name,
                poster = row.poster, backdrop = row.backdrop, year = row.year,
                rating = row.rating, confidence = best.score,
            )
            else -> null
        }
    }

    /** Programme year from an explicit tag, else 0 — the EPG rarely carries one. */
    private fun yearOf(prog: EpgProgram): Int =
        Regex("""\b(19|20)\d{2}\b""").find(prog.title)?.value?.toIntOrNull() ?: 0
}
