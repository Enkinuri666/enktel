package tv.enktel.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.enktel.app.ui.components.FirstRunTour
import tv.enktel.app.ui.components.ToastHost
import tv.enktel.app.ui.guide.GuideScreen
import tv.enktel.app.ui.live.LivePlayerScreen
import tv.enktel.app.ui.multi.MultiViewScreen
import tv.enktel.app.ui.player.DockCorner
import tv.enktel.app.ui.player.MiniPlayer
import tv.enktel.app.ui.screens.CatchupScreen
import tv.enktel.app.ui.screens.HomeScreen
import tv.enktel.app.ui.screens.OnboardingScreen
import tv.enktel.app.ui.screens.RecordingsScreen
import tv.enktel.app.ui.screens.SearchScreen
import tv.enktel.app.ui.screens.SettingsScreen
import tv.enktel.app.ui.screensaver.ScreensaverHost
import tv.enktel.app.ui.sports.SportsHubScreen
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelTheme
import tv.enktel.app.ui.vod.MovieDetailsScreen
import tv.enktel.app.ui.vod.MoviesScreen
import tv.enktel.app.ui.vod.SeriesDetailsScreen
import tv.enktel.app.ui.vod.SeriesScreen
import tv.enktel.app.ui.vod.VodPlayerScreen
import tv.enktel.app.ui.watchlist.WatchlistScreen
import java.net.URLDecoder
import java.net.URLEncoder

fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
fun decode(s: String): String = URLDecoder.decode(s, "UTF-8")

class MainActivity : ComponentActivity() {
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val graph = (application as EnktelApp).graph

