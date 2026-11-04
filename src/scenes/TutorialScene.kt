package scenes

import audio.InteractionSoundController
import audio.InteractionSoundEffect
import domain.GameState
import domain.ChallengeResult
import domain.Level
import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.io.file.std.applicationVfs
import audio.onClickWithSound
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.View
import korlibs.korge.view.addUpdater
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.position
import korlibs.korge.view.text
import korlibs.korge.view.xy
import korlibs.math.geom.Point
import korlibs.math.geom.vector.StrokeInfo
import kotlinx.coroutines.launch
import kotlin.time.Duration
import ui.Assets
import ui.ControlButtons
import ui.LevelActionButtonMode

/**
 * Specialized play scene for the tutorial level.
 *
 * It reuses the simulation and rendering flow from [LevelScene], but owns all tutorial-specific
 * UI: the instructional text, the start hint, the completion label, and the button that advances
 * to the level menu.
 */
class TutorialScene : LevelScene() {

    /** Countdown state for tutorial continue button */
    private var tutorialCountdownActive = false
    private var tutorialCountdownValue = 0.0
    private var tutorialCountdownButton: Container? = null
    private var tutorialCountdownFill: Container? = null
    private var tutorialCountdownAction: (() -> Unit)? = null
    private var tutorialCountdownExplanationShown = false
    private var tutorialCountdownStartDelaySeconds = 0.0
    private var tutorialExplanationView: View? = null

    companion object {
        /** Layout and copy values that define the tutorial-specific overlay. */
        object TutorialLayout {
            /** Level number used by the bundled tutorial level. */
            const val LEVEL_NUMBER = 0

            /** Primary tutorial instruction shown while the player is solving the level. */
            const val PRIMARY_TEXT = "Tutorial: Baue eine Straße, sodass die Fahrzeuge zu ihrem Ziel kommen"

            /** Width of the white tutorial instruction box. */
            const val PRIMARY_BOX_W = 1640.0

            /** Height of the white tutorial instruction box. */
            const val PRIMARY_BOX_H = 82.0

            /** Vertical position of the white tutorial instruction box. */
            const val PRIMARY_BOX_Y = 112.0

            /** Text size used inside the primary tutorial instruction box. */
            const val PRIMARY_TEXT_SIZE = 32.0

            /** Hint shown next to the active black play button in the tutorial. */
            const val START_HINT_TEXT = "Simulation starten"

            /** Horizontal position of the tutorial hint next to the top-left play button. */
            const val START_HINT_X: Double = 95.0

            /** Vertical position of the tutorial hint next to the top-left play button. */
            const val START_HINT_Y: Double = 30.0

            /** Predefined level slot that follows after the tutorial is completed. */
            const val NEXT_PREDEFINED_LEVEL_ID = 1

            /** Title shown on the challenge explanation screen. */
            const val CHALLENGE_TITLE = "Challenge-Modus"

            /** Short explanation shown after the first completion-button click. */
            const val CHALLENGE_EXPLANATION_TEXT =
                "Ist das Level zu leicht?\n" +
                    "Im Challenge-Modus gelten\n" +
                    "besondere Regeln, welche die\n" +
                    "Level schwieriger machen."

            /** Text used by the tutorial's single completion button. */
            const val CONTINUE_BUTTON_TEXT = "Weiter"

            /** Length of countdown for tutorial continue button (in seconds) */
            const val TUTORIAL_COUNTDOWN_DURATION = 15

            /** Width of the challenge explanation box. */
            const val EXPLANATION_BOX_W = 920.0
            /** Height of the challenge explanation box. */
            const val EXPLANATION_BOX_H = 300.0
            /** Y position of the challenge explanation box. */
            const val EXPLANATION_BOX_Y = 380.0

            /** Text size of the challenge title. */
            const val CHALLENGE_TITLE_TEXT_SIZE = 54.0
            /** Icon size of the challenge title. */
            const val CHALLENGE_TITLE_ICON_SIZE = 84.0
            /** Gap between the icon and the text in the challenge title. */
            const val CHALLENGE_TITLE_ICON_GAP = 28.0
            /** Y position of the challenge title. */
            const val CHALLENGE_TITLE_Y = 180.0
            /** Width of the challenge title background box. */
            const val CHALLENGE_TITLE_BOX_W = 720.0
            /** Height of the challenge title background box. */
            const val CHALLENGE_TITLE_BOX_H = 120.0
            /** Vertical offset from the title baseline container to the title background box. */
            const val CHALLENGE_TITLE_BOX_Y_OFFSET = -30.0

            /** Width of the continue button. */
            const val CONTINUE_BUTTON_W = 240.0
            /** Height of the continue button. */
            const val CONTINUE_BUTTON_H = 80.0
            /** Y position of the continue button. */
            const val CONTINUE_BUTTON_Y = 780.0
        }
    }

