package scenestests

import domain.GameState
import domain.Level
import domain.SimulationSpeed
import domain.SimulationState
import imageprocessing.StreetDetectionIssue
import imageprocessing.StreetDetectionIssueKind
import scenes.LevelMenuScene
import scenes.LevelScene
import scenes.SavedNetworksScene
import ui.Scrollbar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Covers the predefined level slot contract used by the level menu.
 */
class LevelMenuSceneTest {

    /**
     * Verifies that the predefined level path uses the replaceable slot files.
     */
    @Test
    fun predefinedLevelPath_usesReplaceableSlotFiles() {
        assertEquals("level/level1.json", LevelMenuScene.predefinedLevelPath(1))
        assertEquals("level/level8.json", LevelMenuScene.predefinedLevelPath(8))
    }

    /**
     * Verifies that predefined level slots outside the menu limit are rejected.
     */
    @Test
    fun predefinedLevelPath_rejectsSlotsOutsideMenuLimit() {
        assertFailsWith<IllegalArgumentException> {
            LevelMenuScene.predefinedLevelPath(0)
        }
        assertFailsWith<IllegalArgumentException> {
            LevelMenuScene.predefinedLevelPath(9)
        }
    }

    /**
     * Verifies that the selected speed multiplies only the simulation delta.
     */
    @Test
    fun scaledSimulationDeltaSeconds_appliesSelectedSpeed() {
        assertEquals(1.0, LevelScene.scaledSimulationDeltaSeconds(1.0, SimulationSpeed.X1))
        assertEquals(2.0, LevelScene.scaledSimulationDeltaSeconds(1.0, SimulationSpeed.X2))
        assertEquals(5.0, LevelScene.scaledSimulationDeltaSeconds(1.0, SimulationSpeed.X5))
        assertEquals(10.0, LevelScene.scaledSimulationDeltaSeconds(1.0, SimulationSpeed.X10))
    }

    /**
     * Verifies that invalid frame deltas cannot move the simulation backwards or into NaN.
     */
    @Test
    fun scaledSimulationDeltaSeconds_rejectsInvalidFrameDeltas() {
        assertEquals(0.0, LevelScene.scaledSimulationDeltaSeconds(0.0, SimulationSpeed.X10))
        assertEquals(0.0, LevelScene.scaledSimulationDeltaSeconds(-1.0, SimulationSpeed.X10))
        assertEquals(0.0, LevelScene.scaledSimulationDeltaSeconds(Double.NaN, SimulationSpeed.X10))
        assertEquals(0.0, LevelScene.scaledSimulationDeltaSeconds(Double.POSITIVE_INFINITY, SimulationSpeed.X10))
    }

    /**
     * Verifies that the level menu exposes the requested saved-network navigation label.
     */
    @Test
    fun savedNetworkButton_usesRequestedLabel() {
        assertEquals("Gespeicherte Netze", LevelMenuScene.SAVED_NETWORK_BUTTON_TEXT)
    }

    /**
     * Verifies that the saved-network navigation button is centered under the predefined level grid.
     */
    @Test
    fun savedNetworkButton_isCenteredBelowLevelGrid() {
        val gridWidth = LevelMenuScene.COLUMNS * LevelMenuScene.BOX_LENGTH +
            (LevelMenuScene.COLUMNS - 1) * LevelMenuScene.GAP
        val gridCenterX = LevelMenuScene.OFFSET_Y + gridWidth / 2.0
        val buttonCenterX = LevelMenuScene.savedNetworkButtonX() +
            LevelMenuScene.SAVED_NETWORK_BUTTON_W / 2.0

        assertEquals(gridCenterX, buttonCenterX)
    }

    /**
     * Verifies that the saved-network menu exposes the requested heading.
     */
    @Test
    fun savedNetworksScene_usesRequestedHeading() {
        assertEquals("Gespeicherte Netze", SavedNetworksScene.HEADING_TEXT)
    }

    /**
     * Verifies that the fixed heading band covers the area above the saved-network list.
     */
    @Test
    fun savedNetworksScene_headerBandCoversAreaAboveList() {
        assertTrue(SavedNetworksScene.HEADER_BAND_H >= SavedNetworksScene.LIST_START_Y)
        assertTrue(SavedNetworksScene.HEADING_Y < SavedNetworksScene.LIST_START_Y)
    }

