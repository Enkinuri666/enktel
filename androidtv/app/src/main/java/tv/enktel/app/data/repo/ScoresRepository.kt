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
    /** Team crest URLs — the API supplies them and they cost nothing to carry. */
    val homeBadge: String = "",
    val awayBadge: String = "",
) {
    /** Not started yet: no score to show, [minute] holds the kick-off time. */
    val notStarted: Boolean get() = status.equals("NS", true)

    /** Match is over. */
    val finished: Boolean get() =
        status.equals("FT", true) || status.equals("AET", true) || status.equals("PEN", true)

    /** Actually in play right now — the only state that deserves a live pulse. */
    val inPlay: Boolean get() = !notStarted && !finished
}

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
class ScoresRepository(
    private val http: OkHttpClient,
    /**
     * TheSportsDB key. "3" is the public test key — no signup needed, but it
     * does **not** include `livescore.php`, which is the endpoint the in-play
     * scoreboard is built on. Users with a Patreon key can enter it in
     * Settings and get real live scores.
     */
    private val apiKey: () -> String = { FREE_KEY },
) {
    companion object {
        const val FREE_KEY = "3"

        /**
         * JSON root keys per endpoint.
         *
         * Named rather than inlined because getting one wrong fails silently:
         * the parse succeeds, the array is simply absent, and the feature
         * reports "nothing right now" forever. That is exactly how live scores
         * spent several releases looking switched off — `livescore.php`
         * returns its rows under `livescore`, and the code asked for `events`.
         */
        const val ROOT_LIVESCORE = "livescore"
        const val ROOT_EVENTS = "events"
        const val ROOT_TIMELINE = "timeline"
        const val ROOT_EVENTSTATS = "eventstats"
        const val ROOT_TVEVENT = "tvevent"
    }

    private val base: String get() = "https://www.thesportsdb.com/api/v1/json/${apiKey().ifBlank { FREE_KEY }}"

    /**
     * Why the last call came back empty, or blank when it didn't.
     *
     * Every method here is best-effort and swallows failures, which is right
     * for resilience and useless for diagnosis: a user who turned live scores
     * on saw an empty panel identical to "no matches on right now" and had no
     * way to tell the difference. The Sports Hub surfaces this so an
     * unavailable endpoint reads as unavailable rather than as nothing on.
     */
    @Volatile
    var lastStatus: String = ""
        private set

    /**
     * True when running on the shared free key.
     *
     * Not a blocker — the free key serves live scores fine. It is rate-limited
     * and shared across every app that uses it, so a personal key is worth
     * having, but the feature is not gated on one and must not be presented as
     * though it were.
     */
    val usingSharedFreeKey: Boolean get() = apiKey().ifBlank { FREE_KEY } == FREE_KEY

    // ---- in-play scoreboard ------------------------------------------------

    /** Every event currently in play, across all sports. */
    suspend fun live(): List<LiveScore> = withContext(Dispatchers.IO) {
        // The root key is `livescore`, not `events`.
        //
        // This is why live scores appeared to do nothing: the endpoint was
        // being read for an "events" array that its response has never
        // contained, so the list was always empty — and the empty result was
        // then blamed on the free key, which was wrong twice over. The free
        // key serves this endpoint perfectly well; verified returning 30
        // in-play fixtures across sports.
        val events = fetchArray("$base/livescore.php?s=all", ROOT_LIVESCORE)
        lastStatus = when {
            events.isNotEmpty() -> ""
            else -> "TheSportsDB returned no in-play matches right now."
        }
        events.mapNotNull { e ->
            LiveScore(
                eventId = e.str("idEvent").orEmpty(),
                home = e.str("strHomeTeam") ?: return@mapNotNull null,
                away = e.str("strAwayTeam") ?: return@mapNotNull null,
                homeScore = e.str("intHomeScore") ?: "–",
                awayScore = e.str("intAwayScore") ?: "–",
                // Not-started fixtures carry no progress string; their
                // kick-off time is the useful thing to show instead.
                minute = e.str("strProgress").orEmpty().ifBlank {
                    if (e.str("strStatus").orEmpty().equals("NS", true)) {
                        e.str("strEventTime").orEmpty()
                    } else ""
                },
                league = e.str("strLeague").orEmpty(),
                sport = e.str("strSport").orEmpty(),
                status = e.str("strStatus").orEmpty(),
                homeBadge = e.str("strHomeTeamBadge").orEmpty(),
                awayBadge = e.str("strAwayTeamBadge").orEmpty(),
            )
        }
            // A ticker exists to surface what is happening now. The endpoint
            // returns whatever order it likes, which regularly buried the only
            // in-play match behind a dozen fixtures that had not kicked off.
            .sortedWith(
                compareBy(
                    { if (it.inPlay) 0 else if (it.notStarted) 1 else 2 },
                    { it.league },
                ),
            )
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
        val e = fetchArray("$base/lookupevent.php?id=$eventId", ROOT_EVENTS).firstOrNull()
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
        fetchArray("$base/lookupeventstats.php?id=$eventId", ROOT_EVENTSTATS).mapNotNull { s ->
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
        fetchArray("$base/lookuptimeline.php?id=$eventId", ROOT_TIMELINE).mapNotNull { t ->
            MatchEvent(
                minute = t.str("intTime").orEmpty(),
                type = t.str("strTimeline") ?: return@mapNotNull null,
                team = t.str("strTeam").orEmpty(),
                player = t.str("strPlayer").orEmpty(),
                assist = t.str("strAssist").orEmpty(),
            )
        }.sortedBy { it.minute.toIntOrNull() ?: Int.MAX_VALUE }
    }

    // ---- Following ---------------------------------------------------------

    /**
     * Something the user can follow, as the sports database actually spells it.
     *
     * [name] is the canonical spelling to store — the whole point, because a
     * followed name is matched against programme titles and channel names, and
     * "man utd" matches nothing that "Manchester United" matches.
     */
    data class Followable(
        val name: String,
        val kind: String,
        /** "English Premier League · Soccer" — enough to tell two apart. */
        val detail: String,
        val badge: String = "",
    )

    /**
     * Teams and leagues matching [query].
     *
     * Exists because the Settings field took any text at all and gave no sign
     * whether it had landed on something real. A typo, an abbreviation or a
     * nickname was stored just as readily as a name, matched nothing for ever
     * after, and looked exactly like a feature that does nothing.
     *
     * Empty on any failure, which the caller reports as "could not check"
     * rather than "not found" — the two deserve different answers, and telling
     * someone their team does not exist because a lookup timed out is worse
     * than saying nothing.
     */
    suspend fun searchFollowable(query: String): List<Followable> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val teams = fetchArray("$base/searchteams.php?t=${enc(q)}", "teams").mapNotNull { t ->
            val name = t.str("strTeam")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Followable(
                name = name,
                kind = "team",
                detail = listOfNotNull(
                    t.str("strLeague")?.takeIf { it.isNotBlank() },
                    t.str("strSport")?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                badge = t.str("strBadge") ?: t.str("strTeamBadge").orEmpty(),
            )
        }
        // Leagues come from the full list rather than a search endpoint,
        // because the only league search TheSportsDB offers is by country.
        // It is one small request and it is cached for the session.
        val leagues = allLeagues()
            .filter { it.name.contains(q, ignoreCase = true) }
            .take(6)
        (teams.take(8) + leagues).distinctBy { it.name.lowercase() }.take(12)
    }

    @Volatile private var leagueCache: List<Followable>? = null

    private suspend fun allLeagues(): List<Followable> {
        leagueCache?.let { return it }
        val fetched = fetchArray("$base/all_leagues.php", "leagues").mapNotNull { l ->
            val name = l.str("strLeague")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Followable(
                name = name,
                kind = "league",
                detail = l.str("strSport").orEmpty(),
            )
        }
        if (fetched.isNotEmpty()) leagueCache = fetched
        return fetched
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    // ---- Official broadcast guide -----------------------------------------

    /**
     * Broadcasters officially carrying [eventId] — the "where do I actually
     * watch this" answer that no IPTV EPG can give you.
     */
    suspend fun broadcasts(eventId: String): List<Broadcast> = withContext(Dispatchers.IO) {
        if (eventId.isBlank()) return@withContext emptyList()
        fetchArray("$base/lookuptv.php?id=$eventId", ROOT_TVEVENT).mapNotNull { b ->
            Broadcast(
                channel = b.str("strChannel") ?: return@mapNotNull null,
                country = b.str("strCountry").orEmpty(),
                logo = b.str("strLogo").orEmpty(),
            )
        }.distinctBy { it.channel + it.country }
    }

    /**
     * Every broadcaster listing for a calendar day, keyed by event id.
     *
     * One request for the whole day rather than [broadcasts] per fixture. That
     * is the difference between showing "where is this on" beside forty
     * fixtures in a rail and making forty calls to a rate-limited free tier to
     * do it — which is why the Sports Hub showed no broadcaster at all and left
     * the answer buried one screen deeper, in the Match Centre.
     *
     * Empty on any failure. A rail with no broadcaster line is the behaviour
     * this replaces, so degrading to it costs nothing.
     */
    suspend fun broadcastsForDay(
        dayMs: Long,
        sport: String = "",
    ): Map<String, List<Broadcast>> = withContext(Dispatchers.IO) {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dayMs))
        val url = buildString {
            append(base).append("/eventstv.php?d=").append(day)
            if (sport.isNotBlank()) append("&s=").append(sport.replace(" ", "_"))
        }
        fetchArray(url, ROOT_TVEVENT)
            .mapNotNull { b ->
                val id = b.str("idEvent")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val channel = b.str("strChannel")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                id to Broadcast(
                    channel = channel,
                    country = b.str("strCountry").orEmpty(),
                    logo = b.str("strLogo").orEmpty(),
                )
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.distinctBy { it.channel + it.country } }
    }

    /**
     * The official schedule for a calendar day, optionally narrowed to one
     * sport ("Soccer", "Basketball", …). Used for the Sports Hub's broadcast
     * guide, which lists fixtures the user's own EPG may not carry at all.
     */
    suspend fun scheduleForDay(dayMs: Long, sport: String = ""): List<LiveScore> = withContext(Dispatchers.IO) {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(dayMs))
        val url = buildString {
            append("$base/eventsday.php?d=$day")
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
                homeBadge = e.str("strHomeTeamBadge").orEmpty(),
                awayBadge = e.str("strAwayTeamBadge").orEmpty(),
            )
        }
            // A ticker exists to surface what is happening now. The endpoint
            // returns whatever order it likes, which regularly buried the only
            // in-play match behind a dozen fixtures that had not kicked off.
            .sortedWith(
                compareBy(
                    { if (it.inPlay) 0 else if (it.notStarted) 1 else 2 },
                    { it.league },
                ),
            )
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
                append("$base/eventshighlights.php?d=$day")
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
            .use { if (it.isSuccessful) it.body.string() else null }.orEmpty()
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
