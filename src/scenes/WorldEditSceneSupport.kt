package scenes

import persistence.PredefinedLevelCatalog
import persistence.WorldLevelSelection

/** Non-visual helpers used while preparing an existing world for editing. */
internal object WorldEditSceneSupport {
    /** Returns whether [world] has enough local data to open an editor preview offline. */
    fun hasLocalEditorPreview(world: WorldSelectionScene.WorldEntry): Boolean {
        return world.levelSelection != null ||
            world.savedLevelPath != null ||
            world.number in 1..LevelMenuScene.MAX_PREDEFINED_LEVELS
    }

    /** Resolves the editor template id, falling back to the bundled world number. */
    fun editorTemplateLevelId(world: WorldSelectionScene.WorldEntry): Int {
        return when (val selection = world.levelSelection) {
            is WorldLevelSelection.PredefinedLevel -> selection.levelId
            else -> runCatching { PredefinedLevelCatalog.entryFor(world.number).number }.getOrDefault(1)
        }
    }
}
