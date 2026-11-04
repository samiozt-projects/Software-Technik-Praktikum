package scenes

import audio.onClickWithSound
import domain.ConnectionState
import network.ServerConnection
import korlibs.image.color.Colors
import korlibs.korge.scene.Scene
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.align.centerXOn
import korlibs.korge.view.container
import korlibs.korge.view.text
import kotlinx.coroutines.launch
import scenes.LevelScene.Companion.TABLE_PX_H
import ui.Assets.bitcountSingle
import ui.ControlButtons
import ui.ServerMenuSceneUI

/**
 * Displays the multi-table mode menu reachable through the globe button in the pause menu.
 *
 * This is a placeholder UI for the upcoming multi-table feature: it lets the player connect to
 * a server address and continue to the world selection menu once connected. All server
 * interaction goes through [ServerConnection], the real server integration
 * replaces it with the real server integration without changing the layout built here.
 *
 * Table-tile selection happens per opened world in [WorldPlaceholderScene], not here: this scene
 * only establishes the connection.
 */
class ServerMenuScene : Scene() {

    /** Back-button destinations from the server menu. */
    enum class BackTarget {
        /** Return to the normal pause menu when no server connection is active. */
        MENU_SCENE,

        /** Return to the multi-table pause menu when opened from the online pause menu. */
        MULTI_TABLE_MENU_SCENE
    }

    /**
     * Stores sizing and layout constants for the multi-table mode scene.
     */
    object ServerMenuConfig {
        /** Left inset for the textual info column (server address, table id, status). */
        const val INFO_COLUMN_X = 80.0

        /** Top offset for the first line of the info column. */
        const val INFO_COLUMN_Y = 170.0

        /** Vertical gap between lines in the info column. */
        const val INFO_LINE_GAP = 40.0

        /** Font size used for info column text. */
        const val INFO_TEXT_SIZE = 28.0

        /** Font size used for the scene title. */
        const val TITLE_TEXT_SIZE = 56.0

        /** Top offset for the scene title. */
        const val TITLE_Y = 60.0

        /** Width of the bordered connect/disconnect button. */
        const val CONNECTION_BUTTON_W = 320.0

        /** Height of the bordered connect/disconnect button and other bordered text buttons. */
        const val ACTION_BUTTON_H = 86.0

        /** Bottom inset for the connect/disconnect button. */
        const val CONNECTION_BUTTON_BOTTOM_OFFSET = 150.0

        /** Font size used for bordered text button labels. */
        const val ACTION_BUTTON_TEXT_SIZE = 32.0

        /** Stroke thickness shared by bordered boxes and buttons in this scene. */
        const val STROKE_THICKNESS = 2.0

        /** Width of the modal overlay shown while connecting or disconnecting. */
        const val OVERLAY_WIDTH = 780.0

        /** Height of the modal overlay shown while connecting or disconnecting. */
        const val OVERLAY_HEIGHT = 220.0

        /** Height of the taller "connection lost" overlay, which stacks an error line and a hint. */
        const val CONNECTION_LOST_OVERLAY_HEIGHT = 280.0

        /** Top offset of the red "connection lost" headline inside its overlay. */
        const val CONNECTION_LOST_TITLE_Y = 56.0

        /** Font size of the red "connection lost" headline. */
        const val CONNECTION_LOST_TITLE_SIZE = 40.0

        /** Top offset of the grey "reconnect running" hint below the headline. */
        const val CONNECTION_LOST_HINT_Y = 118.0

        /** Hint shown below the "connection lost" headline while the automatic reconnect runs. */
        const val RECONNECT_HINT_TEXT = "Automatischer Reconnect läuft..."

        /** Colour of the red "connection lost" headline. */
        val CONNECTION_LOST_COLOR = Colors["#CC3333"]

        /** Colour of the grey "reconnect running" hint. */
        val RECONNECT_HINT_COLOR = Colors["#808080"]

        /** Width of the "World Selection" button shown once a world has been opened before. */
        const val WORLD_SELECT_BUTTON_W = 320.0

        /** Height of the "World Selection" button. */
        const val WORLD_SELECT_BUTTON_H = 90.0

        /** Width of the prominent "World Selection" button shown right after the first connect. */
        const val WORLD_SELECT_BUTTON_PROMINENT_W = 520.0

        /** Height of the prominent "World Selection" button. */
        const val WORLD_SELECT_BUTTON_PROMINENT_H = 140.0

        /** Font size for the prominent "World Selection" button label. */
        const val WORLD_SELECT_BUTTON_PROMINENT_TEXT_SIZE = 36.0

        /** Vertical gap between stacked left-side navigation buttons. */
        const val NAV_BUTTON_GAP = 16.0

        /** Simulated delay for the connect transition. Overridable for tests. */
        var CONNECT_DELAY_MS = 900L

        /** Simulated delay for the placeholder disconnect transition. Overridable for tests. */
        var DISCONNECT_DELAY_MS = 700L

