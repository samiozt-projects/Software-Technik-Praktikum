package imagedetectiontests

import domain.Color
import domain.ConnectionNode
import domain.SourceNode
import domain.Street
import domain.TargetNode
import imageprocessing.StreetNetworkStabilityTracker
import imageprocessing.StreetNetworkStabilityTracker.Action
import imageprocessing.streetNetworkSignature
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Tests [StreetNetworkStabilityTracker] and [streetNetworkSignature] directly, independent of
 * image detection. A camera-driven capture cycle calls `observe` once per detection; these tests
 * simulate that sequence of observations directly with hand-built signatures.
 */
class StreetNetworkStabilityTrackerTest {

    private val networkA = setOf("0:0:0->100:0:0")
    private val networkB = setOf("0:0:0->100:0:0", "100:0:0->200:0:0")

    /** Tests that the very first observation ever made is never confirmed immediately. */
    @Test
    fun observe_firstEverObservation_isPending() {
        val tracker = StreetNetworkStabilityTracker()

        assertEquals(Action.PENDING, tracker.observe(networkA))
    }

    /** Tests that a signature seen on two consecutive observations is confirmed on the second one. */
    @Test
    fun observe_sameSignatureTwiceInARow_confirmsOnTheSecondObservation() {
        val tracker = StreetNetworkStabilityTracker()

        assertEquals(Action.PENDING, tracker.observe(networkA))
        assertEquals(Action.CONFIRMED, tracker.observe(networkA))
    }

    /** Tests that repeating an already-confirmed signature is reported as unchanged, not re-confirmed. */
    @Test
    fun observe_repeatingAConfirmedSignature_reportsUnchanged() {
        val tracker = StreetNetworkStabilityTracker()
        tracker.observe(networkA)
        tracker.observe(networkA) // confirmed here

        assertEquals(Action.UNCHANGED, tracker.observe(networkA))
        assertEquals(Action.UNCHANGED, tracker.observe(networkA))
    }

    /**
     * Tests that a single noisy capture in between two confirmed observations does not disturb the
     * already-confirmed network: the outlier itself is PENDING, but the very next capture matches
     * the still-confirmed network and is accepted immediately rather than waiting for a repeat.
     */
    @Test
    fun observe_singleNoisyFrame_doesNotDisruptTheConfirmedNetwork() {
        val tracker = StreetNetworkStabilityTracker()
        tracker.observe(networkA)
        tracker.observe(networkA) // confirmed

        assertEquals(Action.PENDING, tracker.observe(networkB))
        assertEquals(Action.UNCHANGED, tracker.observe(networkA))
    }

    /** Tests that a genuinely new signature is applied once it repeats twice. */
    @Test
    fun observe_genuineChangeConfirmedTwice_becomesTheNewConfirmedNetwork() {
        val tracker = StreetNetworkStabilityTracker()
        tracker.observe(networkA)
        tracker.observe(networkA) // confirmed as networkA

        tracker.observe(networkB) // pending
        assertEquals(Action.CONFIRMED, tracker.observe(networkB))
        assertEquals(Action.UNCHANGED, tracker.observe(networkB))
    }

    /**
     * Tests that a single differing capture sandwiched between two matches does not reset progress:
     * the tracker tolerates exactly one interruption while accumulating toward confirmation.
     */
    @Test
    fun observe_singleInterruptionBetweenTwoMatches_stillConfirmsOnTheSecondMatch() {
        val tracker = StreetNetworkStabilityTracker()

        assertEquals(Action.PENDING, tracker.observe(networkA))
        assertEquals(Action.PENDING, tracker.observe(networkB), "The interruption itself is not yet confirmable")
        assertEquals(Action.CONFIRMED, tracker.observe(networkA), "A's second match confirms it - B was tolerated as a single interruption")
    }

    /**
     * Tests that the tolerance covers exactly one interruption, not an arbitrary run of them: A's
     * single match is abandoned once a second differing capture (B again) arrives, and B then has to
     * accumulate its own two matches from scratch like any other new candidate - it does not
     * retroactively confirm just because it was the interrupting value.
     */
    @Test
    fun observe_secondConsecutiveInterruption_abandonsTheOriginalCandidateAndStartsFresh() {
        val tracker = StreetNetworkStabilityTracker()

        assertEquals(Action.PENDING, tracker.observe(networkA), "A becomes the candidate")
        assertEquals(Action.PENDING, tracker.observe(networkB), "First interruption: tolerated, A is still the candidate")
        assertEquals(Action.PENDING, tracker.observe(networkB), "Second differing capture in a row: A is abandoned, B becomes the new candidate with its first match")
        assertEquals(Action.CONFIRMED, tracker.observe(networkB), "B's own second match confirms it - not carried over from the earlier interruption")
    }

