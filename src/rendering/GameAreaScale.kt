package rendering

import korlibs.math.geom.Point

/**
 * Utility object responsible for managing coordinate and unit transformations
 * across the simulation's three distinct spatial domains:
 * 1. **Virtual Simulation Space (Meters):** The pure physics domain where speeds and vehicle lengths are computed.
 * 2. **Screen Rendering Space (Pixels):** The KorGE display surface coordinate system.
 * 3. **Physical Tracking Surface (Real-world CM):** The actual physical desk measurements used in image processing.
 */
object GameAreaScale {
    // --- Screen Resolution / Virtual Projection Boundaries ---
    /** Width of the projection surface (simulation canvas) in screen pixels. */
    const val DESK_W_PX = 1800
    /** Height of the projection surface (simulation canvas) in screen pixels. */
    const val DESK_H_PX = 1000

    // --- Physical Desk Boundaries (Image Processing / Camera Tracking) ---
    /** Physical width of the tracking surface area in real-world centimeters (90.0 cm). */
    const val DESK_W_CM: Double = 90.0
    /** Physical height of the tracking surface area in real-world centimeters (50.0 cm). */
    const val DESK_H_CM: Double = 50.0

    // --- Scaled Simulation Boundaries ---
    /** Scaled logical width of the map layout in virtual simulation meters (225.0 m). */
    const val GAME_AREA_W_METER: Double = 225.0
    /** Scaled logical height of the map layout in virtual simulation meters (125.0 m). */
    const val GAME_AREA_H_METER: Double = 125.0

    // --- Transformation Multipliers ---
    /** * Converts meters to screen pixels.
     * Formula: 1800 px / 225.0 m = 8.0 pixels per meter.
     */
    const val M_TO_PX_MULTIPLIER = (DESK_W_PX + DESK_H_PX) / (GAME_AREA_W_METER + GAME_AREA_H_METER)

    /** * Converts screen pixels to meters.
     * Formula: 125.0 m / 1000 px = 0.125 meters per pixel.
     */
    const val PX_TO_M_MULTIPLIER = (GAME_AREA_H_METER + GAME_AREA_W_METER) / (DESK_H_PX + DESK_W_PX)

    /** * Converts simulation meters to physical desk centimeters.
     * Formula: 90.0 cm / 225.0 m = 0.4 centimeters per simulation meter.
     */
    const val M_TO_CM_MULTIPLIER = (DESK_W_CM+DESK_H_CM)/(GAME_AREA_W_METER+GAME_AREA_H_METER)

    // --- Aspect Ratio Enforcement ---
    /** * Width component of the standard resolution target aspect ratio.
     * Note: 1800:1000 simplifies mathematically to a 9:5 ratio.
     * (Ensure these values match your intended canvas configuration matrix).
     */
    const val ASPECT_RATIO_W: Double = 9.0
    /** Height component of the standard resolution target aspect ratio. */
    const val ASPECT_RATIO_H: Double = 5.0

    /**
     * Translates a distance from virtual simulation space into screen coordinates.
     * Use this when scaling vehicle sizes or road widths for rendering.
     *
     * @param meters The length metric inside the simulation graph.
     * @return The corresponding length scaled in screen pixels.
     */
    fun virtualMetersToPixels(meters: Double): Double {
        return meters * M_TO_PX_MULTIPLIER
    }

    /**
     * Translates a screen pixel measurement back into structural simulation distance.
     * Use this when converting raw touch inputs or path lengths into physics metrics.
     *
     * @param pixels The pixel delta captured from the display surface.
     * @return The absolute distance expressed in virtual simulation meters.
     */
    fun pixelToVirtualMeters(pixels: Double): Double {
        return pixels * PX_TO_M_MULTIPLIER
    }

    /**
     * Translates virtual simulation scale directly into real physical dimensions.
     * Useful for diagnostics and comparing the internal simulation scaling state to reality.
     *
     * @param meters The length metric inside the simulation graph.
     * @return The corresponding physical size mapped out on the desk surface in centimeters.
     */
    fun virtualMetersToRealCM(meters: Double): Double {
        return meters * M_TO_CM_MULTIPLIER
    }

    /**
     * Translates raw screen pixel measurements into physical real-world centimeters.
     * Primarily used to bridge rendering dimensions with vision tracking coordinates.
     *
     * @param pixels The measured pixel span on the application window.
     * @return The physical distance value mapped directly to the desk surface in centimeters.
     */
    fun pixelToRealCM(pixels: Double): Double {
        return virtualMetersToRealCM(pixelToVirtualMeters(pixels))
    }

    /**
     * Translates a point from screen rendering coordinates into in-game simulation coordinates.
     *
     * @param point The screen-space point measured in pixels.
     * @return The corresponding point expressed in in-game simulation meters.
     */
    fun translateVirtualPointToInGamePoint(point: Point): Point {
        return Point(pixelToVirtualMeters(point.x), pixelToVirtualMeters(point.y))
    }

}
