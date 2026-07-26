package tv.enktel.app.data.metadata

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import tv.enktel.app.EnktelApp
import tv.enktel.app.data.xtream.XtreamClient
import tv.enktel.app.data.get
import tv.enktel.app.data.long
import tv.enktel.app.data.str
import java.util.concurrent.TimeUnit

/**
 * Background enrichment pass that walks movies + series from the local DB
 * whose `enrichedAt` is stale (or zero), calls Xtream's `get_vod_info` /
 * `get_series_info` to extract each item's `tmdb_id`, then pings TMDB
 * for the full metadata blob and stashes it via
 * [tv.enktel.app.data.db.ContentDao.enrichMovie].
 *
 * Rate-limiting: TMDB's free tier is 40 requests / 10 seconds, so we sleep
 * 300 ms between HTTP calls (~33 req/s upper bound after Xtream fetch).
 * No-op when the user hasn't supplied a TMDB API key (setting blank) —
 * the worker returns SUCCESS immediately so the sync chain doesn't fail.
 *
 * Schedule with [enqueueFor]; the WorkManager unique name means duplicate
 * requests coalesce, so re-triggering after a sync doesn't stack workers.
 */
class MetadataEnrichmentWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.applicationContext as? EnktelApp
            ?: return Result.success()
        val graph = app.graph
        val apiKey = graph.settings.tmdbApiKey.first()
        if (apiKey.isBlank()) return Result.success() // opt-in feature

        val profileId = inputData.getLong(KEY_PROFILE_ID, graph.settings.activeProfileIdNow())
        if (profileId <= 0) return Result.success()
        val profile = graph.playlists.byId(profileId) ?: return Result.success()
        if (profile.kind != "xtream") return Result.success() // Xtream only

        val client = TmdbClient(graph.http, apiKey)
        val dao = graph.db.contentDao()
        val xtream = graph.xtream
        val staleBefore = System.currentTimeMillis() - MAX_AGE_MS

        val movies = dao.moviesNeedingEnrichment(profileId, staleBefore, MOVIES_PER_RUN)
        for (m in movies) {
            val tmdbId = lookupTmdbId(xtream, profile, "vod", m.streamId) ?: continue
            val e = client.movie(tmdbId) ?: continue
            val tags = computeTags(m.name, e.genres, e.keywords)
            val cleanName = TitleSanitizer.clean(m.name)
            dao.enrichMovie(
                key = m.key,
                tmdbId = e.tmdbId,
                studios = e.studios.joinToString(","),
                tags = tags,
                genre = e.genres.joinToString(", ").ifBlank { m.genre },
                year = if (e.releaseYear > 0) e.releaseYear else m.year,
                cast = e.cast.joinToString(", ").ifBlank { m.cast },
                director = e.directors.joinToString(", ").ifBlank { m.director },
                now = System.currentTimeMillis(),
            )
            // Also normalise the title if the sanitizer produced a
            // materially cleaner form; skip if unchanged so we don't
            // churn the Movie row on every run.
            if (cleanName != m.name) dao.upsertMovies(listOf(m.copy(name = cleanName)))
            delay(RATE_LIMIT_DELAY_MS)
            if (isStopped) return Result.success()
        }

        val series = dao.seriesNeedingEnrichment(profileId, staleBefore, SERIES_PER_RUN)
        for (s in series) {
            val tmdbId = lookupTmdbId(xtream, profile, "series", s.seriesId) ?: continue
            val e = client.series(tmdbId) ?: continue
            val tags = computeTags(s.name, e.genres, e.keywords)
            val cleanName = TitleSanitizer.clean(s.name)
            dao.enrichSeries(
                key = s.key,
                tmdbId = e.tmdbId,
                studios = e.studios.joinToString(","),
                tags = tags,
                genre = e.genres.joinToString(", ").ifBlank { s.genre },
                year = if (e.releaseYear > 0) e.releaseYear else s.year,
                cast = e.cast.joinToString(", ").ifBlank { s.cast },
                director = e.directors.joinToString(", ").ifBlank { s.director },
                now = System.currentTimeMillis(),
            )
            if (cleanName != s.name) dao.upsertSeries(listOf(s.copy(name = cleanName)))
            delay(RATE_LIMIT_DELAY_MS)
            if (isStopped) return Result.success()
        }
        return Result.success()
    }

    private suspend fun lookupTmdbId(
        xtream: XtreamClient,
        profile: tv.enktel.app.data.db.Profile,
        kind: String,
        id: Long,
    ): Long? = runCatching {
        val info = when (kind) {
            "vod" -> xtream.vodInfo(profile, id).get("info")
            else -> xtream.seriesInfo(profile, id).get("info")
        }
        // Xtream sometimes returns tmdb / tmdb_id / imdb — try all.
        info.long("tmdb_id") ?: info.long("tmdb") ?: info.str("tmdb")?.toLongOrNull()
    }.getOrNull()

    private fun computeTags(title: String, genres: List<String>, keywords: List<String>): String {
        val tokens = buildList {
            addAll(TitleSanitizer.keywords(title))
            addAll(genres.map { it.lowercase() })
            addAll(keywords.map { it.lowercase() })
        }.distinct()
        val hits = UfoKeywords.matched(tokens)
        // Store the full keyword set (bounded) plus explicit UFO tag hits so
        // both the themed rails and general search can query the tags column.
        return (hits + keywords.map { it.lowercase() }).distinct().take(30).joinToString(",")
    }

    companion object {
        const val WORK_NAME = "enktel_metadata_enrichment"
        private const val KEY_PROFILE_ID = "profile_id"
        private const val MOVIES_PER_RUN = 50
        private const val SERIES_PER_RUN = 30
        // Re-enrich anything older than 60 days so long-lived catalogues
        // still absorb TMDB updates (release date corrections, new keywords).
        private const val MAX_AGE_MS = 60L * 24 * 3600 * 1000
        private const val RATE_LIMIT_DELAY_MS = 300L

        fun enqueueFor(context: Context, profileId: Long) {
            val req = OneTimeWorkRequestBuilder<MetadataEnrichmentWorker>()
                .setInputData(Data.Builder().putLong(KEY_PROFILE_ID, profileId).build())
                .setInitialDelay(3, TimeUnit.SECONDS) // let the sync finish committing rows first
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
