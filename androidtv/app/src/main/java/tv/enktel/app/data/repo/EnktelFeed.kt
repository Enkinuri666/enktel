package tv.enktel.app.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.enktel.app.data.LenientJson
import tv.enktel.app.data.arr
import tv.enktel.app.data.double
import tv.enktel.app.data.get
import tv.enktel.app.data.long
import tv.enktel.app.data.str
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.TimeUnit

/**
 * The catalogue feed published at enktel.tv — upcoming releases, latest film
 * releases and new shows.
 *
 * The old "Coming Soon" rail was built from the user's own catalogue, filtered
 * to `year > currentYear`. That could never work: anything sitting in your
 * library is by definition already available, so the rail was showing films you
 * could play right now, and there was no date to count down to because a
 * catalogue row only carries a year.
 *
 * This reads the upcoming-releases feed published at [ENDPOINT] instead —
 * public, no credentials, and every entry carries a real `releaseDate`.
 */
class EnktelFeed(private val http: OkHttpClient) {

    /**
     * One upcoming title. [releaseEpochDay] is the release date as an epoch day
     * so the countdown is a plain subtraction against the device's own date,
     * with no timezone arithmetic at the call site.
     */
    data class Upcoming(
        val id: Long,
        val title: String,
        val overview: String,
        val poster: String,
        val backdrop: String,
        val releaseDate: String,
        val releaseEpochDay: Long,
        val rating: Double,
        val genres: List<String>,
    ) {
        /** Whole days until release; 0 means it lands today. */
        fun daysUntil(todayEpochDay: Long): Long = (releaseEpochDay - todayEpochDay).coerceAtLeast(0)

        /** "Out today", "Tomorrow", "In 6 days", "In 3 weeks". */
        fun countdown(todayEpochDay: Long): String = when (val d = daysUntil(todayEpochDay)) {
            0L -> "Out today"
            1L -> "Tomorrow"
            in 2L..13L -> "In $d days"
            in 14L..60L -> "In ${(d + 3) / 7} weeks"
            else -> "In ${(d + 15) / 30} months"
        }
    }

    private val lock = Mutex()
    private val cache = HashMap<String, List<Upcoming>>()
    private val fetchedAt = HashMap<String, Long>()

    /**
     * Upcoming titles, soonest first. Only genuinely future dates survive.
     *
     * The filter is not belt-and-braces: the feed itself carries already-released
     * entries — at the time of writing one is dated 1991 — so passing it straight
     * through would reproduce the exact bug this class replaces.
     */
    suspend fun upcoming(todayEpochDay: Long, limit: Int = 20): List<Upcoming> =
        withContext(Dispatchers.IO) {
            load(COMING_SOON, "movies", "releaseDate")
                // Not belt-and-braces. The feed itself carries already-released
                // entries -- at the time of writing one is dated 1991 -- so
                // passing it straight through would reproduce the exact bug this
                // replaces.
                .filter { it.releaseEpochDay > todayEpochDay }
                .sortedBy { it.releaseEpochDay }
                .take(limit)
        }

    /** Recently-released films, newest first. */
    suspend fun latestMovies(limit: Int = 20): List<Upcoming> = withContext(Dispatchers.IO) {
        load(LATEST, "movies", "releaseDate").sortedByDescending { it.releaseEpochDay }.take(limit)
    }

    /**
     * Recently-started shows. These carry `firstAirDate` rather than
     * `releaseDate`, and the feed leaves it null on plenty of them, so they are
     * ordered by popularity instead of by a date most of them do not have.
     */
    suspend fun latestShows(limit: Int = 20): List<Upcoming> = withContext(Dispatchers.IO) {
        load(LATEST, "shows", "firstAirDate").take(limit)
    }

    private suspend fun load(url: String, arrayKey: String, dateKey: String): List<Upcoming> {
        val cacheKey = "$url#$arrayKey"
        lock.withLock {
            val age = System.currentTimeMillis() - (fetchedAt[cacheKey] ?: 0L)
            val hit = cache[cacheKey]
            if (age < TTL_MS && hit != null) return hit
        }
        val parsed = try { fetch(url, arrayKey, dateKey) } catch (_: Throwable) { emptyList() }
        return lock.withLock {
            if (parsed.isNotEmpty()) {
                cache[cacheKey] = parsed
                fetchedAt[cacheKey] = System.currentTimeMillis()
            }
            // A failed refresh keeps serving the last good list rather than
            // blanking the rail.
            cache[cacheKey].orEmpty()
        }
    }

    private fun fetch(url: String, arrayKey: String, dateKey: String): List<Upcoming> {
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        val body = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            resp.body.string().orEmpty()
        }
        if (body.isBlank()) return emptyList()
        val items = LenientJson.parseToJsonElement(body).get(arrayKey).arr() ?: return emptyList()
        return items.mapNotNull { parseOne(it, dateKey) }
    }

    private fun parseOne(e: JsonElement, dateKey: String): Upcoming? {
        val title = e.str("title") ?: return null
        // Shows frequently arrive with a null first-air date. They are still
        // worth showing, so a missing date is allowed through as 0 rather than
        // dropping the row -- only the Coming Soon filter needs a real date.
        val date = e.str(dateKey).orEmpty()
        val epochDay = parseIsoDateToEpochDay(date) ?: 0L
        return Upcoming(
            id = e.long("id") ?: 0L,
            title = title,
            overview = e.str("overview").orEmpty(),
            poster = e.str("posterPath").orEmpty(),
            backdrop = e.str("backdropPath").orEmpty(),
            releaseDate = date,
            releaseEpochDay = epochDay,
            rating = e.double("rating") ?: 0.0,
            genres = e.get("genres").arr()
                ?.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) }
                .orEmpty(),
        )
    }

    companion object {
        const val COMING_SOON = "https://enktel.tv/api/coming-soon"
        const val LATEST = "https://enktel.tv/api/latest-releases"
        private const val TTL_MS = 6 * 60 * 60 * 1000L // six hours

        /**
         * `2026-08-14` to an epoch day, without java.time — minSdk is 23 and
         * LocalDate needs 26 or desugaring.
         *
         * Uses a UTC calendar so the result is a pure calendar date rather than
         * an instant, which is what a release date is: it does not shift because
         * the viewer is in Sydney.
         */
        fun parseIsoDateToEpochDay(iso: String): Long? {
            val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(iso.trim()) ?: return null
            val (y, mo, d) = m.destructured
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            cal.clear()
            cal.set(y.toInt(), mo.toInt() - 1, d.toInt(), 0, 0, 0)
            return cal.timeInMillis / 86_400_000L
        }

        /** Today as an epoch day in the device's own zone. */
        fun todayEpochDay(): Long {
            val local = java.util.Calendar.getInstance()
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            cal.clear()
            cal.set(
                local.get(java.util.Calendar.YEAR),
                local.get(java.util.Calendar.MONTH),
                local.get(java.util.Calendar.DAY_OF_MONTH),
                0, 0, 0,
            )
            return cal.timeInMillis / 86_400_000L
        }
    }
}
