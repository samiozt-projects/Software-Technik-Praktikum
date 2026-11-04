package domain

import korlibs.math.geom.Point
import korlibs.math.geom.Angle

/**
 * A node that acts as a spawn point for vehicles.
 * 
 * @property position The 2D coordinates of the node.
 * @property elevation The elevation level of the node (must be 0, 1, or 2).
 * @property orientation The angle at which the source faces to generate vehicles.
 * @property configurations A list of configurations specifying what vehicles to generate.
 * @property kind Classifies whether this source belongs to the local field or is a neutral server source.
 */
class SourceNode(
    position: Point,
    elevation: Int,
    var orientation: Angle,
    var configurations: List<SourceNodeConfiguration> = emptyList(),
    var kind: SourceNodeKind = SourceNodeKind.LOCAL
) : Node(position, elevation) {
    
    /** Keeps track of the total number of vehicles created by this node. */
    var vehiclesGenerated: Int = 0

    /**
     * Checks if a configuration change would violate the 2.0 meter minimum distance rule
     * between generated vehicles from this source node.
     * 
     * @param targetConfig The configuration that is being changed.
     * @param newSpeed The new speed of the vehicle.
     * @param newFreq The new generation frequency of the vehicle.
     * @return True if the configuration change is valid, false otherwise.
     */
    fun isDistanceValidWithConfigurationChange(targetConfig: SourceNodeConfiguration, newSpeed: Double, newFreq: Double): Boolean {
        /** Calculate the total rate of vehicle generation from all configurations */
        var totalRate = 0.0
        configurations.forEach { conf ->
            totalRate += if (conf === targetConfig) {
                1.0 / newFreq
            } else {
                1.0 / conf.generationSpeed
            }
        }

        /** Calculate the effective generation frequency */
        val effectiveFreq = if (totalRate > 0.0) 1.0 / totalRate else Double.MAX_VALUE
        
        /** Check if the distance is valid for all configurations */
        for (conf in configurations) {
            val speed = if (conf === targetConfig) newSpeed else conf.initialVehicleSpeed
            val distance = (speed * effectiveFreq) - conf.vehicleType.length
            if (distance < 2.0) {
                return false
            }
        }
        return true
    }
}

/**
 * Distinguishes real local field sources from neutral server-managed sources.
 */
enum class SourceNodeKind {
    /** Source belongs to the local table and may receive handed-over vehicles. */
    LOCAL,

    /** Source is managed by the server and must not be used as a handover target. */
    SERVER_NEUTRAL
}
