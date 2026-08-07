package tv.enktel.app.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import tv.enktel.app.data.net.NetworkClass
import tv.enktel.app.data.prefs.SettingsStore

/**
 * Process-scoped owner of the one and only [PlayerEngine].
 *
 * ### Why this exists (v1.38.0)
 *
 * Playback used to be owned by the player *screens*. `LivePlayerScreen` and
 * `VodPlayerScreen` are NavHost destinations, each doing
 * `remember { PlayerEngine(...) }` and releasing it in `onDispose` — so
 * navigating anywhere else tore the player down. That is why the app had no
 * way to check a download, look something up in the guide, or glance at the
 * Sports Hub without abandoning what you were watching, and why the old
 * `backAction == "guide_dock"` setting could only navigate to the guide and
 * hope (its own comment admitted the mini-player was "a follow-up piece of
 * work" — this is that work).
 *
 * Hoisting the engine here decouples *playback* from *what's on screen*. The
 * engine outlives every composable; screens borrow it. Two presentations draw
 * the same engine:
 *
 *  - **Fullscreen** — the player routes, unchanged, with their full OSD.
 *  - **Docked** — a small always-on-top window ([tv.enktel.app.ui.player.MiniPlayer])
 *    drawn over whatever the user navigated to.
 *
 * ### The invariant that matters
 *
 * An engine nobody can see is the bug this app has shipped twice (v1.35.1's
 * audio-after-exit, and the duplicate engine before it). The rule is therefore
 * explicit rather than emergent: **a player screen that goes away while the
 * session is not docked calls [stop]**. Docking is the only way to keep audio
 * alive without a visible player, and docking always draws the mini window.
 */
