package tv.enktel.app.player

import android.app.Activity
import android.app.PictureInPictureParams
import androidx.annotation.RequiresApi
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
            activity.enterPictureInPictureMode(params())
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Keeps the system's PiP parameters current while a player is on screen.
     *
     * From Android 12 the transition into PiP is animated from a source
     * rectangle, and without one the window visibly jumps rather than growing
     * out of the video. `setAutoEnterEnabled` additionally lets the system
     * start the transition as the user begins the gesture instead of after it
     * completes, which is what makes swiping up feel immediate rather than
     * stuttery. Neither was set, so PiP worked but looked broken on every
     * modern device.
     */
    fun update(activity: Activity, videoBounds: android.graphics.Rect? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        sourceRect = videoBounds ?: sourceRect
        try { activity.setPictureInPictureParams(params()) } catch (_: Exception) {}
    }

    /** Last known on-screen bounds of the video surface. */
    @Volatile private var sourceRect: android.graphics.Rect? = null

    // Every caller gates on SDK_INT >= O first; the annotation is what tells
    // lint that, since the checks live in enter()/update() rather than here.
    @RequiresApi(Build.VERSION_CODES.O)
    private fun params(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        sourceRect?.takeIf { !it.isEmpty }?.let { b.setSourceRectHint(it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Only when a player is actually up — auto-enter on a browse screen
            // would put the catalogue into a floating window on every swipe home.
            b.setAutoEnterEnabled(playerActive && userWantsPipOnBack)
            b.setSeamlessResizeEnabled(true)
        }
        return b.build()
    }
}
