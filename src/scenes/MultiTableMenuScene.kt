package scenes

import domain.GameState
import audio.InteractionSoundController
import audio.InteractionSoundEffect
import audio.onClickWithErrorSound
import audio.onClickWithSound
import korlibs.korge.scene.Scene
import korlibs.korge.scene.SceneContainer
import korlibs.korge.view.SContainer
import kotlinx.coroutines.launch
import ui.Assets
import ui.ControlButtons

/**
 * Displays the pause menu shown while the table is connected to the multi-table server.
 *
 * Reachable only through the gear button while [network.ServerConnection.connectionState] is
 * [domain.ConnectionState.CONNECTED] (see [LevelScene]); [MenuScene] is used otherwise, regardless
 * of which level happens to be loaded. The button layout mirrors [MenuScene] but replaces the
 * level-specific actions with world-specific ones: saving and loading operate on multi-table
 * worlds (through [WorldSelectionScene]) instead of single-player levels.
 */
class MultiTableMenuScene : Scene() {

    internal companion object {
        /**
         * Opens the world selection from the multi-table pause menu.
         *
         * @param sceneContainer active scene container to navigate in.
         */
        suspend fun openWorldSelection(sceneContainer: SceneContainer) {
            WorldSelectionScene.backDestination = WorldSelectionBackDestination.MULTI_TABLE_MENU_SCENE
            sceneContainer.pushTo<WorldSelectionScene>()
        }

        /**
         * Opens the vehicle/source configuration from the multi-table pause menu.
         *
         * @param sceneContainer active scene container to navigate in.
         */
        suspend fun openConfiguration(sceneContainer: SceneContainer) {
            ConfigurationScene.backDestination = ConfigurationBackDestination.MULTI_TABLE_MENU_SCENE
            sceneContainer.pushTo<ConfigurationScene>()
        }

        /**
         * Opens the server menu from the multi-table pause menu.
         *
         * @param sceneContainer active scene container to navigate in.
         */
        suspend fun openServerMenu(sceneContainer: SceneContainer) {
            ServerMenuScene.backTarget = ServerMenuScene.BackTarget.MULTI_TABLE_MENU_SCENE
            sceneContainer.pushTo<ServerMenuScene>()
        }

        /**
         * Closes the multi-table pause menu back to the active world scene.
         *
         * @param sceneContainer active scene container to navigate in.
         */
        suspend fun closeToWorld(sceneContainer: SceneContainer) {
            sceneContainer.changeTo<WorldScene>()
        }
    }

    /**
     * Stores sizing constants for the large pause-menu action buttons.
     *
     * Mirrors [MenuScene.MenuConfig] so both pause-menu variants look identical. Kept as its own
     * object rather than a shared one since every scene in this codebase already owns its
     * self-contained layout constants (see [ServerMenuScene.ServerMenuConfig], [WorldSelectionScene]).
     */
    object MultiTableMenuConfig {
        /** Length of the side of the square icon box. */
        val BOX_LENGTH get() = PauseMenuConfig.BOX_LENGTH
        /** Spacing between the icon boxes. */
        val BOX_SPACING get() = PauseMenuConfig.BOX_SPACING
        /** Height of a button's descriptive label field below the icon box. */
        val LABEL_HEIGHT get() = PauseMenuConfig.LABEL_HEIGHT
        /** Label text shown below the save button. */
        const val SAVE_BUTTON_LABEL = "Welt speichern"
        /** Label text shown below the world-selection button. */
        const val WORLD_SELECTION_BUTTON_LABEL = "Weltauswahl"
        /** Label text shown below the vehicle/source configuration button. */
        const val CONFIGURATION_BUTTON_LABEL = "Fahrzeug-\nkonfiguration"
        /** Label text shown below the multi-table/server button. */
        const val SERVER_BUTTON_LABEL = "Multi-Tisch-Modus"
        /** Scale factor for the icons. */
        val ICON_SCALE get() = PauseMenuConfig.ICON_SCALE
        /** Scale factor for the car icon. */
        val CAR_ICON_SCALE get() = PauseMenuConfig.CAR_ICON_SCALE
        /** Thickness of the stroke around the icons. */
        val STROKE_THICKNESS get() = PauseMenuConfig.STROKE_THICKNESS
    }

    /**
     * Groups the main pause-menu button positions.
     *
     * @property saveButton position of the save button.
     * @property worldSelectionButton position of the world-selection button.
     * @property configurationButton position of the configuration button.
     * @property serverButton position of the server button.
     * @property bounds full bounding rectangle of the row.
     */
    data class ButtonRowLayout(
        val saveButton: MenuPoint,
        val worldSelectionButton: MenuPoint,
        val configurationButton: MenuPoint,
        val serverButton: MenuPoint,
        val bounds: MenuRect
    ) {
        /** Descriptive labels rendered below each button, in the same order as button fields. */
        val labels: List<ButtonLabelLayout> = listOf(
            buttonLabelLayout(saveButton, MultiTableMenuConfig.SAVE_BUTTON_LABEL),
            buttonLabelLayout(worldSelectionButton, MultiTableMenuConfig.WORLD_SELECTION_BUTTON_LABEL),
            buttonLabelLayout(configurationButton, MultiTableMenuConfig.CONFIGURATION_BUTTON_LABEL),
            buttonLabelLayout(serverButton, MultiTableMenuConfig.SERVER_BUTTON_LABEL)
        )
        /** List of all button positions in order. */
        val buttons = listOf(saveButton, worldSelectionButton, configurationButton, serverButton)
    }

