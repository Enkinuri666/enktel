package tv.enktel.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.enktel.app.ui.components.FirstRunTour
import tv.enktel.app.ui.components.ToastHost
import tv.enktel.app.ui.guide.GuideScreen
import tv.enktel.app.ui.live.LivePlayerScreen
import tv.enktel.app.ui.multi.MultiViewScreen
import tv.enktel.app.ui.screens.CatchupScreen
import tv.enktel.app.ui.screens.HomeScreen
import tv.enktel.app.ui.screens.OnboardingScreen
import tv.enktel.app.ui.screens.RecordingsScreen
import tv.enktel.app.ui.screens.SearchScreen
import tv.enktel.app.ui.screens.SettingsScreen
import tv.enktel.app.ui.screensaver.ScreensaverHost
import tv.enktel.app.ui.sports.SportsHubScreen
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelTheme
import tv.enktel.app.ui.vod.MovieDetailsScreen
import tv.enktel.app.ui.vod.MoviesScreen
import tv.enktel.app.ui.vod.SeriesDetailsScreen
import tv.enktel.app.ui.vod.SeriesScreen
import tv.enktel.app.ui.vod.VodPlayerScreen
import tv.enktel.app.ui.watchlist.WatchlistScreen
import java.net.URLDecoder
import java.net.URLEncoder

fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
fun decode(s: String): String = URLDecoder.decode(s, "UTF-8")

class MainActivity : ComponentActivity() {
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val graph = (application as EnktelApp).graph

