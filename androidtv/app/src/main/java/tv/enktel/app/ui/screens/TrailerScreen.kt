package tv.enktel.app.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.enktel.app.ui.components.FocusButton
import tv.enktel.app.ui.components.YouTubeEmbed
import tv.enktel.app.ui.theme.EnktelTextDim
import tv.enktel.app.ui.theme.EnktelTextOnArt
import tv.enktel.app.ui.theme.EnktelType

/**
 * Full-screen trailer playback, inside the app.
 *
 * ## What was wrong
 *
 * The screen showed YouTube's own grey "Unsupported" panel instead of a
 * trailer, and then sat there — no retry, no explanation, no way forward
 * except Back. Three separate faults stacked up to produce that:
 *
 *  1. **The WebView had no `WebChromeClient`.** Android will render a page
 *     containing `<video>` and refuse to play it without one; that interface
 *     is where the platform routes the callbacks HTML5 media depends on. This
 *     is the main event, and it is fixed in [YouTubeEmbed.configure] for both
 *     embeds at once, because configuring two WebViews separately is how they
 *     came to be wrong in the same way twice.
 *  2. **Nothing listened for failure.** The IFrame API reports precisely why
 *     it could not play something — a withdrawn video, an owner who disallows
 *     embedding, an engine too old — and none of those callbacks were wired,
 *     so every one of them looked identical from the outside: a grey box that
 *     never became a trailer.
 *  3. **There was only ever one candidate.** TMDB usually publishes several
 *     videos per title. When the chosen one refused to embed, that was the end
 *     of it, even though the teaser two entries down would have played.
 *
 * ## And then it broke again, differently
 *
 * A tester saw YouTube's grey "Video unavailable" panel and then this screen
 * announcing that *this device's browser engine could not play it* — on a
 * modern phone, which plainly could. Two more faults:
 *
 *  4. **The page declared an `origin` it did not have.** The document is
 *     loaded through `loadDataWithBaseURL`, whose origin is not an ordinary
 *     page origin however convincing the base URL looks. Handing YouTube an
 *     `origin` player var gave it something to check the postMessage channel
 *     against; it did not match, and the player answered with its own grey
 *     panel and error 5. Error 5 was then reported to the viewer as a fault in
 *     their device. It was not. The var is gone, which is what every working
 *     Android embed does.
 *  5. **The fall-through had nothing to fall through to.** Alternates come
 *     from TMDB, and only a *personal* TMDB key yields more than one — the
 *     site's lookup proxy answers with a single key, which is what almost
 *     everybody uses. So the recovery path built for embed-refusals never ran
 *     for almost anybody. Each upload is now tried on both embed hosts, which
 *     gives a real second attempt whether or not TMDB supplied a second video.
 *
 * ## What it does now
 *
 * Plays the first candidate. If the player reports an error, or simply never
 * starts within [START_TIMEOUT_MS], it moves to the next candidate without
 * saying anything — a viewer wants the trailer, not a bulletin about which
 * upload of it failed. Only when every candidate is exhausted does it say so,
 * in plain words, alongside a button that opens the video in whatever *can*
 * play it on this device.
 *
 * The watchdog matters as much as the error callback: a WebView too old for
 * YouTube's player does not always report an error at all. It renders the
 * unsupported notice inside the frame and goes quiet, which is indistinguishable
 * from a slow load unless something is counting.
 *
 * D-pad handling is ours, not the iframe's. `disablekb=1` keeps YouTube's key
 * bindings out of the way and playback is driven through `evaluateJavascript`,
 * because leaving focus inside a WebView on a television is how users end up
 * trapped on a page with no way back.
 */
private const val START_TIMEOUT_MS = 9_000L

