package worldeditor

import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import rendering.GameAreaScale
import kotlin.math.roundToInt

/** Central placement rules for editor-managed sources and sinks. */
internal object WorldEditorPlacement {
    /** Minimum allowed center-to-center distance between editor-managed sources and sinks. */
    const val MIN_NODE_DISTANCE_METER = 12.0

    /**
     * Minimum distance between a node center and the level border, so the node graphic (half an
     * arrow length) is never clipped by the screen edge. Matches the border positions of the
     * predefined template nodes in [persistence.WorldTemplate], which sit exactly flush with
     * the window border.
     */
    const val NODE_EDGE_MARGIN_METER = persistence.WorldTemplate.ARROW_HALF_M

    /** Returns the neutral orientation used for newly placed editor nodes. */
    fun defaultOrientation(): Angle = Angle.ZERO

    /** Returns whether a point lies inside the playable level bounds. */
    fun isInsideLevel(x: Double, y: Double): Boolean =
        x in 0.0..GameAreaScale.GAME_AREA_W_METER && y in 0.0..GameAreaScale.GAME_AREA_H_METER

    /** Resolves a requested position to the nearest valid free-field position, if one exists. */
    fun resolvedPlacementPosition(
        x: Double,
        y: Double,
        excludedId: String?,
        records: Collection<EditorNodeRecord>
    ): Point? {
        val preferred = clampedToLevel(Point(x, y))
        if (hasMinimumDistance(preferred, excludedId, records)) return preferred
        return findNearestValidFieldPosition(preferred, excludedId, records)
    }

    /** Returns whether any editor-managed source or sink violates the minimum distance. */
    fun spacingViolationExists(records: Collection<EditorNodeRecord>): Boolean {
        val recordList = records.toList()
        for (i in recordList.indices) {
            for (j in (i + 1) until recordList.size) {
                if (recordList[i].node.position.distanceTo(recordList[j].node.position) < MIN_NODE_DISTANCE_METER) {
                    return true
                }
            }
        }
        return false
    }

    private fun findNearestValidFieldPosition(
        preferred: Point,
        excludedId: String?,
        records: Collection<EditorNodeRecord>
    ): Point? {
        return fieldCandidates(preferred)
            .filter { hasMinimumDistance(it, excludedId, records) }
            .minWithOrNull(
                compareBy<Point> { it.distanceTo(preferred) }
                    .thenBy { it.x }
                    .thenBy { it.y }
            )
    }

    private fun fieldCandidates(preferred: Point): List<Point> {
        val points = mutableListOf<Point>()
        val seen = mutableSetOf<Pair<Int, Int>>()

        val addPoint: (Point) -> Unit = { point ->
            val clamped = clampedToLevel(point)
            val key = (clamped.x * FIELD_SEARCH_KEY_MULTIPLIER).roundToInt() to
                (clamped.y * FIELD_SEARCH_KEY_MULTIPLIER).roundToInt()
            if (seen.add(key)) points += clamped
        }

        addPoint(preferred)
        var x = 0.0
        while (x < GameAreaScale.GAME_AREA_W_METER) {
            addFieldColumn(x, addPoint)
            x += FIELD_SEARCH_STEP_METER
        }
        addFieldColumn(GameAreaScale.GAME_AREA_W_METER, addPoint)
        return points
    }

    private fun addFieldColumn(x: Double, add: (Point) -> Unit) {
        var y = 0.0
        while (y < GameAreaScale.GAME_AREA_H_METER) {
            add(Point(x, y))
            y += FIELD_SEARCH_STEP_METER
        }
        add(Point(x, GameAreaScale.GAME_AREA_H_METER))
    }

    /**
     * Clamps a point into the placeable area: the level bounds inset by
     * [NODE_EDGE_MARGIN_METER], so no node graphic ever sticks out of the screen.
     */
    private fun clampedToLevel(point: Point): Point =
        Point(
            point.x.coerceIn(NODE_EDGE_MARGIN_METER, GameAreaScale.GAME_AREA_W_METER - NODE_EDGE_MARGIN_METER),
            point.y.coerceIn(NODE_EDGE_MARGIN_METER, GameAreaScale.GAME_AREA_H_METER - NODE_EDGE_MARGIN_METER)
        )

    private fun hasMinimumDistance(
        point: Point,
        excludedId: String?,
        records: Collection<EditorNodeRecord>
    ): Boolean =
        records.none { record ->
            record.id != excludedId && point.distanceTo(record.node.position) < MIN_NODE_DISTANCE_METER
        }

    private const val FIELD_SEARCH_STEP_METER = 0.5
    private const val FIELD_SEARCH_KEY_MULTIPLIER = 1000.0
}
