package domain

import korlibs.math.geom.Point
import korlibs.math.geom.Angle

/**
 * A node that acts as a destination where vehicles are removed from the network.
 * 
 * @property position The 2D coordinates of the node.
 * @property elevation The elevation level of the node (must be 0, 1, or 2).
 * @property orientation The angle at which the target node faces.
 * @property color The color representing this target node.
 */
class TargetNode(
    position: Point,
    elevation: Int,
    var orientation: Angle,
    var color: Color
) : Node(position, elevation) {

    /** Keeps track of the total number of vehicles that have arrived at this node. */
    var vehiclesArrived: Int = 0
}
