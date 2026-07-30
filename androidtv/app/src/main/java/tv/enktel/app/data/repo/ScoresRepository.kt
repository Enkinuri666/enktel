package tv.enktel.app.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.enktel.app.data.LenientJson
import tv.enktel.app.data.arr
import tv.enktel.app.data.str
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LiveScore(
    /** TheSportsDB event id — the key for every match-centre lookup. */
    val eventId: String,
    val home: String,
    val away: String,
    val homeScore: String,
    val awayScore: String,
    /** In-play clock or period, e.g. "78'", "HT", "Q3". */
    val minute: String,
    val league: String,
    val sport: String,
    val status: String = "",
)

/** Everything the Match Centre knows about one fixture. */
data class MatchDetail(
    val eventId: String,
    val name: String,
    val home: String,
    val away: String,
    val homeScore: String,
    val awayScore: String,
    val league: String,
    val sport: String,
    val season: String,
    val round: String,
    val venue: String,
    val country: String,
    val status: String,
    val progress: String,
    val kickoffMs: Long,
    val thumb: String,
    val homeBadge: String,
    val awayBadge: String,
    val description: String,
    /** YouTube (or other) highlights URL, when the fixture has finished. */
    val videoUrl: String,
)

/** One in-play statistic, e.g. "Possession 61% / 39%". */
data class MatchStat(val name: String, val home: String, val away: String)

/** One timeline entry — goal, card, substitution. */
data class MatchEvent(
    val minute: String,
    val type: String,
    val team: String,
    val player: String,
    val assist: String,
)

/** An official broadcaster carrying a fixture. */
data class Broadcast(
    val channel: String,
    val country: String,
    val logo: String,
)

/** A published highlights package for a finished fixture. */
data class HighlightClip(
    val title: String,
    val league: String,
    val sport: String,
    val dateMs: Long,
    val videoUrl: String,
    val thumb: String,
)

/**
 * Sports data client, backed by TheSportsDB's free v1 tier.
 *
 * This is the app's only source of *fixture* truth. Everything else in the
 * Sports Hub is derived from the user's own EPG, which knows what a channel is
 * showing but not what the score is, who's playing, or where else the match is
 * being broadcast. TheSportsDB fills exactly those gaps:
 *
 *  - [live] — the in-play scoreboard across every sport.
 *  - [matchDetail] / [matchStats] / [matchTimeline] — the Live Match Centre.
 *  - [broadcasts] — the official "where to tune in" guide for a fixture.
 *  - [scheduleForDay] — the official schedule, so a user can see what's on
 *    before it appears in any EPG.
 *  - [highlights] — catch-up clips for fixtures that have already finished.
 *
 * Every method is best-effort and returns empty/null rather than throwing: the
 * free tier rate-limits, occasionally 404s an endpoint, and is not something a
 * paying customer's Sports Hub should be able to break. The hub degrades to the
 * EPG-only view whenever this class comes back empty.
 */
class ScoresRepository(private val http: OkHttpClient) {
    private companion object {
        /** "3" is TheSportsDB's public test key — no signup, free tier limits. */
        const val BASE = "https://www.thesportsdb.com/api/v1/json/3"
    }

    // ---- in-play scoreboard ------------------------------------------------

    /** Every event currently in play, across all sports. */
    suspend fun live(): List<LiveScore> = withContext(Dispatchers.IO) {
        val events = fetchArray("$BASE/livescore.php?s=all", "events")
        events.mapNotNull { e ->
            LiveScore(
                eventId = e.str("idEvent").orEmpty(),
                home = e.str("strHomeTeam") ?: return@mapNotNull null,
                away = e.str("strAwayTeam") ?: return@mapNotNull null,
                homeScore = e.str("intHomeScore") ?: "–",
                awayScore = e.str("intAwayScore") ?: "–",
                minute = e.str("strProgress").orEmpty(),
                league = e.str("strLeague").orEmpty(),
                sport = e.str("strSport").orEmpty(),
                status = e.str("strStatus").orEmpty(),
            )
        }
    }

