package scenes

import config.ConfigManager
import domain.GameState.tutorialActivated
import audio.InteractionSoundController
import audio.InteractionSoundEffect
import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.annotations.KorgeExperimental
import audio.onClickWithSound
import korlibs.korge.scene.Scene
import korlibs.korge.ui.UITextInput
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.Text
import korlibs.korge.view.addUpdater
import korlibs.korge.view.align.centerXOn
import korlibs.korge.view.text
import kotlinx.coroutines.launch
import persistence.PredefinedLevelCatalog
import persistence.WorldLevelSelection
import persistence.WorldTemplate
import persistence.loadLevelForWorld
import ui.Assets
import ui.ControlButtons
import worldeditor.WorldEditorSession
import java.util.UUID

/**
 * Form used to create a new multi-table world on the coupling server.
 */
@OptIn(KorgeExperimental::class)
class NewWorldCreationScene(
    private val existingWorlds: List<WorldSelectionScene.WorldEntry> = emptyList()
) : Scene() {
    private var knownWorldsForValidation: List<WorldSelectionScene.WorldEntry> = existingWorlds
    private lateinit var levelOptions: List<WorldLevelOption>
    private lateinit var worldNameInput: UITextInput
    private lateinit var messageText: Text
    private lateinit var bottomActions: NewWorldCreationSceneUi.BottomActionHandles
    private var selectedLevelIndex = 0
    private var dropdown: Container? = null
    private var createInProgress = false
    private var createButtonFill: RGBA = CREATE_BUTTON_FILL
    private var errorIncoming = false

    /** A selectable starting level for a newly created world. */
    data class WorldLevelOption(
        /** Persistent level choice used when loading the editor template. */
        val selection: WorldLevelSelection,
        /** User-facing label shown in the level dropdown. */
        val label: String
    )

    companion object {
        /** Scene title. */
        const val TITLE_TEXT = "Neue Welt erstellen"

        /** Label for the world-name field. */
        const val WORLD_NAME_LABEL = "Weltname:"

        /** Label for the level field. */
        const val LEVEL_LABEL = "Level:"

        /** Text on the cancel action. */
        const val CANCEL_BUTTON_TEXT = "Abbrechen"

        /** Text on the create action. */
        const val CREATE_BUTTON_TEXT = "Erstellen"

        /** User-facing label of the standard template option. */
        const val STANDARD_WORLD_LABEL = "Standard-Welt"

        /** Error shown when the required world name is missing. */
        const val EMPTY_WORLD_NAME_ERROR = WorldModeUi.BLANK_WORLD_NAME_ERROR

        /** Error shown when the entered world name already exists. */
        const val DUPLICATE_WORLD_NAME_ERROR = WorldModeUi.DUPLICATE_WORLD_NAME_ERROR

        /** Status text shown while the server request is running. */
        const val CREATING_STATUS_TEXT = "Welt wird erstellt..."

        /** Fixed server world columns for newly created worlds. */
        const val DEFAULT_WORLD_COLUMNS = 2

        /** Fixed server world rows for newly created worlds. */
        const val DEFAULT_WORLD_ROWS = 2

        /** Fixed server field width for newly created worlds. */
        const val DEFAULT_FIELD_WIDTH = 100

        /** Fixed server field height for newly created worlds. */
        const val DEFAULT_FIELD_HEIGHT = 100

        /** Width of the text input fields. */
        const val INPUT_W = 1080.0

        /** Height of the text input fields. */
        const val INPUT_H = 66.0

        /** Left x-position of the form labels. */
        const val FORM_LABEL_X = 130.0

        /** Left x-position of the form input boxes. */
        const val FORM_INPUT_X = 438.0

        /** Top y-position of the world-name input row. */
        const val WORLD_NAME_INPUT_Y = 235.0

        /** Top y-position of the level input row. */
        const val LEVEL_INPUT_Y = 332.0

        /** Text size used for form labels. */
        const val FORM_LABEL_TEXT_SIZE = 30.0

        /** Width of the bottom action buttons. */
        const val ACTION_BUTTON_W = 330.0

        /** Height of the bottom action buttons. */
        const val ACTION_BUTTON_H = 82.0

        /** Horizontal gap between the bottom action buttons. */
        const val ACTION_BUTTON_GAP = 60.0

        /** Top y-position of the bottom action buttons. */
        const val ACTION_BUTTON_Y = 792.0

        /** Height of one level option row. */
        const val LEVEL_OPTION_H = 52.0

        /** Number of level options visible before the dropdown starts scrolling. */
        const val LEVEL_DROPDOWN_VISIBLE_ROWS = 6

        /** Maximum visible height of the level dropdown. */
        const val LEVEL_DROPDOWN_MAX_H = LEVEL_OPTION_H * LEVEL_DROPDOWN_VISIBLE_ROWS

        /** Gap between the level list and its scrollbar. */
        const val LEVEL_DROPDOWN_SCROLLBAR_GAP = 12.0

        /** Mouse wheel scroll multiplier for the level dropdown. */
        const val LEVEL_DROPDOWN_SCROLL_SPEED = 20.0

        /** Border thickness used by boxes and buttons. */
        const val STROKE_THICKNESS = 2.0

        /** Top y-position of the title. */
        const val TITLE_Y = 82.0

        /** Text size used for the title. */
        const val TITLE_TEXT_SIZE = 78.0

        /** Vertical position of centered status and error messages. */
        const val MESSAGE_Y = 560.0

        /** Fill color used for the primary create button. */
        val CREATE_BUTTON_FILL: RGBA = Colors["#DCEFD9"]

        /** Fill color used to mark the create button while the world name is invalid. */
        val INVALID_CREATE_BUTTON_FILL: RGBA = RGBA(255, 50, 50)

        /** Fill color used for the level dropdown rows. */
        val LEVEL_OPTION_FILL: RGBA = Colors["#F7F7F7"]

        /** Error text color. */
        val ERROR_TEXT_COLOR: RGBA = Colors["#B00020"]

        /** Prefix for temporary in-memory worlds used while server creation is unavailable. */
        const val TEMPORARY_WORLD_ID_PREFIX = "temp-world-"

        /** Generates delete tokens for new server worlds. Tests may replace this. */
        var deleteTokenFactory: () -> String = { ConfigManager.loadNetworkConfig().worldDeleteToken }

        /** Generates IDs for temporary local worlds. Tests may replace this. */
        var temporaryWorldIdFactory: () -> String = {
            TEMPORARY_WORLD_ID_PREFIX + UUID.randomUUID().toString()
        }

        /** Enables in-memory world creation when the server is unavailable. */
        var temporaryLocalCreationEnabled: Boolean = true

        /** Loads server worlds for submit-time duplicate validation. Tests may replace this. */
        internal var loadWorldsForValidation: suspend () -> ServerWorldsResult = {
            WorldSelectionServerLoader.loadWorlds()
        }

        /** Restores replaceable collaborators after scene tests. */
        internal fun resetTestHooks() {
            deleteTokenFactory = { ConfigManager.loadNetworkConfig().worldDeleteToken }
            temporaryWorldIdFactory = { TEMPORARY_WORLD_ID_PREFIX + UUID.randomUUID().toString() }
            temporaryLocalCreationEnabled = true
            loadWorldsForValidation = { WorldSelectionServerLoader.loadWorlds() }
            NewWorldCreationServer.resetTestHooks()
        }

        /** Returns all selectable starting levels in display order. */
        internal suspend fun buildLevelOptions(): List<WorldLevelOption> {
            return listOf(WorldLevelOption(WorldLevelSelection.StandardTemplate, STANDARD_WORLD_LABEL)) +
                PredefinedLevelCatalog.levels.map {
                    WorldLevelOption(WorldLevelSelection.PredefinedLevel(it.number), it.title)
                } +
                SavedNetworksScene.savedLevelPaths().map {
                    WorldLevelOption(
                        WorldLevelSelection.SavedLevel(it),
                        "Gespeichert: ${SavedNetworksScene.savedLevelDisplayName(it)}"
                    )
                }
        }

        /** Returns whether [name] already exists in [existingWorlds]. */
        fun isDuplicateWorldName(
            name: String,
            existingWorlds: List<WorldSelectionScene.WorldEntry>
        ): Boolean = WorldModeUi.isDuplicateWorldName(name, existingWorlds)

        /** Returns whether [name] can be submitted. */
        fun isWorldNameValid(
            name: String,
            existingWorlds: List<WorldSelectionScene.WorldEntry> = emptyList()
        ): Boolean = WorldModeUi.validateWorldName(name, existingWorlds).valid

        /**
         * Returns a validation error for [name], or null when no text error should be shown.
         *
         * Blank and duplicate names return the user-facing message shown in the scene.
         */
        fun validationErrorForWorldName(
            name: String,
            existingWorlds: List<WorldSelectionScene.WorldEntry> = emptyList()
        ): String? = WorldModeUi.validateWorldName(name, existingWorlds).message

        /** Returns the create-button fill color for the current world-name input. */
        fun createButtonFillForWorldName(
            name: String,
            existingWorlds: List<WorldSelectionScene.WorldEntry> = emptyList()
        ): RGBA =
            WorldModeUi.buttonFillForValidation(
                WorldModeUi.validateWorldName(name, existingWorlds),
                CREATE_BUTTON_FILL,
                INVALID_CREATE_BUTTON_FILL
            )

        /** Returns the full content height for [optionCount] level dropdown rows. */
        fun levelDropdownContentHeight(optionCount: Int): Double =
            optionCount.coerceAtLeast(0) * LEVEL_OPTION_H

        /** Returns the visible viewport height for [optionCount] level dropdown rows. */
        fun levelDropdownViewportHeight(optionCount: Int): Double =
            levelDropdownContentHeight(optionCount).coerceAtMost(LEVEL_DROPDOWN_MAX_H)

        /** Builds the server request for a new world with fixed dimensions. */
        fun createWorldRequest(deleteToken: String) = NewWorldCreationServer.createWorldRequest(deleteToken)

        /** Maps create-flow failures to concise user-facing messages. */
        fun errorMessageFor(error: Throwable): String = NewWorldCreationServer.errorMessageFor(error)
    }

    /**
     * Builds the creation form and wires validation, server creation, and editor navigation.
     */
    @OptIn(KorgeExperimental::class)
    override suspend fun SContainer.sceneMain() {
        with(WorldModeUi) { drawWorldFormBackground() }
        drawNavigation()
        text(TITLE_TEXT, textSize = TITLE_TEXT_SIZE, color = Colors.BLACK, font = Assets.bitcountSingle) {
            centerXOn(this@sceneMain)
            y = TITLE_Y
        }

        levelOptions = buildLevelOptions()
        selectedLevelIndex = 0
        dropdown = null
        createInProgress = false

        with(NewWorldCreationSceneUi) {
            drawFormLabel(WORLD_NAME_LABEL, WORLD_NAME_INPUT_Y)
            worldNameInput = drawInputBox(FORM_INPUT_X, WORLD_NAME_INPUT_Y, WorldTemplate.DEFAULT_WORLD_NAME)
            drawFormLabel(LEVEL_LABEL, LEVEL_INPUT_Y)
            val levelField = drawLevelField(levelOptions[selectedLevelIndex].label)
            levelField.parent?.onClickWithSound { toggleDropdown(levelField) }
            messageText = drawMessageText()
            createButtonFill = createButtonFillForWorldName(worldNameInput.text, knownWorldsForValidation)
            errorIncoming = createButtonFill == INVALID_CREATE_BUTTON_FILL
            bottomActions = drawBottomActions(createButtonFill, ::goBackToWorldSelection, ::handleCreateClick)
        }
        addUpdater { refreshCreateButton() }
    }

    /** Updates the centered status/error text shown above the action row. */
    private fun setMessage(message: String?, color: RGBA = ERROR_TEXT_COLOR) {
        WorldModeUi.setCenteredMessage(messageText, message, color, LevelScene.TABLE_PX_W, MESSAGE_Y)
    }

    /** Repaints the create button when the current world-name validation state changes. */
    private fun refreshCreateButton(force: Boolean = false) {
        val nextFill = createButtonFillForWorldName(worldNameInput.text, knownWorldsForValidation)
        if (!force && nextFill == createButtonFill) return

        createButtonFill = nextFill
        errorIncoming = createButtonFill == INVALID_CREATE_BUTTON_FILL
        bottomActions.createButton.removeChildren()
        with(NewWorldCreationSceneUi) {
            bottomActions.createButton.drawActionButtonContent(CREATE_BUTTON_TEXT, nextFill)
        }
    }

    /** Removes the open level dropdown, if any. */
    private fun closeDropdown() {
        dropdown?.let { it.parent?.removeChild(it) }
        dropdown = null
    }

    /** Applies the selected level option to the form field. */
    private fun updateSelectedLevel(index: Int, levelField: Text) {
        selectedLevelIndex = index
        levelField.text = levelOptions[index].label
        with(NewWorldCreationSceneUi) { positionLevelFieldText(levelField) }
        closeDropdown()
    }

    /** Opens or closes the level dropdown below the level field. */
    private fun SContainer.toggleDropdown(levelField: Text) {
        if (dropdown != null) {
            closeDropdown()
            return
        }
        with(NewWorldCreationSceneUi) {
            dropdown = drawLevelDropdown(levelOptions, selectedLevelIndex) { index ->
                updateSelectedLevel(index, levelField)
            }
        }
    }

    /** Handles the create action after the player clicks the create button. */
    private fun handleCreateClick(): Boolean {
        if (createInProgress) return !errorIncoming

        val worldName = worldNameInput.text.trim()
        val localValidation = WorldModeUi.validateWorldName(worldName, knownWorldsForValidation)
        if (!localValidation.valid) {
            setMessage(localValidation.message)
            refreshCreateButton(force = true)
            return !errorIncoming
        }

        createInProgress = true
        closeDropdown()
        launch { submitCreateRequest(worldName) }
        return !errorIncoming
    }

    /** Performs final server validation and opens the editor for the created world. */
    private suspend fun submitCreateRequest(worldName: String) {
        try {
            val submitValidation = validateWorldNameForSubmit(worldName)
            if (!submitValidation.valid) {
                createInProgress = false
                setMessage(submitValidation.message)
                refreshCreateButton(force = true)
                InteractionSoundController.play(InteractionSoundEffect.ERROR)
                return
            }
            setMessage(CREATING_STATUS_TEXT, Colors.BLACK)
            createWorldAndOpenEditor(worldName, levelOptions[selectedLevelIndex].selection)
        } catch (e: Throwable) {
            createInProgress = false
            setMessage(errorMessageFor(e))
            InteractionSoundController.play(InteractionSoundEffect.ERROR)
        }
    }

    /**
     * Creates the server world, persists local metadata, loads the selected level, and opens the editor scene.
     */
    private suspend fun createWorldAndOpenEditor(worldName: String, levelSelection: WorldLevelSelection) {
        val level = levelSelection.loadLevelForWorld(worldName)
        val deleteToken = deleteTokenFactory()
        val world = NewWorldCreationServer.createServerWorldEntry(
            worldName = worldName,
            levelSelection = levelSelection,
            deleteToken = deleteToken,
            temporaryWorldIdFactory = temporaryWorldIdFactory,
            temporaryLocalCreationEnabled = temporaryLocalCreationEnabled
        )
        val session = WorldEditorSession.fromLevel(
            worldId = world.id,
            worldName = worldName,
            templateLevelId = NewWorldCreationServer.editorTemplateLevelId(levelSelection),
            isNewWorld = false,
            sourceLevel = level
        )
        Assets.preloadVehicleGraphics(session.level.vehicleTypes)
        InteractionSoundController.play(InteractionSoundEffect.SUCCESS)
        sceneContainer.changeTo {
            WorldEditorScene(
                session,
                WorldEditorBackDestination.NEW_WORLD_CREATION_SCENE,
                backExistingWorlds = existingWorlds
            )
        }
    }

    /** Refreshes server names when available and validates [worldName] against all known worlds. */
    private suspend fun validateWorldNameForSubmit(worldName: String): WorldModeUi.WorldNameValidationResult {
        when (val result = loadWorldsForValidation()) {
            is ServerWorldsResult.Success -> {
                knownWorldsForValidation = existingWorlds + result.worlds
            }
            is ServerWorldsResult.Failure -> {
                if (NewWorldCreationServer.networkConfigProvider().active) {
                    throw java.io.IOException(result.message)
                }
            }
        }
        return WorldModeUi.validateWorldName(worldName, knownWorldsForValidation)
    }

    /**
     * Adds the shared back and close controls and wires their scene transitions.
     */
    private fun SContainer.drawNavigation() {
        ControlButtons.drawBackButton(this).onClickWithSound {
            goBackToWorldSelection()
        }
        ControlButtons.drawCloseButton(this).onClickWithSound {
            launch {
                if (tutorialActivated) {
                    sceneContainer.changeTo<TutorialScene>()
                } else {
                    sceneContainer.changeTo<LevelScene>()
                }
            }
        }
    }

    /**
     * Returns to the world-selection menu without applying changes.
     */
    private fun goBackToWorldSelection() {
        launch {
            sceneContainer.changeTo { WorldSelectionScene() }
        }
    }
}
