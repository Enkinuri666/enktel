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

    /** Ordered candidate list for a VOD movie. Widened to also try ts / avi
     *  containers — some Xtream panels report `container_extension` as `mp4`
     *  but only actually serve `.ts` or (rarely) `.avi`. */
    fun forMovie(p: Profile, m: Movie): List<String> {
        if (p.kind == "m3u") return listOf(m.url).filter { it.isNotBlank() }
        val base = p.server.trimEnd('/')
        val ext = m.ext.ifBlank { "mp4" }.lowercase()
        val prefix = "$base/movie/${p.username}/${p.password}/${m.streamId}"
        val ordered = listOf(ext, "mp4", "mkv", "ts", "avi").distinct()
        return ordered.map { "$prefix.$it" }
    }

    /** Ordered candidate list for a series episode. Same widening as [forMovie]. */
    fun forEpisode(p: Profile, episodeId: Long, ext: String): List<String> {
        val base = p.server.trimEnd('/')
        val e = ext.ifBlank { "mp4" }.lowercase()
        val prefix = "$base/series/${p.username}/${p.password}/$episodeId"
        val ordered = listOf(e, "mp4", "mkv", "ts", "avi").distinct()
        return ordered.map { "$prefix.$it" }
    }
}
