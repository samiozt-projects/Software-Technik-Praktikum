package scenes

import config.ConfigManager
import imageprocessing.runCameraAutoCalibration
import korlibs.image.color.Colors
import korlibs.korge.input.onClick
import korlibs.korge.scene.Scene
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.Text
import korlibs.korge.view.addUpdater
import korlibs.korge.view.container
import korlibs.korge.view.solidRect
import korlibs.korge.view.text
import korlibs.korge.view.xy
import kotlinx.coroutines.launch
import scenes.LevelScene.Companion.TABLE_PX_H
import scenes.LevelScene.Companion.TABLE_PX_W
import ui.Assets

/** Prompt shown next to the start button before the player has started the calibration. */
private const val START_PROMPT_TEXT = "Kalibrierung mit Start beginnen"

/** Base status text shown while the calibration sweep runs; the animated dots are appended to it. */
private const val CALIBRATION_STATUS_TEXT = "Kamera wird kalibriert"

/** Number of loading-dot states the animation cycles through: 0, 1, 2, 3 dots, then back to 0. */
private const val LOADING_DOT_STATE_COUNT = 4

/** How long each loading-dot animation step is shown before advancing to the next one, in ms. */
private const val LOADING_DOT_STEP_MS = 450.0

/**
 * Vertical position of the status text, close to the top edge.
 *
 * [imageprocessing.measureCalibrationPatch] only samples a centered region of the raw camera frame
 * (see its `CALIBRATION_PATCH_FRACTION`), leaving an untouched margin along every edge regardless of
 * how the camera happens to be mounted relative to the beamer (even a 180-degree mounting only swaps
 * which edge margin is which, never turns a margin into the sampled center). Keeping the status text
 * inside that margin, near the top, guarantees the loading animation can never influence a
 * measurement.
 */
internal const val STATUS_TEXT_Y = 60.0

/** Width and height of the start button, in pixels. */
private const val START_BUTTON_W = 300.0
private const val START_BUTTON_H = 100.0

/** Thickness of the start button's black border, in pixels. */
private const val START_BUTTON_BORDER_PX = 4.0

/**
 * Runs the one-time camera auto-calibration sweep (exposure, white balance) at application
 * startup, showing a full white reference area for the camera to sample against the current room
 * lighting.
 *
 * The sweep does not start on its own: the scene waits on a Start button first, so the operator can
 * finish setting up the table (camera aligned, projection surface clear, room lighting final)
 * before the white reference is measured. Only after the button is pressed does the white-balance/
 * exposure correction run - the button is removed beforehand so the sampled center of the frame is
 * pure white.
 *
 * Afterwards the flow continues to [ArucoDetectionScene], which projects and detects the four
 * inverted corner markers in its own scene, or straight to [TutorialScene] when
 * [config.ConfigManager.CameraAutoCalibrationConfig.projectArucoMarkers] is false (the older
 * physical-sticker A/B path, see [imageprocessing.ArucoRectificationCache]).
 *
 * Calibration never blocks startup: any failure (disabled via config, invalid sweep range, no
 * camera available) is logged and the flow proceeds regardless, using whatever camera state was
 * already established.
 */
class CameraCalibrationScene : Scene() {

    /** Guards against a second button click racing the removal of the button after the first one. */
    private var calibrationStarted = false

    /**
     * Shows the white reference area with the start prompt and wires the Start button that kicks
     * off the calibration sweep and the follow-up scene change.
     */
    override suspend fun SContainer.sceneMain() {
        val config = ConfigManager.loadCameraAutoCalibrationConfig()

        solidRect(TABLE_PX_W, TABLE_PX_H, Colors.WHITE)

        val statusLabel = text(START_PROMPT_TEXT, textSize = 32.0, color = Colors.BLACK, font = Assets.bitcountSingle)
        centerHorizontally(statusLabel)

        val startButton = drawStartButton()
        startButton.onClick {
            if (calibrationStarted) return@onClick
            calibrationStarted = true
            startButton.removeFromParent()

            statusLabel.text = CALIBRATION_STATUS_TEXT
            centerHorizontally(statusLabel)
            animateLoadingDots(statusLabel, CALIBRATION_STATUS_TEXT)

            launch {
                runCameraAutoCalibration(config)

                if (config.projectArucoMarkers) {
                    sceneContainer.changeTo<ArucoDetectionScene>()
                } else {
                    sceneContainer.changeTo<TutorialScene>()
                }
            }
        }
    }

    /** Draws the centered start button (white box with a black border and label). */
    private fun SContainer.drawStartButton(): Container = container {
        solidRect(START_BUTTON_W, START_BUTTON_H, Colors.BLACK)
        solidRect(
            START_BUTTON_W - 2 * START_BUTTON_BORDER_PX,
            START_BUTTON_H - 2 * START_BUTTON_BORDER_PX,
            Colors.WHITE
        ) { xy(START_BUTTON_BORDER_PX, START_BUTTON_BORDER_PX) }

        val label = text("Start", textSize = 40.0, color = Colors.BLACK, font = Assets.bitcountSingle)
        label.xy((START_BUTTON_W - label.width) / 2.0, (START_BUTTON_H - label.height) / 2.0)

        xy((TABLE_PX_W - START_BUTTON_W) / 2.0, (TABLE_PX_H - START_BUTTON_H) / 2.0)
    }

    /** Re-centers [statusLabel] horizontally at [STATUS_TEXT_Y] after its text changed. */
    private fun centerHorizontally(statusLabel: Text) {
        statusLabel.xy((TABLE_PX_W - statusLabel.width) / 2.0, STATUS_TEXT_Y)
    }
}

/**
 * Cycles [statusLabel]'s trailing dots (0 to 3, looping, always restarting from the first dot)
 * behind [baseText] so the player can see the app is still working during a calibration step
 * instead of wondering whether it has frozen. Shared by [CameraCalibrationScene] and
 * [ArucoDetectionScene].
 *
 * Registered via [addUpdater], which the view tree calls every render frame independently of the
 * suspending calibration work - so the animation keeps advancing smoothly while the calibration
 * step awaits each camera capture, rather than pausing between steps.
 *
 * The text grows to the right from its initial (dot-less) left edge without being re-centered on
 * every tick, so the animation never jitters horizontally.
 */
internal fun SContainer.animateLoadingDots(statusLabel: Text, baseText: String) {
    var elapsedMs = 0.0
    var dotCount = 0
    addUpdater { dt ->
        elapsedMs += dt.inWholeMicroseconds / 1000.0
        if (elapsedMs < LOADING_DOT_STEP_MS) return@addUpdater
        elapsedMs = 0.0

        dotCount = (dotCount + 1) % LOADING_DOT_STATE_COUNT
        statusLabel.text = baseText + ".".repeat(dotCount)
    }
}
