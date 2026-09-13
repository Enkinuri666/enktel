package tv.enktel.app.i18n

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How a language agrees a noun with a number.
 *
 * Not a detail that can be left to a format string. English needs two forms
 * and Serbo-Croatian needs three, and the three do not follow the tens: 21 is
 * *dan*, 22 is *dana*, but 11 and 12 are both *dana*. A single rule applied to
 * both languages gets one of them wrong every time — the first attempt here
 * used the Slavic rule everywhere and produced "21 day" in English, which is
 * what the test in `StringsTest` was written to catch.
 */
enum class PluralRule {
    /** one / other. */
    ENGLISH,

    /** one / few / other, with 11–14 taking the last form despite their ending. */
    SLAVIC,
    ;

    /** 0 = one, 1 = few, 2 = many/other. */
    fun formFor(n: Int): Int = when (this) {
        ENGLISH -> if (n == 1) 0 else 2
        SLAVIC -> {
            val ones = n % 10
            val tens = n % 100
            when {
                ones == 1 && tens != 11 -> 0
                ones in 2..4 && tens !in 12..14 -> 1
                else -> 2
            }
        }
    }
}

/**
 * The app's own words, in more than one language.
 *
 * ## Why a Kotlin catalogue and not `res/values-hr/strings.xml`
 *
 * The platform way would be resource files and a per-app locale. That was the
 * first plan and it lost on three counts. Per-app locales below API 33 need
 * AppCompat, which this app does not use — it is Compose plus
 * `androidx.tv.material3`, and pulling in an activity hierarchy to change a
 * language is a large change to make invisibly. Switching a locale that way
 * also recreates the activity, so the viewer's place on the screen goes; here
 * the language is a [androidx.compose.runtime.CompositionLocal], so flipping
 * it redraws in place with the scroll position intact. And a typed catalogue
 * can be checked: `StringsTest` asserts that every screen this covers has a
 * value in every language, which an XML file silently missing a `<string>`
 * cannot give you.
 *
 * ## What is translated, and what is not
 *
 * Deliberately not everything. Roughly eighty strings — the ones a new
 * subscriber actually reads — are covered here: signing in, the menu, the
 * home tiles, More, search, and the top of Settings. The deep screens
 * (diagnostics, the download engine, the system monitor, the player's own
 * chrome) are still English, and they say so nowhere, which is honest only
 * because the setting itself says so.
 *
 * Nothing that comes from the provider is touched, and nothing here can touch
 * it: channel names, film titles, category names and the TV guide are the
 * panel's own text arriving over the wire. A viewer who switches to
 * Srpskohrvatski and finds "Live TV" is now "TV uživo" while the channel
 * underneath is still called "UK | Sky Sports Main Event" has not hit a bug,
 * and the setting's own description says as much rather than leaving them to
 * work it out.
 *
 * ## "Serbo-Croatian"
 *
 * Not a current ISO language — the standards are Croatian, Serbian, Bosnian
 * and Montenegrin, and they are mutually intelligible. One Latin-script
 * translation serves all four, which is what the region's own broadcasters
 * do, and it is labelled with the name people in the diaspora actually use
 * for it rather than with a flag. Cyrillic is a second script rather than a
 * second language, and is a separate job.
 */
