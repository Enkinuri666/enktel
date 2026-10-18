package tv.enktel.app

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.player.LiveRecovery
import tv.enktel.app.player.LiveRecovery.Action

/**
 * A tester reported live TV stuttering and visibly reconnecting every few
 * minutes on `/live` Xtream channels, both `.m3u8` and raw MPEG-TS, on a
 * connection with bandwidth to spare.
 *
 * The buffering window was not the cause — BufferProfiles already keeps live
 * inside the provider's segment window. The cause was classification: a 404 on
 * a segment that had rolled off the sliding window was being treated exactly
 * like a 404 on a film, which meant "this URL is dead, try another shape of
 * it". Each of those is a fresh connection and a visible reconnect.
 *
 * These pin the distinction, because it is the kind of thing that reads as
 * obviously correct in either direction until you ask which stream it is.
 */
class LiveRecoveryTest {

    private val notFound = PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
    private val badStatus = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
    private val behind = PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
    private val malformed = PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
    private val decoder = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED

    private fun act(
        code: Int,
        live: Boolean,
        retries: Int = 0,
        hls: Boolean = false,
        candidates: Boolean = true,
    ) = LiveRecovery.action(code, live, retries, hls, candidates)

    @Test
    fun `a 404 mid-stream is a rolled-off segment on live and a missing file on VOD`() {
        // The whole bug in one assertion pair.
        assertEquals(Action.RETRY_IN_PLACE, act(notFound, live = true))
        assertEquals(Action.NEXT_CANDIDATE, act(notFound, live = false))
    }

    @Test
    fun `a bad status is a panel hiccup on live and a dead URL on VOD`() {
        assertEquals(Action.RETRY_IN_PLACE, act(badStatus, live = true))
        assertEquals(Action.NEXT_CANDIDATE, act(badStatus, live = false))
    }

    @Test
    fun `falling behind the live window never costs a retry`() {
        // A long session on a channel that drops one segment an hour used to
        // run out of attempts and be declared dead mid-programme.
        assertTrue(LiveRecovery.isFree(live = true, code = behind))
        assertEquals(Action.RETRY_IN_PLACE, act(behind, live = true, retries = 99))
    }

    @Test
    fun `nothing is free on VOD`() {
        assertFalse(LiveRecovery.isFree(live = false, code = behind))
        assertFalse(LiveRecovery.isFree(live = false, code = notFound))
    }

    @Test
    fun `live gets a bigger budget, but only for the errors live actually makes`() {
        assertEquals(LiveRecovery.LIVE_BUDGET, LiveRecovery.budget(live = true, code = notFound))
        // A decoder failure is as final on a channel as on a film. Retrying it
        // five times only delays telling the viewer.
        assertEquals(LiveRecovery.VOD_BUDGET, LiveRecovery.budget(live = true, code = decoder))
    }

    @Test
    fun `an unparseable container moves on at once, live or not`() {
        // Retrying the identical URL cannot change the answer, and with six
        // candidates two wasted attempts each is most of a minute of black.
        assertEquals(0, LiveRecovery.budget(live = true, code = malformed))
        assertEquals(0, LiveRecovery.budget(live = false, code = malformed))
        assertEquals(Action.NEXT_CANDIDATE, act(malformed, live = true))
    }

    @Test
    fun `reinterpreting the container beats retrying it`() {
        // A panel serving HLS from a URL that names a .ts fails to parse every
        // time; no amount of retrying the same reading helps.
        assertEquals(Action.HLS_RETRY, act(malformed, live = true, hls = true))
        assertEquals(Action.HLS_RETRY, act(malformed, live = false, hls = true))
    }

    @Test
    fun `a live budget that is genuinely exhausted still walks the chain`() {
        assertEquals(
            Action.NEXT_CANDIDATE,
            act(notFound, live = true, retries = LiveRecovery.LIVE_BUDGET),
        )
    }

    @Test
    fun `with nothing left to try it fails rather than looping`() {
        assertEquals(
            Action.FAIL,
            act(notFound, live = true, retries = LiveRecovery.LIVE_BUDGET, candidates = false),
        )
        assertEquals(Action.FAIL, act(malformed, live = false, candidates = false))
    }

    @Test
    fun `a dropped socket on live is transient, not fatal`() {
        // Raw MPEG-TS live is one endless response; the failure mode is the
        // socket going away, not a 404.
        assertEquals(
            Action.RETRY_IN_PLACE,
            act(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, live = true),
        )
        assertEquals(
            Action.RETRY_IN_PLACE,
            act(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, live = true),
        )
    }

    @Test
    fun `the retry delay stays inside a segment`() {
        // Media3's default backs off to five seconds. On live that is the
        // entire cushion behind the edge, so the retry lands after the player
        // has already fallen out of the window — one dropped segment becomes a
        // reconnect with nothing else having gone wrong.
        assertEquals(200L, LiveRecovery.retryDelayMs(1))
        assertEquals(400L, LiveRecovery.retryDelayMs(2))
        assertEquals(LiveRecovery.MAX_RETRY_DELAY_MS, LiveRecovery.retryDelayMs(50))
        assertTrue(LiveRecovery.MAX_RETRY_DELAY_MS <= 1_000L)
    }

