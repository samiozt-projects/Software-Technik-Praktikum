package domain

import korlibs.math.geom.Point
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Detects circular junctions (roundabouts) from a set of connection nodes and derives the
 * right-hand traffic direction between two nodes on a detected circle.
 *
 * Shared by [StreetPathFinder] (path-preview adjacency) and [imageprocessing.StreetDetection]
 * (assigning the one-way direction to detected roundabout segments), so a roundabout is
 * recognized and driven consistently wherever streets are used.
 */
object RoundaboutGeometry {
    /**
     * A detected roundabout.
     * @property nodeKeys Logical keys of the junction nodes that lie on the circle.
     * @property center The fitted circle's center.
     * @property radius The fitted circle's radius, so a later call can re-validate this exact circle
     * (see [detect]'s `previousCandidate` parameter) instead of only remembering its center.
     */
    data class Candidate(val nodeKeys: Set<String>, val center: Point, val radius: Double)

    /**
     * One undirected physical street connection between two nodes, including its polyline, so
     * [detect] can check that ring segments actually follow the fitted circle instead of cutting
     * straight across it (a rectangular city block's outline passes a plain circle fit, but its
     * edges bow far off the circle).
     *
     * @property nodeA One endpoint.
     * @property nodeB The other endpoint.
     * @property path The polyline between [nodeA] and [nodeB].
     */
    data class RingEdge(val nodeA: Node, val nodeB: Node, val path: List<Point>)

    /**
     * A circle fit to three points.
     * @property center The circle's center.
     * @property radius The circle's radius.
     */
    private data class CircleFit(val center: Point, val radius: Double)

    /**
     * One junction-triple's candidate roundabout: the resulting [Candidate] plus how well it fits,
     * so [detect] can keep whichever candidate fits best across all triples.
     * @property candidate The roundabout candidate this triple produced.
     * @property inlierCount How many junctions lie on the fitted circle.
     * @property error Mean distance of the inlier junctions from the fitted circle's radius.
     */
    private data class ScoredCandidate(val candidate: Candidate, val inlierCount: Int, val error: Double)

