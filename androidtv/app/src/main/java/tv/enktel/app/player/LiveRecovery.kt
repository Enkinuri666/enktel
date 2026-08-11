package tv.enktel.app.player

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * What a playback error means when the stream is live.
 *
 * ### The bug this exists to fix
 *
 * Live playback stuttered and visibly reconnected every few minutes on `/live`
 * Xtream channels, both `.m3u8` and raw MPEG-TS, on connections with bandwidth
 * to spare. The buffering window was not the problem — see [BufferProfiles],
 * which already keeps live inside the provider's segment window and holds a
 * six-second cushion behind the edge. The problem was that a routine live
 * hiccup was being classified as a broken channel.
 *
 * A live HLS server publishes a sliding window and deletes segments off the
 * back of it. Ask for one a moment too late and the answer is **404**. That is
 * not a statement that the channel is gone; it is a statement that you asked
 * for yesterday's segment, and the cure is to re-join at the live edge, which
 * takes a fraction of a second.
 *
 * Two classifications got that wrong, and they compounded:
 *
 *  1. `ERROR_CODE_IO_FILE_NOT_FOUND` and `ERROR_CODE_IO_BAD_HTTP_STATUS` sat in
 *     the engine's "deterministic" set — errors where retrying the same URL
 *     cannot change the answer. For a film that is exactly right: a 404 on a
 *     film means the film is not there. For a live segment it is exactly wrong,
 *     and it made the engine skip the cheap in-place retry and jump straight to
 *     rewriting the URL into another shape. Every one of those is a fresh
 *     connection, a fresh handshake and a visible "Reconnecting".
 *  2. `ERROR_CODE_BEHIND_LIVE_WINDOW` — the player noticing it has drifted off
 *     the back of the window — was treated as an ordinary failure and charged
 *     to the same two-attempt budget as a dead host. Falling behind says
 *     nothing about the URL. Recovering costs one seek, and it must not push a
 *     working channel one step closer to being abandoned.
 *
 * Between them, a panel that dropped one segment every few minutes spent those
 * minutes walking its fallback chain instead of playing.
 *
 * ### Why the policy below matters more than any of it
 *
 * The best-handled player error is the one that never happens. Media3 retries a
 * failed load inside the media source before giving up and surfacing anything,
 * and the default schedule — three attempts, backing off to five seconds —
 * was written with VOD in mind. On live, a five-second wait *is* the failure:
 * segments are a few seconds long, so by the time the retry goes out the
 * player has fallen off the window and the next thing to arrive is
 * `BEHIND_LIVE_WINDOW`. The default schedule turns one dropped segment into a
 * reconnect all by itself.
 *
 * [LiveLoadErrorPolicy] retries more often and much sooner, which is the right
 * shape for a source whose next segment is already sitting on the server.
 *
 * Pure decision logic, so the classification can be tested rather than
 * observed on a Fire Stick at half past eleven at night.
 */
@UnstableApi
object LiveRecovery {

    /** What the engine should do about an error. */
    enum class Action {
        /** Re-join at the live edge, or re-prepare in place. Cheap, invisible. */
        RETRY_IN_PLACE,

        /** Same URL, read as HLS instead. See PlayerEngine.Routing.asHlsRetry. */
        HLS_RETRY,

        /** This URL shape is not working; try the next one StreamUrlResolver built. */
        NEXT_CANDIDATE,

        /** Nothing left to try. */
        FAIL,
    }

    /**
     * Errors that are routine on a live stream and permanent on a file.
     *
     * A 404 mid-stream means the segment rolled off the window. A 5xx means the
     * panel hiccuped — IPTV resellers are frequently a thin proxy in front of
     * something overloaded, and a single bad response is not a dead channel.
     * `BEHIND_LIVE_WINDOW` is the player itself reporting it drifted.
     *
     * On VOD every one of these keeps its old meaning, which is why this set is
     * consulted only when live.
     */
    val LIVE_TRANSIENT = setOf(
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    )

    /**
     * Errors where the same URL will produce the same answer however often it
     * is asked, so the fallback chain should move on immediately.
     *
     * The IO codes that used to live here have moved to [LIVE_TRANSIENT]; they
     * are still deterministic for VOD, which [budget] expresses by giving them
     * no in-place attempts when the stream is not live.
     */
    val DETERMINISTIC = setOf(
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    )

