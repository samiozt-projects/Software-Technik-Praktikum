package domain

import korlibs.math.geom.Point

/**
 * Abstract base class for all nodes in the street network.
 * 
 * @property position The 2D coordinates of the node.
 * @property elevation The elevation level of the node (must be 0, 1, or 2).
 * @property incomingStreets The list of streets that end at this node.
 * @property outgoingStreets The list of streets that start at this node.
 */
abstract class Node(
    var position: Point,
    var elevation: Int
) {
    /** Initialization of a Node object */
    init {
        require(elevation in 0..2) { "Elevation must be 0, 1, or 2" }
    }

    private val _incomingStreets: MutableList<Street> = mutableListOf()
    private val _outgoingStreets: MutableList<Street> = mutableListOf()

    val incomingStreets: List<Street> get() = _incomingStreets
    val outgoingStreets: List<Street> get() = _outgoingStreets

    internal fun addIncomingStreet(street: Street) {
        _incomingStreets.add(street)
    }

    internal fun removeIncomingStreet(street: Street) {
        _incomingStreets.remove(street)
    }

    internal fun addOutgoingStreet(street: Street) {
        _outgoingStreets.add(street)
    }

    internal fun removeOutgoingStreet(street: Street) {
        _outgoingStreets.remove(street)
    }
}
