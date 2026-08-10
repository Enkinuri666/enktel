package tv.enktel.app.ui.components

import android.content.Context
import android.content.Intent
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.net.toUri

/**
 * Shared setup for every YouTube IFrame embed in the app.
 *
 * ## Why the trailers showed a grey "Unsupported" box
 *
 * Both embeds — the full-screen [tv.enktel.app.ui.screens.TrailerScreen] and
 * the hover preview in [AutoTrailerLayer] — built their own `WebView` and
 * configured it by hand, and both were missing the same things. Two of those
 * are not optional:
 *
 *  - **No `WebChromeClient`.** This is the one that actually stops video.
 *    Android's `WebView` will happily render a page containing `<video>` and
 *    then refuse to play it unless a `WebChromeClient` is attached, because
 *    that interface is where the platform routes the callbacks HTML5 media
 *    needs. It is not a nicety, and its absence produces exactly what was
 *    reported: the player chrome loads, the video does not, and YouTube shows
 *    its own error surface inside the frame.
 *  - **No `WebViewClient`.** Without one, any navigation the page attempts —
 *    including YouTube's own "watch on YouTube" links — is handed to an
 *    external browser, which on a bare Fire TV Stick means an activity-not-found
 *    and a dead-looking screen.
 *
 * The `wv` token in WebView's default User-Agent is the third factor. YouTube
 * sniffs it, and some builds decide an embedded browser is unsupported and say
 * so rather than playing. Presenting a plain Chrome UA is not a trick to get
 * around a restriction — the IFrame player is the *supported* way to embed a
 * YouTube video, and this is the same engine Chrome uses.
 *
 * ## Why a shared object rather than two configure calls
 *
 * Because that is how it broke. Two WebViews configured separately drifted into
 * being wrong in the same way twice, and a fix applied to one would not have
 * reached the other. There is one correct configuration for playing a YouTube
 * embed on this hardware and it lives here.
 */
object YouTubeEmbed {

    /**
     * A plain Chrome UA, with no `wv` token.
     *
     * Mobile rather than desktop deliberately: the mobile player is lighter,
     * degrades better on the weak GPUs in a streaming stick, and does not try
     * to lay out a desktop watch page inside a 16:9 frame.
     */
    const val USER_AGENT: String =
        "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"

    /**
     * Chromium major version behind the system WebView, or 0 when it cannot be
     * determined.
     *
     * Read out of the default User-Agent rather than through `WebViewCompat`,
     * which would mean taking an androidx.webkit dependency to learn one number.
     */
    fun webViewChromeMajor(context: Context): Int = try {
        val ua = WebSettings.getDefaultUserAgent(context)
        Regex("""Chrome/(\d+)""").find(ua)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    } catch (_: Throwable) {
        0
    }

    /**
     * Whether the IFrame player stands any chance on this device.
     *
     * Fire OS has shipped sticks whose system WebView is frozen at a Chromium
     * far older than anything YouTube still serves, and no amount of correct
     * configuration will play a video on those. Recognising that up front lets
     * the trailer screen offer a route that works instead of a frame that
     * cannot.
     *
     * The threshold is deliberately low. The exact version YouTube drops is not
     * published and moves; a conservative floor catches the genuinely hopeless
     * devices while leaving the real decision to what actually happens at
     * runtime, which the player reports through its own error callbacks.
     */
    fun canEmbed(context: Context): Boolean {
        val major = webViewChromeMajor(context)
        // 0 means "couldn't tell" — try anyway rather than refusing on a guess.
        return major == 0 || major >= 60
    }

    /**
     * Applies the configuration a YouTube embed needs.
     *
     * [takesFocus] is false for the hover preview, which is scenery behind a
     * grid the user is still navigating and must never capture the D-pad, and
     * false for the full-screen player too — that screen drives playback
     * through `evaluateJavascript` from its own overlay controls, because
     * letting focus into a WebView on a television is how someone ends up on a
     * page with no way back.
     */
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    fun configure(web: WebView, takesFocus: Boolean = false) {
        web.setBackgroundColor(android.graphics.Color.BLACK)
        web.isFocusable = takesFocus
        web.isFocusableInTouchMode = takesFocus
        // HTML5 video does not play without one of these attached. See the
        // class comment — this single line is most of the reported bug.
        web.webChromeClient = WebChromeClient()
        web.webViewClient = android.webkit.WebViewClient()
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Autoplay is only permitted without a gesture when the caller has
            // said so; the hover preview is muted, which is the other condition
            // browsers require.
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = USER_AGENT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
    }

