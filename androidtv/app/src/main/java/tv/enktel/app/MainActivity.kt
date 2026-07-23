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
                ToastHost {
                    ScreensaverHost(graph, isPlaying = { false }) {
                        tv.enktel.app.voice.VoiceHost(voiceBus) {
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
                    // Search field takes a moment to mount; publishing the query is a
                    // future-work follow-up (screen currently reads its query from
                    // local state).
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
        composable("search") { SearchScreen(graph, nav) }
        composable("settings") { SettingsScreen(graph, nav) }
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
