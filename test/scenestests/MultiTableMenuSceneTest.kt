package scenestests

import scenes.LevelScene
import scenes.MultiTableMenuScene
import ui.ControlButtons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the pure layout contract of the multi-table pause menu.
 *
 * Mirrors [MenuSceneTest], since [MultiTableMenuScene] intentionally reproduces the same button
 * row and label layout as [scenes.MenuScene] so both pause-menu variants look identical.
 */
class MultiTableMenuSceneTest {

    /**
     * Verifies that the button row contains exactly four equally sized buttons.
     */
    @Test
    fun buttonRow_containsExactlyFourEqualButtons() {
        val row = MultiTableMenuScene.MenuLayout.buttonRow()

        assertEquals(4, row.buttons.size)
        assertEquals(
            MultiTableMenuScene.MultiTableMenuConfig.BOX_LENGTH * 4.0 +
                MultiTableMenuScene.MultiTableMenuConfig.BOX_SPACING * 3.0,
            row.bounds.width
        )
        assertEquals(MultiTableMenuScene.MultiTableMenuConfig.BOX_LENGTH, row.bounds.height)
    }

    /**
     * Verifies that the button row is centered on the projection.
     */
    @Test
    fun buttonRow_isCenteredOnTheProjection() {
        val row = MultiTableMenuScene.MenuLayout.buttonRow()

        assertEquals(LevelScene.TABLE_PX_W / 2.0, row.bounds.centerX)

        val labelGap = MultiTableMenuScene.MultiTableMenuConfig.BOX_SPACING / 2.0
        val blockHeight = MultiTableMenuScene.MultiTableMenuConfig.BOX_LENGTH + labelGap +
            MultiTableMenuScene.MultiTableMenuConfig.LABEL_HEIGHT
        val blockCenterY = row.bounds.y + blockHeight / 2.0

        assertEquals(LevelScene.TABLE_PX_H / 2.0, blockCenterY)
    }

    /**
     * Verifies that the button row places buttons on one line with configured gap, in the mockup
     * order: save, world selection, vehicle configuration, multi-table mode.
     */
    @Test
    fun buttonRow_placesButtonsOnOneLineWithConfiguredGap() {
        val row = MultiTableMenuScene.MenuLayout.buttonRow()

        assertEquals(row.saveButton.y, row.worldSelectionButton.y)
        assertEquals(row.worldSelectionButton.y, row.configurationButton.y)
        assertEquals(row.configurationButton.y, row.serverButton.y)

        val step = MultiTableMenuScene.MultiTableMenuConfig.BOX_LENGTH +
            MultiTableMenuScene.MultiTableMenuConfig.BOX_SPACING
        assertEquals(step, row.worldSelectionButton.x - row.saveButton.x)
        assertEquals(step, row.configurationButton.x - row.worldSelectionButton.x)
        assertEquals(step, row.serverButton.x - row.configurationButton.x)
    }

    /**
     * Verifies that each button's label matches its expected text and is aligned under its button.
     */
    @Test
    fun buttonLabels_matchButtonsAndUseConfiguredGap() {
        val row = MultiTableMenuScene.MenuLayout.buttonRow()
        val expectedLabels = listOf(
            MultiTableMenuScene.MultiTableMenuConfig.SAVE_BUTTON_LABEL,
            MultiTableMenuScene.MultiTableMenuConfig.WORLD_SELECTION_BUTTON_LABEL,
            MultiTableMenuScene.MultiTableMenuConfig.CONFIGURATION_BUTTON_LABEL,
            MultiTableMenuScene.MultiTableMenuConfig.SERVER_BUTTON_LABEL
        )

        assertEquals(expectedLabels, row.labels.map { it.text })
        row.buttons.zip(row.labels).forEach { (button, label) ->
            assertEquals(button.x, label.bounds.x)
            assertEquals(
                button.y + MultiTableMenuScene.MultiTableMenuConfig.BOX_LENGTH +
                    MultiTableMenuScene.MultiTableMenuConfig.BOX_SPACING / 2,
                label.bounds.y
            )
        }
    }

    /**
     * Verifies that the multi-table pause menu reuses the same server label as the normal pause
     * menu, since the globe button still leads to the same multi-table-mode scene.
     */
    @Test
    fun serverButtonLabel_matchesNormalPauseMenu() {
        assertEquals("Multi-Tisch-Modus", MultiTableMenuScene.MultiTableMenuConfig.SERVER_BUTTON_LABEL)
    }

    /**
     * Verifies that the close button is inset from the top right and inside the viewport, matching
     * the normal pause menu's anchor.
     */
    @Test
    fun closeButton_isInsetFromTopRightAndInsideViewport() {
        val closeButton = MultiTableMenuScene.MenuLayout.closeButton()
        val closeRight = closeButton.x + ControlButtons.BUTTON_SIZE
        val closeBottom = closeButton.y + ControlButtons.BUTTON_SIZE

        assertTrue(closeButton.x > 0.0)
        assertTrue(closeButton.y > 0.0)
        assertTrue(closeRight < LevelScene.TABLE_PX_W)
        assertTrue(closeBottom < LevelScene.TABLE_PX_H)
    }

    /**
     * Verifies that the icon sizes fit inside their button boxes.
     */
    @Test
    fun iconSizesFitInsideTheirButtonBoxes() {
        val defaultIconSize = MultiTableMenuScene.MenuLayout.iconSize()
        val carIconSize = MultiTableMenuScene.MenuLayout.iconSize(MultiTableMenuScene.MultiTableMenuConfig.CAR_ICON_SCALE)

        assertTrue(defaultIconSize > 0.0)
        assertTrue(carIconSize > defaultIconSize)
        assertTrue(defaultIconSize < MultiTableMenuScene.MultiTableMenuConfig.BOX_LENGTH)
        assertTrue(carIconSize < MultiTableMenuScene.MultiTableMenuConfig.BOX_LENGTH)
    }

    /**
     * Verifies that an explicit line break in a label (used for the configuration button, whose
     * single-word label has no natural wrap point) is preserved as two separate lines.
     */
    @Test
    fun labelLines_preservesExplicitLineBreakForConfigurationLabel() {
        val lines = MultiTableMenuScene.MenuLayout.labelLines(
            MultiTableMenuScene.MultiTableMenuConfig.CONFIGURATION_BUTTON_LABEL
        )

        assertEquals(listOf("Fahrzeug-", "konfiguration"), lines)
    }
}
