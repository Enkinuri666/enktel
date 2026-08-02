package tv.enktel.app.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.util.EventLogger
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

data class TrackChoice(val name: String, val groupIndex: Int, val trackIndex: Int, val selected: Boolean)

data class StreamStats(
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val videoBitrate: Int = 0,
    val bandwidthEstimate: Long = 0,
    val droppedFrames: Int = 0,
    val bufferAheadMs: Long = 0,
    val decoder: String = "",
)

/**
 * Owns a tuned ExoPlayer instance. Buffer profiles trade zap speed vs. resilience;
 * playback errors trigger bounded auto-retry so flaky IPTV feeds recover on their own.
 */
@UnstableApi
class PlayerEngine(
    context: Context,
    private val http: OkHttpClient,
    bufferProfile: String,
    /** "hwplus" (default, EXTENSION_RENDERER_MODE_PREFER) | "hw"
     *  (EXTENSION_RENDERER_MODE_OFF, hardware-only) | any other value
     *  falls back to hwplus. Kept as a plain string so the setting flow
     *  can drive it without introducing a shared enum. */
    decoderMode: String = "hwplus",
    /** Override the profile's minimum buffer (ms). 0 = don't override. */
    minBufferOverrideMs: Int = 0,
    /** v1.26.0 — when true, force the AdaptiveTrackSelection to pin the top
     *  bitrate rendition and hold it. Used by Streaming Companion Mode so
     *  Discord viewers don't see quality flapping mid-stream. */
    lockToTopBitrate: Boolean = false,
) {

    private val bandwidthMeter = DefaultBandwidthMeter.getSingletonInstance(context)
    // Track selection: explicitly pair DefaultTrackSelector with an
    // AdaptiveTrackSelection factory so ABR (adaptive bitrate) is on by
    // default for HLS/DASH streams that publish multiple renditions. The
    // user can then override the adaptive pick per-track through the
    // Video/Audio quality dialog (see selectTrack / videoQualityOptions).
    val trackSelector = DefaultTrackSelector(
        context,
        androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection.Factory(),
    ).apply {
        if (lockToTopBitrate) {
            parameters = buildUponParameters()
                .setForceHighestSupportedBitrate(true)
                .build()
        }
    }

    val stats = MutableStateFlow(StreamStats())
    val error = MutableStateFlow<String?>(null)
    /** true while ExoPlayer is in BUFFERING state — surfaced so the UI can show
     *  the branded animated loader over the video pane. */
    val buffering = MutableStateFlow(false)
    /** Native frame rate reported by the current video track, or 0 if unknown.
     *  UI observes this so it can command a matching HDMI refresh rate. */
    val videoFrameRate = MutableStateFlow(0f)
    private var dropped = 0
    private var retries = 0
    private var lastUrl: String? = null
    // Fallback-chain state: remaining candidate URLs to try (in priority
    // order) if the current one keeps failing.  Populated by
    // playCandidates(); empty when playing a single fixed url via play().
    private var candidateQueue: MutableList<Candidate> = mutableListOf()
    /** What is loaded right now, so a container failure can re-read the same
     *  URL as HLS before giving up on it. */
    private var currentCandidate: Candidate? = null
    private var candidateLive = false
    private var candidateStartMs = 0L
    private var candidateSubUrl = ""
    /** Surfaced so the UI can show "trying an alternate stream source…"
     *  instead of a flat error while the fallback chain is still working. */
    val triedFallback = MutableStateFlow(false)

    /** True while a dropped live feed is being picked back up, so the UI can
     *  say "reconnecting" rather than showing a bare spinner. */
    val reconnecting = MutableStateFlow(false)

    private var isLiveStream = false
    private var liveReconnects = 0
    private var playingSinceMs = 0L
    private var bufferingSinceMs = 0L
    private val playerHandler by lazy { android.os.Handler(player.applicationLooper) }

    /**
     * Pick a dropped live feed back up.
     *
     * Backed off, because a panel that just hung up on us is often busy, and a
     * tight reconnect loop is how a client gets its IP throttled.
     */
    private fun reconnectLive() {
        if (liveReconnects >= MAX_LIVE_RECONNECTS) {
            error.value = "The stream keeps dropping — the panel may be overloaded or the line in use elsewhere"
            reconnecting.value = false
            return
        }
        liveReconnects++
        reconnecting.value = true
        playingSinceMs = 0L
        val delayMs = (1000L shl minOf(liveReconnects - 1, 4)).coerceAtMost(16_000L)
        playerHandler.postDelayed({
            try {
                player.seekToDefaultPosition()
                player.prepare()
                player.play()
            } catch (_: Throwable) { /* player torn down mid-wait */ }
        }, delayMs)
    }

    /**
     * Catches the other way a live feed dies: the socket stays open but the
     * panel stops sending, so ExoPlayer sits in BUFFERING indefinitely rather
     * than erroring. The app-wide read timeout is 180 s, which is three minutes
     * of frozen picture before anything happens.
     */
    private fun scheduleStallCheck() {
        if (!isLiveStream) return
        playerHandler.postDelayed({
            val since = bufferingSinceMs
            if (since != 0L &&
                System.currentTimeMillis() - since >= LIVE_STALL_MS &&
                player.playbackState == Player.STATE_BUFFERING
            ) {
                bufferingSinceMs = 0L
                reconnectLive()
            }
        }, LIVE_STALL_MS + 500)
    }
    /** MIME type to pin on the next MediaItem, bypassing ExoPlayer's own
     *  container auto-detection. Empty = let ExoPlayer figure it out. Set
     *  via [play]'s `forceMimeType` (used by the "Force MP4 fallback (VOD)"
     *  setting). Cleared automatically at the top of every new [play] call. */
    private var forcedMimeType: String = ""

    val player: ExoPlayer

    init {
        // Buffer profiles trade zap speed vs. resilience. "auto" scales the window
        // by device class (TV keeps a bigger cushion; phones keep it lean).
        val isTv = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        data class BufferWindow(val min: Int, val max: Int, val play: Int, val rebuf: Int)
        val bw = when (bufferProfile) {
            "low" -> BufferWindow(5_000, 20_000, 1_000, 2_000)
            "large" -> BufferWindow(30_000, 180_000, 3_500, 6_000)
            "auto" -> if (isTv) BufferWindow(20_000, 90_000, 2_500, 4_500)
                      else BufferWindow(15_000, 60_000, 2_000, 3_500)
            else -> BufferWindow(15_000, 60_000, 2_000, 3_500)
        }
        val effMin = if (minBufferOverrideMs > 0) minBufferOverrideMs.coerceAtMost(bw.max) else bw.min
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(effMin, bw.max, bw.play, bw.rebuf)
            // Keep 60 s behind the live/playhead so instant-rewinds inside DVR-style
            // catch-up don't force a re-fetch, and short backward skips stay smooth.
            .setBackBuffer(60_000, true)
            // Time-priority means the player refuses to eat into the buffered window
            // just because we downloaded "enough bytes" — better on high-bitrate 4K
            // where a small byte count still represents seconds of runway.
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Same UA as the OkHttp client uses everywhere else — see
        // tv.enktel.app.DEFAULT_UA for the rationale (Cloudflare / WAF /
        // Xtream panel bot rules answer OkHttp's default UA with HTTP 407).
        val httpFactory = OkHttpDataSource.Factory(http).setUserAgent(tv.enktel.app.DEFAULT_UA)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        val extMode = when (decoderMode) {
            "hw" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            "hwplus", "on" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        }
        val renderers = DefaultRenderersFactory(context)
            // "hwplus" (default): favours software extension decoders
            // (AV1/VP9/Opus/FFmpeg) then falls back to SoC hardware —
            // maximum codec breadth without giving up HW acceleration.
            // "hw": extensions OFF, hardware-only — sharper on devices with
            // strong SoC decoders (Nvidia Shield, Fire Cube gen 3) that
            // don't need the software safety net.
            .setExtensionRendererMode(extMode)
            .setEnableDecoderFallback(true)
            // Route higher-tier audio (AC-3 / E-AC-3 / TrueHD / DTS) untouched to the receiver
            // where supported, so home-theatre pass-through works instead of software decode.
            .setEnableAudioFloatOutput(true)

        // Tunneled HW decoding on Android TV — feeds compressed samples straight to the SoC's
        // hardware decoder for lower latency + fewer dropped frames on 4K panels.
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTunnelingEnabled(isTv)
            .setPreferredAudioMimeTypes(
                androidx.media3.common.MimeTypes.AUDIO_E_AC3_JOC,
                androidx.media3.common.MimeTypes.AUDIO_E_AC3,
                androidx.media3.common.MimeTypes.AUDIO_AC3,
                androidx.media3.common.MimeTypes.AUDIO_TRUEHD,
                androidx.media3.common.MimeTypes.AUDIO_DTS_HD,
                androidx.media3.common.MimeTypes.AUDIO_DTS,
                androidx.media3.common.MimeTypes.AUDIO_OPUS,
                androidx.media3.common.MimeTypes.AUDIO_AAC,
            )
            // Prefer modern codecs when the source publishes multiple variants —
            // AV1 → HEVC → H.264 on the video side. When only one is offered this
            // is a no-op; when several exist the player picks the smallest bitrate
            // for the quality tier, which is the point of adaptive streaming.
            .setPreferredVideoMimeTypes(
                androidx.media3.common.MimeTypes.VIDEO_AV1,
                androidx.media3.common.MimeTypes.VIDEO_H265,
                androidx.media3.common.MimeTypes.VIDEO_VP9,
                androidx.media3.common.MimeTypes.VIDEO_H264,
            )
            .build()

        // Extractor factory — explicitly registers Matroska + MP4 + TS
        // extractors and turns on the permissive flags for each. On
        // DefaultExtractorsFactory the full order is:
        //   MP4 → FMP4 → Matroska/WebM → FLV → MPEG-TS → OGG → AAC → …
        // so MP4-labelled URLs that actually serve MKV on the wire are
        // picked up on the second attempt. We also explicitly whitelist
        // video/x-matroska MIME + .mkv extension routing on the media source
        // factory below so a MediaItem with mimeType = VIDEO_MATROSKA (from
        // the Force MP4 setting's sibling code path, if extended later)
        // routes to MatroskaExtractor without sniffing.
        val extractors = androidx.media3.extractor.DefaultExtractorsFactory()
            // Best-effort seeking inside constant-bitrate MPEG-TS streams —
            // the alternative is "seek always jumps to nearest keyframe
            // 30 s away" on live catch-up TS archives.
            .setConstantBitrateSeekingEnabled(true)
            // …and take it even when the container claims it can't seek.
            //
            // Xtream VOD is routinely a raw .ts or an MP4 whose index the panel
            // never serves, so the extractor reports "not seekable" and Media3
            // answers every seek by restarting from position zero. Estimating
            // the position from the bitrate is approximate — a seek can land a
            // second or two off on variable-bitrate content — but approximate
            // seeking is the entire feature, and being thrown back to the start
            // of a two-hour film is not a rounding error.
            .setConstantBitrateSeekingAlwaysEnabled(true)
            // Matroska has one flag worth flipping: disable cue-point seeking
            // fallback so ExoPlayer will *fall through* to raw sample-index
            // seeking when the MKV has no Cues element (common on live-DVR
            // captures). Media3 exposes this as FLAG_DISABLE_SEEK_FOR_CUES.
            .setMatroskaExtractorFlags(
                androidx.media3.extractor.mkv.MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES
            )
            .setTsExtractorFlags(
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractors)

        player = ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .setUsePlatformDiagnostics(false) // trims one Google Play Services dep on Fire TV
            .build()

        player.addAnalyticsListener(EventLogger())
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                dropped += droppedFrames
                push()
            }

            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                stats.value = stats.value.copy(decoder = decoderName)
            }

            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
            ) {
                stats.value = stats.value.copy(
                    width = format.width.coerceAtLeast(0),
                    height = format.height.coerceAtLeast(0),
                    frameRate = if (format.frameRate > 0) format.frameRate else stats.value.frameRate,
                    videoCodec = format.sampleMimeType.orEmpty().substringAfterLast('/'),
                    videoBitrate = format.bitrate.coerceAtLeast(0),
                )
                if (format.frameRate > 0) videoFrameRate.value = format.frameRate
            }

            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
            ) {
                stats.value = stats.value.copy(audioCodec = format.sampleMimeType.orEmpty().substringAfterLast('/'))
            }
        })

        player.addListener(object : Player.Listener {
            override fun onPlayerError(err: PlaybackException) {
                // A container/manifest error is a statement about the bytes, not
                // about the network: the same URL will produce the same
                // unparseable response every time. Retrying it twice before
                // moving on just makes the user wait ~6 s per candidate — and
                // with six candidates that is most of a minute staring at a
                // black screen before anything useful is tried.
                val deterministic = err.errorCode in DETERMINISTIC_SOURCE_ERRORS
                val hlsRetry = if (err.errorCode in CONTAINER_ERRORS) {
                    currentCandidate?.let { asHlsRetry(it) }
                } else null
                if (hlsRetry != null) {
                    // Same URL, read as HLS this time. Reactive rather than
                    // queued up front: reinterpreting a URL that 404'd is
                    // pointless, and doubling the candidate list would double
                    // how long a genuinely dead channel takes to report.
                    triedFallback.value = true
                    retries = 0
                    playInternal(hlsRetry, candidateLive, candidateStartMs, candidateSubUrl)
                } else if (!deterministic && retries < 2 && lastUrl != null) {
                    // Short in-place retry first — covers transient network
                    // blips without wasting time walking the fallback chain.
                    retries++
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                } else if (candidateQueue.isNotEmpty()) {
                    // In-place retries exhausted and we still have alternate
                    // URL shapes to try (see StreamUrlResolver) — this is
                    // what actually recovers from a panel that 404s on
                    // .m3u8 but happily serves raw .ts, or vice versa.
                    triedFallback.value = true
                    val next = candidateQueue.removeAt(0)
                    retries = 0
                    playInternal(next, candidateLive, candidateStartMs, candidateSubUrl)
                } else {
                    error.value = err.errorCodeName.removePrefix("ERROR_CODE_").replace('_', ' ')
                    // Every shape failed. Ask the panel what it is actually
                    // serving so the user gets a cause instead of a code —
                    // "PARSING CONTAINER UNSUPPORTED" tells them nothing they
                    // can act on, and the answer is usually an HTML error page
                    // about credentials or a connection limit.
                    diagnose(lastUrl)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        retries = 0
                        error.value = null
                        reconnecting.value = false
                        bufferingSinceMs = 0L
                        // A stream that has played for a decent stretch has
                        // proved itself; forgive its earlier stumbles so a
                        // multi-hour session doesn't slowly exhaust its
                        // reconnect budget and die on the last one.
                        if (playingSinceMs == 0L) playingSinceMs = System.currentTimeMillis()
                        else if (System.currentTimeMillis() - playingSinceMs > GOOD_RUN_MS) {
                            liveReconnects = 0
                            playingSinceMs = System.currentTimeMillis()
                        }
                        classifySeekSupport()
                    }
                    Player.STATE_BUFFERING -> {
                        if (bufferingSinceMs == 0L) bufferingSinceMs = System.currentTimeMillis()
                        scheduleStallCheck()
                    }
                    Player.STATE_ENDED -> {
                        // The one that made live channels "stop after a while".
                        //
                        // A live MPEG-TS feed is one long-lived HTTP response.
                        // When the panel closes it — session rotation, a load
                        // balancer recycling the backend, an idle timeout — the
                        // extractor simply sees EOF. ExoPlayer treats that as
                        // the media having *finished*: STATE_ENDED, no error,
                        // no listener callback anywhere in this class. So
                        // nothing reconnected and the picture just stopped,
                        // with the app convinced everything went fine.
                        if (isLiveStream) reconnectLive()
                    }
                    else -> Unit
                }
                buffering.value = state == Player.STATE_BUFFERING
                push()
            }
        })
    }

    /**
     * Asks the panel what it actually served, and rewrites [error] with a cause.
     *
     * "PARSING CONTAINER UNSUPPORTED" is ExoPlayer telling the user it didn't
     * recognise the bytes, which is true and completely unactionable. In
     * practice the bytes are nearly always one of three things, and each has a
     * different answer for the person holding the remote.
     */
    private fun diagnose(url: String?) {
        val target = url ?: return
        diagScope.launch {
            val hint = try {
                val req = okhttp3.Request.Builder()
                    .url(target)
                    .header("User-Agent", tv.enktel.app.DEFAULT_UA)
                    .header("Range", "bytes=0-2047")
                    .build()
                http.newCall(req).execute().use { r ->
                    val head = (r.body?.source()?.peek()?.readUtf8Line() ?: "").trim()
                    val body = r.body?.string()?.take(512).orEmpty()
                    val text = (head + " " + body).trim()
                    when {
                        r.code == 401 || r.code == 403 ->
                            "The panel refused this stream (HTTP ${r.code}) — check the account is active and not expired."
                        r.code >= 500 ->
                            "The panel returned a server error (HTTP ${r.code}) — it's likely overloaded."
                        r.code == 404 ->
                            "The panel has no stream at this address (404) — the channel list may be stale, try re-syncing."
                        text.startsWith("#EXTM3U", ignoreCase = true) ->
                            "The panel served an HLS playlist here. EnkTel retried it as HLS and that failed too — the playlist may point at segments the panel isn't serving."
                        text.startsWith("<", ignoreCase = true) ->
                            "The panel returned a web page instead of a stream — usually an expired account, " +
                                "a device/connection limit, or the line being used elsewhere."
                        text.isBlank() ->
                            "The panel accepted the request but sent no data — the stream is probably offline."
                        else ->
                            "The panel sent data EnkTel couldn't recognise as video. Try switching " +
                                "Settings → Playback → Stream format, or pick another channel."
                    }
                }
            } catch (_: Throwable) {
                "Couldn't reach the panel to find out why — check the connection."
            }
            error.value = hint
        }
    }

    private val diagScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    fun push() {
        stats.value = stats.value.copy(
            bandwidthEstimate = bandwidthMeter.bitrateEstimate,
            droppedFrames = dropped,
            bufferAheadMs = (player.totalBufferedDuration).coerceAtLeast(0),
        )
        // Mirror to the process-wide snapshot the system monitor reads, so
        // playback health is visible from Settings without this screen being
        // on top. See PlaybackTelemetry.
        PlaybackTelemetry.publish(stats.value)
    }

    /** Play a single fixed URL — no fallback chain (used for M3U channels,
     *  VOD/catch-up assets that already resolved to one confirmed URL).
     *  [forceMimeType] pins the container so ExoPlayer skips its own
     *  auto-detection (e.g. `MimeTypes.VIDEO_MP4` to strictly parse as MP4
     *  when the "Force MP4 fallback (VOD)" setting is on). */
    fun play(url: String, live: Boolean, startPositionMs: Long = 0, externalSubUrl: String = "", forceMimeType: String = "") {
        candidateQueue = mutableListOf()
        triedFallback.value = false
        forcedMimeType = forceMimeType
        liveReconnects = 0
        playInternal(Candidate(url, forceMimeType), live, startPositionMs, externalSubUrl)
    }

    /** A URL plus how to interpret it. See [expand] for why one URL can
     *  produce more than one candidate. */
    private data class Candidate(val url: String, val mimeType: String = "")

    /**
     * Turns each resolver URL into the interpretations actually worth trying.
     *
     * ExoPlayer picks a media source from the URL's extension. `.m3u8` routes
     * to HlsMediaSource; everything else falls to ProgressiveMediaSource, which
     * identifies the container by sniffing its bytes.
     *
     * That is fine until a panel serves an HLS playlist from a URL that doesn't
     * say `.m3u8` — extensionless `/live/user/pass/12345` is the common shape,
     * and plenty of panels answer `.ts` with a playlist too. **There is no HLS
     * extractor.** HLS is a media source, not a container, so sniffing `#EXTM3U`
     * matches nothing at all and playback fails with
     * PARSING_CONTAINER_UNSUPPORTED — deterministically, every time, no matter
     * how often it is retried. The fallback chain never recovered because it
     * only ever tried each URL once, unhinted.
     *
     * So any URL that isn't explicitly `.m3u8` gets a second attempt with the
     * HLS MIME type pinned, which routes it to HlsMediaSource regardless of
     * what the path looks like.
     */
    private fun asHlsRetry(current: Candidate): Candidate? {
        if (current.mimeType == androidx.media3.common.MimeTypes.APPLICATION_M3U8) return null
        val path = current.url.substringBefore('?').substringBefore('#')
        if (path.endsWith(".m3u8", ignoreCase = true)) return null
        return current.copy(mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8)
    }

    private fun initialCandidate(url: String): Candidate {
        val path = url.substringBefore('?').substringBefore('#')
        return if (path.endsWith(".m3u8", ignoreCase = true)) {
            Candidate(url, androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        } else {
            Candidate(url)
        }
    }

    /**
     * Play the first URL in [urls], falling through to the next candidate
     * if playback errors out after a couple of in-place retries.  This is
     * how the app recovers from a panel that serves one Xtream URL shape
     * (HLS, raw TS, extensionless, or the legacy no-`/live/` layout) but
     * 404s or resets the connection on the others — see
     * [tv.enktel.app.data.xtream.StreamUrlResolver].
     */
    fun playCandidates(urls: List<String>, live: Boolean, startPositionMs: Long = 0, externalSubUrl: String = "") {
        if (urls.isEmpty()) return
        val expanded = urls.distinct().map { initialCandidate(it) }
        candidateQueue = expanded.drop(1).toMutableList()
        candidateLive = live
        candidateStartMs = startPositionMs
        candidateSubUrl = externalSubUrl
        triedFallback.value = false
        liveReconnects = 0
        playInternal(expanded.first(), live, startPositionMs, externalSubUrl)
    }

    private fun playInternal(candidate: Candidate, live: Boolean, startPositionMs: Long = 0, externalSubUrl: String = "") {
        val url = candidate.url
        lastUrl = url
        currentCandidate = candidate
        // A candidate's own hint wins over the screen-level force (which only
        // the "Force MP4 fallback (VOD)" setting sets).
        if (candidate.mimeType.isNotBlank()) forcedMimeType = candidate.mimeType
        retries = 0
        dropped = 0
        error.value = null
        seekSupport.value = SeekSupport.UNKNOWN
        isLiveStream = live
        playingSinceMs = 0L
        bufferingSinceMs = 0L
        val builder = MediaItem.Builder().setUri(url).apply {
            // Pin the MIME type when the caller asked us to skip container
            // auto-detection (Force MP4 fallback etc). Media3 uses this
            // hint to select the extractor directly rather than sniffing.
            if (forcedMimeType.isNotBlank()) setMimeType(forcedMimeType)
            if (live) setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder().setMaxPlaybackSpeed(1.03f).build()
            )
            if (externalSubUrl.isNotBlank()) {
                val mime = when {
                    externalSubUrl.endsWith(".vtt", true) -> androidx.media3.common.MimeTypes.TEXT_VTT
                    externalSubUrl.endsWith(".ass", true) || externalSubUrl.endsWith(".ssa", true) -> androidx.media3.common.MimeTypes.TEXT_SSA
                    else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                }
                setSubtitleConfigurations(listOf(
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(externalSubUrl))
                        .setMimeType(mime)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                ))
            }
        }
        player.setMediaItem(builder.build(), if (startPositionMs > 0) startPositionMs else C.TIME_UNSET)
        player.prepare()
        player.playWhenReady = true
    }

    /** Enable/disable loudness normalization via Android's LoudnessEnhancer. */
    fun setLoudnessOn(on: Boolean) {
        loudness?.release(); loudness = null
        if (!on) return
        val sessionId = player.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        loudness = try {
            android.media.audiofx.LoudnessEnhancer(sessionId).apply { setTargetGain(600); enabled = true }
        } catch (_: Exception) { null }
    }

    private var loudness: android.media.audiofx.LoudnessEnhancer? = null

    /**
     * Dialogue boost — a multi-band DynamicsProcessing chain that lifts the
     * voice band (200–3400 Hz) so whisper-quiet dialogue stops getting
     * drowned by explosion-loud action tracks. Requires API 28 (P);
     * gracefully no-ops on older devices.
     *
     * "off" / "low" (+2 dB) / "medium" (+4 dB) / "high" (+6 dB).
     */
    fun setDialogueBoost(level: String) {
        dialogueFx?.release(); dialogueFx = null
        if (level == "off" || level.isBlank()) return
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return
        val sessionId = player.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        val gainDb = when (level) {
            "low" -> 2f
            "medium" -> 4f
            "high" -> 6f
            else -> 0f
        }
        if (gainDb == 0f) return
        dialogueFx = try {
            val cfg = android.media.audiofx.DynamicsProcessing.Config.Builder(
                android.media.audiofx.DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                /*channels*/ 2,
                /*preEqInUse*/ true, /*preEqBandCount*/ 3,
                /*mbcInUse*/ false, /*mbcBandCount*/ 0,
                /*postEqInUse*/ false, /*postEqBandCount*/ 0,
                /*limiterInUse*/ true,
            ).build()
            val fx = android.media.audiofx.DynamicsProcessing(0, sessionId, cfg)
            // Boost the ~200–3400 Hz voice band on both channels; leave the
            // low + high bands flat so bass rumble and cymbal air stay intact.
            for (ch in 0..1) {
                val preEq = fx.getPreEqByChannelIndex(ch)
                preEq.setEnabled(true)
                // Band 0: 0–200 Hz (flat)
                preEq.getBand(0).apply { setEnabled(true); setCutoffFrequency(200f); setGain(0f) }
                // Band 1: 200–3400 Hz (voice) → boost
                preEq.getBand(1).apply { setEnabled(true); setCutoffFrequency(3400f); setGain(gainDb) }
                // Band 2: 3400 Hz+ (flat)
                preEq.getBand(2).apply { setEnabled(true); setCutoffFrequency(20_000f); setGain(0f) }
                fx.setPreEqByChannelIndex(ch, preEq)
            }
            fx.enabled = true
            fx
        } catch (_: Throwable) { null }
    }

    private var dialogueFx: android.media.audiofx.DynamicsProcessing? = null

    /**
     * True when the loaded item can actually be seeked.
     *
     * Worth checking before every seek because Media3's failure mode is
     * actively hostile: `seekTo` on an item whose timeline says it isn't
     * seekable **seeks to the default position** — the start. So a user who
     * taps `+30s` on an Xtream MP4 whose panel serves no byte ranges, or a
     * `.ts` VOD with no index, gets thrown back to the beginning of the film.
     * That is not a hypothetical; it is what "+30s resets the stream entirely
     * from the start" means.
     */
    val seekable: Boolean
        get() = try {
            player.isCurrentMediaItemSeekable
        } catch (_: Throwable) {
            false
        }

    /** Why seeking is or isn't available on the loaded item. */
    enum class SeekSupport {
        UNKNOWN,
        OK,
        /** The server streams without a Content-Length, so nothing can map a
         *  timestamp to a byte offset. Unfixable client-side. */
        NO_LENGTH,
        /** The server won't serve byte ranges — same conclusion. */
        NO_RANGES,
        /** Ranges and length are both fine; this container just didn't publish
         *  an index. A different container from the same panel often will. */
        CONTAINER,
    }

    val seekSupport = MutableStateFlow(SeekSupport.UNKNOWN)

    /** True when another container shape is still queued to try. */
    val hasAlternateSource: Boolean get() = candidateQueue.isNotEmpty()

    /**
     * Advance to the next candidate on demand — for "try another source" when
     * the current one plays but can't be seeked.
     *
     * @return false when nothing else is left to try.
     */
    fun tryNextCandidate(): Boolean {
        if (candidateQueue.isEmpty()) return false
        val next = candidateQueue.removeAt(0)
        triedFallback.value = true
        retries = 0
        playInternal(next, candidateLive, candidateStartMs, candidateSubUrl)
        return true
    }

    /**
     * Work out *why* an item can't be seeked, so the UI can say something the
     * user can act on.
     *
     * The distinction matters because the three causes have different answers.
     * A stream with no Content-Length cannot be seeked by any player ever
     * written — there is no way to turn a timestamp into a byte offset — so
     * the honest advice is to download it. A container with no index, on a
     * panel that does serve ranges, is often fixed by asking the same panel
     * for the same film as `.mp4` instead of `.ts`.
     *
     * The screenshot that prompted this showed a known duration (2:45:34) on
     * an unseekable item, which is exactly the TS-without-a-length shape.
     */
    private fun classifySeekSupport() {
        if (seekable) { seekSupport.value = SeekSupport.OK; return }
        if (seekSupport.value != SeekSupport.UNKNOWN) return
        val target = lastUrl ?: return
        diagScope.launch {
            val verdict = try {
                val req = okhttp3.Request.Builder()
                    .url(target)
                    .header("User-Agent", tv.enktel.app.DEFAULT_UA)
                    .header("Range", "bytes=0-1")
                    .header("Accept-Encoding", "identity")
                    .get()
                    .build()
                http.newCall(req).execute().use { r ->
                    val contentRange = r.header("Content-Range").orEmpty()
                    val totalKnown = contentRange.substringAfterLast('/', "")
                        .trim().toLongOrNull()?.takeIf { it > 0 } != null ||
                        (r.header("Content-Length")?.toLongOrNull() ?: 0L) > 2L
                    // Drain so the socket goes back to the pool clean.
                    try { r.body?.bytes() } catch (_: Throwable) {}
                    when {
                        r.code != 206 -> SeekSupport.NO_RANGES
                        !totalKnown -> SeekSupport.NO_LENGTH
                        else -> SeekSupport.CONTAINER
                    }
                }
            } catch (_: Throwable) {
                SeekSupport.UNKNOWN
            }
            seekSupport.value = verdict
        }
    }

    /**
     * Seek to an absolute position, refusing rather than restarting when the
     * item isn't seekable.
     *
     * @return false when the seek was refused, so the caller can say why
     *   instead of silently doing something destructive.
     */
    fun seekToSafe(positionMs: Long): Boolean {
        if (!seekable) return false
        val dur = player.duration
        val clamped = if (dur > 0) positionMs.coerceIn(0, dur) else positionMs.coerceAtLeast(0)
        player.seekTo(clamped)
        return true
    }

    /** Relative seek — the transport buttons and remote keys route through here. */
    fun seekBySafe(deltaMs: Long): Boolean =
        seekToSafe(player.currentPosition.coerceAtLeast(0) + deltaMs)

    fun tracksOf(type: Int): List<TrackChoice> {
        val out = ArrayList<TrackChoice>()
        player.currentTracks.groups.forEachIndexed { gi, group ->
            if (group.type != type) return@forEachIndexed
            for (ti in 0 until group.length) {
                if (!group.isTrackSupported(ti)) continue
                val f = group.getTrackFormat(ti)
                val name = buildString {
                    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }
                    // Video quality picker prefers "1080p · 5.2 Mbps · H.265"
                    // over the format's raw label (which is usually blank on
                    // Xtream). Audio picker gets language/channels; text
                    // stays as-is.
                    if (type == C.TRACK_TYPE_VIDEO) {
                        val res = if (f.height > 0) "${f.height}p" else null
                        val br = if (f.bitrate > 0) "%.1f Mbps".format(f.bitrate / 1_000_000.0) else null
                        val codec = f.sampleMimeType?.substringAfterLast('/')?.uppercase()
                        val parts = listOfNotNull(res, br, codec).filter { it.isNotBlank() }
                        if (parts.isNotEmpty()) append(parts.joinToString(" · "))
                        else append(f.label ?: "Track ${out.size + 1}")
                    } else {
                        append(f.label ?: lang ?: "Track ${out.size + 1}")
                        if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) append(" · ${f.channelCount}ch")
                    }
                }
                out += TrackChoice(name, gi, ti, group.isTrackSelected(ti))
            }
        }
        return out
    }

    /** Apply a manual track override, or clear the override to fall back
     *  to the AdaptiveTrackSelection factory. For video specifically,
     *  `choice == null` means "let ExoPlayer pick adaptively" (does NOT
     *  disable video); text tracks are actually disabled when null. */
    fun selectTrack(type: Int, choice: TrackChoice?) {
        val params = player.trackSelectionParameters.buildUpon()
        if (choice == null) {
            if (type == C.TRACK_TYPE_VIDEO) {
                // Clear any prior override and unlock adaptive selection —
                // this is how the "Auto (adaptive)" row in the quality
                // dialog returns the player to normal ABR behaviour.
                params.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                params.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            } else {
                params.setTrackTypeDisabled(type, true)
            }
        } else {
            val group: Tracks.Group = player.currentTracks.groups[choice.groupIndex]
            params.setTrackTypeDisabled(type, false)
            params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, choice.trackIndex))
        }
        player.trackSelectionParameters = params.build()
    }

    fun release() {
        playerHandler.removeCallbacksAndMessages(null)
        loudness?.release(); loudness = null
        dialogueFx?.release(); dialogueFx = null
        diagScope.cancel()
        player.release()
    }

    private companion object {
        /** Reconnect attempts before a live feed is declared dead. */
        const val MAX_LIVE_RECONNECTS = 8
        /** Buffering longer than this on live means the feed has stalled. */
        const val LIVE_STALL_MS = 25_000L
        /** Play cleanly for this long and the reconnect budget is restored. */
        const val GOOD_RUN_MS = 120_000L

        /**
         * Errors that describe the *content*, not the connection. Retrying the
         * identical URL cannot change the answer, so the fallback chain should
         * move on immediately rather than burning two attempts first.
         */
        /** Container/manifest failures specifically — the ones where the same
         *  URL may still be playable if interpreted as HLS instead. */
        val CONTAINER_ERRORS = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        )

        val DETERMINISTIC_SOURCE_ERRORS = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        )
    }
}
