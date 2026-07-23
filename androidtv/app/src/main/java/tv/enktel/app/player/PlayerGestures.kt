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

    /** Move volume by [delta] as a fraction of the max stream volume. */
    fun adjustVolume(context: Context, delta: Float): Float {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val target = (current + (delta * max)).toInt().coerceIn(0, max)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        return (target.toFloat() / max).coerceIn(0f, 1f)
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