    /**
     * Errors that never count against the retry budget.
     *
     * Drifting off the back of the window is a normal event on a long live
     * session — a Wi-Fi dip, a moment of CPU contention, a panel that stalls
     * for two seconds. Charging it to the budget meant a channel that dropped
     * one segment an hour eventually ran out of attempts and was declared dead
     * mid-programme, which is the "it just stops after a while" complaint.
     */
    val FREE = setOf(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)

    /** In-place attempts before walking the candidate chain, on VOD. */
    const val VOD_BUDGET = 2

    /**
     * In-place attempts before walking the candidate chain, on live.
     *
     * Higher than VOD because on live the in-place retry is the *correct*
     * recovery rather than a hopeful one, and because rewriting the URL cannot
     * fix a segment that rolled off the window — it only costs a reconnect.
     */
    const val LIVE_BUDGET = 5

    /**
     * The two IO codes that mean opposite things on a file and on a channel.
     *
     * On VOD they are final: the panel does not have this film, and asking
     * again wastes six seconds per candidate. They keep that meaning here, and
     * only here, so that [LIVE_TRANSIENT] can give them the other one.
     */
    private val VOD_FINAL_IO = setOf(
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    )

    /**
     * How many in-place attempts [code] gets before the chain is walked.
     *
     * The generous live budget is spent only on the errors live actually
     * produces. A decoder or DRM failure on a live channel is as final as it is
     * on a film, and retrying it five times only delays the message.
     */
    fun budget(live: Boolean, code: Int): Int = when {
        code in DETERMINISTIC -> 0
        live && code in LIVE_TRANSIENT -> LIVE_BUDGET
        !live && code in VOD_FINAL_IO -> 0
        else -> VOD_BUDGET
    }

    /** True when this error should not be charged to the budget at all. */
    fun isFree(live: Boolean, code: Int): Boolean = live && code in FREE

    /**
     * What to do about [code].
     *
     * @param retries how many in-place attempts have already been spent
     * @param hasHlsRetry the URL could be reinterpreted as an HLS playlist
     * @param hasCandidates StreamUrlResolver has other URL shapes left
     */
    fun action(
        code: Int,
        live: Boolean,
        retries: Int,
        hasHlsRetry: Boolean,
        hasCandidates: Boolean,
    ): Action = when {
        // Reinterpreting the container beats everything: a panel serving HLS
        // from a URL that names a .ts fails to parse every time, and no amount
        // of retrying the same reading will help.
        hasHlsRetry -> Action.HLS_RETRY
        isFree(live, code) -> Action.RETRY_IN_PLACE
        retries < budget(live, code) -> Action.RETRY_IN_PLACE
        hasCandidates -> Action.NEXT_CANDIDATE
        else -> Action.FAIL
    }

    /**
     * Retry a failed load quickly and often, because live cannot wait.
     *
     * Media3's default backs off to five seconds after three attempts. With
     * segments a few seconds long and a six-second cushion behind the edge,
     * that schedule spends the whole cushion waiting and then reports the
     * player has fallen behind — converting one dropped segment into a
     * reconnect without anything else having gone wrong.
     *
     * Delegates to the default for the *decision* of whether an error is
     * retryable at all, so genuinely unparseable responses still fail fast:
     * `C.TIME_UNSET` from the parent means "do not retry", and shortening a
     * delay that was never going to be used would only hide the real fault.
     */
    class LiveLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {

        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val base = super.getRetryDelayMsFor(loadErrorInfo)
            if (base == C.TIME_UNSET) return C.TIME_UNSET
            return retryDelayMs(loadErrorInfo.errorCount)
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int = MIN_RETRY_COUNT
    }

    /**
     * Attempts allowed before a load is surfaced as a player error.
     *
     * Six rather than the default three. Each is cheap — a segment request on a
     * connection that is already open — and the failure mode being absorbed is
     * a panel that drops one request in a few hundred.
     */
    const val MIN_RETRY_COUNT = 6

    /** The longest a live retry ever waits. A whole segment, near enough. */
    const val MAX_RETRY_DELAY_MS = 1_000L

    /** Growth per attempt: 200ms, 400ms, 600ms … capped. */
    const val RETRY_STEP_MS = 200L

    /**
     * Delay before retry number [errorCount] (1-based).
     *
     * Rises so a panel that is genuinely struggling is not hammered, but caps
     * inside a segment so the player never spends its cushion waiting.
     */
    fun retryDelayMs(errorCount: Int): Long =
        (errorCount.coerceAtLeast(1) * RETRY_STEP_MS).coerceAtMost(MAX_RETRY_DELAY_MS)
}
