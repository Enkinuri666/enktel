package tv.enktel.app.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Disk cache for VOD bytes.
 *
 * Xtream VOD servers throttle range requests, so every backward seek or
 * re-buffer that re-downloads bytes already fetched costs real time. With the
 * bytes on disk, a scrub back inside what has played is served locally and the
 * network is only asked for what is genuinely new.
 *
 * Live is never cached: a channel has no stable bytes to reuse and caching it
 * would only churn the disk.
 *
 * SimpleCache allows exactly one instance per directory per process, hence the
 * process-wide singleton.
 */
@UnstableApi
object VodCache {
    /** Small enough for a 8 GB Fire TV, large enough for a 4K film's seek window. */
    private const val MAX_BYTES = 768L * 1024 * 1024
    private const val LOW_RAM_MAX_BYTES = 256L * 1024 * 1024

    @Volatile private var cache: SimpleCache? = null

    fun get(context: Context, lowRam: Boolean): SimpleCache =
        cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.applicationContext.cacheDir, "media3-vod"),
                LeastRecentlyUsedCacheEvictor(if (lowRam) LOW_RAM_MAX_BYTES else MAX_BYTES),
                StandaloneDatabaseProvider(context.applicationContext),
            ).also { cache = it }
        }

    /** Wrap [upstream] so reads go through the cache, and a cache fault falls
     *  back to the network rather than failing playback. */
    fun wrap(context: Context, upstream: DataSource.Factory, lowRam: Boolean): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(get(context, lowRam))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
