package tv.enktel.app.ui.vod

import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.enktel.app.AppGraph
import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.db.Series
import tv.enktel.app.data.repo.MovieDetails
import tv.enktel.app.data.repo.SeriesDetails
import tv.enktel.app.data.xtream.XtreamClient
import tv.enktel.app.ui.components.Badge
import tv.enktel.app.ui.components.CenterMessage
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.KeyValue
import tv.enktel.app.ui.components.PosterCard
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.vodPlayerRoute
import tv.enktel.app.ui.components.tvRailFocus

// Lint false-positive: produceState's vararg-keys overload isn't recognized by the
// ProduceStateDoesNotAssignValue detector even though every producer below assigns `value`.
@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun MovieDetailsScreen(graph: AppGraph, nav: NavHostController, key: String) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val movie by produceState<Movie?>(initialValue = null, key) { value = graph.content.movie(key) }
    val m = movie ?: return
    val details by produceState<MovieDetails?>(initialValue = null, m.key) {
        if (p.kind == "xtream") value = runCatching { graph.content.movieDetails(p, m.streamId) }.getOrNull()
    }
    val scope = rememberCoroutineScope()
    var resumeMs by remember { mutableLongStateOf(0L) }
    val progressKey = "${p.id}:vod:${m.streamId}"
    androidx.compose.runtime.LaunchedEffect(progressKey) {
        resumeMs = graph.content.progress(progressKey)?.positionMs ?: 0L
    }
    val url = graph.content.vodUrl(p, m)

    // The panel's backdrop when it has one, otherwise the one enrichment
    // stored from TMDB. `details` is a live fetch that only runs for Xtream
    // profiles and only lands if the panel answers, so on an M3U line — or any
    // time the panel is slow or down — the hero image was simply absent and
    // the page opened on flat background. The enriched copy is already in the
    // local row, so it costs nothing and is always there.
    val hero = details?.backdrop?.takeIf { it.isNotBlank() } ?: m.backdrop
    Box(Modifier.fillMaxSize()) {
        if (hero.isNotBlank()) {
            AsyncImage(
                model = hero, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.25f,
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, EnktelBg))))
        }
        // v1.28.1 — phone portrait was hard-wired to Row(220 dp poster +
        // 32 dp gap + 48 dp side padding), leaving ~62 dp for the metadata
        // column on a 410 dp handset — so "Documentary" wrapped one letter
        // per line and the title stacked "The / Age / of / Disclo / sure".
        // On narrow mobile we now stack the poster above the metadata; TV
        // and tablet/landscape keep the side-by-side Row.
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        val narrow = tv.enktel.app.BuildConfig.FLAVOR == "mobile" && cfg.screenWidthDp < 600
        val outerPad = if (narrow) 20.dp else 48.dp
        if (narrow) Column(Modifier.fillMaxSize().padding(outerPad).verticalScroll(rememberScrollState())) {
            Box(Modifier.width(180.dp).height(260.dp).align(Alignment.CenterHorizontally).clip(RoundedCornerShape(12.dp)).background(EnktelSurfaceHigh)) {
                if (m.poster.isNotBlank()) {
                    AsyncImage(model = m.poster, contentDescription = m.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(20.dp))
            MovieDetailsBody(
                graph = graph, nav = nav, p = p, m = m, details = details,
                url = url, progressKey = progressKey, resumeMs = resumeMs,
            )
        } else Row(Modifier.fillMaxSize().padding(outerPad)) {
            Box(Modifier.width(220.dp).height(320.dp).clip(RoundedCornerShape(12.dp)).background(EnktelSurfaceHigh)) {
                if (m.poster.isNotBlank()) {
                    AsyncImage(model = m.poster, contentDescription = m.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(32.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                MovieDetailsBody(
                    graph = graph, nav = nav, p = p, m = m, details = details,
                    url = url, progressKey = progressKey, resumeMs = resumeMs,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MovieDetailsBody(
    graph: AppGraph,
    nav: NavHostController,
    p: Profile,
    m: tv.enktel.app.data.db.Movie,
    details: tv.enktel.app.data.repo.MovieDetails?,
    url: String,
    progressKey: String,
    resumeMs: Long,
) {
    Text(m.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (m.rating > 0) Badge("★ ${"%.1f".format(m.rating)}")
        if (details?.genre?.isNotBlank() == true) Badge(details.genre.take(30))
        // Same fallback as the hero image: TMDB's runtime is in the row
        // already, so a panel that does not report a duration no longer means
        // the badge disappears.
        val mins = ((details?.durationSecs ?: 0L) / 60).toInt().takeIf { it > 0 } ?: m.runtimeMins
        if (mins > 0) Badge("$mins min")
    }
    Spacer(Modifier.height(14.dp))
    var trailerKey by remember { mutableStateOf<String?>(null) }
    // Through the repository, not a bare TmdbClient: the repository falls back
    // to enktel.tv's server-side key, so the button appears without the user
    // having pasted a TMDB key into Settings first. It also resolves by title
    // when the panel published no TMDB id, which is most of the catalogue —
    // the old `if (m.tmdbId > 0)` guard is why the button was usually absent.
    // The rest of the uploads TMDB knows about, so the player has somewhere to
    // go when the first one turns out to be embed-disabled.
    var trailerAlts by remember { mutableStateOf<List<String>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(m.tmdbId, m.name) {
        val keys = runCatching {
            graph.trailers.trailerKeys(m.tmdbId, m.name, isSeries = false)
        }.getOrDefault(emptyList())
        trailerKey = keys.firstOrNull()
            ?: runCatching {
                graph.trailers.trailerKey(m.tmdbId, m.name, isSeries = false)
            }.getOrNull()
        trailerAlts = keys.drop(1)
    }
    // FlowRow so long action lists (Play + Resume + Trailer + Fav + Watchlist +
    // Download) wrap to a second row on narrow phones instead of clipping.
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FocusButton("▶ Play", accent = true, onClick = {
            nav.navigate(vodPlayerRoute(url, m.name, progressKey, poster = m.poster))
        })
        if (resumeMs > 60_000) {
            FocusButton("Resume ${resumeMs / 60000}m", onClick = {
                nav.navigate(vodPlayerRoute(url, m.name, progressKey, poster = m.poster))
            })
        }
        trailerKey?.let { key ->
            FocusButton("🎬 Trailer", onClick = {
                // In-app, not an ACTION_VIEW at the YouTube app. A sideloaded
                // Fire TV Stick has neither YouTube nor a browser installed, so
                // both intents threw, both throws were swallowed by runCatching,
                // and the button did nothing at all — no error, no trailer.
                nav.navigate(
                    "trailer?key=$key&title=${tv.enktel.app.encode(m.name)}" +
                        "&alts=${trailerAlts.joinToString(",")}",
                )
            })
        }
        FavButton(graph, p.id, "vod", m.streamId)
        WatchlistButton(graph, p.id, "vod", m.streamId, m.name, m.poster)
        DownloadButton(
            graph = graph,
            id = "${p.id}:movie:${m.streamId}",
            profileId = p.id,
            kind = "movie",
            refId = m.streamId,
            title = m.name,
            poster = m.poster,
            sourceUrl = url,
        )
        tv.enktel.app.ui.components.ShareButton(
            tv.enktel.app.DeepLink.Target.Movie(m.streamId, m.name)
        )
    }
    Spacer(Modifier.height(18.dp))
    val plot = details?.plot?.takeIf { it.isNotBlank() } ?: m.plot
    if (plot.isNotBlank()) {
        Text(plot, color = Color.White.copy(0.9f), fontSize = 14.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(16.dp))
    }
    if (details != null) {
        if (details.cast.isNotBlank()) KeyValue("Cast", details.cast.take(160))
        if (details.director.isNotBlank()) KeyValue("Director", details.director)
        if (details.releaseDate.isNotBlank()) KeyValue("Released", details.releaseDate)
    }

    // Somewhere to go. The page used to describe a title and stop: whoever had
    // decided against it had only Back, and whoever liked it had no way to
    // find its neighbours — while the app knew exactly what else was on the
    // line and never said.
    val allMovies by remember(p.id) { graph.content.movies(p.id) }
        .collectAsStateWithLifecycle(initialValue = emptyList<Movie>())
    val similar = remember(m.key, allMovies) {
        tv.enktel.app.data.repo.SimilarTitles.rank(
            seed = tv.enktel.app.data.repo.SimilarTitles.of(m),
            pool = allMovies,
            facets = { tv.enktel.app.data.repo.SimilarTitles.of(it) },
        )
    }
    SimilarRail(
        titles = similar,
        poster = { it.poster },
        label = { it.name },
        subtitle = { if (it.year > 0) it.year.toString() else "" },
        onOpen = { nav.navigate("movie/${it.key}") },
    )
}

/**
 * "More like this", for either kind of title.
 *
 * Drawn only when there is something to draw — a heading over an empty row
 * reads as a fault, and a title with nothing in common with anything is a
 * perfectly ordinary thing for a catalogue to contain.
 */
@Composable
private fun <T> SimilarRail(
    titles: List<T>,
    poster: (T) -> String,
    label: (T) -> String,
    subtitle: (T) -> String,
    onOpen: (T) -> Unit,
) {
    if (titles.isEmpty()) return
    Spacer(Modifier.height(24.dp))
    Text(
        "MORE LIKE THIS",
        color = EnktelTextDim, fontSize = 12.sp, fontWeight = FontWeight.Black,
        letterSpacing = 1.4.sp,
    )
    Spacer(Modifier.height(10.dp))
    LazyRow(
        modifier = Modifier.tvRailFocus(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(titles.size) { i ->
            val t = titles[i]
            PosterCard(
                title = label(t),
                imageUrl = poster(t),
                subtitle = subtitle(t),
                onClick = { onOpen(t) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun WatchlistButton(graph: AppGraph, profileId: Long, kind: String, refId: Long, name: String, poster: String) {
    val saved by graph.watchlist.isSavedFlow(profileId, kind, refId).collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    FocusButton(if (saved) "✓ In Watchlist" else "＋ Watchlist", onClick = {
        scope.launch { graph.watchlist.toggle(profileId, kind, refId, name, poster) }
    })
}

@Composable
fun FavButton(graph: AppGraph, profileId: Long, kind: String, refId: Long) {
    val fav by graph.content.isFavoriteFlow(profileId, kind, refId)
        .collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    FocusButton(if (fav) "★ Favorited" else "☆ Favorite", onClick = {
        scope.launch { graph.content.toggleFavorite(profileId, kind, refId) }
    })
}

/**
 * Download / offline-status button. Reads liveness from the downloads DAO so
 * the label reflects state changes as the queue progresses ("⬇ Download" →
 * "⏳ Queued" → "✓ Saved") without a manual refresh.
 */
@Composable
fun DownloadButton(
    graph: AppGraph,
    id: String,
    profileId: Long,
    kind: String, // "movie" | "episode"
    refId: Long,
    title: String,
    poster: String,
    sourceUrl: String,
    seriesKey: String = "",
    seriesName: String = "",
    season: Int = 0,
    episode: Int = 0,
) {
    val exists by graph.db.downloadDao().existsFlow(id).collectAsStateWithLifecycle(initialValue = false)
    val done by graph.db.downloadDao().completedFlow(id).collectAsStateWithLifecycle(initialValue = false)
    val label = when {
        done -> "✓ Saved offline"
        exists -> "⏳ Downloading…"
        else -> "⬇ Download"
    }
    FocusButton(label, onClick = {
        if (done) return@FocusButton
        if (exists) {
            graph.downloads.cancel(id)
        } else {
            graph.downloads.enqueue(
                tv.enktel.app.data.db.DownloadEntry(
                    id = id,
                    profileId = profileId,
                    kind = kind,
                    refId = refId,
                    seriesKey = seriesKey,
                    seriesName = seriesName,
                    season = season,
                    episode = episode,
                    title = title,
                    poster = poster,
                    sourceUrl = sourceUrl,
                )
            )
        }
    })
}

@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun SeriesDetailsScreen(graph: AppGraph, nav: NavHostController, key: String) {
    val profile by produceState<Profile?>(initialValue = null) { value = graph.playlists.activeProfile() }
    val p = profile ?: return
    val series by produceState<Series?>(initialValue = null, key) { value = graph.content.oneSeries(key) }
    val s = series ?: return
    val details by produceState<SeriesDetails?>(initialValue = null, s.key) {
        value = runCatching { graph.content.seriesDetails(p, s.seriesId) }.getOrNull()
    }
    var season by remember { mutableIntStateOf(-1) }
    val seasons = details?.seasons ?: emptyMap()
    if (season == -1 && seasons.isNotEmpty()) season = seasons.keys.first()

    // Other series on this line that resemble this one. The catalogue itself,
    // not a recommendation service — a rail of titles the playlist does not
    // carry would be an advert for somebody else's library.
    val allSeries by remember(p.id) { graph.content.series(p.id) }
        .collectAsStateWithLifecycle(initialValue = emptyList<Series>())
    val similarSeries = remember(s.key, allSeries) {
        tv.enktel.app.data.repo.SimilarTitles.rank(
            seed = tv.enktel.app.data.repo.SimilarTitles.of(s),
            pool = allSeries,
            facets = { tv.enktel.app.data.repo.SimilarTitles.of(it) },
        )
    }

    // v1.28.1 — narrower horizontal padding on phones so the metadata column
    // isn't squeezed to ~180 dp with 48 dp side gutters.
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val narrow = tv.enktel.app.BuildConfig.FLAVOR == "mobile" && cfg.screenWidthDp < 600
    val hPad = if (narrow) 16.dp else 48.dp
    val posterW = if (narrow) 90.dp else 110.dp
    val posterH = if (narrow) 130.dp else 160.dp
    val gap = if (narrow) 14.dp else 24.dp
    Box(Modifier.fillMaxSize()) {
    if (s.backdrop.isNotBlank()) {
        // Series never had a hero image at all — only films did, and only when
        // the panel supplied one. This is the enriched TMDB backdrop, so it is
        // there for every series TMDB knows.
        AsyncImage(
            model = s.backdrop, contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.25f,
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, EnktelBg))))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = hPad, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(posterW).height(posterH).clip(RoundedCornerShape(12.dp)).background(EnktelSurfaceHigh)) {
                if (s.poster.isNotBlank()) {
                    AsyncImage(model = s.poster, contentDescription = s.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(gap))
            Column(Modifier.weight(1f)) {
                Text(s.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.rating > 0) Badge("★ ${"%.1f".format(s.rating)}")
                    if (s.genre.isNotBlank()) Badge(s.genre.take(30))
                    Badge("${seasons.size} season${if (seasons.size == 1) "" else "s"}")
                }
                Spacer(Modifier.height(8.dp))
                val plot = details?.plot?.ifBlank { s.plot } ?: s.plot
                if (plot.isNotBlank()) {
                    Text(plot, color = Color.White.copy(0.85f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                // Series had no trailer button at all — only films did, for no
                // reason other than nobody having added one.
                var seriesTrailer by remember { mutableStateOf<String?>(null) }
                var seriesAlts by remember { mutableStateOf<List<String>>(emptyList()) }
                androidx.compose.runtime.LaunchedEffect(s.tmdbId, s.name) {
                    val keys = runCatching {
                        graph.trailers.trailerKeys(s.tmdbId, s.name, isSeries = true)
                    }.getOrDefault(emptyList())
                    seriesTrailer = keys.firstOrNull()
                        ?: runCatching {
                            graph.trailers.trailerKey(s.tmdbId, s.name, isSeries = true)
                        }.getOrNull()
                    seriesAlts = keys.drop(1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    seriesTrailer?.let { key ->
                        FocusButton("🎬 Trailer", onClick = {
                            nav.navigate(
                                "trailer?key=$key&title=${tv.enktel.app.encode(s.name)}" +
                                    "&alts=${seriesAlts.joinToString(",")}",
                            )
                        })
                    }
                    FavButton(graph, p.id, "series", s.seriesId)
                    WatchlistButton(graph, p.id, "series", s.seriesId, s.name, s.poster)
                    tv.enktel.app.ui.components.ShareButton(
                        tv.enktel.app.DeepLink.Target.Series(s.seriesId, s.name)
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (details == null) {
            CenterMessage("Loading episodes…")
            return
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tv.enktel.app.ui.components.ChipRowLabel("Season")
            Spacer(Modifier.weight(1f))
            FocusButton("⬇ Season", onClick = {
                val entries = seasons[season].orEmpty().map { ep ->
                    tv.enktel.app.data.db.DownloadEntry(
                        id = "${p.id}:episode:${ep.id}",
                        profileId = p.id,
                        kind = "episode",
                        refId = ep.id,
                        seriesKey = s.key,
                        seriesName = s.name,
                        season = ep.season,
                        episode = ep.episode,
                        title = "${s.name} S${ep.season}E${ep.episode} · ${ep.title}",
                        poster = s.poster,
                        sourceUrl = XtreamClient.episodeUrl(p, ep.id, ep.ext),
                    )
                }
                graph.downloads.enqueueMany(entries)
            })
            FocusButton("⬇ All episodes", accent = true, onClick = {
                val entries = seasons.values.flatten().map { ep ->
                    tv.enktel.app.data.db.DownloadEntry(
                        id = "${p.id}:episode:${ep.id}",
                        profileId = p.id,
                        kind = "episode",
                        refId = ep.id,
                        seriesKey = s.key,
                        seriesName = s.name,
                        season = ep.season,
                        episode = ep.episode,
                        title = "${s.name} S${ep.season}E${ep.episode} · ${ep.title}",
                        poster = s.poster,
                        sourceUrl = XtreamClient.episodeUrl(p, ep.id, ep.ext),
                    )
                }
                graph.downloads.enqueueMany(entries)
            })
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(modifier = Modifier.tvRailFocus(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons.keys.toList()) { sn ->
                tv.enktel.app.ui.components.GlassChip(
                    "S$sn", selected = sn == season,
                    onClick = { season = sn },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons[season].orEmpty(), key = { it.id }) { ep ->
                val playEpisode = {
                    val url = XtreamClient.episodeUrl(p, ep.id, ep.ext)
                    val pk = "${p.id}:episode:${ep.id}"
                    // Seed the first roll-over while we still have the season
                    // map, so it needs no network. Every hop after that the
                    // player resolves for itself from the series id below —
                    // a route cannot usefully nest another route twice.
                    val next = tv.enktel.app.data.repo.NextEpisode.after(seasons, ep.id)
                    val nextRoute = next?.let {
                        vodPlayerRoute(
                            XtreamClient.episodeUrl(p, it.id, it.ext),
                            tv.enktel.app.data.repo.NextEpisode.title(s.name, it),
                            "${p.id}:episode:${it.id}",
                            poster = s.poster,
                            seriesId = s.seriesId,
                            episodeId = it.id,
                            seriesName = s.name,
                        )
                    }.orEmpty()
                    val nextLabel = next?.let {
                        tv.enktel.app.data.repo.NextEpisode.label(it)
                    }.orEmpty()
                    nav.navigate(
                        vodPlayerRoute(
                            url, tv.enktel.app.data.repo.NextEpisode.title(s.name, ep), pk,
                            nextRoute = nextRoute, nextLabel = nextLabel, poster = s.poster,
                            seriesId = s.seriesId, episodeId = ep.id, seriesName = s.name,
                        ),
                    )
                }
                androidx.tv.material3.Surface(
                    onClick = playEpisode,
                    modifier = Modifier.fillMaxWidth().tapClick(playEpisode),
                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                        containerColor = EnktelSurfaceHigh.copy(0.5f),
                        focusedContainerColor = tv.enktel.app.ui.theme.EnktelBlue,
                        focusedContentColor = Color.White,
                        contentColor = Color.White,
                    ),
                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "E${ep.episode}",
                            color = tv.enktel.app.ui.theme.EnktelBlue, fontWeight = FontWeight.Black, fontSize = 14.sp,
                            modifier = Modifier.width(48.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(ep.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (ep.plot.isNotBlank()) {
                                Text(ep.plot, fontSize = 11.sp, color = EnktelTextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (ep.durationSecs > 0) {
                            Spacer(Modifier.width(10.dp))
                            Text("${ep.durationSecs / 60} min", fontSize = 12.sp, color = EnktelTextDim)
                        }
                        Spacer(Modifier.width(10.dp))
                        DownloadButton(
                            graph = graph,
                            id = "${p.id}:episode:${ep.id}",
                            profileId = p.id,
                            kind = "episode",
                            refId = ep.id,
                            title = "${s.name} S${ep.season}E${ep.episode} · ${ep.title}",
                            poster = s.poster,
                            sourceUrl = XtreamClient.episodeUrl(p, ep.id, ep.ext),
                            seriesKey = s.key,
                            seriesName = s.name,
                            season = ep.season,
                            episode = ep.episode,
                        )
                    }
                }
            }
            // Inside the list rather than under it: the episode list already
            // owns the remaining height, so anything placed after it would be
            // squeezed to nothing. As the last item it simply scrolls into
            // view once the season has been read through, which is also when
            // somebody is most likely to want the next thing to watch.
            item {
                SimilarRail(
                    titles = similarSeries,
                    poster = { it.poster },
                    label = { it.name },
                    subtitle = { if (it.year > 0) it.year.toString() else "" },
                    onOpen = { nav.navigate("seriesDetails/${'$'}{it.key}") },
                )
            }
        }
    }
    }
}
