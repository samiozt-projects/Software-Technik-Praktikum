package scenes

import korlibs.korge.view.SContainer
import domain.Level
import simulation.Simulation
import ui.ControlButtons

/**
 * Level scene tailored for multi-table worlds created from [persistence.WorldTemplate].
 *
 * Inherits the full simulation and rendering flow from [LevelScene], but hides UI controls that
 * are not part of the world-editing workflow:
 * - The simulation-speed selector (1x / 2x / 5x / 10x) is hidden because world editing always
 *   runs at the default speed.
 * - The challenge-mode indicator is hidden because challenge mode is not applicable to user-built
 *   worlds.
 * - The weather indicator is hidden.
 * - The standard level background renderer is reused so world mode shows the same dry grass
 *   landscape as normal dry levels.
 */
class WorldScene : LevelScene() {

    /**
     * World scenes do not show the simulation speed selector.
     *
     * @return always false.
     */
    override fun showSimulationSpeedSelector(): Boolean = false

    /**
     * World scenes do not show the challenge-mode indicator.
     *
     * @return always false.
     */
    override fun showChallengeIndicator(): Boolean = false

    /**
     * World scenes do not show the weather indicator.
     *
     * @return always false.
     */
    override fun showWeatherIndicator(): Boolean = false

    /**
     * World scenes are open-ended server worlds and do not have a meaningful local result overlay.
     *
     * @return always false.
     */
    override fun showResultStatisticsOverlay(): Boolean = false

    /**
     * World scenes should keep running even when local source counters or congestion flags change.
     *
     * @return always false.
     */
    override fun usesFiniteSimulationResultFlow(): Boolean = false

    /**
     * Creates a simulation with server transition handover wiring when a server tile is selected.
     *
     * @param level active world level.
     * @return simulation configured for multi-table handover where possible.
     */
    override fun createSimulation(level: Level): Simulation = WorldServerHandover.createSimulation(level)

    /**
     * Draws the globe indicator button to the left of the gear button.
     *
     * The globe signals that the user is currently editing a server world in multi-table mode.
     * It is purely informational and carries no click action.
     *
     * @param container scene container that receives the button.
     */
    override fun drawExtraTopRightButtons(container: SContainer) {
        ControlButtons.drawGlobeButton(container)
    }
}
