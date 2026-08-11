package tv.enktel.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.enktel.app.AppGraph
import tv.enktel.app.R
import tv.enktel.app.data.TimeFormat
import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.db.Profile
import tv.enktel.app.ui.components.ContentRail
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.PosterCard
import tv.enktel.app.ui.components.tapClick
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.vodPlayerRoute

// Lint false-positive: produceState's vararg-keys overload isn't recognized by the
// ProduceStateDoesNotAssignValue detector even though every producer below assigns `value`.
@Suppress("ProduceStateDoesNotAssignValue")
@Composable
fun HomeScreen(graph: AppGraph, nav: NavHostController) {
    val profile by produceState<Profile?>(initialValue = null) {
        value = graph.playlists.activeProfile()
    }
    val p = profile ?: return

    val continueWatching by graph.content.continueWatching(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    // Resume points written before the player carried artwork have no poster,
    // and this is the screen that shows them. Films can be matched back to the
    // catalogue; the rail redraws itself when the UPDATE lands, because the
    // query above is a Flow. See UserDao.backfillProgressArtwork.
    LaunchedEffect(p.id) { graph.content.backfillProgressArtwork(p.id) }
    val favChannels by graph.content.favoriteChannels(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val recentMovies by graph.content.recentMovies(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val favMovies by graph.content.favoriteMovies(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val allChannels by graph.content.channels(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val recordings by graph.db.recordingDao().all().collectAsStateWithLifecycle(initialValue = emptyList())
    val recentRecordings = remember(recordings) {
        recordings.filter { it.status == "DONE" && it.filePath.isNotBlank() }.sortedByDescending { it.startMs }.take(10)
    }

    val watchlist by graph.watchlist.all(p.id).collectAsStateWithLifecycle(initialValue = emptyList())
    // v1.25.0 — one aggregated computation with cross-rail dedup so a
    // single title no longer shows up in five rails at once. See
    // RecommendationsRepository.homeRails().
    var rails by remember { mutableStateOf<tv.enktel.app.data.repo.RecommendationsRepository.HomeRails?>(null) }
    val today = remember { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) }
    LaunchedEffect(p.id, today) {
        try {
            rails = graph.recommendations.homeRails(p.id)
        } catch (_: Throwable) {}
    }
    val becauseYouWatched = rails?.becauseYouWatched ?: emptyList()
    val trending = rails?.trending ?: emptyList()
    val topPicks = rails?.topPicks ?: emptyList()
    val newThisWeek = rails?.newThisWeek ?: emptyList()
    val justAdded = rails?.justAdded ?: emptyList()
    val latestReleases = rails?.latestReleases ?: emptyList()
    val moodGritty = rails?.moodGritty ?: emptyList()
    val moodLateNight = rails?.moodLateNight ?: emptyList()
    val moodFastPaced = rails?.moodFastPaced ?: emptyList()
    val moodMindBending = rails?.moodMindBending ?: emptyList()
    val moodFeelGood = rails?.moodFeelGood ?: emptyList()
    val topRated = rails?.topRated ?: emptyList()
    val documentaries = rails?.documentaries ?: emptyList()
    val newSeries = rails?.newSeries ?: emptyList()

    // If the profile has never finished its first sync (e.g. onboarding was interrupted),
    // kick off content + EPG sync in the background. Only key on p.id — depending on
    // allChannels.size caused the LaunchedEffect to restart mid-sync, which cancelled the
    // EPG download and (because runCatching also swallows CancellationException) left an
    // "EPG failed: The coroutine scope left the composition" banner stuck on-screen forever.
    var syncing by remember(p.id) { mutableStateOf(false) }
    var syncStatus by remember(p.id) { mutableStateOf("") }
    var syncTriggered by remember(p.id) { mutableStateOf(false) }
    LaunchedEffect(p.id) {
        if (syncTriggered || p.lastSync != 0L) return@LaunchedEffect
        syncTriggered = true
        syncing = true
        syncStatus = "Downloading your playlist…"
        try {
            runCatching { graph.feed.invalidate() }
            val summary = graph.content.refreshAll(p)
            syncStatus = "Downloading TV guide… ($summary)"
        } catch (ce: kotlinx.coroutines.CancellationException) { throw ce
        } catch (e: Exception) { syncStatus = "Sync failed: ${e.message ?: "unknown"}" }
        try {
            graph.epg.refresh(p)
            syncStatus = "Ready"
        } catch (ce: kotlinx.coroutines.CancellationException) { throw ce
        } catch (e: Exception) { syncStatus = "EPG failed: ${e.message ?: "unknown"}" }
        runCatching { graph.playlists.markSynced(p) }
        kotlinx.coroutines.delay(1600)
        syncing = false
        syncStatus = ""
    }

    val heroItems = remember(favMovies, recentMovies) {
        (favMovies + recentMovies).distinctBy { it.key }.filter { it.poster.isNotBlank() }.take(5)
    }
    // Titles already given prime placement in the hero should not turn up again
    // three rows further down. The rails dedupe against each other, but the hero
    // is built separately, so nothing used to stop it repeating itself.
    val heroKeys = remember(heroItems) { heroItems.map { it.key }.toHashSet() }

    // Rails are built from the panel's own VOD data — titles, years,
    // ratings and the provider's `added` timestamp all arrive with the
    // catalogue. The enktel.tv published feed used to drive Latest
    // Releases and Coming Soon, and that was the bug: it advertised films
    // from the wider world that this line does not carry, so the rails
    // showed titles the subscriber did not have and could not open.
    val todayEpochDay = remember { tv.enktel.app.data.repo.EnktelFeed.todayEpochDay() }

    var clock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            clock = TimeFormat.now("HH:mm · EEE d MMM")
            delay(30_000)
        }
    }

    // v1.28.0 — install a shared FocusedPosterState so every PosterCard on
    // this screen can crossfade the Ambilight backdrop to its own artwork
    // after a 220 ms dwell. Falls back to the current hero when nothing is
    // focused (fresh screen, empty rail, etc.).
    val focusedPoster = tv.enktel.app.ui.components.rememberFocusedPosterState()
    androidx.compose.runtime.CompositionLocalProvider(
        tv.enktel.app.ui.components.LocalFocusedPoster provides focusedPoster,
    ) {
    // Ambilight glow: extract dominant colour from the currently-focused
    // poster (or the hero banner if nothing is focused yet) and bleed it
    // behind the whole Home layout as a soft radial wash.  Sits in a Box
    // below the LazyColumn so it never intercepts touches.
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        val heroPoster = heroItems.firstOrNull()?.poster
        val backdropUrl = focusedPoster.currentUrl ?: heroPoster
        tv.enktel.app.ui.components.AmbilightGlow(
            imageUrl = backdropUrl,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        // Hover auto-trailer for whichever rail poster the user has settled on.
        // Sits above the colour wash but below the rails, and no-ops entirely
        // for cards without a TMDB id (continue-watching, live channels).
        tv.enktel.app.ui.components.AutoTrailerLayer(graph)
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
    ) {
        if (heroItems.isNotEmpty()) {
            item { HeroBanner(items = heroItems, clock = clock, nav = nav, graph = graph, profile = p) }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(painter = painterResource(R.drawable.logo_full), contentDescription = "EnkTel", modifier = Modifier.width(190.dp))
                    Spacer(Modifier.weight(1f))
                    Text(clock, color = EnktelTextDim, fontSize = 14.sp)
                }
            }
        }
        // The hub-tile row that used to sit here has gone.
        //
        // It was eleven pill cards — Live TV, TV Guide, Movies, Series, Sports,
        // Watchlist, Downloads, Recordings, Catch-Up, Search, Settings — and
        // every single one of them is already a permanent entry in the shell's
        // navigation: the rail on TV, the tab bar plus its More sheet on mobile.
        // So Home opened with a complete second copy of the menu that was
        // visible three centimetres to its left, and the first thing a D-pad
        // met on the screen was a row of shortcuts rather than the user's
        // content.
        //
        // Nothing is lost by removing it (the nav reaches all eleven from every
        // screen, not just this one) and two things are gained: Home starts on
        // Continue Watching, and a screenful of vertical space comes back —
        // which is most of what made Home feel cramped in landscape.
        if (continueWatching.isNotEmpty()) {
            item {
                ContentRail(
                    "Continue Watching", continueWatching,
                    accent = tv.enktel.app.ui.theme.EnktelLive,
                    subtitle = "pick up where you left off",
                    key = { it.key },
                ) { cw ->
                    // The percentage used to be appended to the subtitle as
                    // text. It is now the bar along the bottom of the card,
                    // which is the same fact stated in a form that survives a
                    // ten-foot viewing distance, so the subtitle goes back to
                    // saying what the action is.
                    val frac = tv.enktel.app.data.repo.ResumePolicy
                        .percent(cw.positionMs, cw.durationMs)
                        ?.let { it / 100f } ?: 0f
                    PosterCard(
                        title = cw.name,
                        imageUrl = cw.poster,
                        subtitle = "Resume",
                        wide = true,
                        progress = frac,
                        // The series identity travels with the row, so resuming
                        // an episode here rolls into the next one the same way
                        // starting it from the series screen does. Films carry
                        // 0 and are unaffected.
                        onClick = {
                            nav.navigate(
                                vodPlayerRoute(
                                    cw.url, cw.name, cw.key, poster = cw.poster,
                                    seriesId = cw.seriesId,
                                    episodeId = if (cw.seriesId != 0L) cw.refId else 0L,
                                    seriesName = cw.seriesName,
                                ),
                            )
                        },
                    )
                }
            }
        }
        if (watchlist.isNotEmpty()) {
            item {
                ContentRail(
                    "My Watchlist", watchlist.take(15),
                    accent = tv.enktel.app.ui.theme.EnktelPurple,
                    key = { it.key },
                ) { w ->
                    PosterCard(
                        title = w.name, imageUrl = w.poster,
                        subtitle = if (w.kind == "series") "Series" else "Movie",
                        onClick = {
                            if (w.kind == "vod") nav.navigate("movie/${w.profileId}:${w.refId}")
                            else nav.navigate("seriesDetails/${w.profileId}:${w.refId}")
                        },
                    )
                }
            }
        }
        // Sits above Latest Releases on purpose: this is the one rail that is
        // literally "what turned up in the last playlist refresh", which is
        // what people open the app to find. It is empty on a first sync (see
        // FreshCatalogue) and empty when a refresh added nothing — both of
        // which are true statements, so it just hides.
        if (justAdded.isNotEmpty()) {
            item {
                ContentRail(
                    "⚡  Just Added", justAdded.filterNot { it.key in heroKeys },
                    accent = tv.enktel.app.ui.theme.EnktelBlue,
                    subtitle = "new since your last sync",
                    key = { it.key },
                ) { m ->
                    val ageDays = ((System.currentTimeMillis() - m.firstSeenAt) / 86_400_000L)
                        .coerceAtLeast(0)
                    PosterCard(
                        m.name, m.poster,
                        subtitle = when {
                            ageDays < 1 -> "Today"
                            ageDays < 2 -> "Yesterday"
                            else -> "${ageDays}d ago"
                        },
                        onClick = { nav.navigate("movie/${m.key}") },
                        tmdbId = m.tmdbId,
                        platformHint = m.categoryId,
                    )
                }
            }
        }
        if (latestReleases.isNotEmpty()) {
            item {
                ContentRail(
                    "🆕  Latest Releases", latestReleases.filterNot { it.key in heroKeys },
                    accent = tv.enktel.app.ui.theme.EnktelOk,
                    subtitle = "newest on your line",
                    key = { it.key },
                ) { m ->
                    val ageDays = ((System.currentTimeMillis() / 1000 - m.addedAt) / 86_400).coerceAtLeast(0)
                    val sub = when {
                        m.addedAt <= 0 && m.year > 0 -> "${m.year}"
                        ageDays <= 1 -> "Just added"
                        ageDays < 7 -> "${ageDays}d ago"
                        m.year > 0 -> "${m.year}"
                        else -> ""
                    }
                    PosterCard(
                        m.name, m.poster, subtitle = sub,
                        onClick = { nav.navigate("movie/${m.key}") }, tmdbId = m.tmdbId,
                        platformHint = m.categoryId,
                    )
                }
            }
        }
        // General-interest rails, surfaced above "Because You Watched" so
        // engaged viewers meet fresh content first. Each is skipped when its
        // query yields nothing, so a catalogue that lacks one does not render
        // an empty section header.
        //
        // These replace three keyword-matched UFO/disclosure rails. Those
        // filtered on one narrow subject, so on most catalogues they were
        // either empty or repeated the same handful of titles — occupying the
        // top of Home without earning it.
        // Ranked rails carry their numeral.
        //
        // Top Rated, Trending and Top Picks are orderings — the position of a
        // title in them is the whole point — and until now that order was
        // invisible: thirty identical cards whose sequence a viewer had no
        // reason to read as meaningful. The hero banner had been saying
        // "#3 TOP 10" since v1.27; the rails that actually are a top ten said
        // nothing. Only the first ten get a numeral, because that is the claim
        // the rail can honestly make.
        if (topRated.isNotEmpty()) {
            item {
                tv.enktel.app.ui.components.ContentRailIndexed(
                    "★  Top Rated", topRated,
                    accent = tv.enktel.app.ui.theme.EnktelOk,
                    subtitle = "highest rated in your playlist",
                    key = { it.key },
                ) { i, m ->
                    PosterCard(
                        m.name, m.poster,
                        subtitle = if (m.rating > 0) "★ ${"%.1f".format(m.rating)}" else m.genre.take(24),
                        onClick = { nav.navigate("movie/${m.key}") }, tmdbId = m.tmdbId,
                        rank = if (i < 10) i + 1 else 0,
                        platformHint = m.categoryId,
                    )
                }
            }
        }
        if (newSeries.isNotEmpty()) {
            item {
                ContentRail(
                    "📺  New Series", newSeries,
                    accent = tv.enktel.app.ui.theme.EnktelPurple,
                    subtitle = "newest shows in your playlist",
                    key = { it.key },
                ) { sr ->
                    PosterCard(
                        sr.name, sr.poster,
                        subtitle = if (sr.year > 0) "${sr.year}" else sr.genre.take(24),
                        onClick = { nav.navigate("seriesDetails/${sr.key}") },
                        tmdbId = sr.tmdbId, isSeries = true,
                    )
                }
            }
        }
        if (documentaries.isNotEmpty()) {
            item {
                ContentRail(
                    "📚  Documentaries", documentaries,
                    accent = tv.enktel.app.ui.theme.EnktelBlue,
                    subtitle = "factual and long-form",
                    key = { it.key },
                ) { m ->
                    PosterCard(
                        m.name, m.poster,
                        subtitle = if (m.year > 0) "${m.year}" else m.genre.take(24),
                        onClick = { nav.navigate("movie/${m.key}") }, tmdbId = m.tmdbId,
                    )
                }
            }
        }
        // Newest additions lead the page.
        //
        // This rail existed all along as "Latest Movies", ninth down the
        // screen under three other rails — so the single most common reason to
        // open the app, "what's new since last time", required scrolling past
        // everything else to answer. It is sorted by addedAt DESC, which is
        // exactly the question being asked; it just wasn't where anyone would
        // look, and "Latest" read as a genre rather than a recency.
        if (recentMovies.isNotEmpty()) {
            item {
                ContentRail(
                    "Recently Added",
                    recentMovies,
                    key = { it.key },
                    subtitle = "new in your playlist",
                ) { m ->
                    PosterCard(
                        title = m.name,
                        imageUrl = m.poster,
                        onClick = { nav.navigate("movie/${m.key}") },
                        tmdbId = m.tmdbId,
                    )
                }
            }
        }
        if (becauseYouWatched.isNotEmpty()) {
            item {
                ContentRail("Because You Watched", becauseYouWatched, key = { it.key }) { m ->
                    PosterCard(m.name, m.poster, subtitle = m.genre.take(20),
                        onClick = { nav.navigate("movie/${m.key}") }, tmdbId = m.tmdbId)
                }
            }
        }
        if (trending.isNotEmpty()) {
            item {
                tv.enktel.app.ui.components.ContentRailIndexed(
                    "Trending on EnkTel", trending,
                    accent = tv.enktel.app.ui.theme.EnktelLive,
                    subtitle = "everyone's watching",
                    key = { it.key },
                ) { i, m ->
                    PosterCard(
                        m.name, m.poster,
                        subtitle = if (m.rating > 0) "★ ${"%.1f".format(m.rating)}" else "",
                        onClick = { nav.navigate("movie/${m.key}") }, tmdbId = m.tmdbId,
                        rank = if (i < 10) i + 1 else 0,
                        platformHint = m.categoryId,
                    )
                }
            }
        }
        if (topPicks.isNotEmpty()) {
            item {
                tv.enktel.app.ui.components.ContentRailIndexed(
                    "⭐  Top Picks", topPicks,
                    accent = tv.enktel.app.ui.theme.EnktelOk,
                    subtitle = "TMDB-rated highlights from your library",
                    key = { it.key },
                ) { i, m ->
                    PosterCard(
                        m.name, m.poster,
                        subtitle = if (m.rating > 0) "★ ${"%.1f".format(m.rating)}" else m.genre.take(20),
                        onClick = { nav.navigate("movie/${m.key}") },
                        rank = if (i < 10) i + 1 else 0,
                        platformHint = m.categoryId,
                    )
                }
            }
        }
        if (newThisWeek.isNotEmpty()) {
            item {
                ContentRail("New This Week", newThisWeek, key = { it.key }) { m ->
                    PosterCard(m.name, m.poster, subtitle = "New",
                        onClick = { nav.navigate("movie/${m.key}") }, tmdbId = m.tmdbId)
                }
            }
        }
        // --- Mood / vibe rails ---
        // Rendered as its own group so users who "just want a vibe" can
        // browse the way they actually think about content instead of
        // hunting through raw genre grids.
        if (moodFastPaced.isNotEmpty()) {
            item {
                ContentRail(
                    "🔥 Fast-Paced Thrillers", moodFastPaced,
                    accent = tv.enktel.app.ui.theme.EnktelLive,
                    subtitle = "keep the adrenaline high",
                    key = { it.key },
                ) { m ->
                    PosterCard(m.name, m.poster,
                        subtitle = if (m.rating > 0) "★ ${"%.1f".format(m.rating)}" else "",
                        onClick = { nav.navigate("movie/${m.key}") }, tmdbId = m.tmdbId)
                }
            }
        }
        if (moodGritty.isNotEmpty()) {
            item {
                ContentRail(
                    "🌒 Gritty & Tension-Filled", moodGritty,
                    accent = tv.enktel.app.ui.theme.EnktelPurple,
                    subtitle = "shadowy, morally grey",
                    key = { it.key },
                ) { m ->
                    PosterCard(m.name, m.poster,
                        subtitle = if (m.rating > 0) "★ ${"%.1f".format(m.rating)}" else "",
                        onClick = { nav.navigate("movie/${m.key}") }, tmdbId = m.tmdbId)
                }
            }
        }
        if (moodMindBending.isNotEmpty()) {
            item {
                ContentRail(
                    "🧠 Mind-Bending Plots", moodMindBending,
                    accent = tv.enktel.app.ui.theme.EnktelBlue,
                    subtitle = "sci-fi and mystery, top-rated",
                    key = { it.key },
                ) { m ->
                    PosterCard(m.name, m.poster,
                        subtitle = if (m.rating > 0) "★ ${"%.1f".format(m.rating)}" else "",
                        onClick = { nav.navigate("movie/${m.key}") }, tmdbId = m.tmdbId)
                }
            }
        }
        if (moodLateNight.isNotEmpty()) {
            item {
                ContentRail(
                    "🌙 Late-Night Background Watch", moodLateNight,
                    accent = tv.enktel.app.ui.theme.EnktelTextDim,
                    subtitle = "easy, comforting picks",
                    key = { it.key },
                ) { m ->
                    PosterCard(m.name, m.poster,
                        subtitle = if (m.rating > 0) "★ ${"%.1f".format(m.rating)}" else "",
                        onClick = { nav.navigate("movie/${m.key}") }, tmdbId = m.tmdbId)
                }
            }
        }
        if (moodFeelGood.isNotEmpty()) {
            item {
                ContentRail(
                    "☀️ Feel-Good & Warm-Fuzzy", moodFeelGood,
                    accent = tv.enktel.app.ui.theme.EnktelOk,
                    subtitle = "wholesome vibes",
                    key = { it.key },
                ) { m ->
                    PosterCard(m.name, m.poster,
                        subtitle = if (m.rating > 0) "★ ${"%.1f".format(m.rating)}" else "",
                        onClick = { nav.navigate("movie/${m.key}") }, tmdbId = m.tmdbId)
                }
            }
        }
        if (recentRecordings.isNotEmpty()) {
            item {
                ContentRail(
                    "Recent Recordings", recentRecordings,
                    accent = tv.enktel.app.ui.theme.EnktelLive,
                    subtitle = "saved to your library",
                    key = { it.id },
                ) { rec ->
                    PosterCard(
                        title = rec.title,
                        imageUrl = rec.channelLogo,
                        subtitle = rec.channelName,
                        wide = true,
                        onClick = { nav.navigate(vodPlayerRoute("file://${rec.filePath}", rec.title)) },
                    )
                }
            }
        }
        if (favChannels.isNotEmpty()) {
            item {
                ContentRail("Favorite Channels", favChannels, key = { it.key }) { ch ->
                    PosterCard(
                        title = ch.name,
                        imageUrl = ch.logo,
                        subtitle = if (ch.num > 0) "CH ${ch.num}" else "",
                        wide = true,
                        onClick = { nav.navigate("live?ch=${ch.key}") },
                    )
                }
            }
        }
        if (favMovies.isNotEmpty()) {
            item {
                ContentRail("Favorite Movies", favMovies, key = { it.key }) { m ->
                    PosterCard(
                        title = m.name,
                        imageUrl = m.poster,
                        onClick = { nav.navigate("movie/${m.key}") },
                    )
                }
            }
        }
        item {
            ContentRail("All Channels", allChannels.take(30), key = { it.key }) { ch ->
                PosterCard(
                    title = ch.name,
                    imageUrl = ch.logo,
                    subtitle = ch.categoryName,
                    wide = true,
                    onClick = { nav.navigate("live?ch=${ch.key}") },
                )
            }
        }
        item {
            Text(
                "Profile: ${p.name} · Synced ${if (p.lastSync > 0) TimeFormat.format("d MMM HH:mm", p.lastSync) else "never"}",
                color = EnktelTextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
        }
    }
        // First sync only (the effect above bails out unless lastSync == 0L), so
        // there is no content behind this yet — a full-screen branded splash
        // reads better than a thin banner floating over an empty Home.
        tv.enktel.app.ui.components.RefreshSplash(visible = syncing, status = syncStatus)
    } // close ambilight wrapper Box

    } // close CompositionLocalProvider (FocusedPosterState)
}


