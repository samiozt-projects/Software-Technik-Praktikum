package scenes

/** Identifies which scene opened [WorldEditorScene] and should receive editor cancel/back. */
internal enum class WorldEditorBackDestination {
    /** Return to the world-selection list. */
    WORLD_SELECTION_SCENE,

    /** Return to the edit form for the selected world. */
    WORLD_EDIT_SCENE,

    /** Return to the create form for a new world. */
    NEW_WORLD_CREATION_SCENE
}
