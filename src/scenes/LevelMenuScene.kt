package scenes

import domain.GameState
import domain.GameState.tutorialActivated
import domain.SimulationState
import korlibs.image.color.Colors
import audio.InteractionSoundController
import audio.InteractionSoundEffect
import audio.onClickWithSound
import korlibs.korge.scene.Scene
import korlibs.korge.view.SContainer
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.align.centerXOn
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.position
import korlibs.korge.view.text
import korlibs.math.geom.vector.StrokeInfo
import kotlinx.coroutines.launch
import ui.Assets.bitcountSingle
import persistence.PredefinedLevelCatalog
import ui.ControlButtons
import korlibs.korge.view.addUpdater
import korlibs.korge.view.Container
import korlibs.image.color.RGBA
import korlibs.korge.view.xy

/**
 * Displays selectable predefined levels and navigation to the saved-network menu.
 */
open class LevelMenuScene : Scene(){

    /** Active status of the level menu autoload countdown */
    private var menuCountdownActive = false
    /** Remaining seconds of the autoload countdown */
    private var menuCountdownValue = 0.0
    /** Visual container that renders the countdown's green progress fill */
    private var menuCountdownFill: Container? = null


    /**
     * One replaceable predefined level entry in the menu.
     *
     * @property levelId the visible menu number and logical slot index.
     * @property path the JSON file that can later be replaced by the real level.
     */
    data class PredefinedLevelSlot(
        val levelId: Int,
        val path: String
    )

    /**
     * Stores layout and scrolling constants for the level selection menu.
     */
    companion object {
        /** Scene target used by the back button. Callers set this before opening the scene. */
        var backDestination: LevelMenuBackDestination = LevelMenuBackDestination.LEVEL_SCENE

        /** Layout constants Levels*/
        /** Number of columns in the level grid. */
        const val COLUMNS = 4
        /** Length of the level card boxes. */
        const val BOX_LENGTH = 220.0
        /** Horizontal spacing between level card boxes. */
        const val GAP = 50.0
        /** Stroke thickness used for bordered UI boxes. */
        const val CARD_STROKE_THICKNESS = 1.5


        /** Base offset from the projected viewport edge for the level grid. */
        const val OFFSET_Y = 200.0

        /** Width of the button that opens the saved-network menu. */
        const val SAVED_NETWORK_BUTTON_W = 520.0

        /** Height of the button that opens the saved-network menu. */
        const val SAVED_NETWORK_BUTTON_H = 86.0

        /** Vertical position below the predefined level grid for saved-network navigation. */
        const val SAVED_NETWORK_BUTTON_Y = 2 * (BOX_LENGTH + GAP) + OFFSET_Y + 50

        /** Horizontal position that centers the saved-network button below the level grid. */
        fun savedNetworkButtonX(): Double {
            val gridWidth = COLUMNS * BOX_LENGTH + (COLUMNS - 1) * GAP
            return OFFSET_Y + gridWidth / 2.0 - SAVED_NETWORK_BUTTON_W / 2.0
        }

        /** Label of the button that opens the saved-network menu. */
        const val SAVED_NETWORK_BUTTON_TEXT = "Gespeicherte Netze"

        /** At most eight predefined level slots are shown in the level menu. */
        const val MAX_PREDEFINED_LEVELS = 8

        /** Length of countdown for automatically loading level 1 after tutorial level (in seconds) */
        const val LEVEL_ONE_COUNTDOWN_DURATION = 15

        /** Returns the replaceable file path for one predefined level slot. */
        fun predefinedLevelPath(levelId: Int): String {
            require(levelId in 1..MAX_PREDEFINED_LEVELS) {
                "levelId must be between 1 and $MAX_PREDEFINED_LEVELS"
            }
            return "level/level$levelId.json"
        }

        /**
         * Stores the selected level in GameState and remembers whether it came from a predefined slot.
         *
         * @param level the level that should become the active level.
         * @param predefinedLevelId the level menu slot id, or null for saved/custom levels.
         */
        internal fun assignLoadedLevel(level: domain.Level, predefinedLevelId: Int?) {
            GameState.currentLevel = level
            GameState.currentPredefinedLevelId = predefinedLevelId
            GameState.simulationState = SimulationState.CONSTRUCTION
        }

    }

