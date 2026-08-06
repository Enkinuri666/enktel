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
        DownloadEntry::class, UserList::class, UserListItem::class,
        MovieFts::class, SeriesFts::class,
    ],
    version = 12, // v12 adds the FTS4 search index over the VOD catalogue
    exportSchema = true,
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
    abstract fun userListDao(): UserListDao

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

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Radio channels are audio-only and want a different presentation
                // from a TV channel; the UA override exists for sources that
                // answer for exactly one User-Agent and 403 everything else.
                db.execSQL("ALTER TABLE channels ADD COLUMN isRadio INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE channels ADD COLUMN userAgent TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // "New to this line" is knowable only by diffing one sync
                // against the last; the panel's own `added` is missing on M3U
                // lines entirely and unreliable elsewhere. Existing rows keep
                // 0, which reads as "was already here" — correct, because it
                // was.
                db.execSQL("ALTER TABLE movies ADD COLUMN firstSeenAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE series ADD COLUMN firstSeenAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Themed lists. Separate from favourites (one flat starred set
                // per kind) and from the watchlist ("things I mean to watch"),
                // because neither can express "these channels and these films
                // belong together" — which is the whole point, and is why the
                // item table is keyed on kind + row key rather than on one
                // content type.
                db.execSQL("""CREATE TABLE IF NOT EXISTS user_lists (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    profileId INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    icon TEXT NOT NULL DEFAULT '📁',
                    createdAt INTEGER NOT NULL,
                    sortIdx INTEGER NOT NULL DEFAULT 0
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_lists_profileId ON user_lists(profileId)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS user_list_items (
                    key TEXT NOT NULL PRIMARY KEY,
                    listId INTEGER NOT NULL,
                    kind TEXT NOT NULL,
                    itemKey TEXT NOT NULL,
                    name TEXT NOT NULL,
                    poster TEXT NOT NULL DEFAULT '',
                    addedAt INTEGER NOT NULL
                )""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_list_items_listId ON user_list_items(listId)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_user_list_items_listId_itemKey " +
                        "ON user_list_items(listId, itemKey)"
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The playlist has always told us how a channel's catch-up
                // works; the sync threw it away, so playback could only ever
                // guess the Xtream shape and every catch-up entry point was
                // gated to Xtream profiles as a result.
                db.execSQL("ALTER TABLE channels ADD COLUMN catchupType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE channels ADD COLUMN catchupSource TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Search was seven LIKE '%q%' comparisons per row across two
                // tables. A leading wildcard makes every index useless, so that
                // is a full scan of the catalogue on every keystroke — and on a
                // hundred-thousand-title line it is exactly the stutter it
                // looks like.
                //
                // Created empty. The tables are filled by the next catalogue
                // sync, and every FTS query has a LIKE fallback for the window
                // in between, so an upgrade never lands on a search box that
                // silently returns nothing.
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `movies_fts` USING FTS4(" +
                        "`itemKey` TEXT NOT NULL, `profileId` INTEGER NOT NULL, `body` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `series_fts` USING FTS4(" +
                        "`itemKey` TEXT NOT NULL, `profileId` INTEGER NOT NULL, `body` TEXT NOT NULL)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "enktel.db")
                .addMigrations(
                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                    MIGRATION_10_11, MIGRATION_11_12,
                )
                // Last resort only. Anything that reaches this line has lost the
                // user's profiles, favourites, watch progress, recordings and
                // download bookkeeping, so every version bump that touches an
                // entity needs a Migration above it. The committed schemas under
                // app/schemas are what makes a missing one visible in review.
                //
                // dropAllTables is stated explicitly because Room 2.8 deprecated
                // the no-arg overload — the argument decides whether tables Room
                // does not own are dropped too, and defaulting that silently is
                // exactly the kind of thing that quietly changes under a bump.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
