package scenes

import korlibs.image.color.RGBA
import audio.onClickWithErrorSound
import audio.onClickWithResultSound
import audio.onClickWithSound
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.Text
import korlibs.korge.view.addUpdater

/** Draws the bottom action row for [WorldEditScene] and keeps validation colors in sync. */
internal object WorldEditSceneActions {
    /** Draws cancel, editor, and save actions and wires them to validation-aware callbacks. */
    fun drawBottomActions(
        container: SContainer, worldName: () -> String, messageText: Text,
        knownWorlds: () -> List<WorldSelectionScene.WorldEntry>, currentWorldId: String?,
        onCancel: () -> Unit,
        onOpenEditor: (String, (String?) -> Unit, (Boolean) -> Unit) -> Unit,
        onSave: (String, (String?) -> Unit, (Boolean) -> Unit) -> Unit
    ) {
        val startX = WorldEditScene.actionRowX()

        with(WorldEditSceneUi) {
            container.drawActionButton(startX, WorldEditScene.CANCEL_BUTTON_TEXT)
        }.onClickWithSound { onCancel() }

        val actionButtons = drawValidationActionButtons(container, startX, worldName, knownWorlds, currentWorldId)
        val showError: (String?) -> Unit = { message -> messageText.showBottomActionError(message) }
        val refreshValidationButtons: (Boolean) -> Unit = { force -> actionButtons.refresh(force) }
        val isWorldNameValid = {
            WorldEditScene.isSaveWorldNameValid(worldName(), knownWorlds(), currentWorldId)
        }
        val validationError = {
            WorldEditScene.validationErrorForWorldName(worldName(), knownWorlds(), currentWorldId)
        }
        actionButtons.editorButton.container.bindEditorAction(worldName, showError, refreshValidationButtons, isWorldNameValid, onOpenEditor)
        actionButtons.saveButton.container.bindSaveAction(worldName, showError, refreshValidationButtons, isWorldNameValid, validationError, onSave)

        container.addUpdater {
            actionButtons.refresh(false)
        }
    }

    /** Wires the editor button with validation-aware feedback. */
    private fun Container.bindEditorAction(
        worldName: () -> String,
        showError: (String?) -> Unit,
        refreshValidationButtons: (Boolean) -> Unit,
        isWorldNameValid: () -> Boolean,
        onOpenEditor: (String, (String?) -> Unit, (Boolean) -> Unit) -> Unit
    ) = onClickWithResultSound {
        val valid = isWorldNameValid()
        onOpenEditor(worldName(), showError, refreshValidationButtons)
        valid
    }

    /** Wires the save button with immediate error feedback for invalid names. */
    private fun Container.bindSaveAction(
        worldName: () -> String,
        showError: (String?) -> Unit,
        refreshValidationButtons: (Boolean) -> Unit,
        isWorldNameValid: () -> Boolean,
        validationError: () -> String?,
        onSave: (String, (String?) -> Unit, (Boolean) -> Unit) -> Unit
    ) = onClickWithErrorSound {
        if (!isWorldNameValid()) {
            showError(validationError())
            refreshValidationButtons(true)
            false
        } else {
            onSave(worldName(), showError, refreshValidationButtons)
            true
        }
    }

    /** Shows validation and loading errors above the bottom action row. */
    private fun Text.showBottomActionError(message: String?) {
        WorldModeUi.setCenteredMessage(
            this,
            message,
            WorldEditScene.ERROR_TEXT_COLOR,
            LevelScene.TABLE_PX_W,
            WorldModeUi.MESSAGE_Y
        )
    }

    /** Draws the validation-aware editor and save buttons. */
    private fun drawValidationActionButtons(
        container: SContainer,
        startX: Double,
        worldName: () -> String,
        knownWorlds: () -> List<WorldSelectionScene.WorldEntry>,
        currentWorldId: String?
    ): WorldEditActionButtons {
        val editorButton = drawValidationActionButton(
            container,
            startX + WorldModeUi.ACTION_BUTTON_W + WorldEditScene.ACTION_BUTTON_GAP,
            WorldEditScene.WORLD_EDITOR_BUTTON_TEXT
        ) {
            WorldEditScene.worldEditorButtonFillForName(worldName(), knownWorlds(), currentWorldId)
        }
        val saveButton = drawValidationActionButton(
            container,
            startX + (WorldModeUi.ACTION_BUTTON_W + WorldEditScene.ACTION_BUTTON_GAP) * 2.0,
            WorldEditScene.SAVE_BUTTON_TEXT
        ) {
            WorldEditScene.saveButtonFillForName(worldName(), knownWorlds(), currentWorldId)
        }
        return WorldEditActionButtons(editorButton, saveButton)
    }

    /** Draws one action button whose fill changes with world-name validation. */
    private fun drawValidationActionButton(
        container: SContainer,
        xPos: Double,
        label: String,
        fillProvider: () -> RGBA
    ): ValidationAwareActionButton {
        val initialFill = fillProvider()
        val buttonContainer = with(WorldEditSceneUi) {
            container.drawActionButton(xPos, label, initialFill)
        }
        return ValidationAwareActionButton(buttonContainer, label, fillProvider, initialFill)
    }

    /**
     * Pair of validation-aware action buttons in the world-edit footer.
     *
     * @property editorButton button that opens the editable world in [WorldEditorScene].
     * @property saveButton button that persists only the edited world metadata.
     */
    private data class WorldEditActionButtons(
        val editorButton: ValidationAwareActionButton,
        val saveButton: ValidationAwareActionButton
    ) {
        /** Refreshes both action-button fill colors. */
        fun refresh(force: Boolean) {
            editorButton.refresh(force)
            saveButton.refresh(force)
        }

    }

    /**
     * Button handle that repaints its Korge content when the validation fill changes.
     *
     * @property container Korge container holding the visible button background and label.
     * @property label button label redrawn whenever the container content is refreshed.
     * @property fillProvider computes the current validation-dependent fill color.
     * @property fillColor last fill color painted into [container].
     */
    private data class ValidationAwareActionButton(
        val container: Container,
        val label: String,
        val fillProvider: () -> RGBA,
        var fillColor: RGBA
    ) {
        /** Repaints the button when [fillProvider] returns a new color or [force] is true. */
        fun refresh(force: Boolean) {
            val nextFill = fillProvider()
            if (!force && nextFill == fillColor) return

            fillColor = nextFill
            container.removeChildren()
            with(WorldEditSceneUi) {
                container.drawActionButtonContent(label, nextFill)
            }
        }
    }
}
