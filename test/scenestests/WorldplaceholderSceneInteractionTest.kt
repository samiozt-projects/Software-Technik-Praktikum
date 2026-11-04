package scenestests

import domain.ConnectionState
import korlibs.korge.scene.sceneContainer
import network.FieldRegistrationResult
import network.ServerConnection
import scenes.ServerMenuScene
import scenes.WorldPlaceholderScene
import scenes.WorldPlaceholderSceneLogic
import scenes.WorldSelectionScene
import ui.Assets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import java.util.concurrent.CompletableFuture

/**
 * Interaction-driven view tests for [WorldPlaceholderScene]'s per-world table-tile selection.
 */
class WorldPlaceholderSceneInteractionTest : WorldPlaceholderSceneTestBase() {

    /**
     * Tests that clicking a free tile successfully claims it for the local table.
     * Verifies that the server connection records the correct tile ID as owned.
     */
    @Test
    fun clickingFreeTile_claimsItForTheLocalTable() = viewsTest {
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        prepareWorldPlaceholderScene()

        val freeTileIndex = firstFreeTileIndex()
        val freeTile = ServerConnection.tilesFor(TEST_WORLD_ID)[freeTileIndex]
        clickFreeTile(freeTileIndex)
        delayFrame()

        assertEquals(freeTile.id, ServerConnection.ownTileIdFor(TEST_WORLD_ID))
    }

    /**
     * Tests that clicking an already occupied tile does not claim it.
     * Verifies that the server connection does not record any tile ID as owned.
     */
    @Test
    fun clickingOccupiedTile_doesNotClaimIt() = viewsTest {
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        prepareWorldPlaceholderScene()

        val occupiedIndex = ServerConnection.tilesFor(TEST_WORLD_ID)
            .indexOfFirst { it.occupiedByOtherTable }
        clickFreeTile(occupiedIndex)
        delayFrame()

        assertNull(ServerConnection.ownTileIdFor(TEST_WORLD_ID))
    }

    /**
     * Tests the complete workflow of claiming and then removing a tile selection.
     * Verifies that the server connection correctly updates the owned tile ID.
     */
    @Test
    fun confirmingThenRemovingTileSelection_updatesOwnTileId() = viewsTest {
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        prepareWorldPlaceholderScene()
        val tileIndex = firstFreeTileIndex()
        clickFreeTile(tileIndex)
        delayFrame()
        delayFrame()

        confirmPendingTileSelection()

        assertNotNull(ServerConnection.ownTileIdFor(TEST_WORLD_ID))

        clickTileSelectionActionButton(confirmed = true)
        awaitFrames { ServerConnection.ownTileIdFor(TEST_WORLD_ID) == null }

        assertNull(ServerConnection.ownTileIdFor(TEST_WORLD_ID))
    }

    /**
     * Verifies that a failed server field registration is surfaced in-place: the tile remains only
     * pending, the player cannot continue on a falsely confirmed tile, and the server session stays
     * connected for the multi-table menu.
     */
    @Test
    fun failedFieldRegistration_doesNotConfirmTileOrDropServerSession() = viewsTest {
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        WorldPlaceholderSceneLogic.connectSelectedFieldCall = {
            CompletableFuture.completedFuture(FieldRegistrationResult.Failure("Field is already active"))
        }
        prepareWorldPlaceholderScene()

        val tile = selectFirstFreeTile()
        attemptPendingTileSelection()

        assertEquals(tile.id, ServerConnection.ownTileIdFor(TEST_WORLD_ID))
        assertNull(WorldPlaceholderScene.confirmedTileIdFor(TEST_WORLD_ID))
        assertEquals(ConnectionState.CONNECTED, ServerConnection.connectionState)
    }

    /**
     * Verifies that a failed field release does not leave local state pretending the server field
     * was freed, which would make the next selection collide with the old active field.
     */
    @Test
    fun failedFieldDeregistration_keepsConfirmedTileAndServerSession() = viewsTest {
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        WorldPlaceholderSceneLogic.disconnectSelectedFieldCall = {
            CompletableFuture.completedFuture(FieldRegistrationResult.Failure("Field disconnect rejected"))
        }
        prepareWorldPlaceholderScene()

        val tile = selectFirstFreeTile()
        confirmPendingTileSelection()
        clickTileSelectionActionButton(confirmed = true)
        repeat(4) { delayFrame() }

        assertEquals(tile.id, ServerConnection.ownTileIdFor(TEST_WORLD_ID))
        assertEquals(tile.id, WorldPlaceholderScene.confirmedTileIdFor(TEST_WORLD_ID))
        assertEquals(ConnectionState.CONNECTED, ServerConnection.connectionState)
    }

    /** Verifies that a confirmed tile cannot be replaced without explicitly removing it first. */
    @Test
    fun confirmedTile_ignoresOtherTileClicksUntilRemoved() = viewsTest {
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        prepareWorldPlaceholderScene()

        val freeTileIndices = ServerConnection.tilesFor(TEST_WORLD_ID)
            .mapIndexedNotNull { index, tile -> if (!tile.occupiedByOtherTable) index else null }
        val firstTile = ServerConnection.tilesFor(TEST_WORLD_ID)[freeTileIndices[0]]
        val secondTileIndex = freeTileIndices[1]
        clickFreeTile(freeTileIndices[0])
        delayFrame()
        confirmPendingTileSelection()
        clickFreeTile(secondTileIndex)
        repeat(2) { delayFrame() }

        assertEquals(firstTile.id, ServerConnection.ownTileIdFor(TEST_WORLD_ID))
        assertEquals(firstTile.id, WorldPlaceholderScene.confirmedTileIdFor(TEST_WORLD_ID))
    }

    /**
     * Verifies that tile selection opened from the server menu returns there directly instead of
     * forcing the user through world selection first.
     */
    @Test
    fun backButton_fromServerMenuTarget_returnsDirectlyToServerMenu() = viewsTest {
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        WorldPlaceholderScene.backDestination = WorldPlaceholderScene.BackDestination.SERVER_MENU
        val container = prepareWorldPlaceholderScene()

        clickBackButton()
        waitForCurrentScene<ServerMenuScene>(container)

        assertIs<ServerMenuScene>(container.currentScene)
    }

    /**
     * Verifies that when a new world is created, all tile-grid slots are free and clickable —
     * including index 0, which the default layout marks as occupied by another table.
     *
     * This covers the "Neue Welt erstellen" button flow in [scenes.WorldSelectionScene]:
     * [ServerConnection.registerAllFreeTiles] must be called before navigating so that no
     * tiles appear blocked.
     */
    @Test
    fun newWorldMode_firstTileIsClickableEvenThoughDefaultLayoutWouldBlockIt() = viewsTest {
        val newWorldEntry = WorldSelectionScene.WorldEntry("new-world", 0, "Neue Welt", "Lokal")
        WorldSelectionScene.openedWorld = newWorldEntry
        ServerConnection.registerAllFreeTiles("new-world")
        Assets.loadAll()
        sceneContainer().changeTo { WorldPlaceholderScene() }

        // Index 0 is "Tisch A" (occupied) in the default layout; with registerAllFreeTiles it is free.
        clickFreeTile(0)
        delayFrame()

        assertNotNull(
            ServerConnection.ownTileIdFor("new-world"),
            "Tile at index 0 must be claimable when all tiles are registered as free for a new world"
        )
    }
}
