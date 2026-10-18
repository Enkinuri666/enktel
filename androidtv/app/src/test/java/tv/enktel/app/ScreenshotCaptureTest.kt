package tv.enktel.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.DownloadEntry
import tv.enktel.app.data.db.EpgProgram
import tv.enktel.app.data.db.Profile
import tv.enktel.app.i18n.Lang
import tv.enktel.app.ui.theme.EnktelTheme
import tv.enktel.app.ui.theme.EnktelBg
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File

/**
 * Photographs the app, without a device.
 *
 * There is no emulator here — no KVM — so the usual answer to "we need
 * screenshots of the Android apps" is a mock-up drawn by hand, and a mock-up
 * in a user guide is a promise the app has not agreed to. Robolectric's native
 * graphics mode rasterises for real, so these are the shipped composables, the
 * shipped theme and the shipped strings, drawn by the same Skia the phone uses.
 *
 * It writes files rather than asserting anything, so `build.gradle.kts`
 * excludes it from the ordinary test task and `-Penktel.shots=1` opts in:
 *
 *     ./gradlew :app:testMobileDebugUnitTest -Penktel.shots=1 \
 *         -Penktel.shots.dir=/somewhere --tests '*ScreenshotCaptureTest'
 *
 * Run it once per flavour; see docs/SCREENSHOTS.md.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotCaptureTest {

    @get:Rule val rule = createComposeRule()

    private companion object {
        /**
         * A mid-range phone in portrait, and a 1080p television.
         *
         * Robolectric sizes the screen from these, and the screen size is what
         * the layouts branch on — capturing everything at the default 320x480
         * would photograph a phone layout and label it a TV.
         */
        const val PHONE = "w411dp-h891dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi-notouch"

        /**
         * A phone on its side. 891x411 is the PHONE qualifier rotated.
         *
         * The shape that exposed the Settings bug: wide enough that
         * `shape.narrow` said "roomy", short enough that the fixed header ate
         * the whole window and the settings under it were measured into
         * nothing.
         */
        const val PHONE_LAND = "w891dp-h411dp-land-xhdpi"
    }

    private val outDir: File by lazy {
        File(System.getProperty("enktel.shots.dir") ?: "build/shots").apply { mkdirs() }
    }

    /**
     * Written with the flavour in the name, so running the harness against
     * `mobile` and against `tv` fills the same directory with both sets
     * rather than one quietly overwriting the other.
     */
    private fun capture(name: String) {
        val img = rule.onRoot().captureToImage()
        val f = File(outDir, "$name-${BuildConfig.FLAVOR}.png")
        f.outputStream().use {
            img.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        println("SHOT $name ${img.width}x${img.height} -> ${f.absolutePath}")
    }

    /**
     * Let a debounced screen finish before photographing it.
     *
     * Search waits 300 ms before it touches the guide, so a capture on the
     * first frame photographs the channel rail alone and reports "Sport (0)" —
     * a picture of the feature not working. One big `advanceTimeBy` is not
     * enough on its own: whether the effect's coroutine has been scheduled by
     * the time the clock jumps is a race, and it lost about half the time.
     * Stepping the clock and idling between steps gives it somewhere to be
     * resumed.
     */
    private fun settle(steps: Int = 20, stepMs: Long = 100) {
        repeat(steps) {
            rule.mainClock.advanceTimeBy(stepMs)
            rule.waitForIdle()
            // waitForIdle only waits for the composition. A screen that loads
            // its content with a suspending query hands that work to the IO
            // dispatcher, on a real thread the test clock knows nothing
            // about, so without a real pause the capture races it: the live
            // grid photographed its channels with no guide on them about half
            // the time, which looks exactly like a bug in the card.
            Thread.sleep(5)
        }
    }

    private fun themed(content: @Composable () -> Unit) = themed(Lang.EN, content)

    /**
     * The same screen, drawn in a chosen language, on the app's own surface.
     *
     * The background matters more than it sounds. Both shells paint EnktelBg
     * behind whatever screen is showing — MobileShell at its root, the TV
     * host at its — and most screens therefore do not paint one themselves.
     * Captured bare they came out on the harness's default light surface,
     * which turned near-white body text into near-white-on-pale and made
     * every render a photograph of a contrast problem the app does not have.
     * Two of the shots taken this way were nearly unreadable, and they were
     * the ones I was about to draw conclusions from.
     */
    private fun themed(lang: String, content: @Composable () -> Unit) {
        rule.setContent {
            EnktelTheme(lang = lang) {
                Box(Modifier.fillMaxSize().background(EnktelBg)) { content() }
            }
        }
    }

    // ── the graph, with just enough in it to draw ──────────────────────

    private fun graph(): AppGraph =
        AppGraph(ApplicationProvider.getApplicationContext())

    private fun seedProfile(g: AppGraph): Long = runBlocking {
        g.db.profileDao().insert(
            Profile(
                name = "My EnkTel line",
                kind = "xtream",
                server = "https://x-api.cc",
                username = "enktel_demo",
                password = "demo",
                expiresAt = System.currentTimeMillis() + 30L * 86_400_000,
                maxConnections = 3,
            ),
        )
    }

    private fun seedChannels(g: AppGraph, profileId: Long): Unit = runBlocking {
        val names = listOf(
            "AU | Channel 7 HD", "AU | Channel 9 HD", "AU | Channel 10 HD", "AU | ABC TV HD",
            "AU | SBS HD", "AU | Fox Sports 501", "AU | Fox Sports 502", "AU | Fox League HD",
            "AU | Fox Footy HD", "AU | Fox Cricket HD", "AU | beIN Sports 1", "AU | ESPN HD",
            "AU | Discovery HD", "AU | National Geographic", "AU | Nickelodeon",
            "UK | Sky Sports Main Event", "UK | BBC One HD", "US | HBO HD",
        )
        g.db.contentDao().upsertChannels(
            names.mapIndexed { i, n ->
                Channel(
                    key = "$profileId:${1000 + i}",
                    profileId = profileId,
                    streamId = (1000 + i).toLong(),
                    name = n,
                    num = i + 1,
                    categoryId = "1",
                    categoryName = "Australia",
                    epgId = "ch.$i",
                    sortIdx = i,
                )
            },
        )
        // A guide on every channel, at a different point in each — otherwise
        // the grid renders with no progress bars at all and the capture
        // proves nothing about them. The last entry is a day-long block, the
        // shape a line with no real EPG publishes: its card should show the
        // title and no countdown.
        val now = System.currentTimeMillis()
        val minute = 60_000L
        val titles = listOf(
            "Seven News at Six", "A Current Affair", "The Project", "7.30",
            "Insight", "NRL: Panthers v Storm", "AFL: Carlton v Collingwood",
            "League Live", "Footy Classified", "Test Cricket: Day 3",
            "LaLiga: Real Betis v Sevilla", "SportsCenter",
            "Gold Rush", "Air Crash Investigation", "SpongeBob SquarePants",
            "Premier League: Arsenal v Chelsea", "BBC News at Ten", "Succession",
        )
        g.db.epgDao().insertAll(
            titles.mapIndexed { i, title ->
                // On air now in every case, at a different point in each.
                val startedMinutesAgo = (7L + i * 11L) % 95L + 3L
                val leftMinutes = if (i == titles.lastIndex) 13 * 60L else 5L + (i * 17L) % 80L
                EpgProgram(
                    profileId = profileId,
                    epgId = "ch.$i",
                    startMs = now - startedMinutesAgo * minute,
                    endMs = now + leftMinutes * minute,
                    title = title,
                )
            },
        )
    }

    /**
     * Sports channels with a fixture on each, so the Sport rail has something
     * to draw. The channel names matter: the classifier reads them, so
     * "Sky Sports Football HD" is what makes "Ajax - PSV" land under Football
     * with no league named in the title.
     */
    private fun seedSport(g: AppGraph, profileId: Long): Unit = runBlocking {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        val channels = listOf(
            Triple("UK | Sky Sports Main Event", "sky.main", "UK | SPORTS"),
            Triple("UK | Sky Sports Premier League", "sky.pl", "UK | SPORTS"),
            Triple("UK | TNT Sports 1 HD", "tnt.1", "UK | SPORTS"),
            Triple("US | ESPN HD", "espn", "US | SPORTS"),
        )
        g.db.contentDao().upsertChannels(
            channels.mapIndexed { i, (name, epgId, cat) ->
                Channel(
                    key = "$profileId:${2000 + i}", profileId = profileId,
                    streamId = (2000 + i).toLong(), name = name, num = 500 + i,
                    categoryId = "9", categoryName = cat, epgId = epgId, sortIdx = 100 + i,
                )
            },
        )
        g.db.epgDao().insertAll(
            listOf(
                // The same fixture on two feeds — the rail folds them into one
                // row and says so, which is the behaviour worth photographing.
                EpgProgram(
                    profileId = profileId, epgId = "sky.main",
                    startMs = now - hour, endMs = now + hour,
                    title = "Premier League: Arsenal v Chelsea",
                ),
                EpgProgram(
                    profileId = profileId, epgId = "sky.pl",
                    startMs = now - hour, endMs = now + hour,
                    title = "Premier League: Arsenal v Chelsea",
                ),
                EpgProgram(
                    profileId = profileId, epgId = "tnt.1",
                    startMs = now + 2 * hour, endMs = now + 4 * hour,
                    title = "Premier League: Liverpool v Everton",
                ),
                EpgProgram(
                    profileId = profileId, epgId = "espn",
                    startMs = now + 5 * hour, endMs = now + 8 * hour,
                    title = "Premier League Review",
                ),
            ),
        )
    }

    /** A film library, so Movies has posters and categories to lay out. */
    private fun seedMovies(g: AppGraph, profileId: Long): Unit = runBlocking {
        g.db.contentDao().upsertCategories(
            listOf("Action", "Drama", "Comedy", "Documentary").mapIndexed { i, name ->
                tv.enktel.app.data.db.Category(
                    key = "$profileId:vod:v$i", profileId = profileId, kind = "vod",
                    categoryId = "v$i", name = name, sortIdx = i,
                )
            },
        )
        val titles = listOf(
            "The Northern Line" to 2021, "Ridgeline" to 2019, "Harbourlight" to 2023,
            "Ashgrove" to 2018, "The Long Weekend" to 2022, "Saltmarsh" to 2020,
            "Winterfold" to 2024, "Quarry Road" to 2017, "The Cold Coast" to 2016,
            "Brightwater" to 2025, "Ninepin" to 2015, "Low Country" to 2014,
        )
        g.db.contentDao().upsertMovies(
            titles.mapIndexed { i, (name, year) ->
                tv.enktel.app.data.db.Movie(
                    key = "$profileId:m$i", profileId = profileId, streamId = (3000 + i).toLong(),
                    name = name, year = year, categoryId = "v${i % 4}",
                    genre = listOf("Action", "Drama", "Comedy", "Documentary")[i % 4],
                    addedAt = System.currentTimeMillis() - i * 86_400_000L,
                )
            },
        )
    }

    private fun seedDownloads(g: AppGraph, profileId: Long): Unit = runBlocking {
        val rows = listOf(
            DownloadEntry(
                id = "d1", profileId = profileId, kind = "movie", refId = 1,
                title = "The Northern Line", sourceUrl = "https://example.invalid/1",
                status = "RUNNING", progressPct = 62,
                sizeBytes = 4_100_000_000, downloadedBytes = 2_542_000_000,
            ),
            DownloadEntry(
                id = "d2", profileId = profileId, kind = "episode", refId = 2,
                seriesKey = "$profileId:9", seriesName = "Ridgeline", season = 2, episode = 4,
                title = "The Long Weekend", sourceUrl = "https://example.invalid/2",
                status = "RUNNING", progressPct = 18,
                sizeBytes = 1_800_000_000, downloadedBytes = 324_000_000,
            ),
            DownloadEntry(
                id = "d3", profileId = profileId, kind = "episode", refId = 3,
                seriesKey = "$profileId:9", seriesName = "Ridgeline", season = 2, episode = 5,
                title = "Ashgrove", sourceUrl = "https://example.invalid/3",
                status = "PAUSED", progressPct = 45,
                sizeBytes = 1_600_000_000, downloadedBytes = 720_000_000,
            ),
            DownloadEntry(
                id = "d4", profileId = profileId, kind = "movie", refId = 4,
                title = "Harbourlight", sourceUrl = "https://example.invalid/4",
                status = "DONE", progressPct = 100,
                filePath = "/storage/emulated/0/Movies/Harbourlight.mkv",
                sizeBytes = 3_872_000_000, downloadedBytes = 3_872_000_000,
            ),
        )
        rows.forEach { g.db.downloadDao().upsert(it) }
        // Without this the header reads "0 B on device" beside three
        // downloads, which is real app behaviour before the first tick and a
        // lie in a still photograph of it.
        g.downloads.refreshTotals()
    }

    // ── the screens ────────────────────────────────────────────────────

    @Test
    @Config(qualifiers = PHONE)
    fun onboarding() {
        val g = graph()
        themed { tv.enktel.app.ui.screens.OnboardingScreen(g) {} }
        capture("onboarding")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun downloads() {
        val g = graph()
        val p = seedProfile(g)
        seedDownloads(g, p)
        themed {
            // A real controller with no graph attached: the screen only holds
            // it to navigate on a tap, and a still capture never taps.
            val nav = androidx.navigation.compose.rememberNavController()
            tv.enktel.app.ui.downloads.DownloadsScreen(g, nav)
        }
        capture("downloads")
    }

    /** The branded category row that opens Home. See CategoryTiles. */
    @Test
    @Config(qualifiers = PHONE)
    fun categoryTiles() {
        themed {
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .background(tv.enktel.app.ui.theme.EnktelBg)
                    .padding(vertical = 20.dp),
            ) {
                tv.enktel.app.ui.screens.CategoryTiles(
                    padHoriz = 16.dp, compact = true,
                    expiry = "This line expires in 12 days.", onSelect = {},
                )
            }
        }
        capture("category-tiles")
    }

    @Test
    @Config(qualifiers = TV)
    fun categoryTilesOnTelevision() {
        themed {
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .background(tv.enktel.app.ui.theme.EnktelBg)
                    .padding(vertical = 28.dp),
            ) {
                tv.enktel.app.ui.screens.CategoryTiles(
                    padHoriz = 48.dp, compact = false,
                    expiry = "This line expires in 12 days.", onSelect = {},
                )
            }
        }
        capture("category-tiles-bigscreen")
    }

    /** The other half of the short menu. See MoreScreen. */
    @Test
    @Config(qualifiers = PHONE)
    fun search() {
        val g = graph()
        val p = seedProfile(g)
        seedChannels(g, p)
        seedSport(g, p)
        themed {
            tv.enktel.app.ui.screens.SearchScreen(
                g,
                androidx.navigation.compose.rememberNavController(),
                initialQuery = "premier league",
            )
        }
        settle()
        capture("search")
    }

    @Test
    @Config(qualifiers = TV)
    fun searchOnTelevision() {
        val g = graph()
        val p = seedProfile(g)
        seedChannels(g, p)
        seedSport(g, p)
        themed {
            tv.enktel.app.ui.screens.SearchScreen(
                g,
                androidx.navigation.compose.rememberNavController(),
                initialQuery = "premier league",
            )
        }
        settle()
        capture("search-bigscreen")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun signInSerboCroatian() {
        val g = graph()
        themed(tv.enktel.app.i18n.Lang.SH) {
            tv.enktel.app.ui.screens.OnboardingScreen(g) {}
        }
        capture("onboarding-sh")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun moreSerboCroatian() {
        themed(tv.enktel.app.i18n.Lang.SH) {
            tv.enktel.app.ui.screens.MoreScreen(onSelect = {})
        }
        capture("more-sh")
    }

    @Test
    @Config(qualifiers = TV)
    fun moreSerboCroatianOnTelevision() {
        themed(tv.enktel.app.i18n.Lang.SH) {
            tv.enktel.app.ui.screens.MoreScreen(onSelect = {})
        }
        capture("more-sh-bigscreen")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun searchSerboCroatian() {
        val g = graph()
        val p = seedProfile(g)
        seedChannels(g, p)
        seedSport(g, p)
        themed(tv.enktel.app.i18n.Lang.SH) {
            tv.enktel.app.ui.screens.SearchScreen(
                g,
                androidx.navigation.compose.rememberNavController(),
                initialQuery = "premier league",
            )
        }
        settle()
        capture("search-sh")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun settingsLanguage() {
        val g = graph()
        seedProfile(g)
        themed {
            tv.enktel.app.ui.screens.SettingsScreen(
                g,
                androidx.navigation.compose.rememberNavController(),
            )
        }
        settle()
        capture("settings-language")
    }

    @Test
    @Config(qualifiers = PHONE_LAND)
    fun settingsLandscapePhone() {
        val g = graph()
        seedProfile(g)
        themed {
            tv.enktel.app.ui.screens.SettingsScreen(
                g,
                androidx.navigation.compose.rememberNavController(),
            )
        }
        settle()
        capture("settings-landscape")
    }

    @Test
    @Config(qualifiers = TV)
    fun settingsOnTelevision() {
        val g = graph()
        seedProfile(g)
        themed {
            tv.enktel.app.ui.screens.SettingsScreen(
                g,
                androidx.navigation.compose.rememberNavController(),
            )
        }
        settle()
        capture("settings-bigscreen")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun liveBrowser() {
        val g = graph()
        val p = seedProfile(g)
        seedChannels(g, p)
        seedSport(g, p)
        themed {
            tv.enktel.app.ui.live.ChannelBrowserScreen(
                g,
                androidx.navigation.compose.rememberNavController(),
            )
        }
        settle()
        capture("live-browser")
    }

    @Test
    @Config(qualifiers = TV)
    fun liveBrowserOnTelevision() {
        val g = graph()
        val p = seedProfile(g)
        seedChannels(g, p)
        seedSport(g, p)
        themed {
            tv.enktel.app.ui.live.ChannelBrowserScreen(
                g,
                androidx.navigation.compose.rememberNavController(),
            )
        }
        settle()
        capture("live-browser-bigscreen")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun movies() {
        val g = graph()
        val p = seedProfile(g)
        seedMovies(g, p)
        themed {
            tv.enktel.app.ui.vod.MoviesScreen(
                g,
                androidx.navigation.compose.rememberNavController(),
            )
        }
        settle()
        capture("movies")
    }

    @Test
    @Config(qualifiers = TV)
    fun moviesOnTelevision() {
        val g = graph()
        val p = seedProfile(g)
        seedMovies(g, p)
        themed {
            tv.enktel.app.ui.vod.MoviesScreen(
                g,
                androidx.navigation.compose.rememberNavController(),
            )
        }
        settle()
        capture("movies-bigscreen")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun more() {
        themed { tv.enktel.app.ui.screens.MoreScreen(onSelect = {}) }
        capture("more")
    }

    @Test
    @Config(qualifiers = TV)
    fun moreOnTelevision() {
        themed { tv.enktel.app.ui.screens.MoreScreen(onSelect = {}) }
        capture("more-bigscreen")
    }

    @Test
    @Config(qualifiers = TV)
    fun onboardingOnTelevision() {
        val g = graph()
        themed { tv.enktel.app.ui.screens.OnboardingScreen(g) {} }
        capture("onboarding-bigscreen")
    }

    @Test
    @Config(qualifiers = TV)
    fun downloadsOnTelevision() {
        val g = graph()
        val p = seedProfile(g)
        seedDownloads(g, p)
        themed {
            val nav = androidx.navigation.compose.rememberNavController()
            tv.enktel.app.ui.downloads.DownloadsScreen(g, nav)
        }
        capture("downloads-bigscreen")
    }
}
