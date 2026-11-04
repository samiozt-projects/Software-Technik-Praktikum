package domaintests

import domain.Color
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.VehicleType
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests the SourceNode class. */
class SourceNodeTest {

    /**
     * Creates a test source node with the given configurations.
     * 
     * @param configurations The configurations to use for the source node.
     * @return The test source node.
     */
    private fun createSourceNode(vararg configurations: SourceNodeConfiguration): SourceNode {
        return SourceNode(Point(0, 0), 0, Angle.ZERO, configurations.toList())
    }

    /**
     * Creates a test source node configuration with the given speed, frequency, and vehicle length.
     * 
     * @param speed The speed of the vehicle.
     * @param freq The frequency of the vehicle.
     * @param vehicleLength The length of the vehicle.
     * @return The test source node configuration.
     */
    private fun createConfig(speed: Double, freq: Double, vehicleLength: Double): SourceNodeConfiguration {
        val color = Color("Red", Colors.RED)
        val vehicleType = VehicleType("TestCar", 80.0, vehicleLength, 0.7, 4.0, "images/car.png")
        return SourceNodeConfiguration(color, freq, speed, vehicleType, vehiclesToSpawn = 1)
    }

    /**
     * Test that the distance is valid when the initial speed and frequency are sufficient to maintain the minimum distance.
     */
    @Test
    fun isDistanceValid_returnsTrueWhenDistanceIsSufficient() {
        /**
         * Speed = 12 m/s, Freq = 2 s, Length = 5 m
         * Distance = (12 * 2) - 5 = 19 m (valid, >= 2.0)
         */
        val config = createConfig(12.0, 2.0, 5.0)
        val sourceNode = createSourceNode(config)

        assertTrue(sourceNode.isDistanceValidWithConfigurationChange(config, 12.0, 2.0))
    }

    /**
     * Test that the distance is invalid when the initial speed and frequency are insufficient to maintain the minimum distance.
     */
    @Test
    fun isDistanceValid_returnsFalseWhenDistanceIsTooShort() {
        /**
         * Speed = 9 m/s, Freq = 0.5 s, Length = 5 m
         * Distance = (9 * 0.5) - 5 = -0.5 m (invalid, < 2.0)
         */
        val config = createConfig(9.0, 2.0, 5.0) // Initial valid config
        val sourceNode = createSourceNode(config)

        assertFalse(sourceNode.isDistanceValidWithConfigurationChange(config, 9.0, 0.5))
    }

    /**
     * Test that the distance is valid when the effective frequency is calculated for multiple configurations.
     */
    @Test
    fun isDistanceValid_calculatesEffectiveFrequencyForMultipleConfigs() {
        /**
         * Two configs on the same node
         * Config 1: initial Freq = 10 s
         * Config 2: initial Freq = 10 s
         */
        val config1 = createConfig(12.0, 10.0, 5.0)
        val config2 = createConfig(8.0, 10.0, 5.0)
        val sourceNode = createSourceNode(config1, config2)

        /**
         * Effective freq with both at 10s is 5s
         * Speed 12 * 5s = 60m - 5m = 55m (valid)
         */
        assertTrue(sourceNode.isDistanceValidWithConfigurationChange(config1, 12.0, 10.0))

        /**
         * Change Config 1 to Freq = 0.5 s
         * totalRate = (1/0.5) + (1/10) = 2.0 + 0.1 = 2.1
         * effectiveFreq = 1 / 2.1 = 0.476 s
         * Distance = (12 * 0.476) - 5 = 0.71 m (invalid)
         */
        assertFalse(sourceNode.isDistanceValidWithConfigurationChange(config1, 12.0, 0.5))
    }

    /**
     * Test that the distance is valid when the effective frequency is calculated for multiple configurations.
     */
    @Test
    fun isDistanceValid_returnsFalseIfAnyConfigFailsDistanceCheck() {
        // Config 1: Length = 5.0
        val config1 = createConfig(11.0, 4.0, 5.0)
        // Config 2: Length = 15.0 (Long truck)
        val config2 = createConfig(8.0, 4.0, 15.0)
        val sourceNode = createSourceNode(config1, config2)

        /**
         * If we change Config 1 to freq = 1.0
         * totalRate = (1/1.0) + (1/4.0) = 1.25
         * effectiveFreq = 1 / 1.25 = 0.8 s
         * Distance for config1 = (11 * 0.8) - 5 = 3.8 m (Valid)
         * Distance for config2 = (8 * 0.8) - 15 = -8.6 m (Invalid)
         */
        assertFalse(sourceNode.isDistanceValidWithConfigurationChange(config1, 11.0, 1.0))
    }
}
