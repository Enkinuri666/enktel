package tv.enktel.app.data.net

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Predictive thermal throttling guard.
 *
 * Fire TV Sticks and Android TV boxes tucked behind a hot TV routinely run
 * hot after an hour of 4K HDR or high-refresh sports.  The system will
 * eventually throttle the CPU / GPU on its own, which shows up as visible
 * stutters in the middle of the action.  This class watches the OS
 * thermal state and publishes a coarse [level] that background workers
 * can check before they schedule non-critical polling — EPG auto-refresh,
 * live-score ticker fetches, playlist pre-cache — so the app itself
 * cools the device down before the OS is forced to.
 *
 * Uses [PowerManager.OnThermalStatusChangedListener] on API 29+; on
 * older builds we permanently report NONE (there's no signal to act on).
 */
object ThermalGuard {

    enum class Level {
        /** No thermal pressure — full background activity allowed. */
        NONE,
        /** Warm — pause aggressive pre-caching, stretch score polls to 2×. */
        MILD,
        /** Hot — drop background EPG refresh, throttle score polls to 4×. */
        MODERATE,
        /** Critical — cancel background work until we cool down. */
        SEVERE;

        /** Convenience: true if any background poll should be skipped. */
        val shouldSkipBackground: Boolean get() = this >= MODERATE

        /** Multiplier applied to background polling intervals. */
        val pollIntervalMultiplier: Float get() = when (this) {
            NONE -> 1f
            MILD -> 2f
            MODERATE -> 4f
            SEVERE -> Float.POSITIVE_INFINITY
        }
    }

    private val _level = MutableStateFlow(Level.NONE)
    val level: StateFlow<Level> = _level.asStateFlow()

    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val pm = try {
            context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        } catch (_: Throwable) { return }
        // Seed with the current status so the guard is accurate from the
        // very first check.
        _level.value = fromStatus(pm.currentThermalStatus)
        try {
            pm.addThermalStatusListener(java.util.concurrent.Executors.newSingleThreadExecutor()) { status ->
                _level.value = fromStatus(status)
            }
        } catch (_: Throwable) { /* API present but device refused */ }
    }

    private fun fromStatus(status: Int): Level = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> Level.NONE
        PowerManager.THERMAL_STATUS_LIGHT -> Level.MILD
        PowerManager.THERMAL_STATUS_MODERATE -> Level.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE,
        PowerManager.THERMAL_STATUS_CRITICAL,
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN -> Level.SEVERE
        else -> Level.NONE
    }
}
