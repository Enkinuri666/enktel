package tv.enktel.app.voice

/**
 * A parsed voice-command intent. The parser tries to match the transcribed text
 * against a set of natural phrasings and either produces one of these or returns
 * [Unknown] so the UI can echo back what it heard.
 */
sealed class VoiceIntent {
    data class TuneChannel(val query: String) : VoiceIntent()
    /** "switch to X and set audio to Y" / "turn to X in French" — tunes,
     *  then applies the audio-language preference once tracks are known. */
    data class TuneChannelWithAudio(val channel: String, val language: String) : VoiceIntent()
    /** "set audio to Spanish" — applies to whatever is currently playing. */
    data class SetAudioLanguage(val language: String) : VoiceIntent()
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
    data object OpenLiveTv : VoiceIntent()
    data object OpenMovies : VoiceIntent()
    data object OpenSeries : VoiceIntent()
    /** Scoped searches — navigate to the given screen and pre-fill the search
     *  field.  Result set is filtered client-side to that content type. */
    data class SearchMovies(val query: String) : VoiceIntent()
    data class SearchSeries(val query: String) : VoiceIntent()
    data object OpenWatchlist : VoiceIntent()
    data object OpenRecordings : VoiceIntent()
    data object OpenSettings : VoiceIntent()
    data object ChannelUp : VoiceIntent()
    data object ChannelDown : VoiceIntent()
    data object Suggest : VoiceIntent()
    data object Fullscreen : VoiceIntent()
    // ---- Question intents: EnkTel answers back with a spoken summary + card ----
    data object WhatSportsIsOn : VoiceIntent()
    data object LatestMovies : VoiceIntent()
    data object UpcomingMovies : VoiceIntent()
    data object LatestSeries : VoiceIntent()
    data object WhatsOnNow : VoiceIntent()
    data class WhatsOnChannel(val channel: String) : VoiceIntent()
    data class TellMeAbout(val query: String) : VoiceIntent()
    data class Unknown(val heard: String) : VoiceIntent()

    // ---- Playback transport --------------------------------------------------
    data class SeekForward(val seconds: Int) : VoiceIntent()
    data class SeekBack(val seconds: Int) : VoiceIntent()
    data class SeekTo(val minutes: Int) : VoiceIntent()
    data object Restart : VoiceIntent()
    data object SkipIntro : VoiceIntent()
    data object NextEpisode : VoiceIntent()
    data object PreviousEpisode : VoiceIntent()
    data object EnterPip : VoiceIntent()
    data object CastNow : VoiceIntent()

    // ---- Content actions -----------------------------------------------------
    data object PlayRandomMovie : VoiceIntent()
    data object PlayRandomSeries : VoiceIntent()
    data object ResumeLast : VoiceIntent()
    data object ContinueWatching : VoiceIntent()
    data class AddToWatchlist(val query: String) : VoiceIntent()
    data class RemoveFromWatchlist(val query: String) : VoiceIntent()
    data class MoreLike(val query: String) : VoiceIntent()

    // ---- Info / knowledge (IMDb-style questions) -----------------------------
    data class WhoIsIn(val query: String) : VoiceIntent()
    data class WhoDirected(val query: String) : VoiceIntent()
    data class WhatYear(val query: String) : VoiceIntent()
    data class WhatRating(val query: String) : VoiceIntent()
    data class WhatGenre(val query: String) : VoiceIntent()
    data class PlotOf(val query: String) : VoiceIntent()

    // ---- Discovery / EPG -----------------------------------------------------
    data object WhatsOnTonight : VoiceIntent()
    data object WhatsOnTomorrow : VoiceIntent()
    data class WhenIsOn(val query: String) : VoiceIntent()
    data object TrendingNow : VoiceIntent()