    /**
     * A raw signal that keeps alternating between exactly two topologies every single capture (e.g.
     * a persistent, never-settling direction flip) must never flip the confirmed network back and
     * forth forever: once A is confirmed, B recurring on its own can never accumulate a second match,
     * because every A in between resets B's progress (see the tracker's class doc, "why this does not
     * flap forever"). This is a regression test for a naive "signature appears a majority of times
     * within the last 3 captures" formulation, which - by the pigeonhole principle - re-confirms
     * (flip-flops) on every single subsequent capture for exactly this input.
     */
    @Test
    fun observe_persistentTwoStateOscillation_confirmsOnceThenNeverFlipsBackAndForthAgain() {
        val tracker = StreetNetworkStabilityTracker()

        tracker.observe(networkA)
        tracker.observe(networkB)
        assertEquals(Action.CONFIRMED, tracker.observe(networkA), "The first A/B/A settles on A")

        repeat(20) {
            assertEquals(Action.PENDING, tracker.observe(networkB), "B alone can never re-accumulate a second match")
            assertEquals(Action.UNCHANGED, tracker.observe(networkA), "A must stay the confirmed network throughout")
        }
    }

    /** Tests that the signature of a street list does not depend on the list's element order. */
    @Test
    fun streetNetworkSignature_isIndependentOfListOrder() {
        val a = ConnectionNode(Point(0.0, 0.0), 0)
        val b = ConnectionNode(Point(100.0, 0.0), 0)
        val c = ConnectionNode(Point(200.0, 0.0), 0)
        val streetOne = Street(a, b, listOf(a.position, b.position))
        val streetTwo = Street(b, c, listOf(b.position, c.position))

        assertEquals(
            streetNetworkSignature(listOf(streetOne, streetTwo)),
            streetNetworkSignature(listOf(streetTwo, streetOne))
        )
    }

    /** Tests that two streets connecting the same endpoints produce the same signature despite different path shapes. */
    @Test
    fun streetNetworkSignature_ignoresPathShapeDifferencesBetweenTheSameEndpoints() {
        val a = ConnectionNode(Point(0.0, 0.0), 0)
        val b = ConnectionNode(Point(100.0, 0.0), 0)
        val straight = Street(a, b, listOf(a.position, b.position))
        val wobbly = Street(a, b, listOf(a.position, Point(50.0, 3.0), b.position))

        assertEquals(streetNetworkSignature(listOf(straight)), streetNetworkSignature(listOf(wobbly)))
    }

    /**
     * Tests that a node position wobbling by a unit or two - the kind of jitter a real camera
     * capture produces between two photos of the same unchanged physical street - still produces
     * the same signature, because coordinates are bucketed rather than rounded to the nearest unit.
     */
    @Test
    fun streetNetworkSignature_absorbsSmallPositionWobbleBetweenCaptures() {
        // Positions centered on a bucket (a multiple of the 3-unit bucket size) so a 1-unit wobble
        // in either direction cannot accidentally straddle a bucket boundary either.
        val a1 = ConnectionNode(Point(99.0, 51.0), 0)
        val b1 = ConnectionNode(Point(201.0, 51.0), 0)
        val captureOne = Street(a1, b1, listOf(a1.position, b1.position))

        // Same physical junctions, detected a moment later with a small pixel-level wobble.
        val a2 = ConnectionNode(Point(100.0, 50.0), 0)
        val b2 = ConnectionNode(Point(200.0, 50.0), 0)
        val captureTwo = Street(a2, b2, listOf(a2.position, b2.position))

        assertEquals(streetNetworkSignature(listOf(captureOne)), streetNetworkSignature(listOf(captureTwo)))
    }

    /** Tests that reversing a street's direction produces a different signature. */
    @Test
    fun streetNetworkSignature_isDirectionSensitive() {
        val a = ConnectionNode(Point(0.0, 0.0), 0)
        val b = ConnectionNode(Point(100.0, 0.0), 0)
        val forward = Street(a, b, listOf(a.position, b.position))
        val backward = Street(b, a, listOf(b.position, a.position))

        assertNotEquals(streetNetworkSignature(listOf(forward)), streetNetworkSignature(listOf(backward)))
    }

    /** Tests that the signature matches across independently created Node instances at the same positions. */
    @Test
    fun streetNetworkSignature_matchesAcrossIndependentlyDetectedNodeInstances() {
        val red = Color("Rot", Colors.RED)
        val source1 = SourceNode(Point(5.0, 31.25), 0, Angle.Companion.ZERO, emptyList())
        val target1 = TargetNode(Point(220.0, 31.5), 0, Angle.Companion.ZERO, red)
        val street1 = Street(source1, target1, listOf(source1.position, target1.position))

        // A second, independent detection run producing fresh Node instances at the same
        // positions - exactly what happens between two consecutive camera captures.
        val source2 = SourceNode(Point(5.0, 31.25), 0, Angle.Companion.ZERO, emptyList())
        val target2 = TargetNode(Point(220.0, 31.5), 0, Angle.Companion.ZERO, red)
        val street2 = Street(source2, target2, listOf(source2.position, target2.position))

        assertEquals(streetNetworkSignature(listOf(street1)), streetNetworkSignature(listOf(street2)))
    }
}
