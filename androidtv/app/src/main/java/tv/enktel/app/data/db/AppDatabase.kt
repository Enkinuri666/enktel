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
    ],
    version = 3, // v3 adds movie/series genre + year for VOD filtering
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun contentDao(): ContentDao
    abstract fun epgDao(): EpgDao
    abstract fun userDao(): UserDao
    abstract fun recordingDao(): RecordingDao

    companion object {
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE movies ADD COLUMN genre TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE movies ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE series ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "enktel.db")
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}
