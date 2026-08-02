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
            val candidate = Profile(name = name, kind = "xtream", server = normalized, username = username, password = password)
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

    private fun normalizeServer(raw: String): String {
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
