package tv.enktel.app

import android.content.ActivityNotFoundException
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import tv.enktel.app.ui.components.launchOr
import java.io.File

/**
 * The app asks Android for things Android is allowed to refuse, and a refusal
 * is an exception thrown at a call site that looks nothing like a risk.
 *
 * Two of those ended the app outright.
 *
 * ## A picker that is not there
 *
 * "Import file", "Export" and "Pick download folder" in Settings each open the
 * system document picker. A Fire TV stick does not have one, nor do a good
 * many Android TV boxes, and `launch` throws `ActivityNotFoundException` when
 * nothing answers. On a phone, where this gets tried, something always does.
 *
 * ## A foreground service past its allowance
 *
 * All three foreground services are `dataSync`. From Android 15 an app gets
 * six hours of that in any twenty-four. When it runs out mid-flight the system
 * calls `onTimeout` and kills the app if the service is still there a few
 * seconds later; when it has already run out, `startForeground` throws inside
 * `onStartCommand`. Neither was handled anywhere.
 *
 * Both mistakes are invisible on a developer's desk — the device has a picker,
 * and nobody records for six hours while testing — so the last two cases here
 * read the sources and fail the build on the next one.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SystemScreensTest {

    private class Launcher(private val failure: Exception?) : ActivityResultLauncher<Array<String>>() {
        var launched = 0
        override val contract: ActivityResultContract<Array<String>, *> = ActivityResultContracts.OpenDocument()
        override fun launch(input: Array<String>, options: ActivityOptionsCompat?) {
            failure?.let { throw it }
            launched++
        }
        override fun unregister() = Unit
    }

    @Test
    fun `a picker that exists is opened, and nothing is said`() {
        val launcher = Launcher(failure = null)
        var told = 0
        assertTrue(launcher.launchOr(arrayOf("*/*")) { told++ })
        assertEquals(1, launcher.launched)
        assertEquals(0, told)
    }

    @Test
    fun `a device with no picker is told so instead of crashing`() {
        val launcher = Launcher(ActivityNotFoundException("No Activity found to handle Intent"))
        var told = 0
        assertFalse(launcher.launchOr(arrayOf("*/*")) { told++ })
        assertEquals("the viewer hears about it exactly once", 1, told)
    }

    @Test
    fun `a launcher that has been unregistered is not a crash either`() {
        // ActivityResultRegistry throws this for a launcher whose owner has
        // gone — a button pressed during a configuration change.
        val launcher = Launcher(IllegalStateException("Attempting to launch an unregistered ActivityResultLauncher"))
        var told = 0
        assertFalse(launcher.launchOr(arrayOf("*/*")) { told++ })
        assertEquals(1, told)
    }

    // ---- the sources ----

    @Test
    fun `no screen launches a system picker unguarded`() {
        val files = sources() ?: return
        val declared = Regex("""val\s+(\w+)\s*=\s*(?:[\w.]+\.)?rememberLauncherForActivityResult""")
        val offenders = mutableListOf<String>()
        var launchers = 0
        for (file in files) {
            val text = file.readText()
            for (name in declared.findAll(text).map { it.groupValues[1] }.toSet()) {
                launchers++
                Regex("""\b${Regex.escape(name)}\.launch\(""").findAll(text).forEach { m ->
                    offenders += "${file.name}:${text.lineOf(m.range.first)}  $name.launch(…)"
                }
            }
        }
        assertTrue("the scan found no launchers at all, so it is checking nothing", launchers >= 5)
        assertTrue(
            "These open a system screen with a bare launch(), which throws on a device that has " +
                "no such screen (most Fire TV sticks have no file picker). Use launchOr { … } and " +
                "tell the viewer:\n  " + offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `every foreground service can be refused and can be told to stop`() {
        val root = sourceRoot() ?: return
        val manifest = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml",
            "androidtv/app/src/main/AndroidManifest.xml").map(::File).firstOrNull { it.isFile } ?: return
        val services = Regex("""<service\s[^>]*android:name="([^"]+)"[^>]*android:foregroundServiceType""")
            .findAll(manifest.readText()).map { it.groupValues[1] }.toList()
        assertTrue("the manifest declares foreground services; the scan found ${services.size}", services.size >= 3)

        val problems = mutableListOf<String>()
        for (name in services) {
            val file = File(root, name.removePrefix(".").replace('.', '/') + ".kt")
            assertTrue("$name is in the manifest but ${file.path} does not exist", file.isFile)
            val code = file.readText().lines().filterNot { it.trim().startsWith("//") || it.trim().startsWith("*") }
                .joinToString("\n")
            if (Regex("""(?<![\w.])startForeground\(""").containsMatchIn(code)) {
                problems += "${file.name}: calls startForeground() directly — it throws when Android " +
                    "refuses; use Foreground.enter() and handle a false"
            }
            if (!code.contains("Foreground.enter(")) {
                problems += "${file.name}: never enters the foreground through Foreground.enter()"
            }
            if (!Regex("""override\s+fun\s+onTimeout\(\s*\w+:\s*Int,\s*\w+:\s*Int\s*\)""").containsMatchIn(code)) {
                problems += "${file.name}: does not override onTimeout(startId, fgsType) — Android 15 " +
                    "kills the app when a dataSync service outlives its allowance"
            }
        }
        assertTrue(problems.joinToString("\n  ", prefix = "\n  "), problems.isEmpty())
    }

    private fun String.lineOf(index: Int) = substring(0, index).count { it == '\n' } + 1

    private fun sourceRoot(): File? = listOf(
        "src/main/java/tv/enktel/app",
        "app/src/main/java/tv/enktel/app",
        "androidtv/app/src/main/java/tv/enktel/app",
    ).map(::File).firstOrNull { it.isDirectory }

    private fun sources(): List<File>? =
        sourceRoot()?.walkTopDown()?.filter { it.isFile && it.extension == "kt" }?.toList()
}
