package domain

import korlibs.math.geom.Point
import java.util.PriorityQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * One physical street segment before a traffic direction has been assigned to it.
 *
 * @property nodeA One physical endpoint.
 * @property nodeB The other physical endpoint.
 * @property path The polyline between [nodeA] and [nodeB], ordered from [nodeA] to [nodeB].
 */
data class UndirectedSegment(val nodeA: Node, val nodeB: Node, val path: List<Point>)

/**
 * Decides the single traffic direction for street segments that do not directly touch a source or
 * target node (streets are one-way, so every segment needs exactly one direction).
 *
 * The decision follows a rule chain, applied in order until exactly one direction is settled:
 * 1. **Nearest source by path distance**: the source closest to the segment (via graph/path distance
 *    over the physical skeleton, not straight-line distance) dictates the direction, continuing away
 *    from the side it is closest to.
 * 2. **Demand filter** (tie-break): only sources whose configured colors actually have a reachable
 *    matching target anywhere in the network are considered; sources whose route would lead nowhere
 *    are dropped.
 * 3. **Direction continuity** (tie-break): the source whose shortest path arrives at the segment with
 *    the smallest bend angle wins (the road "continues straight").
 * 4. **Deterministic fallback**: a stable ordering of the endpoint keys, so a remaining tie still
 *    always resolves to the same single direction, reproducibly across repeated detections of the
 *    same physical street.
 *
 * @param segments All physical segments of the detected network (undirected), used to build the
 * skeleton graph that distances and bend angles are computed over.
 * @param sources All source nodes in the network.
 * @param targets All target nodes in the network.
 */