    /**
     * Computes menu layout values used by rendering code and unit tests.
     */
    object MenuLayout {
        /**
         * Returns the centered row layout for save, world-selection, and configuration buttons.
         *
         * @return the button row layout with centered positions.
         */
        fun buttonRow(): ButtonRowLayout {
            val (rowX, rowY, rowWidth, step) = pauseMenuButtonRowGeometry()
            return ButtonRowLayout(
                saveButton = MenuPoint(rowX, rowY),
                worldSelectionButton = MenuPoint(rowX + step, rowY),
                configurationButton = MenuPoint(rowX + step * 2.0, rowY),
                serverButton = MenuPoint(rowX + step * 3.0, rowY),
                bounds = MenuRect(rowX, rowY, rowWidth, PauseMenuConfig.BOX_LENGTH)
            )
        }

        /**
         * Returns the top-right close button position shared with other menu scenes.
         *
         * @return the top-right close button position.
         */
        fun closeButton(): MenuPoint = pauseMenuCloseButtonPosition()

        /**
         * Returns the icon size for a large square menu button.
         *
         * @param iconScale scale factor relative to the standard square menu button size.
         * @return the icon size in pixels.
         */
        fun iconSize(iconScale: Double = MultiTableMenuConfig.ICON_SCALE): Double =
            PauseMenuConfig.iconSize(iconScale)

        /**
         * Wraps a menu label into short lines that fit into the fixed-width label field.
         *
         * Existing explicit line breaks are preserved. Without them, wrapping prefers natural
         * separators in German compound labels such as spaces, hyphens, and slashes.
         *
         * @param label unwrapped label text.
         * @return one or more lines for centered rendering.
         */
        fun labelLines(label: String) = PauseMenuConfig.labelLines(label)
    }

    /**
     * Builds the multi-table pause menu and wires each button to its scene transition.
     *
     * @return nothing; the scene content is created for its side effects.
     */
    override suspend fun SContainer.sceneMain() {
        drawWhiteSceneBackground()
        val buttonRow = MenuLayout.buttonRow()
        drawSaveButton(buttonRow)
        drawWorldSelectionButton(buttonRow)
        drawConfigurationButton(buttonRow)
        drawServerButton(buttonRow)
        buttonRow.labels.drop(1).forEach { label ->
            drawPauseMenuButtonLabel(label.bounds.x, label.bounds.y, label.text)
        }
        ControlButtons.drawCloseButton(this).onClickWithSound {
            launch { closeToWorld(sceneContainer) }
        }
        drawPauseMenuColorBlindnessToggle()
    }

    /**
     * Draws the save button and wires it to save the current level.
     *
     * @param buttonRow layout providing the save button position.
     */
    private fun SContainer.drawSaveButton(buttonRow: ButtonRowLayout) {
        val saveButton = drawPauseMenuSaveButton(
            buttonRow.saveButton,
            buttonRow.labels.first(),
            Assets.save,
            initiallySaved = currentLevelSavedInMenu()
        )
        saveButton.root.onClickWithErrorSound {
            saveButton.setSaved(currentLevelSavedInMenu())
            if (saveButton.levelSaved) {
                false
            } else {
                launch { handleMenuSaveButton(saveButton) }
                true
            }
        }
    }

    /**
     * Draws the world-selection button and navigates to [WorldSelectionScene] on click.
     *
     * Sets [WorldSelectionScene.backDestination] before navigating so that the back button
     * in [WorldSelectionScene] returns to the active game instead of looping through this menu.
     *
     * @param buttonRow layout providing the world-selection button position.
     */
    private fun SContainer.drawWorldSelectionButton(buttonRow: ButtonRowLayout) {
        drawPauseMenuButtonWithIcon(
            buttonRow.worldSelectionButton.x, buttonRow.worldSelectionButton.y, Assets.loadLevel
        ).onClickWithSound {
            launch { openWorldSelection(sceneContainer) }
        }
    }

    /**
     * Draws the vehicle-configuration button and navigates to [ConfigurationScene] on click.
     *
     * @param buttonRow layout providing the configuration button position.
     */
    private fun SContainer.drawConfigurationButton(buttonRow: ButtonRowLayout) {
        drawPauseMenuButtonWithIcon(
            buttonRow.configurationButton.x, buttonRow.configurationButton.y,
            Assets.carPause, MultiTableMenuConfig.CAR_ICON_SCALE
        ).onClickWithSound { launch { openConfiguration(sceneContainer) } }
    }

    /**
     * Draws the multi-table server button and navigates to [ServerMenuScene] on click.
     *
     * @param buttonRow layout providing the server button position.
     */
    private fun SContainer.drawServerButton(buttonRow: ButtonRowLayout) {
        drawPauseMenuButtonWithIcon(buttonRow.serverButton.x, buttonRow.serverButton.y, Assets.globus)
            .onClickWithSound { launch { openServerMenu(sceneContainer) } }
    }

    /**
     * Returns whether the current multi-table level should show the save button as already saved.
     */
    private fun currentLevelSavedInMenu(): Boolean {
        val level = GameState.currentLevel ?: return false
        return MenuScene.levelMatchesRememberedSave(level)
    }

    /**
     * Saves from the multi-table pause menu and updates the save button inline.
     *
     * @param saveButton save button handles to update after saving.
     */
    private suspend fun handleMenuSaveButton(saveButton: PauseMenuSaveButton) {
        when (MenuScene.saveCurrentLevel()) {
            is LevelSaveResult.Saved,
            is LevelSaveResult.Unchanged -> {
                saveButton.markSaved()
                InteractionSoundController.play(InteractionSoundEffect.SUCCESS)
            }
            LevelSaveResult.MissingLevel -> {
                InteractionSoundController.play(InteractionSoundEffect.ERROR)
                sceneContainer.recoverToLevelSelection()
            }
        }
    }
}
