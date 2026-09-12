package tv.enktel.app.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.EpgProgram
import java.util.concurrent.TimeUnit

/** A programme identified as a sports event, joined against its host channel. */
data class SportsEvent(
    val program: EpgProgram,
    val channel: Channel,
    val sport: String,
    /** LIVE | UPCOMING | FINISHED */
    val phase: String,
) {
    val startMs: Long get() = program.startMs
    val endMs: Long get() = program.endMs
    val title: String get() = program.title
}

/**
 * Sports Hub data source. IPTV panels rarely expose a structured sports API, so the hub is
 * built by scanning the EPG for programmes that look like sports events (title/desc keyword
 * match, "sport" category names, or channels in a sports category). This lets tap-to-watch
 * work reliably: we already know which channel a match is on, so we can hand off to the
 * live player or a timeshift URL.
 */
class SportsRepository(private val content: ContentRepository, private val epg: EpgRepository) {

    /** Sports lookup window (in ms) around now. */
    private val PAST_WINDOW = TimeUnit.DAYS.toMillis(3)
    private val FUTURE_WINDOW = TimeUnit.DAYS.toMillis(7)

    /** Ceiling on channels scanned per load — a 20k-channel playlist would
     *  otherwise pull a ten-day EPG window for every sports-ish channel. */
    private val CHANNEL_SCAN_CAP = 400

    /** One fixture found by a search, with the duplicate feeds folded in. */
    data class SportsSearchHit(
        /**
         * Every feed carrying this fixture, best-classified first.
         *
         * A panel routinely lists one match on Main Event, on the league
         * channel, and again in SD — three rows for one thing to watch, which
         * reads as a fault in the search rather than as a choice of feeds.
         *
         * The whole list rather than just the winner, because the caller needs
         * to know which programmes were claimed: the search screen takes the
         * fixtures out of its guide rail, and folding a feed away without
         * saying so put it straight back there under a different heading.
         */
        val feeds: List<SportsEvent>,
    ) {
        val event: SportsEvent get() = feeds.first()

        /** How many *other* channels carry the same fixture at the same time. */
        val alsoOn: Int get() = feeds.size - 1
    }

    /**
     * How much of the playlist the last [load] actually covered.
     *
     * The scan is capped for performance, which is fine, but capping silently
     * is not: a user whose fixture fell outside the limit sees an incomplete
     * hub and reasonably concludes the match isn't on. Exposing the coverage
     * lets the UI admit it and point at the sport filter, which narrows the
     * scan rather than truncating it.
     */
    data class ScanCoverage(
        val channelsMatched: Int = 0,
        val channelsScanned: Int = 0,
        val hitsCapped: Boolean = false,
    ) {
        val truncated: Boolean get() = hitsCapped || channelsMatched > channelsScanned
    }

    @Volatile
    var lastScan: ScanCoverage = ScanCoverage()
        private set

    /** How many alternative feeds of one fixture the Channel Finder offers. */
    private val FEEDS_PER_FIXTURE = 4

    /**
     * Load sports events grouped by phase, applying [filter] if not blank.
     *
     * v1.9.4: bigger scanning envelope (up to 400 channels, up to 2000 hits) plus channel-
     * name-based sport auto-detection so leagues without an obvious keyword in the EPG
     * title (e.g. "Sky Sports Football HD" broadcasting a match with just team names)
     * still land in the right bucket. Every programme on a recognised sports channel is
     * kept — the "Other" bucket catches anything without a specific league tag so no
     * sport ever silently disappears.
     */
    suspend fun load(profileId: Long, filter: String = ""): Map<String, List<SportsEvent>> =
        withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            val from = now - PAST_WINDOW
            val to = now + FUTURE_WINDOW
            val HITS_CAP = 2000

