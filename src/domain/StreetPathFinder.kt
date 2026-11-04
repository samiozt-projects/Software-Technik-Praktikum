package domain

import geometry.simplifyPath
import korlibs.math.geom.Point
import kotlin.math.roundToInt
import kotlin.math.sqrt
import java.util.PriorityQueue

/**
 * Finds paths and routes in the street graph.
 */
object StreetPathFinder {
    /**
     * One directed route option from a source to a matching target.
     *
     * [length] is the Euclidean length over all street polylines and [path] is the flattened
     * point sequence used by rendering/simulation helpers.
     *
     * @property target The target node.
     * @property streets The streets taken so far.
     * @property length The length of the route so far.
     */
    data class DrivingRoute(
        val target: TargetNode,
        val streets: List<Street>,
        val length: Double
    ) {
        /** The path of the route. */
        val path: List<Point> = streets.flatMapIndexed { index, street ->
            if (index == 0) street.path else street.path.drop(1)
        }
    }

    /**
     * Represents an edge in the street graph.
     *
     * @property toKey The key of the node to which the edge leads.
     * @property path The path of the edge.
     */
    private data class Edge(val toKey: String, val path: List<Point>)

    /**
     * Represents the state of a route.
     *
     * @property node The current node.
     * @property streets The streets taken so far.
     * @property cost The cost of the route so far.
     * @property visitedNodeKeys The keys of the nodes visited so far.
     */
    private data class RouteState(
        val node: Node,
        val streets: List<Street>,
        val cost: Double,
        val visitedNodeKeys: Set<String>
    )

    /**
     * Finds a simplified point path between [source] and [target].
     *
     * This is kept for image-processing/path-preview tests. Simulation route choice uses
     * [findDrivingRoutes] so it can store multiple alternatives.
     *
     * @param streets The streets to find a path in.
     * @param source The source node.
     * @param target The target node.
     * @return The simplified point path.
     */
    fun findDrivingPath(streets: List<Street>, source: SourceNode, target: TargetNode): List<Point> {
        val adjacency = buildAdjacency(streets)

        val startKey = logicalNodeKey(source)
        val targetKey = logicalNodeKey(target)

        val visited = mutableSetOf<String>()
        val queue = PriorityQueue<Triple<Double, String, List<Point>>>(compareBy { it.first })
        queue.add(Triple(0.0, startKey, listOf(source.position)))

        while (queue.isNotEmpty()) {
            val (cost, currentKey, points) = queue.poll()

            if (currentKey == targetKey) return simplifyPath(points, PATH_SIMPLIFICATION_EPSILON)
            if (currentKey in visited) continue
            visited.add(currentKey)

            for (edge in adjacency[currentKey] ?: continue) {
                if (edge.toKey in visited) continue

                queue.add(
                    Triple(
                        cost + streetLength(edge.path),
                        edge.toKey,
                        points + edge.path.drop(1)
                    )
                )
            }
        }

        return emptyList()
    }

    /**
     * Returns directed route options from [source] to targets with [targetColor], ordered by
     * Euclidean street length. The simulation stores this list on each vehicle and starts with the
     * first route unless congestion makes a longer option preferable.
     * @param streets The streets to find routes in.
     * @param source The source node.
     * @param targetColor The color of the target nodes.
     * @return The list of driving routes.
     */
    fun findDrivingRoutes(streets: List<Street>, source: SourceNode, targetColor: Color): List<DrivingRoute> {
        val outgoingByNode = streets
            .filter { it.path.size >= 2 }
            .groupBy { logicalNodeKey(it.startNode) }
        val routes = mutableListOf<DrivingRoute>()
        val queue = PriorityQueue<RouteState>(compareBy { it.cost })
        val sourceKey = logicalNodeKey(source)

        queue.add(
            RouteState(
                node = source,
                streets = emptyList(),
                cost = 0.0,
                visitedNodeKeys = setOf(sourceKey)
            )
        )

        while (queue.isNotEmpty() && routes.size < MAX_ROUTE_OPTIONS) {
            val state = queue.poll()
            val node = state.node

            if (state.streets.isNotEmpty() && node is TargetNode && node.color.name == targetColor.name) {
                routes.add(DrivingRoute(node, state.streets, state.cost))
                continue
            }

            for (street in outgoingByNode[logicalNodeKey(node)].orEmpty()) {
                val nextNode = street.endNode
                val nextKey = logicalNodeKey(nextNode)
                if (nextKey in state.visitedNodeKeys) continue

                queue.add(
                    RouteState(
                        node = nextNode,
                        streets = state.streets + street,
                        cost = state.cost + streetLength(street.path),
                        visitedNodeKeys = state.visitedNodeKeys + nextKey
                    )
                )
            }
        }

        return routes
    }

