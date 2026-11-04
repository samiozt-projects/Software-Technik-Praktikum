package renderingtests

import domain.WeatherCondition
import kotlinx.coroutines.runBlocking
import rendering.LevelViewRenderer
import ui.Assets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Verifies that level backgrounds use the weather-specific landscape assets.
 */
class LevelViewRendererWeatherBackgroundTest {

    /**
     * Ensures each supported weather condition maps to the intended landscape image.
     */
    @Test
    fun backgroundForWeather_returnsWeatherSpecificLandscape() = runBlocking {
        Assets.loadAll()

        assertSame(Assets.sunnyLandscape, LevelViewRenderer.backgroundForWeather(WeatherCondition.DRY))
        assertSame(Assets.wetLandscape, LevelViewRenderer.backgroundForWeather(WeatherCondition.WET))
        assertSame(Assets.snowyLandscape, LevelViewRenderer.backgroundForWeather(WeatherCondition.SNOWY))
    }

    /**
     * Ensures wide images are scaled high enough to cover the full viewport.
     */
    @Test
    fun coverSize_forWideImageCoversViewportWithoutDistortion() {
        val (width, height) = LevelViewRenderer.coverSize(
            imageWidth = 2000.0,
            imageHeight = 500.0,
            viewportWidth = 1000.0,
            viewportHeight = 1000.0
        )

        assertEquals(4000.0, width)
        assertEquals(1000.0, height)
        assertTrue(width >= 1000.0)
        assertTrue(height >= 1000.0)
    }

    /**
     * Ensures tall images are scaled wide enough to cover the full viewport.
     */
    @Test
    fun coverSize_forTallImageCoversViewportWithoutDistortion() {
        val (width, height) = LevelViewRenderer.coverSize(
            imageWidth = 500.0,
            imageHeight = 2000.0,
            viewportWidth = 1000.0,
            viewportHeight = 1000.0
        )

        assertEquals(1000.0, width)
        assertEquals(4000.0, height)
        assertTrue(width >= 1000.0)
        assertTrue(height >= 1000.0)
    }

    /**
     * Ensures invalid image dimensions fail early instead of producing unusable sizes.
     */
    @Test
    fun coverSize_rejectsInvalidImageDimensions() {
        assertFailsWith<IllegalArgumentException> {
            LevelViewRenderer.coverSize(0.0, 100.0)
        }
        assertFailsWith<IllegalArgumentException> {
            LevelViewRenderer.coverSize(100.0, -1.0)
        }
    }
}
