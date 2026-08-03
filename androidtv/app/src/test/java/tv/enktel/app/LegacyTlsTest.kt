package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.net.LegacyTls
import java.security.MessageDigest

/**
 * The two roots are pasted into LegacyTls as string literals. A single mangled
 * character would not fail to compile — it would just stop the certificate
 * parsing, and the fallback would silently vanish on exactly the old devices
 * it exists for. These fingerprints are the published SHA-256 values for
 * ISRG Root X1 and X2.
 */
class LegacyTlsTest {

    @Test fun `both bundled roots parse`() {
        assertEquals(2, LegacyTls.bundledAnchors().size)
    }

    @Test fun `bundled roots match their published fingerprints`() {
        val actual = LegacyTls.bundledAnchors().map { sha256(it.encoded) }.toSet()
        assertTrue("ISRG Root X1 fingerprint changed", actual.contains(ISRG_X1))
        assertTrue("ISRG Root X2 fingerprint changed", actual.contains(ISRG_X2))
    }

    @Test fun `bundled roots are self-signed CAs`() {
        LegacyTls.bundledAnchors().forEach {
            assertEquals(it.subjectX500Principal, it.issuerX500Principal)
            assertTrue("a trust anchor must be a CA", it.basicConstraints >= 0)
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(":") { "%02X".format(it) }

    private companion object {
        const val ISRG_X1 =
            "96:BC:EC:06:26:49:76:F3:74:60:77:9A:CF:28:C5:A7:CF:E8:A3:C0:AA:E1:1A:8F:FC:EE:05:C0:BD:DF:08:C6"
        const val ISRG_X2 =
            "69:72:9B:8E:15:A8:6E:FC:17:7A:57:AF:B7:17:1D:FC:64:AD:D2:8C:2F:CA:8C:F1:50:7E:34:45:3C:CB:14:70"
    }
}
