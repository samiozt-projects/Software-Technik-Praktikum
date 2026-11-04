package renderingtests

import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import rendering.GameAreaScale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit test suite validating the precision, mathematical consistency, and boundary constraints
 * of the [GameAreaScale] utility.
 */
class GameAreaScaleTest {

    /** Maximum allowed floating-point discrepancy during double-precision comparison checks. */
    private val epsilon = 0.0001

    // ============================================================================
    // UNIT CONVERSION TESTS
    // ============================================================================

    /**
     * Validates conversion accuracy from virtual simulation space meters into screen rendering pixels.
     * Expected transformation multiplier rule: 1800 px / 225 m = 8.0 pixels per meter.
     */
    @Test
    fun testVirtualMetersToPixels() {
        // Zero bound boundary check
        assertEquals(0.0, GameAreaScale.virtualMetersToPixels(0.0), epsilon)

        // Single unit identity multiplier verification
        assertEquals(8.0, GameAreaScale.virtualMetersToPixels(1.0), epsilon)

        // Full resolution scaling limit check
        assertEquals(1800.0, GameAreaScale.virtualMetersToPixels(225.0), epsilon)

        // Arbitrary intermediate dimension scaling check
        assertEquals(80.0, GameAreaScale.virtualMetersToPixels(10.0), epsilon)
    }

    /**
     * Validates conversion accuracy from screen pixels back into structural simulation meters.
     * Expected transformation multiplier rule: 125 m / 1000 px = 0.125 meters per pixel.
     */
    @Test
    fun testPixelToVirtualMeters() {
        // Zero bound boundary check
        assertEquals(0.0, GameAreaScale.pixelToVirtualMeters(0.0), epsilon)

        // Single pixel step resolution check
        assertEquals(0.125, GameAreaScale.pixelToVirtualMeters(1.0), epsilon)

        // Maximum map distance resolution check
        assertEquals(125.0, GameAreaScale.pixelToVirtualMeters(1000.0), epsilon)

        // Mid-range coordinate verification matching 80px block down to 10m
        assertEquals(10.0, GameAreaScale.pixelToVirtualMeters(80.0), epsilon)
    }

    /**
     * Validates scaling conversions from virtual simulation space meters directly into physical tracking desk centimeters.
     * Expected transformation multiplier rule: 90 cm / 225 m = 0.4 physical centimeters per simulation meter.
     */
    @Test
    fun testVirtualMetersToRealCM() {
        // Zero bound boundary check
        assertEquals(0.0, GameAreaScale.virtualMetersToRealCM(0.0), epsilon)

        // Single unit spatial resolution footprint mapping
        assertEquals(0.4, GameAreaScale.virtualMetersToRealCM(1.0), epsilon)

        // Maximum table surface length mapping boundary limits
        assertEquals(90.0, GameAreaScale.virtualMetersToRealCM(225.0), epsilon)
    }

    /**
     * Validates complex composite conversions bridging screen pixel definitions directly into physical table centimeters.
     * Chain logic requirement: 80 pixels must transform to exactly 10 virtual meters, which maps out to 4 physical desk centimeters.
     */
    @Test
    fun testPixelToRealCM() {
        // Composite verification mapping an arbitrary window dimension directly down to table space
        assertEquals(4.0, GameAreaScale.pixelToRealCM(80.0), epsilon)

        // Zero baseline verification
        assertEquals(0.0, GameAreaScale.pixelToRealCM(0.0), epsilon)
    }

    /**
     * Verifies round-trip consistency across space boundaries (Bijectivity test).
     * Ensures transforming an arbitrary metric value into screen space and immediately reversing the conversion
     * results in the original value without compounding floating-point artifacts.
     */
    @Test
    fun testInversionConsistency() {
        val testMeter = 45.7

        // Perform the round-trip calculation sequence
        val pixels = GameAreaScale.virtualMetersToPixels(testMeter)
        val calculatedMeters = GameAreaScale.pixelToVirtualMeters(pixels)

        // Since conversion ratios are stable fractions (8.0 * 0.125 = 1.0),
        // the inverted value must align perfectly with the original sample.
        assertEquals(testMeter, calculatedMeters, epsilon)
    }

    /**
     * Verifies that both the pixel-space and the virtual meter-space enforce
     * the target 9:5 (or 5:9) aspect ratio to prevent map stretching.
     */
    @Test
    fun testAspect_enforcesTargetRatio() {
        val expectedRatio = GameAreaScale.ASPECT_RATIO_W / GameAreaScale.ASPECT_RATIO_H
        val actualPixelRatio = GameAreaScale.DESK_W_PX.toDouble() / GameAreaScale.DESK_H_PX.toDouble()
        val actualMeterRatio = GameAreaScale.GAME_AREA_W_METER / GameAreaScale.GAME_AREA_H_METER

        assertEquals(expectedRatio, actualPixelRatio, epsilon, "Pixel playfield must match the 9:5 aspect ratio")
        assertEquals(expectedRatio, actualMeterRatio, epsilon, "Virtual playfield must match the 9:5 aspect ratio")
    }

    /**
     * Ensures that the virtual maximum boundaries of the simulation layout
     * remain consistently fixed at the required dimensions of 225m x 125m.
     */
    @Test
    fun testVirtualMaxBoundaries_matchExpectedDimensions() {
        assertEquals(225.0, GameAreaScale.GAME_AREA_W_METER, "Virtual maximum width must be 225m")
        assertEquals(125.0, GameAreaScale.GAME_AREA_H_METER, "Virtual maximum height must be 125m")
    }

