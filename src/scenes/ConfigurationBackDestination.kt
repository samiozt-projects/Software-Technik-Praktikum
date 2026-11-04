package scenes

/** Identifies which scene opened [ConfigurationScene] and is the back-button target. */
enum class ConfigurationBackDestination {
    /** The normal pause menu opened configuration. */
    MENU_SCENE,

    /** The multi-table pause menu opened configuration. */
    MULTI_TABLE_MENU_SCENE,

    /** Configuration was opened directly from gameplay. */
    GAMEPLAY_SCENE
}
