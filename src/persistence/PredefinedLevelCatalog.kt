package persistence

/**
 * Catalog of bundled challenge levels that can be selected from the level menu.
 */
object PredefinedLevelCatalog {
    
    /**
     * The list of all predefined levels.
     */
    val levels = listOf(
        PredefinedLevelEntry(1, "Level 1", "level/level1.json"),
        PredefinedLevelEntry(2, "Level 2", "level/level2.json"),
        PredefinedLevelEntry(3, "Level 3", "level/level3.json"),
        PredefinedLevelEntry(4, "Level 4", "level/level4.json"),
        PredefinedLevelEntry(5, "Level 5", "level/level5.json"),
        PredefinedLevelEntry(6, "Level 6", "level/level6.json"),
        PredefinedLevelEntry(7, "Level 7", "level/level7.json"),
        PredefinedLevelEntry(8, "Level 8", "level/level8.json")
    )

    /**
     * Loads the bundled level with the given menu number.
     *
     * @throws LevelSerializationException if the level number is unknown or the file cannot be read.
     */
    suspend fun load(number: Int) =
        LevelSerializer.loadFromResources(entryFor(number).path)

    /**
     * Returns the catalog entry for the given menu number.
     *
     * @throws LevelSerializationException if the level number is not part of the catalog.
     */
    fun entryFor(number: Int) =
        levels.firstOrNull { it.number == number }
            ?: throw LevelSerializationException("Unknown predefined level: '$number'")
}

/**
 * Describes one selectable bundled level.
 * @property number The menu number of the level.
 * @property title The title of the level.
 * @property path The path to the level file.
 */
data class PredefinedLevelEntry(
    val number: Int,
    val title: String,
    val path: String
)
