package scenes

import domain.ChallengeMode
import domain.ConnectionState
import domain.GameState
import domain.GameState.tutorialActivated
import network.ServerConnection
import korlibs.image.color.Colors
import korlibs.io.file.std.applicationVfs
import audio.InteractionSoundController
import audio.InteractionSoundEffect
import audio.onClickWithErrorSound
import audio.onClickWithSound
import korlibs.korge.scene.Scene
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.align.centerXOn
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.text
import korlibs.korge.view.xy
import korlibs.math.geom.vector.StrokeInfo
import korlibs.time.DateFormat
import korlibs.time.DateTime
import korlibs.time.nowLocal
import kotlinx.coroutines.launch
import persistence.LevelSerializer
import scenes.LevelScene.Companion.TABLE_PX_H
import scenes.LevelScene.Companion.TABLE_PX_W
import ui.Assets
import ui.Assets.bitcountSingle
import ui.ControlButtons

/**
 * Displays the pause menu for save, load, and configuration actions.
 */
class MenuScene : Scene() {

    /**
     * Stores sizing constants for the large pause-menu action buttons.
     */
    object MenuConfig {
        /** Width of the locked popup */
        const val LOCKED_POPUP_WIDTH = 1040.0
        /** Height of the locked popup */
        const val LOCKED_POPUP_HEIGHT = 250.0
        /** Text shown in the locked popup */
        const val LOCKED_POPUP_TEXT = "Konfigurationsmenü im Challenge-Modus nicht verfügbar"
        /** Text shown on the close button of the locked popup */
        const val LOCKED_POPUP_CLOSE_TEXT = "OK"
        /** Length of the side of the square icon box */
        const val BOX_LENGTH = PauseMenuConfig.BOX_LENGTH
        /** Spacing between the icon boxes */
        const val BOX_SPACING = PauseMenuConfig.BOX_SPACING
        /** Height of a button's descriptive label field below the icon box */
        const val LABEL_HEIGHT = PauseMenuConfig.LABEL_HEIGHT
        /** Font size used for button label text */
        const val LABEL_TEXT_SIZE = PauseMenuConfig.LABEL_TEXT_SIZE
        /** Maximum character count per line before a button label wraps onto the next line */
        const val LABEL_MAX_CHARS_PER_LINE = PauseMenuConfig.LABEL_MAX_CHARS_PER_LINE
        /** Vertical spacing between wrapped lines within a button label */
        const val LABEL_LINE_HEIGHT = PauseMenuConfig.LABEL_LINE_HEIGHT
        /** Label text shown below the save button */
        const val SAVE_BUTTON_LABEL = "Level speichern"
        /** Label text shown below the level-selection button */
        const val LOAD_LEVEL_BUTTON_LABEL = "Levelauswahl"
        /** Label text shown below the vehicle/source configuration button */
        const val CONFIGURATION_BUTTON_LABEL = "Konfiguration"
        /** Label text shown below the multi-table/server button */
        const val SERVER_BUTTON_LABEL = "Multi-Tisch-Modus"
        /** Scale factor for the icons */
        const val ICON_SCALE = PauseMenuConfig.ICON_SCALE
        /** Scale factor for the car icon */
        const val CAR_ICON_SCALE = PauseMenuConfig.CAR_ICON_SCALE
        /** Thickness of the stroke around the icons */
        const val STROKE_THICKNESS = PauseMenuConfig.STROKE_THICKNESS
    }

