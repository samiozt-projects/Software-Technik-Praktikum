package scenestests

import scenes.WorldSelectionScene
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the open decision of the world selection: only locally saved snapshot worlds skip the
 * tile selection. Server worlds must always dial in through the tile selection, even after an
 * editor save stored a local level file for them (regression: they used to jump straight into
 * the level scene once savedLevelPath was set).
 */
class WorldSelectionOpenDecisionTest {

    /** Verifies that locally saved snapshot worlds open directly in the level scene. */
    @Test
    fun savedSnapshotWorld_opensDirectly() {
        val world = WorldSelectionScene.WorldEntry(
            id = "saved-0",
            number = 1,
            name = "Snapshot",
            source = WorldSelectionScene.SAVED_WORLD_SOURCE,
            savedLevelPath = "savedworld/snapshot.json"
        )

        assertTrue(WorldSelectionScene.opensDirectlyFromSave(world))
    }

    /** Verifies that a server world keeps its tile selection even with a local editor save. */
    @Test
    fun serverWorldWithLocalEditorSave_stillUsesTileSelection() {
        val world = WorldSelectionScene.WorldEntry(
            id = "world-123",
            number = 1,
            name = "Serverwelt",
            source = "Server",
            savedLevelPath = "editorstate/editor-level-world-123.json"
        )

        assertFalse(WorldSelectionScene.opensDirectlyFromSave(world))
    }

    /** Verifies that temporary local worlds keep their tile selection as well. */
    @Test
    fun temporaryLocalWorldWithEditorSave_stillUsesTileSelection() {
        val world = WorldSelectionScene.WorldEntry(
            id = "temp-world-1",
            number = 1,
            name = "Testwelt",
            source = "Lokal",
            savedLevelPath = "editorstate/editor-level-temp-world-1.json"
        )

        assertFalse(WorldSelectionScene.opensDirectlyFromSave(world))
    }

    /** Verifies that worlds without any saved file always use the tile selection. */
    @Test
    fun worldWithoutSavedFile_usesTileSelection() {
        val world = WorldSelectionScene.WorldEntry(
            id = "world-456",
            number = 1,
            name = "Serverwelt",
            source = "Server"
        )

        assertFalse(WorldSelectionScene.opensDirectlyFromSave(world))
    }
}
