package scenes

import korlibs.korge.scene.SceneContainer

/** Stores and executes cancel/back navigation for [WorldEditorScene]. */
internal object WorldEditorBackNavigation {
    /** Last editor cancel target, reused when configuration scenes return to the editor. */
    var currentDestination: WorldEditorBackDestination = WorldEditorBackDestination.WORLD_SELECTION_SCENE

    /** Last edited world, needed to rebuild [WorldEditScene] after editor cancel. */
    var currentWorld: WorldSelectionScene.WorldEntry? = null

    /** World list snapshot used to rebuild [NewWorldCreationScene] after editor cancel. */
    var currentExistingWorlds: List<WorldSelectionScene.WorldEntry> = emptyList()

    /** Stores the editor cancel target for later editor recreation. */
    fun remember(
        destination: WorldEditorBackDestination,
        world: WorldSelectionScene.WorldEntry?,
        existingWorlds: List<WorldSelectionScene.WorldEntry>
    ) {
        currentDestination = destination
        currentWorld = world
        currentExistingWorlds = existingWorlds
    }

    /** Navigates to the scene that opened the editor. */
    suspend fun navigate(
        sceneContainer: SceneContainer,
        destination: WorldEditorBackDestination,
        world: WorldSelectionScene.WorldEntry?,
        existingWorlds: List<WorldSelectionScene.WorldEntry>
    ) {
        when (destination) {
            WorldEditorBackDestination.WORLD_SELECTION_SCENE -> sceneContainer.changeTo { WorldSelectionScene() }
            WorldEditorBackDestination.WORLD_EDIT_SCENE -> navigateToWorldEdit(sceneContainer, world)
            WorldEditorBackDestination.NEW_WORLD_CREATION_SCENE ->
                sceneContainer.changeTo { NewWorldCreationScene(existingWorlds) }
        }
    }

    /** Navigates to the edit form, falling back to world selection if no world is known. */
    private suspend fun navigateToWorldEdit(
        sceneContainer: SceneContainer,
        world: WorldSelectionScene.WorldEntry?
    ) {
        val targetWorld = world ?: WorldSelectionScene.openedWorld
        if (targetWorld != null) {
            sceneContainer.changeTo { WorldEditScene(targetWorld) }
        } else {
            sceneContainer.changeTo { WorldSelectionScene() }
        }
    }
}
