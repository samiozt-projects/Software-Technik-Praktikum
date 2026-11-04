package scenes

import domain.GameState
import domain.GameState.tutorialActivated
import domain.SimulationState
import korlibs.image.color.Colors
import korlibs.io.file.baseName
import korlibs.io.file.std.applicationVfs
import audio.onClickWithSound
import korlibs.korge.scene.Scene
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.image
import korlibs.korge.view.position
import korlibs.korge.view.size
import korlibs.korge.view.text
import korlibs.math.geom.vector.StrokeInfo
import kotlinx.coroutines.launch
import persistence.LevelSerializer
import persistence.WorldMetadataStore
import ui.Assets
import ui.Assets.bitcountSingle
import ui.ControlButtons
import ui.Scrollbar
import ui.attachScrollbarInput

/**
 * Displays user-saved road networks and lets the player load or delete them.
 */
class SavedNetworksScene : Scene() {

    companion object {
        /** Visual border thickness used for card-like buttons. */
        const val CARD_STROKE_THICKNESS = 1.5

        /** Directory that contains saved user networks. */
        const val SAVED_LEVEL_DIRECTORY = "savedlevel"

        /** Directory that contains worlds saved while connected to the multi-table server. */
        const val SAVED_WORLD_DIRECTORY = "savedworld"

        /** Width of one saved-network list row. */
        const val LIST_ITEM_WIDTH = 900.0

        /** Height of one saved-network list row. */
        const val LIST_ITEM_HEIGHT = 70.0

        /** Gap between saved-network list rows. */
        const val LIST_GAP = 12.0

        /** Left position of the saved-network list. */
        const val LIST_X = 430.0

        /** Top position of the saved-network list. */
        const val LIST_START_Y = 220.0

        /** Height of the fixed header band that visually clips scrolling rows below the heading. */
        const val HEADER_BAND_H = LIST_START_Y

        /** Heading text shown above the saved-network list. */
        const val HEADING_TEXT = "Gespeicherte Netze"

        /** Vertical position of the saved-network menu heading. */
        const val HEADING_Y = 56.0

        /** Size of the delete icon shown at the end of each row. */
        const val DELETE_ICON_SIZE = 42.0

        /** Padding between the delete icon and the row edge. */
        const val DELETE_ICON_PADDING = 18.0

        /** Width of the load-click area before the delete icon. */
        const val LOAD_AREA_WIDTH = LIST_ITEM_WIDTH - DELETE_ICON_SIZE - DELETE_ICON_PADDING * 3.0

        /** Last vertical scroll offset, remembered so the list keeps its position across reloads. */
        var SCROLL_Y = 0.0

        /** Mouse wheel scroll multiplier. */
        const val SCROLL_SPEED = 20.0

        /** Horizontal position of the saved-network scrollbar. */
        const val SCROLLBAR_X = LIST_X + LIST_ITEM_WIDTH + 34.0

        /** Height of the saved-network scrollbar, matching the visible list area. */
        val SCROLLBAR_HEIGHT = LevelScene.TABLE_PX_H - LIST_START_Y - 30.0

        /** Height of the content area the saved-network list scrolls within. */
        val VIEWPORT_HEIGHT = LevelScene.TABLE_PX_H - LIST_START_Y

        /**
         * Builds the application VFS path for one saved-level file.
         *
         * @param fileName the file name inside [SAVED_LEVEL_DIRECTORY].
         */
        fun savedLevelPath(fileName: String): String = "$SAVED_LEVEL_DIRECTORY/$fileName"

        /**
         * Builds the application VFS path for one saved-world file.
         *
         * @param fileName the file name inside [SAVED_WORLD_DIRECTORY].
         */
        fun savedWorldPath(fileName: String): String = "$SAVED_WORLD_DIRECTORY/$fileName"

        /**
         * Filters saved-level file names to JSON files and sorts them newest-first by timestamp.
         *
         * Files without a parseable timestamp are kept after timestamped saves and sorted by name.
         *
         * @param fileNames file names read from the saved-level directory.
         */
        fun sortedSavedLevelJsonFiles(fileNames: List<String>): List<String> {
            return fileNames
                .filter { it.endsWith(".json", ignoreCase = true) }
                .sortedWith(
                    compareByDescending<String> { savedLevelTimestampSortKey(it) ?: "" }
                        .thenBy { savedLevelBaseName(it).lowercase() }
                        .thenBy { it.lowercase() }
                )
        }

        /**
         * Converts a saved-level path into the display name used in the menu.
         *
         * File names are saved as "Level name_dd.MM.yyyy HH.mm.json". The menu displays that as
         * "Level name (dd.MM.yyyy HH:mm)" so the level name and save time are easier to scan.
         *
         * @param levelPath the saved-level path including directory and file extension.
         */
        fun savedLevelDisplayName(levelPath: String): String {
            val fileName = levelPath.substringAfterLast("/").substringBeforeLast(".")
            val separatorIndex = fileName.lastIndexOf("_")
            if (separatorIndex <= 0 || separatorIndex == fileName.lastIndex) return fileName

            val levelName = fileName.substring(0, separatorIndex)
            val timestamp = formatTimestampForDisplay(fileName.substring(separatorIndex + 1))
            return "$levelName ($timestamp)"
        }

        /**
         * Converts the filename-safe saved timestamp to a user-facing timestamp.
         *
         * @param timestamp timestamp in the saved filename, usually formatted as "dd.MM.yyyy HH.mm".
         */
        fun formatTimestampForDisplay(timestamp: String): String {
            val timeSeparatorIndex = timestamp.lastIndexOf(" ")
            if (timeSeparatorIndex < 0 || timeSeparatorIndex == timestamp.lastIndex) return timestamp

            val date = timestamp.substring(0, timeSeparatorIndex)
            val time = timestamp.substring(timeSeparatorIndex + 1).replace('.', ':')
            return "$date $time"
        }

        /**
         * Extracts a sortable timestamp from a saved-level file name.
         *
         * @param fileName saved-level file name, usually "Level name_dd.MM.yyyy HH.mm.json".
         */
        private fun savedLevelTimestampSortKey(fileName: String): String? {
            val timestamp = savedLevelBaseName(fileName).substringAfterLast("_", missingDelimiterValue = "")
            val parts = timestamp.split(" ", limit = 2)
            if (parts.size != 2) return null

            val dateParts = parts[0].split(".")
            val timeParts = parts[1].split(".")
            if (dateParts.size != 3 || timeParts.size != 2) return null

            val (day, month, year) = dateParts
            val (hour, minute) = timeParts
            if (day.length != 2 || month.length != 2 || year.length != 4 || hour.length != 2 || minute.length != 2) {
                return null
            }

            return "$year$month$day$hour$minute"
        }

        /**
         * Removes the JSON extension from a saved-level file name.
         *
         * @param fileName saved-level file name with or without extension.
         */
        private fun savedLevelBaseName(fileName: String): String =
            fileName.substringBeforeLast(".")

        /**
         * Returns the bottom edge of the saved-network content for a given row count.
         *
         * @param itemCount number of saved-network rows in the list.
         */
        fun savedNetworkContentBottom(itemCount: Int): Double {
            if (itemCount <= 0) return LIST_START_Y
            return LIST_START_Y + itemCount * LIST_ITEM_HEIGHT + (itemCount - 1) * LIST_GAP
        }

        /**
         * Returns the total scrollable height of the saved-network list below [LIST_START_Y].
         *
         * @param itemCount number of saved-network rows in the list.
         */
        fun savedNetworkContentHeight(itemCount: Int): Double =
            savedNetworkContentBottom(itemCount) - LIST_START_Y

        /**
         * Reads all saved-network paths from [SAVED_LEVEL_DIRECTORY], newest first.
         *
         * @return saved-network paths, or an empty list when the directory is missing or unreadable.
         */
        suspend fun savedLevelPaths(): List<String> =
            listSavedFilesInDirectory(SAVED_LEVEL_DIRECTORY) { savedLevelPath(it) }

        /**
         * Reads all saved-world paths from [SAVED_WORLD_DIRECTORY], newest first.
         *
         * Parallel to [savedLevelPaths] but reads from the multi-table world save directory.
         *
         * @return saved-world paths, or an empty list when the directory is missing or unreadable.
         */
        suspend fun savedWorldPaths(): List<String> =
            listSavedFilesInDirectory(SAVED_WORLD_DIRECTORY) { savedWorldPath(it) }
                .filterNot { it.substringAfterLast("/") == WorldMetadataStore.METADATA_FILE_NAME }

        /**
         * Returns sorted JSON file paths from [directory], applying [pathBuilder] to each file name.
         *
         * @param directory VFS directory to scan for `.json` save files.
         * @param pathBuilder maps each file base name to its full VFS path.
         * @return sorted paths, or an empty list when the directory is missing or unreadable.
         */
        private suspend fun listSavedFilesInDirectory(
            directory: String,
            pathBuilder: (String) -> String
        ): List<String> {
            return try {
                val saveDirectory = applicationVfs[directory]
                if (!saveDirectory.exists()) return emptyList()
                sortedSavedLevelJsonFiles(saveDirectory.listSimple().map { it.baseName })
                    .map(pathBuilder)
            } catch (e: Exception) {
                println("Error loading saved files from $directory: ${e.message}")
                emptyList()
            }
        }

        /**
         * Loads a saved level file into [GameState] and starts construction mode, without
         * switching scenes.
         *
         * Split out from [loadSavedLevel] so callers that are not themselves an active
         * [SavedNetworksScene] instance (e.g. [WorldPlaceholderSceneLogic]) can reuse the same
         * loading behavior and then perform their own scene transition.
         *
         * @param levelPath path to the saved JSON level.
         * @return the loaded level, now assigned to [GameState.currentLevel].
         */
        suspend fun loadSavedLevelIntoGameState(levelPath: String): domain.Level {
            println("Load saved level: $levelPath")

            val level = LevelSerializer.loadFromAppVfs(levelPath)
            GameState.currentLevel = level
            // A saved network is already the intended construction. Keep it stable until the
            // player explicitly loads another level instead of replacing it from camera input.
            GameState.automaticStreetDetectionEnabled = false
            GameState.currentPredefinedLevelId = null
            GameState.simulationState = SimulationState.CONSTRUCTION
            GameState.markCurrentLevelSaved(MenuScene.saveSnapshotFor(level))
            Assets.preloadVehicleGraphics(level.vehicleTypes)
            return level
        }

        /**
         * Deletes a saved level or world file from the application VFS.
         *
         * Split out from [deleteSavedLevel] so callers that are not themselves an active
         * [SavedNetworksScene] instance (e.g. [WorldSelectionScene]) can reuse the same deletion
         * behavior for locally saved worlds shown in the world-selection list.
         *
         * @param path VFS path to the saved level or world file that should be removed.
         */
        suspend fun deleteSavedFile(path: String) {
            println("Delete saved file: $path")
            applicationVfs[path].delete()
        }
    }

