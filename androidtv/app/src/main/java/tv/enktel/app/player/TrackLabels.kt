package tv.enktel.app.player

import java.util.Locale

/**
 * Human-readable names for the things a track picker has to show.
 *
 * The audio and subtitle pickers were rendering whatever the stream happened
 * to carry: `Format.label` when the provider set one, and the raw ISO language
 * code when it didn't. On an Xtream panel the label is almost always null, so
 * a viewer choosing between dubs got a list reading "eng · 6ch", "spa · 2ch",
 * "ara · 2ch" — codes and channel counts rather than languages and layouts.
 */
object TrackLabels {

    /**
     * ISO 639-2/T codes that the JDK does not resolve.
     *
     * `Locale` understands ISO 639-1 ("fr") and the *bibliographic* 639-2/B
     * codes ("ger", "fre"), and returns the input unchanged for the
     * *terminological* 639-2/T spellings ("deu", "fra") — which is the half of
     * the standard that streams actually tend to carry, because it is the half
     * derived from each language's own name for itself.
     *
     * Media3 normalises most language tags down to 639-1 before they reach us,
     * so this is a backstop rather than the main path; it covers the /T codes
     * common in international IPTV catalogues. Verified against the JDK rather
     * than assumed: "ger" resolves, "deu" does not.
     */
    private val ISO_639_2T = mapOf(
        "deu" to "de", "fra" to "fr", "zho" to "zh", "ces" to "cs", "ell" to "el",
        "fas" to "fa", "isl" to "is", "mkd" to "mk", "mri" to "mi", "msa" to "ms",
        "mya" to "my", "nld" to "nl", "ron" to "ro", "slk" to "sk", "sqi" to "sq",
        "bod" to "bo", "cym" to "cy", "eus" to "eu", "hye" to "hy", "kat" to "ka",
        "hrv" to "hr", "srp" to "sr", "slv" to "sl", "lav" to "lv", "lit" to "lt",
        "est" to "et", "gle" to "ga", "glg" to "gl", "kor" to "ko", "jpn" to "ja",
    )

    /**
     * "en" / "eng" / "pt-BR" → "English" / "English" / "Portuguese".
     *
     * Returns null for a code that names no language — `und`, the private-use
     * `qaa`-`qtz` range, or a provider's freeform junk — so the caller can fall
     * back to whatever else it knows rather than print "Unknown language" at
     * someone.
     */
    fun languageName(code: String?): String? {
        val raw = code?.trim()?.lowercase(Locale.ROOT)
        if (raw.isNullOrBlank() || raw == "und" || raw == "mul" || raw == "zxx") return null
        val tag = ISO_639_2T[raw] ?: raw
        val name = Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault())
        // forLanguageTag hands back the input unchanged when it cannot resolve
        // it, so an unresolved code arrives here looking like a language name.
        if (name.isBlank() || name.equals(tag, ignoreCase = true)) return null
        return name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    /**
     * Channel count → the layout people actually say out loud.
     *
     * "6ch" is the number of discrete channels in the bitstream; "5.1" is the
     * thing written on the back of the receiver and on the box the film came
     * in. They are the same fact, but only one of them answers "will this play
     * in surround".
     */
    fun channelLayout(channels: Int): String? = when {
        channels <= 0 -> null
        channels == 1 -> "Mono"
        channels == 2 -> "Stereo"
        channels == 3 -> "2.1"
        channels == 6 -> "5.1"
        channels == 8 -> "7.1"
        else -> "${channels}ch"
    }

    /**
     * MIME type → the codec name on the packaging.
     *
     * Worth showing on audio tracks specifically: this build ships a software
     * AC-3/E-AC-3/DTS decoder precisely because so much Fire TV hardware
     * decodes none of them, and "which of these tracks is the Dolby one" is a
     * question the picker should answer without the viewer trying each.
     */
    fun codecName(mimeType: String?): String? = when (mimeType?.lowercase(Locale.ROOT)) {
        null -> null
        "audio/ac3" -> "Dolby Digital"
        "audio/eac3", "audio/eac3-joc" -> "Dolby Digital+"
        "audio/true-hd" -> "Dolby TrueHD"
        "audio/vnd.dts" -> "DTS"
        "audio/vnd.dts.hd", "audio/vnd.dts.hd;profile=lbr" -> "DTS-HD"
        "audio/mp4a-latm" -> "AAC"
        "audio/mpeg", "audio/mpeg-l2" -> "MP3"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/flac" -> "FLAC"
        else -> mimeType.substringAfterLast('/').uppercase(Locale.ROOT).takeIf { it.isNotBlank() }
    }
}
