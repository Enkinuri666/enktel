package tv.enktel.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Profile::class, Channel::class, Category::class, Movie::class, Series::class,
        EpgProgram::class, Favorite::class, WatchProgress::class, Recording::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun contentDao(): ContentDao
    abstract fun epgDao(): EpgDao
    abstract fun userDao(): UserDao
    abstract fun recordingDao(): RecordingDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "enktel.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
