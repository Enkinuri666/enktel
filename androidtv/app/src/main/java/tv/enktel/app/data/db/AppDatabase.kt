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
    version = 20, // v20 records what kind of programme each EPG entry is
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

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Enrichment has always asked TMDB for the synopsis, the
                // backdrop and the runtime — TmdbClient parsed all three into
                // Enrichment — and then dropped them on the floor, because
                // there was nowhere to put them. Movies did not even have a
                // plot column. So every title in the catalogue was costing a
                // request that returned a description nobody could read and a
                // hero image nobody could see.
                //
                // Nullable-free with defaults: every consumer already treats ""
                // and 0 as "we do not have this", and a NOT NULL DEFAULT keeps
                // the Room-generated schema and these statements identical.
                db.execSQL("ALTER TABLE movies ADD COLUMN plot TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE movies ADD COLUMN backdrop TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE movies ADD COLUMN runtimeMins INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE series ADD COLUMN backdrop TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE series ADD COLUMN runtimeMins INTEGER NOT NULL DEFAULT 0")

                // Existing rows carry enrichedAt from before these columns
                // existed, so the "needs enrichment" queries consider them done
                // and would never backfill the new fields. Clearing the stamp
                // puts the whole catalogue back in the queue; the worker chains
                // itself until the backlog is gone, so it drains on its own.
                db.execSQL("UPDATE movies SET enrichedAt = 0")
                db.execSQL("UPDATE series SET enrichedAt = 0")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Resuming an episode from Continue Watching started it with no
                // idea which series it belonged to: the row's refId is the
                // episode's own id and nothing joins that back to a series. So
                // the player could not work out what followed, and next-episode
                // autoplay did nothing from that entry point however well it
                // worked when the same episode was started from the series
                // screen. The player knows the series while it is playing;
                // these columns are what make it still known afterwards.
                //
                // Existing rows keep 0 and "", which reads as "not known" — the
                // same as a film — so a resume from a row written before this
                // behaves exactly as it did, and the next episode played from a
                // series screen writes the identity in.
                db.execSQL("ALTER TABLE progress ADD COLUMN seriesId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE progress ADD COLUMN seriesName TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // A panel that answers 403 to valid credentials is filtering on
                // User-Agent. There was one global override for that, reachable
                // only if the Panel Doctor happened to suggest it — the wrong
                // shape for anyone with more than one line, because it applies
                // one provider's workaround to every other provider too.
                //
                // Blank means "no opinion", which is what every existing row
                // gets, so nothing changes for anyone until they choose.
                db.execSQL("ALTER TABLE profiles ADD COLUMN userAgent TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // TMDB knows IMDb's id for most titles but carries none of
                // IMDb's ratings, so the id is stored to key the lookup on and
                // the rating is stored beside it rather than over the existing
                // `rating` column — that one holds whatever the panel
                // published, and for a line whose catalogue IMDb has never
                // heard of it is the only rating a row will ever have.
                //
                // Defaults, so every existing row migrates to "not looked up
                // yet" and the enrichment worker fills them in on its own
                // schedule. Nothing is lost if it never gets to a row.
                for (table in listOf("movies", "series")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN imdbId TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE $table ADD COLUMN imdbRating REAL NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $table ADD COLUMN imdbVotes INTEGER NOT NULL DEFAULT 0")
                }
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Where else this channel can be played from, newline-separated.
                //
                // Default empty, so every existing row migrates to "no
                // alternates known" and the next playlist sync fills in what
                // the published index has. Nothing is lost if a sync never
                // happens: the column is only read after a stream has already
                // failed.
                db.execSQL("ALTER TABLE channels ADD COLUMN altUrls TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // What the playlist said about encryption, if anything.
                //
                // Both default empty, which reads as "not encrypted" — correct
                // for every row that exists, since nothing in the lineup
                // carried DRM before there was anywhere to put it. The next
                // sync fills in whatever the playlist declares.
                db.execSQL("ALTER TABLE channels ADD COLUMN drmScheme TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE channels ADD COLUMN drmLicense TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // The on-demand playlist a profile syncs films from.
                //
                // Empty for every existing row, which reads as "no free film
                // library" — correct, since there was nowhere to put one
                // before. The seeded free-to-air profile gets its URL on the
                // next launch; a profile the viewer set up themselves keeps
                // the blank, because their films come from their own line.
                db.execSQL("ALTER TABLE profiles ADD COLUMN vodUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // What kind of programme this is, from the guide's <category>
                // elements.
                //
                // Empty for every existing row, which reads as "the guide did
                // not say" — correct, because until now it was parsed and
                // thrown away. The next guide refresh fills it in, and one
                // happens on the schedule the app already keeps, so nothing
                // needs to ask the viewer to do anything.
                db.execSQL("ALTER TABLE epg ADD COLUMN genre TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "enktel.db")
                .addMigrations(
                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                    MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                    MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
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
