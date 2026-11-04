package scenes

import imageprocessing.ArucoTableRectifier
import imageprocessing.seedPermanentArucoTransform
import korlibs.image.color.Colors
import korlibs.korge.scene.Scene
import korlibs.korge.view.SContainer
import korlibs.korge.view.solidRect
import korlibs.korge.view.text
import korlibs.korge.view.xy
import kotlinx.coroutines.delay
import scenes.LevelScene.Companion.TABLE_PX_H
import scenes.LevelScene.Companion.TABLE_PX_W
import ui.Assets

/** Base status text shown while the marker detection runs; the animated dots are appended to it. */
private const val DETECTION_STATUS_TEXT = "Projektionsecken werden erkannt"

/**
 * How long the projected markers are given to actually reach the table (scene render + beamer
 * latency) before the first detection capture, in ms. Without this, the first of
 * [imageprocessing.seedPermanentArucoTransform]'s attempts could photograph the previous scene's
 * white calibration frame and be wasted.
 */
private const val MARKER_PROJECTION_SETTLE_MS = 500L

/**
 * The four corner markers' 4x4 inner bit patterns (1 = white in the standard marker, 0 = black),
 * keyed by ArUco id, matching [imageprocessing.ArucoTableRectifier]'s DICT_4X4_50 dictionary.
 * Decoded from the project's own marker artwork (`4x4_1000-{id}.svg`) - ids 0-49 are identical
 * across every DICT_4X4_* dictionary size, so these bit patterns are detected the same way
 * regardless of which one is configured.
 */
private val ARUCO_MARKER_BITS: Map<Int, List<List<Int>>> = mapOf(
    0 to listOf(listOf(1, 0, 1, 1), listOf(0, 1, 0, 1), listOf(0, 0, 1, 1), listOf(0, 0, 1, 0)),
    1 to listOf(listOf(0, 0, 0, 0), listOf(1, 1, 1, 1), listOf(1, 0, 0, 1), listOf(1, 0, 1, 0)),
    2 to listOf(listOf(0, 0, 1, 1), listOf(0, 0, 1, 1), listOf(0, 0, 1, 0), listOf(1, 1, 0, 1)),
    3 to listOf(listOf(1, 0, 0, 1), listOf(1, 0, 0, 1), listOf(0, 1, 0, 0), listOf(0, 1, 1, 0))
)

/**
 * Side length of one marker bit cell, in pixels. The marker (with its 1-cell border) is 6 cells
 * wide/tall. Derived from the rectifier's geometry so drawing and destination mapping cannot drift
 * apart: [imageprocessing.ArucoTableRectifier.detectTransform] maps each detected marker back onto
 * exactly this projected position to rectify the FULL projection surface.
 */
private val ARUCO_MARKER_CELL_PX = ArucoTableRectifier.PROJECTED_MARKER_SIZE_PX / 6.0

/** Distance from the table edge to the nearest edge of each corner marker, in pixels (shared with the rectifier). */
private val ARUCO_MARKER_MARGIN_PX = ArucoTableRectifier.PROJECTED_MARKER_MARGIN_PX

/**
 * Projects the four ArUco corner markers at the table's corners (instead of relying on physical
 * stickers) and detects them once via [seedPermanentArucoTransform], locking the resulting
 * rectification transform in for the rest of the application's runtime
 * ([imageprocessing.PermanentArucoTransform]). The beamer never shows the markers again after this
 * scene, so this is the only chance to ever detect them.
 *
 * The markers are projected INVERTED (bright border/cells on a black background) rather than in
 * their standard black-on-white form: the beamer cannot project "black" - it just leaves the white
 * table in ambient light, so a standard projected marker is only a washed-out gray on white with
 * poor contrast. Inverted, the marker's structure is carried by bright projected light against the
 * unlit table, which the camera sees with far better contrast; detection simply inverts the camera
 * frame again and additionally crops to the four projection corners before searching (see
 * [imageprocessing.ArucoTableRectifier.detectTransform]). Each detected marker is mapped back onto
 * the known projected position it is drawn at here, so the locked-in rectification covers the
 * ENTIRE projection surface - not just the inner quad between the markers.
 *
 * Runs directly after [CameraCalibrationScene]'s white-balance/exposure correction, so the markers
 * are photographed with an already well-calibrated camera. Detection never blocks startup: if the
 * markers cannot be found, the failure is logged and the flow proceeds to [TutorialScene] anyway,
 * with street detection falling back to the physical-sticker path
 * ([imageprocessing.ArucoRectificationCache]) for the whole session.
 */
