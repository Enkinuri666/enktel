package tv.enktel.app

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.enktel.app.data.metadata.MetadataEnrichmentWorker

/**
 * The background enrichment walk must honour a Wi-Fi-only policy the same way
 * downloads do: a phone should never spend its cellular allowance fetching
 * artwork for the whole catalogue, while a TV stick on a fixed line stays
 * unrestricted.
 */
class EnrichNetworkPolicyTest {

    @Test fun `wifi-only pins enrichment to an unmetered connection`() {
        assertEquals(NetworkType.UNMETERED, MetadataEnrichmentWorker.networkTypeFor(wifiOnly = true))
    }

    @Test fun `unrestricted enrichment runs on any connection`() {
        assertEquals(NetworkType.CONNECTED, MetadataEnrichmentWorker.networkTypeFor(wifiOnly = false))
    }
}
