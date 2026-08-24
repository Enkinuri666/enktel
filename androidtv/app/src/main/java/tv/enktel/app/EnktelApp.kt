package tv.enktel.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil3.ImageLoader
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
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
import java.util.concurrent.TimeUnit

/**
 * AGP 9 ships a newer lint that flags the *type* of an exposed property, not
 * only the call that builds it. [playback] is a `PlaybackSession`, which is
 * itself `@UnstableApi`, so opting in at the assignment was no longer enough —
 * the declaration exposing that type has to opt in too. Marked here rather
 * than baselined: AppGraph really is the thing consuming media3's unstable
 * surface, and saying so is the point of the annotation.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AppGraph(app: Application) {
    val db = AppDatabase.build(app)
    val settings = SettingsStore(app)
    // Volatile so the health interceptor can read the latest without a Flow
    // subscription — updated whenever the setting flow emits (see below).
    @Volatile private var backupGatewaysSnapshot: List<String> = emptyList()

    /**
     * The viewer's own relay endpoint, or blank for the built-in one.
     *
     * Same reason as the gateways above: read on the failure path of any
     * request, so it cannot be a Flow subscription there.
     */
    @Volatile private var relayBaseSnapshot: String = ""

    // Read on every request by UserAgentInterceptor, so changing it in
    // Settings (or via the Panel Doctor's auto-tune) takes effect immediately
    // rather than needing the OkHttp client rebuilt.
    /**
     * The active provider's User-Agent, or blank.
     *
     * Held beside the global one rather than folded into it, so the two stay
     * separable: Settings shows which of them is actually in force, and
     * switching provider must not leave the previous provider's agent behind.
     */
    @Volatile private var profileUserAgent: String = ""
    @Volatile private var userAgentOverride: String = ""
    val http: OkHttpClient = OkHttpClient.Builder()
        // Wider timeouts so slower IPTV proxy layers (Cloudflare, IPTV-Editor,
        // reseller relays) don't trip the "unable to measure throughput" or
        // ExoPlayer's own read-timeout error before the first byte lands.
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS) // unlimited overall — long VOD downloads shouldn't be capped
        .retryOnConnectionFailure(true)
        // Still no inherited proxy: an OS- or JVM-level one applied to every
        // call without credentials produces a 407 loop, and picking one up
        // silently is worse than refusing all of them. A selector rather than
        // a pinned Proxy so the refusal has one exception — an exit the viewer
        // configured deliberately, used only for hosts that have already
        // refused this device. See ProxyRoute; it answers NO_PROXY for
        // everything else, which is what `.proxy(Proxy.NO_PROXY)` did here.
        .proxySelector(tv.enktel.app.data.net.ProxyRoute.selector())
        .proxyAuthenticator(tv.enktel.app.data.net.ProxyRoute.authenticator())
        // Negotiate with old panels *and* old devices.
        //
        // MODERN_TLS alone (OkHttp's default) restricts the cipher list, and
        // this app supports API 23 — Marshmallow-era Fire TV hardware, whose
        // TLS stack predates most of it. Plenty of IPTV panels sit behind
        // equally dated nginx builds. Offering COMPATIBLE_TLS as a fallback
        // widens the overlap at both ends. CLEARTEXT is listed because a great
        // many panels are http-only.
        //
        // This does not weaken certificate verification: the trust manager and
        // hostname verifier are untouched, only the cipher/protocol menu is
        // broader.
        .connectionSpecs(
            listOf(
                okhttp3.ConnectionSpec.MODERN_TLS,
                okhttp3.ConnectionSpec.COMPATIBLE_TLS,
                okhttp3.ConnectionSpec.CLEARTEXT,
            )
        )
        // Send a well-known media UA on every request. Many Cloudflare WAFs +
        // IPTV panels block "okhttp/*" or empty UAs with a proxy challenge
        // (which surfaces here as an unauthenticated 407). VLC's UA is the
        // industry-standard "just let it through" string for IPTV endpoints.
        .addInterceptor(
            tv.enktel.app.data.net.UserAgentInterceptor(DEFAULT_UA) {
                // Provider first, then the device-wide override. See UserAgents.
                tv.enktel.app.data.net.UserAgents.effective(
                    profile = profileUserAgent, global = userAgentOverride, default = "",
                )
            },
        )
        .addInterceptor(
            tv.enktel.app.data.net.StreamHealthInterceptor(
                gateways = { backupGatewaysSnapshot },
                // Every message this produced used to go into the default
                // empty lambda, so a stream that failed over and a stream that
                // never tried looked identical — to the viewer and to anyone
                // trying to work out which had happened.
                notify = { tv.enktel.app.data.net.StreamHealth.note(it) },
                relayBase = {
                    relayBaseSnapshot.ifBlank { tv.enktel.app.data.net.RelayUrls.DEFAULT_BASE }
                },
            )
        )
        // Adds ISRG Root X1/X2 as trust anchors on pre-7.1.1 Android, where the
        // system store predates Let's Encrypt. No-op from API 25 up. See
        // LegacyTls — system anchors are still tried first, and nothing else
        // becomes trusted.
        .let { tv.enktel.app.data.net.LegacyTls.install(it) }
        .build()

    /**
     * The same client, minus the automatic failover — for anything whose job
     * is to *report* what the panel did rather than to get a stream playing.
     *
     * [tv.enktel.app.data.net.StreamHealthInterceptor] turns a 403 into a
     * thrown `IOException` when no backup gateway is configured. That is right
     * for playback: a blocked request should fail over or fail fast, and the
     * user wants a picture, not a status code. It is exactly wrong for a
     * diagnostic. `SystemMonitor.probeLatency` documents that "403 and 404 are
     * successful round trips here" — and then never sees one, because the
     * interceptor has already converted it to a transport error. The panel
     * answers, the ping reports no reply, and the tester is sent looking for a
     * network fault that does not exist. The speed test's probes had the same
     * blind spot, which is how a reply became `HTTP 0` in the report.
     *
     * Built by removing one interceptor rather than by assembling a second
     * client, so the timeouts, TLS/cipher fallbacks, NO_PROXY and user agent
     * cannot drift from the client whose behaviour these tools exist to
     * measure.
     */
    val diagHttp: OkHttpClient = http.newBuilder()
        .apply { interceptors().removeAll { it is tv.enktel.app.data.net.StreamHealthInterceptor } }
        .build()

    val xtream = XtreamClient(http)
    val trialClient = tv.enktel.app.data.net.EagleTrialClient(http)
    val playlists = PlaylistRepository(db.profileDao(), settings, xtream, trialClient)
    // settings passed so a sync also folds in whatever playlist files the
    // viewer imported — they attach to a profile rather than replacing it.
    val content = ContentRepository(app, db, xtream, http, settings)
    // settings passed so the "EPG timezone offset" chips in Settings actually
    // move the guide — before this they were written and read by nothing.
    val epg = EpgRepository(db, xtream, http, settings)
    val sports = SportsRepository(content, epg)
    val watchlist = WatchlistRepository(db.watchlistDao())
    val recommendations = RecommendationsRepository(content)
    @Volatile private var sportsDbKeySnapshot: String = ScoresRepository.FREE_KEY
    val scores = ScoresRepository(http) { sportsDbKeySnapshot }
    val trailers = tv.enktel.app.data.repo.TrailerRepository(http, settings)
    val feed = tv.enktel.app.data.repo.EnktelFeed(http)
    val downloads = DownloadHub(app, db.downloadDao(), db.profileDao(), settings, http)
    val discord = tv.enktel.app.data.net.DiscordAnnouncer(http, settings)

    /**
     * Owns the ExoPlayer instance for the whole process, so playback survives
     * navigation and can keep running in the docked mini window while the user
     * browses the rest of the app. See [tv.enktel.app.player.PlaybackSession].
     */
    val playback = tv.enktel.app.player.PlaybackSession(
        app, http, settings,
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate,
        ),
    )

    /**
     * Lives as long as the process. For work that must outlive the composable
     * that started it — saving a resume point as the player screen goes away,
     * for instance, where a composition-scoped scope would be cancelled at
     * exactly the wrong moment.
     */
    val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    init {
        val bgScope = appScope
        bgScope.launch {
            settings.backupGateways.collect { backupGatewaysSnapshot = it }
        }
        bgScope.launch {
            settings.relayBase.collect { relayBaseSnapshot = it }
        }
        bgScope.launch {
            settings.proxyConfig.collect { (endpoint, user, pass) ->
                tv.enktel.app.data.net.ProxyRoute.configure(
                    tv.enktel.app.data.net.ProxyRoute.parse(endpoint, user, pass),
                )
            }
        }
        bgScope.launch {
            settings.customUserAgent.collect { userAgentOverride = it }
        }
        // Follow the active provider's agent, so switching line switches it.
        bgScope.launch {
            settings.activeProfileId.collect { id ->
                profileUserAgent = runCatching {
                    db.profileDao().byId(id)?.userAgent.orEmpty()
                }.getOrDefault("")
            }
        }
        bgScope.launch {
            settings.sportsDbKey.collect {
                sportsDbKeySnapshot = it.ifBlank { ScoresRepository.FREE_KEY }
            }
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
        // Recordings can't survive the process dying — the foreground service
        // goes with it. Rows left claiming RECORDING would otherwise show a
        // permanent "● REC" badge and a Stop button with nothing to stop.
        // A partial recording with real content is still watchable, so anything
        // that got past the service's own 5 MB "worth keeping" threshold is
        // closed as DONE rather than thrown away as FAILED.
        bgScope.launch {
            try {
                val dao = db.recordingDao()
                dao.inFlight().forEach { rec ->
                    val bytes = runCatching {
                        if (rec.filePath.isNotBlank()) java.io.File(rec.filePath).length() else 0L
                    }.getOrDefault(0L)
                    dao.update(
                        rec.copy(
                            status = if (bytes > 5L shl 20) "DONE" else "FAILED",
                            sizeBytes = bytes,
                        )
                    )
                }
            } catch (_: Throwable) { /* best effort — never block start-up */ }
        }
    }
}

