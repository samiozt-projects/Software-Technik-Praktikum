package scenes

import domain.GameState
import korlibs.korge.scene.SceneContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import network.ServerConnection

/**
 * Wires [ServerConnection.setOnConnectionLost] so that losing an established connection switches the
 * game to [ServerMenuScene], where the "Verbindung zum Server verloren" overlay and the automatic
 * reconnect attempt are shown (issue #210, acceptance criterion 1).
 *
 * Installed once at startup from `main`. The listener fires from `ServerConnection.loseConnection`
 * (triggered by an unexpected WebSocket close/error); the scene change is dispatched on [scope] so it
 * runs on the game's coroutine context regardless of which thread reported the drop. It navigates
 * from whichever scene is active (typically a level being played), so a server outage during play
 * reliably lands the player on the reconnect overlay.
 *
 * @param sceneContainer the root scene container to navigate.
 * @param scope coroutine scope bound to the game loop, used to launch the scene change.
 */
internal fun installConnectionLossNavigation(sceneContainer: SceneContainer, scope: CoroutineScope) {
    ServerConnection.setOnConnectionLost {
        scope.launch { sceneContainer.navigateToServerMenuOnConnectionLoss() }
    }
}

/**
 * Switches to [ServerMenuScene] so the reconnect overlay becomes visible, guarding against
 * overlapping scene transitions through [GameState.tryBeginSceneTransition] (issue #184).
 *
 * Re-entering the scene when it is already active is intentional: it rebuilds the scene in the
 * [domain.ConnectionState.RECONNECTING] state so the overlay is shown even when the connection drops
 * while the multi-table menu itself is open.
 */
private suspend fun SceneContainer.navigateToServerMenuOnConnectionLoss() {
    if (!GameState.tryBeginSceneTransition()) return
    try {
        changeTo<ServerMenuScene>()
    } finally {
        GameState.endSceneTransition()
    }
}
