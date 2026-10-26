package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.repo.FixtureTeams

/**
 * Who is playing, and whether you follow them.
 *
 * Following a team was a substring test against the programme title, which is
 * wrong in both directions and silently. Every case below is a way that went
 * wrong without anybody being able to point at it: a filter that keeps
 * fixtures you did not ask for, or one that drops the fixture you did.
 */
class FixtureTeamsTest {

    // ---- who is playing ----------------------------------------------------

    @Test
    fun `the two sides of a fixture, however the guide writes it`() {
        assertEquals(listOf("Arsenal", "Chelsea"), FixtureTeams.parse("Arsenal v Chelsea"))
        assertEquals(listOf("Arsenal", "Chelsea"), FixtureTeams.parse("Arsenal vs Chelsea"))
        assertEquals(listOf("Arsenal", "Chelsea"), FixtureTeams.parse("Arsenal vs. Chelsea"))
        assertEquals(listOf("Ajax", "PSV"), FixtureTeams.parse("Ajax - PSV"))
        assertEquals(listOf("Lakers", "Celtics"), FixtureTeams.parse("Lakers @ Celtics"))
        assertEquals(listOf("Roma", "Lazio"), FixtureTeams.parse("Roma – Lazio"))
    }

    @Test
    fun `the competition in front of the fixture is not one of the teams`() {
        assertEquals(
            listOf("Arsenal", "Chelsea"),
            FixtureTeams.parse("Premier League: Arsenal v Chelsea"),
        )
        assertEquals(listOf("Ajax", "PSV"), FixtureTeams.parse("LIVE: Ajax - PSV"))
        assertEquals(listOf("Spain", "Italy"), FixtureTeams.parse("Football: Spain vs Italy"))
    }

    @Test
    fun `nor is the competition written at the end`() {
        // Stored as part of the club's name, this follows something that then
        // matches nothing ever again.
        assertEquals(
            listOf("Arsenal", "Chelsea"),
            FixtureTeams.parse("Arsenal v Chelsea: Premier League"),
        )
    }

    @Test
    fun `a competition hung off the end with a dash is not a team either`() {
        // The most ordinary title shape there is, and the one that decides
        // whether an ordinary card gets a follow control at all.
        assertEquals(
            listOf("Arsenal", "Chelsea"),
            FixtureTeams.parse("Arsenal v Chelsea — Premier League"),
        )
        assertEquals(
            listOf("Australia", "India"),
            FixtureTeams.parse("Australia v India — 3rd Test, Day 2"),
        )
        assertEquals(listOf("Ajax", "PSV"), FixtureTeams.parse("Live — Ajax vs PSV"))
    }

    @Test
    fun `a hyphen inside a name is part of the name`() {
        // Split on it and you have invented a team called Étienne.
        assertEquals(
            listOf("Saint-Étienne", "Lyon"),
            FixtureTeams.parse("Saint-Étienne v Lyon"),
        )
    }

    @Test
    fun `trailing feed decoration is not part of a club`() {
        assertEquals(listOf("Ajax", "PSV"), FixtureTeams.parse("Ajax v PSV (Live)"))
        assertEquals(listOf("Ajax", "PSV"), FixtureTeams.parse("Ajax v PSV [1080p]"))
    }

    @Test
    fun `a programme that is not a fixture offers nothing to follow`() {
        // Empty rather than a guess: an offer to follow something that is not
        // a team gets stored, and then silently matches nothing for ever.
        assertTrue(FixtureTeams.parse("Matchday Live").isEmpty())
        assertTrue(FixtureTeams.parse("NFL RedZone").isEmpty())
        assertTrue(FixtureTeams.parse("").isEmpty())
        assertTrue(FixtureTeams.parse("Live").isEmpty())
        // Three sides is not a fixture.
        assertTrue(FixtureTeams.parse("Arsenal v Chelsea v Spurs").isEmpty())
        // A scoreline is not a team.
        assertTrue(FixtureTeams.parse("2 - 1").isEmpty())
        // Neither is a state.
        assertTrue(FixtureTeams.parse("Live - Highlights").isEmpty())
    }

    // ---- whether you follow them -------------------------------------------

    @Test
    fun `Roma is not Romania`() {
        // The complaint this class exists for. A substring test put every
        // Romanian fixture in the "my teams" filter of somebody following
        // Roma, which reads as the filter being broken.
        assertTrue(FixtureTeams.mentions("Roma v Lazio", "roma"))
        assertFalse(FixtureTeams.mentions("Romania v Serbia", "roma"))
        assertFalse(FixtureTeams.mentions("RO: ROMANIA SPORT 1", "roma"))
    }

    @Test
    fun `and Milan is not Milano, and Ajax is not Ajaxx`() {
        assertFalse(FixtureTeams.mentions("Milano Cortina Olympics", "milan"))
        assertFalse(FixtureTeams.mentions("Ajaxx v PSV", "ajax"))
        assertTrue(FixtureTeams.mentions("AC Milan v Inter", "milan"))
    }

    @Test
    fun `a club suffix the provider left off still matches`() {
        // The stored spelling comes from the sports database; the line comes
        // from the provider, and they do not agree about "FC".
        assertTrue(FixtureTeams.mentions("Arsenal v Chelsea", "arsenal fc"))
        assertTrue(FixtureTeams.mentions("Arsenal F.C. v Chelsea", "arsenal"))
        assertTrue(FixtureTeams.mentions("ARSENAL  v  CHELSEA", "Arsenal FC"))
    }

    @Test
    fun `a two-word club is not matched by one of its words`() {
        // Dropping "United" as decoration is the tempting mistake, and it
        // makes following Manchester United match Manchester City — a worse
        // failure than the one being fixed.
        assertTrue(FixtureTeams.mentions("Manchester United v Liverpool", "manchester united"))
        assertFalse(FixtureTeams.mentions("Manchester City v Liverpool", "manchester united"))
        assertFalse(FixtureTeams.mentions("Leeds United v Hull City", "manchester united"))
    }

    @Test
    fun `a channel name carries the fixture when the guide does not`() {
        // Pay-per-view and event lines put the match in the channel name and
        // have no guide entry worth the name.
        assertTrue(FixtureTeams.mentions("UK: PPV 04 | Arsenal vs Chelsea", "arsenal"))
    }

    @Test
    fun `nothing followed matches nothing`() {
        assertFalse(FixtureTeams.mentions("Arsenal v Chelsea", ""))
        assertFalse(FixtureTeams.mentions("", "arsenal"))
        assertFalse(FixtureTeams.mentions("Arsenal v Chelsea", "   "))
    }
}