    /** Tracks whether the post-completion challenge explanation is currently shown. */
    private var challengeExplanationVisible = false

    /** The currently visible tutorial completion message overlay, if it has been drawn. */
    private var tutorialCompletionOverlay: View? = null

    /** Uses the tutorial-specific completion text instead of the normal level completion label. */
    override fun completionText(): String = "Tutorial abgeschlossen"

    /**
     * Keeps the tutorial free of the simulation-speed selector and fixed to normal playback speed.
     */
    override fun showSimulationSpeedSelector(): Boolean = false

    /**
     * Keeps the tutorial completion flow free of the generic statistics overlay.
     */
    override fun showResultStatisticsOverlay(): Boolean = false

    /**
     * Draws the tutorial instruction above the road and the conditional start hint beside play.
     *
     * The start hint is only visible while the current network state makes the play button active.
     */
    override fun SContainer.drawSceneOverlay(level: Level): View {
        return container {
            drawPrimaryTutorialBox()
            val startHint = drawStartHint()
            addUpdater { updateStartHintVisibility(startHint, level) }
        }
    }

    /**
     * Draws the white tutorial text box above the road.
     */
    private fun Container.drawPrimaryTutorialBox() {
        container {
            xy(TABLE_PX_W / 2.0 - TutorialLayout.PRIMARY_BOX_W / 2.0, TutorialLayout.PRIMARY_BOX_Y)
            drawPrimaryTutorialBoxBackground()
            drawPrimaryTutorialText()
        }
    }

    /**
     * Draws the tutorial text box background and border.
     */
    private fun Container.drawPrimaryTutorialBoxBackground() {
        graphics {
            fill(Colors.WHITE) { rect(0.0, 0.0, TutorialLayout.PRIMARY_BOX_W, TutorialLayout.PRIMARY_BOX_H) }
            stroke(Colors.BLACK, StrokeInfo(thickness = 2.0)) {
                rect(0.0, 0.0, TutorialLayout.PRIMARY_BOX_W, TutorialLayout.PRIMARY_BOX_H)
            }
        }
    }

    /**
     * Draws the main tutorial text centered in its box.
     */
    private fun Container.drawPrimaryTutorialText() {
        text(
            TutorialLayout.PRIMARY_TEXT,
            textSize = TutorialLayout.PRIMARY_TEXT_SIZE,
            color = Colors.BLACK,
            font = Assets.bitcountSingle
        ).apply {
            centerOn(this@drawPrimaryTutorialText)
        }
    }

    /**
     * Draws the conditional start hint beside the play button.
     *
     * @return the hint text view.
     */
    private fun Container.drawStartHint(): View {
        return text(
            TutorialLayout.START_HINT_TEXT,
            textSize = 30.0,
            color = Colors.BLACK,
            font = Assets.bitcountSingle
        ).apply {
            xy(TutorialLayout.START_HINT_X, TutorialLayout.START_HINT_Y)
        }
    }

