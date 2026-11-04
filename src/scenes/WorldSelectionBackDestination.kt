package scenes

/** Identifies which scene opened [WorldSelectionScene] and is the back-button target. */
enum class WorldSelectionBackDestination {
    /** The active game scene ([LevelScene]). */
    LEVEL_SCENE,

    /** The multi-table pause menu ([MultiTableMenuScene]). */
    MULTI_TABLE_MENU_SCENE,

    /** The server connection/settings scene ([ServerMenuScene]). */
    SERVER_MENU
}
