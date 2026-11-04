package network

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Certificate authority trusted by [trustedSslContext] in addition to the running JVM's own
 * default trust anchors.
 *
 * The coupling server's certificate (traficsim.se.uni-kassel.de) is issued by HARICA's "GEANT TLS
 * ECC 1" intermediate, rooted at "HARICA TLS ECC Root CA 2021". That root is not part of the
 * default `cacerts` trust store shipped with most JDK distributions, so a plain
 * `HttpClient.newHttpClient()` fails the TLS handshake with a PKIX path-building error even though
 * the server and its certificate chain are otherwise valid (verified independently via `curl` and
 * `openssl s_client`). Bundling the root certificate here lets every machine running this client
 * connect without requiring a local `keytool` import into its JDK installation.
 */
internal object TrustedCertificates {

    /**
     * PEM-encoded "HARICA TLS ECC Root CA 2021" self-signed root certificate (SHA-256 fingerprint
     * 3F:99:CC:47:4A:CF:CE:4D:FE:D5:87:94:66:5E:47:8D:15:47:73:9F:2E:78:0F:1B:B4:CA:9B:13:30:97:D4:01),
     * as served by traficsim.se.uni-kassel.de's TLS handshake.
     */
    private const val HARICA_TLS_ECC_ROOT_CA_2021 = """-----BEGIN CERTIFICATE-----
MIICVDCCAdugAwIBAgIQZ3SdjXfYO2rbIvT/WeK/zjAKBggqhkjOPQQDAzBsMQsw
CQYDVQQGEwJHUjE3MDUGA1UECgwuSGVsbGVuaWMgQWNhZGVtaWMgYW5kIFJlc2Vh
cmNoIEluc3RpdHV0aW9ucyBDQTEkMCIGA1UEAwwbSEFSSUNBIFRMUyBFQ0MgUm9v
dCBDQSAyMDIxMB4XDTIxMDIxOTExMDExMFoXDTQ1MDIxMzExMDEwOVowbDELMAkG
A1UEBhMCR1IxNzA1BgNVBAoMLkhlbGxlbmljIEFjYWRlbWljIGFuZCBSZXNlYXJj
aCBJbnN0aXR1dGlvbnMgQ0ExJDAiBgNVBAMMG0hBUklDQSBUTFMgRUNDIFJvb3Qg
Q0EgMjAyMTB2MBAGByqGSM49AgEGBSuBBAAiA2IABDgI/rGgltJ6rK9JOtDA4MM7
KKrxcm1lAEeIhPyaJmuqS7psBAqIXhfyVYf8MLA04jRYVxqEU+kw2anylnTDUR9Y
STHMmE5gEYd103KUkE+bECUqqHgtvpBBWJAVcqeht6NCMEAwDwYDVR0TAQH/BAUw
AwEB/zAdBgNVHQ4EFgQUyRtTgRL+BNUW0aq8mm+3oJUZbsowDgYDVR0PAQH/BAQD
AgGGMAoGCCqGSM49BAMDA2cAMGQCMBHervjcToiwqfAircJRQO9gcS3ujwLEXQNw
SaSS6sUUiHCm0w2wqsosQJz76YJumgIwK0eaB8bRwoF8yguWGEEbo/QwCZ61IygN
nxS2PFOiTAZpffpskcYqSUXm7LcT4Tps
-----END CERTIFICATE-----"""

    /** Lazily built so the (cheap but non-trivial) trust store setup runs at most once per process. */
    private val sslContext: SSLContext by lazy { buildTrustedSslContext() }

    /**
     * Returns an [SSLContext] that trusts the JVM's default certificate authorities plus
     * [HARICA_TLS_ECC_ROOT_CA_2021], so [TrafficSimRestClient] and [TrafficSimWebSocketClient] can
     * reach the coupling server regardless of whether the running JVM's own trust store has been
     * updated to include it.
     */
    fun trustedSslContext(): SSLContext = sslContext

    /**
     * Builds the merged trust store and wraps it in a fresh [SSLContext].
     */
    private fun buildTrustedSslContext(): SSLContext {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val extraCertificate = certificateFactory.generateCertificate(
            ByteArrayInputStream(HARICA_TLS_ECC_ROOT_CA_2021.toByteArray(StandardCharsets.US_ASCII))
        )

        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
        trustStore.load(null, null)

        // Seed with the JVM's own default trust anchors so unrelated HTTPS hosts keep working.
        val defaultTrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        defaultTrustManagerFactory.init(null as KeyStore?)
        val defaultTrustManager = defaultTrustManagerFactory.trustManagers.filterIsInstance<X509TrustManager>().first()
        defaultTrustManager.acceptedIssuers.forEachIndexed { index, certificate ->
            trustStore.setCertificateEntry("default-$index", certificate)
        }

        trustStore.setCertificateEntry("harica-tls-ecc-root-ca-2021", extraCertificate)

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(trustStore)

        return SSLContext.getInstance("TLSv1.3").apply {
            init(null, trustManagerFactory.trustManagers, null)
        }
    }
}