    // ---- Sync + housekeeping -------------------------------------------------
    data object RefreshPlaylist : VoiceIntent()
    data object RefreshEpg : VoiceIntent()
    data object ToggleTheme : VoiceIntent()
    data object OpenSports : VoiceIntent()
    /** "Show me sports channels" / "movie channels" — filter Live TV by category kind. */
    data class ShowChannelKind(val keyword: String) : VoiceIntent()
    /** "Play the Arsenal game" / "Man United game" — best-effort fuzzy match
     *  against currently-live EPG programs. */
    data class PlayTeamGame(val team: String) : VoiceIntent()
    /** "Remind me when Formula 1 starts" — schedules a notification for the
     *  next upcoming EPG match of the phrase. */
    data class RemindWhenOn(val query: String) : VoiceIntent()
    /** Multi-attribute filter: "action movies from 2020 starring Tom Cruise". */
    data class FilteredMovieSearch(
        val genre: String? = null,
        val year: Int? = null,
        val decade: Int? = null,
        val actor: String? = null,
    ) : VoiceIntent()
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
            // People talk to a television the way they talk to a person, and
            // the politeness is never part of the command. Stripped here so
            // the exact-match rules below stay usable without every one of
            // them having to spell out "please" and "can you".
            //
            // Only words that carry no intent. "I want to" reads like more of
            // the same and is not: stripping it turned "I want to go to bed"
            // into "go to bed", which then opened with a tune verb and
            // changed the channel to one called "bed". A filler word can be
            // dropped; a statement of intent cannot.
            .replace(Regex("^(?:please |hey |ok |okay |could you |can you )+"), "")
            .trim()
        if (text.isBlank()) return VoiceIntent.Unknown(raw)

        // ---- Player control -----------------------------------------------------
        if (text.matchesAny(
                "pause", "pause it", "pause the show", "pause playback",
                "pause the movie", "stop it", "hold on",
            )) return VoiceIntent.Pause

