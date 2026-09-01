package tv.enktel.app.data.repo

import tv.enktel.app.BuildConfig

/**
 * The line a fresh install starts on.
 *
 * Without this the app opens on the onboarding form and stays there until
 * someone types a panel address from memory. With it, the server is already
 * filled in and — for a build that was given credentials — the first launch
 * goes straight to the channel list.
 *
 * ## Why the credentials are blank by default
 *
 * An APK is a zip file. Anything compiled into it is readable by anyone who
 * downloads it: `unzip`, `strings`, done. A username and password baked into a
 * public build is a published username and password, and an Xtream line is
 * capped at a few simultaneous connections — so a shared one is not merely
 * leaked, it stops working for its owner.
 *
 * So the **server** ships (it is a public hostname, and prefilling it is the
 * whole convenience) and the **credentials** do not. A private build can bake
 * them in for testing:
 *
 * ```
 * ./gradlew :app:assembleTvDebug \
 *   -PenkDefaultUser=… -PenkDefaultPass=…
 * ```
 *
 * or via `ENK_DEFAULT_USER` / `ENK_DEFAULT_PASS` in the environment. Neither
 * form touches a committed file. Builds made that way are for the person who
 * owns the line, not for distribution.
 */
object DefaultLine {
    /** Panel address prefilled on the onboarding form. */
    val server: String get() = BuildConfig.DEFAULT_SERVER

    /** Blank unless this build was given credentials at build time. */
    val username: String get() = BuildConfig.DEFAULT_USERNAME

    /** Blank unless this build was given credentials at build time. */
    val password: String get() = BuildConfig.DEFAULT_PASSWORD

    /** Display name for the seeded profile. */
    const val NAME = "EnkTel"

    /**
     * Can this build sign in without being asked?
     *
     * All three parts are required. A server with no credentials is a
     * prefilled form, which is useful; a credential with no server is not
     * something to attempt a login with.
     */
    val canSeed: Boolean
        get() = server.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    /**
     * The free-to-air playlist a build with no line falls back to.
     *
     * These are the public streams the scraper collects and liveness-checks —
     * whole URLs that belong to their broadcasters and carry nothing private,
     * so they play with no account at all. That is the difference between them
     * and the panel catalogs: a panel's stream URL *contains* the line's
     * username and password, so there is no version of it that plays without a
     * login. Anything a viewer can watch before signing in comes from here.
     */
    val freePlaylistUrl: String get() = BuildConfig.FREE_PLAYLIST_URL

    /** Optional guide for [freePlaylistUrl]; blank means use the playlist's own. */
    val freePlaylistEpg: String get() = BuildConfig.FREE_PLAYLIST_EPG

    /** Display name for the seeded free-to-air profile. */
    const val FREE_NAME = "EnkTel Free-to-Air"

    /** Is there a free playlist to fall back to when [canSeed] is false? */
    val hasFreePlaylist: Boolean
        get() = freePlaylistUrl.startsWith("http")

    /**
     * The on-demand half of the free tier.
     *
     * [freePlaylistUrl] carries live channels, so a viewer with no line got
     * live TV and an empty Movies tab. This is the catalogue
     * `scripts/scrape-vod.mjs` collects: public-domain and openly-licensed
     * films, documentaries and series, every one of which carries both a
     * redistribution licence and membership of a curated archive collection.
     *
     * Published from this project rather than linked from the archive
     * directly, for the same reason the lineup is — a viewer's network has to
     * reach one host we control, not whichever host a generated file happened
     * to name.
     */
    val freeVodUrl: String get() = BuildConfig.FREE_VOD_URL

    /** Is there a free film library to seed alongside the channels? */
    val hasFreeVod: Boolean
        get() = freeVodUrl.startsWith("http")
}
