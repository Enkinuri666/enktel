package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.share.LanShareApi
import tv.enktel.app.data.share.LanShareServer

/**
 * The wire format the PC client reads.
 *
 * Worth testing precisely because the two ends ship separately: an Android
 * release and a Windows installer are built by different workflows and land on
 * a viewer's devices weeks apart, so a field that quietly changes shape breaks
 * a pairing that used to work and nothing in either build says why.
 */
class LanShareApiTest {

    private fun job(
        id: String = "d1",
        title: String = "Film",
        subtitle: String = "",
        status: String = "RUNNING",
    ) = LanShareApi.Job(
        id = id,
        title = title,
        subtitle = subtitle,
        status = status,
        progressPct = 42,
        sizeBytes = 1_000,
        downloadedBytes = 420,
        speedBps = 2_048,
        error = "",
    )

    // ── escaping ───────────────────────────────────────────────────────

    @Test
    fun `quotes and backslashes survive a round trip`() {
        val title = "He said \"no\" \\ then left"
        val json = LanShareApi.jobsJson(listOf(job(title = title)))
        // The escaped form, spelled out rather than re-derived with the same
        // helper the code under test uses.
        assertTrue(json, json.contains("\"title\":\"He said \\\"no\\\" \\\\ then left\""))
    }

    @Test
    fun `control characters become escapes, not raw bytes`() {
        assertEquals("\"a\\u0007b\\nc\"", LanShareApi.Json.str("a\u0007b\nc"))
    }

    @Test
    fun `the line separators JSON allows but JavaScript rejects are escaped`() {
        // U+2028 and U+2029 are legal inside a JSON string and terminate a
        // statement when the same text is read as JavaScript source. Escaping
        // them costs nothing and removes the difference.
        assertEquals("\"a\\u2028b\\u2029c\"", LanShareApi.Json.str("a\u2028b\u2029c"))
    }

    @Test
    fun `a title that is itself JSON cannot break out of its string`() {
        val hostile = "\",\"evil\":\"yes"
        val json = LanShareApi.jobsJson(listOf(job(title = hostile)))
        assertFalse("injected key escaped its string", json.contains("\"evil\":\"yes"))
    }

    // ── shapes ─────────────────────────────────────────────────────────

    @Test
    fun `files are listed by name with token and size`() {
        val files = listOf(
            LanShareServer.Shared("t2", "Beta.mkv", 20) { null },
            LanShareServer.Shared("t1", "Alpha.mp4", 10) { null },
        )
        val json = LanShareApi.filesJson(files)
        assertEquals(
            """{"version":1,"files":[""" +
                """{"token":"t1","name":"Alpha.mp4","size":10},""" +
                """{"token":"t2","name":"Beta.mkv","size":20}]}""",
            json,
        )
    }

    @Test
    fun `an empty share is an empty array, not a missing key`() {
        // A client reading `files` should never have to distinguish "no files"
        // from "field absent" — the second would be a null dereference.
        assertEquals("""{"version":1,"files":[]}""", LanShareApi.filesJson(emptyList()))
        assertEquals("""{"version":1,"downloads":[]}""", LanShareApi.jobsJson(emptyList()))
    }

    @Test
    fun `a download carries every field the client draws`() {
        val json = LanShareApi.jobsJson(listOf(job(subtitle = "Show \u00b7 S02E04")))
        listOf(
            "\"id\":\"d1\"",
            "\"title\":\"Film\"",
            "\"subtitle\":\"Show \u00b7 S02E04\"",
            "\"status\":\"RUNNING\"",
            "\"progressPct\":42",
            "\"sizeBytes\":1000",
            "\"downloadedBytes\":420",
            "\"speedBps\":2048",
            "\"error\":\"\"",
        ).forEach { assertTrue("missing $it in $json", json.contains(it)) }
    }

    @Test
    fun `pairing names the device and the app so the client can show what it joined`() {
        assertEquals(
            """{"version":1,"token":"abc","device":"Pixel 8","app":"1.66.1"}""",
            LanShareApi.pairedJson("abc", "Pixel 8", "1.66.1"),
        )
    }

    @Test
    fun `the announcement gives away nothing but a name and a port`() {
        val json = LanShareApi.announceJson("Living Room TV", 8787)
        assertEquals("""{"enktel":1,"device":"Living Room TV","port":8787}""", json)
        // Explicit, because this datagram goes to the whole subnet unasked.
        listOf("pin", "token", "file", "password").forEach {
            assertFalse("announcement leaked $it", json.contains(it, ignoreCase = true))
        }
    }

    // ── actions ────────────────────────────────────────────────────────

    @Test
    fun `actions parse case-insensitively and reject anything else`() {
        assertEquals(LanShareApi.Action.PAUSE, LanShareApi.Action.parse("pause"))
        assertEquals(LanShareApi.Action.CANCEL, LanShareApi.Action.parse(" Cancel "))
        assertEquals(LanShareApi.Action.RETRY, LanShareApi.Action.parse("RETRY"))
        assertNull(LanShareApi.Action.parse("delete"))
        assertNull(LanShareApi.Action.parse(""))
        // "resume" is a verb the queue has; "resumeall" is not, and a prefix
        // match would have accepted it.
        assertNull(LanShareApi.Action.parse("resumeall"))
    }
}
