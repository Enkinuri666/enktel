package tv.enktel.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "xtream" or "m3u" */
    val kind: String,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    val epgUrl: String = "",
    val lastSync: Long = 0,
    val expiresAt: Long = 0,
    val maxConnections: Int = 0,
)

@Entity(tableName = "channels", indices = [Index("profileId"), Index("profileId", "categoryId")])
data class Channel(
    /** "$profileId:$streamId" */
    @PrimaryKey val key: String,
    val profileId: Long,
    val streamId: Long,
    val name: String,
    val num: Int = 0,
    val logo: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val epgId: String = "",
    /** direct URL for m3u playlists; empty for xtream (built on demand) */
    val url: String = "",
    val hasArchive: Boolean = false,
    val archiveDays: Int = 0,
    val sortIdx: Int = 0,
    /** Audio-only stream. Xtream reports `stream_type: "radio"`; M3U uses `radio="true"`. */
    val isRadio: Boolean = false,
    /**
     * Per-channel User-Agent from `#EXTVLCOPT:http-user-agent=`.
     *
     * Some sources answer only for one specific UA and 403 everything else, so
     * a single global override cannot fix them without breaking the rest of the
     * playlist. Empty means "use the app default".
     */
    val userAgent: String = "",
)

@Entity(tableName = "categories")
data class Category(
    /** "$profileId:$kind:$categoryId" */
    @PrimaryKey val key: String,
    val profileId: Long,
    /** "live" | "vod" | "series" */
    val kind: String,
    val categoryId: String,
    val name: String,
    val sortIdx: Int = 0,
)

@Entity(tableName = "movies", indices = [Index("profileId"), Index("profileId", "categoryId"), Index("tmdbId")])
data class Movie(
    /** "$profileId:$streamId" */
    @PrimaryKey val key: String,
    val profileId: Long,
    val streamId: Long,
    val name: String,
    val poster: String = "",
    val categoryId: String = "",
    val rating: Double = 0.0,
    val ext: String = "mp4",
    val addedAt: Long = 0,
    val url: String = "",
    val genre: String = "",
    val year: Int = 0,
    val cast: String = "",
    val director: String = "",
    // v1.20.0 metadata enrichment — populated first from Xtream (tmdbId), then
    // overwritten by MetadataEnrichmentWorker with the extended TMDB payload.
    val tmdbId: Long = 0,
    /** Comma-separated production studios (e.g. "Warner Bros.,Legendary"). */
    val studios: String = "",
    /** Comma-separated lowercased normalised keywords + tags — used by the
     *  themed home rails (The Phenomenon, Deep Dive Documentaries, etc.) and
     *  by the advanced search. */
    val tags: String = "",
    /** Unix ms of the last successful TMDB enrichment — 0 means never. */
    val enrichedAt: Long = 0,
)

@Entity(tableName = "series", indices = [Index("profileId"), Index("profileId", "categoryId"), Index("tmdbId")])
data class Series(
    /** "$profileId:$seriesId" */
    @PrimaryKey val key: String,
    val profileId: Long,
    val seriesId: Long,
    val name: String,
    val poster: String = "",
    val categoryId: String = "",
    val rating: Double = 0.0,
    val plot: String = "",
    val genre: String = "",
    val year: Int = 0,
    val cast: String = "",
    val director: String = "",
    val tmdbId: Long = 0,
    val studios: String = "",
    val tags: String = "",
    val enrichedAt: Long = 0,
)

@Entity(tableName = "watchlist")
data class WatchlistItem(
    /** "$profileId:$kind:$refId" */
    @PrimaryKey val key: String,
    val profileId: Long,
    /** "vod" | "series" */
    val kind: String,
    val refId: Long,
    val name: String,
    val poster: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "search_history")
data class SearchHistoryItem(
    @PrimaryKey val query: String,
    val usedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "followed_teams")
data class FollowedTeam(
    /** lowercased team name (or league) */
    @PrimaryKey val name: String,
    val displayName: String,
    val kind: String = "team", // team | league
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "match_reminders")
data class MatchReminder(
    /** "$channelKey:$startMs" */
    @PrimaryKey val key: String,
    val channelKey: String,
    val channelName: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val scheduledAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "epg", indices = [Index("profileId", "epgId", "startMs")])
data class EpgProgram(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val epgId: String,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val desc: String = "",
)

@Entity(tableName = "favorites")
data class Favorite(
    /** "$profileId:$kind:$refId" */
    @PrimaryKey val key: String,
    val profileId: Long,
    /** "live" | "vod" | "series" */
    val kind: String,
    val refId: Long,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "progress")
data class WatchProgress(
    /** "$profileId:$kind:$refId" */
    @PrimaryKey val key: String,
    val profileId: Long,
    /** "vod" | "episode" */
    val kind: String,
    val refId: Long,
    val name: String,
    val poster: String = "",
    val url: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val title: String,
    val channelName: String,
    val streamUrl: String,
    val filePath: String = "",
    /** SCHEDULED | RECORDING | DONE | FAILED | CANCELLED */
    val status: String,
    val startMs: Long,
    val endMs: Long,
    val sizeBytes: Long = 0,
    val channelLogo: String = "",
)

/**
 * A single offline download entry — one row per movie or episode the user has
 * queued or saved locally. Media3's own DownloadManager keeps the byte-level
 * cache; this table holds the user-facing metadata (name, poster, kind, parent
 * series) plus a snapshot of progress so the UI can render offline without
 * subscribing to the DownloadManager listener stream on every scroll.
 */
@Entity(tableName = "downloads", indices = [Index("profileId"), Index("kind"), Index("seriesKey")])
data class DownloadEntry(
    /** Stable id — matches the Media3 Download#request.id used to enqueue. */
    @PrimaryKey val id: String,
    val profileId: Long,
    /** "movie" | "episode" */
    val kind: String,
    val refId: Long,
    /** For "episode": the "$profileId:$seriesId" key of the parent series so
     *  we can group episodes together in the Downloads screen. */
    val seriesKey: String = "",
    val seriesName: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val title: String,
    val poster: String = "",
    /** Remote URL that was queued. */
    val sourceUrl: String,
    /** Local path once the download is DONE — empty while in flight. */
    val filePath: String = "",
    /** QUEUED | RUNNING | PAUSED | DONE | FAILED */
    val status: String = "QUEUED",
    val progressPct: Int = 0,
    val sizeBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val errorMessage: String = "",
    /** Which engine owns this download: "parallel" (resumable, ranged OkHttp)
     *  or "system" (platform DownloadManager — no pause API, so pausing one
     *  hands it over to the parallel engine on the next resume). */
    val engine: String = "parallel",
    /** Opaque per-segment byte offsets so a paused / interrupted parallel
     *  download picks up exactly where it stopped instead of restarting.
     *  Encoded by ParallelDownloader.encodeState; blank means "start fresh". */
    val resumeState: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