/**
 * Full-width rotating hero banner (Netflix-style): large backdrop art with a readability
 * scrim, title/logo overlay, and Play / More Info actions. Auto-advances through up to
 * 5 featured titles (favorites first, then latest additions).
 */
@Composable
private fun HeroBanner(items: List<Movie>, clock: String, nav: NavHostController, graph: AppGraph, profile: Profile) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(items) {
        index = 0
        while (items.size > 1) {
            delay(7_000)
            index = (index + 1) % items.size
        }
    }
    val current = items.getOrNull(index.coerceIn(0, items.lastIndex)) ?: return

    val isMobile = tv.enktel.app.BuildConfig.FLAVOR == "mobile"
    val heroHeight = if (isMobile) 500.dp else 480.dp
    Box(Modifier.fillMaxWidth().height(heroHeight)) {
        Crossfade(targetState = current, animationSpec = tween(700), label = "hero") { movie ->
            AsyncImage(
                model = movie.poster,
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Netflix-style stacked scrims: darker top/bottom + subtle left readability strip.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.75f),
                    0.25f to Color.Transparent,
                    0.55f to Color.Transparent,
                    1f to tv.enktel.app.ui.theme.EnktelBg,
                )
            ),
        )
        val scrimStop = if (isMobile) 0.7f else 0.5f
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.85f),
                    scrimStop to Color.Transparent,
                )
            ),
        )

        Row(
            Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painter = painterResource(R.drawable.logo_full), contentDescription = "EnkTel", modifier = Modifier.width(150.dp))
            Spacer(Modifier.weight(1f))
            Text(clock, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
        }

        val heroPad = if (isMobile) 20.dp else 48.dp
        val contentWidth = if (isMobile) Modifier.fillMaxWidth() else Modifier.width(620.dp)
        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = heroPad, vertical = 32.dp).then(contentWidth),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "▶  FEATURED",
                    fontSize = 11.sp, fontWeight = FontWeight.Black, color = tv.enktel.app.ui.theme.EnktelBlue,
                    letterSpacing = 2.sp,
                )
                if (index < 3) {
                    Box(
                        Modifier.background(tv.enktel.app.ui.theme.EnktelLive, RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text("#${index + 1} TOP 10", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                current.name,
                fontSize = if (isMobile) 28.sp else 38.sp, fontWeight = FontWeight.Black, color = Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = if (isMobile) 32.sp else 42.sp,
            )
            if (current.year > 0 || current.categoryId.isNotBlank() || current.rating > 0) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (current.rating > 0) {
                        Box(
                            Modifier.background(tv.enktel.app.ui.theme.EnktelOk.copy(0.85f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text("★ ${"%.1f".format(current.rating)}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (current.year > 0) Text("${current.year}", color = Color.White.copy(0.85f), fontSize = 13.sp)
                    if (current.categoryId.isNotBlank()) Text(current.categoryId, color = Color.White.copy(0.7f), fontSize = 13.sp)
                    if (current.genre.isNotBlank()) Text("· ${current.genre.take(28)}", color = Color.White.copy(0.7f), fontSize = 13.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                // Big Netflix-style Play button
                androidx.tv.material3.Surface(
                    onClick = { nav.navigate(vodPlayerRoute(graph.content.vodUrl(profile, current), current.name, current.key)) },
                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                        containerColor = Color.White,
                        focusedContainerColor = Color.White.copy(0.85f),
                        contentColor = Color.Black,
                        focusedContentColor = Color.Black,
                    ),
                    scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                    modifier = Modifier.tapClick { nav.navigate(vodPlayerRoute(graph.content.vodUrl(profile, current), current.name, current.key)) },
                ) {
                    Text(
                        "▶  Play",
                        color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                    )
                }
                // Glassmorphism My List (Watchlist) toggle
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                androidx.tv.material3.Surface(
                    onClick = { scope.launch { graph.watchlist.toggle(profile.id, "vod", current.streamId, current.name, current.poster) } },
                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        focusedContainerColor = Color.White.copy(alpha = 0.32f),
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                    modifier = Modifier.tapClick { scope.launch { graph.watchlist.toggle(profile.id, "vod", current.streamId, current.name, current.poster) } },
                ) {
                    Text(
                        "＋  My List",
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
                androidx.tv.material3.Surface(
                    onClick = { nav.navigate("movie/${current.key}") },
                    shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        focusedContainerColor = Color.White.copy(alpha = 0.32f),
                        contentColor = Color.White,
                        focusedContentColor = Color.White,
                    ),
                    modifier = Modifier.tapClick { nav.navigate("movie/${current.key}") },
                ) {
                    Text(
                        "ⓘ  Info",
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                }
            }
            if (items.size > 1) {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.indices.forEach { i ->
                        Box(
                            Modifier
                                .height(3.dp)
                                .width(if (i == index) 28.dp else 14.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (i == index) Color.White else Color.White.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Premium glass hub tile — big rounded pill with a leading glyph and the
 * destination label. When [accent] is true it gets a brand-color gradient
 * fill (used for the primary "Live TV" entry); otherwise it renders as a
 * frosted-glass card with a hairline border, matching modern TV OS aesthetics.
 * Focus adds a slight scale + border sheen so a viewer can see which tile
 * they're on from across the room.
 */
@Composable
private fun HubTile(glyph: String, label: String, onClick: () -> Unit, accent: Boolean = false) {
    val brand = tv.enktel.app.ui.theme.EnktelBlue
    val brandDeep = tv.enktel.app.ui.theme.EnktelBlueDeep
    val container = if (accent) brand.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f)
    val focused = if (accent) brand else Color.White.copy(alpha = 0.18f)
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = Modifier.tapClick(onClick),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = container,
            focusedContainerColor = focused,
            contentColor = Color.White,
            focusedContentColor = Color.White,
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (accent) brand.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.14f),
                ),
                shape = RoundedCornerShape(14.dp),
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
                shape = RoundedCornerShape(14.dp),
            ),
        ),
    ) {
        Box(
            Modifier
                .background(
                    // Subtle diagonal sheen so the tile reads as a "premium
                    // glass" card rather than a flat pill.
                    Brush.linearGradient(
                        colors = if (accent) {
                            listOf(brand.copy(alpha = 0.35f), brandDeep.copy(alpha = 0.25f))
                        } else {
                            listOf(Color.White.copy(alpha = 0.06f), Color.Transparent)
                        }
                    )
                )
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(glyph, fontSize = 18.sp)
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}