    @Test
    fun `a zero or negative error count is still a real delay`() {
        // errorCount is documented as 1-based; a 0 would otherwise mean a
        // tight retry loop against a panel that just refused us.
        assertEquals(200L, LiveRecovery.retryDelayMs(0))
        assertEquals(200L, LiveRecovery.retryDelayMs(-3))
    }

    @Test
    fun `live absorbs more attempts inside the source than the default three`() {
        assertTrue(LiveRecovery.MIN_RETRY_COUNT > 3)
    }

    // ---- where a recovered stream resumes ---------------------------------
    //
    // The second complaint, and a worse one than the stutter: a film that hit
    // any recoverable fault came back at the opening titles. Recovery called
    // seekToDefaultPosition() for both kinds of stream, and the default
    // position of a file is zero. The hiccup lasted a second; the recovery
    // threw away however long the viewer had been watching.

    @Test
    fun `live rejoins at the edge rather than at a remembered position`() {
        assertEquals(
            LiveRecovery.RESUME_AT_EDGE,
            LiveRecovery.resumePositionMs(live = true, currentPositionMs = 2_400_000L),
        )
    }

    @Test
    fun `a film resumes where the viewer was, a beat earlier`() {
        val fortyMinutes = 40 * 60 * 1000L
        assertEquals(
            fortyMinutes - LiveRecovery.RESUME_REWIND_MS,
            LiveRecovery.resumePositionMs(live = false, currentPositionMs = fortyMinutes),
        )
    }

    @Test
    fun `the rewind never runs off the front of a film`() {
        // A fault in the first second would otherwise ask for a negative
        // position, which is a seek exception rather than a restart.
        assertEquals(0L, LiveRecovery.resumePositionMs(live = false, currentPositionMs = 400L))
        assertEquals(0L, LiveRecovery.resumePositionMs(live = false, currentPositionMs = 0L))
        assertEquals(0L, LiveRecovery.resumePositionMs(live = false, currentPositionMs = -1L))
    }

    @Test
    fun `the edge sentinel cannot be mistaken for a position`() {
        // The engine branches on this value, so it has to be a number no real
        // position can take.
        assertTrue(LiveRecovery.RESUME_AT_EDGE < 0L)
    }

    // ---- the frozen picture ------------------------------------------------

    @Test
    fun `a stalled film is eventually treated as dead, not merely buffering`() {
        // There was no VOD answer at all: the watchdog returned early for
        // anything that was not live, so a panel that stopped sending left a
        // frozen frame until OkHttp's 180-second read timeout.
        val vod = LiveRecovery.stallThresholdMs(live = false)
        assertTrue("a film must have a stall threshold", vod > 0)
        assertTrue("and it must beat the 180s HTTP read timeout", vod < 180_000L)
    }

    @Test
    fun `a film is given longer to recover itself than a channel`() {
        // A film has a deep buffer and a long stall may still be a slow
        // refill; re-preparing costs a visible stutter. Live has seconds of
        // cushion by construction, so a stall there is already a failure.
        assertTrue(
            LiveRecovery.stallThresholdMs(live = false) > LiveRecovery.stallThresholdMs(live = true),
        )
    }

    // ---- how patiently a load is retried -----------------------------------

    @Test
    fun `VOD retries more often than the default three`() {
        // The same overloaded panels serve the films. One 502 used to end one.
        assertTrue(LiveRecovery.VOD_MIN_RETRY_COUNT > 3)
    }

    @Test
    fun `VOD waits where live cannot`() {
        // The two policies are deliberately opposite. Live caps inside a
        // segment because waiting costs it the window; a file is not going
        // anywhere, and the player has a deep buffer to spend.
        assertTrue(LiveRecovery.VOD_MAX_RETRY_DELAY_MS > LiveRecovery.MAX_RETRY_DELAY_MS)
        assertEquals(1_000L, LiveRecovery.vodRetryDelayMs(1))
        assertEquals(2_000L, LiveRecovery.vodRetryDelayMs(2))
        assertEquals(LiveRecovery.VOD_MAX_RETRY_DELAY_MS, LiveRecovery.vodRetryDelayMs(50))
    }

    @Test
    fun `a zero error count is a real VOD delay too`() {
        assertEquals(1_000L, LiveRecovery.vodRetryDelayMs(0))
        assertEquals(1_000L, LiveRecovery.vodRetryDelayMs(-3))
    }

    @Test
    fun `the whole VOD retry schedule fits inside the stall threshold`() {
        // Otherwise the two recoveries fight: the source is still patiently
        // retrying a load while the watchdog has already decided the stream
        // is dead and re-prepared it underneath.
        val worstCase = (1..LiveRecovery.VOD_MIN_RETRY_COUNT).sumOf { LiveRecovery.vodRetryDelayMs(it) }
        assertTrue(
            "retry schedule is ${worstCase}ms against a ${LiveRecovery.VOD_STALL_MS}ms watchdog",
            worstCase < LiveRecovery.VOD_STALL_MS,
        )
    }
}
