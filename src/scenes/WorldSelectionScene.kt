package scenes

import domain.ConnectionState
import network.ServerConnection
import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.scene.Scene
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.container
import korlibs.korge.view.text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.WorldEditorStateDTO
import persistence.WorldLevelSelection
import ui.Assets
import ui.Scrollbar

/**
 * Menu scene that lets the user select and manage worlds for the multi-table mode.
 *
 * The list can be refreshed in place and keeps locally known editor worlds visible even when the
 * server is unavailable. Server-provided rows are enriched with local metadata such as display
 * names and level templates until the coupling server persists those fields itself.
 */
open class WorldSelectionScene : Scene() {

    /**
     * One world row shown in the selection menu.
     *
     * @property id stable identifier used when opening the world.
     * @property number user-facing world number.
     * @property name world display name.
     * @property source server or table origin shown in the row.
     * @property savedLevelPath VFS path to the saved JSON file when this entry represents a locally
     *   saved world; null for server-provided placeholder worlds.
     * @property columns number of columns in the world grid.
     * @property rows number of rows in the world grid.
     * @property levelSelection selected starting level for a server-created world.
     * @property deleteToken locally stored delete token for future server-world deletion.
     */
    data class WorldEntry(
        val id: String,
        val number: Int,
        val name: String,
        val source: String,
        val savedLevelPath: String? = null,
        val columns: Int = 3, val rows: Int = 3,
        val levelSelection: WorldLevelSelection? = null,
        val deleteToken: String? = null
    )