    /**
     * Verifies that scrolling is disabled when every saved-network row fits on screen.
     */
    @Test
    fun scrollbar_doesNotMoveContentThatFits() {
        val viewport = SavedNetworksScene.VIEWPORT_HEIGHT
        val content = SavedNetworksScene.savedNetworkContentHeight(3)

        assertFalse(Scrollbar.isVisible(viewport, content))
        assertEquals(0.0, Scrollbar.minScrollY(viewport, content))
        assertEquals(0.0, Scrollbar.clampScrollY(-500.0, viewport, content))
        assertEquals(0.0, Scrollbar.clampScrollY(500.0, viewport, content))
    }

    /**
     * Verifies that long lists stop scrolling when the final row is visible.
     */
    @Test
    fun scrollbar_stopsWhenLastRowIsVisible() {
        val viewport = SavedNetworksScene.VIEWPORT_HEIGHT
        val content = SavedNetworksScene.savedNetworkContentHeight(12)
        val expectedMinScroll = viewport - content

        assertTrue(expectedMinScroll < 0.0)
        assertTrue(Scrollbar.isVisible(viewport, content))
        assertEquals(expectedMinScroll, Scrollbar.minScrollY(viewport, content))
        assertEquals(expectedMinScroll, Scrollbar.clampScrollY(expectedMinScroll - 500.0, viewport, content))
        assertEquals(0.0, Scrollbar.clampScrollY(500.0, viewport, content))
    }

    /**
     * Verifies that the scrollbar thumb tracks the current scroll progress from top to bottom.
     */
    @Test
    fun scrollbar_thumbMovesFromTopToBottom() {
        val viewport = SavedNetworksScene.VIEWPORT_HEIGHT
        val content = SavedNetworksScene.savedNetworkContentHeight(12)
        val bar = SavedNetworksScene.SCROLLBAR_HEIGHT
        val minScroll = Scrollbar.minScrollY(viewport, content)
        val topOffset = Scrollbar.THUMB_INSET
        val bottomOffset = topOffset + Scrollbar.trackHeight(bar) - Scrollbar.thumbHeight(bar, viewport, content)

        assertTrue(Scrollbar.thumbHeight(bar, viewport, content) >= Scrollbar.MIN_THUMB_HEIGHT)
        assertEquals(topOffset, Scrollbar.thumbOffsetY(0.0, bar, viewport, content))
        assertEquals(bottomOffset, Scrollbar.thumbOffsetY(minScroll, bar, viewport, content))
    }

    /**
     * Verifies that dragging the thumb its full travel maps to the entire scroll range.
     */
    @Test
    fun scrollbar_thumbDragMapsTravelToScrollRange() {
        val viewport = SavedNetworksScene.VIEWPORT_HEIGHT
        val content = SavedNetworksScene.savedNetworkContentHeight(12)
        val bar = SavedNetworksScene.SCROLLBAR_HEIGHT
        val minScroll = Scrollbar.minScrollY(viewport, content)
        val travel = Scrollbar.trackHeight(bar) - Scrollbar.thumbHeight(bar, viewport, content)

        assertEquals(0.0, Scrollbar.scrollYFromThumbDrag(0.0, 0.0, bar, viewport, content))
        assertEquals(minScroll, Scrollbar.scrollYFromThumbDrag(0.0, travel, bar, viewport, content))
        assertEquals(minScroll, Scrollbar.scrollYFromThumbDrag(0.0, travel + 500.0, bar, viewport, content))
    }

    /**
     * Verifies that saved-network files are addressed inside the saved-level directory.
     */
    @Test
    fun savedLevelPath_usesSavedLevelDirectory() {
        assertEquals(
            "savedlevel/Network.json",
            SavedNetworksScene.savedLevelPath("Network.json")
        )
    }

    /**
     * Verifies that the saved-network menu only lists JSON files in a stable newest-first order.
     */
    @Test
    fun sortedSavedLevelJsonFiles_filtersJsonAndSortsNewestFirst() {
        assertEquals(
            listOf(
                "Level 2_21.05.2026 09.30.json",
                "Level 1_20.05.2026 13.19.json",
                "Eigenes Netz.json"
            ),
            SavedNetworksScene.sortedSavedLevelJsonFiles(
                listOf(
                    "notes.txt",
                    "Eigenes Netz.json",
                    "Level 1_20.05.2026 13.19.json",
                    "Level 2_21.05.2026 09.30.json",
                    "preview.png"
                )
            )
        )
    }

