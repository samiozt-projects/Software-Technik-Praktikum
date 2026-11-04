package scenes

import network.ServerConnection
import network.WorldEditorStateDTO
import persistence.PredefinedLevelCatalog
import persistence.WorldLevelSelection

/** Mutable selection metadata kept outside [WorldSelectionScene] to keep the scene lean. */
internal object WorldSelectionSceneState {
    /** Sample world list kept for tests and fallback level lookup. */
    val PLACEHOLDER_WORLDS = listOf(
        WorldSelectionScene.WorldEntry("world-1", 1, "Stadtkreuzung", "Server"),
        WorldSelectionScene.WorldEntry("world-2", 2, "Autobahnkreuz", "Tisch D"),
        WorldSelectionScene.WorldEntry("world-3", 3, "Testwelt", "Tisch A"),
        WorldSelectionScene.WorldEntry("world-4", 4, "Innenstadt", "Server"),
        WorldSelectionScene.WorldEntry("world-5", 5, "Brueckenroute", "Tisch B"),
        WorldSelectionScene.WorldEntry("world-6", 6, "Ringverkehr", "Tisch C"),
        WorldSelectionScene.WorldEntry("world-7", 7, "Hafen", "Server"),
        WorldSelectionScene.WorldEntry("world-8", 8, "Bergpass", "Tisch A")
    )

    /** Last world opened through the selection menu. */
    var openedWorld: WorldSelectionScene.WorldEntry? = null

    /** Scene that the back button should return to. */
    var backDestination: WorldSelectionBackDestination = WorldSelectionBackDestination.LEVEL_SCENE

    private val editedWorldNames = mutableMapOf<String, String>()
    private val locallySavedWorlds = mutableMapOf<String, WorldSelectionScene.WorldEntry>()
    private const val DEFAULT_TEMPLATE_LEVEL_NUMBER = 1

    /** Applies a placeholder edit to a world name. */
    fun saveWorldName(world: WorldSelectionScene.WorldEntry, name: String): Boolean {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return false
        editedWorldNames[world.id] = trimmedName
        rememberWorldMetadata(world.copy(name = trimmedName))
        if (openedWorld?.id == world.id) {
            openedWorld = openedWorld?.copy(name = trimmedName) ?: world.copy(name = trimmedName)
        }
        return true
    }

    /** Applies local placeholder edits to one world entry. */
    fun withLocalEdits(world: WorldSelectionScene.WorldEntry): WorldSelectionScene.WorldEntry {
        val rememberedWorld = locallySavedWorlds[world.id]
        val rememberedName = editedWorldNames[world.id] ?: rememberedWorld?.name
        if (rememberedWorld == null && rememberedName == null) return world
        return world.copy(
            name = rememberedName ?: world.name,
            savedLevelPath = rememberedWorld?.savedLevelPath ?: world.savedLevelPath,
            levelSelection = rememberedWorld?.levelSelection ?: world.levelSelection,
            deleteToken = rememberedWorld?.deleteToken ?: world.deleteToken
        )
    }

    /** Remembers metadata for the currently opened world until the server list returns it. */
    fun rememberOpenedWorld(world: WorldSelectionScene.WorldEntry): WorldSelectionScene.WorldEntry {
        val rememberedWorld = rememberWorldMetadata(world)
        openedWorld = rememberedWorld
        return rememberedWorld
    }

    /** Returns locally remembered editor worlds missing from the current server response. */
    fun rememberedWorldsMissingFrom(
        serverWorlds: List<WorldSelectionScene.WorldEntry>
    ): List<WorldSelectionScene.WorldEntry> {
        val serverIds = serverWorlds.mapTo(mutableSetOf()) { it.id }
        return locallySavedWorlds.values
            .filter { it.id !in serverIds }
            .mapIndexed { index, world ->
                val number = world.number.takeIf { it > 0 } ?: serverWorlds.size + index + 1
                world.copy(number = number)
            }
    }

    /** Closes a deleted world locally so it cannot be played or dialed into anymore. */
    fun closeDeletedWorld(world: WorldSelectionScene.WorldEntry) {
        editedWorldNames.remove(world.id)
        locallySavedWorlds.remove(world.id)
        if (WorldPlaceholderScene.confirmedTileIdFor(world.id) != null) {
            WorldPlaceholderScene.clearConfirmedTile()
            ServerConnection.disconnectSelectedField()
        }
        ServerConnection.forgetWorldTiles(world.id)
        if (isOpenedWorld(world)) openedWorld = null
    }

    /** Clears remembered mutable world metadata for scene tests. */
    fun resetLocalWorldStateForTesting() {
        openedWorld = null
        editedWorldNames.clear()
        locallySavedWorlds.clear()
        backDestination = WorldSelectionBackDestination.LEVEL_SCENE
    }

    /** Returns whether a saved snapshot opens directly in the level scene. */
    fun opensDirectlyFromSave(world: WorldSelectionScene.WorldEntry): Boolean =
        world.source == WorldSelectionScene.SAVED_WORLD_SOURCE && world.savedLevelPath != null

    /** Resolves the bundled level file used as the editable template for [world]. */
    fun editableWorldLevelPath(world: WorldSelectionScene.WorldEntry): String =
        runCatching { PredefinedLevelCatalog.entryFor(world.number).path }
            .getOrDefault(PredefinedLevelCatalog.entryFor(DEFAULT_TEMPLATE_LEVEL_NUMBER).path)

    /** Updates locally visible selection metadata after a successful editor save. */
    fun rememberSavedEditorWorld(state: WorldEditorStateDTO): WorldSelectionScene.WorldEntry {
        val existingWorld = openedWorld?.takeIf { it.id == state.worldId }
            ?: PLACEHOLDER_WORLDS.firstOrNull { it.id == state.worldId }
            ?: locallySavedWorlds[state.worldId]
        val templateSelection = WorldLevelSelection.PredefinedLevel(state.templateLevelId)
        return rememberOpenedWorld(
            WorldSelectionScene.WorldEntry(
                id = state.worldId,
                number = existingWorld?.number ?: state.templateLevelId,
                name = state.name,
                source = existingWorld?.source ?: "Server",
                savedLevelPath = existingWorld?.savedLevelPath,
                columns = existingWorld?.columns ?: 3,
                rows = existingWorld?.rows ?: 3,
                levelSelection = existingWorld?.levelSelection ?: templateSelection,
                deleteToken = existingWorld?.deleteToken
            )
        )
    }

    private fun rememberWorldMetadata(world: WorldSelectionScene.WorldEntry): WorldSelectionScene.WorldEntry {
        val rememberedWorld = world.copy(name = world.name.trim().ifEmpty { world.name })
        locallySavedWorlds[rememberedWorld.id] = rememberedWorld
        editedWorldNames[rememberedWorld.id] = rememberedWorld.name
        return rememberedWorld
    }

    private fun isOpenedWorld(world: WorldSelectionScene.WorldEntry): Boolean {
        val opened = openedWorld ?: return false
        return if (opensDirectlyFromSave(world)) {
            world.savedLevelPath == opened.savedLevelPath
        } else {
            world.id == opened.id
        }
    }
}