    /**
     * Updates whether the start hint should be visible.
     *
     * @param startHint The start hint view that is being displayed
     * @param level The level that is currently being displayed
     */
    private fun updateStartHintVisibility(startHint: View, level: Level) {
        val mode = ControlButtons.levelActionMode(level.networkState, GameState.simulationState)
        startHint.visible = mode == LevelActionButtonMode.START
    }

    /**
     * Routes the completion next-level action from tutorial directly to predefined level 1.
     *
     * @param level The level that was completed
     * @return Predefined level slot for the next level
     */
    override suspend fun nextCompletionLevelSlot(level: Level): LevelMenuScene.PredefinedLevelSlot? {
        val nextLevelId = TutorialLayout.NEXT_PREDEFINED_LEVEL_ID
        val nextLevelPath = LevelMenuScene.predefinedLevelPath(nextLevelId)
        if (!applicationVfs[nextLevelPath].exists()) return null

        return LevelMenuScene.PredefinedLevelSlot(nextLevelId, nextLevelPath)
    }

    /**
     * Draws the tutorial's two-step completion action.
     *
     * @param level The level that was completed
     * @return The completion action view
     */
    override suspend fun SContainer.drawCompletionAction(level: Level): View {
        /** Reset countdown state for this completion action */
        tutorialCountdownActive = false
        tutorialCountdownValue = 0.0
        tutorialCountdownButton = null
        tutorialCountdownFill = null
        tutorialCountdownAction = null
        tutorialCountdownExplanationShown = false
        tutorialCountdownStartDelaySeconds = 0.0
        tutorialExplanationView = null

        challengeExplanationVisible = false
        return container {
            val explanation = drawChallengeExplanation().apply {
                visible = false
            }
            /** Store reference to explanation view for countdown */
            tutorialExplanationView = explanation

            val buttonX = TABLE_PX_W / 2.0 - TutorialLayout.CONTINUE_BUTTON_W / 2.0
            val continueButton = drawTutorialContinueButton(buttonX, TutorialLayout.CONTINUE_BUTTON_Y)
            continueButton.onClickWithSound {
                handleTutorialContinueClick(explanation)
            }

            /** Add tutorial-specific countdown updater */
            addUpdater { dt ->
                processTutorialCountdown(dt)
            }
        }
    }

    /**
     * Handles the two-step tutorial completion flow.
     *
     * @param explanation the Challenge-Modus explanation that appears after the first click.
     */
    private fun handleTutorialContinueClick(explanation: View) {
        if (!challengeExplanationVisible) {
            InteractionSoundController.stop(InteractionSoundEffect.TIMER_15S)
            /** First click: show explanation and start second phase countdown */
            showChallengeExplanation(explanation)
            challengeExplanationVisible = true
            tutorialCountdownExplanationShown = true

            /** Reset and start second phase countdown */
            tutorialCountdownActive = true
            tutorialCountdownValue = TutorialLayout.TUTORIAL_COUNTDOWN_DURATION.toDouble()
            tutorialCountdownAction = {
                launch {
                    LevelMenuScene.backDestination = LevelMenuBackDestination.LEVEL_SCENE
                    sceneContainer.changeTo<LevelMenuScene>()
                }
            }
            InteractionSoundController.play(InteractionSoundEffect.TIMER_15S)

            /** Reset the fill visualization */
            tutorialCountdownFill?.removeChildren()
            tutorialCountdownFill?.graphics {
                fill(RGBA(0, 255, 0, 178)) {
                    rect(2.0, 2.0, 0.0, TutorialLayout.CONTINUE_BUTTON_H - 2.0)
                }
            }
            return
        }

        /** Second click: transition to level menu immediately */
        tutorialCountdownActive = false
        InteractionSoundController.stop(InteractionSoundEffect.TIMER_15S)
        launch {
            LevelMenuScene.backDestination = LevelMenuBackDestination.LEVEL_SCENE
            sceneContainer.changeTo<LevelMenuScene>()
        }
    }

    /**
     * Shows the Challenge-Modus explanation and hides the initial completion message.
     *
     * @param explanation the Challenge-Modus explanation view.
     */
    private fun showChallengeExplanation(explanation: View) {
        challengeExplanationVisible = true
        tutorialCompletionOverlay?.visible = false
        explanation.visible = true
    }

