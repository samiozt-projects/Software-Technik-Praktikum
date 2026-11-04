package domain

import korlibs.math.geom.Point
import kotlin.math.abs

/**
 * Represents a directed (one-way) street between two nodes.
 *
 * @property startNode The node where the street begins.
 * @property endNode The node where the street ends.
 * @property path The physical path of the street, represented as a list of Points. (Polylines from
 * JavaCV should be converted accordingly)
 * @property utilization Normalized traffic load of this street, `0.0` = free flow,
 * `1.0` = fully blocked. It is a runtime value recomputed every tick by [simulation.Simulation];
 * it stays `0.0` while no simulation is running and is not persisted with the level.
 */
class Street(val startNode: Node, val endNode: Node, var path: List<Point>) {
    var utilization: Double = 0.0

    /** Initialization of a street object */
    init {
        require(abs(startNode.elevation - endNode.elevation) <= 1) {
            "Elevation difference between startNode and endNode cannot be greater than 1"
        }
        startNode.addOutgoingStreet(this)
        endNode.addIncomingStreet(this)
    }

    /** Removes this street from the network by unregistering it from its connected nodes. */
    fun delete() {
        startNode.removeOutgoingStreet(this)
        endNode.removeIncomingStreet(this)
    }
}
