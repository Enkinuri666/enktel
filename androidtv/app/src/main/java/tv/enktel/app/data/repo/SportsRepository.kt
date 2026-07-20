package tv.enktel.app.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    /** Categories on the channel entity itself that always count as sports. */
    private val SPORTS_CATEGORY_TOKENS = listOf(
        "sport", "sports", "espn", "sky sports", "bein", "dazn", "fubo", "tsn", "nbc sports",
        "eurosport", "premier", "nfl", "nba", "mlb", "nhl", "ufc", "fifa", "uefa", "champions",
    )

    /** Sport tag → keywords that identify a programme title as belonging to that sport. */
    private val SPORT_TAGS: List<Pair<String, List<String>>> = listOf(
        "Football" to listOf("football", "soccer", "premier league", "uefa", "fifa", "champions league",
            "la liga", "bundesliga", "serie a", "ligue 1", "mls", "world cup", "europa"),
        "American Football" to listOf("nfl", "monday night football", "sunday night football", "college football", "super bowl"),
        "Basketball" to listOf("nba", "basketball", "wnba", "ncaa basketball", "euroleague"),
        "Baseball" to listOf("mlb", "baseball", "world series"),
        "Hockey" to listOf("nhl", "hockey", "ice hockey", "iihf"),
        "MMA/Boxing" to listOf("ufc", "mma", "boxing", "wba", "wbo", "wbc", "prizefight"),
        "Tennis" to listOf("atp", "wta", "tennis", "wimbledon", "us open", "roland garros", "australian open"),
        "Cricket" to listOf("cricket", "ipl", "test match", "t20", "odi"),
        "Motor Racing" to listOf("formula 1", "f1", "motogp", "indycar", "nascar", "wrc", "le mans"),
        "Cycling" to listOf("tour de france", "giro", "vuelta", "cycling"),
        "Golf" to listOf("pga", "lpga", "masters tournament", " open championship", " golf "),
        "Rugby" to listOf("rugby", "six nations", "premiership rugby", "nrl", "super rugby"),
        "Wrestling" to listOf("wwe", "aew", "wrestling ", "nxt"),
        "Combat" to listOf("kickboxing", "muay thai", "one championship", "bellator"),
    )

    private val GENERIC_KEYWORDS = listOf(
        "match", "highlights", "vs ", "vs.", "v.", "playoff", "quarter-final", "semi-final",
        "final", "tournament", "cup", "league", "grand prix", "derby", "classico",
    )

    /** Load sports events grouped by phase, applying [filter] if not blank. */
    suspend fun load(profileId: Long, filter: String = ""): Map<String, List<SportsEvent>> =
        withContext(Dispatchers.Default) {
            val now = System.currentTimeMillis()
            val from = now - PAST_WINDOW
            val to = now + FUTURE_WINDOW

            val channels = content.channels(profileId).first()

            // Preferred sports channels: category name / channel name hits any known token.
            val (sportsChannels, otherChannels) = channels.partition { ch ->
                SPORTS_CATEGORY_TOKENS.any { t ->
                    ch.categoryName.contains(t, true) || ch.name.contains(t, true)
                }
            }

            // EPG lookup — sports channels get the full window, other channels only when they
            // have EPG data at all (avoids hammering the DB on huge providers).
            val allProgrammes = coroutineScope {
                val a = async { epg.window(profileId, sportsChannels.map { it.epgId }.filter { it.isNotBlank() }, from, to) }
                val b = async { epg.window(profileId, otherChannels.map { it.epgId }.filter { it.isNotBlank() }, from, to) }
                a.await() to b.await()
            }
            val channelById = channels.associateBy { it.epgId }

            val hits = ArrayList<SportsEvent>()
            for ((epgId, list) in allProgrammes.first + allProgrammes.second) {
                val ch = channelById[epgId] ?: continue
                val chIsSports = SPORTS_CATEGORY_TOKENS.any { t ->
                    ch.categoryName.contains(t, true) || ch.name.contains(t, true)
                }
                for (prog in list) {
                    val sport = classify(prog, chIsSports) ?: continue
                    if (filter.isNotBlank() && sport != filter) continue
                    val phase = when {
                        prog.endMs <= now -> "FINISHED"
                        prog.startMs <= now -> "LIVE"
                        else -> "UPCOMING"
                    }
                    hits += SportsEvent(prog, ch, sport, phase)
                }
            }

            val bucketed = hits.groupBy { it.phase }
            mapOf(
                "LIVE" to bucketed["LIVE"].orEmpty().sortedBy { it.startMs },
                "UPCOMING" to bucketed["UPCOMING"].orEmpty().sortedBy { it.startMs },
                "FINISHED" to bucketed["FINISHED"].orEmpty().sortedByDescending { it.startMs },
            )
        }

    /** Return null if the programme isn't identifiable as a sports event. */
    private fun classify(prog: EpgProgram, channelIsSports: Boolean): String? {
        val text = (prog.title + " " + prog.desc).lowercase()
        for ((sport, keywords) in SPORT_TAGS) {
            if (keywords.any { it in text }) return sport
        }
        // Generic "match keyword + sports channel" fallback so lesser-known events still surface.
        if (channelIsSports && GENERIC_KEYWORDS.any { it in text }) return "Other"
        return null
    }

    /** All distinct sport names present across the loaded event set. */
    fun sportsInSet(events: Map<String, List<SportsEvent>>): List<String> =
        events.values.flatten().map { it.sport }.distinct().sorted()
}
