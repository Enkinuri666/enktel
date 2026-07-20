package tv.enktel.app.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.enktel.app.data.LenientJson
import tv.enktel.app.data.arr
import tv.enktel.app.data.get
import tv.enktel.app.data.str

data class LiveScore(
    val home: String,
    val away: String,
    val homeScore: String,
    val awayScore: String,
    val minute: String,
    val league: String,
    val sport: String,
)

/**
 * Live-scores lookup via TheSportsDB (v1 free tier). We try to match events by teams that
 * appear in the programme title. If TheSportsDB is unavailable the hub silently falls back
 * to the EPG-only view.
 */
class ScoresRepository(private val http: OkHttpClient) {
    private val BASE = "https://www.thesportsdb.com/api/v1/json/3"

    /** Fetch all live events currently in play across all sports. */
    suspend fun live(): List<LiveScore> = withContext(Dispatchers.IO) {
        try {
            val body = http.newCall(Request.Builder().url("$BASE/livescore.php?s=all").build()).execute()
                .use { it.body?.string() }.orEmpty()
            if (body.isBlank()) return@withContext emptyList()
            val root = LenientJson.parseToJsonElement(body) as? JsonObject ?: return@withContext emptyList()
            val events: JsonArray = root["events"]?.arr() ?: return@withContext emptyList()
            events.mapNotNull { e ->
                LiveScore(
                    home = e.str("strHomeTeam") ?: return@mapNotNull null,
                    away = e.str("strAwayTeam") ?: return@mapNotNull null,
                    homeScore = e.str("intHomeScore") ?: "–",
                    awayScore = e.str("intAwayScore") ?: "–",
                    minute = e.str("strProgress").orEmpty(),
                    league = e.str("strLeague").orEmpty(),
                    sport = e.str("strSport").orEmpty(),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    /** Try to find a live score whose two team names both appear in [programmeTitle]. */
    fun matchByTitle(programmeTitle: String, scores: List<LiveScore>): LiveScore? {
        val t = programmeTitle.lowercase()
        return scores.firstOrNull { s ->
            val h = s.home.lowercase(); val a = s.away.lowercase()
            h.isNotBlank() && a.isNotBlank() && h in t && a in t
        }
    }
}