data class Strings(
    /** The id persisted in settings. */
    val id: String,
    /** How this language names itself, for the picker. */
    val endonym: String,
    /** How this language agrees a noun with a number. See [days]. */
    val plural: PluralRule,

    // ---- menu ----------------------------------------------------------
    val navHome: String,
    val navLive: String,
    val navGuide: String,
    val navMovies: String,
    val navSeries: String,
    val navSports: String,
    val navComingSoon: String,
    val navWatchlist: String,
    val navLists: String,
    val navDownloads: String,
    val navRecordings: String,
    val navCatchup: String,
    val navSearch: String,
    val navSettings: String,
    val navMore: String,

    // ---- More ----------------------------------------------------------
    val moreSubtitle: String,
    val blurbGuide: String,
    val blurbComingSoon: String,
    val blurbWatchlist: String,
    val blurbLists: String,
    val blurbDownloads: String,
    val blurbRecordings: String,
    val blurbCatchup: String,
    val blurbSettings: String,

    // ---- signing in ----------------------------------------------------
    val signInTitle: String,
    val signInLede: String,
    val username: String,
    val password: String,
    /** `%s` is the host, e.g. x-api.cc. */
    val connectingTo: String,
    val differentProvider: String,
    val serverOrLink: String,
    val serverOrLinkHelp: String,
    val signIn: String,
    val working: String,
    val signingIn: String,
    val loadingChannels: String,
    val loadingGuide: String,
    val detailsStayOnDevice: String,
    /** `%s` is the trial address. */
    val noAccountYet: String,

    // ---- what goes wrong -----------------------------------------------
    val errNeedCredentials: String,
    val errAlmostThere: String,
    val errRejected: String,
    val errUnknownHost: String,
    val errTimeout: String,
    val errCertificate: String,
    val errGeneric: String,
    /** `%s` is the underlying message. */
    val errSignedInNoChannels: String,

    // ---- search --------------------------------------------------------
    val searchTitle: String,
    val searchHint: String,
    val searchHintShort: String,
    val scopeAll: String,
    val scopeGuide: String,
    val recentSearches: String,
    val clear: String,
    /** `%s` is the query. */
    val noMatchesFor: String,
    val noMatchesHelp: String,
    val railChannels: String,
    val railSport: String,
    val railInGuide: String,
    val live: String,
    val ended: String,
    /** `%d` is how many other channels carry the same fixture. */
    val alsoOnMore: String,

    // ---- settings ------------------------------------------------------
    val settingsTitle: String,
    val language: String,
    val languageFollowDevice: String,
    val languageNote: String,
    val menuSimple: String,
    val menuFull: String,
    val menuSimpleHelp: String,
    val menuFullHelp: String,
    val runDiagnostics: String,
    val systemMonitor: String,
    val manageCategories: String,
    val diagnosticsHelp: String,

    // ---- the line ------------------------------------------------------
    val lineExpired: String,
    val lineExpiresToday: String,
    val lineExpiresTomorrow: String,
    /** `%s` is the already-pluralised day count, e.g. "3 dana". */
    val lineExpiresIn: String,
    val dayOne: String,
    val dayFew: String,
    val dayMany: String,

    /**
     * Sport tags, keyed by the English tag `SportsRepository` produces.
     *
     * A map rather than thirty more constructor fields, which the compiler
     * would then have to be told about one at a time. The completeness
     * guarantee moves to `StringsTest`, which walks
     * `SportsRepository.SPORT_NAMES` — the real list the classifier can emit —
     * so a sport added there and forgotten here fails a test rather than
     * printing "Wrestling" in the middle of a Serbo-Croatian card.
     */
    val sports: Map<String, String>,
) {
    /**
     * The locale to format dates and times with.
     *
     * Follows the *app's* language, not the device's. Someone on an English
     * phone who chose Srpskohrvatski has said which language they read; a
     * fixture card reading "Sun 7:11 AM · Fudbal" is the kind of half-and-half
     * that makes a translation feel unfinished.
     *
     * Croatian rather than a Serbian tag for the Latin script: `hr` is
     * Latin-only, so the day names come back as Latin on every platform,
     * whereas `sr` depends on the device's ICU data choosing a script.
     */
    val locale: java.util.Locale
        get() = if (id == Lang.SH) java.util.Locale.forLanguageTag("hr") else java.util.Locale.getDefault()

    /**
     * Day-and-time pattern for a fixture card.
     *
     * Twelve-hour with AM/PM is an English-speaking convention; the region
     * this translation is for writes 19:45 and would read "7:45 PM" as a
     * foreign clock.
     */
    val dayTimeFormat: String
        get() = if (id == Lang.SH) "EEE HH:mm" else "EEE h:mm a"

    /** What to call a sport. Falls back to the English tag. */
    fun sport(tag: String): String = sports[tag] ?: tag

    /**
     * The menu label for a nav id.
     *
     * A `when` rather than a map so a missing case is a compile error inside
     * this file; the id coming in from [tv.enktel.app.ui.components.TvNavItem]
     * is still only checked by `StringsTest`, which walks the real menu and
     * asserts every entry resolves in every language. That test is the reason
     * a new destination cannot ship half-translated.
     */
    fun navLabel(id: String): String? = when (id) {
        "home" -> navHome
        "live" -> navLive
        "guide" -> navGuide
        "movies" -> navMovies
        "series" -> navSeries
        "sports" -> navSports
        "comingSoon" -> navComingSoon
        "watchlist" -> navWatchlist
        "lists" -> navLists
        "downloads" -> navDownloads
        "recordings" -> navRecordings
        "catchup" -> navCatchup
        "search" -> navSearch
        "settings" -> navSettings
        "more" -> navMore
        else -> null
    }

    /** The one-line description under a tile on the More screen. */
    fun moreBlurb(id: String): String? = when (id) {
        "guide" -> blurbGuide
        "comingSoon" -> blurbComingSoon
        "watchlist" -> blurbWatchlist
        "lists" -> blurbLists
        "downloads" -> blurbDownloads
        "recordings" -> blurbRecordings
        "catchup" -> blurbCatchup
        "settings" -> blurbSettings
        else -> null
    }

    /**
     * "3 days" / "3 dana", agreed the way this language agrees.
     *
     * A function and not a format string because the agreement is the point:
     * 1 dan, 2–4 dana, 5+ dana, and then it starts again at 21 dan. Getting
     * this wrong is the classic tell that a translation was done by
     * substitution, and "ističe za 21 dana" is the sentence that gives it away.
     */
    fun days(n: Int): String {
        val word = when (plural.formFor(n)) {
            0 -> dayOne
            1 -> dayFew
            else -> dayMany
        }
        return "$n $word"
    }
}

