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
class PlayerEngine(context: Context, http: OkHttpClient, bufferProfile: String) {

    private val bandwidthMeter = DefaultBandwidthMeter.getSingletonInstance(context)
    val trackSelector = DefaultTrackSelector(context)

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
    private var candidateQueue: MutableList<String> = mutableListOf()
    private var candidateLive = false
    private var candidateStartMs = 0L
    private var candidateSubUrl = ""
    /** Surfaced so the UI can show "trying an alternate stream source…"
     *  instead of a flat error while the fallback chain is still working. */
    val triedFallback = MutableStateFlow(false)

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
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(bw.min, bw.max, bw.play, bw.rebuf)
            // Keep 60 s behind the live/playhead so instant-rewinds inside DVR-style
            // catch-up don't force a re-fetch, and short backward skips stay smooth.
            .setBackBuffer(60_000, true)
            // Time-priority means the player refuses to eat into the buffered window
            // just because we downloaded "enough bytes" — better on high-bitrate 4K
            // where a small byte count still represents seconds of runway.
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val httpFactory = OkHttpDataSource.Factory(http).setUserAgent("EnktelTV/1.0")
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        val renderers = DefaultRenderersFactory(context)
            // PREFER favours the software extension decoders (AV1/VP9/Opus/FFmpeg)
            // when they're compiled in, then falls back to the SoC hardware path —
            // maximum codec breadth without giving up hardware acceleration.
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
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

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

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
                if (retries < 2 && lastUrl != null) {
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
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) { retries = 0; error.value = null }
                buffering.value = state == Player.STATE_BUFFERING
                push()
            }
        })
    }

    fun push() {
        stats.value = stats.value.copy(
            bandwidthEstimate = bandwidthMeter.bitrateEstimate,
            droppedFrames = dropped,
            bufferAheadMs = (player.totalBufferedDuration).coerceAtLeast(0),
        )
    }

    /** Play a single fixed URL — no fallback chain (used for M3U channels,
     *  VOD/catch-up assets that already resolved to one confirmed URL). */
    fun play(url: String, live: Boolean, startPositionMs: Long = 0, externalSubUrl: String = "") {
        candidateQueue = mutableListOf()
        triedFallback.value = false
        playInternal(url, live, startPositionMs, externalSubUrl)
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
        candidateQueue = urls.drop(1).toMutableList()
        candidateLive = live
        candidateStartMs = startPositionMs
        candidateSubUrl = externalSubUrl
        triedFallback.value = false
        playInternal(urls.first(), live, startPositionMs, externalSubUrl)
    }

    private fun playInternal(url: String, live: Boolean, startPositionMs: Long = 0, externalSubUrl: String = "") {
        lastUrl = url
        retries = 0
        dropped = 0
        error.value = null
        val builder = MediaItem.Builder().setUri(url).apply {
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

    fun tracksOf(type: Int): List<TrackChoice> {
        val out = ArrayList<TrackChoice>()
        player.currentTracks.groups.forEachIndexed { gi, group ->
            if (group.type != type) return@forEachIndexed
            for (ti in 0 until group.length) {
                if (!group.isTrackSupported(ti)) continue
                val f = group.getTrackFormat(ti)
                val name = buildString {
                    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }
                    append(f.label ?: lang ?: "Track ${out.size + 1}")
                    if (type == C.TRACK_TYPE_VIDEO && f.height > 0) append(" · ${f.height}p")
                    if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) append(" · ${f.channelCount}ch")
                }
                out += TrackChoice(name, gi, ti, group.isTrackSelected(ti))
            }
        }
        return out
    }

    fun selectTrack(type: Int, choice: TrackChoice?) {
        val params = player.trackSelectionParameters.buildUpon()
        if (choice == null) {
            params.setTrackTypeDisabled(type, true)
        } else {
            val group: Tracks.Group = player.currentTracks.groups[choice.groupIndex]
            params.setTrackTypeDisabled(type, false)
            params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, choice.trackIndex))
        }
        player.trackSelectionParameters = params.build()
    }

    fun release() {
        loudness?.release(); loudness = null
        player.release()
    }
}
