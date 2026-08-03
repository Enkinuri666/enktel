package tv.enktel.app.data.net

import android.annotation.SuppressLint
import android.os.Build
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Teaches pre-7.1.1 Android about Let's Encrypt.
 *
 * ### The problem
 *
 * ISRG Root X1 — the root behind the majority of the certificates IPTV panels
 * present — was added to Android's system trust store in **7.1.1 (API 25)**.
 * This app supports API 21, which includes the Fire TV Stick 2nd gen it
 * explicitly targets. On those devices an HTTPS panel with a Let's Encrypt
 * certificate fails the handshake outright:
 *
 *     SSLHandshakeException: Trust anchor for certification path not found
 *
 * and there is nothing the panel, the URL shapes, or the cipher list can do
 * about it. Cross-signing by DST Root CA X3 used to paper over this; that
 * expired in 2021.
 *
 * ### What this does, and what it deliberately doesn't
 *
 * It **adds** two well-known public roots to the set of trust anchors, and
 * nothing else:
 *
 *  - ISRG Root X1 — SHA-256 `96:BC:EC:06:…:08:C6`
 *  - ISRG Root X2 — SHA-256 `69:72:9B:8E:…:14:70`
 *
 * (Both were checked against their published fingerprints before being
 * embedded, not merely downloaded and trusted.)
 *
 * The system trust manager is consulted **first** and only falls through to
 * these on failure, so nothing the platform already trusts changes, and
 * nothing else becomes trusted. Hostname verification is untouched. This is
 * not certificate pinning and it is emphatically not "trust everything" — a
 * self-signed or expired certificate still fails exactly as before.
 *
 * Applied only below API 25. Newer devices ship these roots already, so there
 * is no reason to take over their SSL stack.
 */
object LegacyTls {

    fun install(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        // 7.1.1 and up already trust ISRG. Leave the platform's own SSL
        // machinery alone rather than replacing it for no gain.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) return builder
        return try {
            val system = platformTrustManager() ?: return builder
            val bundled = bundledTrustManager() ?: return builder
            val composite = FallbackTrustManager(system, bundled)
            val ssl = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(composite), null)
            }
            builder.sslSocketFactory(ssl.socketFactory, composite)
        } catch (_: Throwable) {
            // Any failure here leaves the default stack in place. A device that
            // can't build this is no worse off than before.
            builder
        }
    }

    private fun platformTrustManager(): X509TrustManager? = try {
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
    } catch (_: Throwable) { null }

    /**
     * The bundled roots, parsed. Exposed (internal) so LegacyTlsTest can assert
     * their SHA-256 fingerprints — a typo in the embedded PEM below would
     * otherwise only show up as a silent loss of the fallback on old devices.
     */
    internal fun bundledAnchors(): List<X509Certificate> {
        val factory = CertificateFactory.getInstance("X.509")
        return listOf(ISRG_ROOT_X1, ISRG_ROOT_X2).mapNotNull { pem ->
            runCatching {
                pem.byteInputStream().use { factory.generateCertificate(it) as X509Certificate }
            }.getOrNull()
        }
    }

    private fun bundledTrustManager(): X509TrustManager? {
      return try {
        val factory = CertificateFactory.getInstance("X.509")
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        var count = 0
        listOf("isrg_root_x1" to ISRG_ROOT_X1, "isrg_root_x2" to ISRG_ROOT_X2).forEach { entry ->
            runCatching {
                entry.second.byteInputStream().use { input ->
                    val cert = factory.generateCertificate(input) as X509Certificate
                    store.setCertificateEntry(entry.first, cert)
                    count++
                }
            }
        }
        if (count == 0) null
        else TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(store) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
      } catch (_: Throwable) { null }
    }

    /**
     * Embedded rather than kept in `res/raw`, so the bytes that get trusted are
     * visible in the same file as the reasoning about why. Both were verified
     * against their published SHA-256 fingerprints before being pasted here.
     */
    private const val ISRG_ROOT_X1 =
        "-----BEGIN CERTIFICATE-----\n" +
        "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\n" +
        "-----END CERTIFICATE-----\n"

    private const val ISRG_ROOT_X2 =
        "-----BEGIN CERTIFICATE-----\n" +
        "MIICGzCCAaGgAwIBAgIQQdKd0XLq7qeAwSxs6S+HUjAKBggqhkjOPQQDAzBPMQsw\n" +
        "CQYDVQQGEwJVUzEpMCcGA1UEChMgSW50ZXJuZXQgU2VjdXJpdHkgUmVzZWFyY2gg\n" +
        "R3JvdXAxFTATBgNVBAMTDElTUkcgUm9vdCBYMjAeFw0yMDA5MDQwMDAwMDBaFw00\n" +
        "MDA5MTcxNjAwMDBaME8xCzAJBgNVBAYTAlVTMSkwJwYDVQQKEyBJbnRlcm5ldCBT\n" +
        "ZWN1cml0eSBSZXNlYXJjaCBHcm91cDEVMBMGA1UEAxMMSVNSRyBSb290IFgyMHYw\n" +
        "EAYHKoZIzj0CAQYFK4EEACIDYgAEzZvVn4CDCuwJSvMWSj5cz3es3mcFDR0HttwW\n" +
        "+1qLFNvicWDEukWVEYmO6gbf9yoWHKS5xcUy4APgHoIYOIvXRdgKam7mAHf7AlF9\n" +
        "ItgKbppbd9/w+kHsOdx1ymgHDB/qo0IwQDAOBgNVHQ8BAf8EBAMCAQYwDwYDVR0T\n" +
        "AQH/BAUwAwEB/zAdBgNVHQ4EFgQUfEKWrt5LSDv6kviejM9ti6lyN5UwCgYIKoZI\n" +
        "zj0EAwMDaAAwZQIwe3lORlCEwkSHRhtFcP9Ymd70/aTSVaYgLXTWNLxBo1BfASdW\n" +
        "tL4ndQavEi51mI38AjEAi/V3bNTIZargCyzuFJ0nN6T5U6VR5CmD1/iQMVtCnwr1\n" +
        "/q4AaOeMSQ+2b1tbFfLn\n" +
        "-----END CERTIFICATE-----\n"

    /**
     * System anchors first; the bundled roots only get a say when the platform
     * has already refused. That ordering is the whole safety argument — this
     * can only ever widen trust to the two roots above, never narrow or
     * redirect what the platform would have accepted on its own.
     */
    @SuppressLint("CustomX509TrustManager")
    private class FallbackTrustManager(
        private val system: X509TrustManager,
        private val bundled: X509TrustManager,
    ) : X509TrustManager {

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            try {
                system.checkServerTrusted(chain, authType)
            } catch (primary: CertificateException) {
                try {
                    bundled.checkServerTrusted(chain, authType)
                } catch (_: CertificateException) {
                    // Report the platform's complaint, not ours — it is the
                    // more informative of the two and the one people can look up.
                    throw primary
                }
            }
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            system.checkClientTrusted(chain, authType)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            system.acceptedIssuers + bundled.acceptedIssuers
    }
}