    /**
     * Hands a video to whatever on this device can actually play it — the
     * YouTube app if it is installed, a browser otherwise.
     *
     * The last resort when the embed cannot work, and the reason the trailer
     * screen never has to leave someone looking at an error box with no way
     * forward. Returns false when nothing on the device can open it, so the
     * caller can say that plainly rather than firing an intent into a void —
     * which is the fault the original "🎬 Trailer" button had, before it was
     * replaced by the in-app player.
     */
    fun openExternally(context: Context, videoId: String): Boolean {
        if (videoId.isBlank()) return false
        val targets = listOf(
            Intent(Intent.ACTION_VIEW, "vnd.youtube:$videoId".toUri()),
            Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=$videoId".toUri()),
        )
        for (intent in targets) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) return true
        }
        return false
    }

    /**
     * The two hosts a YouTube embed can be served from.
     *
     * They are not interchangeable in practice. The privacy-enhanced host is a
     * different embed path with different referrer handling, and a video that
     * the default host refuses with a grey "Video unavailable" will often play
     * there — so it is worth a second attempt before concluding anything about
     * the video or the device.
     */
    const val HOST_DEFAULT = "https://www.youtube.com"
    const val HOST_NOCOOKIE = "https://www.youtube-nocookie.com"

    /** One thing to try: a video, served from a particular embed host. */
    data class Attempt(val videoId: String, val host: String)

    /**
     * Everything worth trying, in order, for a list of candidate uploads.
     *
     * Both hosts for the first upload before moving to the second, because a
     * host change is far more likely to help than a different upload is: the
     * alternates are ordered best-first by TMDB, and most catalogues supply
     * only one of them anyway (see TrailerRepository.trailerKeys — the site's
     * lookup proxy answers with a single key, so users without a personal TMDB
     * key have no alternates at all and the fall-through to another upload,
     * which is what the recovery path was built around, never runs for them).
     */
    fun attempts(videoIds: List<String>): List<Attempt> =
        videoIds.filter { it.isNotBlank() }.distinct().flatMap { id ->
            // Privacy-enhanced host first, on evidence: a tester reported the
            // default host answering with the grey "Video unavailable" panel
            // and the retry then playing the same trailer. One device and one
            // video is not proof, but the order costs nothing when both work
            // and saves a failed attempt when they do not.
            listOf(Attempt(id, HOST_NOCOOKIE), Attempt(id, HOST_DEFAULT))
        }

    /**
     * Where to go after the attempt at [index] failed.
     *
     * A code that condemns the *video* skips that video's remaining hosts —
     * an upload whose owner has switched embedding off is switched off on both
     * hosts, and trying the second one only makes the viewer wait. Anything
     * else advances by one, so the host retry happens.
     *
     * Returns an index past the end when there is nothing left to try.
     */
    fun nextAttempt(index: Int, attempts: List<Attempt>, code: Int): Int {
        if (index !in attempts.indices) return attempts.size
        if (!isPermanent(code)) return index + 1
        val exhausted = attempts[index].videoId
        var i = index + 1
        while (i < attempts.size && attempts[i].videoId == exhausted) i++
        return i
    }

    /**
     * Reported by the page itself when the IFrame API never came up, as
     * distinct from the API coming up and refusing.
     *
     * Outside YouTube's own numbering deliberately — it is not their error. It
     * means the script did not load, which is a blocked or broken connection,
     * and it is worth saying so rather than blaming the video or the device.
     */
    const val ERR_NO_PLAYER = -1

    /**
     * The IFrame API's error codes, as the reasons they actually are.
     *
     * Worth distinguishing because they call for different responses: a video
     * whose owner has switched embedding off will never play here no matter
     * what, and moving on is the only useful reaction, whereas a refusal to
     * embed may be the host rather than the video and is worth retrying.
     */
    fun errorReason(code: Int): String = when (code) {
        ERR_NO_PLAYER -> "YouTube's player did not load — check this device's connection"
        2 -> "That trailer's video id was rejected by YouTube"
        // Not "this device could not play it". Code 5 is the HTML5 player
        // error, and YouTube returns it for an embed it has decided not to
        // serve as readily as for a codec it cannot decode — the grey "Video
        // unavailable" panel inside the frame is the same refusal. Blaming the
        // browser engine sent people looking for a fault on their own device
        // that was not there.
        5 -> "YouTube would not play this trailer in an embedded player"
        100 -> "The trailer has been removed from YouTube"
        101, 150 -> "The owner does not allow this trailer to be embedded"
        else -> "YouTube reported an error playing this trailer"
    }

    /** True when [code] means "this video will never embed", not "try again". */
    fun isPermanent(code: Int): Boolean = code == 2 || code == 100 || code == 101 || code == 150

    /**
     * True when [code] means there is no point trying anything else at all.
     *
     * The API script failing to load is not about this video, so working
     * through the remaining candidates would be fourteen more seconds of
     * staring at black before the same dead end.
     */
    fun isTerminal(code: Int): Boolean = code == ERR_NO_PLAYER
}
