package domaintests

import domain.Color
import domain.CurveSpeed
import domain.GameSimulationConfig
import domain.Level
import simulation.Simulation
import domain.SimulationSpeed
import domain.SourceNodeConfiguration
import domain.VehicleType
import korlibs.image.color.Colors
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that gameplay and simulation defaults are exposed through [GameSimulationConfig].
 */
class GameSimulationConfigTest {

    /**
     * Verifies that newly created domain objects use the centralized gameplay defaults.
     */
    @Test
    fun domainDefaults_useCentralSimulationConfig() {
        val level = Level("Configured", 1)
        val configuration = SourceNodeConfiguration(
            color = Color("Rot", Colors.RED),
            generationSpeed = 1.0,
            initialVehicleSpeed = 30.0,
            vehicleType = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)
        )

        assertEquals(GameSimulationConfig.DEFAULT_ADHESION_COEFFICIENT_MODIFIER, level.adhesionCoefficientModifier)
        assertEquals(GameSimulationConfig.DEFAULT_LEVEL_SIMULATION_SPEED, level.simulationSpeed)
        assertEquals(GameSimulationConfig.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT, level.challengeConnectionNodeLimit)
        assertEquals(GameSimulationConfig.DEFAULT_VEHICLES_TO_SPAWN, configuration.vehiclesToSpawn)
    }

    /**
     * Verifies that legacy public constants continue to expose the centralized values.
     */
    @Test
    fun publicSimulationConstants_delegateToCentralConfig() {
        assertEquals(GameSimulationConfig.KMH_TO_MS, Simulation.KMH_TO_MS)
        assertEquals(GameSimulationConfig.PX_PER_METER, Simulation.PX_PER_METER)
        assertEquals(GameSimulationConfig.MIN_DISTANCE_M, Simulation.MIN_DISTANCE_M)
        assertEquals(GameSimulationConfig.MERGE_CLEARANCE_M, Simulation.MERGE_CLEARANCE_M)
        assertEquals(GameSimulationConfig.STOP_LINE_FRONT_OFFSET_M, Simulation.STOP_LINE_FRONT_OFFSET_M)
        assertEquals(GameSimulationConfig.FIXED_PLAYBACK_STEP_SECONDS, Simulation.FIXED_PLAYBACK_STEP_SECONDS)
        assertEquals(GameSimulationConfig.GRAVITY_MS2, CurveSpeed.GRAVITY_MS2)
        assertEquals(GameSimulationConfig.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT, Level.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT)
        assertEquals(GameSimulationConfig.LEVEL_WIDTH_PX, Level.WIDTH)
        assertEquals(GameSimulationConfig.LEVEL_HEIGHT_PX, Level.HEIGHT)
    }

    /**
     * Verifies that selectable simulation speeds use centrally configured multipliers.
     */
    @Test
    fun simulationSpeedMultipliers_useCentralConfig() {
        assertEquals(GameSimulationConfig.SIMULATION_SPEED_X1_MULTIPLIER, SimulationSpeed.X1.multiplier)
        assertEquals(GameSimulationConfig.SIMULATION_SPEED_X2_MULTIPLIER, SimulationSpeed.X2.multiplier)
        assertEquals(GameSimulationConfig.SIMULATION_SPEED_X5_MULTIPLIER, SimulationSpeed.X5.multiplier)
        assertEquals(GameSimulationConfig.SIMULATION_SPEED_X10_MULTIPLIER, SimulationSpeed.X10.multiplier)
    }
}
