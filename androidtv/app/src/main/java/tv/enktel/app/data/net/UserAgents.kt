package tv.enktel.app.data.net

/**
 * Which client the app claims to be, and who decides.
 *
 * ### Why this is a setting at all
 *
 * A panel that answers 403 to a request carrying perfectly valid credentials
 * is filtering on User-Agent, not rejecting the login. It is the single
 * highest-yield thing to change when a line works in one app and not another,
 * and it is why the app already presents as VLC rather than as OkHttp — some
 * panels answer OkHttp's default with a 407 before anything else happens.
 *
 * ### Why it belongs to the provider
 *
 * There was one global override, reachable only if the Panel Doctor happened
 * to suggest it. That is the wrong shape for anyone with more than one line:
 * the agent a strict panel demands is a fact about *that panel*, and forcing
 * it globally applies one provider's workaround to every other provider a
 * viewer has — including the ones that were working. So the choice lives on
 * the profile, and the global setting stays as the fallback it always was.
 *
 * ### The order
 *
 * Channel, then provider, then the global override, then the app default.
 * Narrowest wins, because each level exists to answer a problem the level
 * above it cannot see: a single channel served from a different CDN, a panel
 * with its own rules, a device-wide preference. [effective] is the whole rule
 * and is pinned by [UserAgentsTest].
 */
object UserAgents {

    /** What the app presents as when nothing overrides it. See DEFAULT_UA. */
    const val APP_DEFAULT = "VLC/3.0.20 LibVLC/3.0.20"

    /**
     * A suggestion the viewer can pick rather than type.
     *
     * These are long, exact, and unforgiving of a typo — a User-Agent with a
     * transposed digit does not fail loudly, it just keeps getting 403 while
     * looking correct on screen. Offering the known-good strings as a list is
     * most of the value of this feature.
     */
    data class Suggestion(
        /** Short name for the chip. */
        val label: String,
        /** The exact header value sent. */
        val value: String,
        /** When this is the one to reach for. */
        val hint: String,
    )

    /**
     * The agents worth trying, in the order worth trying them.
     *
     * Deliberately short. A list of forty is a list nobody reads, and these
     * cover the filtering rules panels in this space actually apply: allow
     * media players, allow set-top boxes, allow their own app, block
     * everything that looks like a script.
     */
    val SUGGESTIONS = listOf(
        Suggestion(
            "VLC (default)",
            APP_DEFAULT,
            "What the app already sends. The widest-accepted agent there is — start here.",
        ),
        Suggestion(
            "Smart TV",
            "Mozilla/5.0 (SMART-TV; Linux; Tizen 6.0) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Version/6.0 TV Safari/537.36",
            "For panels that allow televisions and block media players.",
        ),
        Suggestion(
            "Fire TV",
            "Mozilla/5.0 (Linux; Android 9; AFTKA Build/PS7233) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Version/4.0 Chrome/70.0.3538.110 Mobile Safari/537.36",
            "Presents as the Fire TV browser. Useful where the line was sold for a Fire Stick.",
        ),
        Suggestion(
            "IPTV Smarters",
            "IPTVSmartersPlayer/1.0",
            "Some resellers allow only the app they sell the line for.",
        ),
        Suggestion(
            "TiviMate",
            "TiviMate/4.7.0 (Android)",
            "Same reason as above, for lines sold against TiviMate.",
        ),
        Suggestion(
            "Kodi",
            "Kodi/20.2 (Linux; Android 12) libcurl/7.85.0",
            "Older panels often have a Kodi allow-rule from before they had an app.",
        ),
        Suggestion(
            "Chrome (desktop)",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/125.0.0.0 Safari/537.36",
            "Last resort. A few panels allow only what looks like a real browser.",
        ),
    )

    /** The [Suggestion] whose value is [ua], or null when it was typed by hand. */
    fun suggestionFor(ua: String): Suggestion? =
        SUGGESTIONS.firstOrNull { it.value == ua.trim() }

    /**
     * The agent to send, given every level that might have an opinion.
     *
     * Blank means "no opinion at this level", which is not the same as an
     * empty header — an empty User-Agent is itself a fingerprint that some
     * panels reject, so there is no path through this that returns "".
     */
    fun effective(
        channel: String = "",
        profile: String = "",
        global: String = "",
        default: String = APP_DEFAULT,
    ): String = channel.trim().ifBlank {
        profile.trim().ifBlank {
            global.trim().ifBlank {
                default.trim().ifBlank { APP_DEFAULT }
            }
        }
    }

    /**
     * A one-line description of where the agent in force came from.
     *
     * Shown in Settings, because "it is set to Smart TV" and "it is set to
     * Smart TV *and that is coming from the global override, not this
     * provider*" are different pieces of information, and only the second one
     * explains why changing the provider's setting appears to do nothing.
     */
    fun sourceOf(channel: String = "", profile: String = "", global: String = ""): String = when {
        channel.isNotBlank() -> "this channel"
        profile.isNotBlank() -> "this provider"
        global.isNotBlank() -> "the global override"
        else -> "the app default"
    }
}
