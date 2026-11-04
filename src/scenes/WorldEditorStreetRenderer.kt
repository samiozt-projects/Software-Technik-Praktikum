package scenes

import domain.Street
import korlibs.image.color.RGBA
import korlibs.korge.view.Container
import korlibs.korge.view.graphics
import korlibs.math.geom.Point
import korlibs.math.geom.vector.LineCap
import korlibs.math.geom.vector.LineJoin
import korlibs.math.geom.vector.StrokeInfo

/** Draws the non-editable street layout inside the world editor field preview. */
internal class WorldEditorStreetRenderer(
    private val levelToScreenX: (Double) -> Double,
    private val levelToScreenY: (Double) -> Double
) {
    /** Draws all streets that contain at least a start and end point. */
    fun draw(container: Container, streets: List<Street>) {
        container.graphics {
            /** Draws one street path with the configured road color and thickness. */
            fun drawStreetPath(path: List<Point>, color: RGBA, thickness: Double) {
                stroke(
                    color,
                    StrokeInfo(
                        thickness = thickness,
                        startCap = LineCap.ROUND,
                        endCap = LineCap.ROUND,
                        join = LineJoin.ROUND
                    )
                ) {
                    val first = path.first()
                    moveTo(levelToScreenX(first.x), levelToScreenY(first.y))
                    path.drop(1).forEach { point ->
                        lineTo(levelToScreenX(point.x), levelToScreenY(point.y))
                    }
                }
            }

            streets.filter { it.path.size >= MIN_STREET_POINTS }.forEach { street ->
                drawStreetPath(street.path, Colors.DARK_ROAD, DARK_ROAD_THICKNESS)
                drawStreetPath(street.path, Colors.LIGHT_ROAD, LIGHT_ROAD_THICKNESS)
            }
        }
    }

    private object Colors {
        val DARK_ROAD: RGBA = korlibs.image.color.Colors["#202020"]
        val LIGHT_ROAD: RGBA = korlibs.image.color.Colors["#7B858C"]
    }

    private companion object {
        const val MIN_STREET_POINTS = 2
        const val DARK_ROAD_THICKNESS = 16.0
        const val LIGHT_ROAD_THICKNESS = 12.0
    }
}
