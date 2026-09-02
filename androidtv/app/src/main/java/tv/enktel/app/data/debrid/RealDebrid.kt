package tv.enktel.app.data.debrid

import tv.enktel.app.data.repo.EnktelFeed

/**
 * The parts of Real-Debrid integration that are decisions rather than I/O.
 *
 * Real-Debrid is a service the viewer pays for: it takes a link the viewer
 * already has and returns a direct, unthrottled one, and it holds the files
 * they have added to their own account. This app is a client for that account
 * — it presents the viewer's token, reads back what the service says is
 * theirs, and plays it. It does not search anywhere for content to feed it.
 *
 * Kept free of OkHttp so the rules below can be tested. See [RealDebridClient]
 * for the requests themselves.
 */
object RealDebrid {

    const val BASE = "https://api.real-debrid.com/rest/1.0"

    /**
     * The published ceiling, and it is stricter than it looks.
     *
     * The API allows 250 requests a minute — but refused requests **also
     * count**, and the documentation warns that hammering it leaves the
     * account blocked for an undefined period. So a 429 is not a signal to
     * retry harder; backing off is the only response that does not make the
     * situation worse, which is why [describeFailure] says so rather than
     * offering a retry.
     */
    const val REQUESTS_PER_MINUTE = 250

    /** Tokens are issued as a long alphanumeric string. */
    private val TOKEN_SHAPE = Regex("^[A-Za-z0-9]{20,120}$")

    /**
     * Clean up a token the viewer pasted, or return "" if it is not one.
     *
     * People paste from a web page, so what arrives carries newlines, stray
     * spaces and sometimes the whole surrounding URL. Whitespace is removed
     * rather than merely trimmed — a token split across two lines by the
     * browser is still a valid token — but anything that survives and is not
     * a bare alphanumeric string is rejected, because storing a URL as a
     * token produces a 401 that reads like "your account is wrong" when the
     * real problem is that the wrong text was copied.
     */
    fun normaliseToken(raw: String): String {
        // Join across line breaks, trimming each piece, then require the shape.
        //
        // Removing *all* whitespace instead is the obvious version and it is
        // wrong: it turns "my token is <token>" into one long alphanumeric
        // string that passes the shape test and then fails at the API as a
        // 401, which reads as "your account is wrong". Splitting on line
        // breaks keeps the case that actually happens — a browser soft-wrapping
        // the token, with or without indentation on the next line — while a
        // surviving space means words, not a token.
        val joined = raw.split('\n', '\r').joinToString("") { it.trim() }
        return if (TOKEN_SHAPE.matches(joined)) joined else ""
    }

    /**
     * What went wrong, in words the viewer can act on.
     *
     * The API's own `error` string is included when there is one, because it
     * distinguishes cases this cannot — but it is not shown alone: it is
     * written for a developer reading a response body, not for someone
     * looking at a television.
     */
    fun describeFailure(httpCode: Int, apiError: String? = null): String {
        val detail = apiError?.trim().orEmpty()
        val base = when (httpCode) {
            401 -> "Real-Debrid rejected the token. Check it in Settings, or generate a new one."
            403 -> "Real-Debrid says this account is locked. Sign in on their site to see why."
            // The one that must not suggest retrying: refused requests count
            // toward the same limit that refused them.
            429 -> "Too many requests to Real-Debrid. Wait a minute before trying again — " +
                "retrying now makes the block last longer."
            503 -> "Real-Debrid could not produce a link. The file may be dead, " +
                "the hoster unsupported, or the account's traffic used up."
            404 -> "Real-Debrid has no record of that item."
            in 500..599 -> "Real-Debrid is having trouble. Try again shortly."
            else -> "Real-Debrid returned an error (HTTP $httpCode)."
        }
        return if (detail.isEmpty()) base else "$base ($detail)"
    }

    /**
     * Whole days from today until the account lapses, or null when the date
     * cannot be read.
     *
     * Deliberately null rather than 0 for an unparseable date. Zero means
     * "expires today", which would tell a viewer with a healthy account that
     * it is about to end.
     */
    fun daysLeft(expiryIso: String, todayEpochDay: Long): Int? {
        val day = EnktelFeed.parseIsoDateToEpochDay(expiryIso) ?: return null
        return (day - todayEpochDay).toInt()
    }

    /** True for the account types that can actually unrestrict anything. */
    fun isPremium(type: String, daysLeft: Int?): Boolean =
        type.trim().equals("premium", ignoreCase = true) && (daysLeft == null || daysLeft >= 0)

    /**
     * One line describing the account, for the Settings row.
     *
     * Says the state first and the detail second, because the state is what
     * someone opening Settings is checking.
     */
    fun accountLine(username: String, type: String, expiryIso: String, todayEpochDay: Long): String {
        val name = username.trim().ifEmpty { "Real-Debrid" }
        val days = daysLeft(expiryIso, todayEpochDay)
        return when {
            !type.trim().equals("premium", ignoreCase = true) ->
                "$name · free account — unrestricting needs premium"
            days == null -> "$name · premium"
            days < 0 -> "$name · premium expired"
            days == 0 -> "$name · premium, expires today"
            days == 1 -> "$name · premium, 1 day left"
            else -> "$name · premium, $days days left"
        }
    }
}
