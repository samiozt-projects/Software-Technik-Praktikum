package scenestests

import domain.GameState
import domain.Level
import domain.TargetNode
import domain.Color
import domain.ConnectionNode
import domain.Street
import domain.WeatherCondition
import korlibs.image.color.Colors
import kotlinx.coroutines.runBlocking
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import persistence.LevelSerializer
import scenes.LevelScene
import scenes.MenuScene
import ui.ControlButtons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the pure layout contract of the pause menu.
 *
 * The scene renders KorGE views from these values; testing the shared layout model
 * keeps the assertions independent of rendering internals while preventing the
 * tests from duplicating the placement formulas.
 */
class MenuSceneTest {

    /**
     * Verifies that the button row contains exactly four equally sized buttons.
     */
    @Test
    fun buttonRow_containsExactlyFourEqualButtons() {
        val row = MenuScene.MenuLayout.buttonRow()

        assertEquals(4, row.buttons.size)
        assertEquals(MenuScene.MenuConfig.BOX_LENGTH * 4.0 + MenuScene.MenuConfig.BOX_SPACING * 3.0, row.bounds.width)
        assertEquals(MenuScene.MenuConfig.BOX_LENGTH, row.bounds.height)
    }

    /**
     * Verifies that the button row is centered on the projection.
     */
    @Test
    fun buttonRow_isCenteredOnTheProjection() {
        val row = MenuScene.MenuLayout.buttonRow()

        assertEquals(LevelScene.TABLE_PX_W / 2.0, row.bounds.centerX)

        // The button boxes themselves sit above the projection's vertical center: the
        // full block (button box + gap + label field below it) is what is centered,
        // so users perceive button and label together as the centered element.
        val labelGap = MenuScene.MenuConfig.BOX_SPACING / 2.0
        val blockHeight = MenuScene.MenuConfig.BOX_LENGTH + labelGap + MenuScene.MenuConfig.LABEL_HEIGHT
        val blockCenterY = row.bounds.y + blockHeight / 2.0

        assertEquals(LevelScene.TABLE_PX_H / 2.0, blockCenterY)
    }

    /**
     * Verifies that the button row places buttons on one line with configured gap.
     */
    @Test
    fun buttonRow_placesButtonsOnOneLineWithConfiguredGap() {
        val row = MenuScene.MenuLayout.buttonRow()

        assertEquals(row.saveButton.y, row.loadLevelButton.y)
        assertEquals(row.loadLevelButton.y, row.configurationButton.y)
        assertEquals(row.configurationButton.y, row.serverButton.y)
        assertEquals(
            MenuScene.MenuConfig.BOX_LENGTH + MenuScene.MenuConfig.BOX_SPACING,
            row.loadLevelButton.x - row.saveButton.x
        )
        assertEquals(
            MenuScene.MenuConfig.BOX_LENGTH + MenuScene.MenuConfig.BOX_SPACING,
            row.configurationButton.x - row.loadLevelButton.x
        )
        assertEquals(
            MenuScene.MenuConfig.BOX_LENGTH + MenuScene.MenuConfig.BOX_SPACING,
            row.serverButton.x - row.configurationButton.x
        )
    }

    /**
     * Verifies that the button row has no overlap between clickable boxes.
     */
    @Test
    fun buttonRow_hasNoOverlapBetweenClickableBoxes() {
        val row = MenuScene.MenuLayout.buttonRow()

        val saveRight = row.saveButton.x + MenuScene.MenuConfig.BOX_LENGTH
        val loadLevelRight = row.loadLevelButton.x + MenuScene.MenuConfig.BOX_LENGTH
        val configurationRight = row.configurationButton.x + MenuScene.MenuConfig.BOX_LENGTH

        assertEquals(MenuScene.MenuConfig.BOX_SPACING, row.loadLevelButton.x - saveRight)
        assertEquals(MenuScene.MenuConfig.BOX_SPACING, row.configurationButton.x - loadLevelRight)
        assertEquals(MenuScene.MenuConfig.BOX_SPACING, row.serverButton.x - configurationRight)
    }