    /**
     * Builds an adjacency list from the given streets.
     * @param streets The streets to build the adjacency list from.
     * @return The adjacency list.
     */
    private fun buildAdjacency(streets: List<Street>): Map<String, List<Edge>> {
        val adjacency = mutableMapOf<String, MutableList<Edge>>()
        val junctions = streets.flatMap { listOf(it.startNode, it.endNode) }.filterIsInstance<ConnectionNode>()
        val ringEdges = streets.map { RoundaboutGeometry.RingEdge(it.startNode, it.endNode, it.path) }
        // Street positions here are in virtual metres, so the metre-based tolerance/radius must be
        // passed explicitly - detect's defaults are in detector pixels (using those here would accept
        // junctions up to 80 m off the circle and mis-lock ordinary streets to ring direction).
        val roundabout = RoundaboutGeometry.detect(
            junctions,
            ringEdges,
            radiusTolerance = GameSimulationConfig.ROUNDABOUT_RADIUS_TOLERANCE_METERS,
            maxRadius = GameSimulationConfig.MAX_ROUNDABOUT_RADIUS_METERS
        )
        val roundaboutNodes = roundabout?.nodeKeys ?: emptySet()
        val center = roundabout?.center

        /**
         * Adds an edge to the adjacency list.
         * @param from The node from which the edge originates.
         * @param to The node to which the edge leads.
         * @param path The path of the edge.
         */
        fun addEdge(from: Node, to: Node, path: List<Point>) {
            if (path.size < 2) return
            adjacency.getOrPut(logicalNodeKey(from)) { mutableListOf() }
                .add(Edge(logicalNodeKey(to), path))
        }

        for (street in streets) {
            if (street.path.size < 2) continue

            val startKey = logicalNodeKey(street.startNode)
            val endKey = logicalNodeKey(street.endNode)

            if (center != null && startKey in roundaboutNodes && endKey in roundaboutNodes) {
                if (RoundaboutGeometry.isRightHandDirection(street.startNode, street.endNode, center)) {
                    addEdge(street.startNode, street.endNode, street.path)
                } else {
                    addEdge(street.endNode, street.startNode, street.path.asReversed())
                }
            } else {
                // Streets are one-way: every non-roundabout street already carries its single valid
                // direction, so only that direction becomes traversable here.
                addEdge(street.startNode, street.endNode, street.path)
            }
        }

        return adjacency
    }

    /**
     * Calculates the length of a path.
     * @param path The path to calculate the length of.
     * @return The length of the path.
     */
    private fun streetLength(path: List<Point>): Double {
        var length = 0.0
        for (i in 1 until path.size) {
            val dx = path[i].x - path[i - 1].x
            val dy = path[i].y - path[i - 1].y
            length += sqrt(dx * dx + dy * dy)
        }
        return length
    }

    /**
     * Creates a string key from a point.
     * @param point The point to create a key from.
     * @return The string key.
     */
    private fun positionKey(point: Point): String = "${point.x.roundToInt()}:${point.y.roundToInt()}"

    /**
     * Creates a string key from a node.
     * @param node The node to create a key from.
     * @return The string key.
     */
    private fun logicalNodeKey(node: Node): String = "${positionKey(node.position)}:${node.elevation}"

    private const val PATH_SIMPLIFICATION_EPSILON = GameSimulationConfig.PATH_SIMPLIFICATION_EPSILON
    private const val MAX_ROUTE_OPTIONS = GameSimulationConfig.MAX_ROUTE_OPTIONS
}
