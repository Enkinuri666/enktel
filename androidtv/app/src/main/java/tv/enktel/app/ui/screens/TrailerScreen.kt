package tv.enktel.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.theme.EnktelTextDim

/**
 * Full-screen trailer playback, inside the app.
 *
 * The old "🎬 Trailer" button fired an `ACTION_VIEW` at
 * `com.google.android.youtube.tv`, with a browser URL as the fallback, both
 * wrapped in `runCatching`. On a Fire TV Stick with neither the YouTube app nor
 * a browser installed — which is the default state of a sideloaded stick, and
 * exactly the device this app is built for — both throws were swallowed and the
 * button did nothing at all. No error, no toast, no trailer. That is the whole
 * bug: pressing it produced *silence*.
 *
 * TMDB only ever hands out a YouTube video id; it hosts no media. Scraping a
 * progressive stream out of YouTube is both against their terms and one deploy
 * away from breaking, so the supported route is the IFrame player in a WebView
 * — the same mechanism [tv.enktel.app.ui.components.AutoTrailerLayer] already
 * uses for the silent hover previews, with three differences that matter here:
 * sound is on, the player is the subject rather than scenery, and the D-pad has
 * to work.
 *
 * D-pad handling is ours, not the iframe's. `disablekb=1` keeps YouTube's own
 * key bindings out of the way and playback is driven through
 * `evaluateJavascript`, because leaving focus inside a WebView on a TV is how
 * users end up trapped on a page with no way back.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerScreen(nav: NavHostController, videoId: String, title: String) {
    var web by remember { mutableStateOf<WebView?>(null) }
    var playing by remember { mutableStateOf(true) }
    // Set from AndroidView's factory when the platform has no WebView provider.
    // Derived rather than assigned during composition — writing state while
    // composing is how a screen ends up recomposing itself forever.
    var noWebView by remember { mutableStateOf(false) }
    val failed = videoId.isBlank() || noWebView
    val closeFocus = remember { FocusRequester() }

    fun js(code: String) {
        runCatching { web?.evaluateJavascript(code, null) }
    }

    val close = { nav.popBackStack(); Unit }
    BackHandler(onBack = close)

    // Stop the audio the instant the screen goes, whatever route it left by.
    // A trailer that keeps playing after you have navigated away is the same
    // class of bug as the engine-nobody-can-see the player session guards
    // against, and it is far more obvious to the user.
    DisposableEffect(Unit) {
        onDispose { runCatching { web?.loadUrl("about:blank") } }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (videoId.isNotBlank()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                onRelease = { v ->
                    (v as? WebView)?.let {
                        runCatching { it.loadUrl("about:blank") }
                        runCatching { it.destroy() }
                    }
                },
                factory = { ctx ->
                    // Some stripped-down TV firmware ships no WebView provider,
                    // and constructing one throws. Say so rather than showing a
                    // black rectangle for ever.
                    runCatching {
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            setBackgroundColor(AndroidColor.BLACK)
                            // The overlay owns the D-pad; the page must not
                            // take focus or the user cannot get back out.
                            isFocusable = false
                            isFocusableInTouchMode = false
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }
                            loadDataWithBaseURL(
                                "https://www.youtube.com",
                                trailerHtml(videoId),
                                "text/html", "utf-8", null,
                            )
                            web = this
                        }
                    }.getOrElse {
                        noWebView = true
                        android.view.View(ctx)
                    }
                },
            )
        }

        if (failed) {
            Column(
                Modifier.fillMaxSize().padding(48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Trailers need a system WebView", color = Color.White, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This device has no WebView component, so YouTube playback isn't available " +
                        "here. Everything else in the app is unaffected.",
                    color = EnktelTextDim, fontSize = 13.sp,
                )
                Spacer(Modifier.height(20.dp))
                FocusButton("Back", accent = true, onClick = close, modifier = Modifier.focusRequester(closeFocus))
            }
        } else {
            // Controls over a gradient at the bottom, the way the player OSD
            // does it — a trailer with no visible way out is the reason this
            // screen exists.
            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(0.85f)),
                        ),
                    )
                    .padding(horizontal = 40.dp, vertical = 28.dp),
            ) {
                Text(
                    title.ifBlank { "Trailer" },
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text("Official trailer", color = EnktelTextDim, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FocusButton(
                        if (playing) "⏸ Pause" else "▶ Play",
                        accent = true,
                        modifier = Modifier.focusRequester(closeFocus),
                        onClick = {
                            playing = !playing
                            js(if (playing) "enktelPlay()" else "enktelPause()")
                        },
                    )
                    FocusButton("⏪ 10s", onClick = { js("enktelSeek(-10)") })
                    FocusButton("10s ⏩", onClick = { js("enktelSeek(10)") })
                    FocusButton("↺ Restart", onClick = { js("enktelSeekTo(0)") })
                    FocusButton("✕ Close", onClick = close)
                }
            }
        }
    }

    // Land on a control rather than nowhere. Without this the first D-pad press
    // has no focused target and appears to do nothing.
    androidx.compose.runtime.LaunchedEffect(failed) {
        runCatching { closeFocus.requestFocus() }
    }
}

/**
 * IFrame-API page with named hooks the Kotlin side calls.
 *
 * `controls=1` stays on as a safety net for touch devices, but the TV path
 * never reaches them — the overlay buttons call these functions instead, which
 * is what keeps focus out of the WebView.
 */
private fun trailerHtml(videoId: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
<style>
  html, body { margin:0; padding:0; height:100%; width:100%; background:#000; overflow:hidden; }
  #player { position:absolute; top:0; left:0; width:100%; height:100%; }
</style>
</head>
<body>
<div id="player"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
  var p = null;
  function onYouTubeIframeAPIReady() {
    p = new YT.Player('player', {
      videoId: '$videoId',
      playerVars: {
        autoplay: 1, controls: 1, rel: 0, modestbranding: 1,
        playsinline: 1, iv_load_policy: 3, disablekb: 1, fs: 0
      },
      events: { onReady: function (e) { e.target.playVideo(); } }
    });
  }
  function enktelPlay()  { if (p && p.playVideo)  p.playVideo(); }
  function enktelPause() { if (p && p.pauseVideo) p.pauseVideo(); }
  function enktelSeek(d) {
    if (p && p.getCurrentTime && p.seekTo) p.seekTo(Math.max(0, p.getCurrentTime() + d), true);
  }
  function enktelSeekTo(t) { if (p && p.seekTo) p.seekTo(t, true); }
</script>
</body>
</html>
""".trimIndent()
