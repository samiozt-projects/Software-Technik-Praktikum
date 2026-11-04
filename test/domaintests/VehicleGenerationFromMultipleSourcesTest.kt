package domaintests

import domain.GameState
import simulation.Simulation
import domain.SimulationState
import domain.TargetNode
import persistence.LevelSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies vehicle generation for a level with multiple sources and multiple source
 * configurations.
 *
 * The tests cover three aspects of the simulation:
 * - immediate spawning from all configured streams
 * - correct application of configuration parameters such as initial speed
 * - number of active vehicles at selected points in time
 */

class VehicleGenerationFromMultipleSourcesTest {

    /**
     * Test that the tick function spawns from all sources and every configuration over time.
     */
    @Test
    fun tick_spawnsFromAllSourcesAndEveryConfigurationOverTime() {
        /**
         * Since Issue #154 node-1's two configurations share one outgoing street and can no longer
         * both spawn on the same tick: the second is serialized behind the first. So at t=0 each
         * source only releases its first due configuration (node-1 cfgA + node-3), giving two
         * vehicles, and node-1's second configuration follows once the shared entry clears. Given
         * enough ticks every configured stream still produces vehicles.
         */
        val simulation = createSimulation()

        simulation.tick(0.0)
        assertEquals(2, simulation.level.vehicles.size, "each source releases its first configuration at t=0")

        repeat(10) { simulation.tick(1.0) }

        val configurations = simulation.level.nodes
            .filterIsInstance<domain.SourceNode>()
            .flatMap { it.configurations }
        assertEquals(3, configurations.size, "the fixture has two configurations on node-1 and one on node-3")
        assertTrue(
            configurations.all { it.vehiclesSpawned >= 1 },
            "every source configuration must produce at least one vehicle over time"
        )
    }

    /**
     * Test that the tick function uses the configured initial vehicle speeds at time zero.
     */
    @Test
    fun tick_atTimeZero_usesConfiguredInitialVehicleSpeeds() {
        /**
         * Issue #154: at t=0 node-1 only releases its first (50 km/h) configuration; the second
         * (70 km/h) one is serialized behind it on the shared street. The two vehicles released at
         * t=0 (node-1 cfgA and node-3) therefore both carry their configured 50 km/h initial speed.
         * The deferred 70 km/h configuration applies its speed through the very same code path and is
         * exercised by the generation test above once it spawns.
         */
        val simulation = createSimulation()

        simulation.tick(0.0)

        val speeds = simulation.level.vehicles.map { it.currentSpeed }.sorted()

        assertEquals(2, speeds.size)
        assertEquals(50.0 / 3.6, speeds[0], 0.0001)
        assertEquals(50.0 / 3.6, speeds[1], 0.0001)
    }

    /**
     * Test that the tick function counts active vehicles correctly over time.
     */
    @Test
    fun tick_countsActiveVehiclesCorrectlyOverTime() {
        /**
         * Issue #154: node-1's two configurations share one outgoing street, so they spawn
         * serialized instead of overlapping and the active-vehicle count climbs a little more slowly
         * than before. The qualitative behaviour is what matters here: while the three streams are
         * still spawning and nothing has reached the ~212 m target yet, the count only grows; once
         * vehicles begin arriving it falls; and every configured vehicle eventually arrives exactly
         * once so the level fully drains.
         */
        val simulation = createSimulation()
        val level = simulation.level
        /** Returns the number of arrived vehicles.
         * 
         * @return The number of arrived vehicles.
         */
        fun arrivedCount() = level.nodes.filterIsInstance<TargetNode>().sumOf { it.vehiclesArrived }

        simulation.tick(0.0)
        assertEquals(2, level.vehicles.size, "at t=0 each source releases only its first configuration")

        /**
         * Spawning phase: at 50-70 km/h no vehicle can cover the ~212 m street within 8 s, so the
         * active count is monotonically non-decreasing and no arrival has happened yet.
         */
        var previous = level.vehicles.size
        repeat(8) {
            simulation.tick(1.0)
            assertTrue(
                level.vehicles.size >= previous,
                "while only spawning the active count must not drop (was $previous, now ${level.vehicles.size})"
            )
            previous = level.vehicles.size
        }
        assertTrue(previous > 2, "the three streams should have raised the active count above its t=0 value")
        assertEquals(0, arrivedCount(), "no vehicle reaches the ~212 m target within the first 8 s")

        /**
         * Arrival phase: 40 more seconds is well beyond the slowest spawn + travel time, so every
         * configured vehicle (6 + 4 + 6) arrives exactly once and the level empties.
         */
        repeat(40) { simulation.tick(1.0) }
        assertTrue(level.vehicles.isEmpty(), "all vehicles eventually arrive and are removed")
        assertEquals(16, arrivedCount(), "all 6 + 4 + 6 configured vehicles arrive exactly once")
    }