        setContent {
            val themeId by graph.settings.theme.collectAsStateWithLifecycle(initialValue = "obsidian")
            val opacityPct by graph.settings.uiOpacityPct.collectAsStateWithLifecycle(initialValue = 92)
            val textPct by graph.settings.textScalePct.collectAsStateWithLifecycle(initialValue = 100)
            EnktelTheme(
                themeId = themeId,
                overlayOpacity = opacityPct / 100f,
                textScalePct = textPct,
            ) {
                val voiceBus = remember { tv.enktel.app.voice.VoiceCommandBus() }
                val wakeWordEnabled by graph.settings.wakeWordEnabled.collectAsStateWithLifecycle(initialValue = false)
                ToastHost {
                    // ActivePlayerRef.active flips true whenever a player screen
                    // (live or VOD) is mounted; passing it here means the
                    // screensaver overlay + the device-level idle countdown both
                    // stay dormant during playback, which is what the user
                    // expects for a TV/movie viewing session.
                    ScreensaverHost(
                        graph,
                        isPlaying = { tv.enktel.app.voice.ActivePlayerRef.active.value },
                    ) {
                        tv.enktel.app.voice.VoiceHost(voiceBus, wakeWordEnabled = wakeWordEnabled) {
                            MainNav(graph, voiceBus = voiceBus, initialChannelKey = intent?.getStringExtra("channel_key"))
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // let composable pick up new channel_key from notification taps
    }

    /**
     * v1.30.0 — global media hardware-key handler so a Fire TV / Android TV
     * remote's dedicated media buttons work everywhere, not just inside a
     * player-owned focus. Routes through ActivePlayerRef so we only act
     * when a player is actually mounted; otherwise falls through to the
     * default handler (letting menu / OS zoom / launcher shortcuts keep
     * their normal behavior).
     *
     * Also handles KEYCODE_CHANNEL_UP / KEYCODE_CHANNEL_DOWN — hardware
     * PVR-style channel keys — by invoking the zap handler that
     * LivePlayerScreen registers on mount. Non-live screens leave the
     * handler null and the key falls through.
     */
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Only claim transport keys while something is actually playing.
        //
        // These branches used to return true unconditionally, so play/pause,
        // rewind and fast-forward were swallowed app-wide even with no player
        // — the Fire TV remote's dedicated media buttons did nothing anywhere
        // in the app, and never reached the OS either.
        val playerLive = tv.enktel.app.voice.ActivePlayerRef.player != null
        if (playerLive && event.action == android.view.KeyEvent.ACTION_DOWN && !event.isCanceled) {
            when (event.keyCode) {
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    tv.enktel.app.voice.ActivePlayerRef.resume(); return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    tv.enktel.app.voice.ActivePlayerRef.pause(); return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (tv.enktel.app.voice.ActivePlayerRef.isPlaying()) tv.enktel.app.voice.ActivePlayerRef.pause()
                    else tv.enktel.app.voice.ActivePlayerRef.resume()
                    return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                    tv.enktel.app.voice.ActivePlayerRef.pause(); return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    tv.enktel.app.voice.ActivePlayerRef.seekForward(30); return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    tv.enktel.app.voice.ActivePlayerRef.seekBack(10); return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    tv.enktel.app.voice.ActivePlayerRef.channelZap(+1); return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    tv.enktel.app.voice.ActivePlayerRef.channelZap(-1); return true
                }
                android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                    tv.enktel.app.voice.ActivePlayerRef.channelZap(+1); return true
                }
                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    tv.enktel.app.voice.ActivePlayerRef.channelZap(-1); return true
                }
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    // Long-press OK on the D-Pad center toggles the current
                    // channel's favorite. Short-press falls through to Compose
                    // so cards, buttons, etc. still get their normal click —
                    // returning true here would break every button in the app.
                    if (event.isLongPress && tv.enktel.app.voice.ActivePlayerRef.toggleFavHandler != null) {
                        tv.enktel.app.voice.ActivePlayerRef.toggleFavorite()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // If the user hits Home while a player is on-screen and they've opted into
        // Auto-PiP-on-home in Settings, hand off to Picture-in-Picture instead of
        // just backgrounding — so playback keeps going in a floating window.
        val pip = tv.enktel.app.player.PictureInPicture
        if (pip.playerActive && pip.userWantsPipOnBack) {
            pip.enter(this)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MainNav(graph: AppGraph, voiceBus: tv.enktel.app.voice.VoiceCommandBus, initialChannelKey: String?) {
    val nav = rememberNavController()
    val activeId by graph.settings.activeProfileId.collectAsStateWithLifecycle(initialValue = -1L)
    val profiles by graph.playlists.profiles.collectAsStateWithLifecycle(initialValue = null)
    val firstRunDone by graph.settings.firstRunDone.collectAsStateWithLifecycle(initialValue = true)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var tourVisible by remember { mutableStateOf(false) }

    if (profiles == null || activeId < 0) return
    val start = if (profiles!!.isEmpty()) "onboarding" else "home"

    LaunchedEffect(firstRunDone, profiles) {
        if (!firstRunDone && profiles!!.isNotEmpty()) tourVisible = true
    }
    LaunchedEffect(initialChannelKey) {
        if (!initialChannelKey.isNullOrBlank()) nav.navigate("live?ch=$initialChannelKey")
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val appCtx = ctx.applicationContext

    // Voice-command navigation handler. Player-scoped commands (Pause/Resume/etc)
    // are consumed by whichever player screen is currently mounted; we take the
    // ones that navigate.
    val routeEntry by nav.currentBackStackEntryAsState()
    val intentRoute = routeEntry?.destination?.route
    LaunchedEffect(voiceBus) {
        voiceBus.intents.collect { intent ->
            when (intent) {
                is tv.enktel.app.voice.VoiceIntent.OpenHome -> nav.navigate("home")
                is tv.enktel.app.voice.VoiceIntent.OpenGuide -> nav.navigate("guide")
                is tv.enktel.app.voice.VoiceIntent.OpenLiveTv -> nav.navigate("live?ch=")
                is tv.enktel.app.voice.VoiceIntent.OpenMovies -> nav.navigate("movies")
                is tv.enktel.app.voice.VoiceIntent.OpenSeries -> nav.navigate("series")
                is tv.enktel.app.voice.VoiceIntent.SearchMovies -> {
                    // Route through the global search screen — it renders
                    // Movies and Series results in separate rails, so the
                    // scope is visible without needing a bespoke filter UI.
                    nav.navigate("search")
                    kotlinx.coroutines.delay(150)
                    voiceBus.searchQueries.emit(intent.query)
                }
                is tv.enktel.app.voice.VoiceIntent.SearchSeries -> {
                    nav.navigate("search")
                    kotlinx.coroutines.delay(150)
                    voiceBus.searchQueries.emit(intent.query)
                }
                is tv.enktel.app.voice.VoiceIntent.OpenWatchlist -> nav.navigate("watchlist")
                is tv.enktel.app.voice.VoiceIntent.OpenRecordings -> nav.navigate("recordings")
                is tv.enktel.app.voice.VoiceIntent.OpenSettings -> nav.navigate("settings")
                is tv.enktel.app.voice.VoiceIntent.FindSports -> nav.navigate("sports")
                is tv.enktel.app.voice.VoiceIntent.Search -> {
                    nav.navigate("search")
                    // Push the spoken query onto the bus so SearchScreen (which
                    // collects it in a LaunchedEffect) can populate its input.
                    // Kick after a short delay so the destination has time to
                    // mount its collector.
                    kotlinx.coroutines.delay(150)
                    voiceBus.searchQueries.emit(intent.query)
                }
                is tv.enktel.app.voice.VoiceIntent.TuneChannel -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val match = try {
                        val all = graph.content.channels(p.id).first()
                        val q = intent.query.lowercase()
                        all.firstOrNull { it.name.equals(intent.query, ignoreCase = true) }
                            ?: all.firstOrNull { q in it.name.lowercase() }
                            ?: intent.query.toIntOrNull()?.let { num ->
                                all.firstOrNull { it.num == num }
                            }
                    } catch (_: Throwable) { null }
                    if (match != null) nav.navigate("live?ch=${match.key}")
                }
                is tv.enktel.app.voice.VoiceIntent.TuneChannelWithAudio -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val match = try {
                        val all = graph.content.channels(p.id).first()
                        val q = intent.channel.lowercase()
                        all.firstOrNull { it.name.equals(intent.channel, ignoreCase = true) }
                            ?: all.firstOrNull { q in it.name.lowercase() }
                    } catch (_: Throwable) { null }
                    if (match != null) {
                        nav.navigate("live?ch=${match.key}")
                        // Give the new player a moment to register + report
                        // its track list before we try to apply the language
                        // preference — otherwise trackSelectionParameters
                        // would be set on the OLD (about-to-be-torn-down)
                        // player instance.
                        kotlinx.coroutines.delay(900)
                        tv.enktel.app.voice.ActivePlayerRef.setAudioLanguage(intent.language)
                    }
                }
                is tv.enktel.app.voice.VoiceIntent.SetAudioLanguage ->
                    tv.enktel.app.voice.ActivePlayerRef.setAudioLanguage(intent.language)
                is tv.enktel.app.voice.VoiceIntent.Suggest -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val pick = try {
                        graph.recommendations.trending(p.id).firstOrNull()
                            ?: graph.recommendations.newThisWeek(p.id).firstOrNull()
                            ?: graph.recommendations.latestReleases(p.id).firstOrNull()
                    } catch (_: Throwable) { null }
                    if (pick != null) nav.navigate("movie/${pick.key}")
                    else nav.navigate("home")
                }
                is tv.enktel.app.voice.VoiceIntent.Pause -> tv.enktel.app.voice.ActivePlayerRef.pause()
                is tv.enktel.app.voice.VoiceIntent.Resume -> tv.enktel.app.voice.ActivePlayerRef.resume()
                is tv.enktel.app.voice.VoiceIntent.SetVolume -> {
                    val am = appCtx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                    val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (intent.fraction * max).toInt().coerceIn(0, max), 0)
                }
                is tv.enktel.app.voice.VoiceIntent.VolumeUp -> {
                    val am = appCtx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                    am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, 0)
                }
                is tv.enktel.app.voice.VoiceIntent.VolumeDown -> {
                    val am = appCtx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                    am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, 0)
                }
                is tv.enktel.app.voice.VoiceIntent.Mute -> {
                    val am = appCtx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0)
                }
                is tv.enktel.app.voice.VoiceIntent.RecordNow, is tv.enktel.app.voice.VoiceIntent.ChannelUp,
                is tv.enktel.app.voice.VoiceIntent.ChannelDown, is tv.enktel.app.voice.VoiceIntent.Fullscreen -> Unit
                is tv.enktel.app.voice.VoiceIntent.Unknown -> {
                    // Contextual fallback prompt: what would a user probably
                    // have meant from THIS screen?  Softer than "I didn't
                    // understand" and gives them a viable retry.
                    val hint = when {
                        intentRoute == "movies" ->
                            "I missed that.  Try \"play random movie\" or \"search for a movie\"."
                        intentRoute == "series" ->
                            "I missed that.  Try \"play random series\" or \"more like\" a show name."
                        intentRoute == "sports" ->
                            "I missed that.  Try \"what live sports is on\"."
                        intentRoute == "guide" ->
                            "I missed that.  Try \"what's on tonight\" or a channel name."
                        intentRoute == "search" ->
                            "I missed that.  Try \"search for\" a title."
                        intentRoute?.startsWith("live?ch") == true ->
                            "I missed that.  Try \"pause\", \"channel up\", or \"turn to\" a channel."
                        intentRoute?.startsWith("vodPlayer") == true ->
                            "I missed that.  Try \"pause\", \"skip forward 30 seconds\", or \"restart\"."
                        else ->
                            "I missed that.  Try \"what should I watch\" or \"what's on now\"."
                    }
                    voiceBus.answers.emit(
                        tv.enktel.app.voice.VoiceAnswer(
                            eyebrow = "Didn't catch that",
                            heading = intent.heard.ifBlank { "Say again?" },
                            lines = emptyList(),
                            spoken = hint,
                        )
                    )
                }

                // ---- Query intents: answer back with a card + TTS ---------------
                is tv.enktel.app.voice.VoiceIntent.WhatSportsIsOn -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val events = try { graph.sports.load(p.id) } catch (_: Throwable) { emptyMap() }
                    val live = events["LIVE"].orEmpty()
                    val lines = live.take(6).map { ev ->
                        tv.enktel.app.voice.VoiceAnswerLine(
                            title = ev.title,
                            subtitle = "${ev.sport} · ${ev.channel.name}",
                            route = "live?ch=${ev.channel.key}",
                        )
                    }
                    val summary = when {
                        live.isEmpty() -> "No live sports on your channels right now."
                        live.size == 1 -> "One live match: ${live[0].title} on ${live[0].channel.name}."
                        else -> "${live.size} live matches right now. Top pick: ${live[0].title} on ${live[0].channel.name}."
                    }
                    voiceBus.answers.emit(
                        tv.enktel.app.voice.VoiceAnswer(
                            eyebrow = "🔴 Live sports right now",
                            heading = if (live.isEmpty()) "Nothing live" else "${live.size} match${if (live.size == 1) "" else "es"} on",
                            lines = lines, spoken = summary,
                        )
                    )
                }

                is tv.enktel.app.voice.VoiceIntent.LatestMovies -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val list = try { graph.recommendations.latestReleases(p.id, 8) } catch (_: Throwable) { emptyList() }
                    val lines = list.take(6).map { m ->
                        tv.enktel.app.voice.VoiceAnswerLine(
                            title = m.name,
                            subtitle = listOfNotNull(
                                if (m.year > 0) "${m.year}" else null,
                                m.genre.takeIf { it.isNotBlank() }?.take(28),
                                if (m.rating > 0) "★ ${"%.1f".format(m.rating)}" else null,
                            ).joinToString(" · "),
                            route = "movie/${m.key}",
                        )
                    }
                    val summary = if (list.isEmpty())
                        "No new movies in your library yet."
                    else "Fresh in: ${list.take(3).joinToString(", ") { it.name }}."
                    voiceBus.answers.emit(
                        tv.enktel.app.voice.VoiceAnswer(
                            eyebrow = "🆕 Latest movies",
                            heading = "Just added to EnkTel",
                            lines = lines, spoken = summary,
                        )
                    )
                }

                is tv.enktel.app.voice.VoiceIntent.UpcomingMovies -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val list = try { graph.recommendations.comingSoon(p.id, 8) } catch (_: Throwable) { emptyList() }
                    val lines = list.take(6).map { m ->
                        tv.enktel.app.voice.VoiceAnswerLine(
                            title = m.name,
                            subtitle = if (m.year > 0) "${m.year}" else m.genre.take(28),
                            route = "movie/${m.key}",
                        )
                    }
                    val summary = if (list.isEmpty())
                        "Nothing marked as coming soon yet."
                    else "Coming soon: ${list.take(3).joinToString(", ") { it.name }}."
                    voiceBus.answers.emit(
                        tv.enktel.app.voice.VoiceAnswer(
                            eyebrow = "🎬 Coming soon",
                            heading = "Upcoming titles",
                            lines = lines, spoken = summary,
                        )
                    )
                }