        setContent {
            val themeId by graph.settings.theme.collectAsStateWithLifecycle(initialValue = "enktel_blue")
            val opacityPct by graph.settings.uiOpacityPct.collectAsStateWithLifecycle(initialValue = 92)
            val textPct by graph.settings.textScalePct.collectAsStateWithLifecycle(initialValue = 100)
            EnktelTheme(
                themeId = themeId,
                overlayOpacity = opacityPct / 100f,
                textScalePct = textPct,
            ) {
                val voiceBus = remember { tv.enktel.app.voice.VoiceCommandBus() }
                val wakeWordEnabled by graph.settings.wakeWordEnabled.collectAsStateWithLifecycle(initialValue = false)
                ToastHost {
                    ScreensaverHost(graph, isPlaying = { false }) {
                        tv.enktel.app.voice.VoiceHost(voiceBus, wakeWordEnabled = wakeWordEnabled) {
                            MainNav(graph, voiceBus = voiceBus, initialChannelKey = intent?.getStringExtra("channel_key"))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // let composable pick up new channel_key from notification taps
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // If the user hits Home while a player is on-screen and they've opted into
        // Auto-PiP-on-home in Settings, hand off to Picture-in-Picture instead of
        // just backgrounding — so playback keeps going in a floating window.
        val pip = tv.enktel.app.player.PictureInPicture
        if (pip.playerActive && pip.userWantsPipOnBack) {
            pip.enter(this)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MainNav(graph: AppGraph, voiceBus: tv.enktel.app.voice.VoiceCommandBus, initialChannelKey: String?) {
    val nav = rememberNavController()
    val activeId by graph.settings.activeProfileId.collectAsStateWithLifecycle(initialValue = -1L)
    val profiles by graph.playlists.profiles.collectAsStateWithLifecycle(initialValue = null)
    val firstRunDone by graph.settings.firstRunDone.collectAsStateWithLifecycle(initialValue = true)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var tourVisible by remember { mutableStateOf(false) }

    if (profiles == null || activeId < 0) return
    val start = if (profiles!!.isEmpty()) "onboarding" else "home"

    LaunchedEffect(firstRunDone, profiles) {
        if (!firstRunDone && profiles!!.isNotEmpty()) tourVisible = true
    }
    LaunchedEffect(initialChannelKey) {
        if (!initialChannelKey.isNullOrBlank()) nav.navigate("live?ch=$initialChannelKey")
    }

    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext

    // Voice-command navigation handler. Player-scoped commands (Pause/Resume/etc)
    // are consumed by whichever player screen is currently mounted; we take the
    // ones that navigate.
    LaunchedEffect(voiceBus) {
        voiceBus.intents.collect { intent ->
            when (intent) {
                is tv.enktel.app.voice.VoiceIntent.OpenHome -> nav.navigate("home")
                is tv.enktel.app.voice.VoiceIntent.OpenGuide -> nav.navigate("guide")
                is tv.enktel.app.voice.VoiceIntent.OpenMovies -> nav.navigate("movies")
                is tv.enktel.app.voice.VoiceIntent.OpenSeries -> nav.navigate("series")
                is tv.enktel.app.voice.VoiceIntent.OpenWatchlist -> nav.navigate("watchlist")
                is tv.enktel.app.voice.VoiceIntent.OpenRecordings -> nav.navigate("recordings")
                is tv.enktel.app.voice.VoiceIntent.OpenSettings -> nav.navigate("settings")
                is tv.enktel.app.voice.VoiceIntent.FindSports -> nav.navigate("sports")
                is tv.enktel.app.voice.VoiceIntent.Search -> {
                    nav.navigate("search")
                    // Push the spoken query onto the bus so SearchScreen (which
                    // collects it in a LaunchedEffect) can populate its input.
                    // Kick after a short delay so the destination has time to
                    // mount its collector.
                    kotlinx.coroutines.delay(150)
                    voiceBus.searchQueries.emit(intent.query)
                }
                is tv.enktel.app.voice.VoiceIntent.TuneChannel -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val match = try {
                        val all = graph.content.channels(p.id).first()
                        val q = intent.query.lowercase()
                        all.firstOrNull { it.name.equals(intent.query, ignoreCase = true) }
                            ?: all.firstOrNull { q in it.name.lowercase() }
                            ?: intent.query.toIntOrNull()?.let { num ->
                                all.firstOrNull { it.num == num }
                            }
                    } catch (_: Throwable) { null }
                    if (match != null) nav.navigate("live?ch=${match.key}")
                }
                is tv.enktel.app.voice.VoiceIntent.Suggest -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val pick = try {
                        graph.recommendations.trending(p.id).firstOrNull()
                            ?: graph.recommendations.newThisWeek(p.id).firstOrNull()
                            ?: graph.recommendations.latestReleases(p.id).firstOrNull()
                    } catch (_: Throwable) { null }
                    if (pick != null) nav.navigate("movie/${pick.key}")
                    else nav.navigate("home")
                }
                is tv.enktel.app.voice.VoiceIntent.Pause -> tv.enktel.app.voice.ActivePlayerRef.pause()
                is tv.enktel.app.voice.VoiceIntent.Resume -> tv.enktel.app.voice.ActivePlayerRef.resume()
                is tv.enktel.app.voice.VoiceIntent.SetVolume -> {
                    val am = appCtx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                    val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (intent.fraction * max).toInt().coerceIn(0, max), 0)
                }
                is tv.enktel.app.voice.VoiceIntent.VolumeUp -> {
                    val am = appCtx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                    am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, 0)
                }
                is tv.enktel.app.voice.VoiceIntent.VolumeDown -> {
                    val am = appCtx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                    am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, 0)
                }
                is tv.enktel.app.voice.VoiceIntent.Mute -> {
                    val am = appCtx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0)
                }
                is tv.enktel.app.voice.VoiceIntent.RecordNow, is tv.enktel.app.voice.VoiceIntent.ChannelUp,
                is tv.enktel.app.voice.VoiceIntent.ChannelDown, is tv.enktel.app.voice.VoiceIntent.Fullscreen,
                is tv.enktel.app.voice.VoiceIntent.Unknown -> Unit

                // ---- Query intents: answer back with a card + TTS ---------------
                is tv.enktel.app.voice.VoiceIntent.WhatSportsIsOn -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val events = try { graph.sports.load(p.id) } catch (_: Throwable) { emptyMap() }
                    val live = events["LIVE"].orEmpty()
                    val lines = live.take(6).map { ev ->
                        tv.enktel.app.voice.VoiceAnswerLine(
                            title = ev.title,
                            subtitle = "${ev.sport} · ${ev.channel.name}",
                            route = "live?ch=${ev.channel.key}",
                        )
                    }
                    val summary = when {
                        live.isEmpty() -> "No live sports on your channels right now."
                        live.size == 1 -> "One live match: ${live[0].title} on ${live[0].channel.name}."
                        else -> "${live.size} live matches right now. Top pick: ${live[0].title} on ${live[0].channel.name}."
                    }
                    voiceBus.answers.emit(
                        tv.enktel.app.voice.VoiceAnswer(
                            eyebrow = "🔴 Live sports right now",
                            heading = if (live.isEmpty()) "Nothing live" else "${live.size} match${if (live.size == 1) "" else "es"} on",
                            lines = lines, spoken = summary,
                        )
                    )
                }

                is tv.enktel.app.voice.VoiceIntent.LatestMovies -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val list = try { graph.recommendations.latestReleases(p.id, 8) } catch (_: Throwable) { emptyList() }
                    val lines = list.take(6).map { m ->
                        tv.enktel.app.voice.VoiceAnswerLine(
                            title = m.name,
                            subtitle = listOfNotNull(
                                if (m.year > 0) "${m.year}" else null,
                                m.genre.takeIf { it.isNotBlank() }?.take(28),
                                if (m.rating > 0) "★ ${"%.1f".format(m.rating)}" else null,
                            ).joinToString(" · "),
                            route = "movie/${m.key}",
                        )
                    }
                    val summary = if (list.isEmpty())
                        "No new movies in your library yet."
                    else "Fresh in: ${list.take(3).joinToString(", ") { it.name }}."
                    voiceBus.answers.emit(
                        tv.enktel.app.voice.VoiceAnswer(
                            eyebrow = "🆕 Latest movies",
                            heading = "Just added to EnkTel",
                            lines = lines, spoken = summary,
                        )
                    )
                }

                is tv.enktel.app.voice.VoiceIntent.UpcomingMovies -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val list = try { graph.recommendations.comingSoon(p.id, 8) } catch (_: Throwable) { emptyList() }
                    val lines = list.take(6).map { m ->
                        tv.enktel.app.voice.VoiceAnswerLine(
                            title = m.name,
                            subtitle = if (m.year > 0) "${m.year}" else m.genre.take(28),
                            route = "movie/${m.key}",
                        )
                    }
                    val summary = if (list.isEmpty())
                        "Nothing marked as coming soon yet."
                    else "Coming soon: ${list.take(3).joinToString(", ") { it.name }}."
                    voiceBus.answers.emit(
                        tv.enktel.app.voice.VoiceAnswer(
                            eyebrow = "🎬 Coming soon",
                            heading = "Upcoming titles",
                            lines = lines, spoken = summary,
                        )
                    )
                }

                is tv.enktel.app.voice.VoiceIntent.LatestSeries -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    // Latest N series by addedAt from the DAO.
                    val list = try {
                        graph.db.searchDao().searchSeriesDeep(p.id, "").take(8)
                    } catch (_: Throwable) { emptyList() }
                    val sortedList = list.sortedByDescending { it.year }.take(6)
                    val lines = sortedList.map { s ->
                        tv.enktel.app.voice.VoiceAnswerLine(
                            title = s.name,
                            subtitle = if (s.year > 0) "${s.year}" else s.genre.take(28),
                            route = "seriesDetails/${s.key}",
                        )
                    }
                    val summary = if (lines.isEmpty())
                        "No series in your library yet."
                    else "Recent series: ${sortedList.take(3).joinToString(", ") { it.name }}."
                    voiceBus.answers.emit(
                        tv.enktel.app.voice.VoiceAnswer(
                            eyebrow = "📺 Latest series",
                            heading = "Recent TV shows",
                            lines = lines, spoken = summary,
                        )
                    )
                }

