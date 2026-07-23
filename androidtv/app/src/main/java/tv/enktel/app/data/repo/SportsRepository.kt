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
        "Esports" to listOf("esports", "esl", "cs:go", "cs2", "league of legends", "valorant", "dota"),
    )

    private val GENERIC_KEYWORDS = listOf(
        "match", "highlights", "vs ", "vs.", "v.", "playoff", "quarter-final",
        "semi-final", "final", "tournament", "cup", "league", "grand prix", "derby",
        "classico", "showcase", "matchweek", "gameweek", "postgame", "pregame", "live from",
    )

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
            val sportsChannels = channels.filter { ch ->
                SPORTS_CATEGORY_TOKENS.any { t ->
                    ch.categoryName.contains(t, true) || ch.name.contains(t, true)
                }
            }.take(400)
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
                    if (hits.size >= HITS_CAP) break@outer
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

    /** All distinct sport names present across the loaded event set, sorted for stable UI. */
    fun sportsInSet(events: Map<String, List<SportsEvent>>): List<String> {
        val counts = events.values.flatten().groupingBy { it.sport }.eachCount()
        // Most-populated sports first, alphabetical as a tiebreak; keep "Other" at the end.
        return counts.entries
            .sortedWith(compareBy({ it.key == "Other" }, { -it.value }, { it.key }))
            .map { it.key }
    }

    private fun emptyPhases(): Map<String, List<SportsEvent>> =
        mapOf("LIVE" to emptyList(), "UPCOMING" to emptyList(), "FINISHED" to emptyList())
}
