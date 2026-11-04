package scenes

import config.ConfigManager
import domain.ConnectionState
import domain.GameState.tutorialActivated
import audio.InteractionSoundController
import audio.InteractionSoundEffect
import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.annotations.KorgeExperimental
import audio.onClickWithSound
import korlibs.korge.scene.Scene
import korlibs.korge.view.SContainer
import korlibs.korge.view.align.centerXOn
import korlibs.korge.view.text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import network.ServerConnection
import persistence.LevelSerializer
import persistence.PredefinedLevelCatalog
import persistence.WorldLevelSelection
import persistence.loadLevelForWorld
import ui.Assets
import ui.ControlButtons
import worldeditor.WorldEditorRepository
import worldeditor.WorldEditorSession
import java.io.IOException

/**
 * Lets the player edit metadata for an existing multi-table world before opening the world editor.
 *
 * @property world selected world from [WorldSelectionScene].
 */
class WorldEditScene(
    private val world: WorldSelectionScene.WorldEntry,
    private val existingWorlds: List<WorldSelectionScene.WorldEntry> = emptyList()
) : Scene() {
    private var knownWorldsForValidation: List<WorldSelectionScene.WorldEntry> = existingWorlds

    /**
     * Layout and copy used by the world-edit form.
     */
    companion object {
        /** Title shown at the top of the edit scene. */
        const val TITLE_TEXT = "Welt bearbeiten"

        /** Label for the world-name field. */
        const val WORLD_NAME_LABEL = "Weltname:"

        /** Label for the level field. */
        const val LEVEL_LABEL = "Level:"

        /** Text on the cancel action. */
        const val CANCEL_BUTTON_TEXT = "Abbrechen"

        /** Text on the action that opens the level editor. */
        const val WORLD_EDITOR_BUTTON_TEXT = "Welt-Editor"

        /** Text on the save action. */
        const val SAVE_BUTTON_TEXT = "Speichern"

        /** Left x-position of the form labels, matching the mockup. */
        const val FORM_LABEL_X = 130.0

        /** Left x-position of the form input boxes, matching the mockup. */
        const val FORM_INPUT_X = 438.0

        /** Top y-position of the world-name input row. */
        const val WORLD_NAME_INPUT_Y = 235.0

        /** Top y-position of the level input row. */
        const val LEVEL_INPUT_Y = 332.0

        /** Horizontal gap between the bottom action buttons. */
        const val ACTION_BUTTON_GAP = 50.0

        /** Fill color used to highlight validation-aware action buttons when the world name is invalid. */
        val INVALID_SAVE_BUTTON_FILL: RGBA = RGBA(255, 50, 50)

        /** Fill color used for a valid save action. */
        val SAVE_BUTTON_FILL: RGBA = Colors.WHITE

        /** Fill color used for a valid editor action. */
        val WORLD_EDITOR_BUTTON_FILL: RGBA = Colors.WHITE

        /** Short delay so the successful save sound can start before leaving the scene. */
        const val SAVE_SUCCESS_SCENE_CHANGE_DELAY_MILLIS = 200L

        /** Error shown when the required world name is missing. */
        const val EMPTY_WORLD_NAME_ERROR = WorldModeUi.BLANK_WORLD_NAME_ERROR

        /** Error shown when the entered world name already exists. */
        const val DUPLICATE_WORLD_NAME_ERROR = WorldModeUi.DUPLICATE_WORLD_NAME_ERROR

        /** Corner radius for the three bottom action buttons. */
        const val ACTION_BUTTON_RADIUS = 10.0

        /** Error text color. */
        val ERROR_TEXT_COLOR: RGBA = Colors["#B00020"]

        /** Returns the non-editable level label shown for a placeholder world. */
        fun levelLabelFor(world: WorldSelectionScene.WorldEntry): String {
            return when (val selection = world.levelSelection) {
                WorldLevelSelection.StandardTemplate -> "Standard-Welt"
                is WorldLevelSelection.PredefinedLevel -> runCatching {
                    PredefinedLevelCatalog.entryFor(selection.levelId).title
                }.getOrDefault("Level ${selection.levelId}")
                is WorldLevelSelection.SavedLevel -> "Gespeichert: ${SavedNetworksScene.savedLevelDisplayName(selection.path)}"
                null -> runCatching { PredefinedLevelCatalog.entryFor(world.number).title }
                    .getOrDefault("Unbekannt")
            }
        }

        /**
         * Returns the left x-position for the centered bottom action row.
         */
        fun actionRowX(): Double {
            val rowWidth = WorldModeUi.ACTION_BUTTON_W * 3.0 + ACTION_BUTTON_GAP * 2.0
            return LevelScene.TABLE_PX_W / 2.0 - rowWidth / 2.0
        }

        /**
         * Returns whether the save action may accept the current world-name input.
         */
        fun isSaveWorldNameValid(
            name: String,
            existingWorlds: List<WorldSelectionScene.WorldEntry> = emptyList(),
            currentWorldId: String? = null
        ): Boolean = WorldModeUi.validateWorldName(name, existingWorlds, currentWorldId).valid

        /** Returns the validation error for [name], if one should be shown. */
        fun validationErrorForWorldName(
            name: String,
            existingWorlds: List<WorldSelectionScene.WorldEntry> = emptyList(),
            currentWorldId: String? = null
        ): String? = WorldModeUi.validateWorldName(name, existingWorlds, currentWorldId).message

        /**
         * Returns the save-button fill color for the current world-name input.
         */
        fun saveButtonFillForName(
            name: String,
            existingWorlds: List<WorldSelectionScene.WorldEntry> = emptyList(),
            currentWorldId: String? = null
        ): RGBA =
            WorldModeUi.buttonFillForValidation(
                WorldModeUi.validateWorldName(name, existingWorlds, currentWorldId),
                SAVE_BUTTON_FILL,
                INVALID_SAVE_BUTTON_FILL
            )

        /**
         * Returns the world-editor button fill color for the current world-name input.
         */
        fun worldEditorButtonFillForName(
            name: String,
            existingWorlds: List<WorldSelectionScene.WorldEntry> = emptyList(),
            currentWorldId: String? = null
        ): RGBA =
            WorldModeUi.buttonFillForValidation(
                WorldModeUi.validateWorldName(name, existingWorlds, currentWorldId),
                WORLD_EDITOR_BUTTON_FILL,
                INVALID_SAVE_BUTTON_FILL
            )
    }

    /**
     * Builds the world-edit form and wires cancel, editor, and save actions.
     */
    @OptIn(KorgeExperimental::class)
    override suspend fun SContainer.sceneMain() {
        with(WorldModeUi) { drawWorldFormBackground() }
        drawNavigation()

        text(TITLE_TEXT, textSize = WorldModeUi.TITLE_TEXT_SIZE, color = Colors.BLACK, font = Assets.bitcountSingle) {
            centerXOn(this@sceneMain)
            y = WorldModeUi.TITLE_Y
        }

        val worldNameInput = with(WorldEditSceneUi) {
            drawFormLabel(WORLD_NAME_LABEL, WORLD_NAME_INPUT_Y)
            val input = drawInputBox(FORM_INPUT_X, WORLD_NAME_INPUT_Y, world.name, editable = true)
            drawFormLabel(LEVEL_LABEL, LEVEL_INPUT_Y)
            drawInputBox(FORM_INPUT_X, LEVEL_INPUT_Y, levelLabelFor(world), editable = false)
            input
        }
        val messageText = with(WorldEditSceneUi) { drawMessageText() }
        WorldEditSceneActions.drawBottomActions(
            container = this,
            worldName = { worldNameInput.text },
            messageText = messageText,
            knownWorlds = { knownWorldsForValidation },
            currentWorldId = world.id,
            onCancel = ::goBackToWorldSelection,
            onOpenEditor = ::openWorldEditor,
            onSave = { name, showError, refreshValidationButtons ->
                launch { saveWorldName(name, showError, refreshValidationButtons) }
            }
        )
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

    /** Validates and persists the edited world name. */
    private suspend fun saveWorldName(
        currentWorldName: String,
        showError: (String?) -> Unit,
        refreshValidationButtons: (Boolean) -> Unit
    ) {
        val validation = validateWorldNameForSave(currentWorldName, showError, refreshValidationButtons) ?: return
        if (!validation.valid) {
            showError(validation.message)
            refreshValidationButtons(true)
            InteractionSoundController.play(InteractionSoundEffect.ERROR)
            return
        }

        if (WorldSelectionScene.saveWorldName(world, currentWorldName)) {
            showError(null)
            InteractionSoundController.play(InteractionSoundEffect.SUCCESS)
            delay(SAVE_SUCCESS_SCENE_CHANGE_DELAY_MILLIS)
            sceneContainer.changeTo { WorldSelectionScene() }
        } else {
            showError(null)
            refreshValidationButtons(true)
            InteractionSoundController.play(InteractionSoundEffect.ERROR)
        }
    }

    /**
     * Validates the edited name for the save action and reports transient validation failures.
     *
     * Server-name refresh can fail while validating; callers get null in that case because the error
     * has already been shown and the button state has already been refreshed.
     */
    private suspend fun validateWorldNameForSave(
        currentWorldName: String,
        showError: (String?) -> Unit,
        refreshValidationButtons: (Boolean) -> Unit
    ): WorldModeUi.WorldNameValidationResult? {
        return try {
            validateWorldNameForSubmit(currentWorldName)
        } catch (e: Throwable) {
            showError(NewWorldCreationScene.errorMessageFor(e))
            refreshValidationButtons(true)
            InteractionSoundController.play(InteractionSoundEffect.ERROR)
            null
        }
    }

    /**
     * Loads the selected world's editable level and opens the construction scene for it.
     */
    private fun openWorldEditor(
        currentWorldName: String,
        showError: (String?) -> Unit,
        refreshValidationButtons: (Boolean) -> Unit
    ) {
        launch {
            val validation = try {
                validateWorldNameForSubmit(currentWorldName)
            } catch (e: Throwable) {
                showError(NewWorldCreationScene.errorMessageFor(e))
                refreshValidationButtons(true)
                return@launch
            }
            if (!validation.valid) {
                showError(validation.message)
                refreshValidationButtons(true)
                return@launch
            }

            openValidatedWorldEditor(currentWorldName, showError)
        }
    }

    /** Opens the selected world editor after world-name validation already succeeded. */
    private suspend fun openValidatedWorldEditor(
        currentWorldName: String,
        showError: (String?) -> Unit
    ) {
        if (ServerConnection.connectionState != ConnectionState.CONNECTED) {
            openDisconnectedWorldEditor(currentWorldName, showError)
            return
        }

        try {
            val state = WorldEditorRepository.gateway.getWorldEditorState(world.id)
            openEditorSession(WorldEditorSession.fromServerState(state), currentWorldName)
        } catch (e: Exception) {
            openLocalEditorPreview(currentWorldName, e, showError)
        }
    }

    /** Opens a local editor preview when the table connection is missing but local data is available. */
    private suspend fun openDisconnectedWorldEditor(
        currentWorldName: String,
        showError: (String?) -> Unit
    ) {
        if (!WorldEditSceneSupport.hasLocalEditorPreview(world)) {
            showError("Der Welt-Editor ist nur bei verbundener Tisch-Verbindung verfuegbar.")
            return
        }

        openLocalEditorPreview(
            currentWorldName = currentWorldName,
            cause = IOException("Lokale Testwelt"),
            showError = showError
        )
    }

    /** Refreshes server names when available and validates [worldName] against all known worlds. */
    private suspend fun validateWorldNameForSubmit(worldName: String): WorldModeUi.WorldNameValidationResult {
        val localValidation = WorldModeUi.validateWorldName(worldName, knownWorldsForValidation, world.id)
        if (!localValidation.valid) return localValidation

        if (ConfigManager.loadNetworkConfig().active) {
            when (val result = WorldSelectionServerLoader.loadWorlds()) {
                is ServerWorldsResult.Success -> knownWorldsForValidation = existingWorlds + result.worlds
                is ServerWorldsResult.Failure -> throw IOException(result.message)
            }
        }
        return WorldModeUi.validateWorldName(worldName, knownWorldsForValidation, world.id)
    }

    /**
     * Opens a local level preview when the server editor-state endpoint is not available yet.
     *
     * This keeps locally known worlds editable even if the server cannot provide an editor-state
     * payload, but refuses unknown server-only worlds because no level template is available.
     */
    private suspend fun openLocalEditorPreview(
        currentWorldName: String,
        cause: Exception,
        showError: (String?) -> Unit
    ) {
        // The locally saved editor state is newer than the world's original template selection,
        // so it must win — otherwise saved edits would silently disappear. It also keeps worlds
        // editable whose creation metadata is no longer known (e.g. after an app restart).
        val localEditorLevel = persistence.EditorWorldSaves.loadLevelOrNull(world.id)
        if (localEditorLevel == null &&
            world.levelSelection == null &&
            world.savedLevelPath == null &&
            world.number !in 1..LevelMenuScene.MAX_PREDEFINED_LEVELS
        ) {
            showError("Welt konnte nicht geladen werden: ${cause.message ?: "Server nicht erreichbar"}")
            return
        }

        try {
            openLocalEditorPreviewSession(currentWorldName, localEditorLevel)
        } catch (previewError: Exception) {
            showError("Welt konnte nicht geladen werden: ${cause.message ?: previewError.message ?: "Server nicht erreichbar"}")
        }
    }

    /** Builds the editor session for a local preview and opens [WorldEditorScene] with it. */
    private suspend fun openLocalEditorPreviewSession(currentWorldName: String, localEditorLevel: domain.Level?) {
        val displayName = currentWorldName.trim().ifEmpty { world.name }
        val level = localEditorLevel
            ?: world.savedLevelPath?.let { LevelSerializer.loadFromAppVfs(it) }
            ?: world.levelSelection?.loadLevelForWorld(displayName)
            ?: LevelSerializer.loadFromResources(WorldSelectionScene.editableWorldLevelPath(world))
        val session = WorldEditorSession.fromLevel(
            worldId = world.id,
            worldName = displayName,
            templateLevelId = WorldEditSceneSupport.editorTemplateLevelId(world),
            isNewWorld = false,
            sourceLevel = level
        )
        Assets.preloadVehicleGraphics(session.level.vehicleTypes)
        sceneContainer.changeTo {
            WorldEditorScene(
                session,
                WorldEditorBackDestination.WORLD_EDIT_SCENE,
                backWorld = world
            )
        }
    }

    /** Applies the edited display name and opens [WorldEditorScene]. */
    private suspend fun openEditorSession(session: WorldEditorSession, currentWorldName: String) {
        session.worldName = currentWorldName.trim().ifEmpty { world.name }
        Assets.preloadVehicleGraphics(session.level.vehicleTypes)
        sceneContainer.changeTo {
            WorldEditorScene(
                session,
                WorldEditorBackDestination.WORLD_EDIT_SCENE,
                backWorld = world
            )
        }
    }

    /**
     * Returns to the world-selection menu without applying additional changes.
     */
    private fun goBackToWorldSelection() {
        launch {
            sceneContainer.changeTo { WorldSelectionScene() }
        }
    }

}
