package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.enktel.app.data.repo.ScoresRepository

/**
 * Pins the JSON root key of each TheSportsDB endpoint.
 *
 * These are asserted because getting one wrong is invisible: the response
 * parses fine, the requested array is simply not there, and the feature
 * reports "nothing right now" indefinitely. Live scores shipped that way —
 * `livescore.php` returns its rows under `livescore`, the code asked for
 * `events`, and the resulting empty list was then misattributed to the free
 * API key needing an upgrade. Both the parse and the explanation were wrong.
 *
 * Verified against the live API on the free key: `livescore.php` returned 30
 * in-play fixtures under `livescore`.
 */
class ScoresEndpointTest {

    @Test fun `livescore rows are not under events`() {
        // The specific mistake this guards against.
        assertEquals("livescore", ScoresRepository.ROOT_LIVESCORE)
    }

    @Test fun `lookup endpoints use their documented roots`() {
        assertEquals("events", ScoresRepository.ROOT_EVENTS)
        assertEquals("timeline", ScoresRepository.ROOT_TIMELINE)
        assertEquals("eventstats", ScoresRepository.ROOT_EVENTSTATS)
        assertEquals("tvevent", ScoresRepository.ROOT_TVEVENT)
    }

    @Test fun `the free key is the documented shared key`() {
        // Live scores work on this key; the feature must not be gated on a
        // paid upgrade that isn't actually required.
        assertEquals("3", ScoresRepository.FREE_KEY)
    }
}
