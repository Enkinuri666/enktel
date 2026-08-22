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

    // ---- v1.20.0 metadata enrichment + themed home rails -------------------

    /** Movies that either have no TMDB enrichment yet (enrichedAt == 0) OR
     *  whose last enrichment is older than [staleBefore]. Ordered by most
     *  recently added so the freshest content gets enriched first. */
    @Query("SELECT * FROM movies WHERE profileId = :profileId AND (enrichedAt = 0 OR enrichedAt < :staleBefore) ORDER BY addedAt DESC LIMIT :n")
    suspend fun moviesNeedingEnrichment(profileId: Long, staleBefore: Long, n: Int): List<Movie>

    @Query("SELECT * FROM series WHERE profileId = :profileId AND (enrichedAt = 0 OR enrichedAt < :staleBefore) ORDER BY name LIMIT :n")
    suspend fun seriesNeedingEnrichment(profileId: Long, staleBefore: Long, n: Int): List<Series>

    /** Themed-rail lookup — matches a comma-separated tags/keywords column
     *  against a single LIKE clause. Callers pass a `%keyword%` pattern.
     *  Also matches title/genre so an "aliens" search catches "Aliens (1986)"
     *  even before TMDB tags land. */
    @Query("""SELECT * FROM movies WHERE profileId = :profileId AND (
             tags LIKE :like OR genre LIKE :like OR name LIKE :like
         ) ORDER BY year DESC, name LIMIT :n""")
    suspend fun moviesTagged(profileId: Long, like: String, n: Int = 40): List<Movie>

    @Query("""SELECT * FROM series WHERE profileId = :profileId AND (
             tags LIKE :like OR genre LIKE :like OR name LIKE :like
         ) ORDER BY year DESC, name LIMIT :n""")
    suspend fun seriesTagged(profileId: Long, like: String, n: Int = 40): List<Series>

    /** Same as [moviesTagged] but constrained to genre = 'Documentary'. */
    @Query("""SELECT * FROM movies WHERE profileId = :profileId AND (
             genre LIKE '%Documentary%' OR tags LIKE '%documentary%'
         ) AND (
             tags LIKE :like OR name LIKE :like
         ) ORDER BY year DESC, addedAt DESC LIMIT :n""")
    suspend fun moviesTaggedDocs(profileId: Long, like: String, n: Int = 40): List<Movie>

    /** Filter dropdown supports: genre / year / studio. Any blank string
     *  becomes an unbounded wildcard so the caller can compose partial
     *  filters (e.g. genre only). */
    @Query("""SELECT * FROM movies WHERE profileId = :profileId
             AND genre LIKE '%' || :genre || '%'
             AND (:year = 0 OR year = :year)
             AND studios LIKE '%' || :studio || '%'
             ORDER BY name LIMIT 200""")
    suspend fun moviesFiltered(profileId: Long, genre: String, year: Int, studio: String): List<Movie>

    @Query("""SELECT * FROM series WHERE profileId = :profileId
             AND genre LIKE '%' || :genre || '%'
             AND (:year = 0 OR year = :year)
             AND studios LIKE '%' || :studio || '%'
             ORDER BY name LIMIT 200""")
    suspend fun seriesFiltered(profileId: Long, genre: String, year: Int, studio: String): List<Series>

    /** Distinct-value helpers for populating the filter chip rows. */
    @Query("SELECT DISTINCT genre FROM movies WHERE profileId = :profileId AND genre != '' ORDER BY genre")
    suspend fun distinctMovieGenres(profileId: Long): List<String>

    @Query("SELECT DISTINCT year FROM movies WHERE profileId = :profileId AND year > 0 ORDER BY year DESC")
    suspend fun distinctMovieYears(profileId: Long): List<Int>

    /**
     * Push enriched fields from the worker without rewriting the entire row.
     *
     * The CASE guards are the whole point of writing this by hand rather than
     * upserting the entity. TMDB is authoritative for the fields the panel
     * usually leaves blank, but it is not always complete either — a
     * straight assignment would let a title TMDB has no synopsis for erase a
     * description the panel did supply, and the user would watch their
     * catalogue get *worse* the longer enrichment ran.
     *
     * [poster] follows the opposite rule: it only fills a gap. The panel's
     * artwork is what the user's provider chose and what the rest of the
     * catalogue looks like, so TMDB's is a fallback for titles with no art at
     * all, not a replacement. [backdrop] has no such conflict — nothing else
     * ever writes it.
     */
    @Query("""UPDATE movies SET tmdbId = :tmdbId, studios = :studios, tags = :tags,
             genre = :genre, year = :year, `cast` = :cast, director = :director,
             plot = CASE WHEN :plot != '' THEN :plot ELSE plot END,
             poster = CASE WHEN poster != '' THEN poster ELSE :poster END,
             backdrop = CASE WHEN :backdrop != '' THEN :backdrop ELSE backdrop END,
             runtimeMins = CASE WHEN :runtimeMins > 0 THEN :runtimeMins ELSE runtimeMins END,
             enrichedAt = :now WHERE key = :key""")
    suspend fun enrichMovie(
        key: String, tmdbId: Long, studios: String, tags: String,
        genre: String, year: Int, cast: String, director: String,
        plot: String, poster: String, backdrop: String, runtimeMins: Int, now: Long,
    )

    @Query("""UPDATE series SET tmdbId = :tmdbId, studios = :studios, tags = :tags,
             genre = :genre, year = :year, `cast` = :cast, director = :director,
             plot = CASE WHEN :plot != '' THEN :plot ELSE plot END,
             poster = CASE WHEN poster != '' THEN poster ELSE :poster END,
             backdrop = CASE WHEN :backdrop != '' THEN :backdrop ELSE backdrop END,
             runtimeMins = CASE WHEN :runtimeMins > 0 THEN :runtimeMins ELSE runtimeMins END,
             enrichedAt = :now WHERE key = :key""")
    suspend fun enrichSeries(
        key: String, tmdbId: Long, studios: String, tags: String,
        genre: String, year: Int, cast: String, director: String,
        plot: String, poster: String, backdrop: String, runtimeMins: Int, now: Long,
    )

    /**
     * Record what IMDb says about a title.
     *
     * Its own statement rather than more parameters on [enrichMovie] because
     * the two lookups fail independently: TMDB answers for a title OMDb has
     * never heard of, and OMDb answers for one whose TMDB record is thin. One
     * combined write would make each failure erase the other's result.
     *
     * The id is written even when no rating came back, so a later run can
     * retry the rating without paying for the TMDB round trip again — and so
     * the IMDb link works for a title whose rating OMDb withholds.
     */
    @Query("""UPDATE movies SET imdbId = :imdbId,
             imdbRating = CASE WHEN :rating > 0 THEN :rating ELSE imdbRating END,
             imdbVotes = CASE WHEN :rating > 0 THEN :votes ELSE imdbVotes END
             WHERE key = :key""")
    suspend fun setMovieImdb(key: String, imdbId: String, rating: Double, votes: Int)

    /** See [setMovieImdb]. */
    @Query("""UPDATE series SET imdbId = :imdbId,
             imdbRating = CASE WHEN :rating > 0 THEN :rating ELSE imdbRating END,
             imdbVotes = CASE WHEN :rating > 0 THEN :votes ELSE imdbVotes END
             WHERE key = :key""")
    suspend fun setSeriesImdb(key: String, imdbId: String, rating: Double, votes: Int)

    /**
     * Write back a sanitised title, and nothing else.
     *
     * The worker used to do this with `upsertMovies(listOf(m.copy(name = …)))`,
     * and that row `m` was read *before* enrichMovie ran. REPLACE on the
     * primary key is a delete-and-reinsert of the whole row, so the copy put
     * the pre-enrichment values back over the ones just written — tmdbId,
     * studios, tags, genre, year, cast, director and, worst of all,
     * enrichedAt.
     *
     * Reverting enrichedAt to 0 put the row straight back at the top of
     * moviesNeedingEnrichment, so every title the sanitizer touched — which on
     * an IPTV catalogue is most of them, that being the sanitizer's entire
     * purpose — was re-fetched from TMDB on every run and never kept anything.
     * A targeted UPDATE cannot have that failure mode.
     */
    @Query("UPDATE movies SET name = :name WHERE key = :key")
    suspend fun renameMovie(key: String, name: String)

    @Query("UPDATE series SET name = :name WHERE key = :key")
    suspend fun renameSeries(key: String, name: String)

    /**
     * Stamp an enrichment *attempt* without writing any metadata.
     *
     * Needed because a title the panel has no `tmdb_id` for, or that TMDB has
     * never heard of, would otherwise keep `enrichedAt = 0` forever — and the
     * "needs enrichment" queries are `ORDER BY … LIMIT n`, so the worker got
     * handed the same failing rows on every single run and never reached row
     * n+1. On a catalogue whose first fifty titles don't resolve, enrichment
     * made exactly zero progress no matter how many times you re-synced.
     */
    @Query("UPDATE movies SET enrichedAt = :now WHERE key = :key")
    suspend fun markMovieEnrichAttempt(key: String, now: Long)

    @Query("UPDATE series SET enrichedAt = :now WHERE key = :key")
    suspend fun markSeriesEnrichAttempt(key: String, now: Long)

    @Query("SELECT COUNT(*) FROM channels WHERE profileId = :profileId")
    suspend fun channelCount(profileId: Long): Int

    /** Channels the panel flagged as having a catch-up archive, and how far
     *  back the deepest one goes. Reported by Diagnostics so "Catch-Up is
     *  empty" can be answered without guessing whose side it is on. */
    @Query("SELECT COUNT(*) FROM channels WHERE profileId = :profileId AND hasArchive = 1")
    suspend fun catchupChannelCount(profileId: Long): Int

    @Query("SELECT MAX(archiveDays) FROM channels WHERE profileId = :profileId AND hasArchive = 1")
    suspend fun maxCatchupDays(profileId: Long): Int?

    @Query("SELECT COUNT(*) FROM movies WHERE profileId = :profileId")
    suspend fun movieCount(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM movies WHERE profileId = :profileId AND tmdbId > 0")
    suspend fun movieEnrichedCount(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM series WHERE profileId = :profileId")
    suspend fun seriesCount(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM series WHERE profileId = :profileId AND tmdbId > 0")
    suspend fun seriesEnrichedCount(profileId: Long): Int

    @Query("SELECT COUNT(*) FROM movies WHERE profileId = :profileId AND (enrichedAt = 0 OR enrichedAt < :staleBefore)")
    suspend fun moviesPendingCount(profileId: Long, staleBefore: Long): Int

    @Query("SELECT COUNT(*) FROM series WHERE profileId = :profileId AND (enrichedAt = 0 OR enrichedAt < :staleBefore)")
    suspend fun seriesPendingCount(profileId: Long, staleBefore: Long): Int
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

    /** How many distinct channels the loaded guide actually covers. A guide
     *  with 40 000 programmes across 30 of your 900 channels looks healthy by
     *  row count and is empty everywhere the user looks. */
    @Query("SELECT COUNT(DISTINCT epgId) FROM epg WHERE profileId = :profileId")
    suspend fun coveredChannelCount(profileId: Long): Int

    /** Furthest-out programme end time — how many days ahead the guide runs. */
    @Query("SELECT MAX(endMs) FROM epg WHERE profileId = :profileId")
    suspend fun horizonMs(profileId: Long): Long?

    /** Global title search — used by the unified master-search screen so
     *  users can find an upcoming program by name across every channel. */
    @Query("SELECT * FROM epg WHERE profileId = :profileId AND endMs > :now AND (title LIKE '%' || :q || '%' OR desc LIKE '%' || :q || '%') ORDER BY startMs LIMIT 40")
    suspend fun searchUpcoming(profileId: Long, q: String, now: Long): List<EpgProgram>
}

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFavorite(f: Favorite)
    @Query("DELETE FROM favorites WHERE key = :key") suspend fun removeFavorite(key: String)
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE key = :key)") suspend fun isFavorite(key: String): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE key = :key)") fun isFavoriteFlow(key: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProgress(p: WatchProgress)
    @Query("SELECT * FROM progress WHERE key = :key") suspend fun progress(key: String): WatchProgress?

    /**
     * Give old resume points their artwork back.
     *
     * The player wrote `poster` empty for its whole life — it was never given
     * the artwork to write — so every row made before that was fixed renders as
     * a blank card on the Continue Watching rail. New rows carry it now, but
     * only from the next time each title is played, and a rail that fills in
     * one title at a time over a fortnight is not really fixed.
     *
     * Films can be recovered outright: a film's progress key is
     * "profileId:vod:streamId" and the catalogue row's key is
     * "profileId:streamId", so the two join. Episodes cannot — an episode's
     * refId is the episode id, episodes are not stored locally at all (they are
     * fetched per-series from the panel), and nothing in the row leads back to
     * the series that owns the artwork. Those fill in as they are watched.
     */
    @Query(
        """UPDATE progress SET poster = COALESCE(
               (SELECT m.poster FROM movies m WHERE m.key = progress.profileId || ':' || progress.refId), '')
           WHERE profileId = :profileId AND kind = 'vod' AND poster = ''"""
    )
    suspend fun backfillProgressArtwork(profileId: Long)
    /**
     * The Continue Watching rail: things actually worth picking up again.
     *
     * The thresholds are ResumePolicy's, restated in SQL because filtering in
     * Kotlin would mean LIMIT :n counting rows the rail then discards — ask
     * for twenty and get four. Keep the two in step; ResumePolicyTest pins the
     * numbers.
     */
    @Query(
        """SELECT * FROM progress WHERE profileId = :profileId
             AND positionMs >= 60000
             AND (durationMs <= 0
                  OR (positionMs < durationMs - 120000 AND positionMs * 100 < durationMs * 95))
           ORDER BY updatedAt DESC LIMIT :n"""
    )
    fun continueWatching(profileId: Long, n: Int): Flow<List<WatchProgress>>

    /**
     * Everything watched, unfiltered — the taste signal behind the
     * recommendation rails.
     *
     * Deliberately not the query above. A film watched to the end is the
     * *strongest* evidence of what somebody likes, and it is exactly what
     * Continue Watching is built to hide, so pointing "Because You Watched" at
     * the rail query would have thrown away the best input it has.
     */
    @Query("SELECT * FROM progress WHERE profileId = :profileId ORDER BY updatedAt DESC LIMIT :n")
    fun watchHistory(profileId: Long, n: Int): Flow<List<WatchProgress>>
    @Query("DELETE FROM progress WHERE key = :key") suspend fun clearProgress(key: String)
}

