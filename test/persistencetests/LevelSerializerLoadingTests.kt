package persistencetests

import domain.*
import korlibs.math.geom.Point
import persistence.LevelSerializer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Loading tests for LevelSerializer.
 * 
 * These tests verify that levels can be loaded correctly from JSON,
 * with all fields preserved and properly validated.
 */
class LevelSerializerLoadingTests {

    /**
     * Happy path: ExampleLevel.json (deep verification of all fields)
     */

    /**
     * Verifies that the level metadata is loaded correctly.
     */
    @Test
    fun loadExampleLevel_levelMetadata() {
        val level = LevelSerializer.fromJsonString(LevelSerializerTestData.EXAMPLE_LEVEL_JSON)

        assertEquals("Beispiel-Level", level.name)
        assertEquals(1, level.number)
        assertEquals(1.0, level.adhesionCoefficientModifier)
        assertEquals(WeatherCondition.DRY, level.weatherCondition)
        assertEquals(Level.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT, level.challengeConnectionNodeLimit)
    }

    /**
     * Verifies that the node and street count is loaded correctly.
     */
    @Test
    fun loadExampleLevel_nodeAndStreetCount() {
        val level = LevelSerializer.fromJsonString(LevelSerializerTestData.EXAMPLE_LEVEL_JSON)

        assertEquals(3, level.nodes.size)
        assertEquals(2, level.streets.size)
    }

    /**
     * Verifies that the source node is loaded correctly.
     */
    @Test
    fun loadExampleLevel_sourceNode() {
        val level = LevelSerializer.fromJsonString(LevelSerializerTestData.EXAMPLE_LEVEL_JSON)
        val source = assertIs<SourceNode>(level.nodes.filterIsInstance<SourceNode>().single())

        assertEquals(Point(100, 200), source.position)
        assertEquals(0, source.elevation)
        assertEquals(90.0, source.orientation.degrees)
    }

    /**
     * Verifies that the source node configuration is loaded correctly.
     */
    @Test
    fun loadExampleLevel_sourceNodeConfiguration() {
        val level = LevelSerializer.fromJsonString(LevelSerializerTestData.EXAMPLE_LEVEL_JSON)
        val config = level.nodes.filterIsInstance<SourceNode>().single().configurations.single()

        assertEquals("Rot", config.color.name)
        assertEquals("Car", config.vehicleType.name)
        assertEquals(50.0, config.vehicleType.maxSpeed)
        assertEquals(4.0, config.vehicleType.length)
        assertEquals(0.8, config.vehicleType.adhesionCoefficient)
        assertEquals(3.0, config.vehicleType.acceleration)
        assertEquals(5.0, config.generationSpeed)
        assertEquals(10.0, config.initialVehicleSpeed)
        assertEquals(7, config.vehiclesToSpawn)
    }

    /**
     * Verifies that the target node is loaded correctly.
     */
    @Test
    fun loadExampleLevel_targetNode() {
        val level = LevelSerializer.fromJsonString(LevelSerializerTestData.EXAMPLE_LEVEL_JSON)
        val target = level.nodes.filterIsInstance<TargetNode>().single()

        assertEquals(Point(500, 200), target.position)
        assertEquals(0, target.elevation)
        assertEquals(270.0, target.orientation.degrees)
        assertEquals("Rot", target.color.name)
    }

    /**
     * Verifies that the connection node is loaded correctly.
     */
    @Test
    fun loadExampleLevel_connectionNode() {
        val level = LevelSerializer.fromJsonString(LevelSerializerTestData.EXAMPLE_LEVEL_JSON)
        val connection = level.nodes.filterIsInstance<ConnectionNode>().single()

        assertEquals(Point(300, 200), connection.position)
        assertEquals(0, connection.elevation)
    }

    /**
     * Verifies that the streets are loaded correctly.
     */
    @Test
    fun loadExampleLevel_streets() {
        val level = LevelSerializer.fromJsonString(LevelSerializerTestData.EXAMPLE_LEVEL_JSON)
        val source = level.nodes.filterIsInstance<SourceNode>().single()
        val target = level.nodes.filterIsInstance<TargetNode>().single()
        val connection = level.nodes.filterIsInstance<ConnectionNode>().single()

        val street1 = level.streets[0]
        assertEquals(source, street1.startNode)
        assertEquals(connection, street1.endNode)
        assertEquals(3, street1.path.size)
        assertEquals(Point(100, 200), street1.path[0])
        assertEquals(Point(200, 200), street1.path[1])
        assertEquals(Point(300, 200), street1.path[2])

        val street2 = level.streets[1]
        assertEquals(connection, street2.startNode)
        assertEquals(target, street2.endNode)
        assertEquals(3, street2.path.size)
        assertEquals(Point(300, 200), street2.path[0])
        assertEquals(Point(400, 200), street2.path[1])
        assertEquals(Point(500, 200), street2.path[2])
    }

    /**
     * Verifies that addStreet() wires up incoming/outgoing collections on both endpoint nodes
     */
    @Test
    fun loadExampleLevel_streetNodeLinkage() {
        val level = LevelSerializer.fromJsonString(LevelSerializerTestData.EXAMPLE_LEVEL_JSON)
        val source = level.nodes.filterIsInstance<SourceNode>().single()
        val target = level.nodes.filterIsInstance<TargetNode>().single()
        val connection = level.nodes.filterIsInstance<ConnectionNode>().single()

        assertEquals(1, source.outgoingStreets.size)
        assertEquals(0, source.incomingStreets.size)
        assertEquals(1, connection.outgoingStreets.size)
        assertEquals(1, connection.incomingStreets.size)
        assertEquals(0, target.outgoingStreets.size)
        assertEquals(1, target.incomingStreets.size)
    }