                is tv.enktel.app.voice.VoiceIntent.WhatsOnNow -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val favs = try { graph.content.favoriteChannels(p.id).first() } catch (_: Throwable) { emptyList() }
                    val pool = if (favs.isNotEmpty()) favs
                    else try { graph.content.channels(p.id).first().take(30) } catch (_: Throwable) { emptyList() }
                    val entries = pool.take(8).mapNotNull { ch ->
                        val nn = try { graph.epg.nowNext(p.id, ch.epgId) } catch (_: Throwable) { null }
                        nn?.now?.let { prog -> ch to prog }
                    }
                    val lines = entries.map { (ch, prog) ->
                        tv.enktel.app.voice.VoiceAnswerLine(
                            title = prog.title,
                            subtitle = ch.name,
                            route = "live?ch=${ch.key}",
                        )
                    }
                    val summary = when {
                        entries.isEmpty() -> "No EPG data for your favourites right now."
                        entries.size == 1 -> "On now: ${entries[0].second.title} on ${entries[0].first.name}."
                        else -> "On your favourites now: ${entries.take(3).joinToString(", ") { it.second.title }}."
                    }
                    voiceBus.answers.emit(
                        tv.enktel.app.voice.VoiceAnswer(
                            eyebrow = "📡 On now",
                            heading = if (favs.isNotEmpty()) "Your favourite channels" else "Popular channels",
                            lines = lines, spoken = summary,
                        )
                    )
                }

                is tv.enktel.app.voice.VoiceIntent.WhatsOnChannel -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val all = try { graph.content.channels(p.id).first() } catch (_: Throwable) { emptyList() }
                    val q = intent.channel.lowercase()
                    val ch = all.firstOrNull { it.name.equals(intent.channel, ignoreCase = true) }
                        ?: all.firstOrNull { q in it.name.lowercase() }
                        ?: intent.channel.toIntOrNull()?.let { num -> all.firstOrNull { it.num == num } }
                    if (ch == null) {
                        voiceBus.answers.emit(
                            tv.enktel.app.voice.VoiceAnswer(
                                eyebrow = "❓ Not found",
                                heading = "No channel named \"${intent.channel}\"",
                                lines = emptyList(),
                                spoken = "I couldn't find a channel called ${intent.channel}.",
                            )
                        )
                        return@collect
                    }
                    val upcoming = try { graph.epg.upcoming(p.id, ch.epgId, 6) } catch (_: Throwable) { emptyList() }
                    val now = System.currentTimeMillis()
                    val lines = upcoming.map { prog ->
                        val isNow = prog.startMs <= now && prog.endMs > now
                        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(prog.startMs))
                        tv.enktel.app.voice.VoiceAnswerLine(
                            title = (if (isNow) "🔴  " else "$time  ") + prog.title,
                            subtitle = if (isNow) "Now" else "",
                            route = "live?ch=${ch.key}",
                        )
                    }
                    val nowShow = upcoming.firstOrNull { it.startMs <= now && it.endMs > now }
                    val summary = if (nowShow != null) "On ${ch.name} now: ${nowShow.title}."
                        else if (upcoming.isNotEmpty()) "Next on ${ch.name}: ${upcoming[0].title}."
                        else "No guide data for ${ch.name}."
                    voiceBus.answers.emit(
                        tv.enktel.app.voice.VoiceAnswer(
                            eyebrow = "📡 ${ch.name}",
                            heading = "What's on next",
                            lines = lines, spoken = summary,
                        )
                    )
                }

                is tv.enktel.app.voice.VoiceIntent.TellMeAbout -> {
                    // Route to search for now; deeper metadata is a future add.
                    nav.navigate("search")
                }
            }
        }
    }

    // Mobile flavor gets a bottom-tab shell; the TV flavor renders the NavHost bare
    // and relies on the in-screen navigation you'd steer with a remote.
    val isMobileShell = BuildConfig.FLAVOR == "mobile" ||
        (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) != Configuration.UI_MODE_TYPE_TELEVISION &&
        BuildConfig.FLAVOR != "tv"

    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    val navHost = @Composable { padding: androidx.compose.foundation.layout.PaddingValues ->
    NavHost(
        navController = nav,
        startDestination = start,
        modifier = Modifier.fillMaxSize().background(EnktelBg).padding(padding),
    ) {
        composable("onboarding") { OnboardingScreen(graph, onDone = { nav.navigate("home") { popUpTo(0) } }) }
        composable("home") { HomeScreen(graph, nav) }
        composable("live?ch={ch}") { back ->
            LivePlayerScreen(graph, nav, initialChannelKey = back.arguments?.getString("ch").orEmpty())
        }
        composable("guide") { GuideScreen(graph, nav) }
        composable("movies") { MoviesScreen(graph, nav) }
        composable("movie/{key}") { back ->
            MovieDetailsScreen(graph, nav, key = back.arguments?.getString("key").orEmpty())
        }
        composable("series") { SeriesScreen(graph, nav) }
        composable("seriesDetails/{key}") { back ->
            SeriesDetailsScreen(graph, nav, key = back.arguments?.getString("key").orEmpty())
        }
        composable("vodPlayer?url={url}&title={title}&pk={pk}&live={live}") { back ->
            val a = back.arguments
            VodPlayerScreen(
                graph,
                nav,
                url = decode(a?.getString("url").orEmpty()),
                title = decode(a?.getString("title").orEmpty()),
                progressKey = a?.getString("pk").orEmpty(),
                isLive = a?.getString("live") == "1",
            )
        }
        composable("search") { SearchScreen(graph, nav, voiceBus = voiceBus) }
        composable("settings") { SettingsScreen(graph, nav) }
        composable("manageCategories") { tv.enktel.app.ui.screens.ManageCategoriesScreen(graph, nav) }
        composable("recordings") { RecordingsScreen(graph, nav) }
        composable("catchup/{ch}") { back ->
            CatchupScreen(graph, nav, channelKey = back.arguments?.getString("ch").orEmpty())
        }
        composable("sports") { SportsHubScreen(graph, nav) }
        composable("watchlist") { WatchlistScreen(graph, nav) }
        composable("multi?left={left}&right={right}") { back ->
            MultiViewScreen(
                graph, nav,
                leftKey = back.arguments?.getString("left").orEmpty(),
                rightKey = back.arguments?.getString("right").orEmpty(),
            )
        }
    }
    }

    if (isMobileShell) {
        tv.enktel.app.ui.mobile.MobileScaffold(nav = nav, currentRoute = currentRoute) { padding ->
            navHost(padding)
        }
    } else {
        navHost(androidx.compose.foundation.layout.PaddingValues(0.dp))
    }

    if (tourVisible) {
        FirstRunTour(onFinish = {
            tourVisible = false
            scope.launch { graph.settings.setFirstRunDone(true) }
        })
    }
}

fun vodPlayerRoute(url: String, title: String, progressKey: String = "", live: Boolean = false): String =
    "vodPlayer?url=${encode(url)}&title=${encode(title)}&pk=$progressKey&live=${if (live) 1 else 0}"
