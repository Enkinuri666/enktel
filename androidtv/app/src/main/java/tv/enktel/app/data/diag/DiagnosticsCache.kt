package tv.enktel.app.data.diag

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the last diagnostic pass so re-opening the panel does not re-probe the
 * line.
 *
 * A full run costs several HTTP round trips against the user's own connection
 * cap — on a one-connection reseller line, re-running it casually can knock
 * playback off another device. So the result is kept and reused until either
 * the profile changes or the settings it was measured under change, both of
 * which genuinely invalidate it.
 *
 * Process-scoped rather than persisted: a stale verdict from yesterday about a
 * panel that has since been fixed is worse than no verdict.
 */
object DiagnosticsCache {

    /**
     * Cache identity: the line being measured plus the settings it was
     * measured under. Keyed on a hash of the actual stream URLs rather than
     * the profile id, because the same profile can resolve to different URL
     * shapes as the catalogue resyncs — and a verdict about the old shape is
     * not a verdict about the new one.
     */
    private data class Key(val lineHash: String, val settings: PlaybackSettings)

    /**
     * Stable fingerprint of the line under test. Includes the server, the
     * credentials that select the line, and the sample URLs actually probed.
     */
    fun lineHash(serverUrl: String, username: String, vararg sampleUrls: String?): String {
        val material = buildString {
            append(serverUrl.trimEnd('/')).append('|').append(username)
            sampleUrls.filterNotNull().sorted().forEach { append('|').append(stripVolatile(it)) }
        }
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * Drops query parameters that change per request (tokens, timestamps) so
     * the same logical stream hashes the same way across runs.
     */
    private fun stripVolatile(url: String): String = url.substringBefore('?')

    private val _last = MutableStateFlow<PanelReport?>(null)
    val last: StateFlow<PanelReport?> = _last

    /** The pass immediately before [last], kept so a re-run can be compared. */
    private val _previous = MutableStateFlow<PanelReport?>(null)
    val previous: StateFlow<PanelReport?> = _previous

    private var key: Key? = null

    /** How long a pass stays fresh. Long enough to cover reading the panel. */
    private const val TTL_MS = 10 * 60_000L

    /**
     * The cached report, or null when it would be misleading — different
     * profile, different settings, or simply too old.
     */
    fun cached(lineHash: String, settings: PlaybackSettings): PanelReport? {
        val r = _last.value ?: return null
        if (key != Key(lineHash, settings)) return null
        if (System.currentTimeMillis() - r.ranAtMs > TTL_MS) return null
        return r
    }

    fun store(report: PanelReport, lineHash: String, settings: PlaybackSettings) {
        // Only displace the baseline with a pass that actually measured
        // something — an errored run must not become the "before" half of a
        // before/after comparison.
        if (report.error == null) _previous.value = _last.value
        _last.value = report
        key = Key(lineHash, settings)
    }

    /** Drops everything — used when the user switches profile. */
    fun clear() {
        _last.value = null
        _previous.value = null
        key = null
    }
}
