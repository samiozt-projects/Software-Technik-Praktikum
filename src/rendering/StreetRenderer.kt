package rendering

import domain.Street
import korlibs.image.color.Colors
import korlibs.korge.view.Container
import korlibs.korge.view.GraphicsRenderer
import korlibs.korge.view.View
import korlibs.korge.view.graphics
import korlibs.math.geom.Point
import korlibs.math.geom.vector.LineCap
import korlibs.math.geom.vector.LineJoin
import korlibs.math.geom.vector.StrokeInfo
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Responsible for rendering streets onto a Korge scene.
 * Uses a multi-layer rendering approach to create realistic street visuals.
 *
 * The renderer returns one parent graphics view per render call. Keeping the whole
 * painted result inside a single view is important for reactive updates because
 * [rendering.syncViews] can later remove or replace that view as one unit.
 */
class StreetRenderer {
    companion object {
        /**
         * Visual elevation of a street. Ramps are drawn on the higher layer so they appear above
         * the lower road they leave or enter.
         */
        fun renderedElevation(street: Street): Int =
            maxOf(street.startNode.elevation, street.endNode.elevation)
    }

    /**
     * Internal helper: combines a path and its elevation into one renderable unit.
     * @property path The path of the street.
     * @property elevation The elevation of the street.
    */
    private data class DrawableStreet(val path: List<Point>, val elevation: Int)
    
    /**
     * Internal helper: defines one visual layer with its colors and width.
     * @property colorsByElevation The colors of the layer at different elevations.
     * @property width The width of the layer.
    */
    private data class VisualLayer(val colorsByElevation: List<String>, val width: Double) {
        /**
         * Returns the color for a given elevation.
         * @param elevation The elevation of the street.
         * @return [Color] The color of the layer at the given elevation.
        */
        fun colorFor(elevation: Int) = Colors[colorsByElevation[elevation.coerceIn(0, colorsByElevation.lastIndex)]]
    }

    // Define the visual layers from outermost to innermost. The order matters:
    // later, narrower strokes are painted on top of wider strokes to create the
    // border, asphalt and centre line without separate shapes.
    //
    // Reduced to three bands with whole-pixel widths: at 8 px/m a 4 m lane is only
    // 32 px wide, so anything finer than ~2 px cannot render crisply. Combined with
    // antialiased = false (see drawElevatedLayers) this gives hard, pixel-art-style
    // edges that match the node sprite instead of fuzzy sub-pixel bands.
    private val layers = listOf(
        // Outer black border (32 px = the 4 m lane width, so it meets the node arrow).
        VisualLayer(listOf("#000000", "#000000", "#000000"), 32.0),
        VisualLayer(listOf("#d5dbe0", "#c2c9cf", "#aeb7be"), 28.0),
        // Main asphalt surface; 2 px of the black border stays visible on each side.
        VisualLayer(listOf("#707b81", "#59646b", "#424c52"), 24.0)

    )

    /** Distance (virtual meters) between consecutive direction arrows along one street. */
    private val ARROW_SPACING_METERS = 20.0

    /** Arrow triangle size in pixels, sized to sit inside the narrowest (24 px) asphalt band. */
    private val ARROW_LENGTH_PX = 20.0
    private val ARROW_WIDTH_PX = 14.0

    private val ARROW_FILL_COLOR = Colors["#fce94f"]
    private val ARROW_OUTLINE_COLOR = Colors.BLACK

    /**
     * One direction arrow's placement along a street.
     * @property tipPx The arrow's center position in pixels.
     * @property headingRadians The arrow's heading in radians (atan2 convention).
     */
    private data class ArrowPlacement(val tipPx: Point, val headingRadians: Double)

