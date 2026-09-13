package tv.enktel.app.data.repo

import tv.enktel.app.i18n.EnglishStrings
import tv.enktel.app.i18n.Strings
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
    fun expiryNotice(
        daysLeft: Int,
        expired: Boolean,
        /**
         * Defaulted to English so the existing callers and `SubscribeTest`
         * read unchanged; the two screens that show this pass the language the
         * app is actually in. The day count goes through [Strings.days]
         * because Serbo-Croatian agrees three ways — 1 dan, 2–4 dana, 5+
         * dana — and "ističe za 21 dana" is the sentence that gives away a
         * translation done by substitution.
         */
        t: Strings = EnglishStrings,
    ): String? = when {
        expired -> t.lineExpired
        daysLeft < 0 -> null
        daysLeft == 0 -> t.lineExpiresToday
        daysLeft == 1 -> t.lineExpiresTomorrow
        daysLeft <= NOTICE_DAYS -> t.lineExpiresIn.format(t.days(daysLeft))
        else -> null
    }

    /** How far out a renewal prompt starts appearing. */
    const val NOTICE_DAYS = 14
}
