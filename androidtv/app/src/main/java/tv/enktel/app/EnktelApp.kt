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
        val bgScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        bgScope.launch {
            settings.backupGateways.collect { backupGatewaysSnapshot = it }
        }
        // Social presence: pushes PresenceTracker.state to a user-configured
        // Discord webhook — no-op when the URL is blank.  Debounces at 15 s
        // so scrubbing doesn't spam the channel.
        tv.enktel.app.data.net.DiscordWebhookPublisher(
            http, settings.discordWebhook,
        ).startIn(bgScope)
        // Keep the NavSounds master flag mirrored to the ui-sounds pref so
        // navigation earcons instantly go silent when the toggle is off.
        bgScope.launch {
            settings.uiSoundsEnabled.collect {
                tv.enktel.app.ui.components.NavSounds.enabled = it
            }
        }
    }
}

class EnktelApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        tv.enktel.app.data.net.ThermalGuard.install(this)
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
