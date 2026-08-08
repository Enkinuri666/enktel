package tv.enktel.app.data.diag

import android.app.Application
import android.content.Context
import android.os.Build
import tv.enktel.app.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records the stack trace of a fatal crash so it survives the process dying.
 *
 * ### Why this exists
 *
 * A tester reported that the mobile build crashed on launch while the TV build
 * was fine. Between us we could not produce a stack trace: the app was dead
 * before any screen could show one, `adb` is not something most testers have
 * set up, and reading the code found several *plausible* causes and no way to
 * choose between them. Guessing at a launch crash and shipping the guess is
 * how a bad afternoon becomes a bad week — a fix that changes nothing is
 * indistinguishable from a fix that works until the next report arrives.
 *
 * So the app now keeps its own black box. The handler is installed first thing
 * in `Application.onCreate`, before the object graph is built, because a crash
 * during startup is exactly the case that needs catching.
 *
 * ### Two copies, deliberately
 *
 * `filesDir` is private and readable by the app itself, which is what lets
 * Settings show the last crash on the next launch. `getExternalFilesDir` is
 * reachable over USB or from any file manager at
 * `Android/data/<package>/files/last-crash.txt` with no root and no adb — the
 * only route that works when the app cannot start at all, which is precisely
 * when the trace matters most.
 *
 * ### What it does not do
 *
 * It does not send anything anywhere. The file sits on the device until
 * somebody chooses to share it. A crash reporter that phones home would be a
 * different feature with different consent attached, and this is not it.
 */
object CrashLog {

    private const val FILE_NAME = "last-crash.txt"
    private const val MAX_BYTES = 64 * 1024

    /**
     * Chains rather than replaces. The platform handler is what shows the
     * "app has stopped" dialog and reports to the system — swallowing it would
     * trade a visible crash for a silent freeze, which is worse.
     */
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The last recorded crash, or null when the app has not crashed. */
    fun read(ctx: Context): String? =
        runCatching { File(ctx.filesDir, FILE_NAME).takeIf { it.exists() }?.readText() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    /** Where a tester can find the copy they can actually get at. */
    fun externalPath(ctx: Context): String? =
        runCatching { File(ctx.getExternalFilesDir(null), FILE_NAME).absolutePath }.getOrNull()

    fun clear(ctx: Context) {
        runCatching { File(ctx.filesDir, FILE_NAME).delete() }
        runCatching { File(ctx.getExternalFilesDir(null), FILE_NAME).delete() }
    }

    private fun write(app: Application, thread: Thread, error: Throwable) {
        val when_ = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.UK).format(Date())
        // Everything a bug report needs, so nobody has to be asked for it
        // afterwards — the round trip costs more than the lines do.
        val text = buildString {
            appendLine("EnkTel crash report")
            appendLine("===================")
            appendLine("when      : $when_")
            appendLine("app       : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.FLAVOR}")
            appendLine("device    : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android   : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("abis      : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("thread    : ${thread.name}")
            appendLine()
            appendLine(error.stackTraceToString())
            // The cause chain is where the answer usually is, and
            // stackTraceToString already walks it — but an UnsatisfiedLinkError
            // or a resource failure often carries its detail only on the root.
            var root: Throwable = error
            while (root.cause != null && root.cause !== root) root = root.cause!!
            if (root !== error) {
                appendLine()
                appendLine("root cause: ${root::class.java.name}: ${root.message}")
            }
        }.take(MAX_BYTES)

        runCatching { File(app.filesDir, FILE_NAME).writeText(text) }
        runCatching {
            app.getExternalFilesDir(null)?.let { File(it, FILE_NAME).writeText(text) }
        }
    }
}
