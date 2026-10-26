package tv.enktel.app.data.repo

/**
 * The teams in a fixture title, and whether one is named in a line of text.
 *
 * ### Why the matching needed its own file
 *
 * Following a team was a plain substring test — `followed.any { it.name in
 * title.lowercase() }` — which is wrong in both directions and quietly.
 *
 * It matches too much: follow **Roma** and the "my teams" filter keeps every
 * fixture in *Romania*; follow **Milan** and it keeps *Milano*; follow **Ajax**
 * and a misspelt *Ajaxx* counts. The viewer sees a filter they asked to narrow
 * the list hand back matches they have no interest in, which reads as the
 * filter not working rather than as a near-miss on a word.
 *
 * And it matches too little, because the provider's spelling is not the sports
 * database's: a line reads `Arsenal FC`, `Arsenal F.C.` or `ARSENAL  LONDON`,
 * and the first two fail a test for the stored `arsenal fc` the moment the
 * punctuation differs. Comparing whole words instead means all of them answer.
 *
 * ### Why the parse exists
 *
 * Following a team lived only in Settings, four screens away from the sport,
 * and had to be typed correctly. [parse] turns "Premier League: Arsenal v
 * Chelsea" into the two sides so the hub can offer them as something to press
 * — the difference between a feature people use and a text field nobody finds.
 */
object FixtureTeams {

    /**
     * The unambiguous separator: "v", "vs", "vs.", "@".
     *
     * Tried first, and that order is the whole reason there are two. A title
     * routinely carries both — "Arsenal v Chelsea — Premier League",
     * "Australia v India — 3rd Test, Day 2" — and treating the dash as a
     * separator of equal standing turns those into three sides, which is
     * rejected, which is a star missing from the most ordinary fixture title
     * there is. Split on the "v" and the dash is what it actually is there:
     * the competition hanging off the end.
     */
    private val VERSUS = Regex("""\s+(?:vs?\.?|@)\s+""", RegexOption.IGNORE_CASE)

    /**
     * The fallback: "Ajax - PSV", "Roma – Lazio".
     *
     * Spaces are required around the dash on purpose: a hyphen inside a name
     * ("Saint-Étienne") is part of the word, and splitting there invents a
     * team called "Étienne".
     */
    private val DASH = Regex("""\s+[-–—]\s+""")

    /** Trailing decoration on a side: "(Live)", "[1080p]", "(2nd leg)". */
    private val TRAILING = Regex("""\s*[\[(].{0,30}[\])]\s*$""")

    /**
     * Suffixes that decorate a club rather than name one.
     *
     * Dropped from the *stored* name before matching, so following "Arsenal
     * FC" still answers a line that writes the club as plain "Arsenal".
     * Deliberately only the abbreviations: "United" and "City" look like
     * decoration and are nothing of the kind — drop those and following
     * Manchester United matches Manchester City, which is a worse failure
     * than the one this class exists to fix.
     */
    private val CLUB_SUFFIX = setOf(
        "fc", "cf", "sc", "ac", "afc", "cfc", "sk", "fk", "bk", "cd", "ud",
        "rc", "as", "ss", "ssc",
    )

    /** Words that cannot be a side on their own — a state, not a club. */
    private val NOT_A_TEAM = CLUB_SUFFIX + setOf(
        "live", "match", "final", "replay", "highlights", "coverage", "build",
        "up", "preview", "full", "game", "round", "day", "week", "and", "the",
        "united", "city", "club", "team", "sport", "sports", "hd", "sd", "fhd",
    )

    /** A side with more words than this is a sentence, not a club. */
    private const val MAX_SIDE_WORDS = 5

    /**
     * The two sides of a fixture, or empty when the title is not one.
     *
     * Empty rather than a best guess: an offer to follow something that is not
     * a team is worse than no offer, because it gets stored and then silently
     * matches nothing for ever.
     */
    fun parse(title: String): List<String> {
        val body = title.trim()
        if (body.isEmpty()) return emptyList()
        return sidesSplitOn(body, VERSUS) ?: sidesSplitOn(body, DASH) ?: emptyList()
    }

