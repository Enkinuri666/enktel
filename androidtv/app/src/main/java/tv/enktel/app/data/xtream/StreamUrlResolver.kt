package tv.enktel.app.data.xtream

import tv.enktel.app.data.db.Channel
import tv.enktel.app.data.db.Movie
import tv.enktel.app.data.db.Profile

/**
 * Universal Xtream Codes stream-URL builder.
 *
 * Xtream panels are notoriously inconsistent about which URL shape they
 * actually serve behind `{server}` — some only answer HLS, some only
 * answer raw MPEG-TS, some drop the `/live/` segment entirely (older
 * reseller panels), and a few serve VOD/series without the extension the
 * `stream_icon`/`container_extension` field claims.  Rather than picking
 * one shape and hoping, this resolver returns every plausible candidate
 * in priority order so the player can fall through the list on failure
 * (see [tv.enktel.app.player.PlayerEngine.playCandidates]).
 *
 * Priority rationale:
 *   1. The user's preferred format (HLS by default — plays nicest with
 *      ExoPlayer's adaptive buffering and survives NAT/proxy weirdness).
 *   2. The other of {HLS, TS} under the standard `/live/` path.
 *   3. Extensionless `/live/{user}/{pass}/{id}` — some panels 404 on any
 *      extension and only serve the bare stream id as raw MPEG-TS.
 *   4. Legacy no-`/live/`-segment shape — `{server}/{user}/{pass}/{id}`,
 *      seen on older Xtream-compatible panels and some reseller mods.
 *
 * M3U playlists carry a literal URL per channel already (no Xtream
 * template to guess), so [forChannel] just returns that single URL
 * un-modified when the profile kind is "m3u".
 */
object StreamUrlResolver {

    /** Ordered candidate list for a Live TV channel. First entry is tried first. */
    fun forChannel(p: Profile, ch: Channel, preferHls: Boolean): List<String> {
        if (p.kind == "m3u") return listOf(ch.url).filter { it.isNotBlank() }
        val base = p.server.trimEnd('/')
        val user = p.username
        val pass = p.password
        val id = ch.streamId
        val primary = if (preferHls) "$base/live/$user/$pass/$id.m3u8" else "$base/live/$user/$pass/$id.ts"
        val secondary = if (preferHls) "$base/live/$user/$pass/$id.ts" else "$base/live/$user/$pass/$id.m3u8"
        val extensionless = "$base/live/$user/$pass/$id"
        val legacy = "$base/$user/$pass/$id"
        return listOf(primary, secondary, extensionless, legacy).distinct()
    }

    /** Ordered candidate list for a VOD movie. */
    fun forMovie(p: Profile, m: Movie): List<String> {
        if (p.kind == "m3u") return listOf(m.url).filter { it.isNotBlank() }
        val base = p.server.trimEnd('/')
        val ext = m.ext.ifBlank { "mp4" }
        val primary = "$base/movie/${p.username}/${p.password}/${m.streamId}.$ext"
        // Some panels lie about container_extension; mp4/mkv are the two
        // overwhelmingly common real containers, so try the other one
        // before giving up.
        val altExt = if (ext.equals("mp4", true)) "mkv" else "mp4"
        val altContainer = "$base/movie/${p.username}/${p.password}/${m.streamId}.$altExt"
        return listOf(primary, altContainer).distinct()
    }

    /** Ordered candidate list for a series episode. */
    fun forEpisode(p: Profile, episodeId: Long, ext: String): List<String> {
        val base = p.server.trimEnd('/')
        val e = ext.ifBlank { "mp4" }
        val primary = "$base/series/${p.username}/${p.password}/$episodeId.$e"
        val altExt = if (e.equals("mp4", true)) "mkv" else "mp4"
        val altContainer = "$base/series/${p.username}/${p.password}/$episodeId.$altExt"
        return listOf(primary, altContainer).distinct()
    }
}
