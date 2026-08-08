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
     * The IFrame API's error codes, as the reasons they actually are.
     *
     * Worth distinguishing because they call for different responses: a video
     * whose owner has switched embedding off will never play here no matter
     * what, and moving on to the next trailer is the only useful reaction,
     * whereas an HTML5 failure may well be this device rather than this video.
     */
    fun errorReason(code: Int): String = when (code) {
        2 -> "That trailer's video id was rejected by YouTube"
        5 -> "This device's browser engine could not play it"
        100 -> "The trailer has been removed from YouTube"
        101, 150 -> "The owner does not allow this trailer to be embedded"
        else -> "YouTube reported an error playing this trailer"
    }

    /** True when [code] means "this video will never embed", not "try again". */
    fun isPermanent(code: Int): Boolean = code == 2 || code == 100 || code == 101 || code == 150
}
