package tv.enktel.app.data.repo

import kotlinx.coroutines.flow.Flow
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.m3u.PlaylistFiles
import tv.enktel.app.data.db.ProfileDao
import tv.enktel.app.data.get
import tv.enktel.app.data.int
import tv.enktel.app.data.long
import tv.enktel.app.data.prefs.SettingsStore
import tv.enktel.app.data.str
import tv.enktel.app.data.net.EagleTrialClient
import tv.enktel.app.data.net.TrialCredentials
import tv.enktel.app.data.xtream.XtreamClient

class PlaylistRepository(
    private val dao: ProfileDao,
    private val settings: SettingsStore,
    private val xtream: XtreamClient,
    private val trialClient: EagleTrialClient? = null,
) {
    val profiles: Flow<List<Profile>> = dao.all()

    suspend fun byId(id: Long): Profile? = dao.byId(id)

    suspend fun activeProfile(): Profile? {
        val id = settings.activeProfileIdNow()
        return dao.byId(id) ?: dao.first()
    }

    /** Validates credentials against the panel, then stores the profile. */
    suspend fun addXtream(name: String, server: String, username: String, password: String): Result<Profile> =
        runCatching {
            val normalized = normalizeServer(server)
            // Don't guess the scheme — try it.
            //
            // A bare hostname gives no clue whether the panel is HTTP or HTTPS,
            // and getting it wrong presents as "the panel rejected the
            // credentials" when the credentials are fine. Rather than pick by
            // heuristic and be wrong for half of them, attempt the inferred
            // scheme and fall back to the other. Only when the user didn't
            // state one: an explicit http:// or https:// is always obeyed.
            val explicitScheme = server.trim().startsWith("http://", true) ||
                server.trim().startsWith("https://", true)
            val resolved = if (explicitScheme) normalized else reachableScheme(normalized, username, password)
            val candidate = Profile(name = name, kind = "xtream", server = resolved, username = username, password = password)
            val info = xtream.login(candidate)
            val user = info.get("user_info")
            val auth = user.int("auth") ?: 0
            check(auth == 1) { "Panel rejected the credentials" }
            val expires = user.long("exp_date")?.times(1000) ?: 0
            val maxConn = user.int("max_connections") ?: 0
            val saved = candidate.copy(expiresAt = expires, maxConnections = maxConn)
            val id = dao.insert(saved)
            settings.setActiveProfile(id)
            saved.copy(id = id)
        }

    /**
     * Sign this build's default line in, if it has one and there is no profile
     * yet.
     *
     * Returns null rather than a failed [Result] when there is nothing to do —
     * a build with no baked-in credentials, or a device that already has a
     * profile — because neither is an error and the caller should not report
     * one. A login that is attempted and *fails* returns the failure, so a
     * wrong or expired line still lands the viewer on the onboarding form
     * instead of an empty home screen.
     *
     * Calling this more than once is safe: the profile check happens first.
     */
    suspend fun seedDefaultProfile(): Result<Profile>? {
        if (dao.first() != null) return null

        // A build carrying a line signs into it; that account is the whole
        // catalog, so nothing else is worth seeding.
        if (DefaultLine.canSeed) {
            return addXtream(DefaultLine.NAME, DefaultLine.server, DefaultLine.username, DefaultLine.password)
        }

        // Otherwise fall back to the free-to-air playlist. A public build had
        // no credentials to seed with and so used to drop the viewer on the
        // login form with nothing to watch — but a few thousand of the
        // channels collected here need no account at all, and an install that
        // opens on live TV is a different product from one that opens on a
        // password field. The paid line is still one tap away in Settings.
        if (DefaultLine.hasFreePlaylist) {
            return addM3u(DefaultLine.FREE_NAME, DefaultLine.freePlaylistUrl, DefaultLine.freePlaylistEpg)
        }

        return null
    }

    /**
     * Turns a [TrialCredentials] payload into a validated Xtream profile.
     * Same login-then-persist pattern as [addXtream] so the panel gets a real
     * auth challenge before we save anything, but we tag the row with
     * "EnkTel 4K — Free Trial" and let the settings banner style it as a
     * trial (expiresAt < 24 h from now is the shipping heuristic).
     */
    suspend fun addTrial(creds: TrialCredentials): Result<Profile> = runCatching {
        val normalized = normalizeServer(creds.serverUrl)
        val candidate = Profile(
            name = "EnkTel 4K — Free Trial",
            kind = "xtream",
            server = normalized,
            username = creds.username,
            password = creds.password,
        )
        val info = xtream.login(candidate)
        val user = info.get("user_info")
        val auth = user.int("auth") ?: 0
        check(auth == 1) { "Trial credentials were rejected by the panel" }
        val panelExpires = user.long("exp_date")?.times(1000L) ?: 0L
        val maxConn = user.int("max_connections") ?: 0
        // Prefer the panel's exp_date if it came back; fall back to whatever the
        // trial API returned (which itself defaults to now + 24 h).
        val expires = if (panelExpires > 0) panelExpires else creds.expiresAt
        val saved = candidate.copy(expiresAt = expires, maxConnections = maxConn)
        val id = dao.insert(saved)
        settings.setActiveProfile(id)
        settings.setTrialUsed(true)
        settings.setTrialExpiresAt(expires)
        saved.copy(id = id)
    }

    /**
     * Import a playlist the viewer picked off their device.
     *
     * The document is copied into app storage first — see [PlaylistFiles] for
     * why a `content://` grant is not something a profile can hold onto.
     */
    suspend fun importM3u(ctx: android.content.Context, uri: android.net.Uri): Result<Profile> =
        runCatching {
            val url = PlaylistFiles.import(ctx, uri)
            addM3u(PlaylistFiles.displayName(ctx, uri), url, epgUrl = "").getOrThrow()
        }

    suspend fun addM3u(name: String, url: String, epgUrl: String): Result<Profile> = runCatching {
        require(url.startsWith("http") || PlaylistFiles.isLocal(url)) {
            "Playlist URL must start with http(s):// or be an imported file"
        }
        val profile = Profile(name = name, kind = "m3u", m3uUrl = url.trim(), epgUrl = epgUrl.trim())
        val id = dao.insert(profile)
        settings.setActiveProfile(id)
        profile.copy(id = id)
    }

    suspend fun switchTo(id: Long) = settings.setActiveProfile(id)

    suspend fun delete(id: Long) {
        // Drop the imported copy with the profile; nothing else references it,
        // and leaving it behind leaks a file the viewer cannot see or reach.
        runCatching { dao.byId(id)?.let { PlaylistFiles.forget(it.m3uUrl) } }
        dao.delete(id)
    }

    suspend fun markSynced(p: Profile) = dao.update(p.copy(lastSync = System.currentTimeMillis()))

    /**
     * Set the User-Agent this provider is served with. Blank clears it.
     *
     * Per provider rather than per device: the agent a strict panel demands is
     * a fact about that panel, and applying it globally would impose one
     * line's workaround on every other line the viewer has. See
     * [tv.enktel.app.data.net.UserAgents].
     */
    suspend fun setUserAgent(p: Profile, ua: String) = dao.update(p.copy(userAgent = ua.trim()))

    /**
     * Returns whichever of https:// / http:// the panel actually answers on,
     * preferring the one already in [normalized]. Falls back to [normalized]
     * unchanged when neither answers, so the caller still gets the real error.
     */
    private suspend fun reachableScheme(normalized: String, user: String, pass: String): String {
        val alternate = if (normalized.startsWith("https://", true)) {
            "http://" + normalized.removePrefix("https://").removePrefix("HTTPS://")
        } else {
            "https://" + normalized.removePrefix("http://").removePrefix("HTTP://")
        }
        for (url in listOf(normalized, alternate)) {
            val ok = runCatching {
                val probe = Profile(name = "probe", kind = "xtream", server = url, username = user, password = pass)
                xtream.login(probe).get("user_info").int("auth") == 1
            }.getOrDefault(false)
            if (ok) return url
        }
        return normalized
    }

    companion object {
        // internal, not private, so PlaylistRepositoryTest can pin the scheme and
        // path-stripping rules. Getting these wrong presents to the user as "the
        // panel rejected the credentials", which is the hardest kind of bug to
        // report, so they are worth a test.
        internal fun normalizeServer(raw: String): String {
            var s = raw.trim().trimEnd('/')
            // Default to HTTPS, not HTTP.
            //
            // A user who types "x-api.cc" rather than the full URL was silently
            // given http://, and an HTTPS-only panel then either redirects (which
            // the Xtream API handles badly) or refuses outright — presenting as
            // "the panel rejected the credentials" when the credentials were fine.
            // Panels that are HTTP-only still work: they are almost always entered
            // with an explicit port, and an explicit scheme is always respected.
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) {
                // A bare host:port on a non-standard port is nearly always plain
                // HTTP in this ecosystem; a bare hostname is nearly always HTTPS.
                val hostPart = s.substringBefore('/')
                val port = hostPart.substringAfterLast(':', "").toIntOrNull()
                s = if (port != null && port != 443) "http://$s" else "https://$s"
            }
            // Strip accidental paths — people paste whatever the reseller sent them.
            s = s.substringBefore("/player_api.php")
                .substringBefore("/get.php")
                .substringBefore("/panel_api.php")
                .substringBefore("/xmltv.php")
            // …and any query string that came with it.
            s = s.substringBefore('?').trimEnd('/')
            return s
        }
    }
}