    /**
     * Verifies that each button's label matches its expected text, is aligned under
     * its button, and uses the configured spacing and label height.
     */
    @Test
    fun buttonLabels_matchButtonsAndUseConfiguredGap() {
        val row = MenuScene.MenuLayout.buttonRow()
        val expectedLabels = listOf(
            MenuScene.MenuConfig.SAVE_BUTTON_LABEL,
            MenuScene.MenuConfig.LOAD_LEVEL_BUTTON_LABEL,
            MenuScene.MenuConfig.CONFIGURATION_BUTTON_LABEL,
            MenuScene.MenuConfig.SERVER_BUTTON_LABEL
        )

        assertEquals(expectedLabels, row.labels.map { it.text })
        row.buttons.zip(row.labels).forEach { (button, label) ->
            assertEquals(button.x, label.bounds.x)
            assertEquals(
                button.y + MenuScene.MenuConfig.BOX_LENGTH + MenuScene.MenuConfig.BOX_SPACING / 2,
                label.bounds.y
            )
            assertEquals(MenuScene.MenuConfig.BOX_LENGTH, label.bounds.width)
            assertEquals(MenuScene.MenuConfig.LABEL_HEIGHT, label.bounds.height)
        }
    }

    /**
     * Verifies that the close button is inset from the top right and inside the viewport.
     */
    @Test
    fun closeButton_isInsetFromTopRightAndInsideViewport() {
        val closeButton = MenuScene.MenuLayout.closeButton()
        val closeRight = closeButton.x + ControlButtons.BUTTON_SIZE
        val closeBottom = closeButton.y + ControlButtons.BUTTON_SIZE

        assertTrue(closeButton.x > 0.0)
        assertTrue(closeButton.y > 0.0)
        assertTrue(closeRight < LevelScene.TABLE_PX_W)
        assertTrue(closeBottom < LevelScene.TABLE_PX_H)
    }

    /**
     * Verifies that the close button keeps the same anchor as the level pause button.
     */
    @Test
    fun closeButton_keepsSameAnchorAsLevelPauseButton() {
        val closeButton = MenuScene.MenuLayout.closeButton()
        val closeCenterX = closeButton.x + ControlButtons.BUTTON_SIZE / 2.0
        val closeCenterY = closeButton.y + ControlButtons.BUTTON_SIZE / 2.0

        assertEquals(
            LevelScene.TABLE_PX_W - ControlButtons.CORNER_OFFSET - ControlButtons.BUTTON_SIZE / 2.0,
            closeCenterX
        )
        assertEquals(ControlButtons.CORNER_OFFSET + ControlButtons.BUTTON_SIZE / 2.0, closeCenterY)
    }

    /**
     * Verifies that the icon sizes fit inside their button boxes.
     */
    @Test
    fun iconSizesFitInsideTheirButtonBoxes() {
        val defaultIconSize = MenuScene.MenuLayout.iconSize()
        val carIconSize = MenuScene.MenuLayout.iconSize(MenuScene.MenuConfig.CAR_ICON_SCALE)

        assertTrue(defaultIconSize > 0.0)
        assertTrue(carIconSize > defaultIconSize)
        assertTrue(defaultIconSize < MenuScene.MenuConfig.BOX_LENGTH)
        assertTrue(carIconSize < MenuScene.MenuConfig.BOX_LENGTH)
    }

    /**
     * Verifies that a level can be saved before a matching snapshot was remembered.
     */
    @Test
    fun saveAllowedForSnapshot_isTrueBeforeFirstSave() {
        GameState.currentLevel = Level("Save Test", 1)
        val snapshot = MenuScene.saveSnapshotFor(GameState.currentLevel!!)

        assertTrue(MenuScene.saveAllowedForSnapshot(snapshot))
    }

    /**
     * Verifies that saving the same construction twice is blocked.
     */
    @Test
    fun saveAllowedForSnapshot_isFalseAfterSameSnapshotWasSaved() {
        GameState.currentLevel = Level("Save Test", 1)
        val snapshot = MenuScene.saveSnapshotFor(GameState.currentLevel!!)

        GameState.markCurrentLevelSaved(snapshot)

        assertEquals(false, MenuScene.saveAllowedForSnapshot(snapshot))
    }

