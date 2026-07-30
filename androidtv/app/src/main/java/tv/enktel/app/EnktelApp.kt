package tv.enktel.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import tv.enktel.app.data.db.AppDatabase
import tv.enktel.app.data.download.DownloadHub
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
import java.net.Proxy
import java.util.concurrent.TimeUnit

class AppGraph(app: Application) {
    val db = AppDatabase.build(app)
    val settings = SettingsStore(app)
    // Volatile so the health interceptor can read the latest without a Flow
    // subscription — updated whenever the setting flow emits (see below).
    @Volatile private var backupGatewaysSnapshot: List<String> = emptyList()
    val http: OkHttpClient = OkHttpClient.Builder()
        // Wider timeouts so slower IPTV proxy layers (Cloudflare, IPTV-Editor,
        // reseller relays) don't trip the "unable to measure throughput" or
        // ExoPlayer's own read-timeout error before the first byte lands.
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS) // unlimited overall — long VOD downloads shouldn't be capped
        .retryOnConnectionFailure(true)
        // Explicit NO_PROXY defeats HTTP_PROXY_AUTH (407) loops caused by an
        // OS-level or JVM-level proxy that gets applied to every OkHttp call
        // without credentials. Users who really need a proxy can put it in
        // Settings → Backup gateways which is applied per-request instead.
        .proxy(Proxy.NO_PROXY)
        // Send a well-known media UA on every request. Many Cloudflare WAFs +
        // IPTV panels block "okhttp/*" or empty UAs with a proxy challenge
        // (which surfaces here as an unauthenticated 407). VLC's UA is the
        // industry-standard "just let it through" string for IPTV endpoints.
        .addInterceptor(tv.enktel.app.data.net.UserAgentInterceptor(DEFAULT_UA))
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
    val trailers = tv.enktel.app.data.repo.TrailerRepository(http, settings)
    val downloads = DownloadHub(app, db.downloadDao(), db.profileDao(), settings, http)
    val discord = tv.enktel.app.data.net.DiscordAnnouncer(http, settings)

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
        tv.enktel.app.data.net.NetworkClass.install(this)
        // Hands the monitor an application Context. It doesn't sample until the
        // System Monitor screen asks it to — see SystemMonitor.start().
        tv.enktel.app.data.net.SystemMonitor.install(this)
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

/** Public so the ExoPlayer HTTP data source and the SpeedTestEngine probe
 *  can send the exact same UA the OkHttp client sends. Kept identical to
 *  what VLC 3 ships as its default network UA — WAF profiles that whitelist
 *  IPTV traffic universally allow it. */
const val DEFAULT_UA: String = "VLC/3.0.20 LibVLC/3.0.20"