    companion object {
        /** Message shown after a successful level save. */
        const val SAVE_SUCCESS_TEXT = "Straßennetz gespeichert"

        /** Message shown when the player tries to save an unchanged level again. */
        const val UNCHANGED_SAVE_TEXT = "Keine Änderungen seit letzter Speicherung"

        /**
         * Returns the stable serialized level state used to detect duplicate saves.
         *
         * @param level the level that should be normalized into a stable snapshot.
         * @return the normalized JSON snapshot string.
         */
        internal fun saveSnapshotFor(level: domain.Level): String {
            return LevelSerializer.toJsonString(level)
        }

        /**
         * Returns whether the active level should be written to disk for [snapshot].
         *
         * @param snapshot normalized snapshot of the active level.
         * @return true when the snapshot may be saved as a new file.
         */
        internal fun saveAllowedForSnapshot(snapshot: String): Boolean {
            return saveAllowedForSnapshot(snapshot, emptyList())
        }

        /**
         * Returns whether [snapshot] differs from the active level's last save and all known saves.
         *
         * @param snapshot normalized construction snapshot of the current level.
         * @param existingSavedSnapshots normalized construction snapshots read from saved JSON files.
         * @return true when the snapshot is not already present among known saves.
         */
        internal fun saveAllowedForSnapshot(snapshot: String, existingSavedSnapshots: Iterable<String>): Boolean {
            return GameState.canSaveCurrentLevelSnapshot(snapshot) && existingSavedSnapshots.none { it == snapshot }
        }

        /**
         * Returns whether [level] matches the last save remembered in [GameState].
         */
        internal fun levelMatchesRememberedSave(level: domain.Level): Boolean {
            return !GameState.canSaveCurrentLevelSnapshot(saveSnapshotFor(level))
        }

        /**
         * Reads all saved JSON levels and converts them into normalized construction snapshots.
         *
         * Broken or outdated save files are ignored here so one bad file does not block saving.
         *
         * @return the normalized snapshots for all readable saved levels.
         */
        internal suspend fun existingSavedLevelSnapshots(): List<String> =
            snapshotsInDirectory(SavedNetworksScene.SAVED_LEVEL_DIRECTORY)

        /**
         * Reads all saved JSON worlds and converts them into normalized construction snapshots.
         *
         * Broken or outdated save files are ignored here so one bad file does not block saving.
         *
         * @return the normalized snapshots for all readable saved worlds.
         */
        internal suspend fun existingSavedWorldSnapshots(): List<String> =
            snapshotsInDirectory(SavedNetworksScene.SAVED_WORLD_DIRECTORY)

        /**
         * Returns whether [level] already matches the last save or a save file in the current mode.
         *
         * @param level active level to compare against saved snapshots.
         * @return true when saving would not write a new file.
         */
        internal suspend fun currentLevelAlreadySaved(level: domain.Level): Boolean {
            val saveSnapshot = saveSnapshotFor(level)
            return !saveAllowedForSnapshot(saveSnapshot, existingSnapshotsForCurrentMode())
        }

        /**
         * Returns the target save directory for the current connection mode.
         */
        private fun saveDirectoryForCurrentMode(): String =
            if (ServerConnection.connectionState == ConnectionState.CONNECTED) {
                SavedNetworksScene.SAVED_WORLD_DIRECTORY
            } else {
                SavedNetworksScene.SAVED_LEVEL_DIRECTORY
            }

        /**
         * Reads comparable saved snapshots from the current save target directory.
         */
        private suspend fun existingSnapshotsForCurrentMode(): List<String> =
            if (ServerConnection.connectionState == ConnectionState.CONNECTED) {
                existingSavedWorldSnapshots()
            } else {
                existingSavedLevelSnapshots()
            }

        /**
         * Reads all saved JSON files in [directory] and converts them into normalized snapshots.
         *
         * @param directory VFS directory path to scan for `.json` save files.
         * @return normalized snapshots for all readable files; broken files are silently skipped.
         */
        private suspend fun snapshotsInDirectory(directory: String): List<String> {
            return try {
                val saveDirectory = applicationVfs[directory]
                if (!saveDirectory.exists()) return emptyList()

                saveDirectory.listSimple()
                    .filter { it.path.endsWith(".json", ignoreCase = true) }
                    .mapNotNull { file ->
                        try {
                            val level = LevelSerializer.fromJsonString(file.readString(), file.path)
                            saveSnapshotFor(level)
                        } catch (e: Exception) {
                            null
                        }
                    }
            } catch (e: Exception) {
                emptyList()
            }
        }

        /**
         * Executes the save flow used by both pause-menu save buttons without changing scenes.
         *
         * Saves to [SavedNetworksScene.SAVED_WORLD_DIRECTORY] when connected to the multi-table
         * server ([ServerConnection.connectionState] is [ConnectionState.CONNECTED]) so the
         * saved world appears in [WorldSelectionScene] rather than in [SavedNetworksScene]. Falls
         * back to [SavedNetworksScene.SAVED_LEVEL_DIRECTORY] when disconnected.
         *
         * @return the save result for the caller's inline UI feedback.
         */
        internal suspend fun saveCurrentLevel(): LevelSaveResult {
            val level = GameState.levelForSceneOrRecover("MenuScene")
                ?: return LevelSaveResult.MissingLevel

            val timestamp = DateTime.nowLocal()
            val newFormat = DateFormat("dd.MM.yyyy HH.mm")
            val timestampNewFormat = timestamp.format(newFormat)

            val saveDir = saveDirectoryForCurrentMode()
            val savePath = "$saveDir/${level.name}_$timestampNewFormat.json"
            val saveSnapshot = saveSnapshotFor(level)

            val existingSnapshots = existingSnapshotsForCurrentMode()
            if (!saveAllowedForSnapshot(saveSnapshot, existingSnapshots)) {
                return LevelSaveResult.Unchanged(level)
            }

            LevelSerializer.saveToFile(level, savePath)
            GameState.markCurrentLevelSaved(saveSnapshot)

            return LevelSaveResult.Saved(savePath, level)
        }
    }