        /** Fill color used for the "World Selection" button. */
        val WORLD_SELECT_BUTTON_COLOR = Colors["#CFE3FA"]

        /** Font size used for the small world-name label above the "World Selection" button. */
        const val WORLD_NAME_LABEL_TEXT_SIZE = 20.0

        /** Width of the bordered world-name label above the "World Selection" button. */
        const val WORLD_NAME_LABEL_W = 520.0

        /** Height of the bordered world-name label above the "World Selection" button. */
        const val WORLD_NAME_LABEL_H = 48.0

        /** Vertical gap between the world-name label and the "World Selection" button below it. */
        const val WORLD_NAME_LABEL_GAP = 12.0

        /** Horizontal breathing room kept clear inside the world-name label's border. */
        const val WORLD_NAME_LABEL_PADDING_X = 16.0

        /** Heading shown at the top of the scene. */
        const val TITLE_TEXT = "Multi-Tisch-Modus"
    }

    companion object {
        /** Scene target used by the back button. Callers set this before opening the scene. */
        var backTarget: BackTarget = BackTarget.MENU_SCENE
    }

    private var infoContainer: Container? = null
    private var actionRowContainer: Container? = null
    private var overlayContainer: Container? = null

    /** Root container of this scene, captured in [sceneMain] so private helper methods can draw into it. */
    private lateinit var sceneRoot: Container

    /**
     * Builds the multi-table mode scene and wires its placeholder server interactions.
     */
    override suspend fun SContainer.sceneMain() {
        sceneRoot = this
        drawWhiteSceneBackground()
        drawTitle()
        drawNavigationButtons()
        redrawDynamicContent()
    }

    /**
     * Draws the centered scene title at the top of the screen.
     */
    private fun SContainer.drawTitle() {
        text(
            ServerMenuConfig.TITLE_TEXT,
            textSize = ServerMenuConfig.TITLE_TEXT_SIZE,
            color = Colors.BLACK,
            font = bitcountSingle
        ) {
            centerXOn(this@drawTitle)
            y = ServerMenuConfig.TITLE_Y
        }
    }

    /**
     * Draws the back and close navigation buttons and wires their click handlers.
     *
     * The back button navigates to the menu that opened this scene.
     * The close button returns to the active gameplay scene for that menu.
     */
    private fun SContainer.drawNavigationButtons() {
        ControlButtons.drawBackButton(this).onClickWithSound {
            launch { navigateBackFromServerMenu() }
        }
        ControlButtons.drawCloseButton(this).onClickWithSound {
            launch {
                closeToGameplayScene()
            }
        }
    }

    /**
     * Navigates to the menu that opened the server scene.
     */
    private suspend fun navigateBackFromServerMenu() {
        when (backTarget) {
            BackTarget.MENU_SCENE -> sceneContainer.changeTo { MenuScene() }
            BackTarget.MULTI_TABLE_MENU_SCENE -> sceneContainer.changeTo { MultiTableMenuScene() }
        }
    }

    /** Closes the server menu to the gameplay scene that matches the opening pause menu. */
    private suspend fun closeToGameplayScene() {
        when (backTarget) {
            BackTarget.MENU_SCENE -> sceneContainer.changeTo<LevelScene>()
            BackTarget.MULTI_TABLE_MENU_SCENE -> sceneContainer.changeTo<WorldScene>()
        }
    }

    /**
     * Rebuilds the parts of the scene that change with connection state.
     */
    private fun redrawDynamicContent() {
        // Clear existing containers
        infoContainer?.let { if (it.parent != null) it.parent?.removeChild(it) }
        actionRowContainer?.let { if (it.parent != null) it.parent?.removeChild(it) }
        overlayContainer?.let { if (it.parent != null) it.parent?.removeChild(it) }

        // Ensure we remove any existing info containers from the scene root
        // This handles cases where the container reference might be stale
        sceneRoot.children.filter { 
            it is Container && it.y == ServerMenuConfig.INFO_COLUMN_Y
        }.forEach { 
            sceneRoot.removeChild(it)
        }

        infoContainer = ServerMenuSceneUI.drawInfoColumn(sceneRoot)
        actionRowContainer = drawActionRow()

        when (ServerConnection.connectionState) {
            ConnectionState.CONNECTING -> overlayContainer = ServerMenuSceneUI.drawTransitionOverlay(
                sceneRoot, ConnectionState.CONNECTING.label, ::onConnectCancelled)
            ConnectionState.DISCONNECTING -> overlayContainer = ServerMenuSceneUI.drawTransitionOverlay(
                sceneRoot, ConnectionState.DISCONNECTING.label, ::onDisconnectCancelled)
            ConnectionState.RECONNECTING -> {
                // Take over state-change notifications so a successful automatic reconnect (driven
                // by the connection manager) redraws this scene and removes the overlay.
                ServerConnection.setOnStateChange(::redrawDynamicContent)
                overlayContainer = ServerMenuSceneUI.drawConnectionLostOverlay(
                    sceneRoot,
                    ConnectionState.RECONNECTING.label,
                    ServerMenuConfig.RECONNECT_HINT_TEXT,
                    ::onReconnectCancelled
                )
            }
            else -> Unit
        }
    }

