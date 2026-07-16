package tv.enktel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import tv.enktel.app.ui.screens.CatchupScreen
import tv.enktel.app.ui.screens.HomeScreen
import tv.enktel.app.ui.screens.OnboardingScreen
import tv.enktel.app.ui.screens.RecordingsScreen
import tv.enktel.app.ui.screens.SearchScreen
import tv.enktel.app.ui.screens.SettingsScreen
import tv.enktel.app.ui.guide.GuideScreen
import tv.enktel.app.ui.live.LivePlayerScreen
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelTheme
import tv.enktel.app.ui.vod.MovieDetailsScreen
import tv.enktel.app.ui.vod.MoviesScreen
import tv.enktel.app.ui.vod.SeriesDetailsScreen
import tv.enktel.app.ui.vod.SeriesScreen
import tv.enktel.app.ui.vod.VodPlayerScreen
import java.net.URLDecoder
import java.net.URLEncoder

fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
fun decode(s: String): String = URLDecoder.decode(s, "UTF-8")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val graph = (application as EnktelApp).graph

        setContent {
            EnktelTheme {
                val nav = rememberNavController()
                val activeId by graph.settings.activeProfileId.collectAsStateWithLifecycle(initialValue = -1L)
                val profiles by graph.playlists.profiles.collectAsStateWithLifecycle(initialValue = null)

                if (profiles == null || activeId < 0) return@EnktelTheme
                val start = if (profiles!!.isEmpty()) "onboarding" else "home"

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
                }
            }
        }
    }
}

fun vodPlayerRoute(url: String, title: String, progressKey: String = "", live: Boolean = false): String =
    "vodPlayer?url=${encode(url)}&title=${encode(title)}&pk=$progressKey&live=${if (live) 1 else 0}"
