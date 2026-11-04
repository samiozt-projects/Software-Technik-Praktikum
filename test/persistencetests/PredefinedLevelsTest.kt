package persistencetests

import domain.SourceNode
import domain.TargetNode
import domain.WeatherCondition
import persistence.PredefinedLevelCatalog
import persistence.LevelSerializer
import korlibs.io.async.suspendTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the predefined challenge levels that are selectable from the level menu.
 */
class PredefinedLevelsTest {

    /**
     * Verifies that there are exactly eight predefined levels available.
     */
    @Test
    fun levelMenu_exposesEightPredefinedLevels() {
        assertEquals(8, PredefinedLevelCatalog.levels.size)
        assertEquals((1..8).toList(), PredefinedLevelCatalog.levels.map { it.number })
    }

    /**
     * Verifies that all predefined levels exist and contain only source and target nodes.
     */
    @Test
    fun predefinedLevels_existAndContainOnlySourcesAndTargets() = suspendTest {
        for (entry in PredefinedLevelCatalog.levels) {
            val level = loadLevel(entry.path)

            assertEquals(entry.number, level.number)
            assertTrue(level.nodes.filterIsInstance<SourceNode>().isNotEmpty())
            assertTrue(level.nodes.filterIsInstance<TargetNode>().isNotEmpty())
            assertTrue(level.streets.isEmpty(), "${entry.path} should not contain predefined streets")
        }
    }

    /**
     * Verifies that the complexity of predefined levels increases progressively.
     */
    @Test
    fun predefinedLevels_increaseInComplexity() = suspendTest {
        val complexityScores = mutableListOf<Int>()
        for (entry in PredefinedLevelCatalog.levels) {
            val level = loadLevel(entry.path)
            val sources = level.nodes.filterIsInstance<SourceNode>()
            val targets = level.nodes.filterIsInstance<TargetNode>()

            val score = sources.size + targets.size + sources.sumOf { it.configurations.size }
            complexityScores.add(score)
        }

        assertEquals(complexityScores.sorted(), complexityScores)
    }

    /**
     * Verifies that later predefined levels use multiple sources, configurations, and targets.
     */
    @Test
    fun laterPredefinedLevels_useMultipleSourcesConfigurationsAndTargets() = suspendTest {
        val levels = mutableListOf<domain.Level>()
        for (entry in PredefinedLevelCatalog.levels) {
            levels.add(loadLevel(entry.path))
        }

        assertTrue(levels.any { it.nodes.filterIsInstance<SourceNode>().size > 1 })
        assertTrue(levels.any { it.nodes.filterIsInstance<TargetNode>().size > 1 })
        assertTrue(
            levels.any { level ->
                level.nodes.filterIsInstance<SourceNode>().any { source ->
                    source.configurations.size > 1
                }
            }
        )
    }

    /**
     * Verifies that every predefined level has a supported weather condition.
     */
    @Test
    fun predefinedLevels_defineSupportedWeatherConditions() = suspendTest {
        for (entry in PredefinedLevelCatalog.levels) {
            val level = loadLevel(entry.path)

            assertTrue(level.weatherCondition in WeatherCondition.entries)
        }
    }

    /**
     * Verifies that bundled levels include rainy and snowy starts, not only dry weather.
     */
    @Test
    fun predefinedLevels_includeWetAndSnowyStarts() = suspendTest {
        val weatherConditions = PredefinedLevelCatalog.levels
            .map { loadLevel(it.path).weatherCondition }
            .toSet()

        assertTrue(WeatherCondition.WET in weatherConditions)
        assertTrue(WeatherCondition.SNOWY in weatherConditions)
    }

    /**
     * Loads a level from the given resource path.
     * 
     * @param path The path to the level file.
     * @return The loaded level.
     */
    private suspend fun loadLevel(path: String) =
        LevelSerializer.loadFromResources(path)
}
