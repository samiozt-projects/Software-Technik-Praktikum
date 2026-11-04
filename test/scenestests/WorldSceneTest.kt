package scenestests

import persistence.WorldTemplate
import rendering.LevelViewRenderer
import scenes.LevelScene
import scenes.WorldScene
import ui.Assets
import kotlinx.coroutines.runBlocking
import network.CoordinateDTO
import network.CoupledSinkVehicleHandover
import network.FieldPropertiesDTO
import network.ServerConnection
import network.TransitionNode
import scenes.WorldSelectionScene
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Verifies that [WorldScene] disables the UI controls that must not appear in the multi-table
 * world-editing workflow (speed selector, challenge indicator, weather indicator) while the
 * base [LevelScene] keeps them enabled by default.
 */
class WorldSceneTest {

    /**
     * Verifies that the simulation-speed selector is hidden in [WorldScene].
     *
     * World mode always runs at the default speed; the 1x / 2x / 5x / 10x control must not be
     * shown to the user.
     */
    @Test
    fun worldScene_hidesSimulationSpeedSelector() {
        val scene = WorldScene()

        assertFalse(scene.showSimulationSpeedSelector())
    }

    /**
     * Verifies that the base [LevelScene] still exposes the speed selector so existing predefined
     * levels are not accidentally affected by the world-scene change.
     */
    @Test
    fun levelScene_showsSimulationSpeedSelectorByDefault() {
        val scene = object : LevelScene() {}

        assertTrue(scene.showSimulationSpeedSelector())
    }

    /**
     * Verifies that the challenge-mode indicator is hidden in [WorldScene].
     *
     * Challenge mode is not part of the multi-table world workflow.
     */
    @Test
    fun worldScene_hidesChallengeIndicator() {
        val scene = WorldScene()

        assertFalse(scene.showChallengeIndicator())
    }

    /**
     * Verifies that the base [LevelScene] still shows the challenge indicator by default.
     */
    @Test
    fun levelScene_showsChallengeIndicatorByDefault() {
        val scene = object : LevelScene() {}

        assertTrue(scene.showChallengeIndicator())
    }

    /**
     * Verifies that the weather indicator is hidden in [WorldScene].
     *
     * Weather configuration is not part of world mode, but the inherited background renderer still
     * uses the world's stored dry weather condition.
     */
    @Test
    fun worldScene_hidesWeatherIndicator() {
        val scene = WorldScene()

        assertFalse(scene.showWeatherIndicator())
    }

    /**
     * Verifies that server worlds do not show the finite level result overlay.
     */
    @Test
    fun worldScene_hidesResultStatisticsOverlay() {
        val scene = WorldScene()

        assertFalse(scene.showResultStatisticsOverlay())
    }

    /**
     * Verifies that server worlds keep their simulation flow open-ended.
     */
    @Test
    fun worldScene_disablesFiniteSimulationResultFlow() {
        val scene = WorldScene()

        assertFalse(scene.usesFiniteSimulationResultFlow())
    }

    /**
     * Verifies that a selected server tile with a transition gets an outgoing handover port.
     */
    @Test
    fun worldScene_createSimulationUsesServerTransitionHandover() {
        ServerConnection.reset()
        try {
            WorldSelectionScene.openedWorld = WorldSelectionScene.WorldEntry(
                id = "world-1",
                number = 1,
                name = "Serverwelt",
                source = "Server"
            )
            ServerConnection.registerFieldTiles("world-1", serverFieldsWithOutgoingTransition())
            ServerConnection.claimTile("world-1", "field-0-0")

            val simulation = WorldScene().createSimulation(WorldTemplate.createBlankWorld())

            assertTrue(simulation.targetHandoverPort is CoupledSinkVehicleHandover)
        } finally {
            WorldSelectionScene.openedWorld = null
            ServerConnection.reset()
        }
    }

    /**
     * Verifies that the base [LevelScene] still shows the weather indicator by default.
     */
    @Test
    fun levelScene_showsWeatherIndicatorByDefault() {
        val scene = object : LevelScene() {}

        assertTrue(scene.showWeatherIndicator())
    }

    /**
     * Verifies that [WorldScene] overrides all three visibility controls at once, confirming that
     * none of the world-excluded features can appear through an un-overridden base method.
     */
    @Test
    fun worldScene_overridesAllThreeVisibilityControls() {
        val scene = WorldScene()

        assertFalse(scene.showSimulationSpeedSelector(), "Speed selector must be hidden in WorldScene")
        assertFalse(scene.showChallengeIndicator(), "Challenge indicator must be hidden in WorldScene")
        assertFalse(scene.showWeatherIndicator(), "Weather indicator must be hidden in WorldScene")
    }

    /**
     * Verifies that blank multi-table worlds use the same grass background as normal dry levels.
     */
    @Test
    fun blankWorld_usesDryGrassBackground() = runBlocking {
        Assets.loadAll()
        val world = WorldTemplate.createBlankWorld()

        assertSame(Assets.sunnyLandscape, LevelViewRenderer.backgroundForWeather(world.weatherCondition))
    }

    private fun serverFieldsWithOutgoingTransition(): List<FieldPropertiesDTO> {
        val transition = TransitionNode(
            nodeId = "transition-1",
            fromFieldId = "field-0-0",
            toFieldId = "field-0-1",
            coordinate = CoordinateDTO(100, 50),
            biDirectionalWay = false,
            level = 0
        )
        return listOf(
            FieldPropertiesDTO(
                fieldId = "field-0-0",
                active = false,
                coordinate = CoordinateDTO(0, 0),
                parentWorldId = "world-1",
                fieldTransitions = listOf(transition)
            ),
            FieldPropertiesDTO(
                fieldId = "field-0-1",
                active = false,
                coordinate = CoordinateDTO(100, 0),
                parentWorldId = "world-1",
                fieldTransitions = listOf(transition)
            ),
            FieldPropertiesDTO(
                fieldId = "field-1-0",
                active = false,
                coordinate = CoordinateDTO(0, 100),
                parentWorldId = "world-1"
            ),
            FieldPropertiesDTO(
                fieldId = "field-1-1",
                active = false,
                coordinate = CoordinateDTO(100, 100),
                parentWorldId = "world-1"
            )
        )
    }
}