    /**
     * Verifies that the save UI can detect a level that already matches the remembered save.
     */
    @Test
    fun currentLevelAlreadySaved_isTrueAfterSameSnapshotWasSaved() = runBlocking {
        val level = Level("Already Saved Test", 1)
        GameState.currentLevel = level
        GameState.markCurrentLevelSaved(MenuScene.saveSnapshotFor(level))

        assertTrue(MenuScene.currentLevelAlreadySaved(level))
    }

    /**
     * Verifies that changing the construction after saving clears the already-saved state.
     */
    @Test
    fun currentLevelAlreadySaved_isFalseAfterConstructionChanged() = runBlocking {
        val level = Level("Changed After Save Test", 1)
        GameState.currentLevel = level
        GameState.markCurrentLevelSaved(MenuScene.saveSnapshotFor(level))

        level.addNode(ConnectionNode(Point(50.0, 50.0), elevation = 0))

        assertEquals(false, MenuScene.currentLevelAlreadySaved(level))
    }

    /**
     * Verifies that a matching saved JSON blocks another save after the level was reloaded.
     */
    @Test
    fun saveAllowedForSnapshot_isFalseAfterReloadWhenSavedJsonAlreadyMatches() {
        val savedLevel = Level("Save Test", 1)
        val savedSnapshot = MenuScene.saveSnapshotFor(savedLevel)
        val reloadedLevel = LevelSerializer.fromJsonString(LevelSerializer.toJsonString(savedLevel))
        GameState.currentLevel = reloadedLevel

        val reloadedSnapshot = MenuScene.saveSnapshotFor(reloadedLevel)

        assertEquals(false, MenuScene.saveAllowedForSnapshot(reloadedSnapshot, listOf(savedSnapshot)))
    }

    /**
     * Verifies that existing saved JSON files only block exact construction duplicates.
     */
    @Test
    fun saveAllowedForSnapshot_isTrueAfterReloadWhenConstructionChangedFromSavedJson() {
        val savedLevel = Level("Save Test", 1)
        val savedSnapshot = MenuScene.saveSnapshotFor(savedLevel)
        val reloadedLevel = LevelSerializer.fromJsonString(LevelSerializer.toJsonString(savedLevel))
        GameState.currentLevel = reloadedLevel

        reloadedLevel.addNode(ConnectionNode(Point(50.0, 50.0), elevation = 0))

        assertTrue(MenuScene.saveAllowedForSnapshot(MenuScene.saveSnapshotFor(reloadedLevel), listOf(savedSnapshot)))
    }

    /**
     * Verifies that changed level metadata can be saved again.
     */
    @Test
    fun saveAllowedForSnapshot_isTrueAfterLevelMetadataChanged() {
        val level = Level("Save Test", 1)
        GameState.currentLevel = level
        GameState.markCurrentLevelSaved(MenuScene.saveSnapshotFor(level))

        level.adhesionCoefficientModifier = 0.9

        assertTrue(MenuScene.saveAllowedForSnapshot(MenuScene.saveSnapshotFor(level)))
    }

    /**
     * Verifies that changed weather can be saved again because it is persisted in level JSON.
     */
    @Test
    fun saveAllowedForSnapshot_isTrueAfterWeatherChanged() {
        val level = Level("Save Test", 1, weatherCondition = WeatherCondition.DRY)
        GameState.currentLevel = level
        GameState.markCurrentLevelSaved(MenuScene.saveSnapshotFor(level))

        level.weatherCondition = WeatherCondition.WET

        assertTrue(MenuScene.saveAllowedForSnapshot(MenuScene.saveSnapshotFor(level)))
    }

    /**
     * Verifies that changed loose nodes can be saved again.
     */
    @Test
    fun saveAllowedForSnapshot_isTrueAfterLooseNodeChanged() {
        val level = Level("Save Test", 1)
        GameState.currentLevel = level
        GameState.markCurrentLevelSaved(MenuScene.saveSnapshotFor(level))

        level.addNode(ConnectionNode(Point(50.0, 50.0), elevation = 0))

        assertTrue(MenuScene.saveAllowedForSnapshot(MenuScene.saveSnapshotFor(level)))
    }