    /**
     * Builds the level list, saved-network navigation button, and shared close control.
     */
    override suspend fun SContainer.sceneMain() {
        drawWhiteSceneBackground()

        /** Reset/init countdown state */
        menuCountdownActive = false
        menuCountdownValue = LEVEL_ONE_COUNTDOWN_DURATION.toDouble()    
        menuCountdownFill = null

        /** Arm the countdown only if the tutorial level was completed successfully. */
        val isTutorialSolved = GameState.currentLevel?.number == 0 && GameState.activeSimulation?.levelCompleted == true
        if (isTutorialSolved) {
            armMenuCountdown()
            /** Clear the simulation object so the countdown is not triggered repeatedly */
            GameState.activeSimulation = null
        }

        text("Levelauswahl", textSize = 78.0, color = Colors.BLACK, font = bitcountSingle) {
            centerXOn(this@sceneMain)
            y = 56.0
        }

        /** Container for the level grid */
        container {
            val mainContainer = createLevelGrid(this@sceneMain)
            mainContainer.centerXOn(this@sceneMain)
        }

        /** Add navigation buttons */
        setupNavigationButtons()

        /** Setup countdown updater */
        setupCountdownUpdater()
    }

    /**
     * Starts countdown audio only after KorGE has destroyed the previous scene.
     *
     * KorGE invokes the new scene's [sceneMain] before the old scene's [Scene.sceneDestroy]. Since
     * [LevelScene.sceneDestroy] stops timer channels, starting the sound in [sceneMain] allowed the
     * departing tutorial scene to silence it again. [sceneAfterInit] runs after that cleanup.
     */
    override suspend fun sceneAfterInit() {
        super.sceneAfterInit()
        if (menuCountdownActive) {
            playMenuCountdownSound()
        }
    }

    /**
     * Creates the grid of level buttons.
     *
     * @param parent The container to add the grid to.
     * @return The container holding the level grid.
     */
    private fun createLevelGrid(parent: SContainer): Container {
        return parent.container {
            for (num in 0 until 8) {
                val col = num % COLUMNS
                val row = num / COLUMNS
                val levelNum = num + 1
                createLevelButton(col, row, levelNum, num == 0 && menuCountdownActive)
            }
            createSavedNetworksButton()
        }
    }