/** Language ids, as persisted. */
object Lang {
    /** Take the device's own setting. The default. */
    const val SYSTEM = "system"
    const val EN = "en"

    /**
     * Latin-script Serbo-Croatian.
     *
     * `sh` is the retired ISO code for exactly this, and it is the honest one
     * to use for a single translation covering hr/sr/bs/cnr. It never reaches
     * Android's resource resolver — it is only a key in this file and in the
     * settings store — so nothing depends on the platform still knowing it.
     */
    const val SH = "sh"

    /** Device locales that should land on [SH] when the setting is [SYSTEM]. */
    private val SH_TAGS = setOf("sh", "hr", "sr", "bs", "cnr", "sr-latn", "hbs")

    /**
     * Which catalogue to use, given the stored setting and the device.
     *
     * @param deviceTags BCP-47 tags in the device's own preference order.
     */
    fun resolve(setting: String, deviceTags: List<String>): String {
        if (setting == EN || setting == SH) return setting
        for (tag in deviceTags) {
            val lower = tag.lowercase()
            val primary = lower.substringBefore('-')
            if (lower in SH_TAGS || primary in SH_TAGS) return SH
            if (primary == "en") return EN
        }
        return EN
    }
}

val EnglishStrings = Strings(
    id = Lang.EN,
    endonym = "English",
    plural = PluralRule.ENGLISH,

    navHome = "Home",
    navLive = "Live TV",
    navGuide = "TV Guide",
    navMovies = "Movies",
    navSeries = "Series",
    navSports = "Sports",
    navComingSoon = "Coming Soon",
    navWatchlist = "Watchlist",
    navLists = "My Lists",
    navDownloads = "Downloads",
    navRecordings = "Recordings",
    navCatchup = "Catch-Up",
    navSearch = "Search",
    navSettings = "Settings",
    navMore = "More",

    moreSubtitle = "Everything else the app can do.",
    blurbGuide = "What's on now and next, channel by channel",
    blurbComingSoon = "Films and series arriving soon",
    blurbWatchlist = "Titles you saved to watch later",
    blurbLists = "Playlists you built yourself",
    blurbDownloads = "Saved to this device — watch with no internet",
    blurbRecordings = "Shows you set the app to record",
    blurbCatchup = "Replay something that has already aired",
    blurbSettings = "Playback, account, diagnostics",

    signInTitle = "Sign in to EnkTel",
    signInLede = "Use the username and password from your welcome email.",
    username = "Username",
    password = "Password",
    connectingTo = "Connecting to %s",
    differentProvider = "Different provider, or a setup link",
    serverOrLink = "Server address, or paste your setup link",
    serverOrLinkHelp = "A link from your provider works here too — it carries the server, " +
        "and often the login as well.",
    signIn = "Sign in",
    working = "Working…",
    signingIn = "Signing in…",
    loadingChannels = "Loading your channels…",
    loadingGuide = "Loading the TV guide…",
    detailsStayOnDevice = "Your details stay on this device.",
    noAccountYet = "No account yet? Start a free 24-hour trial at %s",

    errNeedCredentials = "Enter the username and password your provider sent you.",
    errAlmostThere = "Almost there — now your username and password.",
    errRejected = "That username or password wasn't accepted. Check them for a stray space, " +
        "then try again.",
    errUnknownHost = "Couldn't reach that address. Check your internet, and check the address " +
        "for a typo.",
    errTimeout = "The provider didn't answer in time. It may be busy — try again in a minute.",
    errCertificate = "That server's security certificate couldn't be checked. Try http:// " +
        "instead of https://.",
    errGeneric = "Couldn't connect. Check the link and try again.",
    errSignedInNoChannels = "Signed in, but the channel list did not load: %s",

    searchTitle = "Search",
    searchHint = "Title, cast, director, genre, channel name or number…",
    searchHintShort = "Channel, match, film or show…",
    scopeAll = "All",
    scopeGuide = "Guide",
    recentSearches = "Recent searches",
    clear = "Clear",
    noMatchesFor = "No matches for \"%s\"",
    noMatchesHelp = "Try a shorter query, an actor's name, or a genre.",
    railChannels = "Channels",
    railSport = "Sport",
    railInGuide = "In the Guide",
    live = "LIVE",
    ended = "Ended",
    alsoOnMore = "+%d more",

    settingsTitle = "Settings",
    language = "Language",
    languageFollowDevice = "Follow this device",
    languageNote = "Changes the app's own text. Channel, film and programme names come from " +
        "your provider and stay as they are. Some deeper screens are still English.",
    menuSimple = "Menu: Simple — show all destinations",
    menuFull = "Menu: Full — show fewer destinations",
    menuSimpleHelp = "The menu shows Home, Live TV, Movies, Series, Sports and Search, with " +
        "the rest under More. Nothing is hidden — this only changes what is offered first.",
    menuFullHelp = "The menu shows all fourteen destinations.",
    runDiagnostics = "🩺  Run connection diagnostics",
    systemMonitor = "📈  System monitor",
    manageCategories = "🗂  Manage categories",
    diagnosticsHelp = "Diagnostics tests your network, the panel's URL shapes, your connection " +
        "cap and the HTTP/TLS path — locally, no browser needed.",

    lineExpired = "This line has expired. Renew to start watching again.",
    lineExpiresToday = "This line expires today. Renew to avoid losing access.",
    lineExpiresTomorrow = "This line expires tomorrow.",
    lineExpiresIn = "This line expires in %s.",
    dayOne = "day",
    dayFew = "days",
    dayMany = "days",

    sports = mapOf(
        "Football" to "Football",
        "American Football" to "American Football",
        "Basketball" to "Basketball",
        "Baseball" to "Baseball",
        "Hockey" to "Hockey",
        "MMA/Boxing" to "MMA/Boxing",
        "Tennis" to "Tennis",
        "Cricket" to "Cricket",
        "Motor Racing" to "Motor Racing",
        "Cycling" to "Cycling",
        "Golf" to "Golf",
        "Rugby" to "Rugby",
        "Wrestling" to "Wrestling",
        "Combat" to "Combat",
        "Darts" to "Darts",
        "Snooker" to "Snooker",
        "Handball" to "Handball",
        "Volleyball" to "Volleyball",
        "Athletics" to "Athletics",
        "Esports" to "Esports",
        "Other" to "Other",
    ),
)

