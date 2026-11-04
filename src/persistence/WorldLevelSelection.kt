package persistence

import domain.Level
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Selectable starting point for a newly created server world.
 */
@Serializable
sealed class WorldLevelSelection {
    /** The shared standard world template. */
    @Serializable
    @SerialName("standard")
    data object StandardTemplate : WorldLevelSelection()

    /**
     * One bundled predefined level.
     *
     * @property levelId id from [PredefinedLevelCatalog].
     */
    @Serializable
    @SerialName("predefined")
    data class PredefinedLevel(val levelId: Int) : WorldLevelSelection()

    /**
     * One user-saved level file.
     *
     * @property path app VFS path to the saved level JSON.
     */
    @Serializable
    @SerialName("saved")
    data class SavedLevel(val path: String) : WorldLevelSelection()
}

/**
 * Loads the selected start level and applies [worldName] as its display name.
 */
suspend fun WorldLevelSelection.loadLevelForWorld(worldName: String): Level {
    return when (this) {
        WorldLevelSelection.StandardTemplate -> WorldTemplate.createBlankWorld(worldName)
        is WorldLevelSelection.PredefinedLevel -> {
            val level = PredefinedLevelCatalog.load(levelId)
            LevelSerializer.copyWithName(level, worldName)
        }
        is WorldLevelSelection.SavedLevel -> {
            val level = LevelSerializer.loadFromAppVfs(path)
            LevelSerializer.copyWithName(level, worldName)
        }
    }
}
