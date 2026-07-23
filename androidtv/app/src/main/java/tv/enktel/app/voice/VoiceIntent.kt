package tv.enktel.app.voice

/**
 * A parsed voice-command intent. The parser tries to match the transcribed text
 * against a set of natural phrasings and either produces one of these or returns
 * [Unknown] so the UI can echo back what it heard.
 */
sealed class VoiceIntent {
    data class TuneChannel(val query: String) : VoiceIntent()
    data class Search(val query: String) : VoiceIntent()
    data object Pause : VoiceIntent()
    data object Resume : VoiceIntent()
    data class SetVolume(val fraction: Float) : VoiceIntent()
    data object VolumeUp : VoiceIntent()
    data object VolumeDown : VoiceIntent()
    data object Mute : VoiceIntent()
    data object RecordNow : VoiceIntent()
    data object FindSports : VoiceIntent()
    data object OpenHome : VoiceIntent()
    data object OpenGuide : VoiceIntent()
    data object OpenMovies : VoiceIntent()
    data object OpenSeries : VoiceIntent()
    data object OpenWatchlist : VoiceIntent()
    data object OpenRecordings : VoiceIntent()
    data object OpenSettings : VoiceIntent()
    data object ChannelUp : VoiceIntent()
    data object ChannelDown : VoiceIntent()
    data object Suggest : VoiceIntent()
    data object Fullscreen : VoiceIntent()
    data class Unknown(val heard: String) : VoiceIntent()
}

/**
 * Turns a raw transcription into a [VoiceIntent]. Deliberately keyword+regex
 * driven rather than LLM-driven so the feature works fully offline once the
 * speech recogniser has produced a string — no server round-trip needed.
 *
 * Match order matters: more specific phrasings first (e.g. "set volume to
 * X percent" before generic "set volume"). We normalise the input to
 * lowercase, strip punctuation, and collapse whitespace before matching.
 */
object VoiceIntentParser {

    private val NUMBER_WORDS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19, "twenty" to 20, "thirty" to 30,
        "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70,
        "eighty" to 80, "ninety" to 90, "hundred" to 100,
    )

    fun parse(raw: String): VoiceIntent {
        val text = raw.lowercase()
            .replace(Regex("[.,!?;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.isBlank()) return VoiceIntent.Unknown(raw)

        // ---- Player control -----------------------------------------------------
        if (text.matchesAny(
                "pause", "pause it", "pause the show", "pause playback",
                "pause the movie", "stop it", "hold on",
            )) return VoiceIntent.Pause

        if (text.matchesAny(
                "resume", "resume playing", "resume playback", "play", "play it",
                "continue playing", "continue", "unpause", "keep playing",
                "start it", "start playing",
            )) return VoiceIntent.Resume

        if (text.matchesAny("mute", "mute it", "silence")) return VoiceIntent.Mute
        if (text.matchesAny("volume up", "louder", "turn it up")) return VoiceIntent.VolumeUp
        if (text.matchesAny("volume down", "quieter", "softer", "turn it down"))
            return VoiceIntent.VolumeDown

        // "set volume to 40 percent" / "set the volume to 60%" / "volume to 30"
        Regex("(?:set )?(?:the )?volume (?:to |at )?(\\d{1,3})\\s*(?:%|percent)?")
            .find(text)?.let {
                val n = it.groupValues[1].toIntOrNull() ?: return@let
                return VoiceIntent.SetVolume(n.coerceIn(0, 100) / 100f)
            }

        if (text.matchesAny("fullscreen", "full screen", "make it fullscreen"))
            return VoiceIntent.Fullscreen

        // ---- Channel navigation -------------------------------------------------
        if (text.matchesAny(
                "channel up", "next channel", "flip up", "go up a channel",
            )) return VoiceIntent.ChannelUp
        if (text.matchesAny(
                "channel down", "previous channel", "last channel",
                "flip down", "go down a channel",
            )) return VoiceIntent.ChannelDown

        // "turn to Nine HD" / "switch to bein sports" / "put on channel 42"
        // / "tune to CNN" / "go to fox news"
        val tuneRegex = Regex(
            "(?:turn|switch|change|tune|put|change to|go|watch) " +
                "(?:it |the channel |over )?" +
                "(?:to |on )?(?:channel )?(.+)",
        )
        tuneRegex.find(text)?.let {
            val q = it.groupValues[1].trim()
            if (q.isNotBlank() && !q.matchesAny("sports", "the sports", "movies", "series")) {
                return VoiceIntent.TuneChannel(q)
            }
        }

        // ---- Recording ----------------------------------------------------------
        if (text.matchesAny(
                "record", "record this", "record the show", "record it",
                "record this program", "record this programme", "download it",
                "download this", "download the show", "download the current show",
                "save this show", "start recording",
            )) return VoiceIntent.RecordNow

        // ---- Sports -------------------------------------------------------------
        if (text.matchesAny(
                "find sports", "find me sports", "find me some sports",
                "find live sports", "find me some live sports",
                "show me sports", "what sports are on", "sports hub",
                "any sports on", "any live sports", "show sports",
            )) return VoiceIntent.FindSports

        // ---- Suggestions --------------------------------------------------------
        if (text.matchesAny(
                "what should i watch", "suggest something", "surprise me",
                "recommend something", "recommend me something",
                "what's good", "what is good", "what's on", "what is on",
                "pick something for me",
            )) return VoiceIntent.Suggest

        // ---- Search -------------------------------------------------------------
        Regex("(?:search|find|look up|look for|show me|do you have) (?:for |me )?(?:the |a |any )?(.+)")
            .find(text)?.let {
                val q = it.groupValues[1].trim()
                    .removePrefix("movie ").removePrefix("show ").removePrefix("series ")
                    .removePrefix("movies ").removePrefix("shows ")
                    .removeSuffix(" movie").removeSuffix(" show").removeSuffix(" series")
                    .trim()
                if (q.isNotBlank()) return VoiceIntent.Search(q)
            }

        // ---- Navigation ---------------------------------------------------------
        if (text.matchesAny("go home", "open home", "back to home", "home screen"))
            return VoiceIntent.OpenHome
        if (text.matchesAny(
                "open guide", "tv guide", "open tv guide", "show me the guide",
                "epg", "electronic program guide",
            )) return VoiceIntent.OpenGuide
        if (text.matchesAny("open movies", "show movies", "movies", "browse movies"))
            return VoiceIntent.OpenMovies
        if (text.matchesAny(
                "open series", "show series", "series", "browse series", "tv shows",
            )) return VoiceIntent.OpenSeries
        if (text.matchesAny(
                "open watchlist", "watchlist", "my watchlist", "my list", "open my list",
            )) return VoiceIntent.OpenWatchlist
        if (text.matchesAny(
                "open recordings", "recordings", "my recordings", "dvr",
            )) return VoiceIntent.OpenRecordings
        if (text.matchesAny("open settings", "settings")) return VoiceIntent.OpenSettings

        return VoiceIntent.Unknown(raw)
    }

    private fun String.matchesAny(vararg patterns: String): Boolean =
        patterns.any { this == it || this.startsWith("$it ") || this.endsWith(" $it") || " $it " in " $this " }
}
