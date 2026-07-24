package tv.enktel.app.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Network-class-aware buffer sizing.
 *
 * Standard players use one static LoadControl regardless of whether the
 * user is on gigabit Ethernet, a hotel Wi-Fi, or a cellular hotspot.
 * That trades zap speed against resilience at both extremes: too small
 * a buffer on a flaky mobile hotspot causes constant rebuffers, and too
 * large a buffer on a fast wired connection wastes memory and hurts
 * channel-change latency.
 *
 * This helper watches the OS's active network and publishes one of three
 * suggested buffer profiles that the player engine reads at construction
 * time.  The user's manual setting in Settings > Playback still wins if
 * they've set one explicitly; this is the auto-mode default.
 *
 *   WIRED  → "large"   — aggressive pre-fetching, zap latency is fine.
 *   WIFI   → "balanced" — modest safety margin.
 *   MOBILE → "large"   — expect jitter, favour resilience over zap.
 *   NONE   → last observed.
 */
object NetworkClass {

    enum class Kind { UNKNOWN, WIRED, WIFI, MOBILE }

    private val _kind = MutableStateFlow(Kind.UNKNOWN)
    val kind: StateFlow<Kind> = _kind.asStateFlow()

    /** Suggested buffer profile string for [PlayerEngine] to consume. */
    val suggestedBufferProfile: String
        get() = when (_kind.value) {
            Kind.WIRED -> "large"
            Kind.WIFI -> "balanced"
            Kind.MOBILE -> "large"
            Kind.UNKNOWN -> "balanced"
        }

    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val cm = try {
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        } catch (_: Throwable) { return }
        _kind.value = classify(cm)
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { _kind.value = classify(cm) }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _kind.value = fromCaps(caps)
                }
                override fun onLost(network: Network) {
                    // Keep the last observed kind so a brief drop doesn't
                    // trigger a buffer resize in the middle of playback.
                }
            })
        } catch (_: Throwable) { /* API present but device refused */ }
    }

    private fun classify(cm: ConnectivityManager): Kind {
        val active = try { cm.activeNetwork } catch (_: Throwable) { null } ?: return Kind.UNKNOWN
        val caps = try { cm.getNetworkCapabilities(active) } catch (_: Throwable) { null } ?: return Kind.UNKNOWN
        return fromCaps(caps)
    }

    private fun fromCaps(caps: NetworkCapabilities): Kind = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Kind.WIRED
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Kind.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Kind.MOBILE
        else -> Kind.UNKNOWN
    }
}
