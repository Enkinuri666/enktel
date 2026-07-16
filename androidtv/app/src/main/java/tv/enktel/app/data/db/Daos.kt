package tv.enktel.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Insert suspend fun insert(p: Profile): Long
    @Update suspend fun update(p: Profile)
    @Query("DELETE FROM profiles WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM profiles") fun all(): Flow<List<Profile>>
    @Query("SELECT * FROM profiles WHERE id = :id") suspend fun byId(id: Long): Profile?
    @Query("SELECT * FROM profiles LIMIT 1") suspend fun first(): Profile?
}

@Dao
interface ContentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertChannels(items: List<Channel>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCategories(items: List<Category>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMovies(items: List<Movie>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSeries(items: List<Series>)

    @Query("DELETE FROM channels WHERE profileId = :profileId") suspend fun clearChannels(profileId: Long)
    @Query("DELETE FROM categories WHERE profileId = :profileId") suspend fun clearCategories(profileId: Long)
    @Query("DELETE FROM movies WHERE profileId = :profileId") suspend fun clearMovies(profileId: Long)
    @Query("DELETE FROM series WHERE profileId = :profileId") suspend fun clearSeries(profileId: Long)

    @Query("SELECT * FROM channels WHERE profileId = :profileId ORDER BY sortIdx")
    fun channels(profileId: Long): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE profileId = :profileId AND categoryId = :categoryId ORDER BY sortIdx")
    fun channelsIn(profileId: Long, categoryId: String): Flow<List<Channel>>

    @Query("SELECT c.* FROM channels c JOIN favorites f ON f.profileId = c.profileId AND f.kind = 'live' AND f.refId = c.streamId WHERE c.profileId = :profileId ORDER BY f.addedAt DESC")
    fun favoriteChannels(profileId: Long): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE key = :key") suspend fun channel(key: String): Channel?
    @Query("SELECT * FROM channels WHERE profileId = :profileId AND num = :num LIMIT 1")
    suspend fun channelByNum(profileId: Long, num: Int): Channel?

    @Query("SELECT * FROM categories WHERE profileId = :profileId AND kind = :kind ORDER BY sortIdx")
    fun categories(profileId: Long, kind: String): Flow<List<Category>>

    @Query("SELECT * FROM movies WHERE profileId = :profileId ORDER BY name") fun movies(profileId: Long): Flow<List<Movie>>
    @Query("SELECT * FROM movies WHERE profileId = :profileId AND categoryId = :categoryId ORDER BY name")
    fun moviesIn(profileId: Long, categoryId: String): Flow<List<Movie>>
    @Query("SELECT * FROM movies WHERE profileId = :profileId ORDER BY addedAt DESC LIMIT :n")
    fun recentMovies(profileId: Long, n: Int): Flow<List<Movie>>
    @Query("SELECT * FROM movies WHERE key = :key") suspend fun movie(key: String): Movie?
    @Query("SELECT m.* FROM movies m JOIN favorites f ON f.profileId = m.profileId AND f.kind = 'vod' AND f.refId = m.streamId WHERE m.profileId = :profileId ORDER BY f.addedAt DESC")
    fun favoriteMovies(profileId: Long): Flow<List<Movie>>

    @Query("SELECT * FROM series WHERE profileId = :profileId ORDER BY name") fun series(profileId: Long): Flow<List<Series>>
    @Query("SELECT * FROM series WHERE profileId = :profileId AND categoryId = :categoryId ORDER BY name")
    fun seriesIn(profileId: Long, categoryId: String): Flow<List<Series>>
    @Query("SELECT * FROM series WHERE key = :key") suspend fun oneSeries(key: String): Series?

    @Query("SELECT * FROM channels WHERE profileId = :profileId AND name LIKE '%' || :q || '%' ORDER BY name LIMIT 60")
    suspend fun searchChannels(profileId: Long, q: String): List<Channel>
    @Query("SELECT * FROM movies WHERE profileId = :profileId AND name LIKE '%' || :q || '%' ORDER BY name LIMIT 60")
    suspend fun searchMovies(profileId: Long, q: String): List<Movie>
    @Query("SELECT * FROM series WHERE profileId = :profileId AND name LIKE '%' || :q || '%' ORDER BY name LIMIT 60")
    suspend fun searchSeries(profileId: Long, q: String): List<Series>
}

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(items: List<EpgProgram>)
    @Query("DELETE FROM epg WHERE profileId = :profileId") suspend fun clear(profileId: Long)
    @Query("DELETE FROM epg WHERE endMs < :before") suspend fun prune(before: Long)

    @Query("SELECT * FROM epg WHERE profileId = :profileId AND epgId = :epgId AND endMs > :now ORDER BY startMs LIMIT :n")
    suspend fun nowNext(profileId: Long, epgId: String, now: Long, n: Int): List<EpgProgram>

    @Query("SELECT * FROM epg WHERE profileId = :profileId AND epgId = :epgId AND endMs > :from AND startMs < :to ORDER BY startMs")
    suspend fun window(profileId: Long, epgId: String, from: Long, to: Long): List<EpgProgram>

    @Query("SELECT * FROM epg WHERE profileId = :profileId AND epgId IN (:epgIds) AND endMs > :from AND startMs < :to ORDER BY startMs")
    suspend fun windowMany(profileId: Long, epgIds: List<String>, from: Long, to: Long): List<EpgProgram>

    @Query("SELECT * FROM epg WHERE profileId = :profileId AND epgId = :epgId AND startMs >= :from AND endMs <= :now ORDER BY startMs DESC")
    suspend fun archive(profileId: Long, epgId: String, from: Long, now: Long): List<EpgProgram>

    @Query("SELECT COUNT(*) FROM epg WHERE profileId = :profileId") suspend fun count(profileId: Long): Int
}

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFavorite(f: Favorite)
    @Query("DELETE FROM favorites WHERE key = :key") suspend fun removeFavorite(key: String)
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE key = :key)") suspend fun isFavorite(key: String): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE key = :key)") fun isFavoriteFlow(key: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProgress(p: WatchProgress)
    @Query("SELECT * FROM progress WHERE key = :key") suspend fun progress(key: String): WatchProgress?
    @Query("SELECT * FROM progress WHERE profileId = :profileId ORDER BY updatedAt DESC LIMIT :n")
    fun continueWatching(profileId: Long, n: Int): Flow<List<WatchProgress>>
    @Query("DELETE FROM progress WHERE key = :key") suspend fun clearProgress(key: String)
}

@Dao
interface RecordingDao {
    @Insert suspend fun insert(r: Recording): Long
    @Update suspend fun update(r: Recording)
    @Delete suspend fun delete(r: Recording)
    @Query("SELECT * FROM recordings WHERE id = :id") suspend fun byId(id: Long): Recording?
    @Query("SELECT * FROM recordings ORDER BY startMs DESC") fun all(): Flow<List<Recording>>
    @Query("UPDATE recordings SET status = :status WHERE id = :id") suspend fun setStatus(id: Long, status: String)
}