    /**
     * Finds the best-fitting roundabout among [junctions], if at least [MIN_ROUNDABOUT_JUNCTIONS] of
     * them lie on a common circle within [radiusTolerance].
     *
     * Any 3 non-collinear points trivially define a circle passing exactly through all 3 of them, so
     * a circle fit alone cannot tell a real roundabout apart from 3 unrelated, merely non-collinear
     * junctions elsewhere in the network. Three additional requirements reject the false positives a
     * plain fit accepts (observed live: a city-block outline or the outer loop of a street grid was
     * "detected" as a roundabout and its segments force-locked to ring direction, corrupting normal
     * one-way streets):
     *  * **Ring wiring**: the fitted junctions must connect to each other as one closed ring (each
     *    exactly two ring neighbors, one cycle over all of them - [formsSingleRing]).
     *  * **Compactness**: the circle's radius must stay within [maxRadius]; a physical roundabout
     *    element is small, a same-shaped street loop spanning half the table is not a roundabout.
     *  * **Path conformity**: every ring segment's polyline must stay within
     *    [GameSimulationConfig.ROUNDABOUT_MAX_PATH_RADIUS_DEVIATION_FRACTION] of the radius from the
     *    circle line ([ringPathsFollowCircle]); a rectangular block's straight edges cut far inside
     *    the circumcircle, a real roundabout's segments follow it.
     *
     * [radiusTolerance] and [maxRadius] are parameters because callers work in different units
     * (detector pixels vs. virtual metres); pass values in the same unit as the node positions.
     *
     * A caller that re-runs [detect] repeatedly on the same physical layout (camera-driven detection
     * across consecutive captures) can pass the previous call's result as [previousCandidate] to get
     * hysteresis, Schmitt-trigger style: *entering* the roundabout state still requires the normal,
     * tighter [radiusTolerance]; but once detected, *staying* classified as the same roundabout only
     * requires the wider [stickyRadiusTolerance]. Without this, a real roundabout whose fitted radius
     * sits right at the edge of [radiusTolerance] flips in and out of classification on nothing but
     * per-capture skeleton noise - and each flip changes every ring segment's locked direction,
     * breaking the exact-repeat network signature camera detection needs to confirm anything at all.
     * A roundabout that is genuinely gone (too few junctions left near [previousCandidate], or its
     * ring wiring/path conformity no longer holds even at the wider tolerance) still falls through to
     * the normal from-scratch search below, so a real removal is still detected correctly.
     *
     * @param junctions Candidate junction nodes.
     * @param ringEdges Undirected street connections between nodes, with their polylines.
     * @param radiusTolerance Maximum distance of an inlier junction from the fitted circle.
     * @param maxRadius Maximum acceptable circle radius.
     * @param previousCandidate The roundabout (if any) a prior call to [detect] on the same evolving
     * layout returned, so this call can preferentially re-confirm it. Pass null for a one-shot
     * detection with no history (e.g. [StreetPathFinder]'s per-request path preview).
     * @param stickyRadiusTolerance Wider tolerance used only to re-validate [previousCandidate].
     */
    fun detect(
        junctions: List<Node>,
        ringEdges: List<RingEdge>,
        radiusTolerance: Double = ROUNDABOUT_RADIUS_TOLERANCE,
        maxRadius: Double = ROUNDABOUT_MAX_RADIUS_DEFAULT,
        previousCandidate: Candidate? = null,
        stickyRadiusTolerance: Double = radiusTolerance * ROUNDABOUT_STICKY_TOLERANCE_MULTIPLIER
    ): Candidate? {
        val distinctJunctions = junctions.distinctBy { logicalNodeKey(it) }
        if (distinctJunctions.size < MIN_ROUNDABOUT_JUNCTIONS) return null

        val neighborKeysByKey = buildNeighborIndex(ringEdges)

        if (previousCandidate != null) {
            val sticky = revalidateSticky(distinctJunctions, previousCandidate, neighborKeysByKey, ringEdges, stickyRadiusTolerance, maxRadius)
            if (sticky != null) return sticky
        }

        var best: ScoredCandidate? = null
        for ((i, j, k) in junctionTriples(distinctJunctions.size)) {
            val scored = scoreCandidateCircle(distinctJunctions, i, j, k, neighborKeysByKey, ringEdges, radiusTolerance, maxRadius)
                ?: continue
            if (best == null || scored.inlierCount > best.inlierCount ||
                scored.inlierCount == best.inlierCount && scored.error < best.error
            ) {
                best = scored
            }
        }

        return best?.candidate
    }

    /**
     * Re-validates [previous] against the current [junctions] at the wider [stickyRadiusTolerance],
     * reusing its exact circle (center and radius) instead of fitting a new one - see [detect]'s
     * `previousCandidate` doc for why. Returns null (falling through to a from-scratch search) when
     * [previous]'s radius no longer fits [maxRadius], too few current junctions remain near its
     * circle, they no longer wire as a single ring, or their paths no longer follow the circle.
     */
    private fun revalidateSticky(
        junctions: List<Node>,
        previous: Candidate,
        neighborKeysByKey: Map<String, Set<String>>,
        ringEdges: List<RingEdge>,
        stickyRadiusTolerance: Double,
        maxRadius: Double
    ): Candidate? {
        if (previous.radius > maxRadius) return null
        val circle = CircleFit(previous.center, previous.radius)

        val inliers = junctions.filter {
            abs(it.position.distanceTo(circle.center) - circle.radius) <= stickyRadiusTolerance
        }
        if (inliers.size < MIN_ROUNDABOUT_JUNCTIONS) return null
        if (!formsSingleRing(inliers, neighborKeysByKey)) return null
        if (!ringPathsFollowCircle(inliers, ringEdges, circle)) return null

        return Candidate(nodeKeys = inliers.map { logicalNodeKey(it) }.toSet(), center = circle.center, radius = circle.radius)
    }

    /** Indexes [ringEdges] as an undirected adjacency set keyed by logical node key. */
    private fun buildNeighborIndex(ringEdges: List<RingEdge>): Map<String, Set<String>> {
        val neighborKeysByKey = mutableMapOf<String, MutableSet<String>>()
        for (edge in ringEdges) {
            val keyA = logicalNodeKey(edge.nodeA)
            val keyB = logicalNodeKey(edge.nodeB)
            neighborKeysByKey.getOrPut(keyA) { mutableSetOf() }.add(keyB)
            neighborKeysByKey.getOrPut(keyB) { mutableSetOf() }.add(keyA)
        }
        return neighborKeysByKey
    }

