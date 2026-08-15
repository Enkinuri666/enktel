package tv.enktel.app.player

/**
 * Closed captions on live TV: finding the ones that are already there.
 *
 * ## What "the channels don't have subtitles" usually means
 *
 * Almost never that the stream carries no caption data. Live TV carries
 * captions in places that are not subtitle *tracks*, and a player has to be
 * told to go looking:
 *
 * **CEA-608 / CEA-708** ride inside the video elementary stream as SEI user
 * data. They are not a separate PID and never appear in a stream's track list
 * until the extractor is asked to pull them out.
 *
 * **DVB bitmap subtitles** are a real PID, but only discoverable through the
 * PMT's subtitling descriptor, which re-muxers drop constantly.
 *
 * So a channel can be captioned end to end and still present as having no
 * subtitles, because nothing asked.
 *
 * ## The two specific gaps this closes
 *
 * **Nothing at all was extracted from an undescribed stream.** Media3 decides
 * which caption formats to expose by parsing the caption service descriptor
 * (ATSC A/65, tag 0x86) out of the PMT. When that descriptor is absent it falls
 * back to whatever format list the payload-reader factory was constructed with —
 * and that list is empty unless someone supplies one. Not "a default CEA-608
 * track": none. An IPTV panel rebuilds the PMT carrying only what it needs to
 * play, so the descriptor is routinely gone, and a channel captioned end to end
 * in its SEI data presents as having no subtitles whatsoever.
 *
 * Supplying both standards as that fallback is the fix. Note what is *not* done:
 * `FLAG_OVERRIDE_CAPTION_DESCRIPTORS` is deliberately left unset. It would make
 * the player ignore the descriptor even when present, and the descriptor is the
 * better source when it exists — it names one format per caption service and
 * carries a three-letter language code for each, which is how a Croatian track
 * arrives correctly tagged `hrv` instead of untagged. Overriding it would throw
 * that away for no gain.
 *
 * **Nothing was ever auto-selected.** Embedded captions arrive with no language
 * tag at all — there is nowhere in a CEA-608 byte pair to put one. Media3 will
 * not select an untagged text track unless told that undetermined is acceptable,
 * so even when the track was found, it sat in the picker switched off.
 *
 * ## Croatian, which has its own problem
 *
 * Croatian is tagged four different ways in the wild and two of them are
 * withdrawn codes:
 *
 * | code | what it is |
 * | :--- | :--- |
 * | `hr` / `hrv` | current ISO 639-1 / 639-2 |
 * | `scr` | withdrawn 1990s code for Serbo-Croat (Roman script) |
 * | `sh` / `hbs` | Serbo-Croatian, also withdrawn |
 *
 * Ex-Yugoslav broadcasters' muxes still emit `scr`, and equipment built against
 * the old tables still writes it. A player matching only `hr` finds nothing on a
 * stream that is captioned in Croatian and says so in the descriptor — which is
 * a good candidate for why this looked like "no subtitle support" rather than a
 * code mismatch.
 *
 * Serbian and Bosnian sit in [NEIGHBOURS] as a lower tier. They are not the same
 * language and are not offered as if they were, but they are mutually
 * intelligible with Croatian in subtitle form, and a viewer who turned captions
 * on wants readable text more than they want a purist's empty screen. They rank
 * below every Croatian spelling and are never chosen while one exists.
 *
 * ## What this is not
 *
 * It does not generate captions. If a stream genuinely carries no caption data
 * then nothing here invents any — that needs speech recognition, which is a
 * different feature with real costs attached, and pretending otherwise would
 * mean shipping a toggle that does nothing on the channels it was asked for.
 */
object ClosedCaptions {

    // ---- settings values ----------------------------------------------------

    const val OFF = "off"
    const val AUTO = "auto"
    const val ENGLISH = "en"
    const val CROATIAN = "hr"

    /** The setting's allowed values, in the order the picker shows them. */
    val MODES: List<String> = listOf(OFF, AUTO, ENGLISH, CROATIAN)

    // ---- language tables ----------------------------------------------------