    /**
     * Verifies that saved-network rows display the file name without directory or extension.
     */
    @Test
    fun savedLevelDisplayName_removesDirectoryAndExtension() {
        assertEquals(
            "Level 0 (20.05.2026 13:19)",
            SavedNetworksScene.savedLevelDisplayName("savedlevel/Level 0_20.05.2026 13.19.json")
        )
    }

    /**
     * Verifies that filename-safe timestamp dots in the time are shown as colons.
     */
    @Test
    fun formatTimestampForDisplay_usesColonForTime() {
        assertEquals(
            "20.05.2026 13:19",
            SavedNetworksScene.formatTimestampForDisplay("20.05.2026 13.19")
        )
    }

    /**
     * Verifies that saved-network rows keep plain file names readable when no timestamp separator exists.
     */
    @Test
    fun savedLevelDisplayName_keepsPlainNamesWithoutTimestamp() {
        assertEquals(
            "Eigenes Netz",
            SavedNetworksScene.savedLevelDisplayName("savedlevel/Eigenes Netz.json")
        )
    }

    /**
     * Verifies that the delete control is placed in the trailing part of each saved-network row.
     */
    @Test
    fun deleteIconArea_isAtEndOfSavedNetworkButton() {
        val deleteIconLeft = SavedNetworksScene.LIST_ITEM_WIDTH -
            SavedNetworksScene.DELETE_ICON_SIZE -
            SavedNetworksScene.DELETE_ICON_PADDING

        assertTrue(deleteIconLeft > SavedNetworksScene.LOAD_AREA_WIDTH)
        assertEquals(
            SavedNetworksScene.DELETE_ICON_PADDING,
            SavedNetworksScene.LIST_ITEM_WIDTH - deleteIconLeft - SavedNetworksScene.DELETE_ICON_SIZE
        )
    }

    /**
     * Verifies that the next-level button uses the requested visible label.
     */
    @Test
    fun nextLevelButton_usesRequestedLabel() {
        assertEquals("Nächstes Level", LevelScene.NEXT_LEVEL_BUTTON_TEXT)
    }

    /**
     * Verifies that completion-only actions are hidden before the level is completed.
     */
    @Test
    fun completionActionVisible_isFalseBeforeCompletion() {
        assertFalse(LevelScene.completionActionVisible(levelCompleted = false))
    }

    /**
     * Verifies that completion-only actions become visible after the level is completed.
     */
    @Test
    fun completionActionVisible_isTrueAfterCompletion() {
        assertTrue(LevelScene.completionActionVisible(levelCompleted = true))
    }

    /**
     * Verifies that successful completion is treated as a terminal simulation result.
     */
    @Test
    fun simulationResultState_isTrueAfterCompletion() {
        assertTrue(LevelScene.simulationResultState(levelCompleted = true, congestionDetected = false))
    }

    /**
     * Verifies that congestion is treated as a terminal simulation result.
     */
    @Test
    fun simulationResultState_isTrueAfterCongestion() {
        assertTrue(LevelScene.simulationResultState(levelCompleted = false, congestionDetected = true))
    }

    /**
     * Verifies that normal playback without a result is not terminal.
     */
    @Test
    fun simulationResultState_isFalseBeforeResult() {
        assertFalse(LevelScene.simulationResultState(levelCompleted = false, congestionDetected = false))
    }

    /**
     * Verifies that the detection banner stays hidden without an ArUco marker issue.
     */
    @Test
    fun detectionStatusMessage_withoutArucoIssueHidesBanner() {
        assertNull(
            LevelScene.detectionStatusMessage(
                issues = emptyList(),
                simulationState = SimulationState.CONSTRUCTION
            )
        )
    }

    /**
     * Verifies that a missing ArUco marker issue shows the marker-specific hint.
     */
    @Test
    fun detectionStatusMessage_missingArucoMarkerShowsMarkerHint() {
        val issue = StreetDetectionIssue(
            kind = StreetDetectionIssueKind.MISSING_ARUCO_MARKERS,
            message = LevelScene.DETECTION_MISSING_ARUCO_MARKERS_TEXT
        )

        assertEquals(
            LevelScene.DETECTION_MISSING_ARUCO_MARKERS_TEXT,
            LevelScene.detectionStatusMessage(
                issues = listOf(issue),
                simulationState = SimulationState.CONSTRUCTION
            )
        )
    }