    /**
     * Layout and state helpers for the world selection scene.
     */
    companion object {
        /** Title shown at the top of the menu. */
        const val TITLE_TEXT = "Weltauswahl"

        /** Caption above the server-world list. */
        const val LIST_CAPTION = "Welten auf dem Server:"

        /** Status banner text shown while a fetch is in flight. */
        const val LOADING_MESSAGE = "Welten werden geladen…"

        /** Width of the world list frame. */
        const val LIST_W = 1040.0

        /** Height of the world list frame. */
        const val LIST_H = 650.0

        /** Left position of the world list frame. */
        const val LIST_X = 150.0

        /** Top position of the world list frame. */
        const val LIST_Y = 285.0

        /** Width of a single world row. */
        const val ROW_W = 900.0

        /** Horizontal text padding reserved inside one world row. */
        const val ROW_LABEL_PADDING_X = 24.0

        /** Height of a single world row. */
        const val ROW_H = 86.0

        /** Gap between world rows. */
        const val ROW_GAP = 4.0

        /** Left inset for world rows inside the list frame. */
        const val ROW_X = 14.0

        /** Top inset for world rows inside the list frame. */
        const val ROW_START_Y = 12.0

        /** Viewport height available for visible world rows inside the list frame. */
        const val ROW_VIEWPORT_H = LIST_H - ROW_START_Y * 2.0

        /** Number of complete world rows shown at once and moved by one page arrow press. */
        const val WORLDS_PER_PAGE = 7

        /** Scroll distance for one page-arrow press, including row gaps. */
        const val WORLD_PAGE_SCROLL_DISTANCE = WORLDS_PER_PAGE * (ROW_H + ROW_GAP)

        /** Number of world rows moved by one mouse-wheel direction event. */
        const val WORLDS_PER_WHEEL_SCROLL = 5.5

        /** Scroll distance for one mouse-wheel direction event, including row gaps. */
        const val WORLD_WHEEL_SCROLL_DISTANCE = WORLDS_PER_WHEEL_SCROLL * (ROW_H + ROW_GAP)

        /** Width of one right-side action button. */
        const val ACTION_BUTTON_W = 485.0

        /** Height of one right-side action button. */
        const val ACTION_BUTTON_H = 82.0

        /** Vertical gap between action buttons. */
        const val ACTION_BUTTON_GAP = 24.0

        /** Left position of the action button column. */
        const val ACTION_X = 1230.0

        /** Top position of the action button column. */
        const val ACTION_Y = 285.0

        /** Border thickness used by list rows and action buttons. */
        const val STROKE_THICKNESS = 2.0

        /** Green fill used to mark the selected world and enabled open action. */
        val SELECTED_FILL: RGBA = Colors["#DCEFD9"]

        /** Grey fill used by disabled placeholder actions. */
        val DISABLED_FILL: RGBA = Colors["#F1F1F1"]

        /**
         * Sample world list kept for tests and as the fallback level catalog used by
         * [editableWorldLevelPath]; no longer used as the default menu content, which now comes
         * from [WorldSelectionServerLoader].
         */
        val PLACEHOLDER_WORLDS: List<WorldEntry> = WorldSelectionSceneState.PLACEHOLDER_WORLDS

        /** Last world opened through the selection menu. */
        var openedWorld: WorldEntry?
            get() = WorldSelectionSceneState.openedWorld
            set(value) {
                WorldSelectionSceneState.openedWorld = value
            }

        /**
         * Scene that the back button should return to.
         *
         * Must be set by the caller before navigating to [WorldSelectionScene] so the back button
         * returns to the correct scene regardless of Korge's single-level navigation history.
         */
        var backDestination: WorldSelectionBackDestination
            get() = WorldSelectionSceneState.backDestination
            set(value) {
                WorldSelectionSceneState.backDestination = value
            }

        /**
         * Returns the label shown for a world row.
         *
         * @param world world entry to display.
         * @return user-facing row label.
         */
        fun worldLabel(world: WorldEntry): String = WorldSelectionLabels.worldLabel(world)

        /**
         * Applies a placeholder edit to a world name.
         *
         * @param world world whose display name should be changed.
         * @param name new non-blank display name.
         * @return true when the name was accepted.
         */
        fun saveWorldName(world: WorldEntry, name: String): Boolean =
            WorldSelectionSceneState.saveWorldName(world, name)

        /**
         * Applies local placeholder edits to one world entry.
         *
         * @param world world entry from the placeholder or future server list.
         * @return world with locally edited display values.
         */
        fun withLocalEdits(world: WorldEntry): WorldEntry = WorldSelectionSceneState.withLocalEdits(world)

        /** Remembers metadata for the currently opened world until the server list returns it. */
        internal fun rememberOpenedWorld(world: WorldEntry): WorldEntry =
            WorldSelectionSceneState.rememberOpenedWorld(world)

        /** Returns locally remembered editor worlds missing from the current server response. */
        internal fun rememberedWorldsMissingFrom(serverWorlds: List<WorldEntry>): List<WorldEntry> =
            WorldSelectionSceneState.rememberedWorldsMissingFrom(serverWorlds)

        /**
         * Closes a deleted world locally so it cannot be played or dialed into anymore.
         *
         * Drops the locally remembered metadata (otherwise [rememberedWorldsMissingFrom] would
         * resurrect the deleted world in the next refresh), clears the confirmed tile and its
         * live field registration when they belong to the deleted world, forgets the world's
         * tile claims and cached tile layout, and finally clears [openedWorld] when the deleted
         * world is the one currently opened. State belonging to other worlds stays untouched.
         *
         * Must only be called after the world was actually removed (server delete succeeded or
         * the saved file was deleted); a failed delete must leave the state intact for a retry.
         *
         * @param world the world entry that was just deleted.
         */
        internal fun closeDeletedWorld(world: WorldEntry) = WorldSelectionSceneState.closeDeletedWorld(world)

        /** Clears remembered mutable world metadata for scene tests. */
        internal fun resetLocalWorldStateForTesting() = WorldSelectionSceneState.resetLocalWorldStateForTesting()

        /** Source label of world entries backed by a locally saved snapshot file. */
        const val SAVED_WORLD_SOURCE = "Gespeichert"

        /**
         * Returns whether [world] is a locally saved snapshot that opens directly in the level
         * scene. Server (and temporary local) worlds must always go through the tile selection
         * first — even though an editor save also stores a local level file for them, that file
         * only preserves the edited level and must not skip the multi-table dial-in.
         */
        internal fun opensDirectlyFromSave(world: WorldEntry): Boolean =
            WorldSelectionSceneState.opensDirectlyFromSave(world)

        /** Returns whether editor entry points may be opened from the current connection state. */
        fun worldEditorAvailable(): Boolean = ServerConnection.connectionState == ConnectionState.CONNECTED

        /**
         * Returns the total scrollable content height for [worldCount] rows.
         *
         * @param worldCount number of rows rendered in the world list.
         * @return height from the first row top to the last row bottom.
         */
        fun worldListContentHeight(worldCount: Int): Double =
            if (worldCount <= 0) 0.0 else worldCount * ROW_H + (worldCount - 1) * ROW_GAP

        /**
         * Resolves a selected index against [worlds].
         *
         * @param worlds available worlds.
         * @param index selected row index, or null when no row is selected.
         * @return selected world, or null for missing and out-of-range selections.
         */
        fun selectedWorld(worlds: List<WorldEntry>, index: Int?): WorldEntry? {
            val safeIndex = index ?: return null
            return worlds.getOrNull(safeIndex)
        }

        /**
         * Returns whether a world-dependent action can run.
         *
         * @param worlds available worlds.
         * @param selectedIndex selected row index, or null when no row is selected.
         * @return true only when the selected index points at an existing world.
         */
        fun worldActionEnabled(worlds: List<WorldEntry>, selectedIndex: Int?): Boolean =
            selectedWorld(worlds, selectedIndex) != null

        /** Returns whether the selected world may be edited right now. */
        fun worldEditorActionEnabled(worlds: List<WorldEntry>, selectedIndex: Int?): Boolean =
            worldEditorAvailable() && worldActionEnabled(worlds, selectedIndex)

        /**
         * Resolves the placeholder world to the bundled level file used until server loading exists.
         *
         * @param world world entry selected in the server-world list.
         * @return resource path of the level used for editing this world.
         */
        fun editableWorldLevelPath(world: WorldEntry): String =
            WorldSelectionSceneState.editableWorldLevelPath(world)

        /** Updates the locally visible world selection state after a successful editor save. */
        fun rememberSavedEditorWorld(state: WorldEditorStateDTO): WorldEntry =
            WorldSelectionSceneState.rememberSavedEditorWorld(state)
    }

