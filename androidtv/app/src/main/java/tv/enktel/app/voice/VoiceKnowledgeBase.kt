package tv.enktel.app.voice

import kotlinx.coroutines.flow.first
import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.db.Series
import tv.enktel.app.AppGraph

/**
 * Voice-assistant knowledge base.
 *
 * A thin adapter over the app's own DAOs that gives the voice layer one
 * consistent lookup surface for "what do we know about title X".  It does
 * NOT hold its own cache — every method reads live from the DB — so a
 * playlist resync (ContentRepository.refreshAll) or EPG refresh
 * (EpgRepository.refresh) is picked up on the very next voice query
 * without any explicit invalidation.
 *
 * All queries are best-effort and never throw: the caller phrases voice
 * commands like "who is in Dune" without knowing whether that title is in
 * the user's library at all.  A miss becomes `null`; the caller falls
 * back on a friendly "I couldn't find that" spoken reply.
 */
class VoiceKnowledgeBase(private val graph: AppGraph) {

    sealed class Hit(open val name: String, open val poster: String) {
        data class MovieHit(val m: Movie) : Hit(m.name, m.poster)
        data class SeriesHit(val s: Series) : Hit(s.name, s.poster)
        data class ChannelHit(val channelName: String, val channelKey: String, val logo: String)
            : Hit(channelName, logo)
    }

    /**
     * Fuzzy title lookup across the whole catalogue.  Search order:
     * exact-match → prefix match → substring match, movies first because
     * VOD queries are the most common phrasing ("who's in Inception").
     */
    suspend fun findTitle(query: String): Hit? {
        val p = graph.playlists.activeProfile() ?: return null
        val q = query.trim().takeIf { it.isNotEmpty() } ?: return null
        val ql = q.lowercase()
        val movies = runCatching { graph.db.searchDao().searchMoviesDeep(p.id, q) }.getOrDefault(emptyList())
        movies.firstOrNull { it.name.equals(q, ignoreCase = true) }?.let { return Hit.MovieHit(it) }
        val series = runCatching { graph.db.searchDao().searchSeriesDeep(p.id, q) }.getOrDefault(emptyList())
        series.firstOrNull { it.name.equals(q, ignoreCase = true) }?.let { return Hit.SeriesHit(it) }
        movies.firstOrNull { it.name.lowercase().startsWith(ql) }?.let { return Hit.MovieHit(it) }
        series.firstOrNull { it.name.lowercase().startsWith(ql) }?.let { return Hit.SeriesHit(it) }
        movies.firstOrNull()?.let { return Hit.MovieHit(it) }
        series.firstOrNull()?.let { return Hit.SeriesHit(it) }
        // Channel fallback — voice commands like "when is CNN on" are
        // frequent enough to be worth checking.
        val chans = runCatching { graph.content.channels(p.id).first() }.getOrDefault(emptyList())
        chans.firstOrNull { it.name.equals(q, ignoreCase = true) }?.let {
            return Hit.ChannelHit(it.name, it.key, it.logo)
        }
        chans.firstOrNull { ql in it.name.lowercase() }?.let {
            return Hit.ChannelHit(it.name, it.key, it.logo)
        }
        return null
    }

    /** IMDb-style "read me the card" summary sentences suitable for TTS. */
    fun describe(hit: Hit): String = when (hit) {
        is Hit.MovieHit -> buildString {
            append(hit.m.name)
            if (hit.m.year > 0) append(" — ").append(hit.m.year)
            if (hit.m.genre.isNotBlank()) append(". ").append(hit.m.genre)
            if (hit.m.rating > 0) append(". Rated ").append("%.1f".format(hit.m.rating))
            if (hit.m.director.isNotBlank()) append(". Directed by ").append(hit.m.director)
            if (hit.m.cast.isNotBlank()) append(". Starring ").append(hit.m.cast.take(120))
        }
        is Hit.SeriesHit -> buildString {
            append(hit.s.name)
            if (hit.s.year > 0) append(" — ").append(hit.s.year)
            if (hit.s.genre.isNotBlank()) append(". ").append(hit.s.genre)
            if (hit.s.rating > 0) append(". Rated ").append("%.1f".format(hit.s.rating))
            if (hit.s.director.isNotBlank()) append(". Created by ").append(hit.s.director)
            if (hit.s.cast.isNotBlank()) append(". Starring ").append(hit.s.cast.take(120))
            if (hit.s.plot.isNotBlank()) append(". ").append(hit.s.plot.take(200))
        }
        is Hit.ChannelHit -> "Channel ${hit.channelName}"
    }

