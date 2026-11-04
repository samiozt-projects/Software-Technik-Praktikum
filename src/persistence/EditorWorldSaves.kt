package persistence

import domain.Level
import korlibs.io.file.std.applicationVfs

/**
 * Local fallback store for world levels edited in the world editor.
 *
 * The coupling server does not offer the world editor-state endpoints yet, so a server-side save
 * of edited worlds fails. Without a local copy the edited level would be lost and every dial-in
 * would fall back to the world's original template. This store keeps one level JSON per world,
 * keyed by the server world id, so the dial-in flow and the editor can restore the last locally
 * saved state — also across app restarts.
 *
 * Files live in their own [DIRECTORY] (not in the saved-worlds directory), so they never show up
 * as separate entries in the saved-world listings.
 */
object EditorWorldSaves {
    /** Directory for locally saved editor levels. */
    const val DIRECTORY = "editorstate"

    /** App-VFS path of the locally saved editor level for [worldId]. */
    fun levelPath(worldId: String): String = "$DIRECTORY/editor-level-${fileSafe(worldId)}.json"

    /**
     * Saves [level] as the local editor state for [worldId].
     *
     * @return the app-VFS path the level was written to.
     */
    suspend fun saveLevel(worldId: String, level: Level): String {
        val path = levelPath(worldId)
        LevelSerializer.saveToFile(level, path)
        return path
    }

    /** Loads the locally saved editor level for [worldId], or null when none exists. */
    suspend fun loadLevelOrNull(worldId: String): Level? {
        return try {
            LevelSerializer.loadFromAppVfs(levelPath(worldId))
        } catch (e: LevelSerializationException) {
            null
        }
    }

    /** Deletes the locally saved editor level for [worldId], if one exists. */
    suspend fun deleteLevel(worldId: String) {
        val file = applicationVfs[levelPath(worldId)]
        if (file.exists()) {
            file.delete()
        }
    }

    /** Maps a world id to a name that is safe as part of a file name. */
    private fun fileSafe(worldId: String): String =
        worldId.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")
}