@Dao
interface WatchlistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(w: WatchlistItem)
    @Query("DELETE FROM watchlist WHERE key = :key") suspend fun remove(key: String)
    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE key = :key)") suspend fun isSaved(key: String): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE key = :key)") fun isSavedFlow(key: String): Flow<Boolean>
    @Query("SELECT * FROM watchlist WHERE profileId = :profileId ORDER BY addedAt DESC") fun all(profileId: Long): Flow<List<WatchlistItem>>
    @Query("SELECT * FROM watchlist WHERE profileId = :profileId AND kind = :kind ORDER BY addedAt DESC") fun ofKind(profileId: Long, kind: String): Flow<List<WatchlistItem>>
}

@Dao
interface SearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun push(item: SearchHistoryItem)
    @Query("SELECT * FROM search_history ORDER BY usedAt DESC LIMIT :n") fun recent(n: Int = 20): Flow<List<SearchHistoryItem>>
    @Query("DELETE FROM search_history WHERE query = :query") suspend fun forget(query: String)
    @Query("DELETE FROM search_history") suspend fun clear()

    // Multi-dimensional search — title, cast, director, genre, studios, tags,
    // and (for movies) a year match if the query is a plain 4-digit year.
    // The `year` OR-clause uses CAST so LIKE '%2023%' still hits releases
    // labelled with `year = 2023` in the integer column.
    @Query("""SELECT * FROM movies WHERE profileId = :profileId AND (
             name LIKE '%' || :q || '%'
             OR `cast` LIKE '%' || :q || '%'
             OR director LIKE '%' || :q || '%'
             OR genre LIKE '%' || :q || '%'
             OR studios LIKE '%' || :q || '%'
             OR tags LIKE '%' || :q || '%'
             OR CAST(year AS TEXT) LIKE '%' || :q || '%'
         ) ORDER BY name LIMIT 60""")
    suspend fun searchMoviesDeep(profileId: Long, q: String): List<Movie>

    @Query("""SELECT * FROM series WHERE profileId = :profileId AND (
             name LIKE '%' || :q || '%'
             OR `cast` LIKE '%' || :q || '%'
             OR director LIKE '%' || :q || '%'
             OR genre LIKE '%' || :q || '%'
             OR studios LIKE '%' || :q || '%'
             OR tags LIKE '%' || :q || '%'
             OR plot LIKE '%' || :q || '%'
             OR CAST(year AS TEXT) LIKE '%' || :q || '%'
         ) ORDER BY name LIMIT 60""")
    suspend fun searchSeriesDeep(profileId: Long, q: String): List<Series>

    // --- FTS4 --------------------------------------------------------------
    //
    // The LIKE queries above stay as a fallback for a profile whose index has
    // not been built yet (an upgrade lands with empty FTS tables until the next
    // sync). Everything else goes through MATCH, where the cost is proportional
    // to the number of hits rather than the size of the catalogue.

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun indexMovies(rows: List<MovieFts>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun indexSeries(rows: List<SeriesFts>)

    @Query("DELETE FROM movies_fts WHERE profileId = :profileId")
    suspend fun clearMovieIndex(profileId: Long)

    @Query("DELETE FROM series_fts WHERE profileId = :profileId")
    suspend fun clearSeriesIndex(profileId: Long)

    @Query("SELECT COUNT(*) FROM movies_fts WHERE profileId = :profileId")
    suspend fun movieIndexSize(profileId: Long): Int

    @Query("""SELECT m.* FROM movies m
             JOIN movies_fts f ON f.itemKey = m.key
             WHERE f.profileId = :profileId AND movies_fts MATCH :match
             ORDER BY m.name LIMIT 60""")
    suspend fun searchMoviesFts(profileId: Long, match: String): List<Movie>

    @Query("""SELECT s.* FROM series s
             JOIN series_fts f ON f.itemKey = s.key
             WHERE f.profileId = :profileId AND series_fts MATCH :match
             ORDER BY s.name LIMIT 60""")
    suspend fun searchSeriesFts(profileId: Long, match: String): List<Series>
}