    /**
     * Draws the bottom action row: connect/disconnect, and, once connected, the
     * world-selection and tile-selection buttons.
     */
    private fun drawActionRow(): Container {
        val state = ServerConnection.connectionState
        val connected = state == ConnectionState.CONNECTED
        val reconnecting = state == ConnectionState.RECONNECTING
        val transitioning = state == ConnectionState.CONNECTING ||
            state == ConnectionState.DISCONNECTING

        return sceneRoot.container {
            if (connected) {
                drawConnectedActionRow(this)
            }

            // While reconnecting the connection is technically down, but the button keeps its
            // "Trennen" affordance (disabled) so the layout stays continuous behind the overlay.
            val locked = transitioning || reconnecting
            val connectionButton = ServerMenuSceneUI.drawConnectionButton(
                this, connected = connected || reconnecting, transitioning = locked
            )
            if (!locked) {
                connectionButton.onClickWithSound { onConnectionButtonClicked(connected) }
            }
        }
    }

    /**
     * Draws the action row shown when the server connection is active.
     *
     * Shows a world-selection button when no world has been opened yet, or the world name
     * and further navigation buttons when a world is already open.
     *
     * @param actionContainer container that receives the action buttons.
     */
    private fun drawConnectedActionRow(actionContainer: Container) {
        val hasOpenedWorld = WorldSelectionScene.openedWorld != null

        if (hasOpenedWorld) {
            drawWorldWithButtons(actionContainer)
        } else {
            val worldButton = ServerMenuSceneUI.drawWorldSelectionButton(actionContainer, prominent = true)
            worldButton.onClickWithSound {
                launch {
                    WorldSelectionScene.backDestination = WorldSelectionBackDestination.SERVER_MENU
                    sceneContainer.pushTo<WorldSelectionScene>()
                }
            }
        }
    }

    /**
     * Draws the opened world's name label and navigation buttons for the connected state.
     *
     * Sets [WorldSelectionScene.backDestination] before navigating to [WorldSelectionScene]
     * so that its back button returns here.
     *
     * @param actionContainer container that receives the buttons.
     */
    private fun drawWorldWithButtons(actionContainer: Container) {
        val buttonHeight = ServerMenuConfig.WORLD_SELECT_BUTTON_PROMINENT_H
        val buttonGap = ServerMenuConfig.NAV_BUTTON_GAP
        val totalHeight = buttonHeight * 2 + buttonGap
        val startY = TABLE_PX_H / 2.0 - totalHeight / 2.0

        val openedWorld = WorldSelectionScene.openedWorld
        if (openedWorld != null) {
            ServerMenuSceneUI.drawWorldNameLabel(
                actionContainer,
                "Aktuelle Welt: ${WorldSelectionScene.worldLabel(openedWorld)}",
                startY
            )
        }

        val worldButton = ServerMenuSceneUI.drawCenteredProminentNavButton(actionContainer, "Weltauswahl", startY)
        worldButton.onClickWithSound {
            launch {
                WorldSelectionScene.backDestination = WorldSelectionBackDestination.SERVER_MENU
                sceneContainer.pushTo<WorldSelectionScene>()
            }
        }

        val tileButton = ServerMenuSceneUI.drawCenteredProminentNavButton(
            actionContainer, "Tisch-Kachel wählen", startY + buttonHeight + buttonGap)
        tileButton.onClickWithSound {
            launch {
                WorldPlaceholderScene.backDestination = WorldPlaceholderScene.BackDestination.SERVER_MENU
                sceneContainer.changeTo<WorldPlaceholderScene>()
            }
        }
    }

    /**
     * Dispatches the connect or disconnect request depending on the current connection state.
     *
     * @param connected true when the server is currently connected; triggers a disconnect request.
     */
    private fun onConnectionButtonClicked(connected: Boolean) {
        if (connected) ServerMenuSceneLogic.onDisconnectRequested(::redrawDynamicContent)
        else ServerMenuSceneLogic.onConnectRequested(::redrawDynamicContent)
    }

    /**
     * Cancels the in-progress connect attempt and redraws the dynamic content.
     */
    private fun onConnectCancelled() = ServerMenuSceneLogic.onConnectCancelled(::redrawDynamicContent)

    /**
     * Cancels the in-progress disconnect attempt and redraws the dynamic content.
     */
    private fun onDisconnectCancelled() = ServerMenuSceneLogic.onDisconnectCancelled(::redrawDynamicContent)

    /**
     * Cancels the automatic reconnect attempt and redraws the dynamic content as disconnected.
     */
    private fun onReconnectCancelled() = ServerMenuSceneLogic.onReconnectCancelled(::redrawDynamicContent)
}