    /**
     * Verifies that vehiclesGenerated, vehiclesSpawned, vehiclesArrived in the JSON are now unknown keys
     * and are silently ignored (ignoreUnknownKeys = true). Domain values start at 0.
     */
    @Test
    fun loadConfigWithLegacySimStateFields_fieldsAreIgnored() {
        val level = LevelSerializer.fromJsonString(LevelSerializerTestData.LEGACY_SIM_STATE_JSON)

        val source = level.nodes.filterIsInstance<SourceNode>().single()
        val config = source.configurations.single()

        assertEquals(0, config.vehiclesSpawned)
        assertEquals(0, config.vehiclesArrived)
    }

    /**
     * Verifies that old level files without weather still load with the default dry condition.
     */
    @Test
    fun fromJsonString_missingWeatherCondition_defaultsToDry() {
        val level = LevelSerializer.fromJsonString(LevelSerializerTestData.LEVEL_WITHOUT_WEATHER_JSON)

        assertEquals(WeatherCondition.DRY, level.weatherCondition)
    }

    /**
     * Verifies that missing optional gameplay defaults are read from the central config.
     */
    @Test
    fun fromJsonString_missingGameplayDefaults_usesCentralConfig() {
        val level = LevelSerializer.fromJsonString(levelJsonWithoutGameplayDefaults())

        val source = level.nodes.filterIsInstance<SourceNode>().single()
        val config = source.configurations.single()

        assertEquals(GameSimulationConfig.DEFAULT_ADHESION_COEFFICIENT_MODIFIER, level.adhesionCoefficientModifier)
        assertEquals(GameSimulationConfig.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT, level.challengeConnectionNodeLimit)
        assertEquals(GameSimulationConfig.DEFAULT_VEHICLES_TO_SPAWN, config.vehiclesToSpawn)
    }

    /**
     * Returns a level JSON that omits optional gameplay defaults.
     */
    private fun levelJsonWithoutGameplayDefaults(): String =
        """
        {
          "name": "Central defaults",
          "number": 1,
          "vehicleTypes": ${singleVehicleTypeJson()},
          "colors": ${singleColorJson()},
          "nodes": ${singleSourceNodeWithoutGameplayDefaultsJson()},
          "streets": []
        }
        """.trimIndent()

    /**
     * Returns a compact vehicle type JSON array for default-loading tests.
     * 
     * @return A compact vehicle type JSON array.
     */
    private fun singleVehicleTypeJson(): String =
        """[{ "name": "Car", "maxSpeed": 50.0, "length": 4.0, "adhesionCoefficient": 0.8, "acceleration": 3.0 }]"""

    /**
     * Returns a compact color JSON array for default-loading tests.
     * 
     * @return A compact color JSON array.
     */
    private fun singleColorJson(): String =
        """[{ "name": "Rot", "argb": 4294901760 }]"""

    /**
     * Returns a source node JSON array without optional gameplay defaults.
     * 
     * @return A source node JSON array without optional gameplay defaults.
     */
    private fun singleSourceNodeWithoutGameplayDefaultsJson(): String =
        """
        [{
          "id": "source",
          "type": "SourceNode",
          "x": 0.0,
          "y": 0.0,
          "elevation": 0,
          "orientationDegrees": 0.0,
          "configurations": [${sourceConfigurationWithoutGameplayDefaultsJson()}]
        }]
        """.trimIndent()

    /**
     * Returns a source configuration JSON object without optional gameplay defaults.
     * 
     * @return A source configuration JSON object without optional gameplay defaults.
     */
    private fun sourceConfigurationWithoutGameplayDefaultsJson(): String =
        """{ "colorName": "Rot", "vehicleTypeName": "Car", "generationSpeed": 1.0, "initialVehicleSpeed": 10.0 }"""

    /**
     * Verifies that a level with an intersection and turns is loaded correctly.
     */
    @Test
    fun loadIntersectionAndTurnLevel_preservesPolylinePath() {
        val levelFile = File("savedlevel/intersection_and_turn_level.json")
        assertTrue(levelFile.exists(), "curved saved level should exist")

        val level = LevelSerializer.fromJsonString(levelFile.readText(), levelFile.path)

        assertEquals("Intersection and Turn Level", level.name)
        assertEquals(8, level.nodes.size)
        assertEquals(8, level.streets.size)

        // CONVERTED TO METERS: 900.0 px -> 112.5 m | 500.0 px -> 62.5 m | 1300.0 px -> 162.5 m | 150.0 px -> 18.75 m
        val street = level.streets.single { it.path.first() == Point(112.5, 62.5) && it.path.last() == Point(162.5, 18.75) }

        val source = level.nodes.single { it.position == Point(112.5, 62.5) }
        val target = level.nodes.single { it.position == Point(162.5, 18.75) }

        assertEquals(source, street.startNode)
        assertEquals(target, street.endNode)
        assertEquals(
            listOf(
                Point(112.5, 62.5),   // 900.0 px * 0.125
                Point(127.5, 60.0),   // 1020.0 px * 0.125
                Point(137.5, 40.625), // 1100.0 px * 0.125
                Point(147.5, 21.25),  // 1180.0 px * 0.125
                Point(162.5, 18.75)   // 1300.0 px * 0.125
            ),
            street.path,
            "curved street path should preserve every JSON point in order within the meter space"
        )
    }
}