    /** Root container captured in [sceneMain] so refresh and scrolling can draw into it later. */
    private lateinit var sceneRoot: SContainer

    /** Root view of the currently rendered world list, replaced on every [refreshWorlds] call. */
    private var listContainer: Container? = null

    /** Root view of the status banner (loading or connection error), if currently shown. */
    private var statusBannerContainer: Container? = null

    /** Controller for the right-side action buttons; reused across refreshes. */
    private lateinit var actionPanel: WorldSelectionActionPanel.Controller

    /** Scrollbar of the currently rendered world list, used by the shared drag/scroll handlers. */
    private var currentScrollbar: Scrollbar? = null

    /** Worlds currently shown by the menu; updated by [refreshWorlds]. */
    private var currentWorlds: List<WorldEntry> = emptyList()

    /** Row index selected in the current world list, or null when nothing is selected. */
    private var selectedWorldIndex: Int? = null

    /** Message shown instead of the server worlds when the last fetch failed; null on success. */
    private var errorMessage: String? = null

    /**
     * Builds the world selection menu and performs the initial world fetch.
     */
    override suspend fun SContainer.sceneMain() {
        sceneRoot = this
        WorldSelectionSceneChrome.drawBackgroundFrame(this)
        WorldSelectionSceneChrome.drawNavigation(this@WorldSelectionScene, this)
        WorldSelectionSceneChrome.drawTitle(this)
        actionPanel = WorldSelectionActionPanel.draw(
            parent = this,
            worlds = { currentWorlds },
            selectedIndex = { selectedWorldIndex },
            callbacks = WorldSelectionActionCallbacks(
                onOpen = ::openSelectedWorld,
                onEdit = ::openWorldEditScene,
                onRefresh = { launch { refreshWorlds() } },
                onNewWorld = { launch { openNewWorldTileSelection() } },
                onDelete = { worlds, selectedIndex -> launch { deleteSelectedWorld(worlds, selectedIndex) } }
            )
        )
        attachWorldListScrolling(
            scrollbar = { currentScrollbar },
            wheelStepDistance = WORLD_WHEEL_SCROLL_DISTANCE
        )
        refreshWorlds()
    }