            val channels = content.channels(profileId).first()
            val allSportsChannels = channels.filter { ch ->
                SPORTS_CATEGORY_TOKENS.any { t ->
                    ch.categoryName.contains(t, true) || ch.name.contains(t, true)
                }
            }
            val sportsChannels = allSportsChannels.take(CHANNEL_SCAN_CAP)
            // Record what the caps hid so the UI can say so. Silently returning
            // a partial list is how a user concludes their fixture "isn't on"
            // when it's simply past the scan limit.
            lastScan = ScanCoverage(
                channelsMatched = allSportsChannels.size,
                channelsScanned = sportsChannels.size,
                hitsCapped = false,
            )
            if (sportsChannels.isEmpty()) return@withContext emptyPhases()

            // Precompute each sports channel's implied sport from its name/category so we can
            // fall back to it when the programme title is generic ("Live", "Match", team
            // names only, etc). If the channel matches multiple sports, the first tag wins.
            val channelSport = sportsChannels.associate { ch ->
                ch.epgId to sportFromChannel(ch)
            }

            val ids = sportsChannels.map { it.epgId }.filter { it.isNotBlank() }.distinct()
            if (ids.isEmpty()) return@withContext emptyPhases()

            val allProgrammes = epg.window(profileId, ids, from, to)
            val channelById = sportsChannels.associateBy { it.epgId }

            val hits = ArrayList<SportsEvent>(minOf(3000, allProgrammes.values.sumOf { it.size }))
            outer@ for ((epgId, list) in allProgrammes) {
                val ch = channelById[epgId] ?: continue
                val chSport = channelSport[epgId]
                for (prog in list) {
                    // 1. Try programme text.
                    // 2. Fall back to the channel's implied sport.
                    // 3. Fall back to "Other" for anything on a sports channel that has a
                    //    generic keyword (Match, Live, Highlights…).
                    // 4. As a last resort on strongly-sports channels (channelSport != null)
                    //    still surface the programme in "Other" so the user sees it.
                    val sport = classify(prog, channelSport = chSport)
                        ?: chSport
                        ?: if (channelIsClearlySports(ch)) "Other" else continue
                    if (filter.isNotBlank() && sport != filter) continue
                    val phase = when {
                        prog.endMs <= now -> "FINISHED"
                        prog.startMs <= now -> "LIVE"
                        else -> "UPCOMING"
                    }
                    hits += SportsEvent(prog, ch, sport, phase)
                    if (hits.size >= HITS_CAP) {
                        lastScan = lastScan.copy(hitsCapped = true)
                        break@outer
                    }
                }
            }

