package scenestests

import config.ConfigManager
import domain.ConnectionState
import domain.GameState
import domain.SimulationState
import korlibs.io.async.suspendTest
import korlibs.korge.tests.ViewsForTesting
import korlibs.math.geom.Size
import network.CoordinateDTO
import network.CreateWorldDTO
import network.CreateWorldResponse
import network.FieldPropertiesDTO
import network.ServerConnection
import persistence.WorldLevelSelection
import scenes.LevelScene
import scenes.NewWorldCreationServer
import scenes.NewWorldCreationScene
import scenes.ServerWorldsResult
import scenes.WorldModeUi
import scenes.WorldSelectionScene
import ui.Scrollbar
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests validation, option building, and the successful create-world scene flow.
 */
class NewWorldCreationSceneTest : ViewsForTesting(
    frameTime = 50.milliseconds,
    windowSize = Size(1800, 1000)
) {

    private lateinit var createRequests: MutableList<CreateWorldDTO>

    /** Installs fake server and metadata services used by each create-flow test. */
    @BeforeTest
    fun setupServices() {
        createRequests = mutableListOf()
        ServerConnection.setConnectionStateForTesting(ConnectionState.CONNECTED)
        NewWorldCreationScene.deleteTokenFactory = { "fixed-delete-token" }
        NewWorldCreationScene.temporaryWorldIdFactory = { "temp-world-fixed" }
        NewWorldCreationScene.temporaryLocalCreationEnabled = true
        NewWorldCreationScene.loadWorldsForValidation = { ServerWorldsResult.Success(emptyList()) }
        NewWorldCreationServer.networkConfigProvider = {
            ConfigManager.NetworkConfig(
                host = "localhost",
                port = 8080,
                active = true,
                baseUrl = "http://localhost:8080",
                worldDeleteToken = "fixed-delete-token"
            )
        }
        NewWorldCreationServer.createWorldCall = { _, request ->
            createRequests += request
            CreateWorldResponse("world-created")
        }
        NewWorldCreationServer.loadFieldsCall = { _, worldId -> serverFields(worldId) }
    }

    /** Restores global state and replaceable factories after each create-flow test. */
    @AfterTest
    fun resetGlobalState() {
        GameState.currentLevel = null
        GameState.simulationState = SimulationState.NO_LEVEL
        WorldSelectionScene.resetLocalWorldStateForTesting()
        ServerConnection.reset()
        NewWorldCreationScene.resetTestHooks()
    }

    /** Verifies blank-name validation and visible validation messages. */
    @Test
    fun worldNameValidationRejectsBlankNamesWithMessageText() {
        assertFalse(NewWorldCreationScene.isWorldNameValid("   "))
        assertEquals(NewWorldCreationScene.EMPTY_WORLD_NAME_ERROR, NewWorldCreationScene.validationErrorForWorldName(""))
        assertTrue(NewWorldCreationScene.isWorldNameValid(" Neue Welt "))
        assertNull(NewWorldCreationScene.validationErrorForWorldName("Neue Welt"))
    }

    /** Verifies that the create button is highlighted as invalid for blank names. */
    @Test
    fun createButtonFillMarksBlankWorldNameRed() {
        assertEquals(
            NewWorldCreationScene.INVALID_CREATE_BUTTON_FILL,
            NewWorldCreationScene.createButtonFillForWorldName("   ")
        )
        assertEquals(
            NewWorldCreationScene.CREATE_BUTTON_FILL,
            NewWorldCreationScene.createButtonFillForWorldName("Neue Welt")
        )
    }

    /** Verifies duplicate-name validation regardless of case and surrounding whitespace. */
    @Test
    fun createButtonFillMarksDuplicateWorldNameRedIgnoringCaseAndWhitespace() {
        val existingWorlds = listOf(WorldSelectionScene.WorldEntry("world-1", 1, "Test", "Server"))

        assertTrue(NewWorldCreationScene.isDuplicateWorldName("  TEST  ", existingWorlds))
        assertFalse(NewWorldCreationScene.isWorldNameValid("test", existingWorlds))
        assertEquals(
            NewWorldCreationScene.DUPLICATE_WORLD_NAME_ERROR,
            NewWorldCreationScene.validationErrorForWorldName("  TEST  ", existingWorlds)
        )
        assertEquals(
            NewWorldCreationScene.INVALID_CREATE_BUTTON_FILL,
            NewWorldCreationScene.createButtonFillForWorldName("  TEST  ", existingWorlds)
        )
        assertEquals(
            NewWorldCreationScene.CREATE_BUTTON_FILL,
            NewWorldCreationScene.createButtonFillForWorldName("Andere Welt", existingWorlds)
        )
    }

    /** Verifies that overflowing level options activate the dropdown scrollbar. */
    @Test
    fun levelDropdownUsesScrollbarWhenOptionsOverflowVisibleRows() {
        val overflowingOptionCount = NewWorldCreationScene.LEVEL_DROPDOWN_VISIBLE_ROWS + 1
        val viewportHeight = NewWorldCreationScene.levelDropdownViewportHeight(overflowingOptionCount)
        val contentHeight = NewWorldCreationScene.levelDropdownContentHeight(overflowingOptionCount)

        assertEquals(NewWorldCreationScene.LEVEL_DROPDOWN_MAX_H, viewportHeight)
        assertTrue(Scrollbar.isVisible(viewportHeight, contentHeight))
    }

    /** Verifies that short option lists keep the dropdown at content height. */
    @Test
    fun levelDropdownFitsShortOptionListsWithoutScrollbar() {
        val shortOptionCount = 2
        val viewportHeight = NewWorldCreationScene.levelDropdownViewportHeight(shortOptionCount)
        val contentHeight = NewWorldCreationScene.levelDropdownContentHeight(shortOptionCount)

        assertEquals(shortOptionCount * NewWorldCreationScene.LEVEL_OPTION_H, viewportHeight)
        assertFalse(Scrollbar.isVisible(viewportHeight, contentHeight))
    }

    /** Verifies that shared world-mode messages are centered in the projected scene. */
    @Test
    fun centeredWorldModeMessageUsesSceneMiddle() {
        assertEquals(650.0, WorldModeUi.centeredMessageX(LevelScene.TABLE_PX_W, 500.0))
    }

    /** Verifies the fixed coupling-server dimensions used for create requests. */
    @Test
    fun createWorldRequestUsesFixedServerDimensionsAndDeleteToken() {
        val request = NewWorldCreationScene.createWorldRequest("secret")

        assertEquals(2, request.columns)
        assertEquals(2, request.rows)
        assertEquals(100, request.fieldWidth)
        assertEquals(100, request.fieldHeight)
        assertEquals("secret", request.deleteToken)
    }

    /** Verifies that the standard template is the first selectable level option. */
    @Test
    fun levelOptionsStartWithStandardWorldThenPredefinedLevels() = suspendTest {
        val options = NewWorldCreationScene.buildLevelOptions()

        assertEquals(WorldLevelSelection.StandardTemplate, options[0].selection)
        assertEquals(NewWorldCreationScene.STANDARD_WORLD_LABEL, options[0].label)
        assertEquals(WorldLevelSelection.PredefinedLevel(1), options[1].selection)
        assertEquals("Level 1", options[1].label)
        assertEquals(WorldLevelSelection.PredefinedLevel(8), options[8].selection)
        assertEquals("Level 8", options[8].label)
    }

    /** Verifies that successful server creation persists metadata and registers selectable tiles. */
    @Test
    fun successfulCreatePersistsMetadataAndRegistersTiles() = suspendTest {
        NewWorldCreationServer.createServerWorldEntry(
            worldName = "Neue Welt",
            levelSelection = WorldLevelSelection.StandardTemplate,
            deleteToken = "fixed-delete-token",
            temporaryWorldIdFactory = NewWorldCreationScene.temporaryWorldIdFactory,
            temporaryLocalCreationEnabled = NewWorldCreationScene.temporaryLocalCreationEnabled
        )

        assertEquals(1, createRequests.size)
        assertEquals("fixed-delete-token", createRequests.single().deleteToken)
        assertNull(GameState.currentLevel)
        assertEquals("world-created", WorldSelectionScene.openedWorld?.id)
        assertEquals("Neue Welt", WorldSelectionScene.openedWorld?.name)
        assertEquals(WorldLevelSelection.StandardTemplate, WorldSelectionScene.openedWorld?.levelSelection)
        assertEquals("fixed-delete-token", WorldSelectionScene.openedWorld?.deleteToken)
        assertEquals(4, ServerConnection.tilesFor("world-created").size)
        assertEquals(
            listOf("field-a", "field-b", "field-c", "field-d"),
            ServerConnection.tilesFor("world-created").map { it.id }
        )
    }

    /** Verifies that inactive networking creates a temporary world without a server call. */
    @Test
    fun inactiveServerCreatesTemporaryWorldWithoutServerCall() = suspendTest {
        ServerConnection.reset()
        NewWorldCreationServer.createServerWorldEntry(
            worldName = "Neue Welt",
            levelSelection = WorldLevelSelection.StandardTemplate,
            deleteToken = "fixed-delete-token",
            temporaryWorldIdFactory = NewWorldCreationScene.temporaryWorldIdFactory,
            temporaryLocalCreationEnabled = NewWorldCreationScene.temporaryLocalCreationEnabled
        )

        assertEquals(emptyList(), createRequests)
        assertEquals("temp-world-fixed", WorldSelectionScene.openedWorld?.id)
        assertEquals(4, ServerConnection.tilesFor("temp-world-fixed").size)
    }

    /** Verifies that duplicate names from the initial list are rejected before create calls. */
    @Test
    fun duplicateWorldNamePreventsCreateCallAndKeepsCreationScene() {
        val existingWorlds = listOf(WorldSelectionScene.WorldEntry("world-1", 1, " neue welt ", "Server"))

        assertFalse(NewWorldCreationScene.isWorldNameValid("Neue Welt", existingWorlds))
        assertEquals(emptyList(), createRequests)
        assertNull(WorldSelectionScene.openedWorld)
    }

    /** Verifies that duplicate names from submit-time server lookup prevent creation. */
    @Test
    fun serverDuplicateWorldNamePreventsCreateCallAndKeepsCreationScene() {
        val serverWorlds = listOf(WorldSelectionScene.WorldEntry("server-world-dup", 1, " neue welt ", "Server"))

        assertFalse(NewWorldCreationScene.isWorldNameValid("Neue Welt", serverWorlds))
        assertEquals(emptyList(), createRequests)
        assertNull(WorldSelectionScene.openedWorld)
    }

    /** Verifies that a failed submit-time name lookup maps to the server-unreachable error. */
    @Test
    fun serverNameCheckFailurePreventsCreateCallAndKeepsCreationScene() {
        assertEquals("Server nicht erreichbar", NewWorldCreationScene.errorMessageFor(IOException("offline")))
        assertEquals(emptyList(), createRequests)
        assertNull(WorldSelectionScene.openedWorld)
    }

    /** Verifies that a failed field load deletes the just-created world instead of orphaning it. */
    @Test
    fun failedFieldLoadDeletesCreatedWorldAndReportsSpecificError() = suspendTest {
        val deletedWorlds = mutableListOf<String>()
        NewWorldCreationServer.loadFieldsCall = { _, _ -> throw IOException("fields offline") }
        NewWorldCreationServer.deleteWorldCall = { _, worldId, _ -> deletedWorlds += worldId }

        val error = assertFailsWith<IOException> {
            NewWorldCreationServer.createServerWorldEntry(
                worldName = "Neue Welt",
                levelSelection = WorldLevelSelection.StandardTemplate,
                deleteToken = "fixed-delete-token",
                temporaryWorldIdFactory = NewWorldCreationScene.temporaryWorldIdFactory,
                temporaryLocalCreationEnabled = NewWorldCreationScene.temporaryLocalCreationEnabled
            )
        }

        assertEquals(listOf("world-created"), deletedWorlds)
        assertEquals(
            "Felder der neuen Welt konnten nicht geladen werden",
            NewWorldCreationScene.errorMessageFor(error)
        )
        assertNull(WorldSelectionScene.openedWorld)
    }

    /** Verifies that an empty server field list deletes the created world and surfaces an error. */
    @Test
    fun emptyFieldListDeletesCreatedWorldAndReportsSpecificError() = suspendTest {
        val deletedWorlds = mutableListOf<String>()
        NewWorldCreationServer.loadFieldsCall = { _, _ -> emptyList() }
        NewWorldCreationServer.deleteWorldCall = { _, worldId, _ -> deletedWorlds += worldId }

        val error = assertFailsWith<IOException> {
            NewWorldCreationServer.createServerWorldEntry(
                worldName = "Neue Welt",
                levelSelection = WorldLevelSelection.StandardTemplate,
                deleteToken = "fixed-delete-token",
                temporaryWorldIdFactory = NewWorldCreationScene.temporaryWorldIdFactory,
                temporaryLocalCreationEnabled = NewWorldCreationScene.temporaryLocalCreationEnabled
            )
        }

        assertEquals(listOf("world-created"), deletedWorlds)
        assertEquals(
            "Server lieferte keine Felder für die neue Welt",
            NewWorldCreationScene.errorMessageFor(error)
        )
        assertNull(WorldSelectionScene.openedWorld)
    }

    private fun serverFields(worldId: String): List<FieldPropertiesDTO> = listOf(
        FieldPropertiesDTO(
            fieldId = "field-a",
            active = false,
            coordinate = CoordinateDTO(0, 0),
            parentWorldId = worldId
        ),
        FieldPropertiesDTO(
            fieldId = "field-b",
            active = false,
            coordinate = CoordinateDTO(100, 0),
            parentWorldId = worldId
        ),
        FieldPropertiesDTO(
            fieldId = "field-c",
            active = false,
            coordinate = CoordinateDTO(0, 100),
            parentWorldId = worldId
        ),
        FieldPropertiesDTO(
            fieldId = "field-d",
            active = false,
            coordinate = CoordinateDTO(100, 100),
            parentWorldId = worldId
        )
    )
}