    /**
     * The two sides, if [separator] splits [body] into exactly two of them.
     *
     * Exactly two: three means the separator also appears in the competition
     * ("Arsenal v Chelsea v Spurs" is not a fixture either), and guessing
     * which two of the three are the teams is how a wrong name gets stored and
     * then matches nothing for ever.
     */
    private fun sidesSplitOn(body: String, separator: Regex): List<String>? {
        val parts = body.split(separator)
        if (parts.size != 2) return null
        val sides = listOf(cleanSide(parts[0], leading = true), cleanSide(parts[1], leading = false))
        if (sides.any { !looksLikeTeam(it) }) return null
        return sides
    }

    /**
     * One side, stripped of the decoration a provider hangs off it.
     *
     * The qualifier is handled by position, which is the whole trick. A
     * competition is written either in front of the fixture ("Premier League:
     * Arsenal v Chelsea") or behind it ("Arsenal v Chelsea — Premier League"),
     * and after the split that is text before the mark on the first side and
     * after it on the second. Stripping a leading qualifier with one regex
     * over the whole title looks equivalent and is not: on the second shape it
     * matches "Arsenal v Chelsea:" and eats both teams.
     */
    private fun cleanSide(raw: String, leading: Boolean): String =
        TRAILING.replace(raw, "")
            .let { if (leading) it.substringAfterLast(':') else it.substringBefore(':') }
            // The same competition, written with a dash instead of a colon:
            // "Arsenal v Chelsea — Premier League", "Live — Ajax v PSV". A
            // spaced dash never appears inside a club's name, so cutting at it
            // costs nothing on a side that was already clean — including the
            // sides this very dash just produced.
            .let { DASH.split(it).let { bits -> if (leading) bits.last() else bits.first() } }
            .trim()
            .trim('-', '–', '—', '·', '|', '.', ',')
            .trim()

    /**
     * Is [s] plausibly the name of a side?
     *
     * Two letters at the least, something alphabetic in it, few enough words
     * to be a name, and not purely the decoration a provider hangs off a
     * fixture: "1" is a scoreline, "HD" is a feed, "Live" is a state.
     */
    private fun looksLikeTeam(s: String): Boolean {
        if (s.length < 2 || s.length > 48) return false
        if (s.none { it.isLetter() }) return false
        val words = words(s)
        if (words.isEmpty() || words.size > MAX_SIDE_WORDS) return false
        return words.any { it !in NOT_A_TEAM }
    }

    /**
     * Is [team] named anywhere in [haystack]?
     *
     * Every word of the team has to appear in the text as a whole word. That
     * is what separates *Roma* from *Romania* — "romania" is one word and it
     * is not "roma" — while a club suffix on the stored name is allowed to go
     * missing from the text, because "Arsenal FC" and "Arsenal" are the same
     * club written two ways.
     *
     * Case and punctuation are ignored on both sides, so `ARSENAL F.C.` and
     * `arsenal fc` are the same request.
     */
    fun mentions(haystack: String, team: String): Boolean {
        val all = words(team)
        if (all.isEmpty()) return false
        // Keep the suffix only when it is all the name has.
        val want = all.filterTo(HashSet()) { it !in CLUB_SUFFIX }.ifEmpty { all }
        val have = words(haystack)
        if (have.isEmpty()) return false
        return have.containsAll(want)
    }

    /**
     * The whole words of a string, lowercased.
     *
     * A set rather than a list: the question is only ever whether a word is
     * there, and a fixture that names a club twice is not a stronger match
     * than one that names it once.
     */
    private fun words(raw: String): Set<String> =
        raw.lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filterTo(HashSet()) { it.isNotBlank() }
}
