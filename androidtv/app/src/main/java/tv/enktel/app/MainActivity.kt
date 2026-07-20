package tv.enktel.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
            EnktelTheme {
                ToastHost {
                    ScreensaverHost(graph, isPlaying = { false }) {
                        MainNav(graph, initialChannelKey = intent?.getStringExtra("channel_key"))
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // let composable pick up new channel_key from notification taps
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MainNav(graph: AppGraph, initialChannelKey: String?) {
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

    NavHost(
        navController = nav,
        startDestination = start,
        modifier = Modifier.fillMaxSize().background(EnktelBg),
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

    if (tourVisible) {
        FirstRunTour(onFinish = {
            tourVisible = false
            scope.launch { graph.settings.setFirstRunDone(true) }
        })
    }
}

fun vodPlayerRoute(url: String, title: String, progressKey: String = "", live: Boolean = false): String =
    "vodPlayer?url=${encode(url)}&title=${encode(title)}&pk=$progressKey&live=${if (live) 1 else 0}"
