package tv.enktel.app.data.m3u

import tv.enktel.app.data.db.Profile

/**
 * Turning the imports made before attachments existed into attachments.
 *
 * Every import used to become a profile of its own. Those profiles still work
 * — the viewer can switch between them — but they behave the old way: opening
 * one shows that file's channels *instead of* the lineup, which is the thing
 * attachments were introduced to stop. A viewer with three old imports has
 * three lineups they have to choose between rather than one they can see at
 * once.
 *
 * The decision is separated from the doing because it is the part that can be
 * wrong in a way nothing would report: picking the wrong host attaches a
 * viewer's channels to a profile they never use, and converting the wrong
 * profile would take a real subscription out of the profile list.
 *
 * ### Idempotent by construction
 *
 * Deliberately not guarded by a "migration done" flag. Running it a second
 * time plans nothing, because after the first run there is at most one local
 * file left as a profile and that one is the host. A flag would add the one
 * failure mode this cannot otherwise have: an error on the single attempt
 * leaving a viewer with old-style imports and no way to retry.
 */
object ImportMigration {

    /**
     * A profile that is really an import.
     *
     * `file://` is the marker: it means this app copied the playlist into its
     * own storage, which only ever happens at import. A subscribed M3U profile
     * holds an `http(s)://` URL, so the build's own free-to-air playlist —
     * also `kind = "m3u"` — is correctly not one of these.
     */
    fun isOldImport(p: Profile): Boolean =
        p.kind == "m3u" && PlaylistFiles.isLocal(p.m3uUrl)

    data class Plan(
        /** The profile the converted files attach to. */
        val hostId: Long,
        /** Profiles to turn into attachments and then remove. */
        val convert: List<Profile>,
        /**
         * Where the active profile has to move, or null if it is unaffected.
         *
         * Converting the profile the viewer currently has open would otherwise
         * leave the app pointing at an id that no longer exists.
         */
        val activeMovesTo: Long?,
    )

    /**
     * What to convert, and what to attach it to. Null when there is nothing
     * worth doing.
     *
     * The host is the viewer's real provider wherever there is one, preferring
     * the profile they are actually using. When a device has *only* imports
     * there is no real provider to attach to, so the oldest import keeps its
     * place as a profile and the rest attach to it — that leaves the viewer
     * with one combined lineup, which is the point, rather than with nothing.
     */
    fun plan(profiles: List<Profile>, activeId: Long): Plan? {
        val imports = profiles.filter { isOldImport(it) }
        if (imports.isEmpty()) return null

        val real = profiles.filter { !isOldImport(it) }
        val host = real.firstOrNull { it.id == activeId }
            ?: real.minByOrNull { it.id }
            ?: imports.minByOrNull { it.id }
            ?: return null

        val convert = imports.filter { it.id != host.id }
        if (convert.isEmpty()) return null

        return Plan(
            hostId = host.id,
            convert = convert,
            activeMovesTo = if (convert.any { it.id == activeId }) host.id else null,
        )
    }
}
