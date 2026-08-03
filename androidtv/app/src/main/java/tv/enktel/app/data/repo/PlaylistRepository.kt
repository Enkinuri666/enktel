package tv.enktel.app.data.repo

import kotlinx.coroutines.flow.Flow
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.db.ProfileDao
import tv.enktel.app.data.get
import tv.enktel.app.data.int
import tv.enktel.app.data.long
import tv.enktel.app.data.prefs.SettingsStore
import tv.enktel.app.data.str
import tv.enktel.app.data.xtream.XtreamClient

class PlaylistRepository(
    private val dao: ProfileDao,
    private val settings: SettingsStore,
    private val xtream: XtreamClient,
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

    suspend fun addM3u(name: String, url: String, epgUrl: String): Result<Profile> = runCatching {
        require(url.startsWith("http")) { "Playlist URL must start with http(s)://" }
        val profile = Profile(name = name, kind = "m3u", m3uUrl = url.trim(), epgUrl = epgUrl.trim())
        val id = dao.insert(profile)
        settings.setActiveProfile(id)
        profile.copy(id = id)
    }

    suspend fun switchTo(id: Long) = settings.setActiveProfile(id)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun markSynced(p: Profile) = dao.update(p.copy(lastSync = System.currentTimeMillis()))

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