class ArucoDetectionScene : Scene() {

    /**
     * Projects the inverted corner markers on a black background, runs the one-time marker
     * detection ([seedPermanentArucoTransform]) after a short projection settle delay, and then
     * continues to [TutorialScene] regardless of the detection outcome.
     */
    override suspend fun SContainer.sceneMain() {
        solidRect(TABLE_PX_W, TABLE_PX_H, Colors.BLACK)
        drawInvertedCornerMarkers(TABLE_PX_W, TABLE_PX_H)

        val statusLabel = text(DETECTION_STATUS_TEXT, textSize = 32.0, color = Colors.WHITE, font = Assets.bitcountSingle)
        statusLabel.xy((TABLE_PX_W - statusLabel.width) / 2.0, STATUS_TEXT_Y)
        animateLoadingDots(statusLabel, DETECTION_STATUS_TEXT)

        delay(MARKER_PROJECTION_SETTLE_MS)
        seedPermanentArucoTransform()

        sceneContainer.changeTo<TutorialScene>()
    }

    /**
     * Draws the four projected corner markers at the table's corners, matching
     * [imageprocessing.ArucoTableRectifier]'s game-space corner mapping (id 2 = top-left, id 1 =
     * top-right, id 0 = bottom-right, id 3 = bottom-left).
     */
    private fun SContainer.drawInvertedCornerMarkers(tableWidth: Double, tableHeight: Double) {
        val markerSize = ARUCO_MARKER_CELL_PX * 6
        val rightX = tableWidth - ARUCO_MARKER_MARGIN_PX - markerSize
        val bottomY = tableHeight - ARUCO_MARKER_MARGIN_PX - markerSize

        drawInvertedArucoMarker(2, ARUCO_MARKER_MARGIN_PX, ARUCO_MARKER_MARGIN_PX)
        drawInvertedArucoMarker(1, rightX, ARUCO_MARKER_MARGIN_PX)
        drawInvertedArucoMarker(0, rightX, bottomY)
        drawInvertedArucoMarker(3, ARUCO_MARKER_MARGIN_PX, bottomY)
    }

    /**
     * Draws one INVERTED ArUco marker (its 4x4 inner bits plus a 1-cell border) with its top-left
     * corner at ([x], [y]): the border block is white and the bits that are white in the standard
     * marker are drawn black, so the camera frame inverted by detection reads as the standard
     * marker again. The black scene background doubles as the (inverted) quiet zone around the
     * white border.
     */
    private fun SContainer.drawInvertedArucoMarker(id: Int, x: Double, y: Double) {
        val bits = ARUCO_MARKER_BITS.getValue(id)
        val totalSize = ARUCO_MARKER_CELL_PX * 6
        solidRect(totalSize, totalSize, Colors.WHITE) { xy(x, y) }
        for (row in bits.indices) {
            for (col in bits[row].indices) {
                if (bits[row][col] == 1) {
                    solidRect(ARUCO_MARKER_CELL_PX, ARUCO_MARKER_CELL_PX, Colors.BLACK) {
                        xy(x + (col + 1) * ARUCO_MARKER_CELL_PX, y + (row + 1) * ARUCO_MARKER_CELL_PX)
                    }
                }
            }
        }
    }
}
