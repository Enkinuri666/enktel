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
    ],
    version = 4, // v4 adds watchlist, search history, followed teams, match reminders + cast/director on movies/series
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

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "enktel.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
