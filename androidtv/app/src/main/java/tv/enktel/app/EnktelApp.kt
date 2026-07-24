package tv.enktel.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import tv.enktel.app.data.db.AppDatabase
import tv.enktel.app.data.prefs.SettingsStore
import tv.enktel.app.data.repo.ContentRepository
import tv.enktel.app.data.repo.EpgRepository
import tv.enktel.app.data.repo.PlaylistRepository
import tv.enktel.app.data.repo.RecommendationsRepository
import tv.enktel.app.data.repo.ScoresRepository
import tv.enktel.app.data.repo.SportsRepository
import tv.enktel.app.data.repo.WatchlistRepository
import tv.enktel.app.data.xtream.XtreamClient
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppGraph(app: Application) {
    val db = AppDatabase.build(app)
    val settings = SettingsStore(app)
    // Volatile so the health interceptor can read the latest without a Flow
    // subscription — updated whenever the setting flow emits (see below).
    @Volatile private var backupGatewaysSnapshot: List<String> = emptyList()
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(
            tv.enktel.app.data.net.StreamHealthInterceptor(
                gateways = { backupGatewaysSnapshot },
            )
        )
        .build()
    val xtream = XtreamClient(http)
    val playlists = PlaylistRepository(db.profileDao(), settings, xtream)
    val content = ContentRepository(app, db, xtream, http)
    val epg = EpgRepository(db, xtream, http)
    val sports = SportsRepository(content, epg)
    val watchlist = WatchlistRepository(db.watchlistDao())
    val recommendations = RecommendationsRepository(content)
    val scores = ScoresRepository(http)

    init {
        // Keep the interceptor's backup-gateway snapshot in sync with the
        // user's setting.  Uses a plain thread so we don't need a coroutine
        // scope pinned to the AppGraph lifecycle — the flow lives as long
        // as the process.
        val bgScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        bgScope.launch {
            settings.backupGateways.collect { backupGatewaysSnapshot = it }
        }
    }
}

class EnktelApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        tv.enktel.app.data.epg.EpgRefreshWorker.schedule(this)
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(DVR_CHANNEL, "DVR Recordings", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val DVR_CHANNEL = "dvr"
    }
}