    /**
     * Fetches the server world list via [WorldSelectionServerLoader], merges it with any locally
     * saved worlds, and rebuilds the list and action panel to reflect the result.
     *
     * Shows [LOADING_MESSAGE] immediately, before the fetch completes, so a slow connection or a
     * large server world list (real deployments have returned several hundred worlds) gives
     * feedback right away instead of appearing frozen. On a connection or parsing failure,
     * [errorMessage] is set and shown instead of crashing (see robustness issue #210); locally
     * saved worlds still appear so multi-table mode is not fully blocked by a server outage.
     */
    private suspend fun refreshWorlds() {
        selectedWorldIndex = null
        showStatusBanner(LOADING_MESSAGE, Colors.BLACK)

        val result = withContext(Dispatchers.IO) { WorldSelectionServerLoader.loadWorlds() }
        val serverWorlds = when (result) {
            is ServerWorldsResult.Success -> {
                errorMessage = null
                result.worlds.map(::withLocalEdits)
            }
            is ServerWorldsResult.Failure -> {
                errorMessage = result.message
                emptyList()
            }
        }
        val rememberedWorlds = rememberedWorldsMissingFrom(serverWorlds)
        val savedWorlds = withContext(Dispatchers.IO) {
            savedWorldEntries(serverWorlds.size + rememberedWorlds.size)
        }
        currentWorlds = serverWorlds + rememberedWorlds + savedWorlds

        rebuildWorldList()
        updateRefreshStatus()
    }

    /** Rebuilds the virtualized list after its world data changes. */
    private fun rebuildWorldList() {
        listContainer?.let { it.parent?.removeChild(it) }
        val worldList = WorldSelectionListRenderer.draw(
            parent = sceneRoot,
            worlds = currentWorlds,
            selectedIndex = { selectedWorldIndex },
            onWorldSelected = { index ->
                selectedWorldIndex = index
                actionPanel.refresh()
            }
        )
        listContainer = worldList.container
        currentScrollbar = worldList.scrollbar
        actionPanel.refresh()
    }

    /** Shows the final success or error state after a world-list refresh. */
    private fun updateRefreshStatus() {
        val message = errorMessage
        if (message != null) showStatusBanner(message, Colors.RED) else clearStatusBanner()
    }

    /**
     * Replaces the status banner (loading or connection error) shown above the world list.
     *
     * @param message user-facing message to show.
     * @param color text color; distinguishes the neutral loading state from an error.
     */
    private fun showStatusBanner(message: String, color: RGBA) {
        clearStatusBanner()
        statusBannerContainer = sceneRoot.container {
            text(message, textSize = 24.0, color = color, font = Assets.bitcountSingle) {
                x = LIST_X
                y = 245.0
            }
        }
    }

    /**
     * Removes the status banner, if one is currently shown.
     */
    private fun clearStatusBanner() {
        statusBannerContainer?.let { it.parent?.removeChild(it) }
        statusBannerContainer = null
    }

    /**
     * Reads saved worlds from [SavedNetworksScene.SAVED_WORLD_DIRECTORY] and maps them to entries.
     *
     * @param precedingWorldCount number of rows already shown above these entries (the fetched
     *   server worlds), so saved-world numbers never collide with server world numbers.
     * @return world entries for all saved world files, ordered as returned by [SavedNetworksScene.savedWorldPaths].
     */
    private suspend fun savedWorldEntries(precedingWorldCount: Int): List<WorldEntry> {
        return SavedNetworksScene.savedWorldPaths().mapIndexed { index, path ->
            WorldEntry(
                id = "saved-$index",
                number = precedingWorldCount + index + 1,
                name = SavedNetworksScene.savedLevelDisplayName(path),
                source = SAVED_WORLD_SOURCE,
                savedLevelPath = path
            )
        }
    }

