package uitests

import korlibs.korge.tests.ViewsForTesting
import korlibs.korge.view.text
import ui.Assets
import ui.SceneUIUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests [SceneUIUtils.truncateWithEllipsis] against real rendered text, since the function
 * measures actual glyph width rather than assuming a fixed character width.
 *
 * Covers the overflow seen with server-provided world IDs in [scenes.ServerMenuScene]'s
 * "Aktuelle Welt" label, which are not length-bounded like locally chosen world names.
 */
class SceneUIUtilsTest : ViewsForTesting() {

    /**
     * Verifies that text already inside the available width is left unchanged.
     */
    @Test
    fun truncateWithEllipsis_keepsShortTextUnchanged() = viewsTest {
        Assets.loadAll()
        val label = text("Testwelt", textSize = 20.0, font = Assets.bitcountSingle)
        val fullWidth = label.width

        SceneUIUtils.truncateWithEllipsis(label, maxWidth = fullWidth + 50.0)

        assertEquals("Testwelt", label.text)
    }

    /**
     * Verifies that text wider than the available width is shortened with a trailing ellipsis
     * and ends up within the requested width, mirroring an overlong server world ID.
     */
    @Test
    fun truncateWithEllipsis_shortensTextThatOverflowsMaxWidth() = viewsTest {
        Assets.loadAll()
        val fullText = "world-f34c5423-c41e-408e-82d2-97b58110534f"
        val label = text(fullText, textSize = 20.0, font = Assets.bitcountSingle)
        val maxWidth = label.width / 2.0

        SceneUIUtils.truncateWithEllipsis(label, maxWidth)

        assertTrue(label.text.length < fullText.length)
        assertTrue(label.text.endsWith("…"))
        assertTrue(label.width <= maxWidth)
    }

    /**
     * Verifies that truncation never throws and settles on the bare ellipsis when even a single
     * character does not fit the requested width.
     */
    @Test
    fun truncateWithEllipsis_settlesOnBareEllipsisWhenNothingElseFits() = viewsTest {
        Assets.loadAll()
        val label = text("Sehr langer Weltname", textSize = 20.0, font = Assets.bitcountSingle)

        SceneUIUtils.truncateWithEllipsis(label, maxWidth = 0.1)

        assertEquals("…", label.text)
    }
}
