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
    private var dropped = 0
    private var retries = 0
    private var lastUrl: String? = null

    val player: ExoPlayer

    init {
        val (minBuf, maxBuf, playBuf) = when (bufferProfile) {
            "low" -> Triple(5_000, 20_000, 1_000)
            "large" -> Triple(30_000, 120_000, 3_500)
            else -> Triple(15_000, 60_000, 2_000)
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuf, maxBuf, playBuf, playBuf * 2)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val httpFactory = OkHttpDataSource.Factory(http).setUserAgent("EnktelTV/1.0")
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
            // Route higher-tier audio (AC-3 / E-AC-3 / TrueHD / DTS) untouched to the receiver
            // where supported, so home-theatre pass-through works instead of software decode.
            .setEnableAudioFloatOutput(true)

        // Tunneled HW decoding on Android TV — feeds compressed samples straight to the SoC's
        // hardware decoder for lower latency + fewer dropped frames on 4K panels.
        val isTv = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTunnelingEnabled(isTv)
            .setPreferredAudioMimeTypes(
                androidx.media3.common.MimeTypes.AUDIO_E_AC3_JOC,
                androidx.media3.common.MimeTypes.AUDIO_E_AC3,
                androidx.media3.common.MimeTypes.AUDIO_AC3,
                androidx.media3.common.MimeTypes.AUDIO_AAC,
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
                if (retries < 4 && lastUrl != null) {
                    retries++
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
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

    fun play(url: String, live: Boolean, startPositionMs: Long = 0, externalSubUrl: String = "") {
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
