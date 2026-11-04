package persistencetests

import korlibs.io.async.suspendTest
import korlibs.io.file.std.applicationVfs
import persistence.EditorWorldSaves
import persistence.WorldTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the local fallback store for edited world levels ([EditorWorldSaves]), which preserves
 * editor saves while the coupling server offers no editor-state endpoints.
 */
class EditorWorldSavesTest {

    private companion object {
        const val TEST_WORLD_ID = "editor-saves-test-world"
    }

    /** Verifies that a saved editor level can be loaded back with its edited content. */
    @Test
    fun saveAndLoad_roundTripsEditedLevel() = suspendTest {
        try {
            val level = WorldTemplate.createBlankWorld("Editierte Welt")

            val path = EditorWorldSaves.saveLevel(TEST_WORLD_ID, level)
            val loaded = EditorWorldSaves.loadLevelOrNull(TEST_WORLD_ID)

            assertEquals(EditorWorldSaves.levelPath(TEST_WORLD_ID), path)
            assertEquals("Editierte Welt", loaded?.name)
            assertEquals(level.nodes.size, loaded?.nodes?.size)
        } finally {
            applicationVfs[EditorWorldSaves.levelPath(TEST_WORLD_ID)].delete()
        }
    }

    /** Verifies that loading without a saved state reports null instead of failing. */
    @Test
    fun loadLevelOrNull_returnsNullWithoutSavedState() = suspendTest {
        assertNull(EditorWorldSaves.loadLevelOrNull("missing-world-id"))
    }

    /** Verifies that unsafe world-id characters cannot escape the store directory. */
    @Test
    fun levelPath_replacesUnsafeCharacters() {
        assertEquals("editorstate/editor-level-a_b_c.json", EditorWorldSaves.levelPath("a/b:c"))
    }
}
