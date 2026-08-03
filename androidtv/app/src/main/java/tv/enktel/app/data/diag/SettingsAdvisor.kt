package tv.enktel.app.data.diag

/**
 * Turns measured facts into setting recommendations.
 *
 * Deliberately pure and separate from the probe layer: this is the only part
 * of the diagnostics that holds an opinion, so it is the only part that can be
 * wrong in an interesting way — and being a pure function of the report, it is
 * fully unit-testable without a network or a device.
 *
 * Every rule states the evidence it fired on. A recommendation without a
 * reason is a superstition, and the whole point of the panel doctor is to
 * replace "try toggling things" with "here is what the panel actually did".
 */
object SettingsAdvisor {

    fun advise(
        current: PlaybackSettings,
        live: ContainerFacts?,
        vod: ContainerFacts?,
        catchup: CatchupFacts,
    ): List<SettingChange> {
        val out = mutableListOf<SettingChange>()

        // ---- Stream format -------------------------------------------------
        // The panel decides this, not preference: if live answers HLS, asking
        // for MPEG-TS just makes the player walk a fallback chain first.
        val liveDetected = live?.detected
        val suggestedFormat = when (liveDetected) {
            "HLS" -> "hls"
            "MPEG-TS" -> "ts"
            else -> current.streamFormat
        }
        if (liveDetected == "HLS" || liveDetected == "MPEG-TS") {
            out += SettingChange(
                key = "streamFormat",
                label = "Stream format",
                current = current.streamFormat,
                suggested = suggestedFormat,
                reason = "Live URL actually serves $liveDetected on the wire.",
            )
        }

        // ---- Force MP4 -----------------------------------------------------
        // This setting pins the VOD container hint. It is right only when the
        // panel really serves MP4; forcing it on Matroska is how you get a
        // file that plays audio and no video.
        val vodDetected = vod?.detected
        if (vodDetected != null && vodDetected != "UNKNOWN") {
            val shouldForce = vodDetected == "MP4"
            if (current.vodForceMp4 != shouldForce) {
                out += SettingChange(
                    key = "vodForceMp4",
                    label = "Force MP4 (VOD)",
                    current = current.vodForceMp4.onOff(),
                    suggested = shouldForce.onOff(),
                    reason = if (shouldForce) {
                        "VOD is genuinely MP4 — pinning it skips container sniffing."
                    } else {
                        "VOD is $vodDetected, not MP4. Forcing MP4 mislabels it to the extractor."
                    },
                )
            }
        }

        // ---- Buffer profile ------------------------------------------------
        // A panel that cannot serve byte ranges has to be read start-to-finish,
        // so a bigger read-ahead is the only thing that helps it.
        val rangeBroken = vod?.range?.tested == true && !vod.range.partialContent
        if (rangeBroken && current.bufferProfile != "large") {
            out += SettingChange(
                key = "bufferProfile",
                label = "Buffer profile",
                current = current.bufferProfile,
                suggested = "large",
                reason = "Panel refused byte-range requests, so the stream can only be read forward.",
            )
        }

        // ---- Live time-shift -----------------------------------------------
        // Offering a time-shift bar the panel cannot honour is worse than not
        // offering it: it looks broken rather than absent.
        if (catchup.tested && !catchup.available && current.liveShiftEnabled) {
            out += SettingChange(
                key = "liveShiftEnabled",
                label = "Live time-shift",
                current = "on",
                suggested = "off",
                reason = "Panel's timeshift endpoint did not answer (${catchup.httpCode.orDash()}).",
            )
        }
        if (catchup.tested && catchup.available && !current.liveShiftEnabled &&
            catchup.channelsWithArchive > 0
        ) {
            out += SettingChange(
                key = "liveShiftEnabled",
                label = "Live time-shift",
                current = "off",
                suggested = "on",
                reason = "Panel serves catch-up and ${catchup.channelsWithArchive} channels advertise an archive.",
            )
        }

        return out
    }

    /**
     * Observations worth showing that are not settings changes — the things a
     * user would otherwise misread as an app bug.
     */
    fun notes(live: ContainerFacts?, vod: ContainerFacts?): List<String> {
        val out = mutableListOf<String>()

        vod?.matroska?.let { mkv ->
            when (mkv.seekable) {
                true -> out += "VOD is Matroska with a Cues index — seeking is supported."
                false -> out += "VOD is Matroska but its SeekHead indexes no Cues. Seeking will be " +
                    "approximate or unavailable for this file; that is the file, not the app."
                null -> if (mkv.isMatroska) {
                    out += "VOD is Matroska but the header was truncated before the seek index " +
                        "could be read — seek support is unconfirmed."
                }
            }
        }

        vod?.let {
            if (it.mismatch) {
                out += "VOD URL extension disagrees with the bytes on the wire (serves ${it.detected}). " +
                    "Handled transparently, but it is why container sniffing is left on."
            }
            if (it.range.tested && it.range.partialContent && !it.range.midFileSeekOk) {
                out += "Panel answers byte-range requests at the start of a file but not mid-file — " +
                    "scrubbing far into a title may fail."
            }
            if (it.range.tested && it.range.partialContent && it.range.totalBytes <= 0) {
                out += "Panel serves ranges but never reports a total length, so the player cannot " +
                    "draw a duration or an accurate scrub bar."
            }
        }

        listOfNotNull(vod?.let { "VOD" to it }, live?.let { "Live" to it }).forEach { (tag, f) ->
            if (!f.mimeCorrect && f.declaredContentType.isNotBlank()) {
                out += "$tag Content-Type is '${f.declaredContentType}' but the bytes are " +
                    "${f.detected} (expected ${f.mimeExpected}). Harmless here — the app sniffs " +
                    "the container — but it breaks players that trust the header."
            }
            if (f.chunked) {
                out += "$tag is sent with chunked transfer encoding, so no length is advertised " +
                    "and the player cannot draw an accurate scrub bar."
            }
        }

        live?.let {
            if (it.detected == "HLS" && it.range.tested && it.range.partialContent) {
                out += "Live is HLS; byte-range support on the playlist is irrelevant to it."
            }
        }

        return out
    }

    private fun Boolean.onOff() = if (this) "on" else "off"
    private fun Int.orDash() = if (this == 0) "no response" else "HTTP $this"
}
