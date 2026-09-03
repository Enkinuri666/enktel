package tv.enktel.app.data.repo

import java.net.URLEncoder

/**
 * Sending a viewer to buy or extend a line.
 *
 * ## Why there are no prices in here
 *
 * It is tempting to show the plan table on the Settings screen. It would also
 * be wrong: an APK is installed once and updated rarely, so a price compiled
 * into one is a price that keeps being advertised long after it changes, on
 * every device that has not taken the update. The web pricing page is the only
 * copy of those numbers that can be corrected in an afternoon, so the app
 * sends people there and quotes nothing itself.
 *
 * ## Why a URL is shown rather than only opened
 *
 * A sideloaded Fire TV Stick often has no browser at all, and an `ACTION_VIEW`
 * at one is a no-op that looks like a broken button — the same fault the IMDb
 * and trailer buttons were changed to avoid. So the caller checks whether
 * anything can handle the link first: if something can, it offers the button;
 * if nothing can, it shows [SHORT_PRICING] as text for the viewer to type into
 * the phone already in their hand.
 */
object Subscribe {

    private const val SITE = "https://enktel.tv"

    /** Where the plans and prices live. */
    const val PRICING = "$SITE/pricing"

    /** The free trial signup. */
    const val TRIAL = "$SITE/trial"

    /** Typeable on a phone, for the screens that cannot open a link. */
    const val SHORT_PRICING = "enktel.tv/pricing"

    /** Typeable on a phone, for the trial. */
    const val SHORT_TRIAL = "enktel.tv/trial"

    /**
     * Checkout for an existing line, carrying the username.
     *
     * The `renew` parameter is what makes the site extend this line rather
     * than issue a second one — a viewer who ends up with two lines has paid
     * twice and has to be refunded by hand, so the username is worth carrying
     * even though the site could ask for it again.
     *
     * Falls back to the plain pricing page when there is no username, which is
     * the M3U case: there is no account to extend.
     */
    fun renewUrl(username: String): String {
        val u = username.trim()
        if (u.isEmpty()) return PRICING
        return "$SITE/checkout?renew=${URLEncoder.encode(u, "UTF-8")}"
    }

    /**
     * What to say about a line that is running out, or null when there is
     * nothing worth saying.
     *
     * Null rather than an empty string: "nothing to report" is the common case
     * and a caller should skip the row entirely, not render a blank one.
     *
     * Deliberately silent above [NOTICE_DAYS]. A renewal prompt shown all year
     * is an advertisement; shown in the last fortnight it is a reminder, and
     * the difference is whether people read it.
     */
    fun expiryNotice(daysLeft: Int, expired: Boolean): String? = when {
        expired -> "This line has expired. Renew to start watching again."
        daysLeft < 0 -> null
        daysLeft == 0 -> "This line expires today. Renew to avoid losing access."
        daysLeft == 1 -> "This line expires tomorrow."
        daysLeft <= NOTICE_DAYS -> "This line expires in $daysLeft days."
        else -> null
    }

    /** How far out a renewal prompt starts appearing. */
    const val NOTICE_DAYS = 14
}
