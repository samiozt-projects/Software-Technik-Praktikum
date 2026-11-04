package persistencetests

import korlibs.io.async.suspendTest
import korlibs.io.file.std.applicationVfs
import persistence.LevelSerializer
import persistence.WorldLevelSelection
import persistence.WorldTemplate
import persistence.loadLevelForWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/** Verifies every level source offered when a server world is created or edited. */
class WorldLevelSelectionTest {

    /** The standard selection creates a fresh template and applies the requested world name. */
    @Test
    fun standardTemplate_createsNamedIndependentWorlds() = suspendTest {
        val first = WorldLevelSelection.StandardTemplate.loadLevelForWorld("Welt A")
        val second = WorldLevelSelection.StandardTemplate.loadLevelForWorld("Welt B")

        assertEquals("Welt A", first.name)
        assertEquals("Welt B", second.name)
        assertEquals(WorldTemplate.TEMPLATE_LEVEL_NUMBER, first.number)
        assertNotSame(first, second)
    }

    /** A bundled predefined level keeps its content while receiving the world display name. */
    @Test
    fun predefinedLevel_loadsCatalogLevelWithWorldName() = suspendTest {
        val selected = WorldLevelSelection.PredefinedLevel(1).loadLevelForWorld("Katalogwelt")

        assertEquals("Katalogwelt", selected.name)
        assertEquals(1, selected.number)
    }

    /** A user save is loaded from app VFS and copied with the requested world display name. */
    @Test
    fun savedLevel_loadsFileWithWorldName() = suspendTest {
        val path = "build/tmp/world-level-selection/saved-level.json"
        try {
            LevelSerializer.saveToFile(WorldTemplate.createBlankWorld("Alter Name"), path)

            val selected = WorldLevelSelection.SavedLevel(path).loadLevelForWorld("Gespeicherte Welt")

            assertEquals("Gespeicherte Welt", selected.name)
            assertEquals(WorldTemplate.TEMPLATE_LEVEL_NUMBER, selected.number)
        } finally {
            applicationVfs[path].delete()
        }
    }
}