/**
 * What the IFrame page reports back through.
 *
 * ### Why this is a named, public class
 *
 * It began as an anonymous `object` inside the composable, which compiles and
 * runs but fails lint: the detector resolves the argument's type, an anonymous
 * object has no name to resolve, and it concludes that nothing was annotated —
 * "None of the methods in the added interface (T) have been annotated with
 * @android.webkit.JavascriptInterface". A named class gives it something to
 * look at.
 *
 * Public, rather than `private` or `internal`, on purpose. `addJavascriptInterface`
 * reaches these methods by reflection from outside this package: a `private`
 * top-level class is package-private in the bytecode, and Kotlin mangles the
 * names of `internal` members. Either would compile happily and then fail to
 * find a callback at runtime — a release-only, silent failure of exactly the
 * kind this whole change exists to remove.
 *
 * [main] is not decoration either. WebView invokes these on its own JavaScript
 * thread, never the main one, and Compose state is not safe to write from
 * there, so everything hops to the main looper before touching it.
 */
class TrailerBridge(
    private val main: Handler,
    private val playing: () -> Unit,
    private val failed: (Int) -> Unit,
) {
    /**
     * The trailer is alive. Takes an argument it ignores, and that is the
     * point.
     *
     * The bridge resolves a call by name *and arity*: JavaScript calling
     * `onPlaying(0)` against a zero-argument method finds nothing and fails
     * silently, which is precisely what happened. Nothing ever reported that
     * playback had begun, so the nine-second watchdog — whose whole job is to
     * catch a player that never starts — fired on a player that had started,
     * moved to the next candidate, and did it again. A trailer that played for
     * eight seconds, stopped, and restarted as the next one, forever.
     *
     * It went unnoticed because until the embed itself was fixed nothing ever
     * got as far as playing, so the signal was never missed.
     */
    @JavascriptInterface
    fun onPlaying(unused: Int) {
        main.post { playing() }
    }

    @JavascriptInterface
    fun onError(code: Int) {
        main.post { failed(code) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerScreen(
    nav: NavHostController,
    videoId: String,
    title: String,
    /**
     * Further YouTube ids to fall back to, best first. The route only carries
     * one id today, so this is usually empty — the recovery path that matters
     * for a single candidate is the external hand-off below.
     */
    alternates: List<String> = emptyList(),
) {
    val context = LocalContext.current
    val candidates = remember(videoId, alternates) {
        (listOf(videoId) + alternates).filter { it.isNotBlank() }.distinct()
    }
    // Each upload is worth trying on both embed hosts before it is written
    // off. Most catalogues supply exactly one upload, so without this the
    // whole recovery path amounted to "give up".
    val plan = remember(candidates) { YouTubeEmbed.attempts(candidates) }
    var attempt by remember(plan) { mutableIntStateOf(0) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var playing by remember { mutableStateOf(true) }
    var started by remember(attempt) { mutableStateOf(false) }
    var noWebView by remember { mutableStateOf(false) }
    /** Set once every candidate has failed; carries the reason to show. */
    var deadEnd by remember(plan) { mutableStateOf<String?>(null) }

    val current = plan.getOrNull(attempt)
    val engineTooOld = remember { !YouTubeEmbed.canEmbed(context) }
    val failed = current == null || noWebView || deadEnd != null
    val controlFocus = remember { FocusRequester() }

    fun js(code: String) {
        runCatching { web?.evaluateJavascript(code, null) }
    }

    val close = { nav.popBackStack(); Unit }
    BackHandler(onBack = close)

    /**
     * Move to the next thing worth trying, or give up with a reason.
     *
     * Deliberately quiet between attempts: the viewer asked for a trailer, and
     * being told that upload #1 was geo-blocked is not information they can act
     * on while #2 is about to play.
     */
    fun advance(code: Int, reason: String) {
        val next = if (YouTubeEmbed.isTerminal(code)) {
            plan.size
        } else {
            YouTubeEmbed.nextAttempt(attempt, plan, code)
        }
        if (next < plan.size) attempt = next else deadEnd = reason
    }

    /** The Kotlin-side watchdog has no YouTube code to reason about. */
    fun advanceOnStall(reason: String) = advance(Int.MIN_VALUE, reason)

    // A player that never starts is the failure mode an error callback misses:
    // an engine too old for YouTube renders its unsupported notice inside the
    // frame and then says nothing at all, which looks exactly like a slow load.
    LaunchedEffect(attempt, current, started, deadEnd) {
        if (current == null || started || deadEnd != null) return@LaunchedEffect
        delay(START_TIMEOUT_MS)
        if (!started) {
            advanceOnStall(
                if (engineTooOld) {
                    "This device's browser engine is too old for YouTube's player"
                } else {
                    "The trailer did not start playing"
                },
            )
        }
    }

    // Stop the audio the instant the screen goes, whatever route it left by.
    DisposableEffect(Unit) {
        onDispose { runCatching { web?.loadUrl("about:blank") } }
    }

    // The lambdas the bridge calls, kept current behind a stable reference.
    //
    // The bridge itself is remembered once, for the life of the screen, because
    // it is handed to a WebView that outlives any single composition — so it
    // must not close over composition state directly. `attempt` changes as
    // candidates fail, and a callback holding the first composition's copy
    // would keep retrying the upload that had already failed.
    val reportPlaying = rememberUpdatedState<() -> Unit> { started = true }
    val reportError = rememberUpdatedState<(Int) -> Unit> { code ->
        advance(code, YouTubeEmbed.errorReason(code))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (current != null && deadEnd == null) {
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
                            YouTubeEmbed.configure(this, takesFocus = false)
                            // Constructed inline, not hoisted into a variable.
                            //
                            // Lint decides whether these methods carry
                            // @JavascriptInterface by resolving the type of
                            // this argument expression, and it could not do
                            // that through a local `val` captured across the
                            // composable and factory lambdas — it reported the
                            // bare type variable "T" whether the object was
                            // anonymous, named, or explicitly typed. A direct
                            // constructor call leaves nothing to infer.
                            //
                            // No `remember` needed either: the factory runs
                            // once per WebView, which is exactly the lifetime
                            // this object wants. The two State handles it
                            // closes over are themselves stable across
                            // recomposition, so the callbacks stay current as
                            // candidates fail.
                            addJavascriptInterface(
                                TrailerBridge(
                                    main = Handler(Looper.getMainLooper()),
                                    playing = { reportPlaying.value() },
                                    failed = { code -> reportError.value(code) },
                                ),
                                "EnktelTrailer",
                            )
                            web = this
                        }
                    }.getOrElse {
                        noWebView = true
                        android.view.View(ctx)
                    }
                },
                update = { v ->
                    (v as? WebView)?.let { w ->
                        if (w.tag != current) {
                            w.tag = current
                            // Base URL is the embed host being attempted, so the
                            // API script, the frame and the document agree about
                            // where they are.
                            w.loadDataWithBaseURL(
                                current.host,
                                trailerHtml(current.videoId, current.host),
                                "text/html", "utf-8", null,
                            )
                        }
                    }
                },
            )
        }

        if (failed) {
            val reason = when {
                noWebView -> "This device has no WebView component, so in-app YouTube playback " +
                    "isn't available here. Everything else in the app is unaffected."
                deadEnd != null -> deadEnd
                else -> "No trailer is available for this title."
            }
            Column(
                Modifier.fillMaxSize().padding(48.dp).widthIn(max = 620.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title.ifBlank { "Trailer" },
                    color = EnktelTextOnArt, style = EnktelType.headline,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(reason.orEmpty(), color = EnktelTextDim, style = EnktelType.body)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // The whole point of the dead end: hand the video to
                    // something that can play it rather than stopping here.
                    val playable = candidates.firstOrNull()
                    if (playable != null && !noWebView) {
                        FocusButton(
                            "▶  Open in YouTube",
                            accent = true,
                            modifier = Modifier.focusRequester(controlFocus),
                            onClick = { YouTubeEmbed.openExternally(context, playable) },
                        )
                        FocusButton("Back", onClick = close)
                    } else {
                        FocusButton(
                            "Back",
                            accent = true,
                            modifier = Modifier.focusRequester(controlFocus),
                            onClick = close,
                        )
                    }
                }
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
                    color = EnktelTextOnArt, style = EnktelType.title,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // Neutral wording: this screen serves highlight packages
                    // and match replays as well as trailers now.
                    if (started) "Playing in EnkTel" else "Loading…",
                    color = EnktelTextDim, style = EnktelType.caption,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FocusButton(
                        if (playing) "⏸ Pause" else "▶ Play",
                        accent = true,
                        modifier = Modifier.focusRequester(controlFocus),
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

    // Land on a control rather than nowhere. Retried, because after a nav
    // transition the first frame routinely has nothing attached to focus yet —
    // a single attempt fails silently and leaves the remote inert.
    LaunchedEffect(failed) {
        repeat(20) {
            if (runCatching { controlFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }
}

/**
 * IFrame-API page with named hooks the Kotlin side calls, and callbacks it
 * reports through.
 *
 * `onStateChange` is what proves playback actually began — `onReady` only means
 * the player object exists, which it does even when the video behind it will
 * never load. `onError` carries YouTube's own reason code straight back so the
 * screen can decide between trying the next upload and giving up honestly.
 */
private fun trailerHtml(videoId: String, host: String): String = """
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
  function report(fn, arg) {
    try { if (window.EnktelTrailer && window.EnktelTrailer[fn]) window.EnktelTrailer[fn](arg); }
    catch (e) {}
  }
  // The API script itself failing to load is a failure too — without this the
  // page just sits blank and only the Kotlin-side watchdog would catch it.
  // Reported under its own code rather than as YouTube's HTML5 error, because
  // "the script never arrived" and "YouTube refused the video" want different
  // things said and different things done about them.
  setTimeout(function () { if (!p) report('onError', ${YouTubeEmbed.ERR_NO_PLAYER}); }, 7000);
  function onYouTubeIframeAPIReady() {
    p = new YT.Player('player', {
      videoId: '$videoId',
      host: '$host',
      playerVars: {
        // controls: 0 — YouTube's own chrome is off entirely.
        //
        // This screen already draws a transport bar of its own and drives the
        // player through evaluateJavascript, so YouTube's controls were a
        // second set of buttons layered under ours, in a different visual
        // language, that a D-pad could not reach anyway. rel: 0 and
        // iv_load_policy: 3 keep the end-card grid and the annotation layer out
        // of it, so what plays is the video and nothing else.
        autoplay: 1, controls: 0, rel: 0, modestbranding: 1,
        playsinline: 1, iv_load_policy: 3, disablekb: 1, fs: 0,
        enablejsapi: 1,
        // No `origin` player var, deliberately.
        //
        // This document is loaded through loadDataWithBaseURL, so its origin
        // is not a normal page origin however convincing the base URL looks.
        // Declaring one anyway gives YouTube a value to check the postMessage
        // channel against, it does not match, and the player answers by
        // showing its own grey "Video unavailable" panel and reporting error
        // 5 — which reads as a broken device and is nothing of the sort.
        // Omitted, the API derives what it needs from the frame itself, which
        // is what every working Android embed does.
        widget_referrer: 'https://enktel.tv'
      },
      events: {
        onReady: function (e) { e.target.playVideo(); },
        // 1 is playing, 3 is buffering. Buffering counts: it proves the video
        // resolved and the player is working on it, and the watchdog exists to
        // catch a player that never becomes a trailer — not to punish a slow
        // line by moving to the next upload while this one is loading.
        onStateChange: function (e) { if (e.data === 1 || e.data === 3) report('onPlaying', 0); },
        onError: function (e) { report('onError', e.data); }
      }
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
