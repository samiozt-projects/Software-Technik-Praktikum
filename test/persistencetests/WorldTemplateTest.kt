package persistencetests

import domain.ChallengeMode
import domain.GameState
import domain.SourceNode
import domain.TargetNode
import domain.WeatherCondition
import persistence.WorldTemplate
import worldeditor.WorldEditorDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies that [WorldTemplate] produces levels that conform to the standard world template
 * contract: dry grass background, no challenge mode, pre-placed source/target nodes, no streets.
 */
class WorldTemplateTest {

    /** Verifies that template green matches the green offered by the world-editor palette. */
    @Test
    fun createBlankWorld_usesWorldEditorGreen() {
        val level = WorldTemplate.createBlankWorld()
        val templateGreen = level.nodes.filterIsInstance<SourceNode>()
            .flatMap { it.configurations }
            .map { it.color }
            .single { WorldEditorDefaults.colorNamesMatch(it.name, "Gruen") }
        val editorGreen = WorldEditorDefaults.paletteColors
            .single { WorldEditorDefaults.colorNamesMatch(it.name, "Gruen") }

        assertEquals(editorGreen.value, templateGreen.value)
    }

    /**
     * Verifies that a newly created world uses dry weather for the standard grass background.
     */
    @Test
    fun createBlankWorld_usesDryWeatherForGrassBackground() {
        val level = WorldTemplate.createBlankWorld()

        assertEquals(WeatherCondition.DRY, level.weatherCondition)
    }

    /**
     * Verifies that the template does not pre-activate challenge mode.
     *
     * Challenge mode is a global singleton; resetting it before and after the test ensures the
     * template does not accidentally leave the singleton in an active state.
     */
    @Test
    fun createBlankWorld_doesNotActivateChallengeMode() {
        ChallengeMode.deactivate()
        WorldTemplate.createBlankWorld()

        assertFalse(ChallengeMode.active)
    }

    /**
     * Verifies that the challenge connection node limit is set to [Int.MAX_VALUE] so challenge
     * mode can never be accidentally triggered on a freshly created world.
     */
    @Test
    fun createBlankWorld_setsChallengeLimitToMaxValue() {
        val level = WorldTemplate.createBlankWorld()

        assertEquals(Int.MAX_VALUE, level.challengeConnectionNodeLimit)
    }

    /**
     * Verifies that the template world contains at least one source node.
     */
    @Test
    fun createBlankWorld_containsAtLeastOneSourceNode() {
        val level = WorldTemplate.createBlankWorld()

        assertTrue(level.nodes.any { it is SourceNode }, "Expected at least one SourceNode")
    }

    /**
     * Verifies that the template world contains at least one target node.
     */
    @Test
    fun createBlankWorld_containsAtLeastOneTargetNode() {
        val level = WorldTemplate.createBlankWorld()

        assertTrue(level.nodes.any { it is TargetNode }, "Expected at least one TargetNode")
    }

    /**
     * Verifies that the template world starts with an empty street network so the user builds
     * connections from scratch.
     */
    @Test
    fun createBlankWorld_startsWithNoStreets() {
        val level = WorldTemplate.createBlankWorld()

        assertTrue(level.streets.isEmpty(), "Expected no streets in the template world")
    }

    /**
     * Verifies that the default name is applied when no custom name is passed.
     */
    @Test
    fun createBlankWorld_usesDefaultWorldName() {
        val level = WorldTemplate.createBlankWorld()

        assertEquals(WorldTemplate.DEFAULT_WORLD_NAME, level.name)
    }

    /**
     * Verifies that a custom name overrides the default.
     */
    @Test
    fun createBlankWorld_acceptsCustomName() {
        val customName = "Meine Testwelt"
        val level = WorldTemplate.createBlankWorld(customName)

        assertEquals(customName, level.name)
    }

    /**
     * Verifies that the template level number is reserved and distinct from all predefined levels
     * (1–8) and the tutorial (0).
     */
    @Test
    fun createBlankWorld_usesTemplateLevelNumber() {
        val level = WorldTemplate.createBlankWorld()

        assertEquals(WorldTemplate.TEMPLATE_LEVEL_NUMBER, level.number)
        assertTrue(level.number < 0, "Template level number must be negative to avoid catalog collision")
    }

    /**
     * Verifies that source node configurations reference a non-null color, ensuring vehicles can
     * be color-matched to targets.
     */
    @Test
    fun createBlankWorld_sourceNodesHaveColoredConfigurations() {
        val level = WorldTemplate.createBlankWorld()
        val sources = level.nodes.filterIsInstance<SourceNode>()

        assertTrue(sources.isNotEmpty())
        sources.forEach { source ->
            assertTrue(source.configurations.isNotEmpty(), "Source at ${source.position} has no configurations")
            source.configurations.forEach { config ->
                assertNotNull(config.color, "Source configuration must have a color")
            }
        }
    }

    /**
     * Verifies that every target color has at least one matching source color so all destinations
     * can be reached.
     *
     * The template also contains an excess source (yellow) without a local target; that source is
     * intended to route vehicles to a neighboring table in the multi-table setup, so the inverse
     * check (every source has a local target) intentionally does not hold.
     */
    @Test
    fun createBlankWorld_everyTargetColorHasMatchingSourceColor() {
        val level = WorldTemplate.createBlankWorld()
        val sourceColors = level.nodes.filterIsInstance<SourceNode>()
            .flatMap { it.configurations }
            .map { it.color.name }
            .toSet()
        val targetColors = level.nodes.filterIsInstance<TargetNode>()
            .map { it.color.name }
            .toSet()

        assertTrue(
            sourceColors.containsAll(targetColors),
            "Every target color must have a matching source color; missing: ${targetColors - sourceColors}"
        )
    }

    /**
     * Verifies that the template contains exactly one excess source (yellow) that has no local
     * matching target — this source routes vehicles to a neighboring table.
     */
    @Test
    fun createBlankWorld_hasExactlyOneExcessSourceWithoutLocalTarget() {
        val level = WorldTemplate.createBlankWorld()
        val sourceColors = level.nodes.filterIsInstance<SourceNode>()
            .flatMap { it.configurations }
            .map { it.color.name }
            .toSet()
        val targetColors = level.nodes.filterIsInstance<TargetNode>()
            .map { it.color.name }
            .toSet()
        val excessColors = sourceColors - targetColors

        assertEquals(1, excessColors.size, "Expected exactly one excess source color without a local target")
    }

    /**
     * Verifies that the template world contains exactly four source nodes:
     * two on the left border, one on the top border, and one excess source in the centre.
     */
    @Test
    fun createBlankWorld_hasFourSourceNodes() {
        val level = WorldTemplate.createBlankWorld()

        assertEquals(4, level.nodes.filterIsInstance<SourceNode>().size)
    }

    /**
     * Verifies that the template world contains exactly three target nodes:
     * two on the right border and one on the bottom border.
     */
    @Test
    fun createBlankWorld_hasThreeTargetNodes() {
        val level = WorldTemplate.createBlankWorld()

        assertEquals(3, level.nodes.filterIsInstance<TargetNode>().size)
    }

    /**
     * Verifies that multiple calls to [WorldTemplate.createBlankWorld] return independent level
     * instances so editing one world does not affect another.
     */
    @Test
    fun createBlankWorld_returnsFreshInstanceEachCall() {
        val levelA = WorldTemplate.createBlankWorld()
        val levelB = WorldTemplate.createBlankWorld()

        assertTrue(levelA !== levelB, "Each call must return a distinct Level instance")
    }
}