                is tv.enktel.app.voice.VoiceIntent.LatestSeries -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    // Latest N series by addedAt from the DAO.
                    val list = try {
                        graph.db.searchDao().searchSeriesDeep(p.id, "").take(8)
                    } catch (_: Throwable) { emptyList() }
                    val sortedList = list.sortedByDescending { it.year }.take(6)
                    val lines = sortedList.map { s ->
                        tv.enktel.app.voice.VoiceAnswerLine(
                            title = s.name,
                            subtitle = if (s.year > 0) "${s.year}" else s.genre.take(28),
                            route = "seriesDetails/${s.key}",
                        )
                    }
                    val summary = if (lines.isEmpty())
                        "No series in your library yet."
                    else "Recent series: ${sortedList.take(3).joinToString(", ") { it.name }}."
                    voiceBus.answers.emit(
                        tv.enktel.app.voice.VoiceAnswer(
                            eyebrow = "📺 Latest series",
                            heading = "Recent TV shows",
                            lines = lines, spoken = summary,
                        )
                    )
                }

                is tv.enktel.app.voice.VoiceIntent.WhatsOnNow -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val favs = try { graph.content.favoriteChannels(p.id).first() } catch (_: Throwable) { emptyList() }
                    val pool = if (favs.isNotEmpty()) favs
                    else try { graph.content.channels(p.id).first().take(30) } catch (_: Throwable) { emptyList() }
                    val entries = pool.take(8).mapNotNull { ch ->
                        val nn = try { graph.epg.nowNext(p.id, ch.epgId) } catch (_: Throwable) { null }
                        nn?.now?.let { prog -> ch to prog }
                    }
                    val lines = entries.map { (ch, prog) ->
                        tv.enktel.app.voice.VoiceAnswerLine(
                            title = prog.title,
                            subtitle = ch.name,
                            route = "live?ch=${ch.key}",
                        )
                    }
                    val summary = when {
                        entries.isEmpty() -> "No EPG data for your favourites right now."
                        entries.size == 1 -> "On now: ${entries[0].second.title} on ${entries[0].first.name}."
                        else -> "On your favourites now: ${entries.take(3).joinToString(", ") { it.second.title }}."
                    }
                    voiceBus.answers.emit(
                        tv.enktel.app.voice.VoiceAnswer(
                            eyebrow = "📡 On now",
                            heading = if (favs.isNotEmpty()) "Your favourite channels" else "Popular channels",
                            lines = lines, spoken = summary,
                        )
                    )
                }

                is tv.enktel.app.voice.VoiceIntent.WhatsOnChannel -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val all = try { graph.content.channels(p.id).first() } catch (_: Throwable) { emptyList() }
                    val q = intent.channel.lowercase()
                    val ch = all.firstOrNull { it.name.equals(intent.channel, ignoreCase = true) }
                        ?: all.firstOrNull { q in it.name.lowercase() }
                        ?: intent.channel.toIntOrNull()?.let { num -> all.firstOrNull { it.num == num } }
                    if (ch == null) {
                        voiceBus.answers.emit(
                            tv.enktel.app.voice.VoiceAnswer(
                                eyebrow = "❓ Not found",
                                heading = "No channel named \"${intent.channel}\"",
                                lines = emptyList(),
                                spoken = "I couldn't find a channel called ${intent.channel}.",
                            )
                        )
                        return@collect
                    }
                    val upcoming = try { graph.epg.upcoming(p.id, ch.epgId, 6) } catch (_: Throwable) { emptyList() }
                    val now = System.currentTimeMillis()
                    val lines = upcoming.map { prog ->
                        val isNow = prog.startMs <= now && prog.endMs > now
                        val time = tv.enktel.app.data.TimeFormat.format("HH:mm", prog.startMs)
                        tv.enktel.app.voice.VoiceAnswerLine(
                            title = (if (isNow) "🔴  " else "$time  ") + prog.title,
                            subtitle = if (isNow) "Now" else "",
                            route = "live?ch=${ch.key}",
                        )
                    }
                    val nowShow = upcoming.firstOrNull { it.startMs <= now && it.endMs > now }
                    val summary = if (nowShow != null) "On ${ch.name} now: ${nowShow.title}."
                        else if (upcoming.isNotEmpty()) "Next on ${ch.name}: ${upcoming[0].title}."
                        else "No guide data for ${ch.name}."
                    voiceBus.answers.emit(
                        tv.enktel.app.voice.VoiceAnswer(
                            eyebrow = "📡 ${ch.name}",
                            heading = "What's on next",
                            lines = lines, spoken = summary,
                        )
                    )
                }

                is tv.enktel.app.voice.VoiceIntent.TellMeAbout -> {
                    val kb = tv.enktel.app.voice.VoiceKnowledgeBase(graph)
                    val hit = kb.findTitle(intent.query)
                    if (hit != null) {
                        voiceBus.answers.emit(
                            tv.enktel.app.voice.VoiceAnswer(
                                eyebrow = "About",
                                heading = hit.name,
                                lines = listOf(
                                    tv.enktel.app.voice.VoiceAnswerLine(
                                        title = hit.name,
                                        subtitle = kb.describe(hit).take(120),
                                        route = kb.route(hit),
                                    )
                                ),
                                spoken = kb.describe(hit),
                            )
                        )
                    } else {
                        voiceBus.answers.emit(
                            tv.enktel.app.voice.VoiceAnswer(
                                eyebrow = "About",
                                heading = intent.query,
                                lines = emptyList(),
                                spoken = "I couldn't find ${intent.query} in your library. Try refreshing the playlist.",
                            )
                        )
                    }
                }

                // ---- Playback transport ---------------------------------------
                is tv.enktel.app.voice.VoiceIntent.SeekForward ->
                    tv.enktel.app.voice.ActivePlayerRef.seekForward(intent.seconds)
                is tv.enktel.app.voice.VoiceIntent.SeekBack ->
                    tv.enktel.app.voice.ActivePlayerRef.seekBack(intent.seconds)
                is tv.enktel.app.voice.VoiceIntent.SeekTo ->
                    tv.enktel.app.voice.ActivePlayerRef.seekToMinutes(intent.minutes)
                is tv.enktel.app.voice.VoiceIntent.Restart ->
                    tv.enktel.app.voice.ActivePlayerRef.restart()
                is tv.enktel.app.voice.VoiceIntent.SkipIntro ->
                    // Best-effort: 90 s ahead, roughly one intro's worth.
                    tv.enktel.app.voice.ActivePlayerRef.seekForward(90)
                is tv.enktel.app.voice.VoiceIntent.NextEpisode ->
                    tv.enktel.app.voice.ActivePlayerRef.next()
                is tv.enktel.app.voice.VoiceIntent.PreviousEpisode ->
                    tv.enktel.app.voice.ActivePlayerRef.previous()
                is tv.enktel.app.voice.VoiceIntent.EnterPip -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        try {
                            (ctx as? android.app.Activity)?.enterPictureInPictureMode(
                                android.app.PictureInPictureParams.Builder().build()
                            )
                        } catch (_: Throwable) {}
                    }
                }
                is tv.enktel.app.voice.VoiceIntent.CastNow ->
                    tv.enktel.app.player.CastToTv.open(appCtx)

                // ---- Content actions -------------------------------------------
                is tv.enktel.app.voice.VoiceIntent.PlayRandomMovie -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val all = try { graph.db.searchDao().searchMoviesDeep(p.id, "") } catch (_: Throwable) { emptyList() }
                    all.randomOrNull()?.let { nav.navigate("movie/${it.key}") }
                }
                is tv.enktel.app.voice.VoiceIntent.PlayRandomSeries -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val all = try { graph.db.searchDao().searchSeriesDeep(p.id, "") } catch (_: Throwable) { emptyList() }
                    all.randomOrNull()?.let { nav.navigate("seriesDetails/${it.key}") }
                }
                is tv.enktel.app.voice.VoiceIntent.ResumeLast,
                is tv.enktel.app.voice.VoiceIntent.ContinueWatching -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val last = try {
                        graph.db.userDao().continueWatching(p.id, 1).first().firstOrNull()
                    } catch (_: Throwable) { null }
                    val route = last?.key?.let { k ->
                        // key convention: "$profileId:vod:$id" / "$profileId:series:$id" / "live:$id"
                        when {
                            "vod" in k -> "movie/$k"
                            "series" in k -> "seriesDetails/$k"
                            else -> "home"
                        }
                    } ?: "home"
                    nav.navigate(route)
                }
                is tv.enktel.app.voice.VoiceIntent.AddToWatchlist -> {
                    val kb = tv.enktel.app.voice.VoiceKnowledgeBase(graph)
                    val hit = kb.findTitle(intent.query)
                    if (hit != null) {
                        val (kind, refId, name, poster) = when (hit) {
                            is tv.enktel.app.voice.VoiceKnowledgeBase.Hit.MovieHit ->
                                Quadruple("vod", hit.m.streamId, hit.m.name, hit.m.poster)
                            is tv.enktel.app.voice.VoiceKnowledgeBase.Hit.SeriesHit ->
                                Quadruple("series", hit.s.seriesId, hit.s.name, hit.s.poster)
                            else -> Quadruple("vod", 0L, hit.name, hit.poster)
                        }
                        val p = graph.playlists.activeProfile() ?: return@collect
                        try {
                            graph.db.watchlistDao().add(
                                tv.enktel.app.data.db.WatchlistItem(
                                    key = "${p.id}:$kind:$refId",
                                    profileId = p.id, kind = kind, refId = refId,
                                    name = name, poster = poster,
                                )
                            )
                            voiceBus.answers.emit(
                                tv.enktel.app.voice.VoiceAnswer(
                                    eyebrow = "☆ Watchlist",
                                    heading = "Added $name",
                                    lines = emptyList(),
                                    spoken = "Added $name to your watchlist.",
                                )
                            )
                        } catch (_: Throwable) {}
                    } else {
                        voiceBus.answers.emit(
                            tv.enktel.app.voice.VoiceAnswer(
                                eyebrow = "☆ Watchlist",
                                heading = "Not found",
                                lines = emptyList(),
                                spoken = "I couldn't find ${intent.query}.",
                            )
                        )
                    }
                }
                is tv.enktel.app.voice.VoiceIntent.RemoveFromWatchlist -> {
                    val kb = tv.enktel.app.voice.VoiceKnowledgeBase(graph)
                    val hit = kb.findTitle(intent.query)
                    val p = graph.playlists.activeProfile() ?: return@collect
                    if (hit != null) {
                        val (kind, refId) = when (hit) {
                            is tv.enktel.app.voice.VoiceKnowledgeBase.Hit.MovieHit -> "vod" to hit.m.streamId
                            is tv.enktel.app.voice.VoiceKnowledgeBase.Hit.SeriesHit -> "series" to hit.s.seriesId
                            else -> "vod" to 0L
                        }
                        try { graph.db.watchlistDao().remove("${p.id}:$kind:$refId") } catch (_: Throwable) {}
                        voiceBus.answers.emit(
                            tv.enktel.app.voice.VoiceAnswer(
                                eyebrow = "☆ Watchlist",
                                heading = "Removed ${hit.name}",
                                lines = emptyList(),
                                spoken = "Removed ${hit.name} from your watchlist.",
                            )
                        )
                    }
                }
                is tv.enktel.app.voice.VoiceIntent.MoreLike -> {
                    val kb = tv.enktel.app.voice.VoiceKnowledgeBase(graph)
                    val hit = kb.findTitle(intent.query)
                    if (hit != null) {
                        val sims = kb.similar(hit)
                        val lines = sims.take(6).map { h ->
                            tv.enktel.app.voice.VoiceAnswerLine(
                                title = h.name,
                                subtitle = kb.genre(h)?.take(28).orEmpty(),
                                route = kb.route(h),
                            )
                        }
                        voiceBus.answers.emit(
                            tv.enktel.app.voice.VoiceAnswer(
                                eyebrow = "Similar to ${hit.name}",
                                heading = "You might also like",
                                lines = lines,
                                spoken = if (sims.isEmpty()) "I couldn't find anything similar."
                                else "Here are ${sims.size} titles similar to ${hit.name}.",
                            )
                        )
                    }
                }

                // ---- Info / IMDb-style ----------------------------------------
                is tv.enktel.app.voice.VoiceIntent.WhoIsIn -> {
                    val kb = tv.enktel.app.voice.VoiceKnowledgeBase(graph)
                    val hit = kb.findTitle(intent.query)
                    val cast = hit?.let { kb.cast(it) }
                    voiceBus.answers.emit(voiceCard(
                        eyebrow = "Cast", heading = hit?.name ?: intent.query,
                        spoken = when {
                            hit == null -> "I couldn't find ${intent.query}."
                            cast == null -> "I don't have cast information for ${hit.name}."
                            else -> "${hit.name} stars ${cast.take(200)}."
                        }
                    ))
                }
                is tv.enktel.app.voice.VoiceIntent.WhoDirected -> {
                    val kb = tv.enktel.app.voice.VoiceKnowledgeBase(graph)
                    val hit = kb.findTitle(intent.query)
                    val d = hit?.let { kb.director(it) }
                    voiceBus.answers.emit(voiceCard(
                        eyebrow = "Director", heading = hit?.name ?: intent.query,
                        spoken = when {
                            hit == null -> "I couldn't find ${intent.query}."
                            d == null -> "I don't have director information for ${hit.name}."
                            else -> "${hit.name} was directed by $d."
                        }
                    ))
                }
                is tv.enktel.app.voice.VoiceIntent.WhatYear -> {
                    val kb = tv.enktel.app.voice.VoiceKnowledgeBase(graph)
                    val hit = kb.findTitle(intent.query)
                    val y = hit?.let { kb.year(it) }
                    voiceBus.answers.emit(voiceCard(
                        eyebrow = "Year", heading = hit?.name ?: intent.query,
                        spoken = when {
                            hit == null -> "I couldn't find ${intent.query}."
                            y == null -> "I don't have a year for ${hit.name}."
                            else -> "${hit.name} came out in $y."
                        }
                    ))
                }
                is tv.enktel.app.voice.VoiceIntent.WhatRating -> {
                    val kb = tv.enktel.app.voice.VoiceKnowledgeBase(graph)
                    val hit = kb.findTitle(intent.query)
                    val r = hit?.let { kb.rating(it) }
                    voiceBus.answers.emit(voiceCard(
                        eyebrow = "Rating", heading = hit?.name ?: intent.query,
                        spoken = when {
                            hit == null -> "I couldn't find ${intent.query}."
                            r == null -> "I don't have a rating for ${hit.name}."
                            else -> "${hit.name} is rated ${"%.1f".format(r)} out of ten."
                        }
                    ))
                }
                is tv.enktel.app.voice.VoiceIntent.WhatGenre -> {
                    val kb = tv.enktel.app.voice.VoiceKnowledgeBase(graph)
                    val hit = kb.findTitle(intent.query)
                    val g = hit?.let { kb.genre(it) }
                    voiceBus.answers.emit(voiceCard(
                        eyebrow = "Genre", heading = hit?.name ?: intent.query,
                        spoken = when {
                            hit == null -> "I couldn't find ${intent.query}."
                            g == null -> "I don't have a genre for ${hit.name}."
                            else -> "${hit.name} is $g."
                        }
                    ))
                }
                is tv.enktel.app.voice.VoiceIntent.PlotOf -> {
                    val kb = tv.enktel.app.voice.VoiceKnowledgeBase(graph)
                    val hit = kb.findTitle(intent.query)
                    val plot = hit?.let { kb.plot(it) ?: kb.describe(it) }
                    voiceBus.answers.emit(voiceCard(
                        eyebrow = "Plot", heading = hit?.name ?: intent.query,
                        spoken = plot ?: "I couldn't find ${intent.query}.",
                    ))
                }

                // ---- Discovery / EPG ------------------------------------------
                is tv.enktel.app.voice.VoiceIntent.WhatsOnTonight,
                is tv.enktel.app.voice.VoiceIntent.WhatsOnTomorrow -> {
                    // Reuse the existing WhatsOnNow rail — a proper time-window
                    // EPG scan lands with the next TV-guide refresh.
                    nav.navigate("guide")
                    voiceBus.answers.emit(voiceCard(
                        eyebrow = "TV Guide", heading = "Opening the guide",
                        spoken = "Opening the guide so you can browse tonight's schedule.",
                    ))
                }
                is tv.enktel.app.voice.VoiceIntent.WhenIsOn -> {
                    voiceBus.answers.emit(voiceCard(
                        eyebrow = "When is on", heading = intent.query,
                        spoken = "Opening the guide — try searching for ${intent.query} there.",
                    ))
                    nav.navigate("guide")
                }
                is tv.enktel.app.voice.VoiceIntent.TrendingNow -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val list = try { graph.recommendations.trending(p.id) } catch (_: Throwable) { emptyList() }
                    val lines = list.take(6).map { m ->
                        tv.enktel.app.voice.VoiceAnswerLine(
                            title = m.name,
                            subtitle = if (m.rating > 0) "★ ${"%.1f".format(m.rating)}" else "",
                            route = "movie/${m.key}",
                        )
                    }
                    voiceBus.answers.emit(voiceCard(
                        eyebrow = "🔥 Trending",
                        heading = "Popular right now",
                        lines = lines,
                        spoken = if (list.isEmpty()) "Nothing marked as trending yet."
                        else "Top of the charts: ${list.take(3).joinToString(", ") { it.name }}.",
                    ))
                }

                // ---- Sync ------------------------------------------------------
                is tv.enktel.app.voice.VoiceIntent.RefreshPlaylist -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    try { graph.content.refreshAll(p) } catch (_: Throwable) {}
                }
                is tv.enktel.app.voice.VoiceIntent.RefreshEpg -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    try { graph.epg.refresh(p) } catch (_: Throwable) {}
                }
                is tv.enktel.app.voice.VoiceIntent.ToggleTheme ->
                    nav.navigate("settings")
                is tv.enktel.app.voice.VoiceIntent.OpenSports ->
                    nav.navigate("sports")

                // ---- IPTV-specific --------------------------------------------
                is tv.enktel.app.voice.VoiceIntent.ShowChannelKind -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val chans = try { graph.content.channels(p.id).first() } catch (_: Throwable) { emptyList() }
                    val kw = intent.keyword.lowercase()
                    val hit = chans.firstOrNull { kw in it.name.lowercase() }
                        ?: chans.firstOrNull { kw in it.categoryId.lowercase() }
                    if (hit != null) nav.navigate("live?ch=${hit.key}") else nav.navigate("live?ch=")
                }
                is tv.enktel.app.voice.VoiceIntent.PlayTeamGame -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val team = intent.team.lowercase()
                    val events = try { graph.sports.load(p.id) } catch (_: Throwable) { emptyMap() }
                    val all = events.values.flatten()
                    val hit = all.firstOrNull { team in it.title.lowercase() }
                    if (hit != null) {
                        nav.navigate("live?ch=${hit.channel.key}")
                        voiceBus.answers.emit(voiceCard(
                            eyebrow = "⚽ ${intent.team}",
                            heading = hit.title,
                            spoken = "Tuning to ${hit.channel.name} for ${hit.title}.",
                        ))
                    } else {
                        voiceBus.answers.emit(voiceCard(
                            eyebrow = "⚽ ${intent.team}",
                            heading = "No live match found",
                            spoken = "I couldn't find a live ${intent.team} match on your channels right now.",
                        ))
                    }
                }
                is tv.enktel.app.voice.VoiceIntent.RemindWhenOn -> {
                    voiceBus.answers.emit(voiceCard(
                        eyebrow = "⏰ Reminder", heading = intent.query,
                        spoken = "Opening the guide — pick a time slot to schedule a reminder.",
                    ))
                    nav.navigate("guide")
                }
                is tv.enktel.app.voice.VoiceIntent.FilteredMovieSearch -> {
                    val p = graph.playlists.activeProfile() ?: return@collect
                    val g = intent.genre
                    val yr = intent.year
                    val dec = intent.decade
                    val actor = intent.actor
                    val seed = actor?.lowercase() ?: g?.lowercase() ?: ""
                    val pool = try { graph.db.searchDao().searchMoviesDeep(p.id, seed) }
                        catch (_: Throwable) { emptyList() }
                    val filtered = pool.filter { m ->
                        (g == null || m.genre.contains(g, ignoreCase = true)) &&
                        (yr == null || m.year == yr) &&
                        (dec == null || m.year in dec..(dec + 9)) &&
                        (actor == null || m.cast.contains(actor, ignoreCase = true))
                    }.take(12)
                    val lines = filtered.take(6).map { m ->
                        tv.enktel.app.voice.VoiceAnswerLine(
                            title = m.name,
                            subtitle = listOfNotNull(
                                m.year.takeIf { it > 0 }?.toString(),
                                m.genre.takeIf { it.isNotBlank() }?.take(24),
                            ).joinToString(" · "),
                            route = "movie/${m.key}",
                        )
                    }
                    val label = listOfNotNull(
                        g, yr?.toString(), dec?.let { "${it}s" }, actor,
                    ).joinToString(" · ")
                    voiceBus.answers.emit(voiceCard(
                        eyebrow = "🎬 Filtered movies", heading = label,
                        lines = lines,
                        spoken = if (filtered.isEmpty()) "I couldn't find any movies matching $label."
                        else "Found ${filtered.size} movies. Top pick: ${filtered[0].name}.",
                    ))
                }
            }
        }
    }

    // Mobile flavor gets a bottom-tab shell; the TV flavor renders the NavHost bare
    // and relies on the in-screen navigation you'd steer with a remote.
    val isMobileShell = BuildConfig.FLAVOR == "mobile" ||
        (LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK) != Configuration.UI_MODE_TYPE_TELEVISION &&
        BuildConfig.FLAVOR != "tv"

    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    // Give every destination a focus origin on TV.
    //
    // Nothing outside the player screens requested focus, so Compose started
    // each destination with no focused node — and a D-pad press with no origin
    // to search from does nothing at all. That is why the remote felt dead.
    // Requesting focus on a focusGroup enters the group and lands on its first
    // focusable child, so this covers every screen at once rather than needing
    // each one to nominate a control.
    val contentFocus = tv.enktel.app.ui.components.rememberScreenFocus(currentRoute, isMobileShell)

    val navHost = @Composable { padding: androidx.compose.foundation.layout.PaddingValues ->
    NavHost(
        navController = nav,
        startDestination = start,
        modifier = Modifier
            .fillMaxSize()
            .background(EnktelBg)
            .padding(padding)
            .then(
                // Touch builds must not take focus on mount: it pops the
                // soft keyboard on text fields and draws focus rings nobody
                // asked for.
                if (isMobileShell) Modifier
                else Modifier.focusRequester(contentFocus).focusGroup()
            ),
    ) {
        composable("onboarding") { OnboardingScreen(graph, onDone = { nav.navigate("home") { popUpTo(0) } }) }
        composable("home") {
            val kidsMode by graph.settings.kidsModeEnabled.collectAsStateWithLifecycle(initialValue = false)
            if (kidsMode) tv.enktel.app.ui.screens.KidsModeScreen(graph, nav) else HomeScreen(graph, nav)
        }
        composable("live?ch={ch}") { back ->
            LivePlayerScreen(graph, nav, initialChannelKey = back.arguments?.getString("ch").orEmpty())
        }
        composable("guide") { GuideScreen(graph, nav) }
        composable("movies") { MoviesScreen(graph, nav) }
        composable("movie/{key}") { back ->
            MovieDetailsScreen(graph, nav, key = back.arguments?.getString("key").orEmpty())
        }
        composable("series") { SeriesScreen(graph, nav) }
        composable("seriesDetails/{key}") { back ->
            SeriesDetailsScreen(graph, nav, key = back.arguments?.getString("key").orEmpty())
        }
        composable("vodPlayer?url={url}&title={title}&pk={pk}&live={live}") { back ->
            val a = back.arguments
            VodPlayerScreen(
                graph,
                nav,
                url = decode(a?.getString("url").orEmpty()),
                title = decode(a?.getString("title").orEmpty()),
                progressKey = a?.getString("pk").orEmpty(),
                isLive = a?.getString("live") == "1",
            )
        }
        composable("search") { SearchScreen(graph, nav, voiceBus = voiceBus) }
        composable("settings") { SettingsScreen(graph, nav) }
        composable("manageCategories") { tv.enktel.app.ui.screens.ManageCategoriesScreen(graph, nav) }
        composable("speedTest") { tv.enktel.app.ui.screens.SpeedTestScreen(graph, nav) }
        composable("recordings") { RecordingsScreen(graph, nav) }
        composable("downloads") { tv.enktel.app.ui.downloads.DownloadsScreen(graph, nav) }
        composable("catchup/{ch}") { back ->
            CatchupScreen(graph, nav, channelKey = back.arguments?.getString("ch").orEmpty())
        }
        composable("sports") { SportsHubScreen(graph, nav) }
        composable("sportsFinder") { tv.enktel.app.ui.sports.ChannelFinderScreen(graph, nav) }
        composable("matchCenter?event={event}&title={title}") { back ->
            tv.enktel.app.ui.sports.MatchCenterScreen(
                graph, nav,
                eventId = back.arguments?.getString("event").orEmpty(),
                fallbackTitle = back.arguments?.getString("title").orEmpty(),
            )
        }
        composable("systemMonitor") { tv.enktel.app.ui.screens.SystemMonitorScreen(graph, nav) }
        composable("watchlist") { WatchlistScreen(graph, nav) }
        composable("multi?left={left}&right={right}") { back ->
            MultiViewScreen(
                graph, nav,
                leftKey = back.arguments?.getString("left").orEmpty(),
                rightKey = back.arguments?.getString("right").orEmpty(),
            )
        }
    }
    }

    // ---- Docked playback (v1.38.0) -----------------------------------------
    //
    // The mini window is drawn here, above the NavHost, precisely because it
    // must outlive whatever destination is showing — that is the feature. A
    // player screen sets the session docked on its way out; this overlay picks
    // it up and keeps the picture on screen while the user browses.
    val nowPlaying by graph.playback.now.collectAsStateWithLifecycle()
    val playbackMode by graph.playback.mode.collectAsStateWithLifecycle()
    val docked = nowPlaying != null &&
        playbackMode == tv.enktel.app.player.PlaybackSession.Mode.DOCKED
    val backgroundAudio by graph.settings.backgroundAudio.collectAsStateWithLifecycle(initialValue = false)
    val dockCornerName by graph.settings.dockCorner.collectAsStateWithLifecycle(initialValue = "BOTTOM_END")
    val dockSizeStep by graph.settings.dockSizeStep.collectAsStateWithLifecycle(initialValue = 1)

    // Hold the display awake for as long as *something is playing*, not for as
    // long as a player screen is mounted. Both screens used to own their own
    // copy of this flag, which was equivalent while they owned the engine and
    // wrong the moment playback could outlive them: a docked stream would let
    // the panel sleep mid-programme.
    //
    // "Background audio" opts out deliberately — the point of that setting is
    // commentary continuing with the screen dark.
    val playbackActive = nowPlaying != null
    DisposableEffect(playbackActive, backgroundAudio) {
        val activity = ctx as? android.app.Activity
        val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (playbackActive && !backgroundAudio) activity?.window?.addFlags(flag)
        else activity?.window?.clearFlags(flag)
        onDispose { activity?.window?.clearFlags(flag) }
    }

    // Same reasoning for auto-PiP-on-home: it should follow playback, not the
    // VOD screen that happened to declare it.
    val pipOn by graph.settings.pipEnabled.collectAsStateWithLifecycle(initialValue = true)
    val autoPipOnHome by graph.settings.autoPipOnHome.collectAsStateWithLifecycle(initialValue = true)
    DisposableEffect(playbackActive, pipOn, autoPipOnHome) {
        tv.enktel.app.player.PictureInPicture.playerActive = playbackActive
        tv.enktel.app.player.PictureInPicture.userWantsPipOnBack = playbackActive && pipOn && autoPipOnHome
        // Push the flags to the system. setAutoEnterEnabled only takes effect
        // once the params are actually handed over, so setting the booleans
        // without this left auto-enter permanently off.
        (ctx as? android.app.Activity)?.let { tv.enktel.app.player.PictureInPicture.update(it) }
        onDispose {
            tv.enktel.app.player.PictureInPicture.playerActive = false
            tv.enktel.app.player.PictureInPicture.userWantsPipOnBack = false
            (ctx as? android.app.Activity)?.let { tv.enktel.app.player.PictureInPicture.update(it) }
        }
    }

    val expandDock = {
        nowPlaying?.let { np ->
            graph.playback.expand()
            // singleTop, or a dock/expand/dock/expand loop stacks a fresh entry
            // every round and Back turns into an archaeology dig.
            nav.navigate(np.returnRoute) { launchSingleTop = true }
        }
        Unit
    }

    val shell = @Composable {
    if (isMobileShell) {
        val kidsModeOnRoot by graph.settings.kidsModeEnabled.collectAsStateWithLifecycle(initialValue = false)
        tv.enktel.app.ui.mobile.MobileScaffold(
            nav = nav, currentRoute = currentRoute, voiceBus = voiceBus,
            kidsModeActive = kidsModeOnRoot && currentRoute == "home",
        ) { padding ->
            navHost(padding)
        }
    } else {
        // v1.29.0 TV cinematic phase 3 — collapsible left rail. Immersive
        // routes (players, onboarding, first-run) render bare; everything
        // else gets the rail (auto-expands on focus, collapses to 64 dp
        // icon strip when focus is in the content area).
        val kidsModeOnRoot by graph.settings.kidsModeEnabled.collectAsStateWithLifecycle(initialValue = false)
        val immersive = currentRoute == null ||
            currentRoute.startsWith("vodPlayer") ||
            currentRoute.startsWith("live?") ||
            currentRoute == "onboarding" ||
            (currentRoute == "home" && kidsModeOnRoot)
        if (immersive) {
            navHost(androidx.compose.foundation.layout.PaddingValues(0.dp))
        } else {
            tv.enktel.app.ui.components.TvNavShell(
                currentRoute = currentRoute,
                // A TV remote can't tap a floating window, and letting the dock
                // compete for D-pad focus with the grid behind it makes both
                // worse. The rail is where a TV user already goes to move
                // around, so that's where "back to what I was watching" lives.
                nowPlayingLabel = nowPlaying?.takeIf { docked }?.title,
                onNowPlaying = expandDock,
                onSelect = { route ->
                    if (currentRoute != route) {
                        nav.navigate(route) { launchSingleTop = true }
                    }
                },
            ) { padding ->
                navHost(padding)
            }
        }
    }
    }

    Box(Modifier.fillMaxSize()) {
        shell()
        val np = nowPlaying
        if (docked && np != null) {
            MiniPlayer(
                session = graph.playback,
                now = np,
                interactive = isMobileShell,
                sizeStep = dockSizeStep,
                corner = runCatching {
                    DockCorner.valueOf(dockCornerName)
                }.getOrDefault(DockCorner.BOTTOM_END),
                onCornerChange = { c: DockCorner -> scope.launch { graph.settings.setDockCorner(c.name) } },
                onExpand = expandDock,
                onClose = { graph.playback.stop() },
                // Clear the mobile tab bar rather than sitting behind it.
                bottomInset = if (isMobileShell) 78.dp else 0.dp,
            )
        }
    }

    if (tourVisible) {
        FirstRunTour(onFinish = {
            tourVisible = false
            scope.launch { graph.settings.setFirstRunDone(true) }
        })
    }
}

fun vodPlayerRoute(url: String, title: String, progressKey: String = "", live: Boolean = false): String =
    "vodPlayer?url=${encode(url)}&title=${encode(title)}&pk=$progressKey&live=${if (live) 1 else 0}"

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/** Compact voice-answer builder shared across the many small info intents. */
private fun voiceCard(
    eyebrow: String,
    heading: String,
    lines: List<tv.enktel.app.voice.VoiceAnswerLine> = emptyList(),
    spoken: String,
): tv.enktel.app.voice.VoiceAnswer = tv.enktel.app.voice.VoiceAnswer(
    eyebrow = eyebrow, heading = heading, lines = lines, spoken = spoken,
)