    /** Try to find a live score whose two team names both appear in [programmeTitle]. */
    fun matchByTitle(programmeTitle: String, scores: List<LiveScore>): LiveScore? {
        val t = programmeTitle.lowercase()
        return scores.firstOrNull { s ->
            val h = s.home.lowercase(); val a = s.away.lowercase()
            h.isNotBlank() && a.isNotBlank() && h in t && a in t
        }
    }

    // ---- Live Match Centre -------------------------------------------------

    /** Full detail for one fixture, or null when the id is unknown. */
    suspend fun matchDetail(eventId: String): MatchDetail? = withContext(Dispatchers.IO) {
        if (eventId.isBlank()) return@withContext null
        val e = fetchArray("$BASE/lookupevent.php?id=$eventId", "events").firstOrNull()
            ?: return@withContext null
        MatchDetail(
            eventId = eventId,
            name = e.str("strEvent").orEmpty(),
            home = e.str("strHomeTeam").orEmpty(),
            away = e.str("strAwayTeam").orEmpty(),
            homeScore = e.str("intHomeScore") ?: "–",
            awayScore = e.str("intAwayScore") ?: "–",
            league = e.str("strLeague").orEmpty(),
            sport = e.str("strSport").orEmpty(),
            season = e.str("strSeason").orEmpty(),
            round = e.str("intRound").orEmpty(),
            venue = e.str("strVenue").orEmpty(),
            country = e.str("strCountry").orEmpty(),
            status = e.str("strStatus").orEmpty(),
            progress = e.str("strProgress").orEmpty(),
            kickoffMs = parseKickoff(e.str("dateEvent"), e.str("strTime")),
            thumb = e.str("strThumb").orEmpty(),
            homeBadge = e.str("strHomeTeamBadge").orEmpty(),
            awayBadge = e.str("strAwayTeamBadge").orEmpty(),
            description = e.str("strDescriptionEN").orEmpty(),
            videoUrl = e.str("strVideo").orEmpty(),
        )
    }

    /** In-play statistics (shots, possession, corners…) for a fixture. */
    suspend fun matchStats(eventId: String): List<MatchStat> = withContext(Dispatchers.IO) {
        if (eventId.isBlank()) return@withContext emptyList()
        fetchArray("$BASE/lookupeventstats.php?id=$eventId", "eventstats").mapNotNull { s ->
            MatchStat(
                name = s.str("strStat") ?: return@mapNotNull null,
                home = s.str("intHome").orEmpty().ifBlank { "–" },
                away = s.str("intAway").orEmpty().ifBlank { "–" },
            )
        }
    }

    /** Goals, cards and substitutions in chronological order. */
    suspend fun matchTimeline(eventId: String): List<MatchEvent> = withContext(Dispatchers.IO) {
        if (eventId.isBlank()) return@withContext emptyList()
        fetchArray("$BASE/lookuptimeline.php?id=$eventId", "timeline").mapNotNull { t ->
            MatchEvent(
                minute = t.str("intTime").orEmpty(),
                type = t.str("strTimeline") ?: return@mapNotNull null,
                team = t.str("strTeam").orEmpty(),
                player = t.str("strPlayer").orEmpty(),
                assist = t.str("strAssist").orEmpty(),
            )
        }.sortedBy { it.minute.toIntOrNull() ?: Int.MAX_VALUE }
    }

    // ---- Official broadcast guide -----------------------------------------

    /**
     * Broadcasters officially carrying [eventId] — the "where do I actually
     * watch this" answer that no IPTV EPG can give you.
     */
    suspend fun broadcasts(eventId: String): List<Broadcast> = withContext(Dispatchers.IO) {
        if (eventId.isBlank()) return@withContext emptyList()
        fetchArray("$BASE/lookuptv.php?id=$eventId", "tvevent").mapNotNull { b ->
            Broadcast(
                channel = b.str("strChannel") ?: return@mapNotNull null,
                country = b.str("strCountry").orEmpty(),
                logo = b.str("strLogo").orEmpty(),
            )
        }.distinctBy { it.channel + it.country }
    }