    /**
     * Verifies that a rejected illegal elevation jump (e.g. level 0 straight to level 2) shows a
     * dedicated hint instead of leaving the player without any explanation.
     */
    @Test
    fun detectionStatusMessage_invalidElevationTransitionShowsRampHint() {
        val issue = StreetDetectionIssue(
            kind = StreetDetectionIssueKind.INVALID_ELEVATION_TRANSITION,
            message = "Ungültiger Ebenensprung erkannt (Ebene 0 zu 2)."
        )

        assertEquals(
            LevelScene.DETECTION_INVALID_ELEVATION_TRANSITION_TEXT,
            LevelScene.detectionStatusMessage(
                issues = listOf(issue),
                simulationState = SimulationState.CONSTRUCTION
            )
        )
    }

    /**
     * Verifies that the ArUco marker banner is hidden outside construction mode.
     */
    @Test
    fun detectionStatusMessage_simulationModeHidesArucoBanner() {
        val issue = StreetDetectionIssue(
            kind = StreetDetectionIssueKind.MISSING_ARUCO_MARKERS,
            message = LevelScene.DETECTION_MISSING_ARUCO_MARKERS_TEXT
        )

        assertNull(
            LevelScene.detectionStatusMessage(
                issues = listOf(issue),
                simulationState = SimulationState.SIMULATION
            )
        )
    }

    /**
     * Verifies that the start button is disabled after successful completion.
     */
    @Test
    fun levelActionModeForResultState_disablesStartAfterCompletion() {
        assertEquals(
            ui.LevelActionButtonMode.DISABLED_START,
            LevelScene.levelActionModeForResultState(
                domain.NetworkState.COMPLETE,
                SimulationState.PAUSED,
                resultState = true
            )
        )
    }

    /**
     * Verifies that the pause button is disabled after congestion is detected.
     */
    @Test
    fun levelActionModeForResultState_disablesPauseAfterCongestion() {
        assertEquals(
            ui.LevelActionButtonMode.DISABLED_START,
            LevelScene.levelActionModeForResultState(
                domain.NetworkState.COMPLETE,
                SimulationState.SIMULATION,
                resultState = true
            )
        )
    }

    /**
     * Verifies that normal non-result states still use the regular action mode.
     */
    @Test
    fun levelActionModeForResultState_keepsStartBeforeResult() {
        assertEquals(
            ui.LevelActionButtonMode.START,
            LevelScene.levelActionModeForResultState(
                domain.NetworkState.COMPLETE,
                SimulationState.CONSTRUCTION,
                resultState = false
            )
        )
    }

    /**
     * Verifies that speed changes are blocked while a terminal result is visible.
     */
    @Test
    fun simulationSpeedSelectorEnabled_isFalseInResultState() {
        assertFalse(LevelScene.simulationSpeedSelectorEnabled(resultState = true))
        assertTrue(LevelScene.simulationSpeedSelectorEnabled(resultState = false))
    }

    /**
     * Verifies that playback is stopped after success or failure so statistics stop changing.
     */
    @Test
    fun pausePlaybackAfterResult_isTrueOnlyForTerminalResults() {
        assertTrue(LevelScene.pausePlaybackAfterResult(levelCompleted = true, congestionDetected = false))
        assertTrue(LevelScene.pausePlaybackAfterResult(levelCompleted = false, congestionDetected = true))
        assertFalse(LevelScene.pausePlaybackAfterResult(levelCompleted = false, congestionDetected = false))
    }

    /**
     * Verifies that weather configuration is blocked only after a terminal result was reached.
     */
    @Test
    fun weatherConfigurationEnabled_isFalseInResultState() {
        assertFalse(LevelScene.weatherConfigurationEnabled(resultState = true))
        assertTrue(LevelScene.weatherConfigurationEnabled(resultState = false))
    }

    /**
     * Verifies that completion text has vertical room inside the level end-state box.
     */
    @Test
    fun completionTextSize_fitsInsideLevelEndStateBox() {
        assertTrue(LevelScene.END_STATE_TEXT_SIZE < LevelScene.END_STATE_LEVEL_BOX_H)
    }

    /**
     * Verifies that completion text can be vertically centered in the end-state box.
     */
    @Test
    fun endStateTextY_centersTextInsideLevelEndStateBox() {
        val y = LevelScene.endStateTextY(LevelScene.END_STATE_TEXT_SIZE)

        assertEquals(
            (LevelScene.END_STATE_LEVEL_BOX_H - LevelScene.END_STATE_TEXT_SIZE) / 2.0,
            y
        )
        assertTrue(y > 0.0)
        assertTrue(y + LevelScene.END_STATE_TEXT_SIZE < LevelScene.END_STATE_LEVEL_BOX_H)
    }

