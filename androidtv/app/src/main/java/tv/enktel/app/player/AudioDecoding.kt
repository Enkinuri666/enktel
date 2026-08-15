package tv.enktel.app.player

/**
 * Which audio decoder gets first refusal on a track, and why the answer used to
 * be wrong on a Fire TV.
 *
 * ### The bug this exists to fix
 *
 * `app/libs/media3-decoder-ffmpeg-*.aar` was bundled in v1.53.0 for one reason:
 * a lot of IPTV boxes have no AC-3, E-AC-3, DTS or TrueHD decoder at all, and a
 * channel with picture and no sound is indistinguishable from a dead channel.
 * The FFmpeg extension decodes those in software so the channel plays.
 *
 * The mode chosen to switch it on was `EXTENSION_RENDERER_MODE_PREFER`, and
 * that is a much bigger lever than it looks. It does not mean "use FFmpeg when
 * nothing else can". It means *index the extension renderer ahead of the core
 * one*, and `DefaultTrackSelector` takes the first renderer that reports it
 * handles the format. So FFmpeg got asked first about every audio track in the
 * app, and it says yes to far more than the four codecs it was brought in for —
 * `FfmpegLibrary.getCodecName` claims AAC, MP3, Vorbis, FLAC, ALAC, A-law,
 * µ-law and **Opus** as well.
 *
 * Two consequences, both of which land on the same title:
 *
 *  1. **Opus was decoded on the CPU.** Every Fire TV ships
 *     `c2.android.opus.decoder` and never got asked. On a stick, the little
 *     cores are already carrying a 4K HEVC elementary stream out of the
 *     extractor and off the network; adding a software audio decode to the
 *     same budget is where the stuttering comes from. HEVC alone is fine and
 *     Opus alone is fine, which is why the report is always "HEVC *and* Opus".
 *  2. **Pass-through to a receiver was defeated.** `MediaCodecAudioRenderer`
 *     is the only renderer that can hand an AC-3 or E-AC-3 bitstream to the
 *     HDMI sink untouched, and it was never reached — FFmpeg answered
 *     `FORMAT_HANDLED` first and decoded to PCM instead. The exact opposite of
 *     the intent.
 *
 * ### The fix
 *
 * `EXTENSION_RENDERER_MODE_ON` indexes the extension renderer *after* the core
 * one. The platform decoder — and pass-through — wins wherever the device
 * actually has one, and FFmpeg still catches AC-3/DTS/TrueHD on the boxes that
 * don't, which was the whole point. Nothing about the bundled decoder changes;
 * only the order in which the two are asked.
 *
 * ### Everything here is pure
 *
 * No Android types and no player, so [AudioDecodingTest] can pin the mapping.
 * Media3's constants are mirrored rather than imported for the same reason;
 * the test asserts the numbers, and [PlayerEngine] is where they meet the real
 * `DefaultRenderersFactory`.
 */
object AudioDecoding {

    /**
     * Hardware first, FFmpeg as the safety net. The default, and what the
     * "HW+" chip in Settings selects.
     */
    const val HW_PLUS = "hwplus"

    /** Platform decoders only — the extension renderer is not built at all. */
    const val HW_ONLY = "hw"

    /**
     * FFmpeg first, ahead of the platform.
     *
     * The old `hwplus` behaviour, kept as an explicit choice because it is the
     * only answer for a box that *advertises* an AC-3 decoder and then returns
     * silence from it. Rare, un-detectable from inside the app, and miserable
     * to sit through — so there is a chip for it — but it is not a default.
     */
    const val SOFTWARE_FIRST = "sw"

    /** Mirrors `DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF`. */
    const val EXTENSIONS_OFF = 0

    /**
     * Mirrors `DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON` —
     * extensions indexed *after* the core renderers.
     */
    const val EXTENSIONS_AFTER_PLATFORM = 1

    /**
     * Mirrors `DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER` —
     * extensions indexed *before* the core renderers.
     */
    const val EXTENSIONS_BEFORE_PLATFORM = 2

    /**
     * The `setExtensionRendererMode` argument for a stored setting value.
     *
     * Unknown values resolve to [HW_PLUS] rather than throwing: this reads
     * straight off a DataStore that older builds have written to, and a
     * setting nobody recognises should not be a crash on the first frame.
     */
    fun extensionRendererMode(mode: String): Int = when (mode) {
        HW_ONLY -> EXTENSIONS_OFF
        SOFTWARE_FIRST -> EXTENSIONS_BEFORE_PLATFORM
        // "on" is a legacy value for hwplus, from before the modes were named.
        HW_PLUS, "on" -> EXTENSIONS_AFTER_PLATFORM
        else -> EXTENSIONS_AFTER_PLATFORM
    }

    /**
     * Whether the audio sink should be allowed to output 32-bit float PCM.
     *
     * Always false, and the constant folding is deliberate — this reads as a
     * decision so the reasoning has somewhere to live.
     *
     * `DefaultRenderersFactory.setEnableAudioFloatOutput(true)` was set here on
     * the belief that it was what routed AC-3/E-AC-3/TrueHD/DTS untouched to a
     * receiver. It is not, and never was: pass-through is decided by
     * `AudioCapabilities` against what the HDMI sink reports, entirely
     * independently of this flag. What the flag actually does, per
     * `DefaultAudioSink`:
     *
     *  - `FfmpegAudioRenderer.shouldOutputFloat` returns true for everything
     *    except AC-3 once the sink advertises direct float support, so the
     *    FFmpeg path decodes to 32-bit float — twice the PCM through the sink,
     *    onto the `ENCODING_PCM_FLOAT` AudioTrack, on a device whose audio HAL
     *    is tuned for 16-bit.
     *  - `DefaultAudioSink.configure` drops `audioProcessorChain`'s processors
     *    from the pipeline whenever float output is in use, because
     *    `SonicAudioProcessor` emits 16-bit integer PCM. Sonic is the fallback
     *    that applies playback speed when the AudioTrack itself will not — and
     *    the live drift loop that trims between 0.97× and 1.03× to hold its
     *    offset behind the edge depends on one of the two working. The engine
     *    asks for the AudioTrack path (`setEnableAudioTrackPlaybackParams`),
     *    but that is a preference the platform is free to decline per route,
     *    and where it declined, float output had already removed the only
     *    thing left to fall back to.
     *
     * The cost of turning it off is that 24-bit source PCM is truncated to
     * 16-bit on the way out. Against stuttering audio on the device this app is
     * mostly installed on, that is not a close call.
     */
    fun floatOutput(): Boolean = false
}