            val bucketed = hits.groupBy { it.phase }
            mapOf(
                "LIVE" to bucketed["LIVE"].orEmpty().sortedBy { it.startMs },
                "UPCOMING" to bucketed["UPCOMING"].orEmpty().sortedBy { it.startMs },
                "FINISHED" to bucketed["FINISHED"].orEmpty().sortedByDescending { it.startMs },
            )
        }

    /** Classify by programme title/description. */
    private fun classify(prog: EpgProgram, channelSport: String?): String? {
        val text = (prog.title + " " + prog.desc).lowercase()
        for ((sport, keywords) in SPORT_TAGS) {
            if (keywords.any { it in text }) return sport
        }
        if (channelSport != null && GENERIC_KEYWORDS.any { it in text }) return channelSport
        return null
    }

    // ---- Smart Channel Finder ---------------------------------------------

    /** A channel that is showing a sports fixture *right now*. */
    data class LiveSportsChannel(
        val channel: Channel,
        val program: EpgProgram,
        val sport: String,
        /** 0-100: how sure we are this is really a live fixture. */
        val confidence: Int,
        /** True when the title mentions one of the user's followed teams. */
        val followed: Boolean,
    ) {
        val startMs: Long get() = program.startMs
        val endMs: Long get() = program.endMs
        val title: String get() = program.title
        /** How far through the fixture we are, 0..1. */
        val progressFrac: Float
            get() = ((System.currentTimeMillis() - startMs).toFloat() /
                (endMs - startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    /**
     * Smart Channel Finder: which of the user's channels are carrying live
     * sport at this moment.
     *
     * [load] answers "what sport is on this week" by scanning a ten-day EPG
     * window across sports-category channels. That's the wrong shape for the
     * question someone actually asks two minutes before kick-off, which is
     * "which channel, right now". So this scans differently:
     *
     *  - **Every channel, not just sports ones.** A World Cup final or a title
     *    fight lands on a general national broadcaster as often as on a channel
     *    with "Sports" in its name, and those are precisely the ones a user
     *    can't find by scrolling.
     *  - **A one-minute EPG window.** At most one programme per channel is
     *    airing now, so the query stays cheap even on a 20,000-channel playlist
     *    — it's bounded by channel count, not by catalogue depth.
     *  - **Scored, not filtered.** Each candidate gets a confidence from how
     *    strong the sports signal is (an explicit league name beats a bare
     *    "Team A vs Team B", which beats "this is a sports channel"), and rows
     *    the user follows float to the top.
     *
     * Results are deduped per channel, highest confidence first.
     */
    suspend fun findLiveNow(
        profileId: Long,
        followedTeams: List<String> = emptyList(),
        limit: Int = 120,
    ): List<LiveSportsChannel> = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val channels = content.channels(profileId).first()
        if (channels.isEmpty()) return@withContext emptyList()

        // Grouped, not keyed. A playlist carries the same broadcaster several
        // times over — SD, HD, FHD, a backup feed, one per country prefix — and
        // every one of those lines shares a single epgId. `associateBy` keeps
        // the last of each collision, so the finder offered exactly one feed
        // per fixture and silently discarded the rest; if that survivor was a
        // dead backup, the answer to "which channel is the game on" was a
        // channel that doesn't play.
        val byEpgId = channels.filter { it.epgId.isNotBlank() }.groupBy { it.epgId }
        if (byEpgId.isEmpty()) return@withContext emptyList()

        // A 60-second window returns each channel's currently-airing programme
        // and nothing else, which is all the finder needs.
        val airing = epg.window(profileId, byEpgId.keys.toList(), now, now + 60_000)
        val teams = followedTeams.map { it.lowercase() }.filter { it.isNotBlank() }

        val hits = ArrayList<LiveSportsChannel>()
        for ((epgId, programmes) in airing) {
            val prog = programmes.firstOrNull { it.startMs <= now && it.endMs > now } ?: continue
            // Capped, because "all of them" is its own kind of useless: a big
            // playlist can carry a dozen lines of one broadcaster and they
            // would sort adjacently, burying the next fixture below a screenful
            // of the same one. A handful is what the user actually wants — one
            // to tune, the rest to fall back on when it stalls.
            for (ch in byEpgId[epgId].orEmpty().take(FEEDS_PER_FIXTURE)) {
                val (sport, confidence) = scoreAsSport(prog, ch) ?: continue
                // Followed teams are matched against the channel name as well
                // as the programme title: event and pay-per-view lines usually
                // carry the fixture in the channel name itself and have no EPG
                // worth the name — "UK: PPV 04 | Arsenal vs Chelsea".
                val haystack = (prog.title + " " + ch.name).lowercase()
                val followed = teams.isNotEmpty() && teams.any { it in haystack }
                hits += LiveSportsChannel(ch, prog, sport, confidence, followed)
            }
        }

        hits.asSequence()
            .distinctBy { it.channel.key }
            .sortedWith(
                compareByDescending<LiveSportsChannel> { it.followed }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.startMs },
            )
            .take(limit)
            .toList()
    }

    /** All distinct sport names present across the loaded event set, sorted for stable UI. */
    fun sportsInSet(events: Map<String, List<SportsEvent>>): List<String> {
        val counts = events.values.flatten().groupingBy { it.sport }.eachCount()
        // Most-populated sports first, alphabetical as a tiebreak; keep "Other" at the end.
        return counts.entries
            .sortedWith(compareBy({ it.key == "Other" }, { -it.value }, { it.key }))
            .map { it.key }
    }

    /**
     * How this app decides something is sport, and which sport.
     *
     * In a companion rather than on the instance because none of it needs
     * one: it reads a programme and a channel and answers. [searchHits] is
     * the entry point the search screen uses, and putting it here is what
     * makes it testable — building a SportsRepository means building a
     * ContentRepository, which means a Context and a Room database, for
     * logic that touches neither.
     */
    companion object {
    /** Broad tokens on the channel category/name that always count as sports. Expanded from
     *  the v1.5.0 list to catch more regional broadcasters and the common Xtream naming
     *  patterns ("SPORT | US: NBA", "DE | Sky Sport", "UK ⚽ Premier League HD", etc). */
    private val SPORTS_CATEGORY_TOKENS = listOf(
        "sport", "sports", "espn", "sky sports", "sky sport", "bein", "dazn", "fubo", "tsn",
        "nbc sport", "eurosport", "premier", "nfl", "nba", "mlb", "nhl", "ufc", "fifa",
        "uefa", "champions", "bt sport", "tnt sport", "canal+ sport", "movistar",
        "starz sport", "ppv", "fight", "boxing", "wrestling", "wwe", "aew", "motogp",
        "f1", "formula 1", "cricket", "willow", "rugby", "golf channel", "tennis channel",
        "olympics", "paramount+", "peacock sport", "flosport", "bally sport", "bein sport",
        "match tv", "setanta", "eleven sports", "viasat sport",
    )

    /** Sport tag → keywords that identify a programme title as belonging to that sport.
     *  Order matters: earlier tags win on multi-match. */
    private val SPORT_TAGS: List<Pair<String, List<String>>> = listOf(
        "Football" to listOf(
            "football", "soccer", "premier league", "uefa", "fifa", "champions league",
            "la liga", "bundesliga", "serie a", "ligue 1", "mls", "world cup", "europa",
            "efl", "carabao", "fa cup", "conference league", "copa", "eredivisie",
            "primeira liga", "brasileirão", "liga mx", "concacaf",
        ),
        "American Football" to listOf(
            "nfl", "monday night football", "sunday night football", "college football",
            "super bowl", "ncaaf", "college gameday",
        ),
        "Basketball" to listOf(
            "nba", "basketball", "wnba", "ncaa basketball", "euroleague", "eurocup", "cba",
            "march madness", "final four",
        ),
        "Baseball" to listOf(
            "mlb", "baseball", "world series", "npb", "ncaa baseball", "little league",
        ),
        "Hockey" to listOf(
            "nhl", "hockey", "ice hockey", "iihf", "khl", "stanley cup", "shl",
        ),
        "MMA/Boxing" to listOf(
            "ufc", "mma", "boxing", "wba", "wbo", "wbc", "prizefight", "bellator", "one fc",
            "one championship", "usyk", "fury", "canelo", "haney",
        ),
        "Tennis" to listOf(
            "atp", "wta", "tennis", "wimbledon", "us open", "roland garros",
            "australian open", "davis cup",
        ),
        "Cricket" to listOf(
            "cricket", "ipl", "test match", "t20", "odi", "big bash", "the hundred",
            "county championship",
        ),
        "Motor Racing" to listOf(
            "formula 1", " f1 ", "motogp", "indycar", "nascar", "wrc", "le mans",
            "formula e", "supercars", "grand prix",
        ),
        "Cycling" to listOf(
            "tour de france", "giro", "vuelta", "cycling", "uci",
        ),
        "Golf" to listOf(
            "pga", "lpga", "masters tournament", " open championship", " golf ",
            "ryder cup", "the open",
        ),
        "Rugby" to listOf(
            "rugby", "six nations", "premiership rugby", "nrl", "super rugby",
            "world cup rugby", "united rugby",
        ),
        "Wrestling" to listOf(
            "wwe", "aew", "wrestling ", "nxt", "smackdown", "raw", "dynamite", "collision",
        ),
        "Combat" to listOf(
            "kickboxing", "muay thai", "one championship", "bellator", "glory",
        ),
        "Darts" to listOf("pdc", " darts ", "world darts", "premier league darts"),
        "Snooker" to listOf("snooker", "world snooker"),
        "Handball" to listOf("handball", "ehl"),
        "Volleyball" to listOf("volleyball", "cev"),
        "Athletics" to listOf("athletics", "diamond league", "world athletics", "olympic"),
        // Singular stems on purpose: these are substring matches, so "esport"
        // catches both "ESPORT" and "ESPORTS" while "esports" catches only the
        // plural. A screenshot of "ARENA ESPORT HD" on Sky Sports Arena landing
        // in the Other bucket is what caught this — the same trap applies to
        // any tag whose keyword was written plural-only.
        "Esports" to listOf("esport", "e-sport", "esl", "cs:go", "cs2", "league of legends", "valorant", "dota"),
    )

    /** "Arsenal vs Chelsea", "Lakers v Celtics", "Ajax - PSV". Requires words on
     *  both sides so a stray "v" or dash in a film title doesn't match. */
    private val FIXTURE_PATTERN = Regex(
        """\b[\p{L}\d.']{2,}\b\s+(?:vs?\.?|@|-|–)\s+\b[\p{L}\d.']{2,}\b""",
        RegexOption.IGNORE_CASE,
    )

    private val GENERIC_KEYWORDS = listOf(
        "match", "highlights", "vs ", "vs.", "v.", "playoff", "quarter-final",
        "semi-final", "final", "tournament", "cup", "league", "grand prix", "derby",
        "classico", "showcase", "matchweek", "gameweek", "postgame", "pregame", "live from",
    )

    /** Detect the sport from the channel's own name/category — e.g. "Sky Sports Football HD"
     *  or "US: NBA TV" — so we can classify events that only carry team names. */
    private fun sportFromChannel(ch: Channel): String? {
        val text = (ch.name + " " + ch.categoryName).lowercase()
        for ((sport, keywords) in SPORT_TAGS) {
            if (keywords.any { it in text }) return sport
        }
        return null
    }

    /** A channel is "clearly sports" if its category/name matches a strong sports token,
     *  so we can safely surface all of its programmes under "Other". */
    private fun channelIsClearlySports(ch: Channel): Boolean {
        val text = (ch.name + " " + ch.categoryName).lowercase()
        val strong = listOf(
            "sport", "sports", "espn", "sky sport", "bein", "dazn", "eurosport", "tnt sport",
            "bt sport", "canal+ sport", "fubo", "nba tv", "nfl network", "mlb network",
            "nhl network", "ufc", "fight", "ppv", "boxing", "wwe", "aew", "cricket", "rugby",
            "motorsport", "motogp", "f1 tv",
        )
        return strong.any { it in text }
    }

    /**
     * Scores a currently-airing programme as a live fixture.
     * @return sport tag to confidence, or null when this isn't sport at all.
     */
    private fun scoreAsSport(prog: EpgProgram, ch: Channel): Pair<String, Int>? {
        val text = (prog.title + " " + prog.desc).lowercase()
        val chSport = sportFromChannel(ch)
        val clearlySports = channelIsClearlySports(ch)

        // Strongest signal: the programme names a league or competition.
        for ((sport, keywords) in SPORT_TAGS) {
            if (keywords.any { it in text }) {
                return sport to if (clearlySports) 100 else 90
            }
        }
        // "Arsenal v Chelsea" / "Lakers vs Celtics" — a fixture even when no
        // league is named. Only trusted on a channel we already believe in,
        // because "Kramer vs. Kramer" is a film.
        if (looksLikeFixture(prog.title) && (clearlySports || chSport != null)) {
            return (chSport ?: "Other") to 80
        }
        if (chSport != null && GENERIC_KEYWORDS.any { it in text }) return chSport to 70
        if (clearlySports && GENERIC_KEYWORDS.any { it in text }) return (chSport ?: "Other") to 60
        // Nothing in the title to go on, but the channel exists to show sport
        // and something is on it — worth surfacing, ranked last.
        if (clearlySports) return (chSport ?: "Other") to 40
        return null
    }

    /** Matches the "A vs B" / "A v B" / "A - B" shape of a fixture title. */
    private fun looksLikeFixture(title: String): Boolean =
        FIXTURE_PATTERN.containsMatchIn(title)

    // ---- Unified search ----------------------------------------------------

    /**
     * Which of these programmes are sport, and what sport.
     *
     * Pure on purpose. The search screen already holds the EPG rows its query
     * matched and the channel list they belong to, so this costs one pass over
     * lists the caller has in hand. [load] cannot serve search: it pulls a
     * multi-day EPG window for up to 400 channels, which is the right price
     * for opening the Sports Hub and quite the wrong one for a keystroke.
     *
     * The floor for counting as sport is [scoreAsSport]'s — a named league or
     * competition, an "A v B" fixture title on a channel we already believe
     * in, or simply being on a channel that exists to show sport. Everything
     * here has already matched the viewer's query, so the last of those is not
     * the guess it would be during a blind scan.
     */
    fun searchHits(
        programmes: List<EpgProgram>,
        channels: List<Channel>,
        now: Long = System.currentTimeMillis(),
        limit: Int = 40,
    ): List<SportsSearchHit> {
        if (programmes.isEmpty() || channels.isEmpty()) return emptyList()
        val byEpgId = channels.asSequence()
            .filter { it.epgId.isNotBlank() }
            .associateBy { it.epgId }

        data class Scored(val event: SportsEvent, val confidence: Int)

        val scored = programmes.mapNotNull { prog ->
            val ch = byEpgId[prog.epgId] ?: return@mapNotNull null
            val (sport, confidence) = scoreAsSport(prog, ch) ?: return@mapNotNull null
            val phase = when {
                prog.endMs <= now -> "FINISHED"
                prog.startMs <= now -> "LIVE"
                else -> "UPCOMING"
            }
            Scored(SportsEvent(prog, ch, sport, phase), confidence)
        }
        if (scored.isEmpty()) return emptyList()

        // Same title at roughly the same time is the same fixture, however
        // many feeds carry it.
        //
        // Walked in order rather than bucketed by `startMs / 60_000`, which is
        // what this did first: two feeds 40 seconds apart across a minute
        // boundary land in different buckets and the match appears twice,
        // which is precisely the case the tolerance exists for.
        val groups = ArrayList<MutableList<Scored>>()
        scored
            .sortedWith(compareBy({ it.event.title.trim().lowercase() }, { it.event.startMs }))
            .forEach { row ->
                val open = groups.lastOrNull()?.last()
                val sameFixture = open != null &&
                    open.event.title.trim().equals(row.event.title.trim(), ignoreCase = true) &&
                    row.event.startMs - open.event.startMs <= DUPLICATE_FEED_SLACK_MS
                if (sameFixture) groups.last() += row else groups += mutableListOf(row)
            }

        // On now first, then what is coming, then what has been. Within a
        // phase: the classification we are surest of, then by kick-off.
        val phaseRank = mapOf("LIVE" to 0, "UPCOMING" to 1, "FINISHED" to 2)
        return groups
            .map { feeds ->
                val ranked = feeds.sortedByDescending { it.confidence }
                SportsSearchHit(ranked.map { it.event }) to ranked.first().confidence
            }
            .sortedWith(
                compareBy(
                    { phaseRank[it.first.event.phase] ?: 3 },
                    { -it.second },
                    { it.first.event.startMs },
                ),
            )
            .take(limit)
            .map { it.first }
    }

    /**
     * How far apart two feeds' start times may be and still be one fixture.
     *
     * Panels publish the same kick-off a few seconds apart across their own
     * duplicates. Two minutes is comfortably more than that drift and
     * comfortably less than any real gap between two different programmes
     * with the same title on the same channel.
     */
    private const val DUPLICATE_FEED_SLACK_MS = 120_000L

    }

    private fun emptyPhases(): Map<String, List<SportsEvent>> =
        mapOf("LIVE" to emptyList(), "UPCOMING" to emptyList(), "FINISHED" to emptyList())
}
