package ui

import domain.Color
import korlibs.image.bitmap.Bitmap
import korlibs.korge.view.Container
import korlibs.korge.view.anchor
import korlibs.korge.view.image
import kotlin.math.min

/**
 * Renders a vehicle bitmap tinted with a single [color], centered at (0, 0) in the parent container.
 * Scales the image to fit within the specified width and height while maintaining aspect ratio.
 */
fun Container.addVehiclePreview(
    bitmap: Bitmap,
    color: Color,
    width: Double,
    height: Double
) {
    image(bitmap) {
        val scaleW = width / bitmap.width.toDouble()
        val scaleH = height / bitmap.height.toDouble()
        scale = min(scaleW, scaleH)
        anchor(0.5, 0.5)
        colorMul = color.value
    }
}

/** Resolves the vehicle graphic for a configuration, or [Assets.car] as fallback. */
fun vehicleBitmapForGraphicRef(graphicRef: String?): Bitmap =
    graphicRef?.takeIf { it.isNotBlank() }?.let { Assets.getVehicleGraphic(it) } ?: Assets.car