    /**
     * Starts the tutorial countdown with the specified action.
     *
     * @param action the action to perform when the countdown completes.
     */
    private fun startTutorialCountdown(action: () -> Unit) {
        tutorialCountdownActive = true
        tutorialCountdownValue = TutorialLayout.TUTORIAL_COUNTDOWN_DURATION.toDouble()
        tutorialCountdownAction = action
        InteractionSoundController.play(InteractionSoundEffect.TIMER_15S)
    }

    /**
     * Updates the tutorial countdown logic.
     *
     * @param dt The delta time since the last frame.
     */
    private fun processTutorialCountdown(dt: Duration) {
        if (tutorialCountdownStartDelaySeconds > 0.0) {
            val deltaSeconds = dt.inWholeMicroseconds / 1_000_000.0
            tutorialCountdownStartDelaySeconds = (tutorialCountdownStartDelaySeconds - deltaSeconds).coerceAtLeast(0.0)
            if (tutorialCountdownStartDelaySeconds <= 0.0) {
                startFirstPhaseCountdown()
            }
            return
        }

        /** Only proceed if countdown should be active */
        if (!tutorialCountdownActive || tutorialCountdownButton == null || tutorialCountdownFill == null) {
            return
        }

        /** Update countdown timer */
        val deltaSeconds = dt.inWholeMicroseconds / 1_000_000.0
        tutorialCountdownValue -= deltaSeconds

        /** Calculate fill width based on elapsed time (fill from left to right) */
        val elapsedRatio = 1.0 - (tutorialCountdownValue / TutorialLayout.TUTORIAL_COUNTDOWN_DURATION.toDouble())
        val fillWidth = (TutorialLayout.CONTINUE_BUTTON_W - 4.0) * elapsedRatio

        /** Update the fill visualization */
        tutorialCountdownFill?.removeChildren()
        tutorialCountdownFill?.graphics {
            fill(RGBA(0, 255, 0, 178)) {
                rect(2.0, 2.0, fillWidth, TutorialLayout.CONTINUE_BUTTON_H - 2.0)
            }
        }

        /** Execute action when countdown completes */
        if (tutorialCountdownValue <= 0) {
            tutorialCountdownActive = false
            InteractionSoundController.stop(InteractionSoundEffect.TIMER_15S)
            val action = tutorialCountdownAction
            tutorialCountdownAction = null
            action?.invoke()
        }
    }

    /**
     * Draws the Challenge-Modus explanation panel in the layout shown by the reference image.
     *
     * @return The challenge explanation view
     */
    private fun Container.drawChallengeExplanation(): Container {
        return container {
            drawChallengeExplanationTitle()
            drawChallengeExplanationBox()
        }
    }

    /**
     * Draws the centered Challenge-Modus title with its static lightning icon.
     *
     * @return The challenge explanation title view
     */
    private fun Container.drawChallengeExplanationTitle(): Container {
        return container {
            val boxX = TABLE_PX_W / 2.0 - TutorialLayout.CHALLENGE_TITLE_BOX_W / 2.0
            val boxY = TutorialLayout.CHALLENGE_TITLE_Y + TutorialLayout.CHALLENGE_TITLE_BOX_Y_OFFSET
            xy(boxX, boxY)
            drawChallengeTitleBoxBackground()

            container {
                val title = text(
                    TutorialLayout.CHALLENGE_TITLE,
                    textSize = TutorialLayout.CHALLENGE_TITLE_TEXT_SIZE,
                    color = Colors.BLACK,
                    font = Assets.bitcountSingle
                ) {
                    xy(0.0, 0.0)
                }
                val iconX = title.width + TutorialLayout.CHALLENGE_TITLE_ICON_GAP
                drawStaticChallengeIcon(iconX, -8.0, TutorialLayout.CHALLENGE_TITLE_ICON_SIZE)
                x = TutorialLayout.CHALLENGE_TITLE_BOX_W / 2.0 - challengeExplanationTitleWidth(title.width) / 2.0
                y = TutorialLayout.CHALLENGE_TITLE_BOX_H / 2.0 - title.height / 2.0
            }
        }
    }

