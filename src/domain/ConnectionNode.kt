package domain

import korlibs.math.geom.Point

/**
 * A standard node that connects multiple streets.
 * 
 * @property position The 2D coordinates of the node.
 * @property elevation The elevation level of the node (must be 0, 1, or 2).
 */
class ConnectionNode(
    position: Point,
    elevation: Int
) : Node(position, elevation)
