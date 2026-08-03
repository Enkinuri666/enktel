package tv.enktel.app.data.diag

/**
 * Results of the panel diagnostics.
 *
 * Every field is either a measured fact or an explicit "not determined".
 * Nothing here is inferred — the advisor in [SettingsAdvisor] is the only
 * place that turns facts into opinions, which keeps the probe layer honest
 * and the advice testable on its own.
 */

/** How a URL responded to a byte-range request. */
data class RangeSupport(
    val tested: Boolean = false,
    /** HEAD advertised `Accept-Ranges: bytes`. Advisory only — plenty of
     *  panels omit or lie about it while still honouring GET ranges. */
    val headAcceptsRanges: Boolean = false,
    /** HEAD answered at all. Some panels reject HEAD outright. */
    val headSupported: Boolean = false,
    /** Server answered 206 Partial Content to a ranged `GET`. This is the
     *  authoritative signal; HEAD is only corroboration. */
    val partialContent: Boolean = false,
    /** Total length known, either from Content-Range or Content-Length. */
    val totalBytes: Long = 0,
    /** A mid-file range came back at the offset we asked for. */
    val midFileSeekOk: Boolean = false,
    val httpCode: Int = 0,
    val error: String? = null,
) {
    /** True only when the *server* side of seeking is fully proven. */
    val usable: Boolean get() = partialContent && totalBytes > 0 && midFileSeekOk
}

/** What the first bytes of a stream actually are, regardless of its URL. */
data class ContainerFacts(
    val url: String = "",
    val declaredContentType: String = "",
    /** From magic bytes: MATROSKA / WEBM / MP4 / MPEG-TS / HLS / UNKNOWN. */
    val detected: String = "UNKNOWN",
    /** Set when the URL extension disagrees with the bytes on the wire. */
    val mismatch: Boolean = false,
    val matroska: Ebml.Head? = null,
    val range: RangeSupport = RangeSupport(),
    /** Content-Type is the one the spec expects for [detected]. */
    val mimeCorrect: Boolean = true,
    /** What the Content-Type should have been, when it is wrong. */
    val mimeExpected: String = "",
    /** `Transfer-Encoding: chunked` — no length, so no scrub bar. */
    val chunked: Boolean = false,
    val keepAlive: Boolean = false,
    /** Set when [detected] is HLS and the playlist parsed. */
    val hls: HlsInspector.Playlist? = null,
    /** Time to first byte, ms. Feeds the before/after comparison. */
    val ttfbMs: Long = 0,
    val error: String? = null,
)

/** Catch-up URL schemes an Xtream/M3U line can advertise. */
enum class CatchupScheme { UNKNOWN, XTREAM_TIMESHIFT, APPEND, SHIFT, FLUSSONIC, DEFAULT }

/**
 * What the Xtream API says the line carries, as distinct from what the bytes
 * on the wire turn out to be. The two disagreeing is itself a finding.
 */
data class LineStructure(
    val queried: Boolean = false,
    val liveCount: Int = 0,
    val vodCount: Int = 0,
    /** `container_extension` histogram from get_vod_streams, e.g. mkv=412, mp4=88. */
    val vodContainers: Map<String, Int> = emptyMap(),
    /** Channels advertising `tv_archive` / catchup-days. */
    val archiveCount: Int = 0,
    val error: String? = null,
) {
    /** The container the panel says most of its VOD library uses. */
    val dominantVodContainer: String?
        get() = vodContainers.maxByOrNull { it.value }?.key?.takeIf { it.isNotBlank() }
}

/** Whether the panel's catch-up/timeshift endpoint answers at all. */
data class CatchupFacts(
    val tested: Boolean = false,
    val scheme: CatchupScheme = CatchupScheme.UNKNOWN,
    val available: Boolean = false,
    val httpCode: Int = 0,
    /** Channels in the catalogue advertising an archive window. */
    val channelsWithArchive: Int = 0,
    val sampleUrl: String = "",
    val error: String? = null,
)

/** One setting the advisor wants changed, with the reason it thinks so. */
data class SettingChange(
    val key: String,
    val label: String,
    val current: String,
    val suggested: String,
    val reason: String,
) {
    val differs: Boolean get() = current != suggested
}

/** The settings the diagnostics actually touch. */
data class PlaybackSettings(
    val streamFormat: String = "hls",
    val bufferProfile: String = "balanced",
    val decoderMode: String = "hwplus",
    val vodForceMp4: Boolean = false,
    val liveShiftEnabled: Boolean = true,
    /** Blank = app default (VLC). */
    val customUserAgent: String = "",
)

/** A complete diagnostic pass. */
data class PanelReport(
    val profileId: Long = 0,
    val ranAtMs: Long = 0,
    val structure: LineStructure = LineStructure(),
    val epg: EpgOffset.Audit = EpgOffset.Audit(),
    val live: ContainerFacts? = null,
    val vod: ContainerFacts? = null,
    val catchup: CatchupFacts = CatchupFacts(),
    val settingsAtRun: PlaybackSettings = PlaybackSettings(),
    val changes: List<SettingChange> = emptyList(),
    val notes: List<String> = emptyList(),
    val error: String? = null,
) {
    val suggestedChanges: List<SettingChange> get() = changes.filter { it.differs }
    val healthy: Boolean get() = error == null && suggestedChanges.isEmpty()

    /** Compact score used to say whether a re-run improved anything. */
    val score: Int
        get() {
            var s = 0
            if (live?.error == null && live != null) s += 2
            if (vod?.error == null && vod != null) s += 2
            if (vod?.range?.usable == true) s += 3
            if (vod?.matroska?.seekable == true) s += 2
            if (catchup.available) s += 1
            s -= suggestedChanges.size
            return s
        }
}
