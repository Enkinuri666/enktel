package tv.enktel.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.enktel.app.AppGraph

/**
 * Netflix-style hover auto-trailer.
 *
 * Rest on a movie or series poster and, once the focus has actually settled,
 * that title's trailer fades up behind the UI and plays silently. Move on and
 * it fades straight back out. Everything hangs off the TMDB id the metadata
 * enrichment worker already stamped on the row, so there's no title matching
 * and no second catalogue to keep in sync.
 *
 * ### Why a WebView
 *
 * TMDB only ever hands out a YouTube video id — it hosts no media itself.
 * Pulling a progressive stream out of YouTube would mean scraping, which is
 * both against their terms and permanently one deploy away from breaking. The
 * supported way to play a YouTube video inside an app is the IFrame player,
 * which needs a WebView. It's created once and reused for every trailer
 * (loading a new video id rather than rebuilding the view), and torn down
 * whenever nothing is playing so no page keeps running in the background.
 *
 * Renders nothing at all when: the user has the feature off, no TMDB API key
 * is set, the focused item has no trailer, or the device has no usable WebView
 * (some cut-down TV firmware). Every one of those is a silent no-op — the
 * screen behind simply looks the way it did before.
 */
@Composable
fun AutoTrailerLayer(
    graph: AppGraph,
    modifier: Modifier = Modifier,
    /** Dim applied over the video so foreground text stays readable. */
    scrimAlpha: Float = 0.55f,
) {
    val enabled by graph.settings.autoTrailersEnabled.collectAsStateWithLifecycle(initialValue = true)
    val focused = LocalFocusedPoster.current
    val target = focused?.trailerTarget

    // Resolved YouTube id for the currently focused title, or null while the
    // lookup is in flight / when there is no trailer.
    var videoKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(enabled, target) {
        if (!enabled || target == null) {
            videoKey = null
            return@LaunchedEffect
        }
        videoKey = try {
            graph.trailers.trailerKey(target.tmdbId, target.isSeries)
        } catch (_: Throwable) { null }
    }

    val key = videoKey
    if (!enabled || key == null) return

    // Fade in from transparent rather than cutting: the trailer arrives while
    // the user is already looking at the poster, and a hard cut reads as a
    // glitch. Keyed on the video so each new trailer gets its own fade.
    var appeared by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { appeared = true }
    val fade by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(700),
        label = "trailerFade",
    )

    Box(modifier.fillMaxSize().alpha(fade)) {
        YouTubeSilentPlayer(videoId = key, modifier = Modifier.fillMaxSize())
        // Scrim: the trailer is set dressing behind the grid, not the subject.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = scrimAlpha * 0.8f),
                        Color.Black.copy(alpha = scrimAlpha),
                        Color.Black.copy(alpha = (scrimAlpha + 0.25f).coerceAtMost(1f)),
                    ),
                ),
            ),
        )
    }
}

/**
 * Muted, chrome-less YouTube IFrame embed.
 *
 * `mute=1` is not decoration: autoplay without a user gesture is only allowed
 * for muted playback, so an unmuted embed would simply sit on the first frame.
 * The trailer is meant to be silent anyway — it plays under a UI the user is
 * still navigating, and audio that starts on its own is the fastest way to make
 * someone turn a feature off.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeSilentPlayer(videoId: String, modifier: Modifier = Modifier) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    // AndroidView.update runs on every recomposition; reloading the page each
    // time would restart the trailer from frame one whenever anything else on
    // the screen changed. Only a genuinely new video id triggers a load.
    val loaded = remember { arrayOfNulls<String>(1) }

    DisposableEffect(Unit) {
        onDispose {
            // Leaving the screen must stop playback, not just hide it.
            webView?.let { view ->
                runCatching { view.loadUrl("about:blank") }
                runCatching { view.destroy() }
            }
            webView = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            // Some stripped-down TV firmware ships without a WebView provider;
            // constructing one throws, and the whole feature just sits out.
            runCatching {
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(AndroidColor.BLACK)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    // The trailer is scenery: it must never steal D-pad focus or
                    // swallow touches meant for the poster grid on top of it.
                    isClickable = false
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }
                    webView = this
                }
            }.getOrElse { android.view.View(ctx) }
        },
        update = { view ->
            if (loaded[0] != videoId) {
                loaded[0] = videoId
                (view as? WebView)?.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    embedHtml(videoId),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
    )
}

/**
 * Minimal IFrame-API page. `playsinline` keeps the video in the page instead of
 * handing it to the system fullscreen player, and the CSS scales the 16:9 frame
 * up until it covers the container so there are never letterbox bars behind the
 * UI (the same `object-fit: cover` trick as the poster art).
 */
private fun embedHtml(videoId: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
<style>
  html, body { margin:0; padding:0; height:100%; width:100%; background:#000; overflow:hidden; }
  #wrap { position:absolute; top:50%; left:50%; transform:translate(-50%,-50%);
          width:100vw; height:56.25vw; min-height:100vh; min-width:177.78vh; pointer-events:none; }
  iframe { width:100%; height:100%; border:0; }
</style>
</head>
<body>
<div id="wrap">
  <iframe
    src="https://www.youtube.com/embed/$videoId?autoplay=1&mute=1&controls=0&showinfo=0&rel=0&modestbranding=1&playsinline=1&loop=1&playlist=$videoId&iv_load_policy=3&disablekb=1&fs=0"
    frameborder="0"
    allow="autoplay; encrypted-media"
    allowfullscreen></iframe>
</div>
</body>
</html>
""".trimIndent()
