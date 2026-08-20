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
}