    /**
     * The official schedule for a calendar day, optionally narrowed to one
     * sport ("Soccer", "Basketball", …). Used for the Sports Hub's broadcast
     * guide, which lists fixtures the user's own EPG may not carry at all.
     */
    suspend fun scheduleForDay(dayMs: Long, sport: String = ""): List<LiveScore> = withContext(Dispatchers.IO) {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dayMs))
        val url = buildString {
            append("$BASE/eventsday.php?d=$day")
            if (sport.isNotBlank()) append("&s=").append(sport.replace(' ', '_'))
        }
        fetchArray(url, "events").mapNotNull { e ->
            LiveScore(
                eventId = e.str("idEvent").orEmpty(),
                home = e.str("strHomeTeam") ?: e.str("strEvent") ?: return@mapNotNull null,
                away = e.str("strAwayTeam").orEmpty(),
                homeScore = e.str("intHomeScore") ?: "–",
                awayScore = e.str("intAwayScore") ?: "–",
                minute = e.str("strTime").orEmpty().take(5),
                league = e.str("strLeague").orEmpty(),
                sport = e.str("strSport").orEmpty(),
                status = e.str("strStatus").orEmpty(),
            )
        }
    }

    // ---- Highlights --------------------------------------------------------

    /**
     * Published highlight packages, newest first. TheSportsDB indexes these by
     * day, so we sweep the last [days] days and merge — a fixture that finished
     * late last night is exactly what someone opening the app wants to catch up
     * on this morning.
     */
    suspend fun highlights(days: Int = 2, sport: String = ""): List<HighlightClip> = withContext(Dispatchers.IO) {
        val out = LinkedHashMap<String, HighlightClip>()
        val dayMs = 24 * 60 * 60_000L
        for (i in 0 until days.coerceIn(1, 5)) {
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() - i * dayMs))
            val url = buildString {
                append("$BASE/eventshighlights.php?d=$day")
                if (sport.isNotBlank()) append("&s=").append(sport.replace(' ', '_'))
            }
            // The endpoint has used both key names across API revisions.
            val rows = fetchArray(url, "tvhighlights").ifEmpty { fetchArray(url, "highlights") }
            rows.forEach { h ->
                val video = h.str("strVideo").orEmpty()
                if (video.isBlank()) return@forEach
                val title = h.str("strEvent").orEmpty().ifBlank { h.str("strFilename").orEmpty() }
                if (title.isBlank()) return@forEach
                out.getOrPut(video) {
                    HighlightClip(
                        title = title,
                        league = h.str("strLeague").orEmpty(),
                        sport = h.str("strSport").orEmpty(),
                        dateMs = parseKickoff(h.str("dateEvent"), null),
                        videoUrl = video,
                        thumb = h.str("strThumb").orEmpty(),
                    )
                }
            }
        }
        out.values.sortedByDescending { it.dateMs }
    }

    // ---- plumbing ----------------------------------------------------------

    /** GET [url], pull [key] out of the JSON object, return it as a list. */
    private fun fetchArray(url: String, key: String): List<JsonElement> = try {
        val body = http.newCall(Request.Builder().url(url).build()).execute()
            .use { if (it.isSuccessful) it.body?.string() else null }.orEmpty()
        if (body.isBlank()) emptyList()
        else {
            val root = LenientJson.parseToJsonElement(body) as? JsonObject
            val arr: JsonArray? = root?.get(key).arr()
            arr?.toList().orEmpty()
        }
    } catch (_: Throwable) { emptyList() }

    /** "2026-07-30" + "19:45:00" → epoch millis; 0 when unparseable. */
    private fun parseKickoff(date: String?, time: String?): Long {
        if (date.isNullOrBlank()) return 0L
        return try {
            val hhmmss = time?.take(8)?.takeIf { it.length == 8 } ?: "00:00:00"
            // TheSportsDB publishes kickoff times in UTC.
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            fmt.parse("$date $hhmmss")?.time ?: 0L
        } catch (_: Throwable) { 0L }
    }
}
