package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import tv.enktel.app.data.TimeFormat
import java.util.Locale
import java.util.TimeZone

class TimeFormatTest {

    @Test fun `same pattern and locale reuse one formatter`() {
        val a = TimeFormat.formatter("HH:mm", Locale.UK)
        val b = TimeFormat.formatter("HH:mm", Locale.UK)
        assertSame("the cache exists to avoid re-compiling the pattern", a, b)
    }

    @Test fun `locale is part of the cache key`() {
        val uk = TimeFormat.formatter("EEEE", Locale.UK)
        val fr = TimeFormat.formatter("EEEE", Locale.FRANCE)
        assertNotSame(uk, fr)
        // Serving a cached English formatter to a French device was the bug
        // this key guards against.
        assertNotSame(uk.format(DAY), fr.format(DAY))
    }

    @Test fun `format matches an equivalent uncached formatter`() {
        val tz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val expected = java.text.SimpleDateFormat("d MMM yyyy HH:mm", Locale.UK)
                .format(java.util.Date(FIXED_MS))
            assertEquals(expected, TimeFormat.format("d MMM yyyy HH:mm", FIXED_MS, Locale.UK))
        } finally {
            TimeZone.setDefault(tz)
        }
    }

    private companion object {
        // 2026-07-16T20:30:00Z — a Thursday, so weekday names differ per locale.
        const val FIXED_MS = 1_784_233_800_000L
        val DAY = java.util.Date(FIXED_MS)
    }
}
