package renderingtests

import domain.Color
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.VehicleType
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import rendering.LevelViewRenderer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the stripe colors of the in-game source indicator: every configured color must appear
 * exactly once, no matter how many vehicle types share it (a source with three vehicle types
 * used to repeat its color sequence three times).
 */
class SourceIndicatorColorsTest {

    private val red = Color("Rot", Colors.RED)
    private val blue = Color("Blau", Colors.BLUE)
    private val car = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0, "images/car.png")
    private val truck = VehicleType("Lastwagen", 60.0, 8.0, 0.7, 2.0, "images/truck.png")
    private val bike = VehicleType("Motorrad", 90.0, 2.0, 0.7, 4.0, "images/bike.png")

    /** Builds a source with one configuration per color-and-type combination. */
    private fun sourceWith(colors: List<Color>, types: List<VehicleType>): SourceNode {
        val configurations = types.flatMap { type ->
            colors.map { color -> SourceNodeConfiguration(color, 2.0, type.maxSpeed, type) }
        }
        return SourceNode(Point(0.0, 0.0), 0, Angle.ZERO, configurations)
    }

    /** Verifies that three vehicle types per color still yield each color exactly once. */
    @Test
    fun colorsAppearOncePerColorRegardlessOfVehicleTypeCount() {
        val source = sourceWith(colors = listOf(red, blue), types = listOf(car, truck, bike))

        assertEquals(
            listOf("Rot", "Blau"),
            LevelViewRenderer.sourceIndicatorColors(source).map { it.name }
        )
    }

    /** Verifies that the first-appearance order of the colors is kept. */
    @Test
    fun colorsKeepFirstAppearanceOrder() {
        val source = sourceWith(colors = listOf(blue, red), types = listOf(car, truck))

        assertEquals(
            listOf("Blau", "Rot"),
            LevelViewRenderer.sourceIndicatorColors(source).map { it.name }
        )
    }

    /** Verifies that color names are deduplicated case-insensitively. */
    @Test
    fun colorsWithDifferentCasingCountAsOneColor() {
        val source = SourceNode(
            Point(0.0, 0.0),
            0,
            Angle.ZERO,
            listOf(
                SourceNodeConfiguration(Color("Rot", Colors.RED), 2.0, car.maxSpeed, car),
                SourceNodeConfiguration(Color("rot", Colors.RED), 2.0, truck.maxSpeed, truck)
            )
        )

        assertEquals(1, LevelViewRenderer.sourceIndicatorColors(source).size)
    }
}