@Dao
interface SportsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun follow(team: FollowedTeam)
    @Query("DELETE FROM followed_teams WHERE name = :name") suspend fun unfollow(name: String)
    @Query("SELECT * FROM followed_teams ORDER BY displayName") fun followed(): Flow<List<FollowedTeam>>
    @Query("SELECT * FROM followed_teams") suspend fun followedNow(): List<FollowedTeam>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addReminder(r: MatchReminder)
    @Query("DELETE FROM match_reminders WHERE key = :key") suspend fun cancelReminder(key: String)
    @Query("SELECT EXISTS(SELECT 1 FROM match_reminders WHERE key = :key)") suspend fun hasReminder(key: String): Boolean
    @Query("SELECT * FROM match_reminders WHERE startMs > :now ORDER BY startMs") fun upcomingReminders(now: Long): Flow<List<MatchReminder>>
}

@Dao
interface RecordingDao {
    @Insert suspend fun insert(r: Recording): Long
    @Update suspend fun update(r: Recording)
    @Delete suspend fun delete(r: Recording)
    @Query("SELECT * FROM recordings WHERE id = :id") suspend fun byId(id: Long): Recording?
    @Query("SELECT * FROM recordings ORDER BY startMs DESC") fun all(): Flow<List<Recording>>
    @Query("UPDATE recordings SET status = :status WHERE id = :id") suspend fun setStatus(id: Long, status: String)