        // Resume is split in two, and the split is the whole point.
        //
        // "play" and "continue" are ordinary English words that turn up inside
        // titles and requests — "play squid game", "continue watching the
        // bear" — and matchesAny treats a listed word as a match anywhere in
        // the phrase. Every "play something" command therefore resumed the
        // previous programme instead of doing what was asked, and it did so
        // silently: resuming looks like it worked.
        //
        // So those two are recognised only as the entire utterance, while the
        // verbs nobody says by accident keep matching loosely. Nothing is
        // called "resume the batman", but plenty is called "play something".
        if (text.equalsAny("play", "play it", "continue", "start it")) {
            return VoiceIntent.Resume
        }
        if (text.matchesAny(
                "resume", "resume playing", "resume playback",
                "continue playing", "unpause", "keep playing", "start playing",
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

        // Shorthand pure-number: "channel 402", "jump to channel 42"
        Regex("(?:jump to |go to |put on |tune to )?channel (\\d+)")
            .find(text)?.let {
                return VoiceIntent.TuneChannel(it.groupValues[1])
            }

        // "show me sports channels" / "browse movie channels" / "kids channels" —
        // opens the channel list scoped to that category kind.
        Regex("(?:show me |browse |open |find |list )?(sports|movie|movies|kids|news|music|entertainment|documentary|adult|international) channels?")
            .find(text)?.let {
                return VoiceIntent.ShowChannelKind(it.groupValues[1])
            }

        // "play the arsenal game" / "arsenal game" / "man united match" —
        // scans live EPG for a program mentioning the team.
        Regex("(?:play (?:the )?|watch (?:the )?)?([a-z][a-z ']{2,30}?) (?:game|match|fixture|kickoff)")
            .find(text)?.let {
                val q = it.groupValues[1].trim()
                if (q.length >= 3 && q !in setOf("live", "next", "the", "a")) {
                    return VoiceIntent.PlayTeamGame(q)
                }
            }

        // "remind me when Formula 1 starts" / "remind me when the game starts"
        Regex("remind me when (.+?) (?:starts|is on|comes on|airs)")
            .find(text)?.let { return VoiceIntent.RemindWhenOn(it.groupValues[1].trim()) }

        // ---- Combo: tune + audio language ----------------------------------------
        // "switch to CNN and set audio to English" / "turn to Canal+ in French"
        // / "tune to BBC and set the audio to Spanish"
        Regex(
            "(?:turn|switch|change|tune|put|go) (?:it |the channel |over )?(?:to |on )?" +
                "(?:channel )?(.+?) (?:and set (?:the )?audio to|and switch audio to|in) " +
                "(english|spanish|french|german|italian|portuguese|arabic|russian|hindi|mandarin|chinese|japanese|korean|dutch|polish|turkish)",
        ).find(text)?.let {
            return VoiceIntent.TuneChannelWithAudio(it.groupValues[1].trim(), it.groupValues[2].trim())
        }

        // "set audio to Spanish" / "switch audio to English" / "change the audio to French"
        Regex("(?:set|switch|change) (?:the )?audio (?:track )?to (english|spanish|french|german|italian|portuguese|arabic|russian|hindi|mandarin|chinese|japanese|korean|dutch|polish|turkish)")
            .find(text)?.let { return VoiceIntent.SetAudioLanguage(it.groupValues[1].trim()) }

        // "turn to Nine HD" / "switch to bein sports" / "put on channel 42"
        // / "tune to CNN" / "go to fox news"
        // Anchored to the start, which it was not.
        //
        // find() looks anywhere, so any sentence merely containing one of
        // these verbs was read as a tune: "what should I watch tonight"
        // tuned to a channel called "tonight", and "I want to go to bed"
        // tuned to one called "bed". Both are ordinary English rather than
        // commands, and both reached here before any discovery or search rule
        // could see them.
        //
        // A tune instruction opens with its verb — nobody asks to change
        // channel halfway through a sentence — so requiring that costs
        // nothing and stops the catch-all swallowing the language around it.
        val tuneRegex = Regex(
            "^(?:turn|switch|change|tune|put|change to|go|watch) " +
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

        // ---- Question intents (must go BEFORE the generic search fallback so
        //      "find me some live sports" and "what movies are new" don't get
        //      caught by the wide "find … / show me …" regex above) -------------

        if (text.matchesAny(
                "what live sports is on", "what sports is on",
                "what sports are on", "what sports are currently on",
                "what live sports are on", "any live sports on right now",
                "any sports on right now", "live sports right now",
                "what is on in sports", "what's on in sports",
            )) return VoiceIntent.WhatSportsIsOn

        if (text.matchesAny(
                "what are the latest movies", "what's new in movies",
                "what is new in movies", "what movies are new",
                "any new movies", "any new films", "latest movies",
                "latest films", "what movies just came out",
                "new movies", "newest movies",
            )) return VoiceIntent.LatestMovies

        if (text.matchesAny(
                "what movies are coming soon", "coming soon movies",
                "what's coming soon", "what is coming soon",
                "upcoming movies", "upcoming films",
                "movies coming out soon", "what movies are coming out",
            )) return VoiceIntent.UpcomingMovies

        if (text.matchesAny(
                "what are the latest series", "what's new in series",
                "what is new in series", "latest series", "latest shows",
                "any new shows", "any new series", "newest series",
                "new shows", "newest shows", "what tv shows are new",
            )) return VoiceIntent.LatestSeries

        // "What is on channel X" / "what's on ESPN" / "what's on Nine HD right now"
        Regex("(?:what(?:'s| is)?|whats) on (?:channel )?(.+?)(?: (?:right )?now)?$")
            .find(text)?.let {
                val q = it.groupValues[1].trim()
                    .removeSuffix(" right now").removeSuffix(" now").trim()
                if (q.isNotBlank() &&
                    !q.matchesAny("tv", "the tv", "guide", "the guide", "sports")) {
                    return VoiceIntent.WhatsOnChannel(q)
                }
            }

        if (text.matchesAny(
                "what's on now", "what is on now", "what's on tv",
                "what is on tv", "what's on tv right now", "what's on right now",
                "what is playing", "what's playing", "what's playing right now",
            )) return VoiceIntent.WhatsOnNow

        // "Tell me about X" / "who is in X" — free-form info requests. Best effort:
        // treat as a search so the user can drill in.
        Regex("(?:tell me about|what is|who is (?:in|starring in)) (.+)")
            .find(text)?.let {
                val q = it.groupValues[1].trim().trimEnd('?', '.', '!')
                if (q.isNotBlank()) return VoiceIntent.TellMeAbout(q)
            }

        // ---- Multi-attribute movie search --------------------------------------
        // "action movies from 2020", "comedy movies after 2015 with Tom Hanks",
        // "80s horror movies", "sci-fi movies starring Sigourney Weaver".
        if ("movie" in text || "film" in text) {
            val genres = listOf(
                "action", "comedy", "drama", "horror", "thriller", "romance",
                "sci-fi", "science fiction", "fantasy", "adventure", "crime",
                "mystery", "documentary", "animation", "family", "western",
                "musical", "war", "biography", "history",
            )
            val g = genres.firstOrNull { it in text }
            val yr = Regex("(?:from |in |released in |year )(\\d{4})").find(text)
                ?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("\\b(\\d{4})\\b").find(text)?.groupValues?.get(1)?.toIntOrNull()
            val decadeMatch = Regex("([1-9]0)s\\b|\\b(80s|90s|00s|70s|60s|50s)\\b").find(text)
            val decade = when (decadeMatch?.groupValues?.firstOrNull { it.isNotBlank() && it != decadeMatch.value }
                ?: decadeMatch?.value) {
                "80s" -> 1980; "90s" -> 1990; "00s" -> 2000
                "70s" -> 1970; "60s" -> 1960; "50s" -> 1950
                else -> null
            }
            val actor = Regex("(?:starring|with|featuring) ([a-z][a-z .'-]+?)(?:$| in| from| after| before| starring)")
                .find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 3 }
            if (g != null || yr != null || decade != null || actor != null) {
                return VoiceIntent.FilteredMovieSearch(
                    genre = g?.let { if (it == "science fiction") "Sci-Fi" else it.replaceFirstChar { c -> c.uppercase() } },
                    year = yr,
                    decade = decade,
                    actor = actor,
                )
            }
        }

        // ---- Scoped search: Movies -----------------------------------------------
        // "search movies for spider-man" / "find me a movie called Dune"
        // / "look for the movie Inception" / "movie search spider-man"
        Regex(
            "(?:search|find|look up|look for|show me|do you have|browse) " +
                "(?:me |for )?(?:a |the |any |some )?(?:movie|film|movies|films) " +
                "(?:called |named |about |for |with |on )?(.+)",
        ).find(text)?.let {
            val q = it.groupValues[1].trim().trimEnd('?', '.', '!')
            if (q.isNotBlank()) return VoiceIntent.SearchMovies(q)
        }
        Regex("(?:movie|film|movies|films) search (?:for |about )?(.+)")
            .find(text)?.let {
                val q = it.groupValues[1].trim().trimEnd('?', '.', '!')
                if (q.isNotBlank()) return VoiceIntent.SearchMovies(q)
            }

        // ---- Scoped search: Series -----------------------------------------------
        // "search series for breaking bad" / "find me a show called The Office"
        // / "look for the series Chernobyl" / "series search Foundation"
        Regex(
            "(?:search|find|look up|look for|show me|do you have|browse) " +
                "(?:me |for )?(?:a |the |any |some )?(?:series|show|shows|tv show|tv shows) " +
                "(?:called |named |about |for |with |on )?(.+)",
        ).find(text)?.let {
            val q = it.groupValues[1].trim().trimEnd('?', '.', '!')
            if (q.isNotBlank()) return VoiceIntent.SearchSeries(q)
        }
        Regex("(?:series|show|shows) search (?:for |about )?(.+)")
            .find(text)?.let {
                val q = it.groupValues[1].trim().trimEnd('?', '.', '!')
                if (q.isNotBlank()) return VoiceIntent.SearchSeries(q)
            }

        // ---- Search (generic — searches both Movies and Series at once) ---------
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
        if (text.matchesAny(
                "open live tv", "live tv", "browse live tv", "show me live tv",
                "browse channels", "show channels", "channel list", "all channels",
                "show me the channels", "open channels", "channels", "live channels",
                "browse live channels",
            )) return VoiceIntent.OpenLiveTv
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
        if (text.matchesAny(
                "open sports", "sports hub", "sports", "browse sports",
            )) return VoiceIntent.OpenSports

        // ---- Playback transport ------------------------------------------------
        // "skip forward 30 seconds" / "jump ahead 2 minutes" / "forward 15"
        Regex(
            "(?:skip|jump|fast[- ]?forward|forward|ff|go forward|move ahead)" +
                "(?: (?:by |ahead |the |about ))?(?:\\s*)?(\\d+)?\\s*(minute|minutes|second|seconds|sec|min)?",
        ).find(text)?.let {
            val n = it.groupValues[1].toIntOrNull() ?: 30
            val unit = it.groupValues[2]
            val secs = if (unit.startsWith("min")) n * 60 else n
            return VoiceIntent.SeekForward(secs)
        }
        Regex(
            "(?:rewind|go back|back|skip back|jump back|rewind by)" +
                "(?: (?:by |the |about ))?(?:\\s*)?(\\d+)?\\s*(minute|minutes|second|seconds|sec|min)?",
        ).find(text)?.let {
            val n = it.groupValues[1].toIntOrNull() ?: 30
            val unit = it.groupValues[2]
            val secs = if (unit.startsWith("min")) n * 60 else n
            return VoiceIntent.SeekBack(secs)
        }
        Regex("(?:seek|go|jump) to (\\d+)\\s*(?:minutes?|min)?")
            .find(text)?.let {
                return VoiceIntent.SeekTo(it.groupValues[1].toInt())
            }
        if (text.matchesAny(
                "restart", "start over", "from the beginning", "restart from the beginning",
                "play from the start", "restart it", "play from beginning",
            )) return VoiceIntent.Restart
        if (text.matchesAny(
                "skip intro", "skip the intro", "skip opening", "skip credits",
                "skip the opening",
            )) return VoiceIntent.SkipIntro
        if (text.matchesAny(
                "next episode", "play next episode", "next", "play next",
            )) return VoiceIntent.NextEpisode
        if (text.matchesAny(
                "previous episode", "play previous episode", "last episode", "go back an episode",
            )) return VoiceIntent.PreviousEpisode
        if (text.matchesAny(
                "picture in picture", "pip", "enter pip", "minimize the player",
                "shrink the player", "picture-in-picture",
            )) return VoiceIntent.EnterPip
        if (text.matchesAny(
                "cast", "cast this", "cast to tv", "cast to the tv", "screencast",
                "mirror to tv", "start casting", "cast now",
            )) return VoiceIntent.CastNow

        // ---- Content actions ---------------------------------------------------
        if (text.matchesAny(
                "play random movie", "play a random movie", "random movie",
                "surprise me with a movie", "pick a movie", "pick a random movie",
            )) return VoiceIntent.PlayRandomMovie
        if (text.matchesAny(
                "play random series", "play a random series", "random series",
                "random show", "surprise me with a show", "pick a series",
            )) return VoiceIntent.PlayRandomSeries
        if (text.matchesAny(
                "resume last", "resume", "pick up where i left off", "continue",
                "continue where i left off", "keep watching",
            )) return VoiceIntent.ResumeLast
        if (text.matchesAny(
                "continue watching", "what am i watching", "what was i watching",
                "show my continue watching",
            )) return VoiceIntent.ContinueWatching

        Regex("(?:add|save|put) (.+?) to (?:my )?(?:watchlist|list|favorites|favourites)")
            .find(text)?.let { return VoiceIntent.AddToWatchlist(it.groupValues[1].trim()) }
        Regex("(?:remove|take|delete) (.+?) from (?:my )?(?:watchlist|list|favorites|favourites)")
            .find(text)?.let { return VoiceIntent.RemoveFromWatchlist(it.groupValues[1].trim()) }
        Regex("(?:more like|similar to|things like|shows like|movies like) (.+)")
            .find(text)?.let { return VoiceIntent.MoreLike(it.groupValues[1].trim().trimEnd('?','.','!')) }

        // ---- Info / IMDb-style questions --------------------------------------
        Regex("(?:who(?:'s| is)? in|who stars in|cast of|who acts in) (.+)")
            .find(text)?.let { return VoiceIntent.WhoIsIn(it.groupValues[1].trim().trimEnd('?','.','!')) }
        Regex("(?:who directed|who is the director of|director of|who made) (.+)")
            .find(text)?.let { return VoiceIntent.WhoDirected(it.groupValues[1].trim().trimEnd('?','.','!')) }
        Regex("(?:what year (?:did|is|was)|when did|when was|release year of|when was .* released|what year is) (.+)")
            .find(text)?.let { return VoiceIntent.WhatYear(it.groupValues[1].trim().trimEnd('?','.','!')) }
        Regex("(?:what(?:'s| is) the rating of|rating of|how good is|score of) (.+)")
            .find(text)?.let { return VoiceIntent.WhatRating(it.groupValues[1].trim().trimEnd('?','.','!')) }
        Regex("(?:what genre is|what kind of movie is|genre of) (.+)")
            .find(text)?.let { return VoiceIntent.WhatGenre(it.groupValues[1].trim().trimEnd('?','.','!')) }
        Regex("(?:what(?:'s| is) (?:.+?)? about|plot of|what happens in|summary of|synopsis of) (.+)")
            .find(text)?.let { return VoiceIntent.PlotOf(it.groupValues[1].trim().trimEnd('?','.','!')) }

        // ---- Discovery / EPG ---------------------------------------------------
        if (text.matchesAny(
                "what's on tonight", "what is on tonight", "tonight's tv", "tonight",
                "what's playing tonight",
            )) return VoiceIntent.WhatsOnTonight
        if (text.matchesAny(
                "what's on tomorrow", "what is on tomorrow", "tomorrow's tv", "tomorrow",
                "what's playing tomorrow",
            )) return VoiceIntent.WhatsOnTomorrow
        Regex("when (?:is|will) (.+?) (?:be )?(?:on|playing|airing)")
            .find(text)?.let { return VoiceIntent.WhenIsOn(it.groupValues[1].trim().trimEnd('?','.','!')) }
        if (text.matchesAny(
                "what's trending", "trending now", "what is trending", "what's popular",
                "what is popular", "top picks",
            )) return VoiceIntent.TrendingNow

        // ---- Sync / housekeeping ----------------------------------------------
        if (text.matchesAny(
                "refresh playlist", "sync playlist", "reload playlist", "refresh channels",
                "refresh the playlist", "resync playlist", "resync the playlist",
                "sync the playlist", "reload channels",
            )) return VoiceIntent.RefreshPlaylist
        if (text.matchesAny(
                "refresh epg", "refresh the guide", "reload epg", "refresh tv guide",
                "sync epg", "resync epg", "update guide", "update the guide",
            )) return VoiceIntent.RefreshEpg
        if (text.matchesAny(
                "toggle theme", "switch theme", "dark mode", "light mode",
                "switch to dark mode", "switch to light mode",
            )) return VoiceIntent.ToggleTheme

        return VoiceIntent.Unknown(raw)
    }

    /**
     * The whole utterance is one of these, rather than merely containing one.
     *
     * For commands whose words also occur inside titles, "contains" is the
     * wrong test — see the note on Resume above.
     */
    private fun String.equalsAny(vararg patterns: String): Boolean =
        patterns.any { this == it }

    private fun String.matchesAny(vararg patterns: String): Boolean =
        patterns.any { this == it || this.startsWith("$it ") || this.endsWith(" $it") || " $it " in " $this " }
}