/**
 * Latin-script Serbo-Croatian (BCMS).
 *
 * Written to be read aloud by someone who has never used an IPTV app, which
 * is a different job from translating word for word. "Line" is the reseller's
 * term and means nothing to a subscriber, so it is *pretplata* — the
 * subscription — everywhere it appears. "Catch-Up" has no accepted noun, so
 * the tile says what you do with it rather than naming it.
 */
val SerboCroatianStrings = Strings(
    id = Lang.SH,
    endonym = "Srpskohrvatski",
    plural = PluralRule.SLAVIC,

    navHome = "Početna",
    navLive = "TV uživo",
    navGuide = "TV vodič",
    navMovies = "Filmovi",
    navSeries = "Serije",
    navSports = "Sport",
    navComingSoon = "Uskoro",
    navWatchlist = "Za gledanje",
    navLists = "Moje liste",
    navDownloads = "Preuzimanja",
    navRecordings = "Snimci",
    navCatchup = "Propušteno",
    navSearch = "Pretraga",
    navSettings = "Podešavanja",
    navMore = "Još",

    moreSubtitle = "Sve ostalo što aplikacija može.",
    blurbGuide = "Šta je na programu sada i sledeće, kanal po kanal",
    blurbComingSoon = "Filmovi i serije koji uskoro stižu",
    blurbWatchlist = "Naslovi koje ste sačuvali za kasnije",
    blurbLists = "Liste koje ste sami napravili",
    blurbDownloads = "Sačuvano na uređaj — gledajte bez interneta",
    blurbRecordings = "Emisije koje ste zakazali za snimanje",
    blurbCatchup = "Pogledajte ponovo ono što je već emitovano",
    blurbSettings = "Reprodukcija, nalog, dijagnostika",

    signInTitle = "Prijavite se na EnkTel",
    signInLede = "Unesite korisničko ime i lozinku iz e-poruke dobrodošlice.",
    username = "Korisničko ime",
    password = "Lozinka",
    connectingTo = "Povezivanje na %s",
    differentProvider = "Drugi provajder ili link za podešavanje",
    serverOrLink = "Adresa servera ili nalepite link za podešavanje",
    serverOrLinkHelp = "Link od vašeg provajdera takođe radi ovde — sadrži adresu servera, " +
        "a često i podatke za prijavu.",
    signIn = "Prijavi se",
    working = "Radim…",
    signingIn = "Prijavljivanje…",
    loadingChannels = "Učitavanje kanala…",
    loadingGuide = "Učitavanje TV vodiča…",
    detailsStayOnDevice = "Vaši podaci ostaju na ovom uređaju.",
    noAccountYet = "Nemate nalog? Pokrenite besplatnu probu od 24 sata na %s",

    errNeedCredentials = "Unesite korisničko ime i lozinku koje vam je poslao provajder.",
    errAlmostThere = "Još malo — sada korisničko ime i lozinku.",
    errRejected = "Korisničko ime ili lozinka nisu prihvaćeni. Proverite da nema suvišnog " +
        "razmaka pa pokušajte ponovo.",
    errUnknownHost = "Nije moguće doći do te adrese. Proverite internet i da li je adresa " +
        "ispravno napisana.",
    errTimeout = "Provajder nije odgovorio na vreme. Možda je zauzet — pokušajte za minut.",
    errCertificate = "Bezbednosni sertifikat servera nije mogao da se proveri. Pokušajte sa " +
        "http:// umesto https://.",
    errGeneric = "Povezivanje nije uspelo. Proverite link i pokušajte ponovo.",
    errSignedInNoChannels = "Prijava je uspela, ali lista kanala nije učitana: %s",

    searchTitle = "Pretraga",
    searchHint = "Naslov, glumci, režiser, žanr, ime ili broj kanala…",
    searchHintShort = "Kanal, utakmica, film ili serija…",
    scopeAll = "Sve",
    scopeGuide = "Vodič",
    recentSearches = "Nedavne pretrage",
    clear = "Obriši",
    noMatchesFor = "Nema rezultata za „%s”",
    noMatchesHelp = "Pokušajte kraći pojam, ime glumca ili žanr.",
    railChannels = "Kanali",
    railSport = "Sport",
    railInGuide = "U vodiču",
    live = "UŽIVO",
    ended = "Završeno",
    alsoOnMore = "još %d",

    settingsTitle = "Podešavanja",
    language = "Jezik",
    languageFollowDevice = "Prati uređaj",
    languageNote = "Menja tekst same aplikacije. Imena kanala, filmova i emisija dolaze od " +
        "vašeg provajdera i ostaju nepromenjena. Neki dublji ekrani su i dalje na engleskom.",
    menuSimple = "Meni: jednostavan — prikaži sve stavke",
    menuFull = "Meni: puni — prikaži manje stavki",
    menuSimpleHelp = "Meni prikazuje Početnu, TV uživo, Filmove, Serije, Sport i Pretragu, a " +
        "ostalo je pod Još. Ništa nije sakriveno — ovo menja samo šta se nudi prvo.",
    menuFullHelp = "Meni prikazuje svih četrnaest stavki.",
    runDiagnostics = "🩺  Pokreni dijagnostiku veze",
    systemMonitor = "📈  Monitor sistema",
    manageCategories = "🗂  Upravljanje kategorijama",
    diagnosticsHelp = "Dijagnostika testira vašu mrežu, oblike URL adresa panela, ograničenje " +
        "broja veza i HTTP/TLS putanju — lokalno, bez pregledača.",

    lineExpired = "Pretplata je istekla. Obnovite je da biste nastavili da gledate.",
    lineExpiresToday = "Pretplata ističe danas. Obnovite je da ne izgubite pristup.",
    lineExpiresTomorrow = "Pretplata ističe sutra.",
    lineExpiresIn = "Pretplata ističe za %s.",
    dayOne = "dan",
    dayFew = "dana",
    dayMany = "dana",

    sports = mapOf(
        // "Football" is association football here, as it is in the EPG this
        // reads; the American game gets its own tag and its own name.
        "Football" to "Fudbal",
        "American Football" to "Američki fudbal",
        "Basketball" to "Košarka",
        "Baseball" to "Bejzbol",
        "Hockey" to "Hokej",
        "MMA/Boxing" to "MMA/Boks",
        "Tennis" to "Tenis",
        "Cricket" to "Kriket",
        "Motor Racing" to "Auto-moto sport",
        "Cycling" to "Biciklizam",
        "Golf" to "Golf",
        "Rugby" to "Ragbi",
        "Wrestling" to "Rvanje",
        "Combat" to "Borilački sportovi",
        "Darts" to "Pikado",
        "Snooker" to "Snuker",
        "Handball" to "Rukomet",
        "Volleyball" to "Odbojka",
        "Athletics" to "Atletika",
        "Esports" to "Esport",
        "Other" to "Ostalo",
    ),
)

/** Every catalogue, in the order the picker offers them. */
val AllStrings = listOf(EnglishStrings, SerboCroatianStrings)

fun stringsFor(id: String): Strings = AllStrings.firstOrNull { it.id == id } ?: EnglishStrings

/**
 * The language the tree is being drawn in.
 *
 * `static` because it changes about once in the life of an install, and a
 * dynamic one would invalidate every reader on every recomposition of the
 * provider.
 */
val LocalStrings = staticCompositionLocalOf { EnglishStrings }
