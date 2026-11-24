package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.enktel.app.player.ParallelRangeDataSource

class ParallelRangeDataSourceTest {
    @Test fun `total length is read from Content-Range`() {
        assertEquals(146515L, ParallelRangeDataSource.parseTotal("bytes 0-1023/146515"))
    }

    @Test fun `unknown or missing totals disable parallel mode`() {
        assertEquals(-1L, ParallelRangeDataSource.parseTotal("bytes 0-1023/*"))
        assertEquals(-1L, ParallelRangeDataSource.parseTotal(null))
        assertEquals(-1L, ParallelRangeDataSource.parseTotal("garbage"))
    }
}
