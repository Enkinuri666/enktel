package tv.enktel.app.player

/**
 * Which video codec to ask for when a source offers several, and why the
 * answer has to depend on the box.
 *
 * ### The rung this sits on
 *
 * `setPreferredVideoMimeTypes` is a MIME-type preference and nothing else — it
 * has no say over bitrate, which the adaptive track selection decides
 * separately. What matters is where it lands in
 * `DefaultTrackSelector.compareNonQualityPreferences`:
 *
 *     isWithinRendererCapabilities   ← above it
 *     …constraints…
 *     preferredMimeTypeMatchIndex    ← this setting
 *     usesHardwareAcceleration       ← below it
 *
 * Above it is the reassuring half: a device that cannot decode HEVC at all is
 * never steered onto it, whatever this list says.
 *
 * Below it is the problem. Among renditions the device *can* decode, the
 * preference outranks whether the decoder is a hardware one — and "can decode"
 * includes decoding in software. A fixed AV1 → HEVC → VP9 → H.264 list
 * therefore resolves a multi-codec source in favour of software HEVC on a box
 * whose only HEVC path is software, in preference to the hardware H.264 sitting
 * right next to it. That is the judder [tv.enktel.app.data.net.DeviceProbe]'s
 * own header describes for a Fire TV Stick Lite, and it is the same shape as
 * the Opus default fixed in v1.60.33: a preference expressed without asking
 * whether the hardware could honour it.
 *
 * ### What this does instead
 *
 * A codec earns its place in the list only if the device has a *hardware*
 * decoder for it. H.264 is always last and always present, as the floor.
 *
 * Leaving a codec out is what makes the fix work, and it is worth being clear
 * that omission is an active choice rather than a gap: an absent MIME type
 * scores as "no match", so a listed H.264 outranks an unlisted software HEVC,
 * and the decision falls through to `usesHardwareAcceleration` exactly as it
 * would with no preference set at all. Listing it and hoping is what the old
 * behaviour did.
 *
 * On a Shield or a Fire Cube, which decode all four in hardware, the result is
 * byte-for-byte the list that was there before — the bandwidth win on HEVC is
 * kept where the box can actually cash it.
 *
 * ### Unknown is not the same as none
 *
 * [order] takes null for "could not read the codec list" and answers with the
 * full legacy order. A probe that throws must not quietly demote a capable box
 * to H.264; the only safe reading of "I don't know" is the behaviour that
 * shipped before there was anything to know.
 *
 * ### Everything here is pure
 *
 * No Android types, so [VideoCodecPreferenceTest] can pin the table. The MIME
 * strings are the platform's own constants, spelled out rather than imported
 * from media3 for the same reason — and they are the same literals
 * `DeviceProbe.WANTED` already keys on.
 */
object VideoCodecPreference {

    const val AV1 = "video/av01"
    const val HEVC = "video/hevc"
    const val VP9 = "video/x-vnd.on2.vp9"
    const val H264 = "video/avc"

    /**
     * What was passed unconditionally before this existed, and still the answer
     * when the device cannot be probed.
     */
    val LEGACY_ORDER: List<String> = listOf(AV1, HEVC, VP9, H264)

    /** Modern first, H.264 last. Only entries with hardware backing survive. */
    private val MODERN = listOf(AV1, HEVC, VP9)

    /**
     * The `setPreferredVideoMimeTypes` argument list for this device.
     *
     * @param hardwareMimes lower-case MIME types the device decodes in
     *   hardware, or null when the codec list could not be read — see the
     *   "unknown is not the same as none" note above.
     */
    fun order(hardwareMimes: Set<String>?): List<String> {
        if (hardwareMimes == null) return LEGACY_ORDER
        val normalised = hardwareMimes.map { it.lowercase() }.toSet()
        return MODERN.filter { it in normalised } + H264
    }
}
