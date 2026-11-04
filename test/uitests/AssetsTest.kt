package uitests

import korlibs.image.bitmap.Bitmap32
import korlibs.image.color.Colors
import kotlinx.coroutines.runBlocking
import ui.Assets
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies that the required 2D sprites for the acceptance criteria are present
 * on disk and can be loaded through [ui.Assets] without falling back to the
 * magenta placeholder bitmap.
 */
class AssetsTest {
    /**
     * Describes one sprite that is required for the acceptance criteria.
     *
     * @property name Human-readable label used in assertion messages.
     * @property path Relative path of the sprite inside the repository.
     * @property bitmap Accessor for the loaded bitmap exposed by [ui.Assets].
     */
    private data class RequiredSprite(
        val name: String,
        val path: String,
        val bitmap: () -> Bitmap32
    )

    /**
     * Central list of all 2D sprites that must be available for the rendered
     * vehicles, sources, targets, and directional arrows.
     */
    private val requiredSprites = listOf(
        RequiredSprite("sourceArrow", "res/images/source.png") { Assets.arrowStart },
        RequiredSprite("targetArrow", "res/images/destination.png") { Assets.arrowEnd },
        RequiredSprite("car", "res/images/car.png") { Assets.car },
        RequiredSprite("truck", "res/images/truck.png") { Assets.truck },
        RequiredSprite("motorcycle", "res/images/motorcycle.png") { Assets.motorcycle },
        RequiredSprite("construction", "res/images/construction.png") { Assets.construction },
        RequiredSprite("sun", "res/images/sun.png") { Assets.sun },
        RequiredSprite("cloud", "res/images/cloud.png") { Assets.cloud },
        RequiredSprite("snowflake", "res/images/snowflake.png") { Assets.snowflake },
        RequiredSprite("sunnyLandscape", "res/images/landscape1.png") { Assets.sunnyLandscape },
        RequiredSprite("wetLandscape", "res/images/landscape2.png") { Assets.wetLandscape },
        RequiredSprite("snowyLandscape", "res/images/landscape3.png") { Assets.snowyLandscape }
    )

    /**
     * Verifies that every required sprite file exists on disk and is not empty.
     *
     * This catches missing resources in version control as well as invalid
     * placeholder files that would exist physically but still be unusable.
     */
    @Test
    fun requiredSpriteFilesExistAndAreNonEmpty() {
        for (sprite in requiredSprites) {
            val file = File(sprite.path)
            assertTrue(file.exists(), "required sprite '${sprite.name}' should exist at ${sprite.path}")
            assertTrue(file.length() > 0L, "required sprite '${sprite.name}' should not be empty")
        }
    }

    /**
     * Loads all shared sprites through the production asset pipeline.
     *
     * The assertions ensure that the required graphics are decoded correctly
     * and do not fall back to the all-magenta fallback bitmap.
     */
    @Test
    fun loadAll_loadsRequiredSpritesWithoutPlaceholderFallback() = runBlocking {
        Assets.loadAll()

        for (sprite in requiredSprites) {
            val bitmap = sprite.bitmap()
            assertTrue(bitmap.width > 0, "sprite '${sprite.name}' should have a positive width")
            assertTrue(bitmap.height > 0, "sprite '${sprite.name}' should have a positive height")
            assertTrue(
                bitmap.any { it != Colors.MAGENTA },
                "sprite '${sprite.name}' should not be the all-magenta fallback placeholder"
            )
        }
    }
}