/**
 * [SingletonImageLoader.Factory] so Coil's shared loader is built here rather
 * than discovered.
 *
 * Coil 3 splits the network fetcher into its own artifact and finds it
 * through a `META-INF/services` entry. That works, but R8 renames both the
 * interface and the file, and a fetcher that fails to register does not throw
 * — every poster and channel logo simply resolves to nothing, in release
 * builds only. Registering it explicitly takes that whole question off the
 * table.
 *
 * Handing it [AppGraph.http] matters for its own sake: that client carries
 * the VLC user agent panels are whitelisted against, the NO_PROXY setting
 * that avoids 407 loops, and the TLS list that keeps API 23 hardware
 * working. A Coil-built client would have none of it, and artwork hosted on
 * the same panel as the streams would fail for reasons nothing in the app
 * could explain.
 */
class EnktelApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { graph.http }))
            }
            // Fade artwork in rather than letting it snap.
            //
            // This app draws thousands of channel logos and VOD posters, all
            // arriving at different moments over a domestic connection, and
            // every one of them appeared as an instant pop from the placeholder
            // to the image. A grid settling in is a dozen unrelated pops, which
            // is a large part of why a fully-populated screen looked cheap even
            // when every individual element was fine.
            //
            // 220 ms: long enough to register as a fade, short enough that a
            // cached image still feels immediate on a Fire TV Stick.
            .crossfade(220)
            // Memory budget, sized for the device this actually runs on.
            //
            // A Fire TV Stick Lite has 1 GB of RAM for the entire system, and
            // Coil's default memory cache takes a fifth of what the app is
            // allowed. A catalogue browse pulls thousands of posters and
            // channel logos through that cache, and the app is simultaneously
            // holding an ExoPlayer, its buffers and a Room database — so the
            // default is a slow walk towards an OOM on exactly the hardware
            // this is built for. 15 % leaves room for the player, which is the
            // part the user will actually notice failing.
            .memoryCache {
                coil3.memory.MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .build()
            }
            // allowRgb565, *not* bitmapConfig(RGB_565).
            //
            // The obvious way to halve the bytes per pixel is to set
            // bitmapConfig directly, and it is wrong here. Coil applies an
            // explicit bitmapConfig with no alpha guard at all — it goes
            // straight to inPreferredConfig — and RGB_565 has no alpha channel.
            // Channel logos are overwhelmingly PNGs with transparency, so a
            // blanket RGB_565 would have flattened every one of them onto
            // whatever happened to be behind it. (Checked in the decoder
            // bytecode rather than assumed; the alpha branch only exists on the
            // allowRgb565 path.)
            //
            // allowRgb565 asks Coil to downgrade only where it is safe: JPEGs,
            // which are opaque by definition. That is posters and backdrops —
            // the large images that actually dominate the cache — while logos
            // keep their alpha. Half the memory on the half that matters.
            .allowRgb565(true)
            .build()

    override fun onCreate() {
        super.onCreate()
        // First, before the object graph exists. A crash while building the
        // graph is exactly the case that leaves a tester with a dead app and
        // nothing to send us, so the black box has to be running before
        // anything that could fail.
        tv.enktel.app.data.diag.CrashLog.install(this)
        graph = AppGraph(this)

        // Everything below is optional infrastructure: thermal monitoring,
        // network classification, the system monitor's context, the EPG
        // refresh schedule and the DVR notification channel. None of it is
        // needed to show a screen, and each one talks to a platform service
        // whose behaviour differs across the eight Android versions this app
        // supports — the app runs on Fire OS 6 and on current phones from the
        // same binary.
        //
        // They were called bare, so a throw in any of them killed the app
        // before the first frame, on the devices where that platform service
        // behaves unusually. That is the worst possible failure mode for the
        // least important code: an app that will not start at all because its
        // temperature sensor hookup did not like something.
        //
        // Each is isolated and its failure named in the log. If one of these is
        // ever the reason a device misbehaves, `adb logcat -s EnktelApp` says
        // which — and the app still starts.
        startupStep("thermal guard") { tv.enktel.app.data.net.ThermalGuard.install(this) }
        startupStep("network class") { tv.enktel.app.data.net.NetworkClass.install(this) }
        // Hands the monitor an application Context. It doesn't sample until the
        // System Monitor screen asks it to — see SystemMonitor.start().
        startupStep("system monitor") { tv.enktel.app.data.net.SystemMonitor.install(this) }
        startupStep("epg schedule") { tv.enktel.app.data.epg.EpgRefreshWorker.schedule(this) }
        startupStep("dvr channel") {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(
                    NotificationChannel(DVR_CHANNEL, "DVR Recordings", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    /**
     * Runs one optional piece of startup wiring, and lets the app live if it
     * fails. Deliberately not silent — a swallowed exception that nobody can
     * see is how a real fault turns into a mystery.
     */
    private inline fun startupStep(name: String, block: () -> Unit) {
        runCatching(block).onFailure {
            android.util.Log.w("EnktelApp", "startup step '$name' failed; continuing without it", it)
            // Recorded as well as logged. "The app starts now" is only half an
            // answer — a caught failure here means a subsystem is quietly off,
            // and the tester who can see which one is the tester who can tell
            // us. Settings ▸ About shows this without needing a computer.
            tv.enktel.app.data.diag.CrashLog.noteStartupFailure(name, it)
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
