package scenestests

import domain.ConnectionState
import korlibs.image.color.Colors
import network.ServerConnection
import persistence.WorldLevelSelection
import scenes.LevelScene
import scenes.WorldEditScene
import scenes.WorldModeUi
import scenes.WorldPlaceholderScene
import scenes.WorldSelectionBackDestination
import scenes.WorldSelectionScene
import ui.Scrollbar
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests stable layout and state helpers used by the world selection menu.
 */
class WorldSelectionSceneTest {
    /** Initializes shared scene state touched by world-selection tests. */
    @BeforeTest
    fun setupSharedState() {
        resetWorldSelectionSharedState()
    }

    /** Resets shared connection and opened-world state touched by editor-gate tests. */
    @AfterTest
    fun resetSharedState() {
        resetWorldSelectionSharedState()
    }

    private fun resetWorldSelectionSharedState() {
        ServerConnection.reset()
        WorldSelectionScene.resetLocalWorldStateForTesting()
        WorldPlaceholderScene.clearConfirmedTile()
    }

    /**
     * Verifies that world selection does not route back through the multi-table pause menu by
     * default.
     */
    @Test
    fun resetLocalWorldState_defaultsBackDestinationToLevelScene() {
        WorldSelectionScene.backDestination = WorldSelectionBackDestination.SERVER_MENU

        WorldSelectionScene.resetLocalWorldStateForTesting()

        assertEquals(WorldSelectionBackDestination.LEVEL_SCENE, WorldSelectionScene.backDestination)
    }

    /**
     * Verifies that placeholder worlds produce the user-facing labels from the mockup.
     */
    @Test
    fun worldLabel_formatsWorldRowsLikeMockup() {
        val world = WorldSelectionScene.WorldEntry("world-1", 1, "Stadtkreuzung", "Server")

        assertEquals("Welt 1 - Stadtkreuzung - 3x3", WorldSelectionScene.worldLabel(world))
    }

    /**
     * Verifies that server fallback names that are only technical IDs stay internal and are not
     * repeated in the normal world-selection label.
     */
    @Test
    fun worldLabel_hidesTechnicalIdFallbackName() {
        val technicalId = "f34c5423-c41e-408e-82d2-97b58110534f"
        val world = WorldSelectionScene.WorldEntry(
            id = technicalId,
            number = 902,
            name = technicalId,
            source = "Server",
            columns = 2,
            rows = 2
        )

        assertEquals("Welt 902 - 2x2", WorldSelectionScene.worldLabel(world))
        assertEquals(technicalId, world.id)
    }

    /**
     * Verifies that meaningful but long world names are shortened before they can overflow the
     * fixed-width world-selection rows.
     */
    @Test
    fun worldLabel_shortensLongDisplayNames() {
        val longName = "Innenstadt-West-Verkehrsversuch-Mit-Sehr-Langem-Namen"
        val world = WorldSelectionScene.WorldEntry("world-12", 12, longName, "Server")

        val label = WorldSelectionScene.worldLabel(world)

        assertFalse(label.contains(longName))
        assertTrue(label.contains("..."))
        assertTrue(label.startsWith("Welt 12 - Innenstadt-West-Verkehrs"))
        assertTrue(label.endsWith(" - 3x3"))
    }

    /**
     * Verifies that opening a world is disabled until the user selects an existing row.
     */
    @Test
    fun worldActionEnabled_requiresExistingSelection() {
        val worlds = WorldSelectionScene.PLACEHOLDER_WORLDS

        assertFalse(WorldSelectionScene.worldActionEnabled(worlds, null))
        assertFalse(WorldSelectionScene.worldActionEnabled(worlds, -1))
        assertFalse(WorldSelectionScene.worldActionEnabled(worlds, worlds.size))
        assertTrue(WorldSelectionScene.worldActionEnabled(worlds, 0))
    }

    /**
     * Verifies that world-editor actions require both an existing selection and a connected table.
     */
    @Test
    fun worldEditorActionEnabled_requiresConnectedTableAndSelection() {
        val worlds = WorldSelectionScene.PLACEHOLDER_WORLDS

        assertFalse(WorldSelectionScene.worldEditorAvailable())
        assertFalse(WorldSelectionScene.worldEditorActionEnabled(worlds, 0))

        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)

