package tv.enktel.app.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import tv.enktel.app.player.PlaybackSession
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelLive
import tv.enktel.app.ui.theme.EnktelSurfaceHigh
import tv.enktel.app.ui.theme.EnktelTextDim

/** Where the dock sits. Persisted so it stays put across sessions. */
enum class DockCorner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END }

/**
 * The docked mini window: playback that keeps going while the user browses
 * downloads, the guide, Movies, the Sports Hub — anything.
 *
 * Deliberately drawn as an overlay in `MainNav` rather than inside any screen.
 * It has to survive the NavHost swapping destinations underneath it, which is
 * the entire point of the feature, and a composable that lives inside a
 * destination cannot do that.
 *
 * ### Touch vs. D-pad
 *
 * On phones the window is a direct-manipulation object: tap the video to go
 * fullscreen, drag it to whichever corner is least in the way, and use the
 * inline transport row. On TV there is no pointer, and a floating overlay that
 * competes for D-pad focus with the grid behind it makes both harder to use —
 * so the TV build renders the window as a non-focusable picture and drives it
 * from the nav rail's "Now playing" entry and the remote's media keys, which
 * `MainActivity.dispatchKeyEvent` already routes to the active player.
 */
@UnstableApi
@Composable
fun BoxScope.MiniPlayer(
    session: PlaybackSession,
    now: PlaybackSession.NowPlaying,
    /** True on phone/tablet builds — enables touch affordances. */
    interactive: Boolean,
    /** 0..2 — small / medium / large. */
    sizeStep: Int,
    corner: DockCorner,
    onCornerChange: (DockCorner) -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    /** Extra bottom inset so the window clears the mobile tab bar. */
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val engine = session.engineOrNull() ?: return
    val config = LocalConfiguration.current

    // Sized against the shortest edge so the window is a consistent fraction of
    // the display rather than a fixed dp that swamps a phone in portrait and
    // vanishes on a 55" panel.
    val shortestDp = minOf(config.screenWidthDp, config.screenHeightDp)
    val widthDp = when (sizeStep) {
        0 -> shortestDp * 0.34f
        2 -> shortestDp * 0.62f
        else -> shortestDp * 0.46f
    }.coerceIn(150f, 420f).dp

    var playing by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(engine) {
        while (true) {
            playing = engine.player.isPlaying
            val dur = engine.player.duration
            progress = if (dur > 0) {
                (engine.player.currentPosition.toFloat() / dur).coerceIn(0f, 1f)
            } else 0f
            delay(500)
        }
    }

    // Controls fade out so the window settles into being a picture, and come
    // back on touch. On TV they never show — there's nothing to touch them with.
    var controlsVisible by remember { mutableStateOf(interactive) }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible && interactive) {
            delay(4000)
            controlsVisible = false
        }
    }
    val controlsAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = tween(180),
        label = "mini-controls",
    )

    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    val alignment = when (corner) {
        DockCorner.TOP_START -> Alignment.TopStart
        DockCorner.TOP_END -> Alignment.TopEnd
        DockCorner.BOTTOM_START -> Alignment.BottomStart
        DockCorner.BOTTOM_END -> Alignment.BottomEnd
    }
    val atBottom = corner == DockCorner.BOTTOM_START || corner == DockCorner.BOTTOM_END

    Column(
        Modifier
            .align(alignment)
            .padding(
                start = 12.dp, end = 12.dp, top = 12.dp,
                bottom = 12.dp + if (atBottom) bottomInset else 0.dp,
            )
            .width(widthDp)
            .clip(RoundedCornerShape(12.dp))
            .background(EnktelSurfaceHigh)
            .border(1.dp, Color.White.copy(0.14f), RoundedCornerShape(12.dp))
            .then(
                if (!interactive) Modifier else Modifier.pointerInput(corner) {
                    detectDragGestures(
                        onDragEnd = {
                            // Snap to whichever corner the gesture pushed toward
                            // rather than leaving the window mid-air: a free
                            // position would need clamping against every screen
                            // size and inset, and corners are what people
                            // actually want anyway.
                            val horizontal = when {
                                dragX > 60f -> true
                                dragX < -60f -> false
                                else -> corner == DockCorner.TOP_END || corner == DockCorner.BOTTOM_END
                            }
                            val vertical = when {
                                dragY > 60f -> true
                                dragY < -60f -> false
                                else -> atBottom
                            }
                            onCornerChange(
                                when {
                                    vertical && horizontal -> DockCorner.BOTTOM_END
                                    vertical -> DockCorner.BOTTOM_START
                                    horizontal -> DockCorner.TOP_END
                                    else -> DockCorner.TOP_START
                                }
                            )
                            dragX = 0f; dragY = 0f
                        },
                    ) { change, drag ->
                        change.consume()
                        dragX += drag.x
                        dragY += drag.y
                    }
                }
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
                .then(
                    if (!interactive) Modifier else Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (controlsVisible) onExpand() else controlsVisible = true
                            },
                            onDoubleTap = { onExpand() },
                        )
                    }
                ),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setKeepContentOnPlayerReset(true)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                update = { view -> session.bind(view) },
                onRelease = { view -> session.unbind(view) },
                modifier = Modifier.fillMaxSize(),
            )

            if (now.kind == PlaybackSession.Kind.LIVE) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(EnktelLive)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        "● LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black,
                    )
                }
            } else if (progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White.copy(0.18f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(2.dp)
                            .background(EnktelBlue),
                    )
                }
            }

            if (interactive && controlsAlpha > 0.01f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .alpha(controlsAlpha)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(0.5f), Color.Transparent, Color.Black.copy(0.35f)),
                            ),
                        ),
                ) {
                    MiniIconButton("✕", Modifier.align(Alignment.TopEnd).padding(4.dp), onClose)
                    MiniIconButton(
                        if (playing) "❚❚" else "▶",
                        Modifier.align(Alignment.Center),
                    ) {
                        if (engine.player.isPlaying) engine.player.pause() else engine.player.play()
                        playing = !playing
                        controlsVisible = true
                    }
                    MiniIconButton("⛶", Modifier.align(Alignment.BottomEnd).padding(4.dp), onExpand)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    now.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (now.subtitle.isNotBlank()) {
                    Text(
                        now.subtitle,
                        color = EnktelTextDim,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!interactive) {
                // TV has no tap target here, so say where the controls are
                // instead of drawing buttons the remote can't reach.
                Text(
                    "▶ in menu",
                    color = EnktelBlue,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun MiniIconButton(glyph: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color.Black.copy(0.55f))
            .pointerInput(glyph) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