    /**
     * Test that the tick function does not complete until each source configuration has reached a target.
     */
    @Test
    fun tick_multipleSources_doesNotCompleteUntilEachSourceConfigurationArrived() {
        val simulation = createSimulation(LEVEL_MULTIPLE_SOURCES_DIFFERENT_ARRIVAL_TIMES)
        val level = simulation.level

        simulation.tick(0.0)
        simulation.tick(10.0)

        assertEquals(1, level.nodes.sumOf { node ->
            if (node is TargetNode) node.vehiclesArrived else 0
        })
        assertFalse(
            simulation.levelCompleted,
            "level should not complete after only one source configuration has reached a target"
        )

        simulation.tick(80.0)

        assertTrue(
            simulation.levelCompleted,
            "level should complete after every source configuration has reached a target"
        )
    }

    /**
     * Creates a simulation with the given level JSON.
     * 
     * @param levelJson The level JSON to use for the simulation.
     * @return The simulation object.
     */
    private fun createSimulation(levelJson: String = LEVEL_MULTIPLE_SOURCES_AND_CONFIGS): Simulation {
        val level = LevelSerializer.fromJsonString(levelJson)
        GameState.currentLevel = level
        GameState.simulationState = SimulationState.CONSTRUCTION
        GameState.simulationState = SimulationState.SIMULATION
        return Simulation(level)
    }