    /**
     * Verifies that a changed street construction can be saved again.
     */
    @Test
    fun saveAllowedForSnapshot_isTrueAfterStreetChanged() {
        val level = Level("Save Test", 1)
        GameState.currentLevel = level
        GameState.markCurrentLevelSaved(MenuScene.saveSnapshotFor(level))

        level.addStreet(
            Street(
                startNode = ConnectionNode(Point(0.0, 0.0), elevation = 0),
                endNode = ConnectionNode(Point(100.0, 0.0), elevation = 0),
                path = listOf(Point(0.0, 0.0), Point(100.0, 0.0))
            )
        )

        assertTrue(MenuScene.saveAllowedForSnapshot(MenuScene.saveSnapshotFor(level)))
    }

    /**
     * Verifies that incomplete street networks can be saved without requiring a complete route.
     */
    @Test
    fun saveAllowedForSnapshot_isTrueForIncompleteStreetNetwork() {
        val level = Level("Incomplete Save Test", 1)
        level.addStreet(
            Street(
                startNode = ConnectionNode(Point(0.0, 0.0), elevation = 0),
                endNode = ConnectionNode(Point(100.0, 0.0), elevation = 0),
                path = listOf(Point(0.0, 0.0), Point(100.0, 0.0))
            )
        )
        GameState.currentLevel = level

        assertTrue(MenuScene.saveAllowedForSnapshot(MenuScene.saveSnapshotFor(level)))
    }

    /**
     * Verifies that incomplete street networks can be saved again after a street change.
     */
    @Test
    fun saveAllowedForSnapshot_isTrueForChangedIncompleteStreetNetwork() {
        val level = Level("Incomplete Save Test", 1)
        val street = Street(
            startNode = ConnectionNode(Point(0.0, 0.0), elevation = 0),
            endNode = ConnectionNode(Point(100.0, 0.0), elevation = 0),
            path = listOf(Point(0.0, 0.0), Point(100.0, 0.0))
        )
        level.addStreet(street)
        GameState.currentLevel = level
        GameState.markCurrentLevelSaved(MenuScene.saveSnapshotFor(level))

        street.path = listOf(Point(0.0, 0.0), Point(50.0, 25.0), Point(100.0, 0.0))

        assertTrue(MenuScene.saveAllowedForSnapshot(MenuScene.saveSnapshotFor(level)))
    }

    /**
     * Verifies that simulation progress alone does not allow another save.
     */
    @Test
    fun saveAllowedForSnapshot_isFalseAfterOnlySimulationCountersChanged() {
        val level = Level("Save Test", 1)
        val target = TargetNode(
            position = Point(100.0, 100.0),
            elevation = 0,
            orientation = Angle.ZERO,
            color = Color("Red", Colors.RED)
        )
        level.addNode(target)
        GameState.currentLevel = level
        GameState.markCurrentLevelSaved(MenuScene.saveSnapshotFor(level))

        target.vehiclesArrived = 1

        assertEquals(false, MenuScene.saveAllowedForSnapshot(MenuScene.saveSnapshotFor(level)))
    }

    /**
     * Verifies the message shown when challenge mode blocks configuration.
     */
    @Test
    fun lockedConfigurationPopup_usesRequiredText() {
        assertEquals("Konfigurationsmenü im Challenge-Modus nicht verfügbar", MenuScene.MenuConfig.LOCKED_POPUP_TEXT)
        assertEquals("OK", MenuScene.MenuConfig.LOCKED_POPUP_CLOSE_TEXT)
    }

    /**
     * Verifies that the locked-configuration popup has enough room for message and action.
     */
    @Test
    fun lockedConfigurationPopup_hasExpandedReadableSize() {
        assertTrue(MenuScene.MenuConfig.LOCKED_POPUP_WIDTH > 1000.0)
        assertTrue(MenuScene.MenuConfig.LOCKED_POPUP_HEIGHT >= 250.0)
        assertTrue(MenuScene.MenuConfig.LOCKED_POPUP_WIDTH < LevelScene.TABLE_PX_W)
        assertTrue(MenuScene.MenuConfig.LOCKED_POPUP_HEIGHT < LevelScene.TABLE_PX_H)
    }
}