    /**
     * Builds the saved-network list, delete controls, and close navigation back to the level menu.
     */
    override suspend fun SContainer.sceneMain() {
        drawWhiteSceneBackground()
        val savedLevels = getSavedLevels()
        val scrollContainer = container {
            for ((index, levelPath) in savedLevels.withIndex()) {
                this@container.drawSavedLevelRow(levelPath, index)
            }
        }

        graphics {
            fill(Colors.WHITE) {
                rect(0.0, 0.0, LevelScene.TABLE_PX_W, HEADER_BAND_H)
            }
        }

        text(HEADING_TEXT, textSize = 78.0, color = Colors.BLACK, font = bitcountSingle) {
            centerOn(this@sceneMain)
            y = HEADING_Y
        }

        // The scrollbar owns the scroll offset; the thumb is draggable and reports back here.
        val scrollbar = Scrollbar(
            parent = this,
            x = SCROLLBAR_X,
            y = LIST_START_Y,
            barHeight = SCROLLBAR_HEIGHT,
            viewportHeight = VIEWPORT_HEIGHT,
            contentHeight = savedNetworkContentHeight(savedLevels.size),
            initialScrollY = SCROLL_Y,
        ) { scrollY ->
            scrollContainer.y = scrollY
            SCROLL_Y = scrollY
        }

        attachScrollbarInput(scrollbar, SCROLL_SPEED)

        ControlButtons.drawCloseButton(this).onClickWithSound {
            launch {
                if(tutorialActivated) {
                    sceneContainer.changeTo<TutorialScene>()
                } else {
                    sceneContainer.changeTo<LevelScene>()
                }
            }
        }

        ControlButtons.drawBackButton(this).onClickWithSound {
            launch {
                sceneContainer.back()
            }
        }
    }

