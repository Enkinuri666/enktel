package tv.enktel.app.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import tv.enktel.app.data.db.Profile
import tv.enktel.app.data.m3u.ImportMigration
import tv.enktel.app.data.m3u.ImportedPlaylist
import tv.enktel.app.data.m3u.PlaylistFiles
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
    /**
     * Only for clearing the rows of a profile this repository removes.
     * Nullable so the repository still constructs without one; the rows are
     * then left behind, which is wasteful but not visible — every query is
     * filtered by profile id.
     */
    private val content: tv.enktel.app.data.db.ContentDao? = null,
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
     * What an import did: the profile the channels were added to, and the
     * attachment record, or null when the file became the first profile.
     */
    data class Imported(val profile: Profile, val attached: ImportedPlaylist?)

    /**
     * Import a playlist the viewer picked off their device, **adding** it to
     * what they already have.
     *
     * This used to build a profile out of the file and make it active. Every
     * screen reads the active profile, so a twenty-channel file replaced the
     * viewer's whole lineup: the old channels were still in the database under
     * a profile nothing was pointing at, which presents as having lost them.
     * An import is an addition, so it attaches to the profile that is already
     * open and its channels are merged into that catalogue on the next sync,
     * under categories named after the file.
     *
     * The document is copied into app storage first — see [PlaylistFiles] for
     * why a `content://` grant is not something a profile can hold onto.
     *
     * A device with no profile at all is the one case that still creates one:
     * there is nothing to add onto, and an attachment belonging to no profile
     * would never be read.
     */
    suspend fun importM3u(ctx: android.content.Context, uri: android.net.Uri): Result<Imported> =
        runCatching {
            val url = PlaylistFiles.copyIn(ctx, uri)
            val name = PlaylistFiles.displayName(ctx, uri)
            val host = activeProfile()
            if (host == null) {
                Imported(addM3u(name, url, epgUrl = "").getOrThrow(), attached = null)
            } else {
                Imported(host, settings.addImportedPlaylist(host.id, name, url))
            }
        }

    /** Playlist files attached to a profile, across all profiles. */
    val importedPlaylists: Flow<List<ImportedPlaylist>> = settings.importedPlaylists

    /** What a migration did, for the caller that has to re-sync afterwards. */
    data class Migrated(val hostId: Long, val converted: Int)

    /**
     * Convert imports made before attachments existed.
     *
     * Those imports are profiles, and a profile is shown *instead of* the
     * lineup rather than alongside it — so a viewer with three old imports has
     * three separate lineups to switch between instead of one that holds
     * everything. This folds each of them into the profile they should have
     * been added to in the first place.
     *
     * The copied file is deliberately **not** deleted: the attachment now
     * points at it, and `PlaylistFiles.forget` would take the channels with
     * it.
     *
     * Order matters for the same reason. The attachment record is stored
     * before the profile row is removed, so an interruption can only ever
     * leave a file referenced twice rather than not at all — and the check
     * below then makes even that harmless on the next run.
     *
     * What is lost is small and worth naming: a converted profile's own EPG
     * URL and its provider User-Agent do not survive, because an attachment
     * has neither. The host's guide covers the channels, and the per-channel
     * agents from `#EXTVLCOPT` — which is where these files carry them — are
     * parsed as before.
     *
     * @return null when there was nothing to convert
     */
    suspend fun migrateImportedProfiles(): Migrated? {
        val all = dao.all().first()
        val plan = ImportMigration.plan(all, settings.activeProfileIdNow()) ?: return null

        // A file already attached is one a previous run stored before it was
        // interrupted. Attaching it again would show its channels twice.
        val attached = settings.importedPlaylistsNow().map { it.url }.toHashSet()

        for (p in plan.convert) {
            if (attached.add(p.m3uUrl)) {
                settings.addImportedPlaylist(plan.hostId, p.name, p.m3uUrl)
            }
            // The catalogue rows of a profile that is going away. Nothing
            // reads them once its id is gone, so this is space rather than
            // correctness — but it is a whole channel list per import.
            content?.let {
                it.clearChannels(p.id)
                it.clearCategories(p.id)
                it.clearMovies(p.id)
                it.clearSeries(p.id)
            }
            dao.delete(p.id)
        }

        // Done last: the viewer may have had one of the converted profiles
        // open, and leaving `activeProfile` on a deleted id shows an empty app.
        plan.activeMovesTo?.let { settings.setActiveProfile(it) }

        return Migrated(hostId = plan.hostId, converted = plan.convert.size)
    }

    /**
     * Detach a file and delete the copy behind it.
     *
     * The channels it contributed stay in the database until the next sync,
     * which is what the caller should trigger — nothing else knows those rows
     * came from this file.
     */
    suspend fun removeImportedPlaylist(id: Long) {
        settings.removeImportedPlaylist(id)?.let { PlaylistFiles.forget(it.url) }
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
        // Same for anything attached to it. These outlive the profile
        // otherwise — they live in preferences, which `dao.delete` never
        // touches — and would be read forever against a profile id that no
        // longer exists.
        runCatching {
            settings.removeImportedPlaylistsFor(id).forEach { PlaylistFiles.forget(it.url) }
        }
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