    /**
     * Creates a new world on the coupling server and navigates to tile selection for it.
     *
     * Uses [WorldSelectionServerLoader.createWorld], tagging the world with this team's delete
     * token so it can later be removed via "Welt löschen". On success, sets [openedWorld] to a
     * placeholder entry carrying the server-assigned world ID and pre-registers an all-free tile
     * layout via [ServerConnection.registerAllFreeTiles] so none of the tiles appear
     * occupied — a new world has not yet been assigned to any table. After the player confirms
     * a tile, [WorldPlaceholderSceneLogic.navigateToPlaceholderTargetLevel] opens the blank
     * world template in [WorldScene]. A failed create request is caught and shown through
     * [errorMessage] instead of crashing (see robustness issue #210), and the menu is not left.
     */
    private suspend fun openNewWorldTileSelection() {
        sceneContainer.changeTo { NewWorldCreationScene(currentWorlds) }
    }

    /**
     * Opens the selected world and navigates to the appropriate scene.
     *
     * Saved worlds open directly in [LevelScene]; placeholder worlds use [WorldPlaceholderScene].
     * Does nothing when [selectedIndex] does not resolve to a world.
     *
     * @param worlds available worlds.
     * @param selectedIndex selected row index, or null when no row is selected.
     */
    private fun openSelectedWorld(worlds: List<WorldEntry>, selectedIndex: Int?) {
        val world = selectedWorld(worlds, selectedIndex) ?: return
        openedWorld = world
        launch {
            if (opensDirectlyFromSave(world)) {
                SavedNetworksScene.loadSavedLevelIntoGameState(world.savedLevelPath!!)
                sceneContainer.changeTo<LevelScene>()
            } else {
                WorldPlaceholderScene.backDestination = WorldPlaceholderScene.BackDestination.WORLD_SELECTION
                sceneContainer.changeTo { WorldPlaceholderScene() }
            }
        }
    }

    /**
     * Opens the world editor for the selected world.
     *
     * Does nothing when [selectedIndex] does not resolve to a world.
     *
     * @param worlds available worlds.
     * @param selectedIndex selected row index, or null when no row is selected.
     */
    private fun openWorldEditScene(worlds: List<WorldEntry>, selectedIndex: Int?) {
        val world = selectedWorld(worlds, selectedIndex) ?: return
        openedWorld = world
        launch {
            sceneContainer.changeTo { WorldEditScene(world, worlds) }
        }
    }

    /**
     * Deletes the selected world and refreshes the menu to reflect the change.
     *
     * Locally saved worlds are removed from disk via [SavedNetworksScene.deleteSavedFile]; all
     * other worlds are deleted on the coupling server via [WorldSelectionServerLoader.deleteWorld].
     * On success the deleted world is also closed locally via [closeDeletedWorld], so a world
     * that was opened (and possibly had a claimed tile) before its deletion no longer counts as
     * opened or playable. A failed server request is caught and shown through [errorMessage]
     * instead of crashing (see robustness issue #210), leaving the current world list untouched
     * so the user can retry. Does nothing when [selectedIndex] does not resolve to a world.
     *
     * @param worlds available worlds.
     * @param selectedIndex selected row index, or null when no row is selected.
     */
    private suspend fun deleteSelectedWorld(worlds: List<WorldEntry>, selectedIndex: Int?) {
        val world = selectedWorld(worlds, selectedIndex) ?: return
        // Same gate as opening: only locally saved snapshot worlds are file-only. A server
        // world may carry a savedLevelPath too (local editor save) and must still be deleted
        // on the server — otherwise it stays behind as an orphan.
        if (opensDirectlyFromSave(world)) {
            SavedNetworksScene.deleteSavedFile(world.savedLevelPath!!)
            closeDeletedWorld(world)
            refreshWorlds()
            return
        }

        when (val result = withContext(Dispatchers.IO) { WorldSelectionServerLoader.deleteWorld(world.id) }) {
            is ServerWorldDeleteResult.Success -> {
                deleteLocalEditorSave(world)
                closeDeletedWorld(world)
                refreshWorlds()
            }
            is ServerWorldDeleteResult.Failure -> {
                errorMessage = result.message
                showStatusBanner(result.message, Colors.RED)
            }
        }
    }

    /** Removes the world's local editor-save file so a deleted server world leaves no orphan. */
    private suspend fun deleteLocalEditorSave(world: WorldEntry) {
        try {
            persistence.EditorWorldSaves.deleteLevel(world.id)
        } catch (e: Exception) {
            println("Could not delete local editor save for world ${world.id}: ${e.message}")
        }
    }
}
