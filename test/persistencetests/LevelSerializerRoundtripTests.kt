package persistencetests

import domain.*
import korlibs.image.color.RGBA
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import persistence.LevelSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Roundtrip tests for LevelSerializer.
 * 
 * These tests verify that save → load cycles preserve all data correctly.
 * Each test creates a level, serializes it to JSON, deserializes it back,
 * and verifies that all properties are preserved.
 */
class LevelSerializerRoundtripTests {

    /**
     * Creates a test level with source, target, and street for roundtrip testing
     */
    private fun createTestLevel(): Level {
        val carType = VehicleType("Car", 50.0, 4.0, 0.8, 3.0)
        val red = Color("Rot", RGBA(-65536))

        val source = SourceNode(
            position = Point(0, 0),
            elevation = 1,
            orientation = Angle.fromDegrees(90.0),
            configurations = listOf(
                SourceNodeConfiguration(
                    color = red,
                    vehicleType = carType,
                    generationSpeed = 5.0,
                    initialVehicleSpeed = 10.0,
                    vehiclesToSpawn = 7,
                    vehiclesSpawned = 3,
                    vehiclesArrived = 2
                )
            )
        )
        val target = TargetNode(
            position = Point(200, 0),
            elevation = 0,
            orientation = Angle.fromDegrees(270.0),
            color = red
        )
        val level = Level(
            name = "Roundtrip-Test",
            number = 42,
            adhesionCoefficientModifier = 1.5,
            weatherCondition = WeatherCondition.SNOWY,
            challengeConnectionNodeLimit = 6
        )
        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, target, listOf(Point(0, 0), Point(100, 0), Point(200, 0))))
        return level
    }

    /**
     * Verifies that roundtrip preserves level properties
     */
    @Test
    fun roundtrip_preservesLevelProperties() {
        val level = createTestLevel()
        val loaded = LevelSerializer.fromJsonString(LevelSerializer.toJsonString(level))

        assertEquals("Roundtrip-Test", loaded.name)
        assertEquals(42, loaded.number)
        assertEquals(1.5, loaded.adhesionCoefficientModifier)
        assertEquals(WeatherCondition.SNOWY, loaded.weatherCondition)
        assertEquals(6, loaded.challengeConnectionNodeLimit)
        assertEquals(2, loaded.nodes.size)
        assertEquals(1, loaded.streets.size)
    }

    /**
     * Verifies that roundtrip preserves source node properties
     */
    @Test
    fun roundtrip_preservesSourceNodeProperties() {
        val level = createTestLevel()
        val loaded = LevelSerializer.fromJsonString(LevelSerializer.toJsonString(level))
        val loadedSource = assertIs<SourceNode>(loaded.nodes.filterIsInstance<SourceNode>().single())

        assertEquals(Point(0, 0), loadedSource.position)
        assertEquals(1, loadedSource.elevation)
        assertEquals(90.0, loadedSource.orientation.degrees)
        
        val cfg = loadedSource.configurations.single()
        assertEquals("Rot", cfg.color.name)
        assertEquals("Car", cfg.vehicleType.name)
        assertEquals(50.0, cfg.vehicleType.maxSpeed)
        assertEquals(4.0, cfg.vehicleType.length)
        assertEquals(0.8, cfg.vehicleType.adhesionCoefficient)
        assertEquals(3.0, cfg.vehicleType.acceleration)
        assertEquals(5.0, cfg.generationSpeed)
        assertEquals(10.0, cfg.initialVehicleSpeed)
        assertEquals(7, cfg.vehiclesToSpawn)
    }

    /**
     * Verifies that roundtrip preserves target node properties
     */
    @Test
    fun roundtrip_preservesTargetNodeProperties() {
        val level = createTestLevel()
        val loaded = LevelSerializer.fromJsonString(LevelSerializer.toJsonString(level))
        val loadedTarget = assertIs<TargetNode>(loaded.nodes.filterIsInstance<TargetNode>().single())

        assertEquals(Point(200, 0), loadedTarget.position)
        assertEquals(0, loadedTarget.elevation)
        assertEquals(270.0, loadedTarget.orientation.degrees)
        assertEquals("Rot", loadedTarget.color.name)
    }

    /**
     * Verifies that roundtrip preserves street properties
     */
    @Test
    fun roundtrip_preservesStreetProperties() {
        val level = createTestLevel()
        val loaded = LevelSerializer.fromJsonString(LevelSerializer.toJsonString(level))
        
        val loadedSource = assertIs<SourceNode>(loaded.nodes.filterIsInstance<SourceNode>().single())
        val loadedTarget = assertIs<TargetNode>(loaded.nodes.filterIsInstance<TargetNode>().single())
        val loadedStreet = loaded.streets.single()

        assertEquals(loadedSource, loadedStreet.startNode)
        assertEquals(loadedTarget, loadedStreet.endNode)
        assertEquals(3, loadedStreet.path.size)
        assertEquals(Point(0, 0), loadedStreet.path[0])
        assertEquals(Point(100, 0), loadedStreet.path[1])
        assertEquals(Point(200, 0), loadedStreet.path[2])
    }

    /**
     * Verifies that all simulation-related fields are NOT included in the saved JSON.
     */
    @Test
    fun toJsonString_excludesAllSimulationStateFields() {
        val level = LevelSerializer.fromJsonString(LevelSerializerTestData.LEVEL_WITH_SIMULATION_STATE_JSON)
        val json = LevelSerializer.toJsonString(level)

        // Verify ALL simulation state fields are NOT in the JSON
        assertTrue(!json.contains("simulationSpeed"), "Level.simulationSpeed should not be in saved JSON")
        assertTrue(!json.contains("vehiclesGenerated"), "SourceNode.vehiclesGenerated should not be in saved JSON")
        assertTrue(!json.contains("vehiclesSpawned"), "SourceNodeConfiguration.vehiclesSpawned should not be in saved JSON")
        assertTrue(!json.contains("vehiclesArrived"), "SourceNodeConfiguration.vehiclesArrived should not be in saved JSON")
    }

    /**
     * Verifies that street endpoint nodes (SourceNode and TargetNode) are preserved when explicitly added to the level.
     */
    @Test
    fun roundtrip_preservesStreetEndpointNodesNotExplicitlyAddedToLevel() {
        val red = Color("Rot", RGBA(-65536))
        val source = SourceNode(Point(0, 0), 0, Angle.ZERO, emptyList())
        val target = TargetNode(Point(200, 0), 0, Angle.ZERO, red)
        val junction = ConnectionNode(Point(100, 0), 0)
        val level = Level("Generated network", 7)

        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, junction, listOf(Point(0, 0), Point(100, 0))))
        level.addStreet(Street(junction, target, listOf(Point(100, 0), Point(200, 0))))

        val loaded = LevelSerializer.fromJsonString(LevelSerializer.toJsonString(level))

        val loadedJunction = loaded.nodes
            .filterIsInstance<ConnectionNode>()
            .single { it.position == junction.position }
        assertEquals(3, loaded.nodes.size)
        assertEquals(2, loaded.streets.size)
        assertEquals(loadedJunction, loaded.streets[0].endNode)
        assertEquals(loadedJunction, loaded.streets[1].startNode)
    }

    /** ------------------------------------------------------------------------
     * Color byte order (regression for #71: "Rot wird zu Blau")
     *
     * The level files store colors as a packed ARGB integer (0xAARRGGBB), while korlibs'
     * RGBA packs red into the lowest byte (0xAABBGGRR). LevelSerializer must convert between
     * the two; a raw cast swaps the red and blue channels. These tests pin both the on-disk
     * byte order and the per-channel round-trip so the swap cannot silently come back.
     * -------------------------------------------------------------------------
     */

    /**
     * Tests if red stored as ARGB in JSON is loaded as red, not blue.
     */
    @Test
    fun fromJsonString_redStoredAsArgb_loadsAsRedNotBlue() {
        val color = LevelSerializer.fromJsonString(LevelSerializerTestData.RED_COLOR_JSON)
            .nodes.filterIsInstance<TargetNode>().single().color.value

        assertEquals(255, color.r, "red channel")
        assertEquals(0, color.g, "green channel")
        assertEquals(0, color.b, "blue channel must be 0 — loading it as 255 was the bug")
        assertEquals(255, color.a, "alpha channel")
    }

    /**
     * Tests if blue stored as ARGB in JSON is loaded as blue, not red.
     */
    @Test
    fun fromJsonString_blueStoredAsArgb_loadsAsBlue() {
        val color = LevelSerializer.fromJsonString(LevelSerializerTestData.BLUE_COLOR_JSON)
            .nodes.filterIsInstance<TargetNode>().single().color.value

        assertEquals(0, color.r, "red channel must be 0 — loading it as 255 was the bug")
        assertEquals(0, color.g, "green channel")
        assertEquals(255, color.b, "blue channel")
        assertEquals(255, color.a, "alpha channel")
    }

    /**
     * Tests if red is stored as ARGB in JSON.
     */
    @Test
    fun toJsonString_storesColorInArgbByteOrder() {
        /**
         * Red must be written as ARGB 0xFFFF0000 (4294901760), NOT korlibs' native
         * 0xFF0000FF (4278190335) — otherwise the next load swaps red and blue.
         */
        val json = LevelSerializer.toJsonString(singleColorLevel(Color("Rot", RGBA(255, 0, 0, 255))))

        assertTrue(json.contains("4294901760"), "red must be serialized as ARGB 0xFFFF0000")
        assertTrue(
            !json.contains("4278190335"),
            "red must not be serialized in korlibs' native RGBA byte order"
        )
    }

    /**
     * Tests if color channels including alpha are preserved.
     */
    @Test
    fun roundtrip_preservesColorChannelsIncludingAlpha() {
        /**
         * Asymmetric channels (r != b) catch a red/blue swap; a non-opaque alpha guards the
         * A channel. Build → save → load must return the exact same channel values.
         */
        val color = Color("Testfarbe", RGBA(200, 100, 50, 128))

        val loaded = LevelSerializer.fromJsonString(
            LevelSerializer.toJsonString(singleColorLevel(color))
        )
        val rgba = loaded.nodes.filterIsInstance<TargetNode>().single().color.value

        assertEquals(200, rgba.r, "red channel")
        assertEquals(100, rgba.g, "green channel")
        assertEquals(50, rgba.b, "blue channel")
        assertEquals(128, rgba.a, "alpha channel")
    }

    /**
     * Minimal valid level whose only source configuration and target both use [color], so the
     * color survives a full serialize/deserialize cycle through both the source and target paths.
     * 
     * @param color The color to use for the level.
     * @return A level with a single source configuration and target, both using [color].
     */
    private fun singleColorLevel(color: Color): Level {
        val carType = VehicleType("Car", 50.0, 4.0, 0.8, 3.0)
        val source = SourceNode(
            position = Point(0, 0),
            elevation = 0,
            orientation = Angle.ZERO,
            configurations = listOf(
                SourceNodeConfiguration(
                    color = color,
                    vehicleType = carType,
                    generationSpeed = 5.0,
                    initialVehicleSpeed = 10.0
                )
            )
        )
        val target = TargetNode(Point(200, 0), 0, Angle.ZERO, color)
        val level = Level("ColorTest", 1)
        level.addNode(source)
        level.addNode(target)
        level.addStreet(Street(source, target, listOf(Point(0, 0), Point(200, 0))))
        return level
    }
}