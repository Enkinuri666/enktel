package tv.enktel.app.player

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Cast helper — opens the OS-level "Cast" chooser so the user can pick any
 * Miracast display, Google Cast device (if Play Services is installed) or
 * DLNA renderer their vendor's stack exposes.  Deliberately avoids taking a
 * hard dep on Google Cast because our audience includes Fire TV / stock
 * AOSP devices where Play Services isn't present.
 *
 * Chains a small list of intents so we degrade gracefully across OEM
 * differences:
 *   1. ACTION_CAST_SETTINGS (stock Android's Cast panel)
 *   2. ACTION_WIFI_DISPLAY_SETTINGS (older + AOSP branches)
 *   3. Settings root (last-ditch — user still has one tap to find Cast).
 */
object CastToTv {
    fun open(context: Context): Boolean {
        val candidates = listOf(
            Intent("android.settings.CAST_SETTINGS"),
            Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
            Intent(Settings.ACTION_WIFI_SETTINGS),
        )
        for (i in candidates) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(i)
                return true
            } catch (_: Throwable) { /* try the next */ }
        }
        return false
    }
}