    /**
     * Direction arrows drawn along [path] so both players and developers can see at a glance which
     * way a street runs (Issue: suspected wrong directions on some detected streets), without having
     * to infer it from vehicle behavior. Arrows point from `path.first()` toward `path.last()`,
     * matching [Street.startNode] -> [Street.endNode] - the same order [drawElevatedLayers] already
     * draws the polyline in.
     *
     * Placed every [ARROW_SPACING_METERS] along the path, centered so a street shorter than that
     * still gets exactly one arrow near its middle instead of none. Each arrow's heading follows the
     * local path tangent, so curved streets still point the right way at every arrow, not just
     * end-to-end straight ones.
     */
    private fun arrowPlacements(path: List<Point>): List<ArrowPlacement> {
        if (path.size < 2) return emptyList()

        val cumulative = DoubleArray(path.size)
        for (i in 1 until path.size) {
            cumulative[i] = cumulative[i - 1] + path[i - 1].distanceTo(path[i])
        }
        val totalLength = cumulative.last()
        if (totalLength <= 0.0) return emptyList()

        val arrowCount = maxOf(1, (totalLength / ARROW_SPACING_METERS).toInt())
        return (0 until arrowCount).map { i ->
            placementAtDistance(path, cumulative, totalLength * (i + 0.5) / arrowCount)
        }
    }

    /** Interpolates the point and local tangent heading on [path] at arc-length [distance] from its start. */
    private fun placementAtDistance(path: List<Point>, cumulative: DoubleArray, distance: Double): ArrowPlacement {
        var segmentIndex = 0
        while (segmentIndex < cumulative.size - 2 && cumulative[segmentIndex + 1] < distance) {
            segmentIndex++
        }

        val segStart = path[segmentIndex]
        val segEnd = path[segmentIndex + 1]
        val segLength = cumulative[segmentIndex + 1] - cumulative[segmentIndex]
        val t = if (segLength > 0.0) ((distance - cumulative[segmentIndex]) / segLength).coerceIn(0.0, 1.0) else 0.0

        val meterPoint = Point(segStart.x + (segEnd.x - segStart.x) * t, segStart.y + (segEnd.y - segStart.y) * t)
        val heading = atan2(segEnd.y - segStart.y, segEnd.x - segStart.x)
        val tipPx = Point(GameAreaScale.virtualMetersToPixels(meterPoint.x), GameAreaScale.virtualMetersToPixels(meterPoint.y))
        return ArrowPlacement(tipPx, heading)
    }

    /**
     * Draw the configured visual layers for the provided polylines and return the
     * created Graphics view. This centralizes the exact sequence of strokes so that
     * single-path and multi-path renderers use identical painting code.
     *
     * @param container The container to attach the resulting Graphics view to.
     * @param paths The list of polylines to draw; each polyline must have at least two points.
     */
    private fun drawLayers(container: Container, paths: List<List<Point>>): View {
        return drawElevatedLayers(container, paths.map { DrawableStreet(it, elevation = 0) })
    }

    /**
     * Draw the configured visual layers for the provided polylines and return the
     * created Graphics view. This centralizes the exact sequence of strokes so that
     * single-path and multi-path renderers use identical painting code.
     *
     * @param container The container to attach the resulting Graphics view to.
     * @param streets The list of polylines to draw; each polyline must have at least two points.
    */
    private fun drawElevatedLayers(container: Container, streets: List<DrawableStreet>): View {
        // The GPU renderer is required here: antialiased is a no-op on the default
        // SYSTEM/CPU rasterizer (it always smooths), so only GpuShapeView can give us
        // the hard, non-antialiased edges that match the pixel-art node sprite.
        return container.graphics(renderer = GraphicsRenderer.GPU) {
            // For each visual layer (from widest/backmost to narrowest/frontmost)
            // paint the same set of polylines so that all layers stay precisely aligned.
            for (layer in layers) {
                for (elevation in 0..2) {
                    val paths = streets
                        .filter { it.elevation == elevation }
                        .map { it.path }
                    if (paths.isEmpty()) continue

                    stroke(layer.colorFor(elevation), roundedStroke(layer.width)) {
                        for (path in paths) {
                            // Start a new sub-path for every polyline
                            moveTo(GameAreaScale.virtualMetersToPixels(path.first().x), GameAreaScale.virtualMetersToPixels(path.first().y))
                            for (i in 1 until path.size) {
                                lineTo(GameAreaScale.virtualMetersToPixels(path[i].x), GameAreaScale.virtualMetersToPixels(path[i].y))
                            }
                        }
                    }
                }
            }

            // Direction arrows on top of the finished road surface, one street at a time.
            for (street in streets) {
                for (arrow in arrowPlacements(street.path)) {
                    val cosHeading = cos(arrow.headingRadians)
                    val sinHeading = sin(arrow.headingRadians)
                    // local +x = pointing along travel direction, local +y = to the left of travel.
                    fun rotated(forwardPx: Double, leftPx: Double) = Point(
                        arrow.tipPx.x + forwardPx * cosHeading - leftPx * sinHeading,
                        arrow.tipPx.y + forwardPx * sinHeading + leftPx * cosHeading
                    )
                    val tip = rotated(ARROW_LENGTH_PX * 0.5, 0.0)
                    val backLeft = rotated(-ARROW_LENGTH_PX * 0.5, ARROW_WIDTH_PX * 0.5)
                    val backRight = rotated(-ARROW_LENGTH_PX * 0.5, -ARROW_WIDTH_PX * 0.5)

                    fill(ARROW_FILL_COLOR) { polygon(tip, backLeft, backRight) }
                    stroke(ARROW_OUTLINE_COLOR, StrokeInfo(thickness = 1.5, join = LineJoin.ROUND)) {
                        polygon(tip, backLeft, backRight)
                    }
                }
            }
        }.also {
            // Hard, pixel-art-style edges instead of smoothed sub-pixel bands.
            // Only effective because the view above uses the GPU renderer.
            it.antialiased = false
        }
    }

