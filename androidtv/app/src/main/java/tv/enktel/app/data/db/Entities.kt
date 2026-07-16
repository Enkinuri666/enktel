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

@Entity(tableName = "movies", indices = [Index("profileId"), Index("profileId", "categoryId")])
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
)

@Entity(tableName = "series", indices = [Index("profileId"), Index("profileId", "categoryId")])
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
)
