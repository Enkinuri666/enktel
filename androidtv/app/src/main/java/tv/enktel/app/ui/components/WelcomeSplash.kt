package tv.enktel.app.ui.components

import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.enktel.app.R
import tv.enktel.app.ui.theme.EnktelBg
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * First-run welcome video.
 *
 * Three rules, because a splash screen is the easiest thing in an app to get
 * hostile:
 *
 *  1. **It plays once.** Gated on a persisted flag, not on process start.
 *  2. **It is always skippable** — any tap, any key. Ten seconds is short
 *     until it is the fourth time you have seen it.
 *  3. **It fails open.** A decode error, a missing file, anything at all, and
 *     [onDone] fires immediately. Nobody is ever locked out of the app by a
 *     decoration, and there is a hard timeout in case the player neither
 *     starts nor errors.
 *
 * Sizing is RESIZE_MODE_ZOOM: the source is 16:9 and real screens are not.
 * Letterboxing a full-bleed brand video looks broken, so it fills the surface
 * and crops the overflow — correct on a 16:9 TV, a 20:9 Galaxy S25 Ultra, and
 * a squarer tablet alike.
 */
object WelcomeSplash {
    /**
     * Set while the splash is on screen so `MainActivity.dispatchKeyEvent` can
     * dismiss it from a remote. A D-pad has nothing to tap, and Compose does
     * not see these keys before the activity does — so the escape hatch has to
     * live at the activity level or TV users have no way out.
     */
    @Volatile
    var skipHandler: (() -> Unit)? = null
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun WelcomeSplash(onDone: () -> Unit) {
    val context = LocalContext.current
    var finished by remember { mutableStateOf(false) }

    fun finish() {
        if (!finished) {
            finished = true
            onDone()
        }
    }

    val player = remember {
        runCatching {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(
                    MediaItem.fromUri(
                        "android.resource://${context.packageName}/${R.raw.welcome}".toUri(),
                    ),
                )
                repeatMode = Player.REPEAT_MODE_OFF
                // Audio left at its natural level; the video is short and the
                // user pressed nothing to get here, so it should not be a
                // surprise blast — but muting a branded intro guts it.
                volume = 0.7f
                prepare()
                playWhenReady = true
            }
        }.getOrNull()
    }

    // No player at all (decoder refused, resource missing) — do not sit on a
    // black screen waiting for something that will never happen.
    LaunchedEffect(player) { if (player == null) finish() }

    DisposableEffect(Unit) {
        WelcomeSplash.skipHandler = { finish() }
        onDispose { WelcomeSplash.skipHandler = null }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) finish()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                finish()
            }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            player?.release()
        }
    }

    // Belt and braces: the clip is 10 s, so anything past 14 s means the player
    // has stalled without reporting either an end or an error.
    LaunchedEffect(Unit) {
        delay(14_000)
        finish()
    }

    AnimatedVisibility(visible = !finished, exit = fadeOut(tween(400))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(EnktelBg)
                .tapClick { finish() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (player != null) {
                // First call site on media3-ui-compose.
                //
                // Deliberately started here rather than in one of the two full
                // players: this surface has no subtitles, no controls, and — the
                // part that matters — no PlaybackSession.bind/unbind. It is a
                // straight swap, so it proves PlayerSurface renders correctly in
                // this app without touching the single-surface hand-off between
                // the fullscreen player, the mini window and the guide dock,
                // which is the mechanism behind two bugs shipped this week.
                //
                // SURFACE_TYPE_SURFACE_VIEW, not TEXTURE_VIEW: a SurfaceView
                // composites in the display pipeline instead of going through
                // the GPU as a texture, which on a Fire TV Stick is the
                // difference between a smooth first frame and a stutter behind
                // the splash. The trade-off is that it cannot be animated or
                // z-ordered freely — irrelevant for a full-bleed background.
                PlayerSurface(
                    player = player,
                    surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                "Tap or press any key to skip",
                color = EnktelTextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 28.dp),
            )
        }
    }
}
