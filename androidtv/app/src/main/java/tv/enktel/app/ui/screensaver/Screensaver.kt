package tv.enktel.app.ui.screensaver

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.enktel.app.AppGraph
import tv.enktel.app.R
import tv.enktel.app.data.TimeFormat

/** Broadcasts "user just did something" to the ambient screensaver watcher. */
class IdleTicker {
    var lastActionMs by mutableLongStateOf(System.currentTimeMillis())
    fun poke() { lastActionMs = System.currentTimeMillis() }
}
val LocalIdleTicker = compositionLocalOf { IdleTicker() }

/**
 * Wraps the app in an idle-detecting envelope. When the user has been idle for the configured
 * number of minutes AND there is nothing playing, we fade in a full-screen screensaver with
 * rotating backdrops from the catalogue. Any keypress or touch dismisses it.
 */
@Composable
fun ScreensaverHost(graph: AppGraph, isPlaying: () -> Boolean, content: @Composable () -> Unit) {
    val ticker = remember { IdleTicker() }
    val idleMin by graph.settings.screensaverMin.collectAsStateWithLifecycle(initialValue = 5)
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(ticker.lastActionMs, idleMin, isPlaying()) {
        visible = false
        if (idleMin <= 0) return@LaunchedEffect
        while (true) {
            val remaining = idleMin * 60_000L - (System.currentTimeMillis() - ticker.lastActionMs)
            if (remaining <= 0) { if (!isPlaying()) visible = true; return@LaunchedEffect }
            delay(remaining + 200)
        }
    }

    CompositionLocalProvider(LocalIdleTicker provides ticker) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { ticker.poke() } }
                .onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown) ticker.poke()
                    false
                },
        ) {
            content()
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500)),
                exit = fadeOut(tween(300)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Screensaver(graph, onDismiss = { visible = false; ticker.poke() })
            }
        }
    }
}

@Composable
private fun Screensaver(graph: AppGraph, onDismiss: () -> Unit) {
    val profileId by graph.settings.activeProfileId.collectAsStateWithLifecycle(initialValue = 0L)
    val movies by graph.content.movies(profileId).collectAsStateWithLifecycle(initialValue = emptyList())
    val backdrops = remember(movies) { movies.filter { it.poster.isNotBlank() }.shuffled().take(20) }
    var idx by remember { mutableIntStateOf(0) }
    LaunchedEffect(backdrops) {
        if (backdrops.isEmpty()) return@LaunchedEffect
        while (true) { delay(9000); idx = (idx + 1) % backdrops.size }
    }
    var now by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            now = TimeFormat.now("HH:mm")
            delay(15_000)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .onPreviewKeyEvent { onDismiss(); true },
    ) {
        if (backdrops.isNotEmpty()) {
            AsyncImage(
                model = backdrops[idx].poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.55f,
            )
        }
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Black.copy(0.3f), Color.Black.copy(0.9f)))
        ))
        Column(
            Modifier.align(Alignment.BottomStart).padding(64.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(R.drawable.logo_full), contentDescription = null, modifier = Modifier.width(260.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(now, color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Black)
            Text(
                TimeFormat.now("EEEE d MMMM yyyy"),
                color = Color.White.copy(0.7f), fontSize = 16.sp,
            )
            Spacer(Modifier.height(8.dp))
            if (backdrops.isNotEmpty()) {
                Text("Now showing: ${backdrops[idx].name}", color = Color.White.copy(0.6f), fontSize = 13.sp)
            }
            Text("Press any key to wake", color = Color.White.copy(0.4f), fontSize = 11.sp)
        }
    }
}
