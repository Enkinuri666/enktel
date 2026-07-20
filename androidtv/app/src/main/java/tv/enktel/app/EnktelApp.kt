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
import tv.enktel.app.data.xtream.XtreamClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppGraph(app: Application) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    val db = AppDatabase.build(app)
    val settings = SettingsStore(app)
    val xtream = XtreamClient(http)
    val playlists = PlaylistRepository(db.profileDao(), settings, xtream)
    val content = ContentRepository(app, db, xtream, http)
    val epg = EpgRepository(db, xtream, http)
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
