package tv.enktel.app

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Calls [onForeground] each time the app comes to the front — the first
 * activity to start on a cold launch, and every 0 → 1 return from the
 * background afterwards.
 *
 * A hand-rolled count rather than `androidx.lifecycle:lifecycle-process`:
 * ProcessLifecycleOwner is a whole dependency to learn one thing the activity
 * callbacks already report, and this app would rather not add one for it. The
 * started-activity count is the same signal ProcessLifecycleOwner derives
 * internally, debounced there by a handler we do not need because the one
 * caller debounces itself.
 */
class ForegroundWatcher(private val onForeground: () -> Unit) :
    Application.ActivityLifecycleCallbacks {

    private var started = 0

    override fun onActivityStarted(activity: Activity) {
        if (started == 0) onForeground()
        started++
    }

    override fun onActivityStopped(activity: Activity) {
        if (started > 0) started--
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
