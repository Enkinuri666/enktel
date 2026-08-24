package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import tv.enktel.app.data.repo.ResumePolicy

/**
 * What belongs in Continue Watching.
 *
 * The rail was wrong in both directions before this existed: it collected
 * titles nobody had really started, and never let go of titles they had
 * finished.
 */
class ResumePolicyTest {

    private val film = 2 * 60 * 60 * 1000L // 2h
    private val episode = 22 * 60 * 1000L  // 22m

    @Test
    fun `a film watched to the credits is finished`() {
        // The case that made the rail useless. The old rule cleared only past
        // `duration - 30s`, and people stop when the credits roll — five to
        // eight minutes out on a feature — so a film watched all the way
        // through sat at the top of the rail for ever offering to resume at
        // 94%, and the only way to dismiss it was to play it again and sit
        // through the credits.
        assertTrue(ResumePolicy.isFinished((film * 95 / 100), film))
        assertTrue(ResumePolicy.isFinished(film - 60_000, film))
    }

    @Test
    fun `two thirds through a film is not finished`() {
        assertFalse(ResumePolicy.isFinished(film * 2 / 3, film))
    }

    @Test
    fun `the absolute tail rule is what covers short content`() {
        // On a 22-minute episode, 95% is still a minute and a half of credits
        // away, so the percentage rule alone would strand it in the rail.
        val at95pct = episode * 95 / 100
        assertTrue(ResumePolicy.isFinished(at95pct, episode))
        assertTrue(ResumePolicy.isFinished(episode - 90_000, episode))
    }

    @Test
    fun `the percentage rule is what covers long content`() {
        // Two minutes from the end of a three-hour film is deep in the
        // credits, but 95% of it is not — that is still nine minutes of film
        // left, and the viewer is not done.
        val long = 3 * 60 * 60 * 1000L
        assertFalse(ResumePolicy.isFinished(long * 94 / 100, long))
        assertTrue(ResumePolicy.isFinished(long * 96 / 100, long))
    }

    @Test
    fun `unknown duration is never finished`() {
        // A stream with no duration is usually live or still being probed.
        // Guessing "finished" there silently drops a resume point the user
        // wanted.
        assertFalse(ResumePolicy.isFinished(60 * 60 * 1000L, 0))
        assertFalse(ResumePolicy.isFinished(60 * 60 * 1000L, -1))
    }

    @Test
    fun `a glance does not create a resume point`() {
        // Opening something for twenty seconds and backing out used to write a
        // row that the details screen then refused to offer a Resume button
        // for, because that screen has always used a one-minute floor.
        assertFalse(ResumePolicy.shouldSave(20_000, film))
        assertTrue(ResumePolicy.shouldSave(ResumePolicy.MIN_RESUME_MS, film))
    }

    @Test
    fun `a finished title is never saved`() {
        assertFalse(ResumePolicy.shouldSave(film - 10_000, film))
    }

    @Test
    fun `percent is null when there is nothing to show`() {
        // Null rather than 0 so a card leaves the bar off entirely for a title
        // whose duration was never known, instead of drawing an empty bar that
        // reads as "you have watched none of this".
        assertNull(ResumePolicy.percent(0, film))
        assertNull(ResumePolicy.percent(60_000, 0))
    }

    @Test
    fun `percent is clamped to a sane range`() {
        assertEquals(50, ResumePolicy.percent(film / 2, film))
        assertEquals(100, ResumePolicy.percent(film * 2, film))
    }

    /**
     * The rail's SQL restates these thresholds, because filtering in Kotlin
     * would mean `LIMIT :n` counting rows the rail then discards — ask for
     * twenty and get four.
     *
     * Two copies of a rule drift. This reads the query out of the DAO source
     * and asserts the literals in it are the ones this object publishes, so
     * changing a constant here without changing the SQL fails the build rather
     * than silently giving the rail different thresholds from the player.
     */
    @Test
    fun `the Continue Watching SQL uses these exact thresholds`() {
        val dao = sequenceOf(
            "src/main/java/tv/enktel/app/data/db/Daos.kt",
            "app/src/main/java/tv/enktel/app/data/db/Daos.kt",
        ).map(::File).firstOrNull { it.isFile }
        requireNotNull(dao) { "Daos.kt not found from ${File(".").absolutePath}" }

        val query = dao.readText()
            .substringAfter("SELECT * FROM progress WHERE profileId = :profileId")
            .substringBefore("ORDER BY updatedAt DESC")

        assertTrue(
            "rail floor should be ${ResumePolicy.MIN_RESUME_MS}: $query",
            query.contains("positionMs >= ${ResumePolicy.MIN_RESUME_MS}"),
        )
        assertTrue(
            "rail tail should be ${ResumePolicy.TAIL_MS}: $query",
            query.contains("durationMs - ${ResumePolicy.TAIL_MS}"),
        )
        assertTrue(
            "rail cutoff should be ${ResumePolicy.FINISHED_PCT}%: $query",
            query.contains("durationMs * ${ResumePolicy.FINISHED_PCT}"),
        )
    }
}
