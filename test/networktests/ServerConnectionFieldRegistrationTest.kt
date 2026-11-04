package networktests

import config.ConfigManager
import domain.ConnectionState
import korlibs.io.async.suspendTest
import kotlinx.coroutines.delay
import network.FieldRegistrationResult
import network.ServerConnectionManager
import org.junit.Assume
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Integration coverage for the real field-registration path of [ServerConnectionManager].
 *
 * The domain tests cover the same guarantees through the local testing shortcut and therefore
 * never reach `establishWebSocketConnection`/`handleFieldRegistrationFailure` — the code that
 * actually fixes issue #266. These tests drive that real path against the coupling server: the
 * server rejects the FIELD_CONNECT for an unknown field id, exactly like it rejected the
 * placeholder tile ids of the original bug. Skipped when no coupling server is reachable.
 */
class ServerConnectionFieldRegistrationTest {

    private val managers = mutableListOf<ServerConnectionManager>()

    /** Cancels the managers' connection jobs after each test. */
    @AfterTest
    fun cleanup() {
        managers.forEach { it.reset() }
    }

    /** Creates a manager instance tracked for cleanup; the global singleton stays untouched. */
    private fun newManager(): ServerConnectionManager =
        ServerConnectionManager().also { managers += it }

    /** Opens the REST-backed session, skipping the test when the server is unreachable. */
    private suspend fun connectOrSkip(manager: ServerConnectionManager) {
        Assume.assumeTrue(
            "Netzwerk ist in der Konfiguration deaktiviert",
            ConfigManager.loadNetworkConfig().active
        )
        manager.connect()
        val timeoutAt = System.currentTimeMillis() + SESSION_CONNECT_TIMEOUT_MS
        while (
            manager.connectionState == ConnectionState.CONNECTING &&
            System.currentTimeMillis() < timeoutAt
        ) {
            delay(100.milliseconds)
        }
        Assume.assumeTrue(
            "Kopplungsserver nicht erreichbar",
            manager.connectionState == ConnectionState.CONNECTED
        )
    }

    /**
     * Verifies the core of issue #266 on the real path: a field registration the server rejects
     * surfaces as a failure and does NOT drop the established session state.
     */
    @Test
    fun rejectedFieldRegistration_keepsRealSessionConnected() = suspendTest {
        val manager = newManager()
        connectOrSkip(manager)

        val result = manager.connectSelectedField("invalid-field-id")
            .get(FIELD_REGISTRATION_TIMEOUT_S, TimeUnit.SECONDS)

        assertTrue(result is FieldRegistrationResult.Failure, "server must reject the unknown field")
        assertEquals(ConnectionState.CONNECTED, manager.connectionState)
    }

    /** Verifies that without a prior session a rejected registration ends disconnected. */
    @Test
    fun rejectedFieldRegistration_withoutSessionEndsDisconnected() = suspendTest {
        Assume.assumeTrue(
            "Netzwerk ist in der Konfiguration deaktiviert",
            ConfigManager.loadNetworkConfig().active
        )
        val manager = newManager()

        val result = manager.connectSelectedField("invalid-field-id")
            .get(FIELD_REGISTRATION_TIMEOUT_S, TimeUnit.SECONDS)

        assertTrue(result is FieldRegistrationResult.Failure)
        assertEquals(ConnectionState.DISCONNECTED, manager.connectionState)
    }

    private companion object {
        /** Upper bound for the REST session check before the test is skipped. */
        const val SESSION_CONNECT_TIMEOUT_MS = 10_000L

        /** Upper bound for one registration round trip (connect timeout + registration wait). */
        const val FIELD_REGISTRATION_TIMEOUT_S = 30L
    }
}