    /**
     * Round caps and joins soften road segment ends and corners.
     * This keeps detected/polyline streets from looking sharply cut at each vertex.
     */
    private fun roundedStroke(width: Double): StrokeInfo = StrokeInfo(
        thickness = width,
        startCap = LineCap.ROUND,
        endCap = LineCap.ROUND,
        join = LineJoin.ROUND
    )

    /**
     *  Renders a single street path with multiple visual layers for realistic appearance.
     *  The street is composed of several concentric layers with different colors and widths
     *  to simulate road markings, asphalt, and borders.
     *
     * @param container The Korge container the street view is attached to.
     * @param path The polyline that should be rendered.
     * @return The created [View] (a `Graphics`) attached as child of [container]. The caller
     *         keeps this reference and can later remove the view via `view.removeFromParent()`.
     * @throws IllegalArgumentException if [path] has fewer than 2 points.
     */
    fun renderPolylineStreet(container: Container, path: List<Point>): View {
        require(path.size >= 2) { "Street path must have at least 2 points, got ${path.size}" }

        // Use the shared drawing helper so the layer painting is identical to
        // the network rendering path. Wrap the single polyline in a list.
        return drawLayers(container, listOf(path))
    }

    /**
     * Renders a collection of saved streets (domain [Street] objects) into a single graphics view.
     *
     * The function composes all streets into one view and paints them layer-by-layer to avoid
     * rendering seams at intersections. That means every street's black border is drawn first,
     * then the shoulder, asphalt and finally the lane markings. Drawing the complete network in
     * this grouped fashion prevents later-drawn streets from covering inner markings of earlier
     * ones.
     *
     * @param container The Korge container the street network view is attached to.
     * @param streets The list of domain [Street] objects to render. Each street's `path` is used.
     * @return The created [View] (a `Graphics`) attached as child of [container]. The caller
     *         keeps this reference and can later remove the view via `view.removeFromParent()`.
     * @throws IllegalArgumentException Not thrown here; streets with fewer than two points are
     *         silently ignored because they cannot form visible geometry.
     */
    fun renderSavedStreetNetwork(container: Container, streets: List<Street>): View {
        // Convert domain streets to drawable polylines and drop invalid ones early.
        val drawableStreets = streets
            .filter { it.path.size >= 2 }
            .map { street ->
                DrawableStreet(
                    path = street.path,
                    elevation = renderedElevation(street)
                )
            }

        // Delegate the actual layered painting to the shared helper so both single-path
        // and multi-path rendering look identical.
        return drawElevatedLayers(container, drawableStreets)
    }

    /**
     * Renders only streets whose visual elevation matches [elevation].
     *
     * LevelScene uses this to interleave street and vehicle layers:
     * street 0, vehicles 0, street 1, vehicles 1, street 2, vehicles 2.
     */
    fun renderSavedStreetNetworkElevation(container: Container, streets: List<Street>, elevation: Int): View {
        val drawableStreets = streets
            .filter { it.path.size >= 2 }
            .filter { renderedElevation(it) == elevation }
            .map { street ->
                DrawableStreet(
                    path = street.path,
                    elevation = renderedElevation(street)
                )
            }

        return drawElevatedLayers(container, drawableStreets)
    }
}

