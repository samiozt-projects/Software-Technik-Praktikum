package worldeditortests

import domain.Level
import worldeditor.WorldEditorConfigurationContext
import worldeditor.WorldEditorSession
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Tests the process-wide bridge that lets the source-configuration menu edit the world editor's
 * working level (issue #227 follow-up: configuration menu reachable from the editor).
 */
class WorldEditorConfigurationContextTest {

    /**
     * Clears the shared context after each test, since it is a process-wide singleton.
     */
    @AfterTest
    fun tearDown() {
        WorldEditorConfigurationContext.clear()
    }

    /**
     * Verifies that an opened context exposes the editor session and its working level.
     */
    @Test
    fun open_exposesSessionAndWorkingLevel() {
        val session = emptySession()

        WorldEditorConfigurationContext.open(session)

        assertTrue(WorldEditorConfigurationContext.isActive)
        assertSame(session, WorldEditorConfigurationContext.activeSession)
        assertSame(session.level, WorldEditorConfigurationContext.levelOrNull())
    }

    /**
     * Verifies that clearing the context detaches the configuration scenes from the editor again.
     */
    @Test
    fun clear_removesActiveSession() {
        WorldEditorConfigurationContext.open(emptySession())

        WorldEditorConfigurationContext.clear()

        assertFalse(WorldEditorConfigurationContext.isActive)
        assertNull(WorldEditorConfigurationContext.activeSession)
        assertNull(WorldEditorConfigurationContext.levelOrNull())
    }

    private fun emptySession(): WorldEditorSession =
        WorldEditorSession.fromLevel(
            worldId = null,
            worldName = "Neue Welt",
            templateLevelId = 1,
            isNewWorld = true,
            sourceLevel = Level("Empty", 1)
        )
}