    /** Every distinct combination of three indices into a list of the given [size], as (i, j, k) with i < j < k. */
    private fun junctionTriples(size: Int): List<Triple<Int, Int, Int>> {
        val triples = mutableListOf<Triple<Int, Int, Int>>()
        for (i in 0 until size - 2) {
            for (j in i + 1 until size - 1) {
                for (k in j + 1 until size) {
                    triples.add(Triple(i, j, k))
                }
            }
        }
        return triples
    }

    /**
     * Fits a circle through the junctions at indices [i], [j] and [k] and scores it as a roundabout
     * candidate, or returns null when that circle does not qualify: too few inliers, radius beyond
     * [maxRadius], inliers not wired as one ring ([formsSingleRing]), or ring segment paths that do
     * not follow the circle ([ringPathsFollowCircle]).
     */
    private fun scoreCandidateCircle(
        junctions: List<Node>,
        i: Int,
        j: Int,
        k: Int,
        neighborKeysByKey: Map<String, Set<String>>,
        ringEdges: List<RingEdge>,
        radiusTolerance: Double,
        maxRadius: Double
    ): ScoredCandidate? {
        val circle = circumcircle(junctions[i].position, junctions[j].position, junctions[k].position) ?: return null
        if (circle.radius > maxRadius) return null

        val inliers = junctions.filter {
            abs(it.position.distanceTo(circle.center) - circle.radius) <= radiusTolerance
        }
        if (inliers.size < MIN_ROUNDABOUT_JUNCTIONS) return null
        if (!formsSingleRing(inliers, neighborKeysByKey)) return null
        if (!ringPathsFollowCircle(inliers, ringEdges, circle)) return null

        val error = inliers.map { abs(it.position.distanceTo(circle.center) - circle.radius) }.average()
        val candidate = Candidate(nodeKeys = inliers.map { logicalNodeKey(it) }.toSet(), center = circle.center, radius = circle.radius)
        return ScoredCandidate(candidate, inliers.size, error)
    }

    /**
     * True when every ring segment between two [inliers] keeps its whole polyline within
     * [GameSimulationConfig.ROUNDABOUT_MAX_PATH_RADIUS_DEVIATION_FRACTION] of the radius from the
     * fitted circle line. A real roundabout's segments follow the circle (straight chords of an
     * n-gon bow inward by 1 - cos(PI/n) of the radius, ~13% for six segments); the straight edges of
     * a coincidentally circle-fitting street block cut much deeper (~29% for a rectangle), so this
     * cleanly separates the two even though both pass the plain junction circle fit.
     */
    private fun ringPathsFollowCircle(inliers: List<Node>, ringEdges: List<RingEdge>, circle: CircleFit): Boolean {
        val inlierKeys = inliers.map { logicalNodeKey(it) }.toSet()
        val maxDeviation = circle.radius * ROUNDABOUT_MAX_PATH_RADIUS_DEVIATION_FRACTION

        for (edge in ringEdges) {
            if (logicalNodeKey(edge.nodeA) !in inlierKeys || logicalNodeKey(edge.nodeB) !in inlierKeys) continue
            for (point in edge.path) {
                if (abs(point.distanceTo(circle.center) - circle.radius) > maxDeviation) return false
            }
            // Also probe the midpoint of every straight sub-segment: a sparse polyline (e.g. a plain
            // two-point chord) has both stored points ON the circle while its middle - where a chord
            // deviates the most - has no stored point at all.
            for (index in 1 until edge.path.size) {
                val midpoint = Point(
                    (edge.path[index - 1].x + edge.path[index].x) / 2.0,
                    (edge.path[index - 1].y + edge.path[index].y) / 2.0
                )
                if (abs(midpoint.distanceTo(circle.center) - circle.radius) > maxDeviation) return false
            }
        }
        return true
    }

