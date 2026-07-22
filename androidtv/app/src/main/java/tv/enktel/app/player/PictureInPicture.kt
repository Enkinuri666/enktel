package tv.enktel.app.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational

/**
 * Enters PiP mode on the hosting activity. `playerActive` is set by the Live/VOD
 * player screens while they're on-screen so MainActivity.onUserLeaveHint() knows
 * whether pressing Home should hand off to PiP or just background the process.
 */
object PictureInPicture {
    @Volatile var playerActive: Boolean = false
    @Volatile var userWantsPipOnBack: Boolean = true
    @Volatile var pipCapable: Boolean = true

    fun enter(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!activity.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
        return try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            activity.enterPictureInPictureMode(params)
        } catch (_: Exception) {
            false
        }
    }
}
