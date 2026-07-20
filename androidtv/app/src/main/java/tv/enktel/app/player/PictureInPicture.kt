package tv.enktel.app.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational

/** Enters PiP mode on the hosting activity, with a 16:9 aspect ratio. No-op below Android 8. */
object PictureInPicture {
    fun enter(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
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
