package scenestests

import domain.ConnectionState
import domain.GameState
import domain.SimulationState
import korlibs.korge.scene.sceneContainer
import korlibs.korge.tests.ViewsForTesting
import korlibs.korge.view.Stage
import korlibs.math.geom.Size
import network.ServerConnection
import scenes.LevelScene
import scenes.ServerMenuScene
import scenes.ServerMenuScene.ServerMenuConfig
import scenes.ServerMenuSceneLogic
import ui.Assets
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * Interaction-driven view tests for [ServerMenuScene]. Where [SceneRenderSmokeTest] only renders
 * the initial scene, these drive real mouse clicks through the KorGE view-test harness to exercise
 * the server-menu connect/disconnect transitions.
 *
 * Table-tile selection is covered separately in [WorldPlaceholderSceneInteractionTest], since it
 * now happens per opened world rather than in this scene.
 */
class ServerMenuSceneInteractionTest : ViewsForTesting(
    frameTime = 50.milliseconds,
    windowSize = Size(1800, 1000)
) {

    private val originalConnectDelayMs = ServerMenuConfig.CONNECT_DELAY_MS
    private val originalDisconnectDelayMs = ServerMenuConfig.DISCONNECT_DELAY_MS

    /**
     * Zeroes out the connect/disconnect delays so transitions complete on the next frame.
     */
    @BeforeTest
    fun removeConnectionDelays() {
        ServerConnection.useTestingConnectionForTesting = true
        ServerMenuConfig.CONNECT_DELAY_MS = 0L
        ServerMenuConfig.DISCONNECT_DELAY_MS = 0L
    }

    /**
     * Resets process-wide server and game state after each interaction test, and restores the
     * production connect/disconnect delays.
     */
    @AfterTest
    fun resetGlobalState() {
        ServerConnection.reset()
        ServerMenuConfig.CONNECT_DELAY_MS = originalConnectDelayMs
        ServerMenuConfig.DISCONNECT_DELAY_MS = originalDisconnectDelayMs
        ServerMenuScene.backTarget = ServerMenuScene.BackTarget.MENU_SCENE
        GameState.currentLevel = null
        GameState.simulationState = SimulationState.NO_LEVEL
        GameState.tutorialActivated = false
    }

    /**
     * Verifies that tapping "Verbinden" leads to a connected state.
     */
    @Test
    fun connectButton_movesToConnected() = viewsTest {
        ServerConnection.connectionDelayForTesting = 0
        prepareServerMenuScene()

        clickConnectionButton()

        waitForConnectionState(ConnectionState.CONNECTED, maxFrames = 300)
    }

    /** Verifies that successful connection feedback runs only after CONNECTED is confirmed. */
    @Test
    fun successfulConnect_triggersSuccessFeedbackExactlyOnce() {
        ServerConnection.reset()
        ServerConnection.useTestingConnectionForTesting = true
        ServerConnection.connectionDelayForTesting = 0
        var successFeedbackCount = 0

        ServerMenuSceneLogic.onConnectRequested(
            onRedraw = {},
            onConnected = { successFeedbackCount++ }
        )

        assertEquals(ConnectionState.CONNECTED, ServerConnection.connectionState)
        assertEquals(1, successFeedbackCount)
    }

    /**
     * Verifies that tapping "Abbrechen" during connecting returns to disconnected.
     */
    @Test
    fun cancelOverlay_duringConnecting_returnsToDisconnected() = viewsTest {
        ServerMenuConfig.CONNECT_DELAY_MS = 5_000L
        ServerConnection.connectionDelayForTesting = 5_000L
        prepareServerMenuScene()

        clickConnectionButton()
        assertEquals(ConnectionState.CONNECTING, ServerConnection.connectionState)

        clickOverlayCancelButton()

        assertEquals(ConnectionState.DISCONNECTED, ServerConnection.connectionState)
    }



    /**
     * Verifies that tapping "Abbrechen" on the connection-lost overlay stops the reconnect and
     * returns to a disconnected state (issue #210).
     */
    @Test
    fun connectionLostOverlay_cancel_returnsToDisconnected() = viewsTest {
        ServerConnection.setConnectionStateForTesting(ConnectionState.RECONNECTING)
        prepareServerMenuScene()
        assertEquals(ConnectionState.RECONNECTING, ServerConnection.connectionState)

        clickConnectionLostOverlayCancelButton()

        assertEquals(ConnectionState.DISCONNECTED, ServerConnection.connectionState)
    }

    /** Verifies that the server-menu back target defaults to the normal pause menu. */
    @Test
    fun backTarget_defaultsToMenuScene() {
        assertEquals(ServerMenuScene.BackTarget.MENU_SCENE, ServerMenuScene.backTarget)
    }

    /** Loads assets and renders [ServerMenuScene] for the test window. */
    private suspend fun Stage.prepareServerMenuScene() {
        Assets.loadAll()
        sceneContainer().changeTo { ServerMenuScene() }
    }

    /** Clicks "Verbinden" or "Trennen" at its centered production position. */
    private suspend fun clickConnectionButton() {
        val x = LevelScene.TABLE_PX_W / 2.0
        val y = LevelScene.TABLE_PX_H - ServerMenuConfig.CONNECTION_BUTTON_BOTTOM_OFFSET +
            ServerMenuConfig.ACTION_BUTTON_H / 2.0

        clickAt(x, y)
    }

    /** Clicks the overlay's "Abbrechen" button shown while connecting or disconnecting. */
    private suspend fun clickOverlayCancelButton() {
        val overlayX = LevelScene.TABLE_PX_W / 2.0 - ServerMenuConfig.OVERLAY_WIDTH / 2.0
        val overlayY = LevelScene.TABLE_PX_H / 2.0 - ServerMenuConfig.OVERLAY_HEIGHT / 2.0
        val x = overlayX + ServerMenuConfig.OVERLAY_WIDTH / 2.0
        val y = overlayY + ServerMenuConfig.OVERLAY_HEIGHT - ServerMenuConfig.ACTION_BUTTON_H / 2.0 - 30.0

        clickAt(x, y)
    }

    /** Clicks the "Abbrechen" button of the taller connection-lost overlay shown while reconnecting. */
    private suspend fun clickConnectionLostOverlayCancelButton() {
        val overlayHeight = ServerMenuConfig.CONNECTION_LOST_OVERLAY_HEIGHT
        val overlayX = LevelScene.TABLE_PX_W / 2.0 - ServerMenuConfig.OVERLAY_WIDTH / 2.0
        val overlayY = LevelScene.TABLE_PX_H / 2.0 - overlayHeight / 2.0
        val x = overlayX + ServerMenuConfig.OVERLAY_WIDTH / 2.0
        val y = overlayY + overlayHeight - ServerMenuConfig.ACTION_BUTTON_H / 2.0 - 30.0

        clickAt(x, y)
    }

    /** Moves the mouse to ([x], [y]) and performs a click, advancing a frame either side. */
    private suspend fun clickAt(x: Double, y: Double) {
        mouseMoveTo(x.toInt(), y.toInt())
        delayFrame()
        mouseClick()
        delayFrame()
    }

    /** Clicks "Verbinden" and waits until the connection reports a connected state. */
    private suspend fun connectAndWait() {
        clickConnectionButton()
        waitForConnectionState(ConnectionState.CONNECTED)
        delayFrame()
    }

    /**
     * Waits until [ServerConnection.connectionState] reaches [expected].
     */
    private suspend fun waitForConnectionState(expected: ConnectionState, maxFrames: Int = 10) {
        repeat(maxFrames) {
            if (ServerConnection.connectionState == expected) return
            delayFrame()
        }
        assertEquals(expected, ServerConnection.connectionState)
    }
}
