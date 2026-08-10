package tv.enktel.app.data.repo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.enktel.app.data.arr
import tv.enktel.app.data.bool
import tv.enktel.app.data.db.AppDatabase
import tv.enktel.app.data.db.Category
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.Favorite
import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.db.MovieFts
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.db.Series
import tv.enktel.app.data.db.SeriesFts
import tv.enktel.app.data.db.WatchProgress
import tv.enktel.app.data.double
import tv.enktel.app.data.get
import tv.enktel.app.data.int
import tv.enktel.app.data.long
import tv.enktel.app.data.m3u.M3uParser
import tv.enktel.app.data.str
import tv.enktel.app.data.xtream.XtreamClient
import java.io.IOException

data class EpisodeInfo(
    val id: Long,
    val season: Int,
    val episode: Int,
    val title: String,
    val ext: String,
    val plot: String,
    val durationSecs: Long,
    val poster: String,
)

data class SeriesDetails(
    val plot: String,
    val cast: String,
    val director: String,
    val genre: String,
    val backdrop: String,
    val seasons: Map<Int, List<EpisodeInfo>>,
)

data class MovieDetails(
    val plot: String,
    val cast: String,
    val director: String,
    val genre: String,
    val releaseDate: String,
    val durationSecs: Long,
    val backdrop: String,
    val trailer: String,
)

class ContentRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val xtream: XtreamClient,
    private val http: OkHttpClient,
) {
    private val content get() = db.contentDao()
    private val searchIndex get() = db.searchDao()

    /**
     * Rebuilds the full-text index for [profileId] from what is now in the
     * catalogue tables.
     *
     * Called at the end of a sync, which is the only moment the catalogue can
     * change wholesale — so the index cannot drift the way an external-content
     * FTS table would without triggers Room does not generate.
     *
     * The body is everything worth matching on, flattened. FTS4 tokenises it,
     * which is what lets "bat man" find "The Batman": the old LIKE query
     * compared the whole phrase and returned nothing for any two-word search
     * that was not a literal substring.
     */
    private suspend fun rebuildSearchIndex(profileId: Long) {
        searchIndex.clearMovieIndex(profileId)
        searchIndex.clearSeriesIndex(profileId)
        content.movies(profileId).first().chunked(500).forEach { chunk ->
            searchIndex.indexMovies(
                chunk.map { m ->
                    MovieFts(
                        rowId = 0L,
                        itemKey = m.key,
                        profileId = profileId,
                        body = listOf(
                            m.name, m.genre, m.cast, m.director, m.studios, m.tags,
                            if (m.year > 0) m.year.toString() else "",
                        ).filter { it.isNotBlank() }.joinToString(" "),
                    )
                },
            )
        }
        content.series(profileId).first().chunked(500).forEach { chunk ->
            searchIndex.indexSeries(
                chunk.map { sRow ->
                    SeriesFts(
                        rowId = 0L,
                        itemKey = sRow.key,
                        profileId = profileId,
                        body = listOf(
                            sRow.name, sRow.genre, sRow.cast, sRow.director, sRow.studios,
                            sRow.tags, sRow.plot,
                            if (sRow.year > 0) sRow.year.toString() else "",
                        ).filter { it.isNotBlank() }.joinToString(" "),
                    )
                },
            )
        }
    }
    private val user get() = db.userDao()

    fun channels(profileId: Long) = content.channels(profileId)
    fun channelsIn(profileId: Long, categoryId: String) = content.channelsIn(profileId, categoryId)
    fun favoriteChannels(profileId: Long) = content.favoriteChannels(profileId)
    fun categories(profileId: Long, kind: String) = content.categories(profileId, kind)
    fun movies(profileId: Long) = content.movies(profileId)
    fun moviesIn(profileId: Long, categoryId: String) = content.moviesIn(profileId, categoryId)
    fun recentMovies(profileId: Long, n: Int = 20) = content.recentMovies(profileId, n)
    fun favoriteMovies(profileId: Long) = content.favoriteMovies(profileId)
    fun series(profileId: Long) = content.series(profileId)
    fun seriesIn(profileId: Long, categoryId: String) = content.seriesIn(profileId, categoryId)
    fun continueWatching(profileId: Long, n: Int = 20) = user.continueWatching(profileId, n)

    /** Unfiltered watch history — see [tv.enktel.app.data.db.UserDao.watchHistory]. */
    fun watchHistory(profileId: Long, n: Int = 30) = user.watchHistory(profileId, n)

    suspend fun channel(key: String) = content.channel(key)
    suspend fun channelByNum(profileId: Long, num: Int) = content.channelByNum(profileId, num)
    suspend fun movie(key: String) = content.movie(key)
    suspend fun oneSeries(key: String) = content.oneSeries(key)
    suspend fun searchChannels(profileId: Long, q: String) = content.searchChannels(profileId, q)
    suspend fun searchMovies(profileId: Long, q: String) = content.searchMovies(profileId, q)
    suspend fun searchSeries(profileId: Long, q: String) = content.searchSeries(profileId, q)

    /**
     * Deep catalogue search — FTS4 where the index exists, LIKE where it does
     * not yet.
     *
     * The fallback is not belt-and-braces: the index is built by a catalogue
     * sync, so between installing an upgrade and the next sync the FTS tables
     * are empty. Without it, search would silently return nothing for every
     * query — the exact silent-empty failure this codebase keeps having to fix.
     */
    suspend fun searchDeep(profileId: Long, q: String): Pair<List<Movie>, List<Series>> =
        withContext(Dispatchers.IO) {
            val dao = db.searchDao()
            val match = FtsQuery.toMatch(q)
            val indexed = match != null && dao.movieIndexSize(profileId) > 0
            if (indexed) {
                // MATCH throws on malformed syntax rather than returning
                // nothing, so a stray quote in a search box would crash the
                // screen. FtsQuery.toMatch is what stops that, and this catch
                // is the second line in case a token still upsets SQLite.
                val hit = runCatching {
                    dao.searchMoviesFts(profileId, match!!) to dao.searchSeriesFts(profileId, match)
                }.getOrNull()
                if (hit != null) return@withContext hit
            }
            dao.searchMoviesDeep(profileId, q) to dao.searchSeriesDeep(profileId, q)
        }

    // ---- v1.20.0 metadata + themed rails ----------------------------------

    /** Advanced filter for the Movies screen — any blank field is a wildcard. */
    suspend fun moviesFiltered(profileId: Long, genre: String = "", year: Int = 0, studio: String = "") =
        content.moviesFiltered(profileId, genre, year, studio)

    /** Advanced filter for the Series screen — any blank field is a wildcard. */
    suspend fun seriesFiltered(profileId: Long, genre: String = "", year: Int = 0, studio: String = "") =
        content.seriesFiltered(profileId, genre, year, studio)

    /** Distinct facet values for populating filter chip rows. */
    suspend fun movieGenres(profileId: Long) = content.distinctMovieGenres(profileId)
    suspend fun movieYears(profileId: Long) = content.distinctMovieYears(profileId)

    /** Themed-rail keyword search. Runs one DB query per keyword and dedupes
     *  by primary key — cheaper than a single big OR-chain LIKE (SQLite
     *  can't use an index on OR-chains of LIKE) and still fast because the
     *  keyword list is short. */
    suspend fun moviesMatchingKeywords(
        profileId: Long, keywords: List<String>, limit: Int,
    ): List<Movie> = withContext(Dispatchers.IO) {
        val bag = LinkedHashMap<String, Movie>()
        keywords.forEach { kw ->
            content.moviesTagged(profileId, "%${kw.lowercase()}%").forEach { if (it.key !in bag) bag[it.key] = it }
            if (bag.size >= limit) return@forEach
        }
        bag.values.take(limit).toList()
    }

    suspend fun seriesMatchingKeywords(
        profileId: Long, keywords: List<String>, limit: Int,
    ): List<tv.enktel.app.data.db.Series> = withContext(Dispatchers.IO) {
        val bag = LinkedHashMap<String, tv.enktel.app.data.db.Series>()
        keywords.forEach { kw ->
            content.seriesTagged(profileId, "%${kw.lowercase()}%").forEach { if (it.key !in bag) bag[it.key] = it }
            if (bag.size >= limit) return@forEach
        }
        bag.values.take(limit).toList()
    }

    suspend fun moviesDocsMatchingKeywords(
        profileId: Long, keywords: List<String>, limit: Int,
    ): List<Movie> = withContext(Dispatchers.IO) {
        val bag = LinkedHashMap<String, Movie>()
        keywords.forEach { kw ->
            content.moviesTaggedDocs(profileId, "%${kw.lowercase()}%").forEach { if (it.key !in bag) bag[it.key] = it }
            if (bag.size >= limit) return@forEach
        }
        bag.values.take(limit).toList()
    }

    fun isFavoriteFlow(profileId: Long, kind: String, refId: Long) =
        user.isFavoriteFlow("$profileId:$kind:$refId")

    suspend fun toggleFavorite(profileId: Long, kind: String, refId: Long) {
        val key = "$profileId:$kind:$refId"
        if (user.isFavorite(key)) user.removeFavorite(key)
        else user.addFavorite(Favorite(key = key, profileId = profileId, kind = kind, refId = refId))
    }

    suspend fun saveProgress(p: WatchProgress) = user.saveProgress(p)
    suspend fun progress(key: String) = user.progress(key)
    suspend fun clearProgress(key: String) = user.clearProgress(key)
    /** See UserDao.backfillProgressArtwork. Cheap, idempotent, safe to re-run. */
    suspend fun backfillProgressArtwork(profileId: Long) = user.backfillProgressArtwork(profileId)

    /** Full catalogue sync for a profile. Returns human-readable summary. */
    suspend fun refreshAll(p: Profile): String = withContext(Dispatchers.IO) {
        if (p.kind == "xtream") refreshXtream(p) else refreshM3u(p)
    }

    private suspend fun refreshXtream(p: Profile): String {
        val liveCats = xtream.liveCategories(p).arr().orEmpty()
        val vodCats = xtream.vodCategories(p).arr().orEmpty()
        val seriesCats = xtream.seriesCategories(p).arr().orEmpty()

        val catNames = HashMap<String, String>()
        val categories = ArrayList<Category>()
        fun mapCats(list: List<kotlinx.serialization.json.JsonElement>, kind: String) {
            list.forEachIndexed { i, e ->
                val id = e.str("category_id") ?: return@forEachIndexed
                val name = e.str("category_name") ?: "Category $id"
                catNames["$kind:$id"] = name
                categories += Category(key = "${p.id}:$kind:$id", profileId = p.id, kind = kind, categoryId = id, name = name, sortIdx = i)
            }
        }
        mapCats(liveCats, "live"); mapCats(vodCats, "vod"); mapCats(seriesCats, "series")

        val live = xtream.liveStreams(p).arr().orEmpty().mapIndexedNotNull { i, e ->
            val sid = e.long("stream_id") ?: return@mapIndexedNotNull null
            val catId = e.str("category_id").orEmpty()
            Channel(
                key = "${p.id}:$sid", profileId = p.id, streamId = sid,
                name = tv.enktel.app.data.metadata.TitleSanitizer.clean(e.str("name") ?: "Channel $sid"),
                num = e.int("num") ?: (i + 1),
                logo = e.str("stream_icon").orEmpty(),
                categoryId = catId,
                categoryName = catNames["live:$catId"].orEmpty(),
                epgId = e.str("epg_channel_id").orEmpty(),
                hasArchive = e.bool("tv_archive") || (e.int("tv_archive") ?: 0) > 0,
                archiveDays = e.int("tv_archive_duration") ?: 0,
                sortIdx = i,
                // Xtream reports audio-only entries through the same
                // get_live_streams call, distinguished only by stream_type.
                isRadio = e.str("stream_type")?.contains("radio", true) == true,
            )
        }

        val movies = xtream.vodStreams(p).arr().orEmpty().mapNotNull { e ->
            val sid = e.long("stream_id") ?: return@mapNotNull null
            Movie(
                key = "${p.id}:$sid", profileId = p.id, streamId = sid,
                name = tv.enktel.app.data.metadata.TitleSanitizer.clean(e.str("name") ?: "Movie $sid"),
                poster = e.str("stream_icon").orEmpty(),
                categoryId = e.str("category_id").orEmpty(),
                rating = e.double("rating") ?: 0.0,
                ext = e.str("container_extension") ?: "mp4",
                addedAt = e.long("added") ?: 0,
                genre = e.str("genre").orEmpty(),
                year = extractYear(e.str("year") ?: e.str("releasedate") ?: e.str("release_date"), e.str("name")),
                cast = e.str("cast").orEmpty(),
                director = e.str("director").orEmpty(),
            )
        }

        val seriesList = xtream.seriesList(p).arr().orEmpty().mapNotNull { e ->
            val sid = e.long("series_id") ?: return@mapNotNull null
            Series(
                key = "${p.id}:$sid", profileId = p.id, seriesId = sid,
                name = tv.enktel.app.data.metadata.TitleSanitizer.clean(e.str("name") ?: "Series $sid"),
                poster = e.str("cover").orEmpty(),
                categoryId = e.str("category_id").orEmpty(),
                rating = e.double("rating") ?: 0.0,
                plot = e.str("plot").orEmpty(),
                genre = e.str("genre").orEmpty(),
                year = extractYear(e.str("year") ?: e.str("releaseDate") ?: e.str("release_date"), e.str("name")),
                cast = e.str("cast").orEmpty(),
                director = e.str("director").orEmpty(),
            )
        }

        // Diff against the catalogue we are about to replace, so "new" means
        // new *to this line* rather than whatever date the provider stamped on
        // ingest. Read before the clear, obviously.
        val prevMovies = content.movies(p.id).first().associate { it.key to it.firstSeenAt }
        val prevSeries = content.series(p.id).first().associate { it.key to it.firstSeenAt }
        val movieStamps = FreshCatalogue.stamp(movies.map { it.key }, prevMovies)
        val seriesStamps = FreshCatalogue.stamp(seriesList.map { it.key }, prevSeries)
        val stampedMovies = movies.mapIndexed { i, m -> m.copy(firstSeenAt = movieStamps[i]) }
        val stampedSeries = seriesList.mapIndexed { i, s -> s.copy(firstSeenAt = seriesStamps[i]) }

        content.clearCategories(p.id); content.clearChannels(p.id)
        content.clearMovies(p.id); content.clearSeries(p.id)
        content.upsertCategories(categories)
        live.chunked(500).forEach { content.upsertChannels(it) }
        stampedMovies.chunked(500).forEach { content.upsertMovies(it) }
        stampedSeries.chunked(500).forEach { content.upsertSeries(it) }
        // The catalogue has just been replaced wholesale, which is the one
        // moment the search index can be rebuilt without risk of drift.
        rebuildSearchIndex(p.id)
        // Kick off the TMDB enrichment worker so the themed home rails
        // repopulate as the metadata streams in. Worker no-ops when the
        // user hasn't supplied a TMDB API key, so calling it is always safe.
        tv.enktel.app.data.metadata.MetadataEnrichmentWorker.enqueueFor(context, p.id)
        return "${live.size} channels · ${movies.size} movies · ${seriesList.size} series"
    }

    private suspend fun refreshM3u(p: Profile): String {
        val req = Request.Builder().url(p.m3uUrl)
            // Ask upstream for gzip; also handle URLs ending in .gz manually below.
            .header("Accept-Encoding", "gzip")
            .build()
        val playlist = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Playlist returned HTTP ${resp.code}")
            val raw = resp.body.byteStream()
            val stream = maybeGunzipStream(raw, p.m3uUrl, resp.header("Content-Encoding"))
            stream.reader(Charsets.UTF_8).buffered().use { M3uParser.parse(it) }
        }

        val groups = LinkedHashMap<String, String>() // name -> kind
        val channels = ArrayList<Channel>()
        val movies = ArrayList<Movie>()
        var liveIdx = 0
        playlist.entries.forEachIndexed { i, e ->
            if (e.isVod) {
                if (!groups.containsKey(e.group)) groups[e.group] = "vod" // minSdk 23: putIfAbsent needs API 24
                movies += Movie(
                    key = "${p.id}:${i + 1}", profileId = p.id, streamId = (i + 1).toLong(),
                    name = e.name, poster = e.logo, categoryId = e.group, url = e.url,
                    ext = e.url.substringAfterLast('.', "mp4").take(5),
                )
            } else {
                if (!groups.containsKey(e.group)) groups[e.group] = "live" // minSdk 23: putIfAbsent needs API 24
                liveIdx++
                channels += Channel(
                    key = "${p.id}:${i + 1}", profileId = p.id, streamId = (i + 1).toLong(),
                    name = e.name, num = e.chno, logo = e.logo,
                    categoryId = e.group, categoryName = e.group,
                    epgId = e.tvgId, url = e.url,
                    hasArchive = e.catchupDays > 0, archiveDays = e.catchupDays,
                    catchupType = e.catchupType, catchupSource = e.catchupSource,
                    sortIdx = liveIdx,
                    isRadio = e.isRadio, userAgent = e.userAgent,
                )
            }
        }
        val categories = groups.entries.mapIndexed { i, (name, kind) ->
            Category(key = "${p.id}:$kind:$name", profileId = p.id, kind = kind, categoryId = name, name = name, sortIdx = i)
        }

        // M3U row keys are positional, so one insertion at the top of a
        // playlist shifts every key below it. Identity has to come from the
        // title, or a re-sync would report the entire library as new.
        val prevM3u = content.movies(p.id).first()
            .associate { FreshCatalogue.titleId(it.name) to it.firstSeenAt }
        val m3uStamps = FreshCatalogue.stamp(movies.map { FreshCatalogue.titleId(it.name) }, prevM3u)
        val stampedM3u = movies.mapIndexed { i, m -> m.copy(firstSeenAt = m3uStamps[i]) }

        content.clearCategories(p.id); content.clearChannels(p.id); content.clearMovies(p.id)
        content.upsertCategories(categories)
        channels.chunked(500).forEach { content.upsertChannels(it) }
        stampedM3u.chunked(500).forEach { content.upsertMovies(it) }
        rebuildSearchIndex(p.id)

        if (p.epgUrl.isBlank() && playlist.epgUrl.isNotBlank()) {
            db.profileDao().update(p.copy(epgUrl = playlist.epgUrl))
        }
        return "${channels.size} channels · ${movies.size} movies"
    }

    suspend fun seriesDetails(p: Profile, seriesId: Long): SeriesDetails = withContext(Dispatchers.IO) {
        val json = xtream.seriesInfo(p, seriesId)
        val info = json.get("info")
        val episodesNode = json.get("episodes")
        val seasons = sortedMapOf<Int, List<EpisodeInfo>>()

        fun parseEpisodes(list: List<kotlinx.serialization.json.JsonElement>) {
            val bySeason = list.mapNotNull { e ->
                val id = e.long("id") ?: return@mapNotNull null
                EpisodeInfo(
                    id = id,
                    season = e.int("season") ?: 1,
                    episode = e.int("episode_num") ?: 0,
                    title = e.str("title") ?: "Episode",
                    ext = e.str("container_extension") ?: "mp4",
                    plot = e.get("info").str("plot").orEmpty(),
                    durationSecs = e.get("info").long("duration_secs") ?: 0,
                    poster = e.get("info").str("movie_image").orEmpty(),
                )
            }.groupBy { it.season }
            bySeason.forEach { (s, eps) -> seasons[s] = eps.sortedBy { it.episode } }
        }

        when (episodesNode) {
            is kotlinx.serialization.json.JsonObject ->
                episodesNode.values.forEach { v -> v.arr()?.let { parseEpisodes(it) } }
            is kotlinx.serialization.json.JsonArray ->
                episodesNode.forEach { v -> v.arr()?.let { parseEpisodes(it) } ?: parseEpisodes(listOf(v)) }
            else -> {}
        }

        SeriesDetails(
            plot = info.str("plot").orEmpty(),
            cast = info.str("cast").orEmpty(),
            director = info.str("director").orEmpty(),
            genre = info.str("genre").orEmpty(),
            backdrop = info.get("backdrop_path").arr()?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty(),
            seasons = seasons,
        )
    }

    suspend fun movieDetails(p: Profile, vodId: Long): MovieDetails = withContext(Dispatchers.IO) {
        val json = xtream.vodInfo(p, vodId)
        val info = json.get("info")
        MovieDetails(
            plot = info.str("plot") ?: info.str("description").orEmpty(),
            cast = info.str("cast") ?: info.str("actors").orEmpty(),
            director = info.str("director").orEmpty(),
            genre = info.str("genre").orEmpty(),
            releaseDate = info.str("releasedate") ?: info.str("release_date").orEmpty(),
            durationSecs = info.long("duration_secs") ?: 0,
            backdrop = info.get("backdrop_path").arr()?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty(),
            trailer = info.str("youtube_trailer").orEmpty(),
        )
    }

    /** Sniff first two bytes for gzip magic number; supports .m3u.gz / gzipped M3Us that
     *  don't declare Content-Encoding. OkHttp transparently ungzips only when it sees the
     *  Accept-Encoding request header + Content-Encoding response header, so we cover the
     *  edge cases here. */
    private fun maybeGunzipStream(input: java.io.InputStream, url: String, encoding: String?): java.io.InputStream {
        val buffered = input.buffered(64 * 1024)
        if (encoding?.contains("gzip", true) == true) return buffered
        buffered.mark(2)
        val b1 = buffered.read(); val b2 = buffered.read()
        buffered.reset()
        val looksGz = b1 == 0x1f && b2 == 0x8b
        return if (looksGz || url.lowercase().endsWith(".gz")) java.util.zip.GZIPInputStream(buffered) else buffered
    }

    companion object {
        private val YEAR_IN_NAME = Regex("""\((\d{4})\)""")

        /** Pull a release year out of panel fields, or a "(2021)" suffix in the title. */
        fun extractYear(raw: String?, name: String?): Int {
            raw?.take(4)?.toIntOrNull()?.let { if (it in 1900..2100) return it }
            name?.let { n -> YEAR_IN_NAME.find(n)?.groupValues?.get(1)?.toIntOrNull()?.let { if (it in 1900..2100) return it } }
            return 0
        }

        /** Normalize a messy panel genre string into clean tags. */
        fun splitGenres(genre: String): List<String> =
            genre.split(',', '/', '|', ';')
                .map { it.trim().replaceFirstChar(Char::uppercase) }
                .filter { it.length in 2..24 }
    }

    /** Resolve the playable URL for a channel with the preferred container. */
    fun liveUrl(p: Profile, ch: Channel, format: String): String =
        if (p.kind == "m3u") ch.url else XtreamClient.liveUrl(p, ch.streamId, hls = format != "ts")

    fun vodUrl(p: Profile, m: Movie): String =
        if (p.kind == "m3u") m.url else XtreamClient.vodUrl(p, m.streamId, m.ext)
}
