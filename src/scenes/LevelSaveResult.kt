package scenes

/**
 * Result of saving the current level without leaving the active UI scene.
 */
internal sealed class LevelSaveResult {
    /** A new save file was written. */
    data class Saved(
        /** VFS path of the newly written save file. */
        val filePath: String,
        /** Level instance that was persisted. */
        val level: domain.Level
    ) : LevelSaveResult()

    /** The active level already matches the last or an existing save. */
    data class Unchanged(
        /** Level instance that is already represented by a save. */
        val level: domain.Level
    ) : LevelSaveResult()

    /** No active level was available, so the caller should recover navigation. */
    data object MissingLevel : LevelSaveResult()
}
