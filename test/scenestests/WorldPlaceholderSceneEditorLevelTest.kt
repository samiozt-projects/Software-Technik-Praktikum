package scenestests

import domain.ConnectionState
import domain.GameState
import korlibs.io.file.std.applicationVfs
import network.SaveWorldEditorDTO
import network.ServerConnection
import network.WorldEditorStateDTO
import persistence.EditorWorldSaves
import persistence.LevelSerializer
import persistence.WorldTemplate
import worldeditor.WorldEditorGateway
import worldeditor.WorldEditorRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.IOException

/**
 * Verifies that the dial-in flow loads the correct level for a server world: the world's saved
 * editor level when one exists, instead of a blank standard template.
 */
class WorldPlaceholderSceneEditorLevelTest : WorldPlaceholderSceneTestBase() {

    /**
     * Verifies that entering a server world after tile confirmation loads the world's saved
     * editor level instead of a blank standard template, so sources and sinks placed in the
     * world editor actually appear in the running world.
     */
    @Test
    fun navigateAfterConfirmedTile_loadsSavedEditorLevelInsteadOfBlankTemplate() = viewsTest {
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        val savedLevel = WorldTemplate.createBlankWorld("Bearbeitete Welt")
        WorldEditorRepository.gateway = FakeWorldEditorGateway(
            WorldEditorStateDTO(
                worldId = TEST_WORLD_ID,
                name = "Bearbeitete Welt",
                templateLevelId = 1,
                levelJson = LevelSerializer.toJsonString(savedLevel),
                editorNodes = emptyList()
            )
        )
        prepareWorldPlaceholderScene()

        selectFirstFreeTile()
        confirmPendingTileSelection()
        clickTileNavigateButton()
        awaitFrames { GameState.currentLevel != null }

        assertEquals("Bearbeitete Welt", GameState.currentLevel?.name)
        assertEquals(savedLevel.nodes.size, GameState.currentLevel?.nodes?.size)
    }

    /**
     * Verifies that the dial-in flow restores the locally saved editor level when the server has
     * no editor state (the coupling server does not offer the editor-state endpoints yet), so
     * editor saves survive instead of degrading to the standard template.
     */
    @Test
    fun navigateAfterConfirmedTile_usesLocalEditorSaveWhenServerStateUnavailable() = viewsTest {
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        WorldEditorRepository.gateway = ThrowingWorldEditorGateway()
        EditorWorldSaves.saveLevel(TEST_WORLD_ID, WorldTemplate.createBlankWorld("Lokal gespeicherte Welt"))
        try {
            prepareWorldPlaceholderScene()

            selectFirstFreeTile()
            confirmPendingTileSelection()
            clickTileNavigateButton()
            awaitFrames { GameState.currentLevel != null }

            assertEquals("Lokal gespeicherte Welt", GameState.currentLevel?.name)
        } finally {
            applicationVfs[EditorWorldSaves.levelPath(TEST_WORLD_ID)].delete()
        }
    }

    /** In-memory editor gateway returning one fixed saved world state. */
    private class FakeWorldEditorGateway(
        private val state: WorldEditorStateDTO
    ) : WorldEditorGateway {
        /**
         * Returns the fixed editor state for every requested world ID.
         *
         * @param worldId ID of the world whose editor state is requested.
         */
        override suspend fun getWorldEditorState(worldId: String): WorldEditorStateDTO = state

        /** Pretends that creating an editor state returns the fixed state. */
        override suspend fun createEditorWorld(request: SaveWorldEditorDTO): WorldEditorStateDTO = state

        /** Pretends that updating an editor state returns the fixed state. */
        override suspend fun updateEditorWorld(worldId: String, request: SaveWorldEditorDTO): WorldEditorStateDTO = state
    }

    /** Editor gateway simulating a server without editor-state endpoints (404 on every call). */
    private class ThrowingWorldEditorGateway : WorldEditorGateway {

        /**
         * Fails every editor-state load like a missing server endpoint, so the local editor save is used instead.
         *
         * @param worldId ID of the world whose editor state is requested.
         * @return Returns nothing, always throws an IOException.
         */
        override suspend fun getWorldEditorState(worldId: String): WorldEditorStateDTO =
            throw IOException("editor-state endpoint missing")

        /** Fails every editor-state creation like a missing server endpoint. */
        override suspend fun createEditorWorld(request: SaveWorldEditorDTO): WorldEditorStateDTO =
            throw IOException("editor-state endpoint missing")

        /** Fails every editor-state update like a missing server endpoint. */
        override suspend fun updateEditorWorld(worldId: String, request: SaveWorldEditorDTO): WorldEditorStateDTO =
            throw IOException("editor-state endpoint missing")
    }
}