    /**
     * Draws one saved-network row with a load area and a delete icon at the row end.
     *
     * @param levelPath path to the saved level that the row represents.
     * @param index zero-based row index for vertical placement.
     */
    private fun Container.drawSavedLevelRow(levelPath: String, index: Int) {
        container {
            position(LIST_X, LIST_START_Y + index * (LIST_ITEM_HEIGHT + LIST_GAP))
            graphics {
                fill(Colors.WHITE) {
                    rect(0.0, 0.0, LIST_ITEM_WIDTH, LIST_ITEM_HEIGHT)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = CARD_STROKE_THICKNESS)) {
                    rect(0.0, 0.0, LIST_ITEM_WIDTH, LIST_ITEM_HEIGHT)
                }
            }

            container {
                text(savedLevelDisplayName(levelPath), textSize = 30.0, color = Colors.BLACK, font = bitcountSingle) {
                    position(20.0, LIST_ITEM_HEIGHT / 2.0 - this.height / 2.0)
                }
                onClickWithSound {
                    launch {
                        loadSavedLevel(levelPath)
                    }
                }
            }

            container {
                position(
                    LIST_ITEM_WIDTH - DELETE_ICON_SIZE - DELETE_ICON_PADDING,
                    LIST_ITEM_HEIGHT / 2.0 - DELETE_ICON_SIZE / 2.0
                )
                image(Assets.delete) {
                    size(DELETE_ICON_SIZE, DELETE_ICON_SIZE)
                }
                onClickWithSound {
                    launch {
                        deleteSavedLevel(levelPath)
                        sceneContainer.changeTo<SavedNetworksScene>()
                    }
                }
            }
        }
    }

    /**
     * Reads all saved-level paths from the saved-level directory.
     */
    private suspend fun getSavedLevels(): List<String> = savedLevelPaths()

    /**
     * Loads a saved level, clears the predefined-level marker, and starts construction mode.
     *
     * @param levelPath path to the saved JSON level.
     */
    suspend fun loadSavedLevel(levelPath: String) {
        loadSavedLevelIntoGameState(levelPath)
        sceneContainer.changeTo<LevelScene>()
    }

    /**
     * Deletes a saved level file from the saved-level directory.
     *
     * @param levelPath path to the saved JSON level that should be removed.
     */
    suspend fun deleteSavedLevel(levelPath: String) {
        deleteSavedFile(levelPath)
    }
}