    /**
     * Verifies that the challenge popup fits into the projected level view.
     */
    @Test
    fun challengePopup_fitsInsideViewport() {
        assertTrue(LevelScene.challengePopupX() > 0.0)
        assertTrue(LevelScene.challengePopupY() > 0.0)
        assertTrue(LevelScene.challengePopupX() + LevelScene.CHALLENGE_POPUP_W < LevelScene.TABLE_PX_W)
        assertTrue(LevelScene.challengePopupY() + LevelScene.CHALLENGE_POPUP_H < LevelScene.TABLE_PX_H)
    }

    /**
     * Verifies that the challenge popup action row fits inside the larger popup box.
     */
    @Test
    fun challengePopupButtons_fitInsidePopup() {
        assertTrue(
            LevelScene.CHALLENGE_POPUP_BUTTON_Y + LevelScene.CHALLENGE_POPUP_BUTTON_H <
                LevelScene.CHALLENGE_POPUP_H
        )
    }

    /**
     * Verifies that the level popup exposes the challenge rules with the active level limit.
     */
    @Test
    fun challengeRulesText_usesLevelSpecificConnectionNodeLimit() {
        val level = Level("Custom Challenge", 1, challengeConnectionNodeLimit = 7)

        assertEquals(
            "Maximal 7 Kreuzungen. Konfigurationen werden zurückgesetzt und gesperrt.",
            LevelScene.challengeRulesText(level)
        )
    }

    /**
     * Verifies that saved levels do not offer a next predefined level action.
     */
    @Test
    fun nextPredefinedLevelSlot_isNullForSavedLevels() {
        val nextSlot = LevelScene.nextPredefinedLevelSlot(
            currentLevelId = null,
            existingLevelIds = (1..LevelMenuScene.MAX_PREDEFINED_LEVELS).toSet()
        )

        assertNull(nextSlot)
    }

    /**
     * Verifies that the final predefined level has no follow-up slot.
     */
    @Test
    fun nextPredefinedLevelSlot_isNullForLastLevel() {
        val nextSlot = LevelScene.nextPredefinedLevelSlot(
            currentLevelId = LevelMenuScene.MAX_PREDEFINED_LEVELS,
            existingLevelIds = (1..LevelMenuScene.MAX_PREDEFINED_LEVELS).toSet()
        )

        assertNull(nextSlot)
    }

    /**
     * Verifies that the next-level action is unavailable when the following file is missing.
     */
    @Test
    fun nextPredefinedLevelSlot_isNullWhenNextFileDoesNotExist() {
        val nextSlot = LevelScene.nextPredefinedLevelSlot(
            currentLevelId = 3,
            existingLevelIds = emptySet()
        )

        assertNull(nextSlot)
    }

    /**
     * Verifies that the next-level action targets the immediate following replaceable level file.
     */
    @Test
    fun nextPredefinedLevelSlot_targetsImmediateNextLevelFile() {
        val nextSlot = LevelScene.nextPredefinedLevelSlot(
            currentLevelId = 3,
            existingLevelIds = setOf(4)
        )

        assertEquals(4, nextSlot?.levelId)
        assertEquals("level/level4.json", nextSlot?.path)
    }

    /**
     * Verifies that saved levels clear the predefined slot marker in GameState.
     */
    @Test
    fun assignLoadedLevel_clearsPredefinedLevelIdForSavedLevels() {
        val level = Level("Saved Level", 99)
        GameState.currentPredefinedLevelId = 4

        LevelMenuScene.assignLoadedLevel(level, predefinedLevelId = null)

        assertSame(level, GameState.currentLevel)
        assertNull(GameState.currentPredefinedLevelId)
        assertEquals(SimulationState.CONSTRUCTION, GameState.simulationState)
    }

    /**
     * Verifies that predefined levels remember their slot id in GameState.
     */
    @Test
    fun assignLoadedLevel_storesPredefinedLevelIdForMenuLevels() {
        val level = Level("Menu Level", 0)

        LevelMenuScene.assignLoadedLevel(level, predefinedLevelId = 2)

        assertSame(level, GameState.currentLevel)
        assertEquals(2, GameState.currentPredefinedLevelId)
        assertEquals(SimulationState.CONSTRUCTION, GameState.simulationState)
    }
}