    fun cast(hit: Hit): String? = when (hit) {
        is Hit.MovieHit -> hit.m.cast.takeIf { it.isNotBlank() }
        is Hit.SeriesHit -> hit.s.cast.takeIf { it.isNotBlank() }
        else -> null
    }
    fun director(hit: Hit): String? = when (hit) {
        is Hit.MovieHit -> hit.m.director.takeIf { it.isNotBlank() }
        is Hit.SeriesHit -> hit.s.director.takeIf { it.isNotBlank() }
        else -> null
    }
    fun year(hit: Hit): Int? = when (hit) {
        is Hit.MovieHit -> hit.m.year.takeIf { it > 0 }
        is Hit.SeriesHit -> hit.s.year.takeIf { it > 0 }
        else -> null
    }
    fun rating(hit: Hit): Double? = when (hit) {
        is Hit.MovieHit -> hit.m.rating.takeIf { it > 0 }
        is Hit.SeriesHit -> hit.s.rating.takeIf { it > 0 }
        else -> null
    }
    fun genre(hit: Hit): String? = when (hit) {
        is Hit.MovieHit -> hit.m.genre.takeIf { it.isNotBlank() }
        is Hit.SeriesHit -> hit.s.genre.takeIf { it.isNotBlank() }
        else -> null
    }
    fun plot(hit: Hit): String? = when (hit) {
        is Hit.SeriesHit -> hit.s.plot.takeIf { it.isNotBlank() }
        else -> null
    }

    /** Route to open this hit in the app's UI. */
    fun route(hit: Hit): String = when (hit) {
        is Hit.MovieHit -> "movie/${hit.m.key}"
        is Hit.SeriesHit -> "seriesDetails/${hit.s.key}"
        is Hit.ChannelHit -> "live?ch=${hit.channelKey}"
    }

    /**
     * Titles similar to [hit], scored by overlap of first genre token + a
     * ±5-year proximity boost.  Kept in-app so it works fully offline and
     * has consistent quality regardless of connectivity.
     */
    suspend fun similar(hit: Hit, limit: Int = 6): List<Hit> {
        val p = graph.playlists.activeProfile() ?: return emptyList()
        val g = genre(hit)?.substringBefore(",")?.trim()?.takeIf { it.length >= 3 } ?: return emptyList()
        val yr = year(hit) ?: 0
        val ms = runCatching { graph.db.searchDao().searchMoviesDeep(p.id, g) }.getOrDefault(emptyList())
        val ss = runCatching { graph.db.searchDao().searchSeriesDeep(p.id, g) }.getOrDefault(emptyList())
        val here: String? = when (hit) {
            is Hit.MovieHit -> hit.m.key
            is Hit.SeriesHit -> hit.s.key
            else -> null
        }
        val hits = (ms.map { Hit.MovieHit(it) } + ss.map { Hit.SeriesHit(it) })
            .filter { h ->
                (h as? Hit.MovieHit)?.m?.key != here && (h as? Hit.SeriesHit)?.s?.key != here
            }
        return hits.sortedByDescending { h ->
            val y = year(h) ?: 0
            val prox = if (yr > 0 && y > 0) (10 - (y - yr).let { if (it < 0) -it else it }).coerceAtLeast(0) else 0
            val ratingBoost = ((rating(h) ?: 0.0) * 2).toInt()
            prox + ratingBoost
        }.take(limit)
    }
}
