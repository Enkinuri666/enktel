package tv.enktel.app.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.enktel.app.data.LenientJson
import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.int
import tv.enktel.app.data.str

/**
 * Internet radio, from the radio-browser.org community directory.
 *
 * Most IPTV lines carry a handful of radio streams as an afterthought, and
 * EnkTel's Radio view was as empty as the line made it. This fills it from a
 * real directory instead — a free, key-less, community-run index of tens of
 * thousands of stations, each already tagged with a genre and a country,
 * which is exactly the two axes the browser groups by.
 *
 * Stations are mapped straight onto [Channel] so every existing piece of
 * machinery — search, favourites, hiding, custom lists, the browser grid —
 * works on them without knowing they came from anywhere unusual. They are
 * *not* written to the channels table: a playlist refresh clears that for the
 * profile, and radio has nothing to do with the playlist.
 */
object RadioDirectory {

    /**
     * Mirrors, tried in order. The directory is run by volunteers and
     * individual mirrors go down; the official advice is to fail over rather
     * than pin one host.
     */
    private val MIRRORS = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
    )

    /** Their API asks callers to identify themselves. */
    private const val UA = "EnktelIPTV/1.0 (+https://enktel.tv)"

    /** Synthetic stream ids start here so they cannot collide with a panel's. */
    private const val ID_BASE = 9_000_000_000L

    /**
     * The genre buckets the browser offers.
     *
     * radio-browser tags are free text and enormously long-tailed — a single
     * station can carry thirty of them, in several languages. Mapping to a
     * fixed set keeps the category chips readable; the first bucket whose
     * keywords appear wins, so ordering is significance, not alphabetical.
     */
    private val GENRES: List<Pair<String, List<String>>> = listOf(
        "News & Talk" to listOf("news", "talk", "info", "speech", "current affairs", "politics"),
        "Sport" to listOf("sport", "sports", "football", "soccer"),
        "Dance & Electronic" to listOf("dance", "electronic", "house", "techno", "trance", "edm", "drum and bass"),
        "Hip-Hop & R&B" to listOf("hip hop", "hip-hop", "rap", "r&b", "rnb", "urban"),
        "Rock" to listOf("rock", "metal", "punk", "grunge", "indie"),
        "Pop" to listOf("pop", "top 40", "chart", "hits", "40s"),
        "Classical" to listOf("classical", "opera", "symphony", "baroque", "orchestral"),
        "Jazz & Blues" to listOf("jazz", "blues", "swing", "soul", "funk"),
        "Country & Folk" to listOf("country", "folk", "americana", "bluegrass"),
        "Oldies" to listOf("oldies", "classic hits", "60s", "70s", "80s", "90s", "retro", "nostalgia"),
        "Chill & Ambient" to listOf("chill", "ambient", "lounge", "relax", "easy listening", "downtempo"),
        "World" to listOf("world", "latin", "reggae", "afro", "balkan", "turkish", "arabic", "bollywood"),
        "Religious" to listOf("christian", "gospel", "religion", "religious", "quran", "islamic"),
        "Kids" to listOf("kids", "children", "family"),
    )

    private const val OTHER = "Other"

    /**
     * Picks a display genre for a station's tag soup.
     *
     * Two passes, and the order matters.
     *
     * An exact tag match is always a stronger signal than a substring one, so
     * every bucket gets a chance at an exact hit before any bucket is allowed
     * a partial. A single pass mixing the two filed a station tagged
     * "big band, jazz, lounge, swing, classic hits" under **Pop**, because
     * Pop sits above Jazz and its "hits" keyword appears inside
     * "classic hits". Exact-first puts it in Jazz & Blues, where a listener
     * would look for it.
     *
     * Substrings still earn their place in the second pass — "classic rock"
     * should reach Rock, and "smooth jazz" Jazz — they just no longer
     * outrank an exact match further down the list.
     */
    fun genreOf(tags: String): String {
        val parts = tags.lowercase().split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return OTHER
        for ((genre, keys) in GENRES) {
            if (parts.any { tag -> keys.any { k -> tag == k } }) return genre
        }
        for ((genre, keys) in GENRES) {
            if (parts.any { tag -> keys.any { k -> tag.contains(k) } }) return genre
        }
        return OTHER
    }

    /** ISO 3166-1 alpha-2 to something a human reads on a category chip. */
    fun countryName(code: String): String = COUNTRY_NAMES[code.uppercase()] ?: code.uppercase()

    /**
     * A station is only useful if it can actually be played and named.
     * radio-browser keeps dead entries around with `lastcheckok = 0`, and a
     * blank resolved URL is common on stations that have never been probed.
     */
    private fun usable(o: JsonObject): Boolean =
        !o.str("name").isNullOrBlank() &&
            !(o.str("url_resolved") ?: o.str("url")).isNullOrBlank() &&
            (o.int("lastcheckok") ?: 1) == 1

    /**
     * Fetches the most-played stations, newest check first.
     *
     * [country] narrows to one ISO code; null means everywhere. The directory
     * caps a single response at 100000, but a TV browser has no use for that
     * — [limit] keeps the payload and the grid sane.
     */
    suspend fun fetch(
        http: OkHttpClient,
        limit: Int = 500,
        country: String? = null,
    ): Result<List<Station>> = withContext(Dispatchers.IO) {
        runCatching {
            var lastError: Throwable? = null
            for (base in MIRRORS) {
                val url = buildString {
                    append(base)
                    append("/json/stations/search?hidebroken=true&order=clickcount&reverse=true")
                    append("&limit=").append(limit)
                    if (!country.isNullOrBlank()) append("&countrycode=").append(country.uppercase())
                }
                val req = Request.Builder().url(url).header("User-Agent", UA).get().build()
                try {
                    val stations = http.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        parse(resp.body.string())
                    }
                    // A mirror that answers 200 with an empty list is up but
                    // useless — fall through to the next one rather than
                    // reporting success with nothing in it.
                    if (stations.isNotEmpty()) return@runCatching stations
                } catch (t: Throwable) {
                    lastError = t
                }
            }
            throw lastError ?: IllegalStateException("No radio directory mirror answered")
        }
    }

    /**
     * A station as the directory describes it.
     *
     * Deliberately not a [Channel]: a station has a genre *and* a country, and
     * [Channel] has one category slot. An earlier draft smuggled the country
     * code through `epgId`, which would have fed a made-up id straight into
     * EPG lookups. Keeping the two apart here lets the browser group by
     * either, and the conversion to a playable [Channel] happens at the point
     * of play, where only the URL matters.
     */
    data class Station(
        val name: String,
        val url: String,
        val genre: String,
        val countryCode: String,
        val logo: String,
        val bitrate: Int,
        val codec: String,
    ) {
        val country: String get() = countryName(countryCode)
    }

    /** Split out so it can be tested without a network. */
    internal fun parse(body: String): List<Station> {
        val arr = LenientJson.parseToJsonElement(body) as? JsonArray ?: return emptyList()
        val seen = HashSet<String>()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            if (!usable(o)) return@mapNotNull null
            val stream = (o.str("url_resolved") ?: o.str("url"))!!.trim()
            // The directory carries plenty of duplicates — the same station
            // registered by several people under slightly different names.
            // Keying on the stream URL collapses them.
            if (!seen.add(stream)) return@mapNotNull null
            Station(
                name = o.str("name")!!.trim(),
                url = stream,
                genre = genreOf(o.str("tags").orEmpty()),
                countryCode = o.str("countrycode").orEmpty().uppercase(),
                logo = o.str("favicon").orEmpty(),
                bitrate = o.int("bitrate") ?: 0,
                codec = o.str("codec").orEmpty(),
            )
        }
    }

    /** Turns a station into something the player and the grid understand. */
    fun Station.toChannel(profileId: Long, index: Int): Channel = Channel(
        key = "$profileId:${ID_BASE + index}",
        profileId = profileId,
        streamId = ID_BASE + index,
        name = name,
        num = 0,
        logo = logo,
        categoryId = "radio_" + genre.lowercase().replace(Regex("[^a-z0-9]+"), "_"),
        categoryName = genre,
        // No EPG for internet radio, and inventing an id would send the guide
        // looking for listings that cannot exist.
        epgId = "",
        url = url,
        isRadio = true,
    )

    /** Genre buckets present in a result set, largest first, for the chips. */
    fun genres(stations: List<Station>): List<Pair<String, Int>> =
        stations.groupingBy { it.genre }.eachCount().toList().sortedWith(
            compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first },
        )

    /** Countries present in a result set, largest first, for the chips. */
    fun countries(stations: List<Station>): List<Pair<String, Int>> =
        stations.filter { it.countryCode.isNotBlank() }
            .groupingBy { it.country }.eachCount().toList().sortedWith(
                compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first },
            )

    /** Countries the directory actually carries in volume, for the chips. */
    private val COUNTRY_NAMES = mapOf(
        "AU" to "Australia", "AT" to "Austria", "BA" to "Bosnia", "BE" to "Belgium",
        "BR" to "Brazil", "CA" to "Canada", "CH" to "Switzerland", "CZ" to "Czechia",
        "DE" to "Germany", "DK" to "Denmark", "ES" to "Spain", "FI" to "Finland",
        "FR" to "France", "GB" to "United Kingdom", "GR" to "Greece", "HR" to "Croatia",
        "HU" to "Hungary", "IE" to "Ireland", "IN" to "India", "IT" to "Italy",
        "JP" to "Japan", "MX" to "Mexico", "NL" to "Netherlands", "NO" to "Norway",
        "NZ" to "New Zealand", "PL" to "Poland", "PT" to "Portugal", "RO" to "Romania",
        "RS" to "Serbia", "RU" to "Russia", "SE" to "Sweden", "SI" to "Slovenia",
        "SK" to "Slovakia", "TR" to "Türkiye", "UA" to "Ukraine", "US" to "United States",
        "ZA" to "South Africa",
    )
}