    /**
     * Groups the main pause-menu button positions.
     *
     * @property saveButton position of the save button.
     * @property loadLevelButton position of the level-selection button.
     * @property configurationButton position of the configuration button.
     * @property serverButton position of the server button.
     * @property bounds full bounding rectangle of the row.
     */
    data class ButtonRowLayout(
        val saveButton: MenuPoint,
        val loadLevelButton: MenuPoint,
        val configurationButton: MenuPoint,
        /** Position of the server button. */
        val serverButton: MenuPoint,
        val bounds: MenuRect
    ) {
        /** List of all button positions in order. */
        val buttons: List<MenuPoint> = listOf(saveButton, loadLevelButton, configurationButton, serverButton)
        /** Descriptive labels rendered below each button, in the same order as [buttons]*/
        val labels: List<ButtonLabelLayout> = listOf(
            buttonLabelLayout(saveButton, MenuConfig.SAVE_BUTTON_LABEL),
            buttonLabelLayout(loadLevelButton, MenuConfig.LOAD_LEVEL_BUTTON_LABEL),
            buttonLabelLayout(configurationButton, MenuConfig.CONFIGURATION_BUTTON_LABEL),
            buttonLabelLayout(serverButton, MenuConfig.SERVER_BUTTON_LABEL)
        )
    }

