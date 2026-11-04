package scenestests

import scenes.ConfigurationScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests scrolling behavior in the ConfigurationScene.
 */
class ConfigurationSceneScrollTest {

  /**
   * Verifies that five sources need scrolling because the second row exceeds the viewport.
   */
  @Test
  fun fiveSourcesNeedScrollingBecauseSecondRowExceedsViewport() {
    val maxPerRow = 4
    val height = ConfigurationScene.sourceCardsContentHeight(5, maxPerRow)
      assertEquals(2 * ConfigurationScene.CARD_SIZE + ConfigurationScene.CARD_GAP, height)

    val minScroll = ConfigurationScene.minSourceScrollY(5, maxPerRow)
      assertTrue(minScroll < 0.0, "a second row must be reachable via scroll")
  }

  /**
   * Verifies that four sources fit without scrolling.
   */
  @Test
  fun fourSourcesFitWithoutScrolling() {
    val maxPerRow = 4
    val minScroll = ConfigurationScene.minSourceScrollY(4, maxPerRow)
      assertEquals(0.0, minScroll)
  }
}