        assertTrue(WorldSelectionScene.worldEditorAvailable())
        assertFalse(WorldSelectionScene.worldEditorActionEnabled(worlds, null))
        assertTrue(WorldSelectionScene.worldEditorActionEnabled(worlds, 0))
    }

    /**
     * Verifies that selected world lookup handles all edge cases without throwing.
     */
    @Test
    fun selectedWorld_returnsNullForMissingOrInvalidSelection() {
        val worlds = WorldSelectionScene.PLACEHOLDER_WORLDS

        assertNull(WorldSelectionScene.selectedWorld(worlds, null))
        assertNull(WorldSelectionScene.selectedWorld(worlds, -1))
        assertNull(WorldSelectionScene.selectedWorld(worlds, worlds.size))
        assertEquals(worlds[1], WorldSelectionScene.selectedWorld(worlds, 1))
    }

    /** Verifies that a freshly created custom name survives a server-list refresh row. */
    @Test
    fun withLocalEdits_appliesCreatedWorldNameAndMetadataToServerRow() {
        val levelSelection = WorldLevelSelection.StandardTemplate
        WorldSelectionScene.rememberOpenedWorld(
            WorldSelectionScene.WorldEntry(
                id = "world-created",
                number = 0,
                name = "Test1",
                source = "Server",
                columns = 2,
                rows = 2,
                levelSelection = levelSelection,
                deleteToken = "fixed-delete-token"
            )
        )
        val serverRow = WorldSelectionScene.WorldEntry(
            id = "world-created",
            number = 5,
            name = "world-created",
            source = "Server",
            columns = 2,
            rows = 2
        )

        val enrichedRow = WorldSelectionScene.withLocalEdits(serverRow)

        assertEquals("Test1", enrichedRow.name)
        assertEquals(5, enrichedRow.number)
        assertEquals(levelSelection, enrichedRow.levelSelection)
        assertEquals("fixed-delete-token", enrichedRow.deleteToken)
    }

    /** Verifies that server UI worlds use their matching predefined level file as template. */
    @Test
    fun editableWorldLevelPath_usesMatchingPredefinedLevelForServerWorlds() {
        WorldSelectionScene.PLACEHOLDER_WORLDS.forEach { world ->
            assertEquals("level/level${world.number}.json", WorldSelectionScene.editableWorldLevelPath(world))
        }
    }

    /** Verifies that a concrete world number selects the corresponding bundled level path. */
    @Test
    fun editableWorldLevelPath_usesWorldNumberAsTemplateNumber() {
        val world = WorldSelectionScene.WorldEntry("world-3", 3, "Testwelt", "Tisch A")

        assertEquals("level/level3.json", WorldSelectionScene.editableWorldLevelPath(world))
    }

    /**
     * Verifies that unsupported placeholder world numbers fall back to the first bundled level.
     */
    @Test
    fun editableWorldLevelPath_usesLevelOneFallbackForUnsupportedWorldNumbers() {
        val world = WorldSelectionScene.WorldEntry("world-9", 9, "Falsche Welt", "Server")

        assertEquals("level/level1.json", WorldSelectionScene.editableWorldLevelPath(world))
    }

    /**
     * Verifies that the edit form exposes the requested title and action labels.
     */
    @Test
    fun worldEditScene_usesRequestedLabels() {
        assertEquals("Welt bearbeiten", WorldEditScene.TITLE_TEXT)
        assertEquals("Abbrechen", WorldEditScene.CANCEL_BUTTON_TEXT)
        assertEquals("Welt-Editor", WorldEditScene.WORLD_EDITOR_BUTTON_TEXT)
        assertEquals("Speichern", WorldEditScene.SAVE_BUTTON_TEXT)
    }

    /**
     * Verifies that a blank world name highlights the save action as invalid.
     */
    @Test
    fun worldEditScene_marksBlankWorldNameSaveButtonRed() {
        assertFalse(WorldEditScene.isSaveWorldNameValid("   "))
        assertEquals(WorldEditScene.EMPTY_WORLD_NAME_ERROR, WorldEditScene.validationErrorForWorldName("   "))
        assertEquals(WorldEditScene.INVALID_SAVE_BUTTON_FILL, WorldEditScene.saveButtonFillForName("   "))
        assertEquals(WorldEditScene.INVALID_SAVE_BUTTON_FILL, WorldEditScene.worldEditorButtonFillForName("   "))
    }

    /**
     * Verifies that a non-blank world name keeps the save action in the regular state.
     */
    @Test
    fun worldEditScene_keepsSaveButtonWhiteForNonBlankName() {
        assertTrue(WorldEditScene.isSaveWorldNameValid(" Neue Welt "))
        assertEquals(Colors.WHITE, WorldEditScene.saveButtonFillForName(" Neue Welt "))
        assertEquals(Colors.WHITE, WorldEditScene.worldEditorButtonFillForName(" Neue Welt "))
    }

    /**
     * Verifies that the current world may keep its own name even with case and whitespace changes.
     */
    @Test
    fun worldEditScene_allowsCurrentWorldNameIgnoringCaseAndWhitespace() {
        val currentWorld = WorldSelectionScene.WorldEntry("world-1", 1, "Test", "Server")
        val otherWorld = WorldSelectionScene.WorldEntry("world-2", 2, "Andere Welt", "Server")
        val existingWorlds = listOf(currentWorld, otherWorld)

        assertTrue(WorldEditScene.isSaveWorldNameValid("  TEST  ", existingWorlds, currentWorld.id))
        assertNull(WorldEditScene.validationErrorForWorldName("  TEST  ", existingWorlds, currentWorld.id))
        assertEquals(
            WorldEditScene.SAVE_BUTTON_FILL,
            WorldEditScene.saveButtonFillForName("  TEST  ", existingWorlds, currentWorld.id)
        )
        assertEquals(
            WorldEditScene.WORLD_EDITOR_BUTTON_FILL,
            WorldEditScene.worldEditorButtonFillForName("  TEST  ", existingWorlds, currentWorld.id)
        )
    }

    /**
     * Verifies that renaming to another existing local world is blocked.
     */
    @Test
    fun worldEditScene_rejectsDuplicateLocalWorldName() {
        val currentWorld = WorldSelectionScene.WorldEntry("world-1", 1, "Alter Name", "Server")
        val otherWorld = WorldSelectionScene.WorldEntry("world-2", 2, "Test", "Server")
        val existingWorlds = listOf(currentWorld, otherWorld)

        assertFalse(WorldEditScene.isSaveWorldNameValid("  test  ", existingWorlds, currentWorld.id))
        assertEquals(
            WorldEditScene.DUPLICATE_WORLD_NAME_ERROR,
            WorldEditScene.validationErrorForWorldName("  test  ", existingWorlds, currentWorld.id)
        )
        assertEquals(
            WorldEditScene.INVALID_SAVE_BUTTON_FILL,
            WorldEditScene.saveButtonFillForName("  test  ", existingWorlds, currentWorld.id)
        )
        assertEquals(
            WorldEditScene.INVALID_SAVE_BUTTON_FILL,
            WorldEditScene.worldEditorButtonFillForName("  test  ", existingWorlds, currentWorld.id)
        )
    }

    /**
     * Verifies that centered world-mode messages use the horizontal scene center.
     */
    @Test
    fun worldModeUi_centersValidationMessages() {
        assertEquals(650.0, WorldModeUi.centeredMessageX(LevelScene.TABLE_PX_W, 500.0))
    }

    /**
     * Verifies that the edit form rows use the mockup's aligned label and input columns.
     */
    @Test
    fun worldEditScene_positionsMetadataRowsLikeMockup() {
        assertEquals(130.0, WorldEditScene.FORM_LABEL_X)
        assertEquals(438.0, WorldEditScene.FORM_INPUT_X)
        assertEquals(235.0, WorldEditScene.WORLD_NAME_INPUT_Y)
        assertEquals(332.0, WorldEditScene.LEVEL_INPUT_Y)
        assertEquals(97.0, WorldEditScene.LEVEL_INPUT_Y - WorldEditScene.WORLD_NAME_INPUT_Y)
    }

    /**
     * Verifies that content height handles empty, single-row, and multi-row lists.
     */
    @Test
    fun worldListContentHeight_handlesEdgeCases() {
        assertEquals(0.0, WorldSelectionScene.worldListContentHeight(0))
        assertEquals(WorldSelectionScene.ROW_H, WorldSelectionScene.worldListContentHeight(1))
        assertEquals(
            WorldSelectionScene.ROW_H * 3.0 + WorldSelectionScene.ROW_GAP * 2.0,
            WorldSelectionScene.worldListContentHeight(3)
        )
    }

    /** Verifies that each world-list page arrow advances exactly the seven visible world rows. */
    @Test
    fun worldListPageArrow_movesSevenWorldRows() {
        assertEquals(7, WorldSelectionScene.WORLDS_PER_PAGE)
        assertEquals(
            7 * (WorldSelectionScene.ROW_H + WorldSelectionScene.ROW_GAP),
            WorldSelectionScene.WORLD_PAGE_SCROLL_DISTANCE
        )
    }

    /** Verifies that wheel input uses a shorter five-and-a-half-world step than the page arrows. */
    @Test
    fun worldListWheelScroll_movesFiveAndAHalfWorldRows() {
        assertEquals(5.5, WorldSelectionScene.WORLDS_PER_WHEEL_SCROLL)
        assertEquals(
            5.5 * (WorldSelectionScene.ROW_H + WorldSelectionScene.ROW_GAP),
            WorldSelectionScene.WORLD_WHEEL_SCROLL_DISTANCE
        )
        assertTrue(WorldSelectionScene.WORLD_WHEEL_SCROLL_DISTANCE < WorldSelectionScene.WORLD_PAGE_SCROLL_DISTANCE)
    }

    /**
     * Verifies that the mockup layout stays inside the projection viewport.
     */
    @Test
    fun worldSelectionLayout_fitsInsideViewport() {
        assertTrue(WorldSelectionScene.LIST_X > 0.0)
        assertTrue(WorldSelectionScene.LIST_Y > 0.0)
        assertTrue(WorldSelectionScene.LIST_X + WorldSelectionScene.LIST_W < LevelScene.TABLE_PX_W)
        assertTrue(WorldSelectionScene.LIST_Y + WorldSelectionScene.LIST_H < LevelScene.TABLE_PX_H)
        assertTrue(WorldSelectionScene.ACTION_X + WorldSelectionScene.ACTION_BUTTON_W < LevelScene.TABLE_PX_W)
    }

    /**
     * Verifies that the world list overflows enough to use the shared scrollbar component.
     */
    @Test
    fun placeholderWorldList_usesVisibleSharedScrollbar() {
        val contentHeight = WorldSelectionScene.worldListContentHeight(WorldSelectionScene.PLACEHOLDER_WORLDS.size)

        assertTrue(Scrollbar.isVisible(WorldSelectionScene.ROW_VIEWPORT_H, contentHeight))
    }

    /**
     * Verifies that deleting the currently opened world closes it and clears all of its local
     * tile state, so the deleted world is no longer selectable, dialed-in, or resurrectable.
     */
    @Test
    fun closeDeletedWorld_closesOpenedWorldAndClearsTileState() {
        val world = WorldSelectionScene.WorldEntry("world-9", 9, "Löschwelt", "Server")
        WorldSelectionScene.rememberOpenedWorld(world)
        ServerConnection.registerAllFreeTiles(world.id)
        ServerConnection.claimTile(world.id, "slot-1")
        WorldPlaceholderScene.confirmTile(world.id, "slot-1")

        WorldSelectionScene.closeDeletedWorld(world)

        assertNull(WorldSelectionScene.openedWorld)
        assertNull(ServerConnection.ownTileIdFor(world.id))
        assertNull(WorldPlaceholderScene.confirmedTileIdFor(world.id))
        // Remembered metadata is gone, so the deleted world cannot reappear after a refresh.
        assertTrue(WorldSelectionScene.rememberedWorldsMissingFrom(emptyList()).isEmpty())
    }

    /**
     * Verifies that deleting a world other than the opened one leaves the opened world and its
     * confirmed tile untouched.
     */
    @Test
    fun closeDeletedWorld_keepsOtherOpenedWorldUntouched() {
        val openedWorld = WorldSelectionScene.WorldEntry("world-1", 1, "Bleibt", "Server")
        WorldSelectionScene.openedWorld = openedWorld
        ServerConnection.registerAllFreeTiles(openedWorld.id)
        ServerConnection.claimTile(openedWorld.id, "slot-2")
        WorldPlaceholderScene.confirmTile(openedWorld.id, "slot-2")
        val deletedWorld = WorldSelectionScene.WorldEntry("world-2", 2, "Weg", "Server")

        WorldSelectionScene.closeDeletedWorld(deletedWorld)

        assertEquals(openedWorld, WorldSelectionScene.openedWorld)
        assertEquals("slot-2", ServerConnection.ownTileIdFor(openedWorld.id))
        assertEquals("slot-2", WorldPlaceholderScene.confirmedTileIdFor(openedWorld.id))
    }

    /**
     * Verifies that saved snapshot worlds are matched by their stable file path instead of the
     * index-based "saved-N" ID, which shifts whenever an earlier saved file is deleted.
     */
    @Test
    fun closeDeletedWorld_matchesSavedWorldsByFilePath() {
        val openedSavedWorld = WorldSelectionScene.WorldEntry(
            "saved-1", 1, "Alpha", WorldSelectionScene.SAVED_WORLD_SOURCE, savedLevelPath = "saves/alpha.json"
        )
        WorldSelectionScene.openedWorld = openedSavedWorld

        // Same row ID after re-indexing, but a different file: must not close the opened world.
        val reindexedOtherWorld = WorldSelectionScene.WorldEntry(
            "saved-1", 1, "Beta", WorldSelectionScene.SAVED_WORLD_SOURCE, savedLevelPath = "saves/beta.json"
        )
        WorldSelectionScene.closeDeletedWorld(reindexedOtherWorld)
        assertEquals(openedSavedWorld, WorldSelectionScene.openedWorld)

        // Same file under a shifted row ID: must close the opened world.
        val sameFileShiftedId = WorldSelectionScene.WorldEntry(
            "saved-0", 1, "Alpha", WorldSelectionScene.SAVED_WORLD_SOURCE, savedLevelPath = "saves/alpha.json"
        )
        WorldSelectionScene.closeDeletedWorld(sameFileShiftedId)
        assertNull(WorldSelectionScene.openedWorld)
    }

}
