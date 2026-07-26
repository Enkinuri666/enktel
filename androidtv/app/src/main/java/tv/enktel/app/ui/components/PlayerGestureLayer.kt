package tv.enktel.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.enktel.app.player.PlayerGestures
import tv.enktel.app.ui.theme.EnktelBlue
import tv.enktel.app.ui.theme.EnktelOk

/**
 * Full-screen invisible gesture surface for the players. Vertical swipe on the
 * left half of the screen adjusts brightness, on the right half adjusts volume
 * (standard modern-video-player convention — YouTube, VLC, MX Player). A short
 * on-screen indicator shows the current level while the user drags.
 *
 * Wraps [content] so tap events for controls still reach whatever the caller
 * puts on top.
 */
@Composable
fun PlayerGestureLayer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val density = LocalDensity.current

    var showLevel by remember { mutableStateOf<Level?>(null) }
    var boxHeightPx by remember { mutableStateOf(1f) }
    var boxWidthPx by remember { mutableStateOf(1f) }
    var dragStartSide by remember { mutableStateOf(Side.None) }
    // Snapshot of the volume/brightness fraction at drag-start plus the
    // cumulative Y delta since then. This lets us set an *absolute* target
    // on each drag event instead of nudging by a per-event delta — the old
    // per-event nudge got truncated to zero by Android's integer-quantised
    // stream volume API, which is why the volume slider felt like it didn't
    // do anything on short drags.
    var dragStartVolume by remember { mutableStateOf(0f) }
    var dragStartBrightness by remember { mutableStateOf(0.5f) }
    var accumulatedFraction by remember { mutableStateOf(0f) }

    Box(
        modifier
            .fillMaxSize()
            // Detect vertical drags. Left half = brightness, right half = volume.
            // 1 screen height of drag ≈ 100% delta, so a small nudge shifts a small
            // amount rather than clipping to max/min instantly.
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        boxHeightPx = size.height.toFloat().coerceAtLeast(1f)
                        boxWidthPx = size.width.toFloat().coerceAtLeast(1f)
                        dragStartSide = if (offset.x < boxWidthPx / 2f) Side.Brightness else Side.Volume
                        accumulatedFraction = 0f
                        dragStartVolume = PlayerGestures.currentVolumeFraction(context)
                        dragStartBrightness = activity?.let { PlayerGestures.currentBrightness(it) } ?: 0.5f
                    },
                    onDragEnd = { dragStartSide = Side.None },
                    onDragCancel = { dragStartSide = Side.None },
                    onVerticalDrag = { _, dy ->
                        // dy positive = drag down = decrease. Invert so drag up = increase.
                        // 1.5× multiplier so a modest thumb swipe covers the full range
                        // — matches the tactile feel of MX Player / VLC on Android.
                        accumulatedFraction += -dy / boxHeightPx * 1.5f
                        when (dragStartSide) {
                            Side.Brightness -> activity?.let {
                                val target = (dragStartBrightness + accumulatedFraction).coerceIn(0.05f, 1f)
                                val next = PlayerGestures.setBrightness(it, target)
                                showLevel = Level.Brightness(next)
                            }
                            Side.Volume -> {
                                val target = (dragStartVolume + accumulatedFraction).coerceIn(0f, 1f)
                                val next = PlayerGestures.setVolumeFraction(context, target)
                                showLevel = Level.Volume(next)
                            }
                            Side.None -> {}
                        }
                    },
                )
            },
    ) {
        content()
        // Auto-hide the indicator ~800ms after the last change.
        LaunchedEffect(showLevel) {
            if (showLevel != null) {
                delay(800)
                showLevel = null
            }
        }
        showLevel?.let { lvl ->
            LevelIndicator(
                label = if (lvl is Level.Brightness) "☀ Brightness" else "🔊 Volume",
                fraction = lvl.fraction,
                accent = if (lvl is Level.Brightness) EnktelOk else EnktelBlue,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private enum class Side { None, Brightness, Volume }

private sealed class Level {
    abstract val fraction: Float
    data class Brightness(override val fraction: Float) : Level()
    data class Volume(override val fraction: Float) : Level()
}

@Composable
private fun LevelIndicator(label: String, fraction: Float, accent: Color, modifier: Modifier) {
    val pct = (fraction * 100).toInt().coerceIn(0, 100)
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Color.White.copy(0.9f), fontSize = 12.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        // Vertical bar filling from bottom to current fraction — reads like a slider
        // even without markers on the sides.
        Box(
            Modifier
                .height(90.dp)
                .width(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((90f * fraction.coerceIn(0f, 1f)).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("$pct%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
    }
}