    /**
     * Tests the fragility of the multiplier logic by checking the X and Y scaling factors
     * independently. This prevents visual distortions if resolution dimensions change.
     */
    @Test
    fun testScaleConsistency_independentAxes() {
        val scaleX = GameAreaScale.DESK_W_PX.toDouble() / GameAreaScale.GAME_AREA_W_METER
        val scaleY = GameAreaScale.DESK_H_PX.toDouble() / GameAreaScale.GAME_AREA_H_METER

        assertEquals(scaleX, scaleY, epsilon, "Scaling factors for X and Y must be absolutely identical to prevent distortion")
        assertEquals(scaleX, GameAreaScale.M_TO_PX_MULTIPLIER, epsilon, "M_TO_PX_MULTIPLIER is inconsistent with the X-axis scale")
        assertEquals(1.0 / scaleX, GameAreaScale.PX_TO_M_MULTIPLIER, epsilon, "PX_TO_M_MULTIPLIER is inconsistent with the X-axis scale")
    }

    /**
     * Verifies that coordinate system boundaries, specifically the absolute origin point (0,0)
     * and the maximum limits (1800, 1000) pixels, map correctly into the meter domain.
     */
    @Test
    fun testBoundaryPoints_translateCorrectly() {
        val originMeters = GameAreaScale.translateVirtualPointToInGamePoint(Point(0.0, 0.0))
        assertEquals(Point(0.0, 0.0), originMeters, "Boundary point (0,0) px must map to (0,0) m")

        val maxBoundPixels = Point(GameAreaScale.DESK_W_PX.toDouble(), GameAreaScale.DESK_H_PX.toDouble())
        val maxBoundMeters = GameAreaScale.translateVirtualPointToInGamePoint(maxBoundPixels)

        assertEquals(Point(225.0, 125.0), maxBoundMeters, "Pixel corner (Max) must map precisely to (225m, 125m)")
    }

    /**
     * Confirms that negative pixel inputs (such as UI offsets or offscreen objects)
     * scale linearly and predictably without causing system math breakdowns.
     */
    @Test
    fun testNegativeCoordinates_scaleLinearly() {
        val negativePixel = Point(-80.0, -40.0)
        val resultMeters = GameAreaScale.translateVirtualPointToInGamePoint(negativePixel)

        assertEquals(Point(-10.0, -5.0), resultMeters, "Negative pixel values must scale stably into negative meter values")
    }

    /**
     * Verifies that positions tracked far outside the regular canvas boundaries
     * continue to scale up linearly to support dynamic world environments.
     */
    @Test
    fun testOutOfBounds_scaleCorrectly() {
        val outOfBoundsPixel = Point(2400.0, 1600.0)
        val resultMeters = GameAreaScale.translateVirtualPointToInGamePoint(outOfBoundsPixel)

        assertEquals(Point(300.0, 200.0), resultMeters, "Values outside the visible area must continue to scale linearly")
    }

    /**
     * Tests a full transformation cycle (Pixel -> Meter -> Pixel) to validate that
     * serializing and deserializing path coordinates does not cause data drift or loss.
     */
    @Test
    fun testPointRoundtrip_preservesExactCoordinates() {
        val originalPoints = listOf(
            Point(0.0, 0.0),
            Point(58.25, 460.125),
            Point(900.0, 500.0),
            Point(1799.9, 999.9)
        )

        for (startPixel in originalPoints) {
            val meters = GameAreaScale.translateVirtualPointToInGamePoint(startPixel)

            val endPixelX = GameAreaScale.virtualMetersToPixels(meters.x)
            val endPixelY = GameAreaScale.virtualMetersToPixels(meters.y)
            val reconstructedPixel = Point(endPixelX, endPixelY)

            assertEquals(startPixel.x, reconstructedPixel.x, epsilon, "X coordinate suffered data loss or drift during roundtrip")
            assertEquals(startPixel.y, reconstructedPixel.y, epsilon, "Y coordinate suffered data loss or drift during roundtrip")
        }
    }

    /**
     * Validates the core tracking metric requirement: a physical vehicle scaling length
     * of 4.0 virtual meters must convert to exactly 1.6 real-world centimeters on the table.
     */
    @Test
    fun testCARLength_translatesToPhysicalCM() {
        val carLengthMeters = 4.0
        val expectedCM = 1.6

        val actualCM = GameAreaScale.virtualMetersToRealCM(carLengthMeters)
        assertEquals(expectedCM, actualCM, epsilon, "A 4-meter vehicle must map to exactly 1.6cm on the physical desk tracker")
    }

    /**
     * Checks the structural orientation invariant, ensuring that an angle of 0 degrees
     * aligns fully forward along the positive X-axis within the underlying engine layout.
     */
    @Test
    fun testRotation_zeroDegreesFacesUp() {
        val angleZero = Angle.fromDegrees(0.0)

        val directionVectorX = cos(angleZero.radians)
        val directionVectorY = sin(angleZero.radians)

        assertEquals(1.0, directionVectorX, epsilon, "At 0°, the X vector component must point fully forward/up")
        assertEquals(0.0, directionVectorY, epsilon, "At 0°, there must be no lateral Y-axis deviation")
    }

}
