package networktests

import java.io.ByteArrayInputStream
import java.net.http.HttpClient
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.TrustManagerFactory

/**
 * Builds HTTP clients for real coupling-server tests.
 *
 * The university test server certificate is pinned as a test trust anchor because the complete
 * chain is not available in the default JVM trust store on all developer machines. Hostname and
 * certificate validation remain enabled; production clients continue to use the JVM defaults.
 */
object RealServerTestHttpClient {
    /**
     * Creates an HTTP/WebSocket client that validates the real test server certificate.
     *
     * @return test-only [HttpClient] for REST and WebSocket integration tests.
     */
    fun create(): HttpClient {
        val sslParameters = SSLParameters().apply {
            endpointIdentificationAlgorithm = "HTTPS"
        }

        return HttpClient.newBuilder()
            .sslContext(pinnedServerSslContext())
            .sslParameters(sslParameters)
            .build()
    }

    /**
     * Creates a TLS 1.3 SSL context backed by the pinned real-server certificate.
     *
     * @return SSL context with normal server certificate validation against the test trust store.
     */
    private fun pinnedServerSslContext(): SSLContext {
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setCertificateEntry(SERVER_CERTIFICATE_ALIAS, pinnedServerCertificate())
        }
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }
        return SSLContext.getInstance("TLSv1.3").apply {
            init(null, trustManagerFactory.trustManagers, SecureRandom())
        }
    }

    /**
     * Parses the pinned server certificate used by the real integration tests.
     *
     * @return the configured X.509 server certificate.
     */
    private fun pinnedServerCertificate(): X509Certificate {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificateBytes = ByteArrayInputStream(PINNED_SERVER_CERTIFICATE.trimIndent().toByteArray())
        return certificateFactory.generateCertificate(certificateBytes) as X509Certificate
    }

    private const val SERVER_CERTIFICATE_ALIAS = "traficsim.se.uni-kassel.de"

    private const val PINNED_SERVER_CERTIFICATE = """
        -----BEGIN CERTIFICATE-----
        MIIFITCCBKegAwIBAgIQQpE5gZJQca8b/2A1iPEgTDAKBggqhkjOPQQDAzBgMQswCQYDVQQGEwJH
        UjE3MDUGA1UECgwuSGVsbGVuaWMgQWNhZGVtaWMgYW5kIFJlc2VhcmNoIEluc3RpdHV0aW9ucyBD
        QTEYMBYGA1UEAwwPR0VBTlQgVExTIEVDQyAxMB4XDTI2MDYxMTEwMDQxNFoXDTI2MTIyNzEwMDQx
        M1owYTELMAkGA1UEBhMCREUxDzANBgNVBAgMBkhlc3NlbjEcMBoGA1UECgwTVW5pdmVyc2l0YWV0
        IEthc3NlbDEjMCEGA1UEAwwadGVhbXNjYWxlLnNlLnVuaS1rYXNzZWwuZGUwWTATBgcqhkjOPQIB
        BggqhkjOPQMBBwNCAAR4jGutW9W3dyVDixi7/2vi/WXu7xlf4jGoAAQ0zGbf38A9B8uINGoagCaQ
        dBye4QV0nf8PzANxl9FErwIohGkPo4IDQDCCAzwwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBTp
        mQaNFx+r+5YaWshbXl1e7NqcjzBIBggrBgEFBQcBAQQ8MDowOAYIKwYBBQUHMAKGLGh0dHA6Ly9j
        cnQuaGFyaWNhLmdyL0hBUklDQS1HRUFOVC1UTFMtRTEuY2VyMIGBBgNVHREEejB4ghp0ZWFtc2Nh
        bGUuc2UudW5pLWthc3NlbC5kZYIed3d3LnRlYW1zY2FsZS5zZS51bmkta2Fzc2VsLmRlghp0cmFm
        aWNzaW0uc2UudW5pLWthc3NlbC5kZYIed3d3LnRyYWZpY3NpbS5zZS51bmkta2Fzc2VsLmRlMC0G
        A1UdIAQmMCQwCAYGZ4EMAQICMAgGBgQAj3oBBzAOBgwrBgEEAYHPEQEBAQIwHQYDVR0lBBYwFAYI
        KwYBBQUHAwIGCCsGAQUFBwMBMD0GA1UdHwQ2MDQwMqAwoC6GLGh0dHA6Ly9jcmwuaGFyaWNhLmdy
        L0hBUklDQS1HRUFOVC1UTFMtRTEuY3JsMB0GA1UdDgQWBBT6siMxkR2/n3Ku99R7/GFMKLhbxDAO
        BgNVHQ8BAf8EBAMCB4AwggF/BgorBgEEAdZ5AgQCBIIBbwSCAWsBaQB3AK9niDtXsE7dj6bZfvYu
        qOuBCsdxYPAkXlXWDC/nhYc6AAABnrYs6i0AAAQDAEgwRgIhANAiJX8N1yIVWDDVA1vm31U6XJlz
        RzcuWgCPyw5JnbvFAiEA/p4nTk+qmwZRmjiY04eqiQcp+Be7KHQ21h1GFQKj0R4AdQCUTkOH+uzB
        74HzGSQmqBhlAcfTXzgCAT9yZ31VNy4Z2AAAAZ62LOpIAAAEAwBGMEQCIFQrR8i3+1bOHn03GfDp
        XOjST+mgJDofKJStfbTEMU5fAiB146Tu7ijvLjcGAUjF4BM0QD5tv0HSDL/+AsXIwZFwbAB3ANgJ
        VTuUT3r/yBYZb5RPhauw+Pxeh1UmDxXRLnK7RUsUAAABnrYs6hIAAAQDAEgwRgIhAPUCkyceLPrP
        peViQrixPTfV8jIqMcVjsYZd08n0bUutAiEA6h0NFmTp7UXK2OttobkJFyYTPnrwN6cHnsvkbQ1V
        NZgwCgYIKoZIzj0EAwMDaAAwZQIwZPL20J11GywmTi0MxVtHFUMbEgnjMbECebLzky6djB8/w4I1
        6WxXsIOsHqBTNC8oAjEAyPrDU743qMzbvONzURvLY5g5kObs8poZpLi4PgbQDhwhRMy34CRB9VF6
        WMbs/jwt
        -----END CERTIFICATE-----
    """
}