class StreetDirectionResolver(
    private val segments: List<UndirectedSegment>,
    private val sources: List<SourceNode>,
    private val targets: List<TargetNode>
) {
    /**
     * One edge of the undirected skeleton graph.
     * @property neighborKey Logical key of the node this edge leads to.
     * @property length Euclidean length of the physical segment this edge represents.
     * @property segment The physical segment this edge represents, so a traversal can check and
     * record the segment's direction constraint.
     */
    private data class SkeletonEdge(val neighborKey: String, val length: Double, val segment: UndirectedSegment)

    /**
     * One traversal step of a demand-flow path.
     * @property segment The undirected segment this step crosses.
     * @property forward True when [segment] is crossed from nodeA to nodeB, false for the reverse.
     */
    private data class FlowStep(val segment: UndirectedSegment, val forward: Boolean)

    /**
     * One source still in contention for deciding a segment's direction.
     * @property source The candidate source node.
     * @property entryKey Key of the segment endpoint this source's path enters through.
     * @property farKey Key of the segment's other endpoint.
     * @property previousOfEntry Predecessor node key on the shortest path into [entryKey], if reached.
     */
    private data class Candidate(val source: SourceNode, val entryKey: String, val farKey: String, val previousOfEntry: String?)

    /**
     * One route demand from a source configuration color to any matching target.
     * @property source The source that produces vehicles for [colorName].
     * @property colorName Color name the vehicle must reach.
     */
    private data class Demand(val source: SourceNode, val colorName: String)

    /**
     * Result of trying one deterministic demand-processing order.
     * @property directions Segment directions fixed by all route demands that found a path.
     * @property fulfilledCount Number of demands still routable after this order has fixed directions.
     * @property totalRouteLength Sum of fulfilled shortest-route lengths, used as a deterministic tie-break.
     * @property orderIndex Stable order index, used as the final tie-break.
     */
    private data class DemandFlowCandidate(
        val directions: LinkedHashMap<UndirectedSegment, Boolean>,
        val fulfilledCount: Int,
        val totalRouteLength: Double,
        val orderIndex: Int
    )

    /** Every node in the skeleton graph, keyed by its logical position/elevation key. */
    private val nodeByKey: Map<String, Node>

    /** Undirected adjacency list of the skeleton graph, keyed by logical node key. */
    private val adjacency: Map<String, List<SkeletonEdge>>

    /** Builds [nodeByKey] and [adjacency] from [segments]. */
    init {
        val nodes = mutableMapOf<String, Node>()
        val adj = mutableMapOf<String, MutableList<SkeletonEdge>>()

        for (segment in segments) {
            val keyA = logicalNodeKey(segment.nodeA)
            val keyB = logicalNodeKey(segment.nodeB)
            val length = pathLength(segment.path)
            nodes.putIfAbsent(keyA, segment.nodeA)
            nodes.putIfAbsent(keyB, segment.nodeB)
            adj.getOrPut(keyA) { mutableListOf() }.add(SkeletonEdge(keyB, length, segment))
            adj.getOrPut(keyB) { mutableListOf() }.add(SkeletonEdge(keyA, length, segment))
        }

        nodeByKey = nodes
        adjacency = adj
    }

    /** Each distinct source's Dijkstra result, keyed by source node key: distance paired with predecessor per reached node key. */
    private val routingBySource: Map<String, Pair<Map<String, Double>, Map<String, String>>> =
        sources.distinctBy { logicalNodeKey(it) }.associate { logicalNodeKey(it) to dijkstraFrom(logicalNodeKey(it)) }

    /**
     * Demand-flow pass: orients yet-undecided segments along an actual shortest source-to-target
     * route for every (source, color) demand, so reachability is *constructed* instead of hoped for.
     *
     * The per-segment rule chain ([resolveForward]) decides each segment in isolation by "which
     * source is nearest" - on layouts like a ladder (two long streets with several cross rungs) that
     * orients every rung the same way, and no local rule notices that one demand now has no directed
     * route at all (its rule-2 demand filter only ever runs on exact rule-1 distance ties, and only
     * checks undirected reachability). This pass runs *before* the rule chain: for each (source,
     * configured color) pair - in a deterministic order - it searches the shortest path from the
     * source to any matching target where already-oriented segments (locked ones passed in
     * [preOriented], and segments oriented by earlier demands in this pass) are traversable only in
     * their fixed direction, while undecided segments are traversable both ways. Every undecided
     * segment on the found path is then fixed to the direction the path crosses it in - so a ladder
     * rung needed as a "return path" is oriented against its siblings automatically.
     *
     * A demand whose search finds no path (e.g. genuinely disconnected, or blocked by earlier
     * demands in an unsatisfiable network) is skipped; later validation/the start gate reports it
     * exactly as before.
     *
     * @param preOriented Segments whose direction is already fixed (source/target-locked, roundabout).
     * @return Directions for the segments this pass decided; disjoint from [preOriented]'s keys.
     */
    fun orientByDemandFlows(preOriented: Map<UndirectedSegment, Boolean>): Map<UndirectedSegment, Boolean> {
        val targetKeysByColor = targets.groupBy({ it.color.name }, { logicalNodeKey(it) })
        val demands = buildDemands()
        if (demands.isEmpty()) return emptyMap()

        return demandOrders(demands, targetKeysByColor, preOriented)
            .mapIndexed { index, order -> tryDemandOrder(order, targetKeysByColor, preOriented, index) }
            .maxWithOrNull(
                compareBy<DemandFlowCandidate> { it.fulfilledCount }
                    .thenByDescending { -it.totalRouteLength }
                    .thenByDescending { -it.orderIndex }
            )
            ?.directions
            ?: emptyMap()
    }

    /** Builds route demands in the old deterministic order: source position first, color name second. */
    private fun buildDemands(): List<Demand> =
        sources
            .distinctBy { logicalNodeKey(it) }
            .sortedBy { logicalNodeKey(it) }
            .flatMap { source ->
                source.configurations.map { it.color.name }.distinct().sorted().map { color -> Demand(source, color) }
            }

    /**
     * Candidate orders for the greedy demand-flow pass. Trying several deterministic orders avoids an
     * earlier demand fixing a shared one-way segment so a later demand can no longer reach its target
     * even though a different processing order would keep all demands routable.
     */
    private fun demandOrders(
        demands: List<Demand>,
        targetKeysByColor: Map<String, List<String>>,
        preOriented: Map<UndirectedSegment, Boolean>
    ): List<List<Demand>> {
        val routeLength = demands.associateWith { demand ->
            targetKeysByColor[demand.colorName]?.toSet()
                ?.let { targetKeys -> constrainedShortestPath(logicalNodeKey(demand.source), targetKeys, preOriented, emptyMap()) }
                ?.sumOf { pathLength(it.segment.path) }
                ?: Double.POSITIVE_INFINITY
        }

        return listOf(
            demands,
            demands.asReversed(),
            demands.sortedWith(compareBy<Demand> { routeLength.getValue(it) }.thenBy { logicalNodeKey(it.source) }.thenBy { it.colorName }),
            demands.sortedWith(compareByDescending<Demand> { routeLength.getValue(it) }.thenBy { logicalNodeKey(it.source) }.thenBy { it.colorName })
        ).distinct()
    }

    /** Runs the original greedy route-orientation pass for one specific demand order and scores it. */
    private fun tryDemandOrder(
        demands: List<Demand>,
        targetKeysByColor: Map<String, List<String>>,
        preOriented: Map<UndirectedSegment, Boolean>,
        orderIndex: Int
    ): DemandFlowCandidate {
        val flowOriented = LinkedHashMap<UndirectedSegment, Boolean>()

        for (demand in demands) {
            val targetKeys = targetKeysByColor[demand.colorName]?.toSet() ?: continue
            val path = constrainedShortestPath(logicalNodeKey(demand.source), targetKeys, preOriented, flowOriented)
                ?: continue
            for (step in path) {
                if (step.segment in preOriented || step.segment in flowOriented) continue
                flowOriented[step.segment] = step.forward
            }
        }

        val finalDirections = completeDirections(preOriented, flowOriented)
        val finalNonLockedDirections = finalDirections.filterKeys { it !in preOriented }
        val fulfilledPaths = demands.mapNotNull { demand ->
            val targetKeys = targetKeysByColor[demand.colorName]?.toSet() ?: return@mapNotNull null
            constrainedShortestPath(logicalNodeKey(demand.source), targetKeys, preOriented, finalNonLockedDirections)
        }

        return DemandFlowCandidate(
            directions = flowOriented,
            fulfilledCount = fulfilledPaths.size,
            totalRouteLength = fulfilledPaths.sumOf { path -> path.sumOf { pathLength(it.segment.path) } },
            orderIndex = orderIndex
        )
    }

    /**
     * Completes one demand-flow candidate exactly like production direction resolution does after
     * [orientByDemandFlows] returns: every still-undecided segment gets the rule-chain direction and
     * repairable ground-level dead ends are fixed. Used only for scoring demand orders.
     */
    private fun completeDirections(
        preOriented: Map<UndirectedSegment, Boolean>,
        flowOriented: Map<UndirectedSegment, Boolean>
    ): Map<UndirectedSegment, Boolean> {
        val ruleChain = segments
            .filter { it !in preOriented && it !in flowOriented }
            .associateWith(::resolveForward)
        val repaired = repairDeadEnds(preOriented + flowOriented + ruleChain, ruleChain.keys)
        return preOriented + flowOriented + repaired.filterKeys { it in ruleChain }
    }

    /**
     * Dijkstra from [startKey] to the nearest of [targetKeys], where a segment with a direction
     * constraint (in [preOriented] or [flowOriented]) may only be crossed in that direction and
     * unconstrained segments may be crossed both ways. Returns the traversal steps in order, or null
     * when no such path exists.
     */
    private fun constrainedShortestPath(
        startKey: String,
        targetKeys: Set<String>,
        preOriented: Map<UndirectedSegment, Boolean>,
        flowOriented: Map<UndirectedSegment, Boolean>
    ): List<FlowStep>? {
        val distance = mutableMapOf(startKey to 0.0)
        val previous = mutableMapOf<String, Pair<String, FlowStep>>()
        val visited = mutableSetOf<String>()
        val queue = PriorityQueue<Pair<Double, String>>(compareBy { it.first })
        queue.add(0.0 to startKey)

        while (queue.isNotEmpty()) {
            val (cost, key) = queue.poll()
            if (!visited.add(key)) continue

            if (key in targetKeys) {
                val steps = mutableListOf<FlowStep>()
                var current = key
                while (current != startKey) {
                    val (predecessor, step) = previous.getValue(current)
                    steps.add(step)
                    current = predecessor
                }
                return steps.asReversed()
            }

            for (edge in adjacency[key].orEmpty()) {
                // Crossing from this node towards the neighbor is "forward" when this node is the
                // segment's nodeA side; a fixed direction only permits the matching crossing.
                val isForwardStep = key == logicalNodeKey(edge.segment.nodeA)
                val constraint = preOriented[edge.segment] ?: flowOriented[edge.segment]
                if (constraint != null && constraint != isForwardStep) continue

                val candidateDistance = cost + edge.length
                if (candidateDistance < (distance[edge.neighborKey] ?: Double.POSITIVE_INFINITY) - DIJKSTRA_RELAXATION_EPSILON) {
                    distance[edge.neighborKey] = candidateDistance
                    previous[edge.neighborKey] = key to FlowStep(edge.segment, isForwardStep)
                    queue.add(candidateDistance to edge.neighborKey)
                }
            }
        }

        return null
    }

    /**
     * Resolves the direction for [segment]. Returns true when the segment should be oriented
     * nodeA→nodeB, false when it should be oriented nodeB→nodeA.
     */
    fun resolveForward(segment: UndirectedSegment): Boolean {
        val keyA = logicalNodeKey(segment.nodeA)
        val keyB = logicalNodeKey(segment.nodeB)

        var candidates = nearestSourceCandidates(keyA, keyB)

        if (candidates.size > 1) {
            // Rule 2: demand filter. Sources whose route would lead nowhere anyway are dropped; if
            // that would eliminate every tied candidate, the tie is kept unresolved for rule 3 instead.
            val filtered = candidates.filter { canReachMatchingTarget(it.farKey, it.source) }
            if (filtered.isNotEmpty()) candidates = filtered
        }

        if (candidates.size > 1) {
            // Rule 3: direction continuity. Narrows to whichever candidate(s) continue straightest;
            // a genuine remaining tie (e.g. two symmetric approaches) falls through to rule 4 below.
            val angled = candidates.mapNotNull { candidate -> bendAngle(candidate)?.let { candidate to it } }
            if (angled.isNotEmpty()) {
                val minAngle = angled.minOf { it.second }
                candidates = angled.filter { abs(it.second - minAngle) < ANGLE_TIE_TOLERANCE_RADIANS }.map { it.first }
            }
        }

        // Rule 4: only a single surviving candidate may decide the direction. Any remaining tie -
        // including one no source candidate could resolve at all - always falls back to the
        // deterministic, source-independent endpoint-key ordering.
        val winner = candidates.singleOrNull() ?: return fallbackForward(keyA, keyB)
        return winner.entryKey == keyA
    }

    /**
     * Rule 1: sources at the smallest graph distance from either endpoint of the segment.
     *
     * Distances are summed path lengths over a camera-detected, per-capture-noisy skeleton
     * ([pathLength] over [UndirectedSegment.path]), so two sources that are genuinely about equally
     * far away can measure a few pixels apart one capture and the other way around the next -
     * without a single physical thing having changed. Comparing with [NEAREST_SOURCE_TIE_TOLERANCE_PX]
     * instead of an exact minimum treats such near-ties as ties, so rule 2/3/4 (whichever is more
     * deterministic given noisy geometry) decide instead of raw distance noise flipping the winner
     * capture to capture - the single biggest source of "[Direction] Inconsistent junction
     * orientation" flicker observed on the real table.
     */
    private fun nearestSourceCandidates(keyA: String, keyB: String): List<Candidate> {
        val perSource = routingBySource.entries.mapNotNull { (sourceKey, routing) ->
            val source = sources.first { logicalNodeKey(it) == sourceKey }
            val (distanceByKey, previousByKey) = routing
            val distanceA = distanceByKey[keyA]
            val distanceB = distanceByKey[keyB]
            if (distanceA == null && distanceB == null) return@mapNotNull null

            val (entryKey, farKey, segmentDistance) = when {
                distanceB == null || (distanceA != null && distanceA <= distanceB) -> Triple(keyA, keyB, distanceA!!)
                else -> Triple(keyB, keyA, distanceB)
            }
            Candidate(source, entryKey, farKey, previousByKey[entryKey]) to segmentDistance
        }
        if (perSource.isEmpty()) return emptyList()

        val nearestDistance = perSource.minOf { it.second }
        return perSource.filter { abs(it.second - nearestDistance) < NEAREST_SOURCE_TIE_TOLERANCE_PX }.map { it.first }
    }

    /** Rule 2: does [source]'s configured colors have a reachable matching target anywhere in the network? */
    private fun canReachMatchingTarget(startKey: String, source: SourceNode): Boolean {
        val colors = source.configurations.map { it.color.name }.toSet()
        if (colors.isEmpty()) return false

        val targetsByKey = targets.associateBy { logicalNodeKey(it) }
        if (matchesAnyColor(targetsByKey[startKey], colors)) return true

        val visited = mutableSetOf(startKey)
        val queue = ArrayDeque<String>()
        queue.add(startKey)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (edge in adjacency[current].orEmpty()) {
                if (edge.neighborKey in visited) continue
                visited.add(edge.neighborKey)
                if (matchesAnyColor(targetsByKey[edge.neighborKey], colors)) return true
                queue.add(edge.neighborKey)
            }
        }
        return false
    }

    /** True when [target] exists and its color is one of [colors]. */
    private fun matchesAnyColor(target: TargetNode?, colors: Set<String>): Boolean =
        target != null && target.color.name in colors

    /** Rule 3: angle between [candidate]'s incoming shortest-path direction and the segment's own direction. */
    private fun bendAngle(candidate: Candidate): Double? {
        val previousKey = candidate.previousOfEntry ?: return null
        val previousNode = nodeByKey[previousKey] ?: return null
        val entryNode = nodeByKey[candidate.entryKey] ?: return null
        val farNode = nodeByKey[candidate.farKey] ?: return null

        val incoming = direction(previousNode.position, entryNode.position) ?: return null
        val outgoing = direction(entryNode.position, farNode.position) ?: return null

        var diff = abs(atan2(incoming.second, incoming.first) - atan2(outgoing.second, outgoing.first))
        if (diff > PI) diff = 2.0 * PI - diff
        return diff
    }

    /** Unit direction vector from [from] to [to], or null for a degenerate (zero-length) step. */
    private fun direction(from: Point, to: Point): Pair<Double, Double>? {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = sqrt(dx * dx + dy * dy)
        if (length < 1e-9) return null
        return (dx / length) to (dy / length)
    }

    /** Rule 4: stable, reproducible fallback so a genuine remaining tie always yields one direction. */
    private fun fallbackForward(keyA: String, keyB: String): Boolean = keyA <= keyB

    /**
     * The direction hard-required by [segment]'s endpoints, or null when the endpoints do not
     * constrain it: a street touching exactly one source endpoint must always flow *away* from that
     * source, and a street touching exactly one target endpoint (and no source) must always flow
     * *into* that target. The source rule is checked first, so a source-to-target street is oriented
     * away from the source (which also satisfies the target side).
     *
     * The endpoint classification is supplied by the caller ([isSource]/[isTarget]) so it can match
     * the caller's own node-identity rules - e.g. [imageprocessing.StreetDetection]'s proximity
     * matching, which merges detected nodes within its node-match radius into the level's
     * source/target nodes when a detection is committed. Segments whose endpoints classify the same
     * way on both sides (source-to-source or target-to-target) are physically unsatisfiable either
     * way and yield null, leaving them to the regular rule chain and later validation.
     *
     * @param segment The segment whose endpoints are checked.
     * @param isSource Whether a node counts as (or will be merged into) a source node.
     * @param isTarget Whether a node counts as (or will be merged into) a target node.
     * @return true for nodeA -> nodeB, false for nodeB -> nodeA, or null when unconstrained.
     */
    fun requiredEndpointDirection(
        segment: UndirectedSegment,
        isSource: (Node) -> Boolean,
        isTarget: (Node) -> Boolean
    ): Boolean? {
        val startIsSource = isSource(segment.nodeA)
        val endIsSource = isSource(segment.nodeB)
        if (startIsSource != endIsSource) return startIsSource
        if (startIsSource) return null

        val startIsTarget = isTarget(segment.nodeA)
        val endIsTarget = isTarget(segment.nodeB)
        if (startIsTarget != endIsTarget) return endIsTarget
        return null
    }

    /**
     * Final guarantee of the source/target orientation rule: flips every entry of [directions] that
     * violates [requiredEndpointDirection] (a street pointing *into* a source or *out of* a target).
     *
     * With source/target-touching segments locked up front this pass never needs to flip anything;
     * it exists as a hard invariant so no later orientation stage (demand flow, rule chain,
     * dead-end repair) can ever reintroduce a street driving into a source, which the simulation
     * would otherwise interpret as a drivable approach and the renderer would draw with an arrow
     * pointing at the source.
     *
     * @param directions Every segment's decided direction (true means nodeA -> nodeB).
     * @param isSource Whether a node counts as (or will be merged into) a source node.
     * @param isTarget Whether a node counts as (or will be merged into) a target node.
     * @return [directions] with violating entries flipped, preserving iteration order.
     */
    fun enforceSourceTargetOrientation(
        directions: Map<UndirectedSegment, Boolean>,
        isSource: (Node) -> Boolean,
        isTarget: (Node) -> Boolean
    ): Map<UndirectedSegment, Boolean> =
        directions.mapValues { (segment, forward) ->
            requiredEndpointDirection(segment, isSource, isTarget) ?: forward
        }

    /**
     * Repairs dead-end junctions in [allDirections]: a [ConnectionNode] where every incident segment
     * ended up oriented toward it has no way out, even though flowing along a redundant (non-bridge)
     * segment instead would not disconnect the network. This can happen when a network loop gives one
     * end of a multi-segment connector a shorter graph-distance path to its nearest source than going
     * straight through the connector itself, so rule 1 orients both ends toward their shared point.
     *
     * Only segments present in [flippableSegments] may be reversed; segments whose direction is a
     * hard requirement (e.g. touching a source or target directly, part of a detected roundabout, or
     * belonging to a bridge/ramp elevation chain) are used only to compute each node's current
     * outgoing-street count, never flipped themselves.
     *
     * Repeats until either no dead end remains, or the only incoming segments left at a dead end are
     * bridges - a genuine dead-end branch with no alternate route, correctly left for
     * [domain.LevelValidator] to report exactly as before this repair pass existed.
     *
     * Fixing one dead end can shift the problem: the flipped segment's own source side loses its only
     * outgoing street and becomes the next dead end, which can in turn require flipping a neighboring
     * segment, and so on. [MAX_REPAIR_ITERATIONS] bounds this cascade; a network small enough to detect
     * on a table never needs anywhere near that many flips to resolve a genuinely repairable case, so
     * hitting the limit means no stable repair exists here - the original, unrepaired directions are
     * returned rather than an arbitrary, possibly still-broken intermediate state.
     *
     * @param allDirections Every segment's current direction (true means nodeA -> nodeB), both locked
     * and flippable ones.
     * @param flippableSegments The subset of [allDirections]' keys that may be reversed.
     * @return [allDirections] with zero or more flippable entries reversed to remove repairable dead
     * ends, or unchanged if no stable repair was found within the iteration bound.
     */
    fun repairDeadEnds(
        allDirections: Map<UndirectedSegment, Boolean>,
        flippableSegments: Set<UndirectedSegment>
    ): Map<UndirectedSegment, Boolean> {
        val directions = LinkedHashMap(allDirections)
        val maxIterations = (allDirections.size + 1) * MAX_REPAIR_ITERATIONS_PER_SEGMENT

        repeat(maxIterations) {
            val outgoingCounts = mutableMapOf<String, Int>()
            val incomingByNode = mutableMapOf<String, MutableList<UndirectedSegment>>()
            for ((segment, forward) in directions) {
                val fromKey = if (forward) logicalNodeKey(segment.nodeA) else logicalNodeKey(segment.nodeB)
                val toKey = if (forward) logicalNodeKey(segment.nodeB) else logicalNodeKey(segment.nodeA)
                outgoingCounts[fromKey] = (outgoingCounts[fromKey] ?: 0) + 1
                incomingByNode.getOrPut(toKey) { mutableListOf() }.add(segment)
            }

            val deadEndKey = incomingByNode.keys
                .filter { (outgoingCounts[it] ?: 0) == 0 && nodeByKey[it] is ConnectionNode }
                .sorted()
                .firstOrNull() ?: return directions

            val toFlip = incomingByNode.getValue(deadEndKey)
                .filter { it in flippableSegments }
                .sortedBy { segmentKey(it) }
                .firstOrNull { isNonBridge(it) }
                ?: return directions

            directions[toFlip] = !directions.getValue(toFlip)
        }

        return allDirections
    }

    /** Stable ordering key for a segment, independent of map iteration order. */
    private fun segmentKey(segment: UndirectedSegment): String =
        "${logicalNodeKey(segment.nodeA)}|${logicalNodeKey(segment.nodeB)}"

    /**
     * True when [segment] is a ground-level non-bridge: both endpoints are on elevation 0 and stay
     * connected via some other path if this edge is removed. Elevated deck/ramp segments must not be
     * flipped by the dead-end repair, because their direction is part of the physical 0->1->2->1->0
     * bridge sequence and reversing one transition disconnects otherwise valid bridge detections.
     */
    private fun isNonBridge(segment: UndirectedSegment): Boolean {
        if (segment.nodeA.elevation != 0 || segment.nodeB.elevation != 0) return false

        val startKey = logicalNodeKey(segment.nodeA)
        val endKey = logicalNodeKey(segment.nodeB)

        val visited = mutableSetOf(startKey)
        val queue = ArrayDeque<String>()
        queue.add(startKey)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (edge in adjacency[current].orEmpty()) {
                val isTestedEdge = (current == startKey && edge.neighborKey == endKey) ||
                    (current == endKey && edge.neighborKey == startKey)
                if (isTestedEdge || edge.neighborKey in visited) continue
                visited.add(edge.neighborKey)
                queue.add(edge.neighborKey)
            }
        }
        return endKey in visited
    }

    /** Runs Dijkstra's algorithm from [startKey] over the undirected skeleton graph. */
    private fun dijkstraFrom(startKey: String): Pair<Map<String, Double>, Map<String, String>> {
        val distance = mutableMapOf(startKey to 0.0)
        val previous = mutableMapOf<String, String>()
        val visited = mutableSetOf<String>()
        val queue = PriorityQueue<Pair<Double, String>>(compareBy { it.first })
        queue.add(0.0 to startKey)

        while (queue.isNotEmpty()) {
            val (dist, key) = queue.poll()
            if (!visited.add(key)) continue

            for (edge in adjacency[key].orEmpty()) {
                val candidateDistance = dist + edge.length
                if (candidateDistance < (distance[edge.neighborKey] ?: Double.POSITIVE_INFINITY) - DIJKSTRA_RELAXATION_EPSILON) {
                    distance[edge.neighborKey] = candidateDistance
                    previous[edge.neighborKey] = key
                    queue.add(candidateDistance to edge.neighborKey)
                }
            }
        }

        return distance to previous
    }

    /** Total Euclidean length of a polyline. */
    private fun pathLength(path: List<Point>): Double {
        var length = 0.0
        for (i in 1 until path.size) {
            val dx = path[i].x - path[i - 1].x
            val dy = path[i].y - path[i - 1].y
            length += sqrt(dx * dx + dy * dy)
        }
        return length
    }

    /** Builds a position-and-elevation key for logical nodes, matching [imageprocessing.StreetDetection]'s node identity. */
    private fun logicalNodeKey(node: Node): String {
        val x = Math.round(node.position.x).toString()
        val y = Math.round(node.position.y).toString()
        return "$x:$y:${node.elevation}"
    }

    companion object {
        /**
         * Numerical-stability epsilon for Dijkstra edge relaxation only (both [dijkstraFrom] and
         * [constrainedShortestPath]) - keeps a floating-point-equal candidate distance from re-queuing
         * the same node forever. Deliberately tiny and NOT a noise-tolerance knob: widening this would
         * make Dijkstra itself ignore genuinely shorter paths (by up to this many detector pixels),
         * silently corrupting the shortest-path/predecessor results rule 1 and rule 3 depend on. Noise
         * tolerance for *tie-breaking* lives in [NEAREST_SOURCE_TIE_TOLERANCE_PX]/[ANGLE_TIE_TOLERANCE_RADIANS]
         * instead, applied only after a correct shortest-path search has already run.
         */
        private const val DIJKSTRA_RELAXATION_EPSILON = 1e-6

        /**
         * Rule-1 tie tolerance, in detector pixels (the same 1800x1000-canvas-over-90x50cm-desk space
         * [imageprocessing.StreetDetection] and [RoundaboutGeometry]'s defaults use). Two sources whose
         * summed path length over the detected skeleton differs by less than this are treated as
         * equally near, so noise in that sum (a wobbly skeleton point shifting a few pixels between
         * captures, well within the single-digit-pixel jitter a real camera produces) cannot alone flip
         * which source "wins" a segment's direction. Chosen well above single-point jitter but far
         * below the distance between physically distinct sources (junctions in this codebase are
         * typically tens to hundreds of pixels apart - see e.g. [RoundaboutGeometry]'s own
         * 80px `ROUNDABOUT_RADIUS_TOLERANCE`), so genuinely different distances still decide cleanly.
         */
        private const val NEAREST_SOURCE_TIE_TOLERANCE_PX = 20.0

        /**
         * Rule-3 tie tolerance, in radians (~6 degrees). [bendAngle] is measured between two direction
         * vectors derived from detected node positions, so it inherits the same per-capture position
         * noise as [NEAREST_SOURCE_TIE_TOLERANCE_PX] - over a typical short bend-angle baseline that
         * noise can move the computed angle by several degrees without the physical street continuation
         * having changed at all. Wide enough to absorb that, still far tighter than a real turn (a
         * street actually turning at a junction bends by tens of degrees, not single digits).
         */
        private const val ANGLE_TIE_TOLERANCE_RADIANS = 0.1

        /** Safety bound on [repairDeadEnds]'s cascade, scaled by network size - see its doc comment. */
        private const val MAX_REPAIR_ITERATIONS_PER_SEGMENT = 4
    }
}