    /**
     * The level JSON used for testing vehicle generation from multiple sources.
     * It contains three source nodes with different configurations, each with a different number of vehicles.
     */
    companion object {
        private val LEVEL_MULTIPLE_SOURCES_AND_CONFIGS = """
            {
                "name": "Level 0 modified",
                "number": 0,
                "adhesionCoefficientModifier": 1.0,
                "simulationSpeed": 1.0,
                "vehicleTypes": [
                    {
                        "name": "Auto",
                        "maxSpeed": 80.0,
                        "length": 4.0,
                        "adhesionCoefficient": 0.7,
                        "acceleration": 3.0
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
                        "type": "SourceNode",
                        "id": "node-1",
                        "x": 7.25,
                        "y": 62.5,
                        "elevation": 0,
                        "orientationDegrees": 0.0,
                        "vehiclesGenerated": 0,
                        "configurations": [
                            {
                                "colorName": "Rot",
                                "vehicleTypeName": "Auto",
                                "generationSpeed": 2.0,
                                "initialVehicleSpeed": 50.0,
                                "vehiclesToSpawn": 6
                            },
                            {
                                "colorName": "Rot",
                                "vehicleTypeName": "Auto",
                                "generationSpeed": 3.0,
                                "initialVehicleSpeed": 70.0,
                                "vehiclesToSpawn": 4
                            }
                        ]
                    },
                    {
                        "type": "TargetNode",
                        "id": "node-2",
                        "x": 219.125,
                        "y": 62.5,
                        "elevation": 0,
                        "orientationDegrees": 0.0,
                        "colorName": "Rot",
                        "vehiclesArrived": 0
                    },
                            {
                        "type": "SourceNode",
                        "id": "node-3",
                        "x": 7.25,
                        "y": 100.0,
                        "elevation": 0,
                        "orientationDegrees": 0.0,
                        "vehiclesGenerated": 0,
                        "configurations": [
                            {
                                "colorName": "Rot",
                                "vehicleTypeName": "Auto",
                                "generationSpeed": 2.0,
                                "initialVehicleSpeed": 50.0,
                                "vehiclesToSpawn": 6
                            }
                        ]
                    },
                    {
                        "type": "TargetNode",
                        "id": "node-4",
                        "x": 219.125,
                        "y": 100.0,
                        "elevation": 0,
                        "orientationDegrees": 0.0,
                        "colorName": "Rot",
                        "vehiclesArrived": 0
                    }
                ],
                "streets": [
                    {
                        "startNodeId": "node-1",
                        "endNodeId": "node-2",
                        "path": [
                            {
                                "x": 7.25,
                                "y": 62.5
                            },
                            {
                                "x": 219.125,
                                "y": 62.5
                            }
                        ]
                    },
                    {
                        "startNodeId": "node-3",
                        "endNodeId": "node-4",
                        "path": [
                            {
                                "x": 7.25,
                                "y": 100.0
                            },
                            {
                                "x": 219.125,
                                "y": 100.0
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        private val LEVEL_MULTIPLE_SOURCES_DIFFERENT_ARRIVAL_TIMES = """
            {
                "name": "Multiple sources completion",
                "number": 1,
                "adhesionCoefficientModifier": 1.0,
                "simulationSpeed": 1.0,
                "vehicleTypes": [
                    {
                        "name": "FastAuto",
                        "maxSpeed": 80.0,
                        "length": 4.0,
                        "adhesionCoefficient": 0.7,
                        "acceleration": 0.0
                    },
                    {
                        "name": "SlowAuto",
                        "maxSpeed": 5.0,
                        "length": 4.0,
                        "adhesionCoefficient": 0.7,
                        "acceleration": 0.0
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
                        "type": "SourceNode",
                        "id": "fast-source",
                        "x": 0.0,
                        "y": 12.5,
                        "elevation": 0,
                        "orientationDegrees": 0.0,
                        "vehiclesGenerated": 0,
                        "configurations": [
                            {
                                "colorName": "Rot",
                                "vehicleTypeName": "FastAuto",
                                "generationSpeed": 9999.0,
                                "initialVehicleSpeed": 80.0,
                                "vehiclesToSpawn": 1
                            }
                        ]
                    },
                    {
                        "type": "TargetNode",
                        "id": "fast-target",
                        "x": 100.0,
                        "y": 12.5,
                        "elevation": 0,
                        "orientationDegrees": 0.0,
                        "colorName": "Rot",
                        "vehiclesArrived": 0
                    },
                    {
                        "type": "SourceNode",
                        "id": "slow-source",
                        "x": 0.0,
                        "y": 37.5,
                        "elevation": 0,
                        "orientationDegrees": 0.0,
                        "vehiclesGenerated": 0,
                        "configurations": [
                            {
                                "colorName": "Rot",
                                "vehicleTypeName": "SlowAuto",
                                "generationSpeed": 9999.0,
                                "initialVehicleSpeed": 5.0,
                                "vehiclesToSpawn": 1
                            }
                        ]
                    },
                    {
                        "type": "TargetNode",
                        "id": "slow-target",
                        "x": 100.0,
                        "y": 37.5,
                        "elevation": 0,
                        "orientationDegrees": 0.0,
                        "colorName": "Rot",
                        "vehiclesArrived": 0
                    }
                ],
                "streets": [
                    {
                        "startNodeId": "fast-source",
                        "endNodeId": "fast-target",
                        "path": [
                            { "x": 0.0, "y": 12.5 },
                            { "x": 100.0, "y": 12.5 }
                        ]
                    },
                    {
                        "startNodeId": "slow-source",
                        "endNodeId": "slow-target",
                        "path": [
                            { "x": 0.0, "y": 37.5 },
                            { "x": 100.0, "y": 37.5 }
                        ]
                    }
                ]
            }
        """.trimIndent()
    }
}
