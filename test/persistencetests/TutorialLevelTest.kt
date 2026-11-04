package persistencetests

import domain.Level
import domain.NetworkState
import domain.Street
import simulation.Simulation
import domain.SourceNode
import domain.TargetNode
import domain.WeatherCondition
import persistence.LevelSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the bundled tutorial level file against the tutorial acceptance criteria.
 *
 * These tests load the actual JSON that the game starts with, so changes to the resource file are
 * checked at the same boundary used by production startup.
 */
class TutorialLevelTest {

    /**
     * Verifies that the tutorial keeps the requested minimal node setup: exactly one source and
     * exactly one target.
     */
    @Test
    fun tutorialLevel_containsExactlyOneSourceAndOneTarget() {
        val level = loadTutorialLevel()

        assertEquals(1, level.nodes.filterIsInstance<SourceNode>().size)
        assertEquals(1, level.nodes.filterIsInstance<TargetNode>().size)
        assertEquals(2, level.nodes.size)
    }

    /**
     * Verifies that the tutorial ships without a predefined street: the player has to lay the
     * street physically, and startup (see main.kt) enables automatic street detection exactly
     * when the loaded level has no streets.
     */
    @Test
    fun tutorialLevel_startsWithoutStreetsSoDetectionIsEnabled() {
        val level = loadTutorialLevel()

        assertTrue(level.streets.isEmpty(), "tutorial must not ship a predefined street")

        level.validate()
        assertEquals(NetworkState.EMPTY, level.networkState)
    }

    /**
     * Verifies that a single detected street from source to target completes the tutorial network,
     * mirroring what the camera detection commits once the player has laid the street.
     */
    @Test
    fun tutorialLevel_becomesCompleteOnceStreetIsDetected() {
        val level = loadTutorialLevel()
        addDetectedTutorialStreet(level)

        val validationErrors = level.validate()

        assertTrue(validationErrors.isEmpty(), "tutorial level should have no validation errors")
        assertEquals(NetworkState.COMPLETE, level.networkState)
    }

    /**
     * Verifies that the tutorial keeps the default dry driving condition.
     */
    @Test
    fun tutorialLevel_startsDry() {
        val level = loadTutorialLevel()

        assertEquals(WeatherCondition.DRY, level.weatherCondition)
    }

    /**
     * Verifies the end-to-end tutorial completion condition: after simulation time advances far
     * enough, a vehicle reaches the target and the level is marked as completed.
     */
    @Test
    fun tutorialSimulation_vehicleReachesTargetAndCompletesLevel() {
        val level = loadTutorialLevel()
        addDetectedTutorialStreet(level)
        val simulation = Simulation(level)

        simulation.tick(0.0)
        repeat(30) {
            simulation.tick(1.0)
        }

        val target = level.nodes.filterIsInstance<TargetNode>().single()
        assertTrue(
            target.vehiclesArrived >= 5,
            "all default tutorial vehicles should reach the tutorial target " +
                "(arrived=${target.vehiclesArrived}, active=${level.vehicles.size})"
        )
        assertTrue(level.vehicles.isEmpty())
        assertTrue(simulation.levelCompleted)
    }



    private fun loadTutorialLevel() =
        LevelSerializer.fromJsonString(TUTORIAL_LEVEL_JSON, "level/tutorial.json")

    /**
     * Adds the direct source-to-target street the player is expected to lay, exactly as the
     * camera detection would commit it into the level.
     */
    private fun addDetectedTutorialStreet(level: Level) {
        val source = level.nodes.filterIsInstance<SourceNode>().single()
        val target = level.nodes.filterIsInstance<TargetNode>().single()
        level.addStreet(Street(source, target, listOf(source.position, target.position)))
    }

    companion object {
        /**
         * Inline copy of res/level/tutorial.json.
         *
         * Keeping the JSON inline matches the existing serializer tests and avoids filesystem
         * dependencies in JVM unit tests.
         */
        private val TUTORIAL_LEVEL_JSON = """
            {
              "name": "Tutorial",
              "number": 0,
              "adhesionCoefficientModifier": 1.0,
              "weatherCondition": "dry",
              "vehicleTypes": [
                {
                  "name": "Auto",
                  "maxSpeed": 60.0,
                  "length": 4.0,
                  "adhesionCoefficient": 0.7,
                  "acceleration": 3.0,
                  "graphicRef": "images/car.png"
                }
              ],
              "colors": [
                {
                  "name": "Rot",
                  "argb": 4294901760
                }
              ],
              "nodes": [
                {
                  "id": "tutorial-source",
                  "type": "SourceNode",
                  "x": 31.25,
                  "y": 70.0,
                  "elevation": 0,
                  "orientationDegrees": 90.0,
                  "configurations": [
                    {
                      "colorName": "Rot",
                      "vehicleTypeName": "Auto",
                      "generationSpeed": 2.0,
                      "initialVehicleSpeed": 30.0,
                      "vehiclesToSpawn": 5
                    }
                  ]
                },
                {
                  "id": "tutorial-target",
                  "type": "TargetNode",
                  "x": 193.75,
                  "y": 70.0,
                  "elevation": 0,
                  "orientationDegrees": 270.0,
                  "colorName": "Rot"
                }
              ],
              "streets": []
            }
        """.trimIndent()
    }
}
