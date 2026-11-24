package tv.enktel.app.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Android's built-in Live Caption.
 *
 * Live Caption (Android 10+, Pixel and many Android phones; absent on Fire OS
 * and most TV boxes) captions any audio playing on the device, on-device. The
 * platform does not let an app enable it, so the most an app can do is make
 * its audio eligible — Media3's AudioTrack output with USAGE_MEDIA already is —
 * and send the viewer to the switch.
 */
object LiveCaption {
    /** Live Caption's own page on Pixel / AOSP builds that have it. */
    private const val ACTION_LIVE_CAPTION = "com.android.settings.action.live_caption"

    fun openSettings(context: Context) {
        val intents = listOf(
            Intent(ACTION_LIVE_CAPTION),
            Intent(Settings.ACTION_CAPTIONING_SETTINGS),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        )
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }
}
