package domaintests

import domain.Color
import domain.ConnectionNode
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.StreetDirectionResolver
import domain.TargetNode
import domain.UndirectedSegment
import domain.VehicleType
import korlibs.image.color.Colors
import korlibs.math.geom.degrees
import korlibs.math.geom.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests [StreetDirectionResolver]'s rule chain for junction-to-junction street segments (streets
 * that do not touch a source or target directly): nearest source by path distance, then a demand
 * filter, then direction continuity, then a deterministic fallback. Each stage is exercised with a
 * deliberate tie so the next rule is what actually decides the outcome.
 */
class StreetDirectionResolverTest {

    private val red = Color("Rot", Colors.RED)
    private val blue = Color("Blau", Colors.BLUE)
    private val car = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)

    private fun sourceConfig(color: Color) = SourceNodeConfiguration(color, 5.0, 50.0, car)

    /**
     * Clear-cut rule 1 case: only one source is anywhere near the segment, so its side dictates the
     * direction without needing any tie-break.
     */
    @Test
    fun resolveForward_singleNearestSource_decidesWithoutTieBreak() {
        val source = SourceNode(Point(-100, 0), 0, 0.degrees, listOf(sourceConfig(red)))
        val a = ConnectionNode(Point(100, 0), 0)
        val b = ConnectionNode(Point(200, 0), 0)
        val target = TargetNode(Point(400, 0), 0, 0.degrees, red)

        val disputed = UndirectedSegment(a, b, listOf(a.position, b.position))
        val segments = listOf(
            UndirectedSegment(source, a, listOf(source.position, a.position)),
            disputed,
            UndirectedSegment(b, target, listOf(b.position, target.position))
        )

        val resolver = StreetDirectionResolver(segments, listOf(source), listOf(target))

        // The source is closest to `a`, so traffic must continue away from it, towards `b`.
        assertTrue(resolver.resolveForward(disputed), "segment should be oriented a -> b, away from the source")
    }

    /**
     * Rule 1 tie: two sources sit an equal path-distance away from opposite ends of the disputed
     * segment. Rule 2 (demand filter) then decides because only one of the two sources has a
     * reachable, color-matching target anywhere in the network — the other source's color has no
     * target at all, so its route would lead nowhere.
     */
    @Test
    fun resolveForward_rule1Tie_resolvedByDemandFilter() {
        val sourceRed = SourceNode(Point(0, 0), 0, 0.degrees, listOf(sourceConfig(red)))
        val sourceBlue = SourceNode(Point(300, 0), 0, 0.degrees, listOf(sourceConfig(blue)))
        val a = ConnectionNode(Point(100, 0), 0)
        val b = ConnectionNode(Point(200, 0), 0)
        val targetRed = TargetNode(Point(100, 100), 0, 0.degrees, red)
        // Deliberately no Blue target anywhere in the network.

        val disputed = UndirectedSegment(a, b, listOf(a.position, b.position))
        val segments = listOf(
            UndirectedSegment(sourceRed, a, listOf(sourceRed.position, a.position)),
            UndirectedSegment(b, sourceBlue, listOf(b.position, sourceBlue.position)),
            disputed,
            UndirectedSegment(a, targetRed, listOf(a.position, targetRed.position))
        )

        val resolver = StreetDirectionResolver(segments, listOf(sourceRed, sourceBlue), listOf(targetRed))

        // Both sources are exactly 100 units from the segment (rule 1 ties), but sourceBlue's color
        // has no reachable target at all, so the demand filter drops it and sourceRed decides.
        assertTrue(resolver.resolveForward(disputed), "sourceRed's demand should win, orienting a -> b")
    }

    /**
     * Rule 1 and rule 2 both tie (both sources are equidistant and both have a reachable matching
     * target through the segment), so rule 3 (direction continuity) decides: the source whose
     * shortest path arrives the straightest wins.
     */
    @Test
    fun resolveForward_rule1And2Tie_resolvedByDirectionContinuity() {
        val straightSource = SourceNode(Point(-100, 0), 0, 0.degrees, listOf(sourceConfig(red)))
        val bendingSource = SourceNode(Point(100, -200), 0, 0.degrees, listOf(sourceConfig(red)))
        val a = ConnectionNode(Point(100, 0), 0)
        val b = ConnectionNode(Point(200, 0), 0)
        val target = TargetNode(Point(300, 0), 0, 0.degrees, red)

        val disputed = UndirectedSegment(a, b, listOf(a.position, b.position))
        val segments = listOf(
            UndirectedSegment(straightSource, a, listOf(straightSource.position, a.position)),
            UndirectedSegment(bendingSource, a, listOf(bendingSource.position, a.position)),
            disputed,
            UndirectedSegment(b, target, listOf(b.position, target.position))
        )

        val resolver = StreetDirectionResolver(
            segments,
            listOf(straightSource, bendingSource),
            listOf(target)
        )

        // Both sources reach `a` at the same distance (200) and both can reach the shared Red
        // target beyond `b`, but only straightSource continues into the segment without bending.
        assertTrue(resolver.resolveForward(disputed), "the straighter approach should win, orienting a -> b")
    }

    /**
     * All three source-based rules tie: two sources are equidistant from opposite ends of the
     * segment and both approach in a perfectly straight line (0 deg bend, just from opposite
     * sides). Only the deterministic endpoint-key fallback can settle this, and it must do so the
     * same way every time.
     */
    @Test
    fun resolveForward_allSourceRulesTie_fallsBackToDeterministicOrder() {
        val leftSource = SourceNode(Point(-100, 0), 0, 0.degrees, emptyList())
        val rightSource = SourceNode(Point(400, 0), 0, 0.degrees, emptyList())
        val a = ConnectionNode(Point(100, 0), 0)
        val b = ConnectionNode(Point(200, 0), 0)

        val disputed = UndirectedSegment(a, b, listOf(a.position, b.position))
        val segments = listOf(
            UndirectedSegment(leftSource, a, listOf(leftSource.position, a.position)),
            UndirectedSegment(rightSource, b, listOf(rightSource.position, b.position)),
            disputed
        )

        val resolverForward = StreetDirectionResolver(segments, listOf(leftSource, rightSource), emptyList())
        val resolverReordered = StreetDirectionResolver(segments, listOf(rightSource, leftSource), emptyList())

        val result1 = resolverForward.resolveForward(disputed)
        val result2 = resolverForward.resolveForward(disputed)
        val result3 = resolverReordered.resolveForward(disputed)

        assertEquals(result1, result2, "resolving the same segment twice must yield the same direction")
        assertEquals(
            result1,
            result3,
            "the fallback must depend only on the endpoint keys, not on source list order"
        )
    }

    /** A genuine one-way dead end: the only physical connection runs the wrong way for this source. */
    @Test
    fun resolveForward_doesNotInventABidirectionalEscape_forADeadEndSegment() {
        val source = SourceNode(Point(0, 0), 0, 0.degrees, listOf(sourceConfig(red)))
        val deadEnd = ConnectionNode(Point(100, 0), 0)
        val stub = ConnectionNode(Point(200, 0), 0)

        // No target anywhere reachable through the stub - this branch genuinely leads nowhere.
        val disputed = UndirectedSegment(deadEnd, stub, listOf(deadEnd.position, stub.position))
        val segments = listOf(
            UndirectedSegment(source, deadEnd, listOf(source.position, deadEnd.position)),
            disputed
        )

        val resolver = StreetDirectionResolver(segments, listOf(source), emptyList())

        // Exactly one direction must still come out (never "both"), continuing away from the source,
        // and it must be the same direction on repeated resolution of the same physical segment.
        assertTrue(resolver.resolveForward(disputed), "direction should continue away from the source (deadEnd -> stub)")
        assertTrue(resolver.resolveForward(disputed), "repeated resolution of the same segment must be reproducible")
    }

    /**
     * DIAGNOSTIC (issue reported 2026-07-10): reproduces a *single*-source network shaped like a
     * ladder - two roads connected by two crossbars (as in the reported level) - where one crossbar
     * was detected as two segments (e.g. a spurious mid-point junction split what should be one
     * physical connector). Only [SourceNode]s ever enter [StreetDirectionResolver]'s source list;
     * this test intentionally has none of the network's [TargetNode]s at all, confirming they play
     * no role in the outcome.
     *
     * Rule 1 resolves each segment *independently* by comparing graph distance from the (one)
     * source to its own two endpoints - it has no notion of "does this direction connect through to
     * the neighboring segment". Because the *other* crossbar offers a shorter loop-around path to
     * the split connector's far end than going straight through it, that far end's shortest path to
     * the source no longer runs through the connector's near end at all: both halves end up oriented
     * *toward* their shared mid-point, leaving it with two incoming streets and zero outgoing ones -
     * an undriveable dead end in the middle of what should be one continuous, shorter connector.
     */
    @Test
    fun resolveForward_loopTopologyWithOneSource_pointsBothHalvesOfSplitConnectorInward() {
        val source = SourceNode(Point(0, 0), 0, 0.degrees, listOf(sourceConfig(red)))
        // Left road, bottom to top.
        val leftLower = ConnectionNode(Point(0, 100), 0)
        val leftJunction = ConnectionNode(Point(0, 500), 0)
        // The disputed crossbar's two halves, split at a spurious mid-point.
        val midpoint = ConnectionNode(Point(200, 500), 0)
        val rightJunction = ConnectionNode(Point(400, 500), 0)
        // Right road's lower junction, close enough to rightJunction that the *other* crossbar (the
        // "lower" one) offers a shorter loop-around path to rightJunction than going through the
        // disputed crossbar directly.
        val rightLower = ConnectionNode(Point(400, 450), 0)

        val segmentL = UndirectedSegment(leftJunction, midpoint, listOf(leftJunction.position, midpoint.position))
        val segmentR = UndirectedSegment(midpoint, rightJunction, listOf(midpoint.position, rightJunction.position))
        val segments = listOf(
            UndirectedSegment(source, leftLower, listOf(source.position, leftLower.position)),
            UndirectedSegment(leftLower, leftJunction, listOf(leftLower.position, leftJunction.position)),
            segmentL,
            segmentR,
            UndirectedSegment(leftLower, rightLower, listOf(leftLower.position, rightLower.position)),
            UndirectedSegment(rightLower, rightJunction, listOf(rightLower.position, rightJunction.position))
        )

        val resolver = StreetDirectionResolver(segments, listOf(source), emptyList())

        // segmentL: nodeA=leftJunction, nodeB=midpoint -> true means leftJunction -> midpoint.
        val segmentLTowardMidpoint = resolver.resolveForward(segmentL)
        // segmentR: nodeA=midpoint, nodeB=rightJunction -> false means rightJunction -> midpoint.
        val segmentRTowardMidpoint = !resolver.resolveForward(segmentR)

        assertTrue(
            segmentLTowardMidpoint && segmentRTowardMidpoint,
            "a single source in a loop-shaped network still orients both halves toward the shared " +
                "mid-point (segmentL forward=$segmentLTowardMidpoint, segmentR forward=$segmentRTowardMidpoint), " +
                "leaving it as an undriveable dead end"
        )
    }

    /**
     * [StreetDirectionResolver.repairDeadEnds] fixes the dead end reproduced above, matching how
     * [imageprocessing.StreetDetection] actually calls it: every segment that does not touch the
     * source directly is eligible for flipping (not just the two disputed halves), since fixing one
     * dead end can shift the problem onto a neighboring junction that in turn needs its own segment
     * flipped - see the method's doc comment. The fix is verified as a property (no junction is left
     * without an outgoing street) rather than asserting one specific combination of flips, since
     * several different combinations can equally resolve this network.
     */
    @Test
    fun repairDeadEnds_fixesLoopTopologyDeadEnd() {
        val source = SourceNode(Point(0, 0), 0, 0.degrees, listOf(sourceConfig(red)))
        val leftLower = ConnectionNode(Point(0, 100), 0)
        val leftJunction = ConnectionNode(Point(0, 500), 0)
        val midpoint = ConnectionNode(Point(200, 500), 0)
        val rightJunction = ConnectionNode(Point(400, 500), 0)
        val rightLower = ConnectionNode(Point(400, 450), 0)

        val sourceTouching = UndirectedSegment(source, leftLower, listOf(source.position, leftLower.position))
        val segmentL = UndirectedSegment(leftJunction, midpoint, listOf(leftJunction.position, midpoint.position))
        val segmentR = UndirectedSegment(midpoint, rightJunction, listOf(midpoint.position, rightJunction.position))
        val segments = listOf(
            sourceTouching,
            UndirectedSegment(leftLower, leftJunction, listOf(leftLower.position, leftJunction.position)),
            segmentL,
            segmentR,
            UndirectedSegment(leftLower, rightLower, listOf(leftLower.position, rightLower.position)),
            UndirectedSegment(rightLower, rightJunction, listOf(rightLower.position, rightJunction.position))
        )
        val junctions = listOf(leftLower, leftJunction, midpoint, rightJunction, rightLower)

        val resolver = StreetDirectionResolver(segments, listOf(source), emptyList())
        // Mirrors StreetDetection.SegmentDirectionContext.resolveAll: everything except the
        // source-touching segment goes through the rule chain and is eligible for repair.
        val ruleChainSegments = segments.filter { it != sourceTouching }
        val allDirections = segments.associateWith { resolver.resolveForward(it) }
        val flippable = ruleChainSegments.toSet()

        val repaired = resolver.repairDeadEnds(allDirections, flippable)

        for (junction in junctions) {
            assertTrue(hasOutgoingStreet(junction, repaired), "${junction.position} must have an outgoing street after repair")
        }
    }

    /** True when some segment in [directions] flows out of [node] (matching the same nodeA/nodeB -> boolean convention as [StreetDirectionResolver.resolveForward]). */
    private fun hasOutgoingStreet(node: ConnectionNode, directions: Map<UndirectedSegment, Boolean>): Boolean =
        directions.any { (segment, forward) ->
            (forward && segment.nodeA === node) || (!forward && segment.nodeB === node)
        }

    /**
     * Without the lower crossbar, segmentA and segmentB are each the *only* connection to their side
     * of the network - genuine bridges. The repair pass must never flip a bridge (that would
     * disconnect part of the network), so a dead end with no alternate route stays exactly as it was,
     * correctly left for [domain.LevelValidator] to report as before this repair pass existed. The
     * direction map is built by hand rather than via [StreetDirectionResolver.resolveForward], since
     * (as the two tests above show) rule 1 alone cannot orient two segments toward each other unless a
     * network loop is involved - this test is purely about the repair pass' own bridge-safety.
     */
    @Test
    fun repairDeadEnds_leavesGenuineDeadEndWithNoAlternateRouteUntouched() {
        val source = SourceNode(Point(0, 0), 0, 0.degrees, listOf(sourceConfig(red)))
        val a = ConnectionNode(Point(100, 0), 0)
        val deadEnd = ConnectionNode(Point(200, 0), 0)
        val b = ConnectionNode(Point(300, 0), 0)

        val segmentA = UndirectedSegment(a, deadEnd, listOf(a.position, deadEnd.position))
        val segmentB = UndirectedSegment(b, deadEnd, listOf(b.position, deadEnd.position))
        val segments = listOf(
            UndirectedSegment(source, a, listOf(source.position, a.position)),
            segmentA,
            segmentB
        )
        val resolver = StreetDirectionResolver(segments, listOf(source), emptyList())

        // Both segments point into deadEnd; neither has an alternate route to the rest of the graph.
        val bothInward = mapOf(segmentA to true, segmentB to true)

        val repaired = resolver.repairDeadEnds(bothInward, bothInward.keys)

        assertEquals(bothInward, repaired, "with no alternate route on either side, a genuine dead end must be left untouched")
    }

    /**
     * Live-observed failure (2026-07-13, ladder-shaped level): two long parallel roads with several
     * cross rungs, one source and one target on *each* road's far corner. The per-segment
     * nearest-source rule orients every rung the same way (its rule-2 demand filter only runs on
     * exact rule-1 distance ties, so it never notices), leaving one demand with no directed route -
     * yet no dead end exists, so the repair pass never fires either. The demand-flow pass must
     * orient one rung against its siblings so that *every* (source, color) demand has a directed
     * route.
     */
    @Test
    fun orientByDemandFlows_ladderNetwork_givesEveryDemandADirectedRoute() {
        // Left road: source Red at the top, running down through l0..l2 and beyond to Blue's target.
        // Right road: source Blue at the top, running down through r0..r2 to Red's target.
        val sourceRed = SourceNode(Point(0, -100), 0, 0.degrees, listOf(sourceConfig(red)))
        val sourceBlue = SourceNode(Point(300, -90), 0, 0.degrees, listOf(sourceConfig(blue)))
        val l0 = ConnectionNode(Point(0, 0), 0)
        val l1 = ConnectionNode(Point(0, 100), 0)
        val l2 = ConnectionNode(Point(0, 200), 0)
        val r0 = ConnectionNode(Point(300, 0), 0)
        val r1 = ConnectionNode(Point(300, 100), 0)
        val r2 = ConnectionNode(Point(300, 200), 0)
        val targetRed = TargetNode(Point(300, 300), 0, 0.degrees, red)
        val targetBlue = TargetNode(Point(0, 300), 0, 0.degrees, blue)

        /** Builds an undirected segment between [a] and [b] with a straight two-point path. */
        fun seg(a: domain.Node, b: domain.Node) = UndirectedSegment(a, b, listOf(a.position, b.position))
        val sourceRedFeed = seg(sourceRed, l0)
        val sourceBlueFeed = seg(sourceBlue, r0)
        val targetRedFeed = seg(r2, targetRed)
        val targetBlueFeed = seg(l2, targetBlue)
        val leftTop = seg(l0, l1)
        val leftBottom = seg(l1, l2)
        val rightTop = seg(r0, r1)
        val rightBottom = seg(r1, r2)
        val rungTop = seg(l1, r1)
        val rungBottom = seg(l2, r2)

        val allSegments = listOf(
            sourceRedFeed, sourceBlueFeed, targetRedFeed, targetBlueFeed,
            leftTop, leftBottom, rightTop, rightBottom, rungTop, rungBottom
        )
        val resolver = StreetDirectionResolver(allSegments, listOf(sourceRed, sourceBlue), listOf(targetRed, targetBlue))

        // Locked directions exactly as StreetDetection's SegmentDirectionContext locks them:
        // away from sources, into targets.
        val locked = mapOf(
            sourceRedFeed to true,
            sourceBlueFeed to true,
            targetRedFeed to true,
            targetBlueFeed to true
        )

        val flowOriented = resolver.orientByDemandFlows(locked)

        // Complete the resolution the same way production does: rule chain for leftovers, then repair.
        val ruleChain = allSegments
            .filter { it !in locked && it !in flowOriented }
            .associateWith { resolver.resolveForward(it) }
        val directions = locked + flowOriented +
            resolver.repairDeadEnds(locked + flowOriented + ruleChain, ruleChain.keys).filterKeys { it in ruleChain }

        assertTrue(
            hasDirectedRoute(directions, sourceRed, targetRed),
            "Red must have a directed route from its source to its target"
        )
        assertTrue(
            hasDirectedRoute(directions, sourceBlue, targetBlue),
            "Blue must have a directed route from its source to its target"
        )
    }

    /**
     * A deterministic alphabetical/source order can be too greedy: the Blue route below has a
     * shorter path over the shared U-V segment in the opposite direction from Red, while Blue also
     * has a slightly longer alternate path. Processing Blue first would lock U-V as V->U and make
     * Red unreachable; trying multiple deterministic demand orders must choose the order that keeps
     * both color demands routable.
     */
    @Test
    fun orientByDemandFlows_choosesDemandOrderThatKeepsMoreRoutesReachable() {
        val sourceBlue = SourceNode(Point(0, 0), 0, 0.degrees, listOf(sourceConfig(blue)))
        val sourceRed = SourceNode(Point(200, 100), 0, 0.degrees, listOf(sourceConfig(red)))
        val u = ConnectionNode(Point(200, 0), 0)
        val v = ConnectionNode(Point(100, 0), 0)
        val alternateBlueJunction = ConnectionNode(Point(0, 250), 0)
        val targetBlue = TargetNode(Point(210, 0), 0, 0.degrees, blue)
        val targetRed = TargetNode(Point(100, -10), 0, 0.degrees, red)

        /** Builds an undirected segment between [a] and [b] with a straight two-point path. */
        fun seg(a: domain.Node, b: domain.Node) = UndirectedSegment(a, b, listOf(a.position, b.position))

        val blueToV = seg(sourceBlue, v)
        val blueAlternateStart = seg(sourceBlue, alternateBlueJunction)
        val blueAlternateEnd = seg(alternateBlueJunction, targetBlue)
        val redToU = seg(sourceRed, u)
        val shared = seg(u, v)
        val uToBlueTarget = seg(u, targetBlue)
        val vToRedTarget = seg(v, targetRed)
        val allSegments = listOf(
            blueToV, blueAlternateStart, blueAlternateEnd, redToU, shared, uToBlueTarget, vToRedTarget
        )

        val resolver = StreetDirectionResolver(allSegments, listOf(sourceBlue, sourceRed), listOf(targetBlue, targetRed))
        val locked = mapOf(
            blueToV to true,
            blueAlternateStart to true,
            blueAlternateEnd to true,
            redToU to true,
            uToBlueTarget to true,
            vToRedTarget to true
        )

        val flowOriented = resolver.orientByDemandFlows(locked)
        val directions = locked + flowOriented

        assertEquals(true, flowOriented[shared], "shared segment must be oriented U->V so Red remains reachable")
        assertTrue(hasDirectedRoute(directions, sourceRed, targetRed), "Red route must stay reachable")
        assertTrue(hasDirectedRoute(directions, sourceBlue, targetBlue), "Blue must use its alternate route")
    }

    /**
     * The source rule (issue reported 2026-07-13: streets leaving a source were sometimes drawn and
     * interpreted as pointing *into* the source): no matter what an earlier resolution stage
     * decided, [StreetDirectionResolver.enforceSourceTargetOrientation] must flip a street touching
     * exactly one source so it flows away from it.
     */
    @Test
    fun enforceSourceTargetOrientation_flipsStreetOrientedIntoASource() {
        val source = SourceNode(Point(0, 0), 0, 0.degrees, listOf(sourceConfig(red)))
        val a = ConnectionNode(Point(100, 0), 0)
        val segment = UndirectedSegment(source, a, listOf(source.position, a.position))
        val resolver = StreetDirectionResolver(listOf(segment), listOf(source), emptyList())

        val enforced = resolver.enforceSourceTargetOrientation(
            mapOf(segment to false), // wrongly oriented a -> source
            isSource = { it is SourceNode },
            isTarget = { it is TargetNode }
        )

        assertEquals(true, enforced[segment], "a street touching one source must always flow away from it")
    }

    /** Mirror of the source rule for targets: a street touching exactly one target must flow into it. */
    @Test
    fun enforceSourceTargetOrientation_flipsStreetOrientedOutOfATarget() {
        val target = TargetNode(Point(0, 0), 0, 0.degrees, red)
        val a = ConnectionNode(Point(100, 0), 0)
        val segment = UndirectedSegment(target, a, listOf(target.position, a.position))
        val resolver = StreetDirectionResolver(listOf(segment), emptyList(), listOf(target))

        val enforced = resolver.enforceSourceTargetOrientation(
            mapOf(segment to true), // wrongly oriented target -> a
            isSource = { it is SourceNode },
            isTarget = { it is TargetNode }
        )

        assertEquals(false, enforced[segment], "a street touching one target must always flow into it")
    }

    /**
     * Segments the rule cannot constrain stay untouched: a plain junction-to-junction segment keeps
     * its decided direction, and a physically unsatisfiable source-to-source segment (both
     * orientations would violate the rule) is left for validation to report instead of being
     * flip-flopped.
     */
    @Test
    fun enforceSourceTargetOrientation_keepsUnconstrainedAndAmbiguousSegments() {
        val sourceA = SourceNode(Point(0, 0), 0, 0.degrees, listOf(sourceConfig(red)))
        val sourceB = SourceNode(Point(100, 0), 0, 0.degrees, listOf(sourceConfig(blue)))
        val a = ConnectionNode(Point(0, 100), 0)
        val b = ConnectionNode(Point(100, 100), 0)
        val sourceToSource = UndirectedSegment(sourceA, sourceB, listOf(sourceA.position, sourceB.position))
        val plain = UndirectedSegment(a, b, listOf(a.position, b.position))
        val resolver = StreetDirectionResolver(
            listOf(sourceToSource, plain),
            listOf(sourceA, sourceB),
            emptyList()
        )

        val decided = mapOf(sourceToSource to false, plain to false)
        val enforced = resolver.enforceSourceTargetOrientation(
            decided,
            isSource = { it is SourceNode },
            isTarget = { it is TargetNode }
        )

        assertEquals(decided, enforced, "unconstrained and ambiguous segments must keep their decided direction")
    }

    /** BFS over the directed result: is [target] reachable from [source] following segment directions? */
    private fun hasDirectedRoute(
        directions: Map<UndirectedSegment, Boolean>,
        source: SourceNode,
        target: TargetNode
    ): Boolean {
        /** Position/elevation key of [node], mirroring the resolver's logical node identity. */
        fun key(node: domain.Node) = "${Math.round(node.position.x)}:${Math.round(node.position.y)}:${node.elevation}"

        val outgoing = mutableMapOf<String, MutableList<String>>()
        for ((segment, forward) in directions) {
            val fromKey = if (forward) key(segment.nodeA) else key(segment.nodeB)
            val toKey = if (forward) key(segment.nodeB) else key(segment.nodeA)
            outgoing.getOrPut(fromKey) { mutableListOf() }.add(toKey)
        }

        val visited = mutableSetOf(key(source))
        val queue = ArrayDeque(listOf(key(source)))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == key(target)) return true
            for (next in outgoing[current].orEmpty()) {
                if (visited.add(next)) queue.add(next)
            }
        }
        return false
    }
}
