package scenes

/** Identifies which scene opened [LevelMenuScene] and is the back-button target. */
enum class LevelMenuBackDestination {
    /** Return to the active gameplay scene. */
    LEVEL_SCENE,

    /** Return to the normal pause menu. */
    MENU_SCENE
}
