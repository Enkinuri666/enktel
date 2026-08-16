package tv.enktel.app.player

import android.content.Context
import androidx.core.net.toUri
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
/**
 * Per-type buffer window. VOD prioritises stability (large buffer, tolerant of
 * ISP jitter), Live prioritises latency (small buffer, fast channel zap).
 */
data class BufferConfig(
    val minMs: Int,
    val maxMs: Int,
    val playbackMs: Int,
    val rebufferMs: Int,
)

@UnstableApi
class PlayerEngine(
    context: Context,
    private val http: OkHttpClient,
    bufferProfile: String,
    /** "hwplus" (default) | "hw" | "sw" — see [AudioDecoding], which owns the
     *  mapping onto Media3's extension renderer modes and the reasoning behind
     *  it. Kept as a plain string so the setting flow can drive it without
     *  introducing a shared enum.
     *
     *  A prebuilt FFmpeg audio decoder *is* bundled (app/libs, since v1.53.0),
     *  so these modes select genuinely different decoders. */
    decoderMode: String = AudioDecoding.HW_PLUS,
    /** Override the profile's minimum buffer (ms). 0 = don't override. */
    minBufferOverrideMs: Int = 0,
    /**
     * Whether this engine is for a live channel rather than a film.
     *
     * A constructor parameter, not a [play] argument, because the buffering
     * window is baked into the LoadControl and the LoadControl is fixed once
     * the ExoPlayer is built. PlaybackSession rebuilds the engine when the
     * kind changes; see BufferProfiles for why one window cannot serve both.
     */
    private val live: Boolean = false,
    /** v1.26.0 — when true, force the AdaptiveTrackSelection to pin the top
     *  bitrate rendition and hold it. Used by Streaming Companion Mode so
     *  Discord viewers don't see quality flapping mid-stream. */
    lockToTopBitrate: Boolean = false,
    /** v1.50.0 — per-type buffer overrides. When non-null the engine was built
     *  with explicit VOD/Live windows from Settings and the legacy
     *  [bufferProfile] is ignored for the matching stream type. */
    vodBuffer: BufferConfig? = null,
    liveBuffer: BufferConfig? = null,
    /** Memory pool chunk size in bytes. 0 = default (16 KB). Larger values
     *  (e.g. 2 MB) reduce allocator overhead for 4K and large MKV streams. */
    allocatorSizeBytes: Int = 0,
    /**
     * Tunneled hardware playback, where the device supports it.
     *
     * Only ever applies on the television build. Configurable because tunneling
     * is one of two things that differ between the TV and mobile builds, and
     * whether a given SoC handles it cleanly for a given codec is not something
     * the app can determine — only try.
     */
    tunneling: Boolean = true,
    /**
     * Closed-caption mode — one of [ClosedCaptions.MODES].
     *
     * Off leaves subtitle handling exactly as it was, so VOD subtitle tracks and
     * external subtitle files are untouched. On, it changes two things: which
     * caption formats the MPEG-TS extractor is willing to expose, and whether an
     * untagged text track may be auto-selected.
     */
    captionMode: String = ClosedCaptions.OFF,
) {
    private var streamHttpFactory: OkHttpDataSource.Factory? = null

    /**
     * Per-channel User-Agent, from `#EXTVLCOPT:http-user-agent=`.
     *
     * Some sources answer for exactly one User-Agent and 403 everything else,
     * which the single global UA cannot fix without breaking the rest of the
     * playlist. Blank restores the app default, so the override cannot leak
     * from one channel onto the next.
     */
    fun setStreamUserAgent(ua: String) {
        streamHttpFactory?.setUserAgent(ua.ifBlank { tv.enktel.app.DEFAULT_UA })
    }



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

    // Rendered-frame bookkeeping for the measured frame rate. See [push].
    /** Frame rate the container declared, 0 when it declared none. */
    private var declaredFps = 0f
    private var lastRenderedFrames = 0L
    private var lastRenderSampleNs = 0L
    private var measuredFps = 0f

    /**
     * The measured rate, once it has held still long enough to be believed.
     *
     * Published exactly once per stream, and that is not a nicety. The only
     * consumer of [videoFrameRate] is RefreshRateMatcher, which sets
     * `preferredDisplayModeId` — an HDMI mode change that blanks a television
     * for a second or two. Publishing a *measurement* every second turned that
     * into a recurring event: 24 and 25 fps sit one frame apart, the original
     * snap tolerance was 1.5, so the decision boundary fell at 24.5 and half a
     * frame of sampling noise walked back and forth across it. A tester saw a
     * stream play, cut out, recover and cut out again on a full 15 s buffer
     * with zero dropped frames — not buffering at all, the panel resyncing.
     *
     * So the rate is latched: agree with itself for [FrameRates.AGREE_SAMPLES]
     * consecutive seconds, publish once, and never again for this stream. A
     * declared rate still wins outright and needs none of this, because a
     * container does not change its mind mid-stream.
     */
    private var latchedFps = 0f
    private val fpsLatch = FrameRates.Latch()

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
     * Must be declared **above** `init`.
     *
     * Kotlin runs property initialisers and init blocks in declaration order,
     * so a property declared below `init` is still null while `init` executes.
     * This one is used by [watchNetwork], which `init` calls — with the
     * declaration further down the file it was dereferenced before it existed
     * and every PlayerEngine construction died with a NullPointerException,
     * i.e. the app crashed the moment anyone opened live TV or a film.
     */
    private val diagScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

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
     * Pick playback back up when the network returns.
     *
     * Switching Wi-Fi to mobile, or a router rebooting, kills the socket
     * underneath a live feed. Depending on timing that surfaces as an error, a
     * silent ENDED, or an indefinite buffer — and in the last two cases nothing
     * else here would ever retry. Watching the transport directly covers all
     * three, and reconnects the moment there is a network to reconnect on
     * rather than after a backoff that started while there wasn't one.
     */
    private fun watchNetwork() {
        diagScope.launch {
            var previous = tv.enktel.app.data.net.NetworkClass.kind.value
            tv.enktel.app.data.net.NetworkClass.kind.collect { now ->
                val regained = now != previous &&
                    now != tv.enktel.app.data.net.NetworkClass.Kind.UNKNOWN
                previous = now
                if (!regained || !isLiveStream) return@collect
                // Hop to the player's own thread before reading its state.
                // ExoPlayer throws on cross-thread access, and the catch that
                // used to wrap this swallowed the throw — so the check always
                // bailed out and network recovery never actually ran.
                playerHandler.post {
                    val state = try { player.playbackState } catch (_: Throwable) { return@post }
                    if (state == Player.STATE_READY) return@post
                    // A fresh network is a fresh chance, so don't let a budget
                    // spent on the old one prevent using it.
                    liveReconnects = 0
                    reconnectLive()
                }
            }
        }
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

    /**
     * The window this engine was actually built with.
     *
     * Read by Diagnostics. The settings screen can only show what was
     * *requested*; a profile is adjusted for live-versus-VOD, device class and
     * available memory before it reaches the player, and "the buffer setting
     * says 90 s but live is capped at 12" is exactly the kind of thing a
     * support conversation needs to be able to see rather than infer.
     */
    var activeWindow: BufferProfiles.Window? = null
        private set

    /** True when this engine was built for live playback. */
    val isLiveEngine: Boolean get() = live

    init {
        // Buffer profiles trade zap speed vs. resilience. "auto" scales the window
        // by device class (TV keeps a bigger cushion; phones keep it lean).
        val isTv = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        // A 1 GB Fire TV Stick Lite shares that gigabyte with the system while
        // decoding 1080p. Holding a three-minute 4K window there is an OOM
        // rather than a stall, so the ceiling is halved whatever the user
        // picked.
        val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)
            ?.isLowRamDevice == true ||
            Runtime.getRuntime().maxMemory() < 128L * 1024 * 1024
        // One engine plays one kind of stream, and `live` is already known here,
        // so only the matching window is needed: a "custom" profile supplies it
        // through vodBuffer/liveBuffer, anything else comes from BufferProfiles.
        val custom = if (live) liveBuffer else vodBuffer
        val bw = if (custom != null) {
            // Custom numbers come from settings-screen text fields, so they get
            // the same clamping a profile does. withMinOverride only normalises
            // when it is given a positive override, hence passing the window's
            // own minimum when the user has not set a separate floor —
            // DefaultLoadControl asserts rather than clamps, and an illegal
            // combination is a crash on the first frame.
            BufferProfiles.withMinOverride(
                BufferProfiles.Window(custom.minMs, custom.maxMs, custom.playbackMs, custom.rebufferMs),
                if (minBufferOverrideMs > 0) minBufferOverrideMs else custom.minMs,
            )
        } else {
            BufferProfiles.withMinOverride(
                BufferProfiles.window(bufferProfile, live = live, isTv = isTv, lowRam = lowRam),
                minBufferOverrideMs,
            )
        }
        activeWindow = bw
        // An explicit allocator rather than DefaultLoadControl's own: the
        // default chunk is 64 KB, and on a high-bitrate 4K stream that is a lot
        // of small allocations per second of video, which shows up as
        // micro-stutter. Bigger chunks, fewer of them.
        val allocator = androidx.media3.exoplayer.upstream.DefaultAllocator(
            true,
            if (allocatorSizeBytes > 0) allocatorSizeBytes else BufferProfiles.allocationChunkBytes(lowRam),
        )
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(bw.minMs, bw.maxMs, bw.playMs, bw.rebufMs)
            // Keep 60 s behind the playhead so instant-rewinds inside DVR-style
            // catch-up don't force a re-fetch, and short backward skips stay
            // smooth. Not on live: a back buffer there is 60 s of memory held
            // for a rewind the stream usually cannot serve anyway, on the
            // devices least able to spare it.
            .setBackBuffer(if (live) 0 else 60_000, true)
            // Time-priority means the player refuses to eat into the buffered window
            // just because we downloaded "enough bytes" — better on high-bitrate 4K
            // where a small byte count still represents seconds of runway.
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Same UA as the OkHttp client uses everywhere else — see
        // tv.enktel.app.DEFAULT_UA for the rationale (Cloudflare / WAF /
        // Xtream panel bot rules answer OkHttp's default UA with HTTP 407).
        val httpFactory = OkHttpDataSource.Factory(http).setUserAgent(tv.enktel.app.DEFAULT_UA)
        // Held so a per-channel override can be applied before the next play().
        // The factory reads its UA when it creates each data source, so setting
        // it here changes the next stream without rebuilding the player.
        streamHttpFactory = httpFactory
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        val extMode = AudioDecoding.extensionRendererMode(decoderMode)
        val renderers = DefaultRenderersFactory(context)
            // "hwplus" (default): the SoC's own decoders answer first and the
            // bundled FFmpeg extension sits behind them, so it only picks up
            // what the device genuinely cannot decode (AC-3 / E-AC-3 / DTS /
            // TrueHD on the boxes that ship without them).
            // "hw": extensions OFF, platform only.
            // "sw": FFmpeg ahead of the platform, for a box that advertises a
            // decoder and returns silence from it.
            //
            // The default used to be PREFER, which put FFmpeg ahead of the
            // platform for *every* codec it claims — Opus and AAC included —
            // and that is what made an HEVC + Opus title stutter on a Fire TV
            // Stick. AudioDecoding has the full account.
            .setExtensionRendererMode(extMode)
            .setEnableDecoderFallback(true)
            // Float PCM output stays off — see AudioDecoding.floatOutput for
            // why it was on, why that reason was not real, and what it cost.
            // Pass-through to a receiver is unaffected either way: it is
            // decided by AudioCapabilities, not by this.
            .setEnableAudioFloatOutput(AudioDecoding.floatOutput())
            // Let the platform's AudioTrack apply playback-speed changes.
            //
            // This matters here specifically because live playback runs a speed
            // control loop: the live MediaItem below is configured to drift
            // between 0.97× and 1.03× to hold its target offset behind the
            // edge. ExoPlayer normally implements that with Sonic, which works
            // on PCM and cannot touch a bitstream being passed through
            // untouched to a receiver — so on a DTS or DTS-HD track the video
            // takes the speed adjustment and the audio does not, and the two
            // walk apart a little further with every correction. That is the
            // progressive desync people report on a Fire TV Cube with DTS,
            // and it is worse on long live sessions precisely because the
            // corrections accumulate.
            //
            // With this set, the speed change is handed to AudioTrack, which
            // applies it to the stream the receiver is decoding, so the two
            // stay locked. Media3 falls back to Sonic wherever the platform
            // cannot oblige, so nothing is lost on devices that do not support
            // it.
            //
            // Renamed in media3: setEnableAudioTrackPlaybackParams is now a
            // deprecated one-line delegate to this. Same flag, same field, no
            // behaviour change — only the build warning goes away.
            .setEnableAudioOutputPlaybackParameters(true)

        // Tunneled HW decoding on Android TV — feeds compressed samples straight to the SoC's
        // hardware decoder for lower latency + fewer dropped frames on 4K panels.
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTunnelingEnabled(isTv && tunneling)
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
            // Prefer modern codecs when the source publishes several for the
            // same content — but only the ones this box decodes in hardware.
            // VideoCodecPreference owns the reasoning and the table; the short
            // version is that this preference outranks `usesHardwareAcceleration`
            // in DefaultTrackSelector's chain, so listing a codec the device can
            // only decode in software is an instruction to prefer the software
            // path over an available hardware one.
            //
            // Not a bitrate control, despite what the comment here used to say:
            // bitrate stays with the adaptive track selection.
            .setPreferredVideoMimeTypes(
                *VideoCodecPreference
                    .order(tv.enktel.app.data.net.DeviceProbe.hardwareVideoMimes())
                    .toTypedArray()
            )
            // Closed captions.
            //
            // Two settings, and the second one is the whole feature. The
            // language list is the obvious half. `selectUndeterminedTextLanguage`
            // is the half that was missing: captions carried inside the video as
            // CEA-608 have nowhere to record a language, so every one of them
            // arrives untagged, and a selector asked to prefer "en" rejects all
            // of them. The track was being found and then declined.
            //
            // Deliberately not touched when captions are off. This engine plays
            // VOD too, and VOD has real subtitle tracks, external subtitle files
            // and a picker of its own — none of which should change because a
            // live-TV setting exists.
            .apply {
                if (ClosedCaptions.enabled(captionMode)) {
                    val langs = ClosedCaptions.preferredLanguages(
                        captionMode,
                        java.util.Locale.getDefault().language,
                    )
                    setPreferredTextLanguages(*langs.toTypedArray())
                    setSelectUndeterminedTextLanguage(ClosedCaptions.allowUndetermined(captionMode))
                    setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                }
            }
            .build()

        // Extractor factory. DefaultExtractorsFactory's sniff order is
        //   MP4 → FMP4 → Matroska/WebM → FLV → MPEG-TS → OGG → AAC → …
        // so an MP4-labelled URL that actually serves MKV on the wire is
        // picked up on the third attempt. `initialCandidate` pins the MIME
        // type for known container extensions so that sniffing is skipped
        // entirely for .mkv/.webm.
        val extractors = androidx.media3.extractor.DefaultExtractorsFactory()
            // Approximate seeking in audio-only streams, and *only* those.
            //
            // These two used to be documented here as what made seeking work in
            // Xtream's raw .ts and index-less MP4 VOD. They do nothing of the
            // kind. `DefaultExtractorsFactory` passes the constant-bitrate flags
            // to exactly three extractors — ADTS, AMR and MP3 — and to no
            // others. Not MPEG-TS, not MP4, and (as the Matroska note below
            // already worked out for its own case) not Matroska either. The
            // claim was checkable against the factory the whole time.
            //
            // They are kept because they are right for the one thing they
            // actually reach: the radio directory serves MP3 and AAC over
            // Icecast/Shoutcast, those responses routinely arrive with no length
            // and no index, and estimating a position from the bitrate is the
            // only way to scrub them at all. "Always" is what extends that to
            // the ones whose container says it cannot seek, which is most of
            // them.
            //
            // What this leaves genuinely unsolved, stated plainly so nobody
            // reads the setting as cover for it: seeking in a raw .ts is
            // TsExtractor's own business, and it is not bitrate estimation. It
            // reads PCR timestamps from both ends of the stream and binary
            // searches — accurate, and better than anything a CBR estimate would
            // give — but only when `input.getLength()` is known, i.e. when the
            // panel answers with a Content-Length and honours range requests.
            // Without that it emits SeekMap.Unseekable and every seek does
            // restart the film from zero, exactly as the old comment described.
            // Nothing here rescues that; the fix lives at the HTTP layer.
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            // Matroska deliberately runs with *no* extra flags.
            //
            // This previously set FLAG_DISABLE_SEEK_FOR_CUES, on the belief that
            // it made the extractor fall through to sample-index seeking when an
            // MKV had no Cues element. It does the exact opposite. In
            // MatroskaExtractor the flag clears `seekForCuesEnabled`, and the
            // branch that reads it goes straight to `SeekMap.Unseekable` when it
            // is false:
            //
            //   seekForCuesEnabled = (flags & FLAG_DISABLE_SEEK_FOR_CUES) == 0
            //   if (!seekForCuesEnabled || cuesContentPosition == UNSET)
            //       output.seekMap(new SeekMap.Unseekable(durationUs))
            //
            // Almost every MKV writes its Cues at the end of the file, after the
            // first cluster, so the flag made essentially all Matroska VOD
            // unseekable — the player answered every seek by restarting from
            // zero. Nor did the constant-bitrate fallback below rescue it:
            // DefaultExtractorsFactory only passes the CBR flags to the MP3,
            // ADTS and AMR extractors, never to Matroska.
            //
            // Cleared, the extractor seeks to read the Cues element and emits a
            // real seek map, which is what turns a scrub into an HTTP range
            // request for the right byte offset.
            // Non-IDR keyframes: right for live, wrong for a film.
            //
            // The flag tells the MPEG-TS extractor to treat a non-IDR frame as a
            // point it may start decoding from. On live that is what you want —
            // a stream joined mid-GOP has no IDR to wait for, and waiting for
            // one is a second or two of black on every channel change.
            //
            // On VOD it is the wrong trade and it is visible. Starting mid-GOP
            // means decoding P-frames whose reference frames were never read, so
            // the picture opens as macroblocked mush and stays that way until
            // the next real keyframe arrives — which is exactly the "starts
            // playing and the video is choppy" report. A film has an IDR at the
            // start and nothing to gain from skipping it; the file is not going
            // anywhere while the extractor finds it.
            //
            // FLAG_DETECT_ACCESS_UNITS stays on both: it makes the extractor
            // find real frame boundaries rather than guess them, which is the
            // thing that stops audio and video drifting apart on a TS with no
            // reliable PTS.
            .setTsExtractorFlags(
                run {
                    var f = androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                    if (live) {
                        f = f or androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
                            .FLAG_ALLOW_NON_IDR_KEYFRAMES
                    }
                    // Note there is deliberately no FLAG_OVERRIDE_CAPTION_DESCRIPTORS
                    // here — see the caption formats below for why.
                    f
                }
            )
            // Caption formats to fall back on when the PMT does not describe any.
            //
            // Media3 works out which caption tracks to expose by parsing the
            // caption service descriptor (ATSC A/65, tag 0x86) out of the PMT.
            // When that descriptor is present it is the better source — it names
            // one format per service *and carries a three-letter language code
            // for each*, which is how a Croatian caption track arrives correctly
            // tagged `hrv` rather than untagged.
            //
            // When it is absent, `DefaultTsPayloadReaderFactory` returns whatever
            // list it was constructed with, and that list is empty unless someone
            // supplies one. Not "one default CEA-608 track" — none at all. An
            // IPTV panel rebuilds the PMT with only what it needs to play, so the
            // descriptor is routinely gone, and a channel captioned end to end in
            // the video's SEI data therefore presents as having no subtitles
            // whatsoever. That is the actual bug behind "live channels don't
            // support subtitles".
            //
            // So: supply both standards as the fallback, and deliberately do NOT
            // set FLAG_OVERRIDE_CAPTION_DESCRIPTORS. The flag would ignore the
            // descriptor even when it is there, throwing away the per-service
            // language tags for no gain. This way a described stream keeps its
            // languages and an undescribed one still gets its captions read.
            //
            // Live only: a VOD container's descriptors are usually intact, and
            // adding fallback formats there would put empty caption entries in a
            // subtitle picker that already has real tracks in it.
            .apply {
                if (live && ClosedCaptions.enabled(captionMode)) {
                    setTsSubtitleFormats(
                        ClosedCaptions.TS_CAPTION_MIME_TYPES.map { mime ->
                            androidx.media3.common.Format.Builder()
                                .setSampleMimeType(mime)
                                .build()
                        },
                    )
                }
            }
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractors).apply {
            // Absorb a dropped segment inside the media source instead of
            // letting it become a player error.
            //
            // The default schedule — three attempts backing off to five
            // seconds — was written for VOD, where waiting costs nothing but
            // time. On live it costs the entire cushion behind the edge, so
            // the retry lands after the player has already fallen out of the
            // window and the recovery becomes a visible reconnect. See
            // LiveRecovery for the whole chain.
            if (live) setLoadErrorHandlingPolicy(LiveRecovery.LiveLoadErrorPolicy())
        }

        // "Change the frame rate even if the switch is not seamless."
        //
        // Media3's own C class names only OFF and ONLY_IF_SEAMLESS; there is no
        // C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ALWAYS. The setter takes any of the
        // platform's Surface.CHANGE_FRAME_RATE_* values, and "always" is one of
        // those rather than one of Media3's.
        //
        // Guarded rather than referenced bare because the constant arrived in
        // API 30 with Surface.setFrameRate itself. Below that the mechanism does
        // not exist at all and the value is moot, so the seamless default stands
        // and RefreshRateMatcher's preferredDisplayModeId path does the work.
        val frameRateStrategy = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.view.Surface.CHANGE_FRAME_RATE_ALWAYS
        } else {
            C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
        }

        player = ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .setUsePlatformDiagnostics(false) // trims one Google Play Services dep on Fire TV
            // Ask the display to match the source frame rate even when the
            // switch is not seamless.
            //
            // Media3 already calls Surface.setFrameRate() on API 30+ — this is
            // not a missing feature — but its default,
            // VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS, declines the
            // switch whenever the panel would blank to make it. Television
            // hardware almost always blanks, so on the devices where matching
            // matters most the default quietly never fires, and 24 fps film
            // keeps being pulled onto a 60 Hz panel with the 3:2 cadence that
            // makes pans judder.
            //
            // ALWAYS trades a brief black frame at the start of playback for a
            // correct cadence throughout, which is the right way round for a
            // film. It is also the modern half of what RefreshRateMatcher does
            // by hand through preferredDisplayModeId; that path still covers
            // devices below API 30. See frameRateStrategy above.
            .setVideoChangeFrameRateStrategy(frameRateStrategy)
            // Hold a partial wake lock + Wi-Fi lock while playing.
            //
            // The WAKE_LOCK permission was already declared and never used, so
            // nothing stopped the device dozing mid-programme: on a Fire TV
            // Stick the Wi-Fi radio powers down on idle and the stream simply
            // dies. WAKE_MODE_NETWORK is the setting that keeps a network-fed
            // player alive, and ExoPlayer drops both locks the moment playback
            // stops, so it costs nothing while paused.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // Play nicely with other audio instead of fighting it. Without
            // focus handling a notification or a second app leaves two streams
            // talking over each other; with it, playback ducks and resumes.
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Pause when headphones are unplugged rather than switching to the
            // speaker at full volume.
            .setHandleAudioBecomingNoisy(true)
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
                // Held separately from stats.frameRate, which [push] also
                // writes: reading the published value back would make a
                // measured rate indistinguishable from a declared one, and the
                // measurement would then latch itself in as gospel.
                if (format.frameRate > 0) declaredFps = format.frameRate
                stats.value = stats.value.copy(
                    width = format.width.coerceAtLeast(0),
                    height = format.height.coerceAtLeast(0),
                    frameRate = if (declaredFps > 0f) declaredFps else stats.value.frameRate,
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

        watchNetwork()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(err: PlaybackException) {
                // What an error means depends on whether this is a file or a
                // channel. A 404 on a film says the film is not there; a 404 on
                // a live segment says it rolled off the sliding window while we
                // were asking for it, and the cure is to re-join at the edge.
                // Classifying the second as the first is what made live spend
                // its evening rewriting URLs instead of playing. See
                // LiveRecovery.
                // A container failure is worth one reinterpretation before the
                // chain is walked: drop a wrong extension-derived pin so the
                // bytes get sniffed, or read an extensionless URL as HLS. See
                // Routing.reinterpret.
                val hlsRetry = if (err.errorCode in CONTAINER_ERRORS) {
                    currentCandidate?.let { Routing.reinterpret(it) }
                } else null
                val action = LiveRecovery.action(
                    code = err.errorCode,
                    live = isLiveStream,
                    retries = retries,
                    hasHlsRetry = hlsRetry != null,
                    hasCandidates = candidateQueue.isNotEmpty(),
                )
                if (action == LiveRecovery.Action.HLS_RETRY && hlsRetry != null) {
                    // Same URL, read as HLS this time. Reactive rather than
                    // queued up front: reinterpreting a URL that 404'd is
                    // pointless, and doubling the candidate list would double
                    // how long a genuinely dead channel takes to report.
                    triedFallback.value = true
                    retries = 0
                    playInternal(hlsRetry, candidateLive, candidateStartMs, candidateSubUrl)
                } else if (action == LiveRecovery.Action.RETRY_IN_PLACE && lastUrl != null) {
                    // Re-join at the live edge. On live this is the correct
                    // recovery rather than a hopeful one, so falling behind the
                    // window is not charged to the budget — otherwise a channel
                    // that drops one segment an hour eventually runs out of
                    // attempts and is declared dead mid-programme.
                    if (!LiveRecovery.isFree(isLiveStream, err.errorCode)) retries++
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                } else if (action == LiveRecovery.Action.NEXT_CANDIDATE) {
                    // In-place retries exhausted and we still have alternate
                    // URL shapes to try (see StreamUrlResolver) — this is
                    // what actually recovers from a panel that 404s on
                    // .m3u8 but happily serves raw .ts, or vice versa.
                    triedFallback.value = true
                    val next = candidateQueue.removeAt(0)
                    retries = 0
                    playInternal(next, candidateLive, candidateStartMs, candidateSubUrl)
                } else if (lastUrl?.startsWith("rtmp://", ignoreCase = true) == true) {
                    // RTMP was dropped from the build — see the comment on the
                    // absent media3-datasource-rtmp dependency. Named here
                    // because the alternative is silence dressed as a codec
                    // fault: DefaultDataSource quietly falls back to the HTTP
                    // source for an unknown scheme, so the failure arrives as
                    // a parsing error about a container nobody chose. And
                    // diagnose() cannot help — it speaks HTTP, and would throw
                    // on the scheme before reaching the panel.
                    error.value = "RTMP streams are not supported by this build"
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
                    val head = (r.body.source().peek().readUtf8Line() ?: "").trim()
                    val body = r.body.string().take(512)
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

    /**
     * Frames actually put on the screen in the last second.
     *
     * The declared frame rate is not available on most of what this app plays.
     * MPEG-TS carries no frame rate at the container level and an HLS variant
     * playlist rarely advertises one, so `Format.frameRate` comes back unset
     * and the readout showed `0fps` — which looked like a stalled picture on a
     * stream that was playing perfectly well.
     *
     * That was not only cosmetic. `LivePlayerScreen` only asks
     * [RefreshRateMatcher] to switch the display when it has a frame rate, so
     * an undeclared rate meant no rate matching at all: 50 fps European
     * broadcasts rendering on a 60 Hz panel with no attempt to match it, which
     * is judder, and on a marginal buffer, dropped frames.
     *
     * Counting what the renderer actually released sidesteps the container
     * entirely. It is sampled rather than declared, so it is smoothed — a raw
     * per-second count visibly wobbles between 49 and 51 — and only trusted
     * while playing, since a paused player renders nothing and would otherwise
     * report a very confident zero.
     */
    private fun sampleFrameRate() {
        val counters = player.videoDecoderCounters
        if (counters == null || !player.isPlaying) {
            // Keep the last good reading and restart the window, so a pause
            // does not produce a fictitious 0 fps on resume.
            lastRenderSampleNs = 0L
            return
        }
        counters.ensureUpdated()
        val frames = counters.renderedOutputBufferCount.toLong()
        val now = System.nanoTime()
        if (lastRenderSampleNs != 0L && frames >= lastRenderedFrames) {
            val elapsedSec = (now - lastRenderSampleNs) / 1_000_000_000.0
            if (elapsedSec >= 0.5) {
                val instant = ((frames - lastRenderedFrames) / elapsedSec).toFloat()
                // Ignore an implausible spike from a decoder flush after a seek
                // or a track change, which dumps a burst of buffers at once.
                if (instant in 1f..240f) {
                    measuredFps =
                        if (measuredFps <= 0f) instant else measuredFps * 0.6f + instant * 0.4f
                }
            } else {
                return // too short a window to divide by
            }
        }
        lastRenderedFrames = frames
        lastRenderSampleNs = now
    }

    fun push() {
        sampleFrameRate()
        // The container's own figure wins when it has one — it is exact, and a
        // measurement can only approximate it. This fills the gap rather than
        // replacing it.
        // Published once, when the measurement has settled — see FrameRates.
        // The consumer changes the display mode, which blanks a television for
        // a second or two, so a rate that is still moving must not reach it.
        if (declaredFps <= 0f && latchedFps <= 0f) {
            val settled = fpsLatch.offer(measuredFps)
            if (settled > 0f) {
                latchedFps = settled
                videoFrameRate.value = settled
            }
        }
        // The readout follows the same rule as the display: show a figure once
        // it has settled, not while it is still moving. A number flickering
        // between 24 and 25 tells a tester nothing except that we are unsure,
        // and "—" says that more honestly.
        val effectiveFps = if (declaredFps > 0f) declaredFps else latchedFps
        stats.value = stats.value.copy(
            frameRate = effectiveFps,
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
    fun play(
        url: String,
        live: Boolean,
        startPositionMs: Long = 0,
        externalSubUrl: String = "",
        forceMimeType: String = "",
        /**
         * What to call this, for anything outside the app that asks.
         *
         * The MediaItem carried a URI and nothing else, so `player.mediaMetadata`
         * was empty and every system surface that reads it — the Fire TV
         * transport overlay, the lock screen, Alexa's "what's playing" — had a
         * stream to control and no idea what it was. Optional so the callers
         * that genuinely have no title (diagnostics probes) need not invent one.
         */
        title: String = "",
        subtitle: String = "",
        artworkUrl: String = "",
    ) {
        candidateQueue = mutableListOf()
        triedFallback.value = false
        forcedMimeType = forceMimeType
        liveReconnects = 0
        pendingMetadata = buildMetadata(title, subtitle, artworkUrl)
        playInternal(Candidate(url, forceMimeType), live, startPositionMs, externalSubUrl)
    }

    /**
     * Metadata for the item currently being prepared.
     *
     * Held on the engine rather than threaded through playInternal because the
     * fallback chain re-enters playInternal for each candidate, and the title
     * does not change just because the fourth URL shape is being tried.
     */
    private var pendingMetadata: androidx.media3.common.MediaMetadata? = null

    /** Null when the caller supplied nothing worth publishing. */
    private fun buildMetadata(
        title: String,
        subtitle: String,
        artworkUrl: String,
    ): androidx.media3.common.MediaMetadata? {
        if (title.isBlank() && subtitle.isBlank() && artworkUrl.isBlank()) return null
        return androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title.ifBlank { null })
            // Station reads better than artist on a live channel, and the
            // controllers that only know about artist still get something.
            .setStation(subtitle.ifBlank { null })
            .setArtist(subtitle.ifBlank { null })
            .setArtworkUri(artworkUrl.ifBlank { null }?.toUri())
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
    }

    /** A URL plus how to interpret it. See [expand] for why one URL can
     *  produce more than one candidate. */
    internal data class Candidate(val url: String, val mimeType: String = "")

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
    // Thin delegates — the logic lives in [Routing] so it can be unit-tested
    // without a Context.
    private fun asHlsRetry(current: Candidate): Candidate? = Routing.asHlsRetry(current)

    private fun initialCandidate(url: String): Candidate = Routing.initialCandidate(url)

    /**
     * Play the first URL in [urls], falling through to the next candidate
     * if playback errors out after a couple of in-place retries.  This is
     * how the app recovers from a panel that serves one Xtream URL shape
     * (HLS, raw TS, extensionless, or the legacy no-`/live/` layout) but
     * 404s or resets the connection on the others — see
     * [tv.enktel.app.data.xtream.StreamUrlResolver].
     */
    fun playCandidates(
        urls: List<String>,
        live: Boolean,
        startPositionMs: Long = 0,
        externalSubUrl: String = "",
        /** See the same parameters on [play]. */
        title: String = "",
        subtitle: String = "",
        artworkUrl: String = "",
    ) {
        if (urls.isEmpty()) return
        pendingMetadata = buildMetadata(title, subtitle, artworkUrl)
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
        // A new stream is a new measurement. Carrying the old frame rate over
        // would hand RefreshRateMatcher the previous channel's rate and switch
        // the display to it before a single frame of this one has rendered.
        declaredFps = 0f
        measuredFps = 0f
        latchedFps = 0f
        fpsLatch.reset()
        lastRenderedFrames = 0L
        lastRenderSampleNs = 0L
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
            // What this is, for anything outside the app that asks. Carried on
            // every candidate so walking the fallback chain does not blank the
            // title on the system's transport controls. See PlaybackSession.
            pendingMetadata?.let { setMediaMetadata(it) }
            if (live) setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    // Sit a fixed distance behind the live edge rather than
                    // wherever the panel's playlist lands us. See
                    // BufferProfiles.LIVE_TARGET_OFFSET_MS for why.
                    .setTargetOffsetMs(BufferProfiles.LIVE_TARGET_OFFSET_MS)
                    .setMaxPlaybackSpeed(1.03f)
                    // Without a minimum the offset control loop is one-way: it
                    // can speed up to close a gap but never ease off to open
                    // one, so a player that drifts up to the edge — which is
                    // the state being corrected here — has no way back to the
                    // target. 3 % either side is below the threshold where
                    // pitch correction becomes audible.
                    .setMinPlaybackSpeed(0.97f)
                    .build()
            )
            if (externalSubUrl.isNotBlank()) {
                val mime = when {
                    externalSubUrl.endsWith(".vtt", true) -> androidx.media3.common.MimeTypes.TEXT_VTT
                    externalSubUrl.endsWith(".ass", true) || externalSubUrl.endsWith(".ssa", true) -> androidx.media3.common.MimeTypes.TEXT_SSA
                    else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                }
                setSubtitleConfigurations(listOf(
                    MediaItem.SubtitleConfiguration.Builder(externalSubUrl.toUri())
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
                    try { r.body.bytes() } catch (_: Throwable) {}
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
                // The picker used to print whatever the stream happened to
                // carry: Format.label when the provider set one, and the raw
                // ISO code when it didn't. On an Xtream panel the label is
                // almost always null, so choosing a dub meant reading
                // "eng · 6ch" / "spa · 2ch" — codes and channel counts instead
                // of languages and layouts. See TrackLabels.
                val name = when (type) {
                    C.TRACK_TYPE_VIDEO -> buildString {
                        val res = if (f.height > 0) "${f.height}p" else null
                        val br = if (f.bitrate > 0) "%.1f Mbps".format(f.bitrate / 1_000_000.0) else null
                        val codec = f.sampleMimeType?.substringAfterLast('/')?.uppercase()
                        val parts = listOfNotNull(res, br, codec).filter { it.isNotBlank() }
                        if (parts.isNotEmpty()) append(parts.joinToString(" · "))
                        else append(f.label ?: "Track ${out.size + 1}")
                    }
                    C.TRACK_TYPE_AUDIO -> {
                        // Language leads, because it is what the viewer is
                        // choosing between. The provider's own label is kept as
                        // a fallback and never discarded — some panels do set
                        // something useful ("Director's commentary").
                        val lead = TrackLabels.languageName(f.language)
                            ?: f.label
                            ?: "Track ${out.size + 1}"
                        val parts = listOfNotNull(
                            lead,
                            TrackLabels.channelLayout(f.channelCount),
                            TrackLabels.codecName(f.sampleMimeType),
                        )
                        parts.joinToString(" · ")
                    }
                    else -> buildString {
                        append(
                            TrackLabels.languageName(f.language)
                                ?: f.label
                                ?: "Track ${out.size + 1}",
                        )
                        // A forced track carries only the subtitles for
                        // dialogue in another language, and picking one when
                        // you wanted full subtitles looks like the feature is
                        // broken. Saying so costs one word.
                        if (f.selectionFlags and C.SELECTION_FLAG_FORCED != 0) append(" · Forced")
                        if (f.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0) append(" · SDH")
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

    /**
     * Let go of the current stream, socket and all, before another is opened.
     *
     * ### Why a capped line needs this and an uncapped one does not
     *
     * On an Xtream panel an open stream is a session, and a line is sold with a
     * cap on them. A channel change momentarily wants two: the one being left
     * and the one being joined. On a cap of one there is no momentarily about
     * it — the panel sees a second request against a limit of one and answers
     * by refusing the new stream or by killing the old one, which is why a
     * single-connection line can fail to tune, or tune and then die, on a
     * channel change that would be unremarkable anywhere else.
     *
     * `setMediaItem` followed by `prepare` does eventually release the previous
     * source, but nothing says it does so before the next request goes out, and
     * the panel's own bookkeeping lags the socket regardless. This makes the
     * release explicit and ordered.
     *
     * ### The eviction is the part that is easy to miss
     *
     * Closing a response is not closing a socket. OkHttp keeps the connection
     * in its pool for reuse — which is normally the entire point, and is what
     * makes zapping fast — but a socket still open to a `/live` URL is how a
     * panel decides the session is still in use. So on a capped line the pool
     * has to be told to actually hang up. That costs the next request a fresh
     * handshake, which is exactly the trade worth making when the alternative
     * is not getting a picture at all.
     *
     * Deliberately not called on lines with room: throwing away warm
     * connections there would slow every zap to fix a problem those lines do
     * not have.
     */
    fun releaseStreamAndConnections() {
        runCatching {
            player.stop()
            player.clearMediaItems()
        }
        runCatching { http.connectionPool.evictAll() }
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

        /** Container/manifest failures specifically — the ones where the same
         *  URL may still be playable if interpreted as HLS instead. */
        val CONTAINER_ERRORS = setOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        )

        // The old DETERMINISTIC_SOURCE_ERRORS set lived here. It is now
        // LiveRecovery.DETERMINISTIC, minus the two IO codes it used to carry:
        // a 404 or a bad status is deterministic for a file and routine for a
        // live segment, and one set could not say both. See LiveRecovery.
    }

    /**
     * URL → container routing. Pure and stateless, so it is exercised directly
     * by StreamRoutingTest rather than only through a running player.
     */
    internal object Routing {
        /**
         * The same URL again, with the container pin removed so ExoPlayer sniffs.
         *
         * ### The bug this exists to fix
         *
         * [initialCandidate] pins the MIME type when a URL's extension names its
         * container, which skips the sniff chain and saves two wasted passes on
         * every Matroska open. That is a real saving and it rests on the
         * extension telling the truth.
         *
         * Xtream panels routinely serve `/movie/user/pass/1234.mp4` that is
         * Matroska on the wire, or the reverse. Pinned, the MP4 extractor is
         * handed MKV bytes and reports the container malformed — and because
         * [asHlsRetry] declines any URL whose extension names a container, there
         * was no second attempt. The fallback chain then walked to the next
         * candidate, which carried the same extension, got the same pin, and
         * failed identically. Every shape exhausted, "PARSING CONTAINER
         * MALFORMED" on screen, for a file that plays perfectly well the moment
         * anything looks at its actual bytes.
         *
         * Unpinning restores the sniff chain, which is what every player that
         * does not optimise this gets for free.
         *
         * Null when there is no pin to remove, which also makes the retry
         * terminate: an unpinned candidate cannot produce another one.
         */
        internal fun asSniffRetry(current: Candidate): Candidate? {
            if (current.mimeType.isBlank()) return null
            // An HLS pin comes from the playlist extension, not from a guess at a
            // container, and dropping it would route a playlist to the
            // progressive source.
            if (current.mimeType == androidx.media3.common.MimeTypes.APPLICATION_M3U8) return null
            return current.copy(mimeType = "")
        }

        /**
         * How to reinterpret a URL that failed to parse: drop a wrong container
         * pin first, and only then consider reading it as HLS.
         *
         * Order matters. A `.mkv` that is really MP4 needs the pin gone; an
         * extensionless URL that is really a playlist needs the HLS reading. The
         * two never apply to the same candidate, so this is a preference rather
         * than a race, but stating it keeps the caller from having to know that.
         */
        internal fun reinterpret(current: Candidate): Candidate? =
            asSniffRetry(current) ?: asHlsRetry(current)

        internal fun asHlsRetry(current: Candidate): Candidate? {
            if (current.mimeType == androidx.media3.common.MimeTypes.APPLICATION_M3U8) return null
            val path = current.url.substringBefore('?').substringBefore('#')
            if (path.endsWith(".m3u8", ignoreCase = true)) return null
            // A URL that names a progressive container is never an HLS playlist.
            // Xtream VOD is `/movie/user/pass/1234.mkv`, and reinterpreting that as
            // HLS is guaranteed to fail — it just adds a doomed round trip on top of
            // whatever the real error was, in the one place (VOD) where the user is
            // already staring at a black screen.
            if (containerMimeFor(path) != null) return null
            return current.copy(mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        }

        /**
         * MIME type for a URL whose extension names its container outright, or null
         * when the extension says nothing useful.
         *
         * Pinning this skips ExoPlayer's sniff chain (MP4 → FMP4 → Matroska → …),
         * which otherwise reads and rejects the head of the file once per candidate
         * extractor before reaching the right one. For Matroska that is two wasted
         * passes on every VOD open.
         */
        internal fun containerMimeFor(path: String): String? = when {
            path.endsWith(".mkv", true) -> androidx.media3.common.MimeTypes.VIDEO_MATROSKA
            path.endsWith(".webm", true) -> androidx.media3.common.MimeTypes.VIDEO_WEBM
            path.endsWith(".mp4", true) || path.endsWith(".m4v", true) ->
                androidx.media3.common.MimeTypes.VIDEO_MP4
            else -> null
        }

        internal fun initialCandidate(url: String): Candidate {
            val path = url.substringBefore('?').substringBefore('#')
            return when {
                path.endsWith(".m3u8", ignoreCase = true) ->
                    Candidate(url, androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                // Deliberately not applied to `.ts`: plenty of panels answer a .ts
                // URL with an HLS playlist, so that extension has to stay sniffed
                // and keep its HLS retry.
                else -> containerMimeFor(path)?.let { Candidate(url, it) } ?: Candidate(url)
            }
        }
    }
}