    /**
     * Creates an individual level button.
     *
     * @param col The column of the button in the grid.
     * @param row The row of the button in the grid.
     * @param levelNum The level number of the button.
     * @param shouldShowCountdown Whether the button should show a countdown.
     */
    private fun Container.createLevelButton(col: Int, row: Int, levelNum: Int, shouldShowCountdown: Boolean) {
        this.container {
            /** Draw button background */
            graphics {
                fill(Colors.WHITE) {
                    rect(0.0, 0.0, BOX_LENGTH, BOX_LENGTH)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = CARD_STROKE_THICKNESS)) {
                    rect(0.0, 0.0, BOX_LENGTH, BOX_LENGTH)
                }
            }
            /** Add countdown progress fill if needed */
            if (shouldShowCountdown) {
                val fillLayer = this.container {
                    xy(0.0, 0.0)
                    graphics {
                        fill(RGBA(0, 255, 0, 178)) {
                            rect(CARD_STROKE_THICKNESS, CARD_STROKE_THICKNESS, 0.0, BOX_LENGTH - 2 * CARD_STROKE_THICKNESS)
                        }
                    }
                }
                menuCountdownFill = fillLayer
            }
            /** Add level number */
            text("$levelNum", textSize = 78.0, color = Colors.BLACK, font = bitcountSingle) {
                centerOn(this@container)
            }
            /** Position the button */
            position(col * (BOX_LENGTH + GAP) + OFFSET_Y, row * (BOX_LENGTH + GAP) + OFFSET_Y)
            /** Set click handler */
            onClickWithSound {
                cancelMenuCountdown()
                launch {
                    loadLevel(levelNum)
                }
            }
        }
    }

    /**
     * Creates the saved networks navigation button.
     */
    private fun Container.createSavedNetworksButton() {
        this.container {
            /** Draw button background */
            graphics {
                fill(Colors.WHITE) {
                    roundRect(0.0, 0.0, SAVED_NETWORK_BUTTON_W, SAVED_NETWORK_BUTTON_H, 5.0, 5.0)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = CARD_STROKE_THICKNESS)) {
                    rect(0.0, 0.0, SAVED_NETWORK_BUTTON_W, SAVED_NETWORK_BUTTON_H)
                }
            }

            /** Add level number */
            text(SAVED_NETWORK_BUTTON_TEXT, textSize = 36.0, color = Colors.BLACK, font = bitcountSingle) {
                centerOn(this@container)
            }

            /** Position the button */
            position(savedNetworkButtonX(), SAVED_NETWORK_BUTTON_Y)

            /** Set click handler */
            onClickWithSound {
                cancelMenuCountdown()
                launch {
                    sceneContainer.pushTo<SavedNetworksScene>()
                }
            }
        }
    }

    /**
     * Sets up the navigation buttons (close and back).
     */
    private fun SContainer.setupNavigationButtons() {
        ControlButtons.drawCloseButton(this).onClickWithSound {
            menuCountdownActive = false
            launch { navigateToGameplayScene() }
        }

        if(shouldShowBackButton()) {
            ControlButtons.drawBackButton(this).onClickWithSound {
                menuCountdownActive = false
                launch { navigateBackFromLevelMenu() }
            }
        }
    }

    /** Returns whether the level menu has a meaningful back target. */
    private fun shouldShowBackButton(): Boolean =
        backDestination == LevelMenuBackDestination.MENU_SCENE ||
            GameState.currentLevel != null ||
            tutorialActivated

    /** Navigates to the level-menu back target. */
    private suspend fun navigateBackFromLevelMenu() {
        when (backDestination) {
            LevelMenuBackDestination.LEVEL_SCENE -> navigateToGameplayScene()
            LevelMenuBackDestination.MENU_SCENE -> sceneContainer.changeTo<MenuScene>()
        }
    }

    /** Navigates back to the active tutorial or level gameplay scene. */
    private suspend fun navigateToGameplayScene() {
        if(tutorialActivated) {
            sceneContainer.changeTo<TutorialScene>()
        } else {
            sceneContainer.changeTo<LevelScene>()
        }
    }

    /**
     * Sets up the countdown updater for automatic level loading.
     */
    private fun SContainer.setupCountdownUpdater() {
        addUpdater { dt ->
            if (menuCountdownActive) {
                /** Subtract delta seconds from countdown */
                val deltaSeconds = dt.inWholeMicroseconds / 1_000_000.0
                menuCountdownValue -= deltaSeconds

                /** Calculate fill width */
                val fillWidth = (BOX_LENGTH - 2 * CARD_STROKE_THICKNESS) * (1.0 - (menuCountdownValue / LEVEL_ONE_COUNTDOWN_DURATION))

                /** Update fill graphics */
                menuCountdownFill?.removeChildren()
                menuCountdownFill?.graphics {
                    fill(RGBA(0, 255, 0, 178)) {
                        rect(
                            CARD_STROKE_THICKNESS,
                            CARD_STROKE_THICKNESS - 1,
                            fillWidth.coerceIn(0.0, BOX_LENGTH - 2 * CARD_STROKE_THICKNESS),
                            BOX_LENGTH - 2 * CARD_STROKE_THICKNESS
                        )
                    }
                }

                /** Load level 1 if countdown finishes */
                if (menuCountdownValue <= 0.0) {
                    cancelMenuCountdown()
                    launch {
                        loadLevel(1)
                    }
                }
            }
        }
    }

    /**
     * Loads a level.
     *
     * @param levelId the level number to load
     */
    open suspend fun loadLevel(levelId: Int) {
        println("Load Level $levelId")

        val level = PredefinedLevelCatalog.load(levelId)
        assignLoadedLevel(level, predefinedLevelId = levelId)
        ui.Assets.preloadVehicleGraphics(level.vehicleTypes)
        sceneContainer.changeTo<LevelScene>()
    }

    /**
     * Arms the level-menu autoload countdown. Audio starts after the countdown view exists.
     */
    private fun armMenuCountdown() {
        menuCountdownActive = true
        menuCountdownValue = LEVEL_ONE_COUNTDOWN_DURATION.toDouble()
    }

    /**
     * Starts the level-menu autoload countdown audio.
     */
    private fun playMenuCountdownSound() {
        // Restart the full sound when this scene takes ownership of the visible countdown.
        InteractionSoundController.stop(InteractionSoundEffect.TIMER_15S)
        InteractionSoundController.play(InteractionSoundEffect.TIMER_15S)
    }

    /**
     * Cancels the level-menu autoload countdown and stops its timer sound.
     */
    private fun cancelMenuCountdown() {
        if (!menuCountdownActive) return
        menuCountdownActive = false
        InteractionSoundController.stop(InteractionSoundEffect.TIMER_15S)
    }

    /**
     * Stops countdown audio when the scene is left by any route.
     */
    override suspend fun sceneDestroy() {
        cancelMenuCountdown()
        super.sceneDestroy()
    }
}
