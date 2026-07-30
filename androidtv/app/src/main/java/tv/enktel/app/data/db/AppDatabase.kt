package tv.enktel.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Profile::class, Channel::class, Category::class, Movie::class, Series::class,
        EpgProgram::class, Favorite::class, WatchProgress::class, Recording::class,
        WatchlistItem::class, SearchHistoryItem::class, FollowedTeam::class, MatchReminder::class,
        DownloadEntry::class,
    ],
    version = 7, // v7 adds resumable-download bookkeeping (engine, resumeState) on downloads
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun contentDao(): ContentDao
    abstract fun epgDao(): EpgDao
    abstract fun userDao(): UserDao
    abstract fun recordingDao(): RecordingDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun searchDao(): SearchDao
    abstract fun sportsDao(): SportsDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movies ADD COLUMN genre TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE movies ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE series ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movies ADD COLUMN cast TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE movies ADD COLUMN director TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE series ADD COLUMN cast TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE series ADD COLUMN director TEXT NOT NULL DEFAULT ''")
                db.execSQL("""CREATE TABLE IF NOT EXISTS watchlist (
                    key TEXT NOT NULL PRIMARY KEY,
                    profileId INTEGER NOT NULL,
                    kind TEXT NOT NULL,
                    refId INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    poster TEXT NOT NULL DEFAULT '',
                    addedAt INTEGER NOT NULL
                )""".trimIndent())
                db.execSQL("""CREATE TABLE IF NOT EXISTS search_history (
                    query TEXT NOT NULL PRIMARY KEY,
                    usedAt INTEGER NOT NULL
                )""".trimIndent())
                db.execSQL("""CREATE TABLE IF NOT EXISTS followed_teams (
                    name TEXT NOT NULL PRIMARY KEY,
                    displayName TEXT NOT NULL,
                    kind TEXT NOT NULL DEFAULT 'team',
                    addedAt INTEGER NOT NULL
                )""".trimIndent())
                db.execSQL("""CREATE TABLE IF NOT EXISTS match_reminders (
                    key TEXT NOT NULL PRIMARY KEY,
                    channelKey TEXT NOT NULL,
                    channelName TEXT NOT NULL,
                    title TEXT NOT NULL,
                    startMs INTEGER NOT NULL,
                    endMs INTEGER NOT NULL,
                    scheduledAt INTEGER NOT NULL
                )""".trimIndent())
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS downloads (
                    id TEXT NOT NULL PRIMARY KEY,
                    profileId INTEGER NOT NULL,
                    kind TEXT NOT NULL,
                    refId INTEGER NOT NULL,
                    seriesKey TEXT NOT NULL DEFAULT '',
                    seriesName TEXT NOT NULL DEFAULT '',
                    season INTEGER NOT NULL DEFAULT 0,
                    episode INTEGER NOT NULL DEFAULT 0,
                    title TEXT NOT NULL,
                    poster TEXT NOT NULL DEFAULT '',
                    sourceUrl TEXT NOT NULL,
                    filePath TEXT NOT NULL DEFAULT '',
                    status TEXT NOT NULL DEFAULT 'QUEUED',
                    progressPct INTEGER NOT NULL DEFAULT 0,
                    sizeBytes INTEGER NOT NULL DEFAULT 0,
                    downloadedBytes INTEGER NOT NULL DEFAULT 0,
                    errorMessage TEXT NOT NULL DEFAULT '',
                    addedAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_profileId ON downloads(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_kind ON downloads(kind)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_seriesKey ON downloads(seriesKey)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Extend movies + series with TMDB enrichment columns. All
                // default-nullable-safe via NOT NULL DEFAULT so existing
                // rows are usable immediately after upgrade; the worker
                // fills them in asynchronously.
                listOf("movies", "series").forEach { table ->
                    db.execSQL("ALTER TABLE $table ADD COLUMN tmdbId INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $table ADD COLUMN studios TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN enrichedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_${table}_tmdbId ON $table(tmdbId)")
                }
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Resumable downloads: remember which engine owns a row and the
                // per-segment byte offsets it reached, so pause/resume survives
                // both a paused download and the process being killed.
                db.execSQL("ALTER TABLE downloads ADD COLUMN engine TEXT NOT NULL DEFAULT 'parallel'")
                db.execSQL("ALTER TABLE downloads ADD COLUMN resumeState TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "enktel.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build()
    }
}