    /**
     * Computes menu layout values used by rendering code and unit tests.
     */
    object MenuLayout {
        /**
         * Returns the centered row layout for save, load, and configuration buttons.
         *
         * The vertical position accounts for both the button boxes and the label
         * fields below them, so the whole button-plus-label block is centered on
         * the table, not just the button boxes.
         *
         * @return the button row layout with centered positions.
         */
        fun buttonRow(): ButtonRowLayout {
            val geo = pauseMenuButtonRowGeometry()
            return ButtonRowLayout(
                saveButton = MenuPoint(geo.rowX, geo.rowY),
                loadLevelButton = MenuPoint(geo.rowX + geo.step, geo.rowY),
                configurationButton = MenuPoint(geo.rowX + geo.step * 2.0, geo.rowY),
                serverButton = MenuPoint(geo.rowX + geo.step * 3.0, geo.rowY),
                bounds = MenuRect(geo.rowX, geo.rowY, geo.rowWidth, PauseMenuConfig.BOX_LENGTH)
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
        fun iconSize(iconScale: Double = MenuConfig.ICON_SCALE): Double =
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
        fun labelLines(label: String): List<String> = PauseMenuConfig.labelLines(label)
    }

    /**
     * Builds the pause menu and wires each button to its scene transition.
     *
     * @return nothing; the scene content is created for its side effects.
     */
    override suspend fun SContainer.sceneMain() {
        drawWhiteSceneBackground()
        val buttonRow = MenuLayout.buttonRow()
        var configurationLockedPopup: Container? = null

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

        val loadLevelButton = drawPauseMenuButtonWithIcon(
            buttonRow.loadLevelButton.x,
            buttonRow.loadLevelButton.y,
            Assets.loadLevel
        )

        loadLevelButton.onClickWithSound {
            launch {
                LevelMenuScene.backDestination = LevelMenuBackDestination.MENU_SCENE
                sceneContainer.pushTo<LevelMenuScene>()
            }
        }

        val configurationButton = drawPauseMenuButtonWithIcon(
            buttonRow.configurationButton.x,
            buttonRow.configurationButton.y,
            Assets.carPause,
            MenuConfig.CAR_ICON_SCALE
        )

        configurationButton.onClickWithSound {
            if (!ChallengeMode.active) {
                launch {
                    ConfigurationScene.backDestination = ConfigurationBackDestination.MENU_SCENE
                    sceneContainer.pushTo<ConfigurationScene>()
                }
            } else {
                configurationLockedPopup?.visible = true
            }
        }

        val serverButton = drawPauseMenuButtonWithIcon(
            buttonRow.serverButton.x,
            buttonRow.serverButton.y,
            Assets.globus
        )

        serverButton.onClickWithSound {
            launch{
                ServerMenuScene.backTarget = ServerMenuScene.BackTarget.MENU_SCENE
                sceneContainer.pushTo<ServerMenuScene>()
            }
        }

        buttonRow.labels.drop(1).forEach { label ->
            drawPauseMenuButtonLabel(label.bounds.x, label.bounds.y, label.text)
        }

        ControlButtons.drawCloseButton(this).onClickWithSound {
            launch {
                if(tutorialActivated) {
                    sceneContainer.changeTo<TutorialScene>()
                } else {
                    sceneContainer.changeTo<LevelScene>()
                }
            }
        }

        drawPauseMenuColorBlindnessToggle()

        configurationLockedPopup = drawConfigurationLockedPopup().apply {
            visible = false
        }
    }

    /**
     * Draws the modal message shown when challenge mode blocks configuration.
     *
     * @return a container that can be shown or hidden by the configuration button.
     */
    private fun Container.drawConfigurationLockedPopup(): Container {
        val popupX = TABLE_PX_W / 2.0 - MenuConfig.LOCKED_POPUP_WIDTH / 2.0
        val popupY = TABLE_PX_H / 2.0 - MenuConfig.LOCKED_POPUP_HEIGHT / 2.0

        return container {
            xy(popupX, popupY)
            graphics {
                fill(Colors.WHITE) {
                    rect(0.0, 0.0, MenuConfig.LOCKED_POPUP_WIDTH, MenuConfig.LOCKED_POPUP_HEIGHT)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = 3.0)) {
                    rect(0.0, 0.0, MenuConfig.LOCKED_POPUP_WIDTH, MenuConfig.LOCKED_POPUP_HEIGHT)
                }
            }
            text(MenuConfig.LOCKED_POPUP_TEXT, textSize = 32.0, color = Colors.BLACK, font = bitcountSingle) {
                centerXOn(this@container)
                y = 58.0
            }
            val closeButton = drawTextButton(
                MenuConfig.LOCKED_POPUP_WIDTH / 2.0 - 70.0,
                154.0,
                140.0,
                54.0,
                MenuConfig.LOCKED_POPUP_CLOSE_TEXT
            )
            closeButton.onClickWithSound {
                visible = false
            }
            onClickWithSound {
                visible = false
            }
        }
    }

    /**
     * Draws a bordered text action button.
     *
     * @param xPos the x-coordinate of the button's top-left corner.
     * @param yPos the y-coordinate of the button's top-left corner.
     * @param width the button width.
     * @param height the button height.
     * @param label the centered button label.
     * @return a container representing the button.
     */
    private fun Container.drawTextButton(
        xPos: Double,
        yPos: Double,
        width: Double,
        height: Double,
        label: String
    ): Container {
        return container {
            xy(xPos, yPos)
            graphics {
                fill(Colors.WHITE) {
                    rect(0.0, 0.0, width, height)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = MenuConfig.STROKE_THICKNESS)) {
                    rect(0.0, 0.0, width, height)
                }
            }
            text(label, textSize = 32.0, color = Colors.BLACK, font = bitcountSingle) {
                centerOn(this@container)
            }
        }
    }

    /**
     * Returns whether the current menu level should show the save button as already saved.
     */
    private fun currentLevelSavedInMenu(): Boolean {
        val level = GameState.currentLevel ?: return false
        return levelMatchesRememberedSave(level)
    }

    /**
     * Saves from the pause menu and updates the save button inline.
     *
     * @param saveButton save button handles to update after saving.
     */
    private suspend fun handleMenuSaveButton(saveButton: PauseMenuSaveButton) {
        when (saveCurrentLevel()) {
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