    /**
     * True when [candidates], restricted to [neighborKeysByKey], forms exactly one simple cycle
     * covering every candidate: each one connects to precisely two others in the set, and following
     * those connections visits all of them.
     */
    private fun formsSingleRing(candidates: List<Node>, neighborKeysByKey: Map<String, Set<String>>): Boolean {
        val keys = candidates.map { logicalNodeKey(it) }.toSet()

        for (key in keys) {
            val neighborsInSet = neighborKeysByKey[key]?.count { it in keys } ?: 0
            if (neighborsInSet != 2) return false
        }

        val start = keys.first()
        val visited = mutableSetOf(start)
        val queue = ArrayDeque<String>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (neighbor in neighborKeysByKey[current].orEmpty()) {
                if (neighbor !in keys || neighbor in visited) continue
                visited.add(neighbor)
                queue.add(neighbor)
            }
        }
        return visited.size == keys.size
    }

    /** True when travelling from [start] to [end] around [center] follows the right-hand direction. */
    fun isRightHandDirection(start: Node, end: Node, center: Point): Boolean {
        val startAngle = atan2(start.position.y - center.y, start.position.x - center.x)
        val endAngle = atan2(end.position.y - center.y, end.position.x - center.x)
        return normalizeAngle(endAngle - startAngle) < 0.0
    }

    /** Calculates the circumcircle of three points. */
    private fun circumcircle(a: Point, b: Point, c: Point): CircleFit? {
        val d = 2.0 * (
            a.x * (b.y - c.y) +
                b.x * (c.y - a.y) +
                c.x * (a.y - b.y)
            )
        if (abs(d) < 0.0001) return null

        val ax2ay2 = a.x * a.x + a.y * a.y
        val bx2by2 = b.x * b.x + b.y * b.y
        val cx2cy2 = c.x * c.x + c.y * c.y
        val centerX = (
            ax2ay2 * (b.y - c.y) +
                bx2by2 * (c.y - a.y) +
                cx2cy2 * (a.y - b.y)
            ) / d
        val centerY = (
            ax2ay2 * (c.x - b.x) +
                bx2by2 * (a.x - c.x) +
                cx2cy2 * (b.x - a.x)
            ) / d

        val center = Point(centerX, centerY)
        return CircleFit(center, center.distanceTo(a))
    }

    /** Normalizes an angle to be within the range of -PI to PI. */
    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle
        while (normalized <= -PI) normalized += 2.0 * PI
        while (normalized > PI) normalized -= 2.0 * PI
        return normalized
    }

    /** Builds a position-and-elevation key for logical nodes. */
    private fun logicalNodeKey(node: Node): String {
        val x = node.position.x.roundToInt()
        val y = node.position.y.roundToInt()
        return "$x:$y:${node.elevation}"
    }

    private const val MIN_ROUNDABOUT_JUNCTIONS = GameSimulationConfig.MIN_ROUNDABOUT_JUNCTIONS
    private const val ROUNDABOUT_RADIUS_TOLERANCE = GameSimulationConfig.ROUNDABOUT_RADIUS_TOLERANCE
    private const val ROUNDABOUT_MAX_PATH_RADIUS_DEVIATION_FRACTION =
        GameSimulationConfig.ROUNDABOUT_MAX_PATH_RADIUS_DEVIATION_FRACTION

    /**
     * Default width of the [detect] hysteresis band: how much wider than the entry [radiusTolerance]
     * the sticky re-validation tolerance is, by default. 1.5x mirrors the kind of margin
     * [ROUNDABOUT_MAX_PATH_RADIUS_DEVIATION_FRACTION]'s own doc comment uses to separate a real
     * roundabout (~13% path deviation) from a false positive (~29%) - wide enough to absorb ordinary
     * per-capture measurement noise at the boundary, nowhere near wide enough to let an unrelated,
     * clearly-different circle re-confirm as the same roundabout.
     */
    private const val ROUNDABOUT_STICKY_TOLERANCE_MULTIPLIER = 1.5

    /**
     * Default [detect] radius cap in detector pixels (30 m at 8 px/m). Callers in metre space must
     * pass [GameSimulationConfig.MAX_ROUNDABOUT_RADIUS_METERS] explicitly.
     */
    private const val ROUNDABOUT_MAX_RADIUS_DEFAULT = GameSimulationConfig.MAX_ROUNDABOUT_RADIUS_METERS * 8.0
}
