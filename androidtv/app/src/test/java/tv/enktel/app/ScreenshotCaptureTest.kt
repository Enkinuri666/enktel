package tv.enktel.app

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.DownloadEntry
import tv.enktel.app.data.db.Profile
import tv.enktel.app.ui.theme.EnktelTheme
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
 * `@Ignore` by design: it writes files rather than asserting anything, takes
 * far longer than the rest of the suite, and exists to be run deliberately —
 *
 *     ./gradlew :app:testMobileDebugUnitTest --tests '*ScreenshotCaptureTest' \
 *         -Denktel.screenshots=1
 *
 * Point [OUT_DIR] wherever the guide is being built.
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

    private fun themed(content: @Composable () -> Unit) {
        rule.setContent { EnktelTheme { Box { content() } } }
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
                    sortIdx = i,
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
