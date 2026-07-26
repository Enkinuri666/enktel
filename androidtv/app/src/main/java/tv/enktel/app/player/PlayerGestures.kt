package tv.enktel.app.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager

/**
 * System hooks for the vertical-swipe volume / brightness gestures on the players.
 *
 * Split so the Composable can stay lean and testable: it just tells us "raise volume
 * by one tick" or "set brightness to 0.42" and this object talks to the OS.
 */
object PlayerGestures {

    fun currentVolumeFraction(context: Context): Float {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val v = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (v.toFloat() / max).coerceIn(0f, 1f)
    }

    /**
     * Set volume to an absolute fraction (0..1). Callers should snapshot
     * [currentVolumeFraction] at drag-start and pass `start + accumulated`
     * on each drag event — trying to increment by tiny deltas doesn't
     * work because Android's stream-volume API is integer-quantised
     * (typically 0–15) and `(current + 0.15).toInt()` truncates to zero,
     * meaning small drags used to move the volume nothing at all.
     */
    fun setVolumeFraction(context: Context, fraction: Float): Float {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        // Round to nearest step so a drag that lands at 4.5/15 hits step 5,
        // not step 4 — matches user expectation on a discrete-step slider.
        val target = (fraction.coerceIn(0f, 1f) * max + 0.5f).toInt().coerceIn(0, max)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        return (target.toFloat() / max).coerceIn(0f, 1f)
    }

    /** Legacy delta-based adjust kept for callers that still pass a per-tick
     *  delta. Prefer [setVolumeFraction] with a start-snapshot accumulator. */
    fun adjustVolume(context: Context, delta: Float): Float {
        return setVolumeFraction(context, currentVolumeFraction(context) + delta)
    }

    /** Read the player Activity's window brightness. If the window is set to
     *  BRIGHTNESS_OVERRIDE_NONE (–1) we fall back to the system-wide value so the first
     *  swipe doesn't jump to 0. */
    fun currentBrightness(activity: Activity): Float {
        val w = activity.window.attributes.screenBrightness
        if (w >= 0f) return w
        return try {
            val sysB = Settings.System.getInt(
                activity.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
            )
            (sysB / 255f).coerceIn(0.05f, 1f)
        } catch (_: Exception) {
            0.5f
        }
    }

    /** Set the player Activity's window brightness (0.05–1.0). Never goes fully dark
     *  so the user can always find the gesture area. */
    fun setBrightness(activity: Activity, value: Float): Float {
        val clamped = value.coerceIn(0.05f, 1f)
        val lp: WindowManager.LayoutParams = activity.window.attributes
        lp.screenBrightness = clamped
        activity.window.attributes = lp
        return clamped
    }
}
