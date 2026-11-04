package persistencetests

import domain.Color
import domain.GameState
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street
import domain.TargetNode
import domain.VehicleType
import korlibs.image.color.RGBA
import korlibs.io.async.suspendTest
import korlibs.io.file.std.applicationVfs
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import persistence.LevelSerializationException
import persistence.LevelSerializer
import scenes.SavedNetworksScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * File-system-backed tests for the suspend [LevelSerializer] entry points
 * ([LevelSerializer.saveToFile], [LevelSerializer.loadFromAppVfs], [LevelSerializer.loadFromResources]).
 *
 * This class exercises the VFS
 * boundary instead, including the missing-file error paths. Files are written under `build/` so a
 * regular Gradle clean removes them, and each test deletes its own artifact afterwards.
 */
class LevelSerializerFileIOTest {

    /** Loading a saved network keeps its serialized streets safe from periodic camera capture. */
    @Test
    fun loadSavedLevel_disablesAutomaticStreetDetection() = suspendTest {
        val path = "build/tmp/level-io-test/static-saved-network.json"
        try {
            LevelSerializer.saveToFile(buildSampleLevel(), path)

            SavedNetworksScene.loadSavedLevelIntoGameState(path)

            assertFalse(GameState.automaticStreetDetectionEnabled)
            assertEquals(1, GameState.currentLevel?.streets?.size)
        } finally {
            GameState.currentLevel = null
            applicationVfs[path].delete()
        }
    }

    /**
     * Verifies that saving to app VFS and loading from app VFS preserves level data.
     */
    @Test
    fun saveToFile_thenLoadFromAppVfs_roundTripsLevel() = suspendTest {
        val path = "build/tmp/level-io-test/roundtrip.json"
        try {
            LevelSerializer.saveToFile(buildSampleLevel(), path)

            assertTrue(applicationVfs[path].exists(), "saveToFile should create the file")

            val loaded = LevelSerializer.loadFromAppVfs(path)

            assertEquals("IO-Test", loaded.name)
            assertEquals(7, loaded.number)
            assertEquals(2, loaded.nodes.size)
            assertEquals(1, loaded.streets.size)
            val config = loaded.nodes.filterIsInstance<SourceNode>().single().configurations.single()
            assertEquals("Rot", config.color.name)
            assertEquals("Car", config.vehicleType.name)
        } finally {
            applicationVfs[path].delete()
        }
    }

    /**
     * Verifies that loading a missing app-VFS file reports a serialization exception.
     */
    @Test
    fun loadFromAppVfs_missingFile_throwsException() = suspendTest {
        val exception = assertFailsWith<LevelSerializationException> {
            LevelSerializer.loadFromAppVfs("build/tmp/level-io-test/does-not-exist.json")
        }

        assertTrue(exception.message!!.contains("not found"))
    }

    /**
     * Verifies that loading a missing bundled resource reports a serialization exception.
     */
    @Test
    fun loadFromResources_missingResource_throwsException() = suspendTest {
        val exception = assertFailsWith<LevelSerializationException> {
            LevelSerializer.loadFromResources("level/does-not-exist-xyz.json")
        }

        assertTrue(exception.message!!.contains("File not found"))
    }

    /** Minimal valid level reused by the round-trip test. */
    private fun buildSampleLevel(): Level {
        val carType = VehicleType("Car", 50.0, 4.0, 0.8, 3.0)
        val red = Color("Rot", RGBA(255, 0, 0, 255))
        val source = SourceNode(
            position = Point(0, 0),
            elevation = 0,
            orientation = Angle.fromDegrees(90.0),
            configurations = listOf(
                SourceNodeConfiguration(
                    color = red,
                    vehicleType = carType,
                    generationSpeed = 5.0,
                    initialVehicleSpeed = 10.0
                )
            )
        )
        val target = TargetNode(Point(200, 0), 0, Angle.fromDegrees(270.0), red)
        val level = Level("IO-Test", 7)
        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, target, listOf(Point(0, 0), Point(200, 0))))
        return level
    }
}
