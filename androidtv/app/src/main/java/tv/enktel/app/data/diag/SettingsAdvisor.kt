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

    /**
     * A widely-accepted set-top agent. Chosen because panels that filter by
     * agent almost always allow the clients their own apps ship with.
     */
    const val SMART_TV_UA = "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) SamsungBrowser/4.0 Safari/537.36"

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

        // ---- User-Agent ----------------------------------------------------
        // A 403 on a stream whose credentials are otherwise fine is nearly
        // always agent filtering, not authorisation. Presenting as a common
        // set-top client is the highest-yield workaround there is.
        val blocked = listOfNotNull(live, vod).any { it.range.httpCode == 403 }
        if (blocked && current.customUserAgent.isBlank()) {
            out += SettingChange(
                key = "customUserAgent",
                label = "User-Agent",
                current = "app default",
                suggested = SMART_TV_UA,
                reason = "Panel answered 403 to a request that carried valid credentials — " +
                    "that is agent filtering rather than auth.",
            )
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

        // ---- Live buffer vs. the provider's segment window -----------------
        // The one measurable version of the rule in BufferProfiles: a live HLS
        // server only retains the segments listed in its playlist, so the
        // furthest back a client can hold is targetDuration × segmentCount.
        // Asking for more is a request for a file the provider has deleted,
        // and the answer is 404 — which the player reports as a load error and
        // a stall. "It buffers constantly" on live is very often this, and the
        // instinct it provokes (raise the buffer) makes it worse.
        live?.hls?.let { pl ->
            val retentionMs = pl.targetDurationSec * pl.segmentCount * 1000
            if (retentionMs in 1 until tv.enktel.app.player.BufferProfiles.LIVE_MAX_CEILING_MS &&
                current.bufferProfile != "low"
            ) {
                out += SettingChange(
                    key = "bufferProfile",
                    label = "Buffer profile",
                    current = current.bufferProfile,
                    suggested = "low",
                    reason = "Live playlist retains only ${retentionMs / 1000}s of segments " +
                        "(${pl.segmentCount} × ${pl.targetDurationSec}s). Buffering past that " +
                        "requests segments the provider has already deleted.",
                )
            }
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

        // One suggestion per setting.
        //
        // Two rules can now reach for bufferProfile — a VOD panel that refuses
        // byte ranges wants "large", a live playlist with a short retention
        // window wants "low" — and they can both be right about their own half
        // of the problem. The UI applies changes by key, so emitting both is a
        // coin flip over which one lands, and the user would see two rows
        // contradicting each other.
        //
        // First wins, and the order above is the priority: the live retention
        // rule sits after the range rule because a stall on live is caused by
        // the panel deleting segments either way, while a VOD panel with no
        // range support genuinely cannot be read any other way.
        return out.distinctBy { it.key }
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

        live?.hls?.let { pl ->
            val retentionMs = pl.targetDurationSec * pl.segmentCount * 1000
            if (retentionMs > 0) {
                out += "Live playlist retains ${retentionMs / 1000}s of segments " +
                    "(${pl.segmentCount} × ${pl.targetDurationSec}s). The live buffer is capped at " +
                    "${tv.enktel.app.player.BufferProfiles.LIVE_MAX_CEILING_MS / 1000}s regardless " +
                    "of the buffer profile, because holding more than the provider keeps means " +
                    "asking for deleted segments."
            }
            if (pl.danglingAudioGroups.isNotEmpty()) {
                out += "Live HLS variants reference audio groups the playlist never declares " +
                    "(${pl.danglingAudioGroups.joinToString()}). ExoPlayer waits on a rendition " +
                    "that never arrives — this is the classic 'plays briefly then buffers forever'."
            }
            if (pl.discontinuities > 0) {
                out += "Live playlist carries ${pl.discontinuities} discontinuity marker(s) — " +
                    "expect a re-buffer at each ad or source switch."
            }
            if (pl.kind == HlsInspector.Kind.MASTER) {
                out += "Live is a master playlist with ${pl.variants.size} variant(s); " +
                    "ExoPlayer will pick a bitrate adaptively."
            }
            if (pl.kind == HlsInspector.Kind.NOT_HLS) {
                out += "The URL was served as HLS but the body is not a playlist " +
                    "(${pl.error ?: "unparseable"})."
            }
        }
        vod?.hls?.let { pl ->
            if (!pl.seekable && pl.kind == HlsInspector.Kind.MEDIA) {
                out += "VOD HLS playlist has no #EXT-X-ENDLIST and is not marked VOD, so the " +
                    "player treats it as live and will not offer a scrub bar."
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
