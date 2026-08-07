package tv.enktel.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
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
    /**
     * `catchup="…"` from the playlist — default | append | shift | flussonic | xc.
     *
     * Parsed since the Panel Doctor work and thrown away at sync time, so
     * playback had nothing to go on and every catch-up path was hard-gated to
     * Xtream profiles. Blank on Xtream lines, where the scheme is implied.
     */
    val catchupType: String = "",
    /** `catchup-source="…"` URL template, when the provider supplies one. */
    val catchupSource: String = "",
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
    /**
     * When this title first appeared in *this user's* catalogue, ms.
     *
     * Distinct from [addedAt], which is the panel's own `added` timestamp:
     * that is missing entirely on M3U lines and is often the date the
     * provider ingested the file rather than the date it reached this line.
     * A re-sync knows exactly which stream ids were not there before, and
     * that is the only signal that actually answers "what is new to me".
     */
    val firstSeenAt: Long = 0,
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
    /**
     * Synopsis. Movies had no plot column at all — Series has always had one —
     * so a movie's description was whatever the details screen could fetch
     * live from the panel, or nothing.
     */
    val plot: String = "",
    /**
     * Landscape hero art from TMDB, already a full URL (see [TmdbImages]).
     *
     * Separate from [poster] because they are different shapes for different
     * jobs: the poster is 2:3 box art for a rail, this is 16:9 and is what the
     * details page and the ambient glow behind a rail actually want. Blurring
     * a poster to fill a 16:9 background is what they did before.
     */
    val backdrop: String = "",
    /** Runtime in minutes from TMDB, 0 when unknown. */
    val runtimeMins: Int = 0,
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
    /** See [Movie.firstSeenAt]. */
    val firstSeenAt: Long = 0,
    val tmdbId: Long = 0,
    val studios: String = "",
    val tags: String = "",
    val enrichedAt: Long = 0,
    /** See [Movie.backdrop]. */
    val backdrop: String = "",
    /** Average episode runtime in minutes from TMDB, 0 when unknown. */
    val runtimeMins: Int = 0,
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

/**
 * A user-created themed list — "Kids Saturday", "Footy", "Rainy Sunday".
 *
 * Deliberately separate from both favourites and the watchlist. Favourites is
 * one flat starred set per kind; the watchlist is "things I mean to watch".
 * Neither can express "these nine channels and four films belong together",
 * which is what a themed list is for, and which is why it has to span kinds.
 */
@Entity(tableName = "user_lists", indices = [Index("profileId")])
data class UserList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    /** One emoji shown beside the name. Purely decorative. */
    val icon: String = "📁",
    val createdAt: Long = System.currentTimeMillis(),
    val sortIdx: Int = 0,
)

/**
 * One entry in a [UserList].
 *
 * Name and poster are denormalised on purpose: a list should survive a
 * playlist that drops the title. The row still renders, so the user can see
 * what they lost and remove it, rather than the list silently shrinking.
 */
@Entity(tableName = "user_list_items", indices = [Index("listId"), Index("listId", "itemKey")])
data class UserListItem(
    /** "$listId:$itemKey" */
    @PrimaryKey val key: String,
    val listId: Long,
    /** "live" | "vod" | "series" */
    val kind: String,
    /** The Channel / Movie / Series row key. */
    val itemKey: String,
    val name: String,
    val poster: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

/**
 * Full-text search index over the VOD catalogue.
 *
 * Search was seven `LIKE '%q%'` comparisons per row across two tables. A
 * leading wildcard makes every index in SQLite useless, so that is a full scan
 * of the whole catalogue on every keystroke — fine on a demo playlist, and on a
 * hundred-thousand-title line it is exactly the stutter it looks like.
 *
 * FTS4 builds an inverted index instead: the cost is proportional to the number
 * of *matches*, not the size of the table. It also tokenises, so "bat man"
 * finds "The Batman" and "batman returns", which the LIKE version could not do
 * at all — a user typing two words got nothing.
 *
 * Standalone rather than `contentEntity`-backed. An external-content FTS table
 * needs SQLite triggers to stay in sync and Room does not generate them, so it
 * would silently drift the first time a row changed. This one is rebuilt inside
 * the same sync that replaces the catalogue, which is the only moment the
 * contents can change, so it cannot drift.
 */
@Fts4
@Entity(tableName = "movies_fts")
data class MovieFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    /** The movie's `key`, so a hit can be resolved back to its row. */
    val itemKey: String,
    val profileId: Long,
    /** Everything worth matching on, flattened into one indexed column. */
    val body: String,
)

@Fts4
@Entity(tableName = "series_fts")
data class SeriesFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val itemKey: String,
    val profileId: Long,
    val body: String,
)