    /**
     * Draws the white background and border for the Challenge-Modus title row.
     */
    private fun Container.drawChallengeTitleBoxBackground() {
        graphics {
            fill(Colors.WHITE) {
                rect(0.0, 0.0, TutorialLayout.CHALLENGE_TITLE_BOX_W, TutorialLayout.CHALLENGE_TITLE_BOX_H)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = 2.0)) {
                rect(0.0, 0.0, TutorialLayout.CHALLENGE_TITLE_BOX_W, TutorialLayout.CHALLENGE_TITLE_BOX_H)
            }
        }
    }

    /**
     * Returns the combined width of the tutorial challenge title and icon.
     *
     * @param titleWidth The width of the title text
     * @return The combined width of the title text and the icon
     */
    private fun challengeExplanationTitleWidth(titleWidth: Double): Double {
        return titleWidth +
            TutorialLayout.CHALLENGE_TITLE_ICON_GAP +
            TutorialLayout.CHALLENGE_TITLE_ICON_SIZE
    }

    /**
     * Draws the bordered Challenge-Modus explanation box and centers its copy.
     *
     * @return The challenge explanation box view
     */
    private fun Container.drawChallengeExplanationBox(): Container {
        val boxX = TABLE_PX_W / 2.0 - TutorialLayout.EXPLANATION_BOX_W / 2.0
        return container {
            xy(boxX, TutorialLayout.EXPLANATION_BOX_Y)
            drawChallengeExplanationBoxBackground()
            text(
                TutorialLayout.CHALLENGE_EXPLANATION_TEXT,
                textSize = 40.0,
                color = Colors.BLACK,
                font = Assets.bitcountSingle
            ).apply {
                centerOn(this@container)
            }
        }
    }

    /**
     * Draws the white background and black border for the challenge explanation box.
     *
     * @return The challenge explanation box background view
     */
    private fun Container.drawChallengeExplanationBoxBackground() {
        graphics {
            fill(Colors.WHITE) {
                rect(0.0, 0.0, TutorialLayout.EXPLANATION_BOX_W, TutorialLayout.EXPLANATION_BOX_H)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = 2.0)) {
                rect(0.0, 0.0, TutorialLayout.EXPLANATION_BOX_W, TutorialLayout.EXPLANATION_BOX_H)
            }
        }
    }

    /**
     * Draws the single Weiter button used by the tutorial completion flow.
     *
     * @param xPos The x-coordinate of the button
     * @param yPos The y-coordinate of the button
     * @return The tutorial continue button view
     */
    private fun Container.drawTutorialContinueButton(xPos: Double, yPos: Double): Container {
        return container {
            xy(xPos, yPos)
            graphics {
                fill(Colors.WHITE) {
                    rect(0.0, 0.0, TutorialLayout.CONTINUE_BUTTON_W, TutorialLayout.CONTINUE_BUTTON_H)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = 2.0)) {
                    rect(0.0, 0.0, TutorialLayout.CONTINUE_BUTTON_W, TutorialLayout.CONTINUE_BUTTON_H)
                }
            }

            /** Add green countdown fill layer with 70% opacity */
            val countdownFill = container {
                xy(0.0, 0.0)
                graphics {
                    fill(RGBA(0, 255, 0, 178)) {
                        rect(2.0, 2.0, 0.0, TutorialLayout.CONTINUE_BUTTON_H - 2.0)
                    }
                }
            }

            /** Store reference for countdown updates */
            this@TutorialScene.tutorialCountdownButton = this
            this@TutorialScene.tutorialCountdownFill = countdownFill

            text(TutorialLayout.CONTINUE_BUTTON_TEXT, textSize = 36.0, color = Colors.BLACK, font = Assets.bitcountSingle) {
                centerOn(this@container)
            }
        }
    }

    /**
     * Draws a non-interactive challenge lightning icon next to the tutorial explanation title.
     *
     * @param xPos The x-coordinate of the icon
     * @param yPos The y-coordinate of the icon
     * @param size The size of the icon
     * @return The challenge icon view
     */
    private fun Container.drawStaticChallengeIcon(xPos: Double, yPos: Double, size: Double): Container {
        return container {
            position(xPos, yPos)
            with(ControlButtons) {
                drawChallengeIconGraphic(size, Colors.WHITE, Colors.WHITE)
            }
        }
    }

    /**
     * Hides the tutorial instruction after completion and reveals the forward action.
     *
     * @param levelCompleted The completion status of the level
     * @param sceneOverlay The scene overlay view
     * @param completionAction The completion action view
     */
    override fun updateCompletionUi(levelCompleted: Boolean, sceneOverlay: View?, completionAction: View?) {
        updateVisibilityBasedOnCompletion(levelCompleted, sceneOverlay, completionAction)

        if (shouldStartFirstPhaseCountdown(levelCompleted)) {
            tutorialCountdownStartDelaySeconds = SUCCESS_SOUND_COUNTDOWN_DELAY_SECONDS
        }
    }

    /**
     * Updates visibility of scene overlay and completion action based on level completion status.
     *
     * @param levelCompleted The completion status of the level
     * @param sceneOverlay The scene overlay view
     * @param completionAction The completion action view
     */
    private fun updateVisibilityBasedOnCompletion(levelCompleted: Boolean, sceneOverlay: View?, completionAction: View?) {
        sceneOverlay?.visible = !levelCompleted
        completionAction?.visible = levelCompleted
    }

    /**
     * Checks if the first phase countdown should be started.
     *
     * @param levelCompleted The completion status of the level
     * @return true if the first phase countdown should be started, false otherwise
     */
    private fun shouldStartFirstPhaseCountdown(levelCompleted: Boolean): Boolean {
        return levelCompleted &&
               !tutorialCountdownActive &&
               !tutorialCountdownExplanationShown &&
               tutorialCountdownStartDelaySeconds <= 0.0 &&
               tutorialCountdownButton != null
    }

    /**
     * Starts the first phase countdown with explanation and second phase setup.
     *
     * @return The countdown view
     */
    private fun startFirstPhaseCountdown() {
        startTutorialCountdown {
            startSecondPhaseAfterExplanation()
        }
    }

    /**
     * Shows the challenge explanation and starts the second phase countdown.
     */
    private fun startSecondPhaseAfterExplanation() {
        tutorialExplanationView?.let { explanation ->
            showChallengeExplanation(explanation)
            challengeExplanationVisible = true
            tutorialCountdownExplanationShown = true

            startSecondPhaseCountdown()
        }
    }

    /**
     * Starts the second phase countdown that transitions to level menu.
     */
    private fun startSecondPhaseCountdown() {
        startTutorialCountdown {
            launch {
                LevelMenuScene.backDestination = LevelMenuBackDestination.LEVEL_SCENE
                sceneContainer.changeTo<LevelMenuScene>()
            }
        }
    }

    /**
     * Shows the tutorial completion message until the Challenge-Modus explanation is opened.
     *
     * @param levelCompleted The completion status of the level
     * @param challengeResult The challenge result
     * @param levelOnlyEndStateOverlay The level only end state overlay
     * @param challengeEndStateOverlay The challenge end state overlay
     */
    override fun updateEndStateUi(
        levelCompleted: Boolean,
        challengeResult: ChallengeResult?,
        levelOnlyEndStateOverlay: View,
        challengeEndStateOverlay: View
    ) {
        tutorialCompletionOverlay = levelOnlyEndStateOverlay
        levelOnlyEndStateOverlay.visible = levelCompleted && challengeResult == null && !challengeExplanationVisible
        challengeEndStateOverlay.visible = false
    }
}