@UnstableApi
class PlaybackSession(
    private val app: Context,
    private val http: OkHttpClient,
    settings: SettingsStore,
    scope: CoroutineScope,
) {
    enum class Kind { LIVE, VOD }

    enum class Mode {
        /** The player route is on top and owns the whole screen. */
        FULLSCREEN,

        /** Playback continues in the mini window while the user browses. */
        DOCKED,
    }

    /**
     * What the mini window shows. Screens keep this current so the dock can
     * label itself without reaching back into the repositories.
     */
    data class NowPlaying(
        val kind: Kind,
        /**
         * Stable identity for what is loaded — a channel key for live, the
         * stream URL for VOD.
         *
         * This is what stops expanding the dock from restarting the stream.
         * A player screen re-mounting has no way of knowing whether the engine
         * is already playing the thing it was asked to play, so without an
         * identity to compare it would dutifully call `play()` again and the
         * user would watch their programme re-buffer from scratch every time
         * they came back from checking a download.
         */
        val contentId: String,
        val title: String,
        val subtitle: String = "",
        val logo: String = "",
        /** Route that re-opens the fullscreen player for this content. */
        val returnRoute: String,
    )

    /**
     * The subset of settings baked into an engine at construction. ExoPlayer
     * takes these in its builder, so changing one means a new engine — which is
     * why they're snapshotted rather than collected per-screen.
     */
    private data class EngineConfig(
        val bufferProfile: String = "balanced",
        val decoderMode: String = "hwplus",
        val minBufferMs: Int = 0,
        val lockToTopBitrate: Boolean = false,
        val dialogueBoost: String = "off",
        val vodBuffer: BufferConfig? = null,
        val liveBuffer: BufferConfig? = null,
        val allocatorSizeBytes: Int = 0,
    )

    @Volatile
    private var config = EngineConfig()

    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now.asStateFlow()

    private val _mode = MutableStateFlow(Mode.FULLSCREEN)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    /**
     * True while a screen is showing the picture inline in its own layout —
     * currently the TV Guide's dock.
     *
     * There is one engine and one surface, but two things want to draw it: the
     * floating mini window that follows you around the app, and an inline
     * preview belonging to a screen. Both were rendering at once, so the guide
     * showed its preview *and* the mini window floated over the corner — two
     * docked players, one of them fighting for a surface it could not keep.
     *
     * A screen that draws the picture itself claims it here for as long as it
     * is composed; the mini window stands down while anything is claiming.
     */
    private val _inlinePreview = MutableStateFlow(false)
    val inlinePreview: StateFlow<Boolean> = _inlinePreview.asStateFlow()

    /** Claim/release the inline surface. Balanced by the caller's DisposableEffect. */
    fun setInlinePreview(active: Boolean) {
        _inlinePreview.value = active
    }

    private var engineRef: PlayerEngine? = null

    init {
        scope.launch {
            val baseFlow = combine(
                settings.bufferProfile,
                settings.decoderMode,
                settings.minBufferMs,
                settings.companionMode,
                settings.dialogueBoost,
            ) { profile, decoder, minBuffer, companion, dialogue ->
                EngineConfig(
                    bufferProfile = profile,
                    decoderMode = decoder,
                    // Companion Mode holds a 30 s floor so a Discord viewer
                    // never sees the stall a local rebuffer causes.
                    minBufferMs = if (companion) maxOf(minBuffer, 30_000) else minBuffer,
                    lockToTopBitrate = companion,
                    dialogueBoost = dialogue,
                )
            }
            val vodBufFlow = combine(
                settings.vodBufferProfile,
                settings.vodMinBufferMs,
                settings.vodMaxBufferMs,
                settings.vodPlaybackMs,
                settings.vodRebufferMs,
            ) { profile, min, max, play, rebuf ->
                if (profile == "custom") BufferConfig(min, max, play, rebuf) else null
            }
            val liveBufFlow = combine(
                settings.liveBufferProfile,
                settings.liveMinBufferMs,
                settings.liveMaxBufferMs,
                settings.livePlaybackMs,
                settings.liveRebufferMs,
            ) { profile, min, max, play, rebuf ->
                if (profile == "custom") BufferConfig(min, max, play, rebuf) else null
            }
            combine(baseFlow, vodBufFlow, liveBufFlow, settings.allocatorSizeKb) { base, vod, live, allocKb ->
                base.copy(
                    vodBuffer = vod,
                    liveBuffer = live,
                    allocatorSizeBytes = allocKb * 1024,
                )
            }.collect { next ->
                val prev = config
                config = next
                // Dialogue boost is an audio effect on the existing session, so
                // it can move under a playing engine. Everything else is
                // constructor state.
                if (prev.dialogueBoost != next.dialogueBoost) {
                    engineRef?.setDialogueBoost(next.dialogueBoost)
                }
                // Rebuilding a live engine would kill playback the instant a
                // user touched a slider. Drop an idle one instead, so the next
                // thing they play picks the new settings up.
                if (_now.value == null && prev.copy(dialogueBoost = next.dialogueBoost) != next) {
                    releaseEngine()
                }
            }
        }
    }

    /**
     * The engine, built on first use. Safe to call from composition on every
     * recomposition — it returns the same instance until something releases it.
     */
    fun engine(): PlayerEngine {
        engineRef?.let { return it }
        val c = config
        val profile =
            if (c.bufferProfile == "auto") NetworkClass.suggestedBufferProfile else c.bufferProfile
        val created = PlayerEngine(
            app, http, profile,
            decoderMode = c.decoderMode,
            minBufferOverrideMs = c.minBufferMs,
            lockToTopBitrate = c.lockToTopBitrate,
            vodBuffer = c.vodBuffer,
            liveBuffer = c.liveBuffer,
            allocatorSizeBytes = c.allocatorSizeBytes,
        )
        created.setDialogueBoost(c.dialogueBoost)
        tv.enktel.app.voice.ActivePlayerRef.register(created.player)
        engineRef = created
        return created
    }

    /** The engine if one exists, without building one. */
    fun engineOrNull(): PlayerEngine? = engineRef

    /*
     * There is deliberately no bind/unbind pair here any more.
     *
     * It existed because a PlayerView has to be handed the player explicitly,
     * and the fullscreen player, the mini window and the guide dock hand that
     * one surface between them with no guaranteed ordering — Compose may mount
     * the new host before disposing the old one, or the other way round, and a
     * late detach from the loser left a black rectangle. Routing both sides
     * through this class made the order irrelevant.
     *
     * Every host now renders through ContentFrame, which attaches and detaches
     * with the composition, and ExoPlayer already guards the losing side:
     * clearVideoSurfaceView and clearVideoTextureView both compare against the
     * view that currently owns the surface and no-op otherwise. That is the
     * same invariant, enforced one layer down, so keeping a second copy of it
     * here would only be a thing to forget to update.
     */

    /** Screens call this as the content they're playing changes. */
    fun setNowPlaying(value: NowPlaying) {
        _now.value = value
    }

    /**
     * True when a live engine is already loaded with [contentId] — i.e. a
     * screen is mounting over a running stream rather than starting one.
     *
     * Must be read *before* [setNowPlaying] overwrites the answer.
     */
    fun isLoaded(contentId: String): Boolean =
        engineRef != null && _now.value?.contentId == contentId

    /** Shrink to the mini window and let the user browse. No-op when nothing
     *  is playing — docking an empty session would strand an unclosable
     *  window with no content. */
    fun dock(): Boolean {
        if (_now.value == null || engineRef == null) return false
        _mode.value = Mode.DOCKED
        return true
    }

    /** Called by a fullscreen player screen as it mounts. */
    fun expand() {
        _mode.value = Mode.FULLSCREEN
    }

    /** End playback and drop the engine. */
    fun stop() {
        _now.value = null
        _mode.value = Mode.FULLSCREEN
        releaseEngine()
        tv.enktel.app.data.net.PresenceTracker.clear()
    }

    private fun releaseEngine() {
        engineRef?.let {
            tv.enktel.app.voice.ActivePlayerRef.unregister(it.player)
            it.release()
        }
        engineRef = null
    }
}
