package tv.enktel.app.player

import android.app.Activity
import android.os.Build
import android.view.Display
import android.view.WindowManager
import kotlin.math.abs

/**
 * Hardware-aware frame-rate matcher.
 *
 * Cinephile problem: TV boxes typically drive HDMI at a fixed 60 Hz, so
 * 24 fps films get 3:2 pull-down judder and 25/50 fps European sports
 * feeds show subtle motion tearing.  This helper flips the active display
 * mode to a refresh rate that cleanly divides into the source frame rate
 * (24 → 24 Hz, 25 → 50 Hz, 30 → 60 Hz, 50 → 50 Hz, 60 → 60 Hz) so motion
 * lands cadence-perfect.
 *
 * Two mechanisms are used depending on API level:
 *  - **API 30+** (Android 11 / R): `Window.attributes.preferredDisplayModeId`
 *    picks the exact mode by ID.  We enumerate `Display.supportedModes`,
 *    match by (width, height, refresh) with a small tolerance, and set
 *    the mode ID on the window's LayoutParams.
 *  - **API 23–29**: `preferredRefreshRate` was the earlier hint field.
 *    We fall back to setting that.
 *
 * Silently no-ops if the display doesn't expose modes or the frame rate
 * is below 20 (usually means "no signal yet") or above 121 (already
 * high-refresh; nothing to gain).
 */
object RefreshRateMatcher {

    /** Called by player screens when the video track's frame rate is known. */
    fun match(activity: Activity, sourceFps: Float) {
        if (sourceFps < 20f || sourceFps > 121f) return
        try {
            val window = activity.window ?: return
            val display: Display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display ?: return
            } else {
                @Suppress("DEPRECATION")
                (activity.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager)
                    .defaultDisplay
            }
            val targetHz = idealHz(sourceFps)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val modes = display.supportedModes
                if (modes.isEmpty()) return
                val current = display.mode
                // Prefer a mode with the same resolution as the current one to
                // avoid a jarring aspect-ratio flash when the panel resyncs.
                val best = modes
                    .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                    .minByOrNull { abs(it.refreshRate - targetHz) }
                    ?: modes.minByOrNull { abs(it.refreshRate - targetHz) }
                    ?: return
                if (best.modeId == current.modeId) return
                val lp = window.attributes
                lp.preferredDisplayModeId = best.modeId
                window.attributes = lp
            } else {
                val lp = window.attributes
                lp.preferredRefreshRate = targetHz
                window.attributes = lp
            }
        } catch (_: Throwable) { /* no-op — display swap is best-effort */ }
    }

    /** Reset back to whatever the OS picks by default.  Player screens call
     *  this in onDispose so the app UI isn't stuck at 24 Hz after playback. */
    fun reset(activity: Activity) {
        try {
            val window = activity.window ?: return
            val lp = window.attributes
            lp.preferredDisplayModeId = 0
            lp.preferredRefreshRate = 0f
            window.attributes = lp
        } catch (_: Throwable) {}
    }

    /**
     * Map source fps → HDMI refresh in Hz.  We favour integer multiples so
     * frame cadence lands 1:1 (24 → 24, 25 → 50) instead of relying on the
     * TV's own 3:2 pull-down.
     */
    private fun idealHz(fps: Float): Float = when {
        fps < 24.5f -> 24f
        fps < 26.5f -> 50f          // 25 fps → 50 Hz (2:2)
        fps < 31f  -> 60f           // 29.97 / 30 → 60 Hz (2:2)
        fps < 51f  -> 50f
        fps < 61f  -> 60f
        else       -> fps           // 100 / 120 — trust the source
    }
}