    /** Every spelling of Croatian a live stream might use, best first. */
    val CROATIAN_CODES: List<String> = listOf("hr", "hrv", "scr", "sh", "hbs")

    /** Every spelling of English. */
    val ENGLISH_CODES: List<String> = listOf("en", "eng")

    /**
     * Mutually intelligible with Croatian in written form.
     *
     * A fallback tier, never a synonym — see the class note.
     */
    val NEIGHBOURS: List<String> = listOf("sr", "srp", "scc", "bs", "bos")

    /**
     * The caption sample MIME types to expose on MPEG-TS.
     *
     * Used only as the fallback for a stream whose PMT does not describe its
     * captions. Both standards, always, because which one a broadcaster used is
     * exactly what the missing descriptor would have told us. An exposed track
     * with no data in it costs an entry in the track picker; a track that was
     * never exposed costs the feature.
     */
    val TS_CAPTION_MIME_TYPES: List<String> = listOf(
        "application/cea-608",
        "application/cea-708",
    )

    // ---- behaviour ----------------------------------------------------------

    fun enabled(mode: String): Boolean = mode != OFF && mode in MODES

    /**
     * Normalise a track's language tag for comparison.
     *
     * Streams write `EN`, `eng`, `en-GB`, `en_US` and ` hr ` interchangeably.
     */
    fun normalise(raw: String?): String =
        raw?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_').orEmpty()

    /**
     * The ordered language preference handed to the track selector.
     *
     * Order is priority: Media3 walks it and takes the first match it can find.
     * Exact spellings of the chosen language come first, then the intelligible
     * neighbours where the choice was Croatian, then the other supported
     * language as a last resort — a viewer who asked for captions is better
     * served by the wrong language than by silence, and can still switch the
     * track by hand.
     *
     * @param deviceLanguage two-letter code from the device locale, used only
     *   by [AUTO].
     */
    fun preferredLanguages(mode: String, deviceLanguage: String = ""): List<String> {
        val device = normalise(deviceLanguage)
        return when (mode) {
            ENGLISH -> ENGLISH_CODES + CROATIAN_CODES
            CROATIAN -> CROATIAN_CODES + NEIGHBOURS + ENGLISH_CODES
            AUTO -> when {
                device in CROATIAN_CODES -> CROATIAN_CODES + NEIGHBOURS + ENGLISH_CODES
                device in ENGLISH_CODES -> ENGLISH_CODES + CROATIAN_CODES
                // A device set to neither still gets both offered rather than
                // nothing: the setting says "caption this", not "caption this
                // only if you speak one of two languages".
                else -> ENGLISH_CODES + CROATIAN_CODES + NEIGHBOURS
            }
            else -> emptyList()
        }
    }

    /**
     * Whether an untagged text track may be selected.
     *
     * Always true while captions are on, and it is the single line that makes
     * embedded captions work. CEA-608 has no field for a language, so every
     * such track arrives undetermined; a selector told to prefer `en` will
     * refuse all of them unless undetermined is explicitly allowed.
     *
     * The risk it accepts is picking a caption track in the wrong language on a
     * multi-language stream. That risk only lands when no tagged track matched
     * — the preference list is consulted first — so the trade is a wrong
     * language against no captions at all.
     */
    fun allowUndetermined(mode: String): Boolean = enabled(mode)

    /**
     * Does this track's language satisfy [mode]?
     *
     * Used for labelling and for deciding whether the auto-selection actually
     * got what was asked for, not for the selection itself — Media3 does that
     * from [preferredLanguages].
     */
    fun matches(trackLanguage: String?, mode: String, deviceLanguage: String = ""): Boolean {
        if (!enabled(mode)) return false
        val lang = normalise(trackLanguage)
        // Undetermined is accepted on purpose: see allowUndetermined.
        if (lang.isEmpty() || lang == "und") return true
        return lang in preferredLanguages(mode, deviceLanguage)
    }

    /** Human label for the settings row and the track picker. */
    fun label(mode: String): String = when (mode) {
        OFF -> "Off"
        AUTO -> "Automatic"
        ENGLISH -> "English"
        CROATIAN -> "Hrvatski"
        else -> "Off"
    }
}
