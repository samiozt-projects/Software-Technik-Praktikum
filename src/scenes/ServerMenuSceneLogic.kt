package scenes

import audio.InteractionSoundController
import audio.InteractionSoundEffect
import domain.ConnectionState
import network.ServerConnection

/**
 * Logic methods for ServerMenuScene.
 * Contains all methods for handling user actions and state changes.
 */
internal object ServerMenuSceneLogic {

    /**
     * Called when the connect action is requested.
     *
     * @param onRedraw Callback to update the UI.
     * @param onConnected Feedback invoked exactly once after the connection is confirmed.
     */
    fun onConnectRequested(
        onRedraw: () -> Unit,
        onConnected: () -> Unit = {
            InteractionSoundController.play(InteractionSoundEffect.SUCCESS)
        }
    ) {
        var connectionSuccessHandled = false
        ServerConnection.setOnStateChange {
            if (
                !connectionSuccessHandled &&
                ServerConnection.connectionState == ConnectionState.CONNECTED
            ) {
                connectionSuccessHandled = true
                onConnected()
            }
            onRedraw()
        }
        ServerConnection.connect()
        onRedraw()
    }

    /**
     * Called when the disconnect action is requested.
     *
     * @param onRedraw Callback to update the UI.
     */
    fun onDisconnectRequested(onRedraw: () -> Unit) {
        ServerConnection.setOnStateChange(onRedraw)
        ServerConnection.disconnect()
        WorldSelectionScene.openedWorld = null
        WorldPlaceholderScene.clearConfirmedTile()
        onRedraw()
    }

    /**
     * Called when the connect action is cancelled.
     *
     * @param onRedraw Callback to update the UI.
     */
    fun onConnectCancelled(onRedraw: () -> Unit) {
        ServerConnection.cancelConnection()
        onRedraw()
    }

    /**
     * Called when the disconnect action is cancelled.
     *
     * @param onRedraw Callback to update the UI.
     */
    fun onDisconnectCancelled(onRedraw: () -> Unit) {
        ServerConnection.cancelDisconnection()
        onRedraw()
    }

    /**
     * Called when the automatic reconnect is cancelled through the overlay's "Abbrechen" button.
     *
     * Cancelling means the player gives up on the dropped connection, so the connection is fully
     * released (via [ServerConnection.cancelReconnect]) and the opened world is cleared, mirroring
     * an explicit disconnect.
     *
     * @param onRedraw Callback to update the UI.
     */
    fun onReconnectCancelled(onRedraw: () -> Unit) {
        ServerConnection.cancelReconnect()
        WorldSelectionScene.openedWorld = null
        WorldPlaceholderScene.clearConfirmedTile()
        onRedraw()
    }
}
