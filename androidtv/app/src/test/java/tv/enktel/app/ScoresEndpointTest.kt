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

    @Test fun `the whole-day broadcast endpoint uses the plural root`() {
        // eventstv.php returns `tvevents`, not `tvevent`; asking for the
        // singular left every "ON TODAY" broadcaster line blank.
        assertEquals("tvevents", ScoresRepository.ROOT_TVEVENTS_DAY)
    }

    @Test fun `the free key is the current public test key`() {
        // The old "3" key was permanently disabled by TheSportsDB (HTTP 403 on
        // every endpoint), which silently emptied the entire Sports Hub. "123"
        // is the live replacement test key and serves livescore.php. The
        // feature must not be gated on a paid upgrade that isn't required.
        assertEquals("123", ScoresRepository.FREE_KEY)
    }
}