    /** Rows still claiming to be recording — used at start-up to clean up after
     *  a process death, which otherwise leaves a permanent "● REC" badge on a
     *  recording that stopped when the app did. */
    @Query("SELECT * FROM recordings WHERE status = 'RECORDING'")
    suspend fun inFlight(): List<Recording>
}

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(d: DownloadEntry)
    @Query("DELETE FROM downloads WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM downloads WHERE id = :id") suspend fun byId(id: String): DownloadEntry?
    @Query("SELECT * FROM downloads ORDER BY addedAt DESC") fun all(): Flow<List<DownloadEntry>>
    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun forProfile(profileId: Long): Flow<List<DownloadEntry>>
    @Query("SELECT * FROM downloads WHERE seriesKey = :seriesKey ORDER BY season, episode")
    fun forSeries(seriesKey: String): Flow<List<DownloadEntry>>
    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE id = :id)") fun existsFlow(id: String): Flow<Boolean>
    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE id = :id AND status = 'DONE')")
    fun completedFlow(id: String): Flow<Boolean>
    @Query("UPDATE downloads SET status = :status, progressPct = :pct, downloadedBytes = :downloaded, sizeBytes = :size, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, pct: Int, downloaded: Long, size: Long, now: Long = System.currentTimeMillis())
    @Query("UPDATE downloads SET status = :status, filePath = :path, updatedAt = :now WHERE id = :id")
    suspend fun markDone(id: String, path: String, status: String = "DONE", now: Long = System.currentTimeMillis())
    @Query("UPDATE downloads SET status = :status, errorMessage = :err, updatedAt = :now WHERE id = :id")
    suspend fun markFailed(id: String, err: String, status: String = "FAILED", now: Long = System.currentTimeMillis())
    @Query("SELECT COALESCE(SUM(downloadedBytes), 0) FROM downloads") suspend fun totalBytes(): Long

    // ---- pause / resume ---------------------------------------------------

    @Query("UPDATE downloads SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: String, now: Long = System.currentTimeMillis())

    /** Persist the byte offsets each range-worker reached so a resume can pick
     *  up mid-file. Written on a slow clock while a download runs, and on pause. */
    @Query("UPDATE downloads SET resumeState = :state, progressPct = :pct, downloadedBytes = :downloaded, sizeBytes = :size, updatedAt = :now WHERE id = :id")
    suspend fun updateResumeState(id: String, state: String, pct: Int, downloaded: Long, size: Long, now: Long = System.currentTimeMillis())

    /**
     * Progress without the resume record.
     *
     * The record is a few kilobytes joined from every chunk in the plan, and it
     * only has to be good enough to survive a crash — whereas the progress bar
     * wants updating several times a second. Rebuilding and rewriting the
     * former at the rate of the latter is work a streaming stick does not have
     * to spare while it is also taking delivery of the file. Leaving
     * `resumeState` alone here keeps the last one written intact.
     */
    @Query("UPDATE downloads SET progressPct = :pct, downloadedBytes = :downloaded, sizeBytes = :size, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: String, pct: Int, downloaded: Long, size: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET status = 'PAUSED', resumeState = :state, progressPct = :pct, downloadedBytes = :downloaded, sizeBytes = :size, errorMessage = '', updatedAt = :now WHERE id = :id")
    suspend fun markPaused(id: String, state: String, pct: Int, downloaded: Long, size: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE downloads SET engine = :engine, updatedAt = :now WHERE id = :id")
    suspend fun setEngine(id: String, engine: String, now: Long = System.currentTimeMillis())

    /** Everything that was mid-flight — used at process start to move orphaned
     *  RUNNING/QUEUED rows (killed app, rebooted box) back to PAUSED so the
     *  user can resume them instead of staring at a frozen progress bar. */
    @Query("SELECT * FROM downloads WHERE status IN ('RUNNING', 'QUEUED')")
    suspend fun inFlight(): List<DownloadEntry>

    /** Downloads parked by the Wi-Fi-only policy, so they can be picked up
     *  automatically when an unmetered connection returns. */
    @Query("SELECT * FROM downloads WHERE status = 'PAUSED' AND errorMessage LIKE 'Waiting for Wi-Fi%'")
    suspend fun waitingForWifi(): List<DownloadEntry>
}

@Dao
interface UserListDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun createList(l: UserList): Long
    @Update suspend fun updateList(l: UserList)
    @Query("DELETE FROM user_lists WHERE id = :id") suspend fun deleteList(id: Long)
    // No foreign key on user_list_items, so the children have to go explicitly.
    // A cascade would be tidier but would mean a schema-level FK on a table
    // whose parent rows users delete casually.
    @Query("DELETE FROM user_list_items WHERE listId = :id") suspend fun deleteItemsOf(id: Long)

    @Query("SELECT * FROM user_lists WHERE profileId = :profileId ORDER BY sortIdx, createdAt")
    fun lists(profileId: Long): Flow<List<UserList>>

    @Query("SELECT * FROM user_list_items WHERE listId = :listId ORDER BY addedAt DESC")
    fun items(listId: Long): Flow<List<UserListItem>>

    @Query("SELECT COUNT(*) FROM user_list_items WHERE listId = :listId")
    fun itemCount(listId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addItem(i: UserListItem)
    @Query("DELETE FROM user_list_items WHERE key = :key") suspend fun removeItem(key: String)

    /** Which lists already hold [itemKey], so the UI can render a toggle. */
    @Query("SELECT listId FROM user_list_items WHERE itemKey = :itemKey")
    fun listsHolding(itemKey: String): Flow<List<Long>>
}
