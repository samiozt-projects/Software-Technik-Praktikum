package scenes

/** Formats user-facing world labels without exposing technical server IDs. */
internal object WorldSelectionLabels {
    private const val WORLD_NAME_LABEL_MAX_CHARS = 32
    private const val WORLD_NAME_LABEL_ELLIPSIS = "..."

    /**
     * Returns the user-facing row label for [world].
     *
     * @param world world entry whose technical ID remains internal.
     * @return display label for the world-selection UI.
     */
    fun worldLabel(world: WorldSelectionScene.WorldEntry): String {
        val namePart = worldDisplayName(world)
        val dimensions = "${world.columns}x${world.rows}"
        return listOfNotNull("Welt ${world.number}", namePart, dimensions).joinToString(" - ")
    }

    private fun worldDisplayName(world: WorldSelectionScene.WorldEntry): String? {
        // The server loader uses the same String instance for a technical fallback name and its
        // ID, so avoid trimming or comparing a potentially long ID before hiding that fallback.
        if (world.name === world.id) return null
        val trimmedName = world.name.trim()
        if (trimmedName.isEmpty() || trimmedName.equals(world.id, ignoreCase = true)) return null
        return shortenedWorldDisplayName(trimmedName)
    }

    private fun shortenedWorldDisplayName(name: String): String {
        if (name.length <= WORLD_NAME_LABEL_MAX_CHARS) return name
        return name
            .take(WORLD_NAME_LABEL_MAX_CHARS - WORLD_NAME_LABEL_ELLIPSIS.length)
            .trimEnd() + WORLD_NAME_LABEL_ELLIPSIS
    }
}
