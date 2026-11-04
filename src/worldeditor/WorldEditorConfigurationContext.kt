package worldeditor

import domain.Level

/**
 * Process-wide bridge between the world editor and the source-configuration scenes.
 *
 * While a context is open, [scenes.ConfigurationScene] and its detail scenes edit the editor's
 * cloned working level instead of [domain.GameState.currentLevel], and their close actions return
 * to the world editor. This keeps the single-table game state untouched and lets configuration
 * edits participate in the editor's save/cancel semantics: they only reach the server when the
 * editor session is saved.
 *
 * The world editor opens the context right before switching to the configuration menu and clears
 * it again whenever the editor scene is (re-)entered.
 */
internal object WorldEditorConfigurationContext {

    /** Session whose working level the configuration scenes currently edit, if any. */
    var activeSession: WorldEditorSession? = null
        private set

    /** Whether the configuration scenes are currently editing a world-editor session. */
    val isActive: Boolean get() = activeSession != null

    /**
     * Attaches the configuration scenes to [session] until [clear] is called.
     */
    fun open(session: WorldEditorSession) {
        activeSession = session
    }

    /**
     * Detaches the configuration scenes from the editor again.
     */
    fun clear() {
        activeSession = null
    }

    /**
     * Returns the editor working level the configuration scenes should edit, or null when no
     * editor session is active.
     */
    fun levelOrNull(): Level? = activeSession?.level
}
