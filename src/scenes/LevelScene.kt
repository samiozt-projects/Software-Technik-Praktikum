package scenes

import audio.SimulationSoundController
import audio.InteractionSoundController
import audio.InteractionSoundEffect
import domain.*
import network.ServerConnection
import korlibs.io.file.std.resourcesVfs
import audio.onClickWithErrorSound
import audio.onClickWithSound
import korlibs.korge.view.addUpdater
import korlibs.korge.view.anchor
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.align.centerXOn
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.image
import korlibs.korge.view.position
import korlibs.korge.view.size
import korlibs.korge.view.text
import korlibs.korge.view.xy
import kotlinx.coroutines.launch
import rendering.syncViews
import ui.addVehiclePreview
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration
import domain.ChallengeMode
import domain.ChallengeResult
import domain.GameState
import domain.Level
import domain.Node
import domain.Street
import simulation.Simulation
import simulation.SimulationStatistics
import domain.SimulationSpeed
import domain.SimulationState
import domain.SourceNode
import domain.TargetNode
import simulation.TrafficFlowRating
import domain.Vehicle
import imageprocessing.CameraManager
import imageprocessing.DetectionPreviewBuffer
import imageprocessing.StreetDetection
import imageprocessing.StreetDetectionIssue
import imageprocessing.StreetDetectionIssueKind
import imageprocessing.StreetNetworkStabilityTracker
import imageprocessing.streetNetworkSignature
import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.scene.Scene
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.Text
import korlibs.korge.view.View
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import korlibs.math.geom.vector.StrokeInfo
import org.bytedeco.opencv.opencv_core.Mat
import persistence.LevelSerializer
import rendering.GameAreaScale
import rendering.LevelViewRenderer
import rendering.StreetRenderer
import ui.Assets
import ui.ControlButtons
import ui.LevelActionButtonMode
import kotlin.math.round

/**
 * Renders the active level, its simulation, and the always-visible level controls.
 *
 * The scene is intentionally generic: normal levels can use it directly, while special flows
 * such as the tutorial can subclass it and provide their own overlay and completion action.
 */
open class LevelScene : Scene() {

    /**
     * Mutable text handles for the result statistics overlay.
     *
     * @property overlay The container holding all statistic views.
     * @property title The title text.
     * @property rows  The row text handles for each traffic flow category.
     */
    private data class StatisticsOverlayViews(
        val overlay: Container,
        val title: Text,
        val rows: List<StatisticsRowTexts>
    )

    /**
     * Mutable text handles for one row in the result statistics overlay.
     *
     * @property label left-aligned statistic label text.
     * @property value right-aligned statistic value text.
     */
    private data class StatisticsRowTexts(
        val label: Text,
        val value: Text
    )

    /**
     * Mutable text handles for the construction-mode detection status banner.
     *
     * @property overlay The container holding the banner frame.
     * @property message The message text centered inside the banner.
     */
    private data class DetectionStatusBannerViews(
        val overlay: Container,
        val message: Text
    )

    /** Countdown state - needs to be accessible to handleLevelActionButtonClick */
    private var countdownValue = 0
    private var countdownActive = false
    private var countdownSoundPending = false
    private var lastCountdownUpdateTime = 0.0
    private var countdownPrerequisiteStableTime = 0.0
    private lateinit var countdownText: Text
    private lateinit var countdownContainer: Container

    /**
     * Route-gate cache - see [StartGateRouteCache] for why an uncommitted (empty) street network
     * must never pass the gate.
     */
    private val startGateRouteCache = StartGateRouteCache()

    /**
     * Diagnostic only, logged once per detection commit: structural health of the freshly committed
     * network. When the start gate blocks even though the level reports COMPLETE, the cause lives in
     * the committed network itself (e.g. a source whose street ended up bound to a different node, a
     * degenerate street the router drops, or a self-loop from an out-of-bounds street end) - this
     * line names it directly instead of leaving only the gate's symptom log.
     */
    private fun logCommittedNetworkHealth(level: Level) {
        val sourcesWithoutExit = level.nodes.filterIsInstance<SourceNode>()
            .filter { it.outgoingStreets.isEmpty() }
            .map { it.position.toString() }
        val targetsWithoutEntry = level.nodes.filterIsInstance<TargetNode>()
            .filter { it.incomingStreets.isEmpty() }
            .map { it.position.toString() }
        val degenerateStreets = level.streets.count { it.path.size < 2 }
        val selfLoops = level.streets.count { it.startNode === it.endNode }

        val issues = buildList {
            if (sourcesWithoutExit.isNotEmpty()) add("sources without outgoing street: $sourcesWithoutExit")
            if (targetsWithoutEntry.isNotEmpty()) add("targets without incoming street: $targetsWithoutEntry")
            if (degenerateStreets > 0) add("streets with fewer than 2 path points (invisible to the router): $degenerateStreets")
            if (selfLoops > 0) add("self-loop streets (start node == end node): $selfLoops")
        }
        val issueText = if (issues.isEmpty()) "no structural issues" else issues.joinToString("; ")
        println("[Commit] Network committed: ${level.streets.size} streets, ${level.nodes.size} nodes - $issueText")
    }

    /**
     * Start gate, cached per network state: (source, configuration) pairs the router cannot serve on
     * the current committed network - see [StartGateRouteCache].
     */
    private fun missingRoutesForCurrentNetwork(level: Level): List<Pair<SourceNode, SourceNodeConfiguration>> =
        startGateRouteCache.missingRoutesFor(level)
    private lateinit var challengePopup: Container

    /**
     * Countdown state for level completion/retrial buttons
     */
    private var completionCountdownActive = false
    private var completionCountdownValue = 0.0
    private var nextLevelCountdownButton: Container? = null
    private var nextLevelCountdownFill: Container? = null
    private var nextLevelCountdownAction: (() -> Unit)? = null
    private var menuCountdownButton: Container? = null
    private var menuCountdownFill: Container? = null
    private var menuCountdownAction: (() -> Unit)? = null
    private var saveCompletionFill: Container? = null
    private var saveCompletionActionSaved = false
    private var retryCountdownButton: Container? = null
    private var retryCountdownFill: Container? = null
    private var retryCountdownAction: (() -> Unit)? = null
    private var levelSuccessSoundPlayed = false
    private var completionCountdownStartDelaySeconds = 0.0

    companion object {
        /** Screen pixels per centimetre of table surface. */
        const val PX_PER_CM: Double = 20.0

        /** Physical table dimensions in cm. */
        /** Width of the table in cm. */
        const val TABLE_CM_W: Double = 90.0
        /** Height of the table in cm. */
        const val TABLE_CM_H: Double = 50.0

        /** Projected viewport in pixels. */
        /** Width of the viewport in pixels. */
        const val TABLE_PX_W: Double = TABLE_CM_W * PX_PER_CM
        /** Height of the viewport in pixels. */
        const val TABLE_PX_H: Double = TABLE_CM_H * PX_PER_CM

        /** Rendered size of direction-arrow overlays in pixels. */
        /** Width of the direction-arrow overlays. */
        val ARROW_L_PX: Double = GameAreaScale.virtualMetersToPixels(4.666666667)
        /** Width of the lanes. */
        val LANE_W_PX: Double = GameAreaScale.virtualMetersToPixels(4.0)

        /** Rendered size of small overlay controls in pixels. */
        const val MENU_BUTTON_PX: Double = 60.0

        /** Rendered size of construction markers for missing connections. */
        const val CONSTRUCTION_MARKER_PX: Double = 40.0

        /** Width of the construction-mode detection status banner. */
        const val DETECTION_STATUS_BANNER_W: Double = 1080.0

        /** Height of the construction-mode detection status banner. */
        const val DETECTION_STATUS_BANNER_H: Double = 76.0

        /** Vertical position of the construction-mode detection status banner. */
        const val DETECTION_STATUS_BANNER_Y: Double = TABLE_PX_H - DETECTION_STATUS_BANNER_H - 34.0

        /** Text size used in the construction-mode detection status banner. */
        const val DETECTION_STATUS_TEXT_SIZE: Double = 30.0

        /** Text shown when one or more ArUco edge markers are not visible to the camera. */
        const val DETECTION_MISSING_ARUCO_MARKERS_TEXT = "Bitte die schwarz-weißen Marker am Rand nicht verdecken."

        /** Text shown when a physical bridge piece skipped an elevation (e.g. level 0 straight to level 2). */
        const val DETECTION_INVALID_ELEVATION_TRANSITION_TEXT =
            "Ungültiger Ebenensprung: Bitte eine Rampe über die dazwischenliegende Ebene legen."

        /**
         * Text shown when the street network looks complete but the start gate still blocks the
         * countdown ([LevelScene.missingRoutesForCurrentNetwork] non-empty) - without this the
         * countdown silently never starts and nothing on screen explains why, since [NetworkState]
         * alone already reports COMPLETE. See [StartGateRouteCache]/[logBlockedSimulationStart] for
         * the matching console-log detail.
         */
        const val DETECTION_START_GATE_BLOCKED_TEXT =
            "Netzwerk erkannt, aber nicht jedes Fahrzeug hat eine durchgehende Route - bitte Straßen prüfen."

        /** Size and placement of the completion action buttons shown after success. */
        /** Width of the completion action buttons. */
        const val COMPLETION_ACTION_BUTTON_W: Double = 380.0
        /** Height of the completion action buttons. */
        const val COMPLETION_ACTION_BUTTON_H: Double = 86.0
        /** Horizontal gap between completion action buttons. */
        const val COMPLETION_ACTION_BUTTON_GAP: Double = 90.0
        /** Vertical position of the completion action buttons. */
        const val COMPLETION_ACTION_BUTTON_Y: Double = 748.0

        /** Width of the retry button shown after congestion. */
        const val RETRY_ACTION_BUTTON_W: Double = 380.0

        /** Height of the retry button shown after congestion. */
        const val RETRY_ACTION_BUTTON_H: Double = 86.0

        /** Vertical position of the retry button shown after congestion. */
        const val RETRY_ACTION_BUTTON_Y: Double = 748.0

        /** Width of the simulation result statistics overlay. */
        const val STATISTICS_OVERLAY_W: Double = 720.0

        /** Height of the simulation result statistics overlay. */
        const val STATISTICS_OVERLAY_H: Double = 446.0

        /** Vertical position of the simulation result statistics overlay. */
        const val STATISTICS_OVERLAY_Y: Double = 230.0

        /** Text size used for the statistics overlay title. */
        const val STATISTICS_TITLE_TEXT_SIZE: Double = 46.0

        /** Vertical offset of the statistics overlay title. */
        const val STATISTICS_TITLE_Y: Double = 24.0

        /** Horizontal padding for the inner statistics table border. */
        const val STATISTICS_TABLE_PADDING_X: Double = 22.0

        /** Vertical position of the inner statistics table border. */
        const val STATISTICS_TABLE_Y: Double = 82.0

        /** Bottom padding between the statistics table border and overlay border. */
        const val STATISTICS_TABLE_BOTTOM_PADDING: Double = 26.0

        /** Horizontal padding for labels and values inside the statistics table. */
        const val STATISTICS_TEXT_PADDING_X: Double = 40.0

        /** Text size used by statistics row labels and values. */
        const val STATISTICS_ROW_TEXT_SIZE: Double = 23.0

        /** Vertical position of the first statistics row. */
        const val STATISTICS_ROW_START_Y: Double = 106.0

        /** Vertical distance between statistics rows. */
        const val STATISTICS_ROW_GAP: Double = 48.0

        /** Vertical offset of dotted separator lines inside each statistics row. */
        const val STATISTICS_DOTTED_LINE_OFFSET_Y: Double = 30.0

        /** Width of one dot in the statistics separator lines. */
        const val STATISTICS_DOT_W: Double = 5.0

        /** Height of one dot in the statistics separator lines. */
        const val STATISTICS_DOT_H: Double = 4.0

        /** Horizontal distance from one statistics separator dot to the next. */
        const val STATISTICS_DOT_STEP_X: Double = 18.0

        /** Width of the end-state box. */
        const val END_STATE_BOX_W: Double = 720.0
        /** Height of the end-state box for levels. */
        const val END_STATE_LEVEL_BOX_H: Double = 104.0
        /** Height of the end-state box for challenges. */
        const val END_STATE_CHALLENGE_BOX_H: Double = 170.0
        /** Vertical position of the end-state box. */
        const val END_STATE_BOX_Y: Double = 44.0
        /** Text size used for the end-state text. */
        const val END_STATE_TEXT_SIZE: Double = 48.0

        /** Width of the challenge-mode popup window. */
        const val CHALLENGE_POPUP_W: Double = 1280.0
        /** Height of the challenge-mode popup window. */
        const val CHALLENGE_POPUP_H: Double = 430.0
        /** Width of the buttons inside the challenge-mode popup. */
        const val CHALLENGE_POPUP_BUTTON_W: Double = 180.0
        /** Height of the buttons inside the challenge-mode popup. */
        const val CHALLENGE_POPUP_BUTTON_H: Double = 58.0
        /** Vertical position of the buttons inside the challenge-mode popup. */
        const val CHALLENGE_POPUP_BUTTON_Y: Double = 340.0
        /** Title text for the challenge-mode popup. */
        const val CHALLENGE_POPUP_TITLE = "Challenge-Modus aktiv"
        /** Title text for the inactive challenge-mode popup. */
        const val CHALLENGE_POPUP_INACTIVE_TITLE = "Challenge-Modus inaktiv"
        /** Question text for the challenge-mode popup. */
        const val CHALLENGE_POPUP_QUESTION = "Challenge-Modus beenden?"
        /** Question text for the start challenge-mode popup. */
        const val CHALLENGE_POPUP_START_QUESTION = "Challenge-Modus starten?"
        /** Title text for the rules in the challenge-mode popup. */
        const val CHALLENGE_POPUP_RULES_TITLE = "Regeln"
        /** Text for the yes button in the challenge-mode popup. */
        const val CHALLENGE_POPUP_YES_TEXT = "Ja"
        /** Text for the no button in the challenge-mode popup. */
        const val CHALLENGE_POPUP_NO_TEXT = "Nein"
        /** Text for the blocked message in the challenge-mode popup. */
        const val CHALLENGE_POPUP_BLOCKED_TEXT = "Challenge nicht realistisch: zu viele Kreuzungen."

        /** Time interval between captures (in ms) */
        const val CAMERA_CAPTURE_INTERVAL_MS: Double = 1000.0

        /**
         * Defines the result-statistics rows in display order.
         *
         * Each entry contains the left-side label and a formatter that extracts the matching
         * value from [SimulationStatistics] for the right-side column.
         */
        private val STATISTICS_ROW_SPECS: List<Pair<String, (SimulationStatistics) -> String>> = listOf(
            "Verkehrsfluss:" to { statistics -> trafficFlowLabel(statistics.trafficFlowRating) },
            "Erzeugte Fahrzeuge:" to { statistics -> statistics.generatedVehicles.toString() },
            "Angekommene Fahrzeuge:" to { statistics -> statistics.arrivedVehicles.toString() },
            "Durchschnittsgeschwindigkeit:" to { statistics -> "${oneDecimalLabel(statistics.averageSpeedKmh)} km/h" },
            "Simulationszeit:" to { statistics -> simulationTimeLabel(statistics.simulationDurationSeconds) },
            "Straßennetzlänge:" to { statistics -> "${round(statistics.roadNetworkLengthMeters).toInt()} m" }
        )

        /** Length of countdown for automatically starting the simulation, when the street network is complete */
        const val COUNTDOWN_DURATION_SIMULATION_START = 3

        /** Time the detected street network must stay complete before the audible countdown starts. */
        const val COUNTDOWN_RECOGNITION_STABILITY_SECONDS = 0.45

        /** Length of countdown for automatically transitioning to next level or retry (in seconds) */
        const val COMPLETION_COUNTDOWN_DURATION = 15

        /** Delay before the post-completion countdown starts, so success feedback does not overlap it. */
        const val SUCCESS_SOUND_COUNTDOWN_DELAY_SECONDS = 1.2

        /**
         * Returns the left x-position of the centered end-state message box.
         *
         * @return left x-coordinate for the end-state box.
         */
        fun endStateBoxX(): Double = TABLE_PX_W / 2.0 - END_STATE_BOX_W / 2.0

        /**
         * Returns the left x-position of the centered detection status banner.
         */
        fun detectionStatusBannerX(): Double = TABLE_PX_W / 2.0 - DETECTION_STATUS_BANNER_W / 2.0

        /**
         * Selects the construction-mode detection status text from camera issues and the start gate.
         *
         * @param issues Latest issues returned by the camera street detection.
         * @param simulationState Current construction/simulation mode.
         * @param startGateBlocked True when the network reports [NetworkState.COMPLETE] but the start
         * gate still holds the countdown back ([LevelScene.missingRoutesForCurrentNetwork] non-empty) -
         * otherwise this looks like nothing is happening at all. Checked last: a concrete camera issue
         * (missing markers, invalid elevation transition) is more actionable and, once fixed, is what
         * usually also resolves the missing route.
         * @return Banner text, or null when no banner should be visible.
         */
        fun detectionStatusMessage(
            issues: List<StreetDetectionIssue>,
            simulationState: SimulationState,
            startGateBlocked: Boolean = false
        ): String? {
            if (simulationState != SimulationState.CONSTRUCTION) {
                return null
            }

            return if (issues.any { it.kind == StreetDetectionIssueKind.MISSING_ARUCO_MARKERS }) {
                DETECTION_MISSING_ARUCO_MARKERS_TEXT
            } else if (issues.any { it.kind == StreetDetectionIssueKind.INVALID_ELEVATION_TRANSITION }) {
                DETECTION_INVALID_ELEVATION_TRANSITION_TEXT
            } else if (startGateBlocked) {
                DETECTION_START_GATE_BLOCKED_TEXT
            } else {
                null
            }
        }

        /**
         * Returns the left x-position of the centered challenge-mode popup.
         *
         * @return left x-coordinate for the challenge popup.
         */
        fun challengePopupX(): Double = TABLE_PX_W / 2.0 - CHALLENGE_POPUP_W / 2.0

        /**
         * Returns the top y-position of the centered challenge-mode popup.
         *
         * @return top y-coordinate for the challenge popup.
         */
        fun challengePopupY(): Double = TABLE_PX_H / 2.0 - CHALLENGE_POPUP_H / 2.0

        /**
         * Returns the vertical position that centers completion text inside its message box.
         *
         * @param textHeight the measured text height.
         * @param boxHeight the container height that should center the text inside.
         * @return y-coordinate that vertically centers the text.
         */
        fun endStateTextY(textHeight: Double, boxHeight: Double = END_STATE_LEVEL_BOX_H): Double {
            return boxHeight / 2.0 - textHeight / 2.0
        }

        /**
         * Returns the user-facing challenge rules for the selected level.
         *
         * @param level the current level, or null if no level is available.
         * @return the rule text shown in the challenge popup.
         */
        fun challengeRulesText(level: Level?): String {
            val limit = level?.challengeConnectionNodeLimit ?: Level.DEFAULT_CHALLENGE_CONNECTION_NODE_LIMIT
            return "Maximal $limit Kreuzungen. Konfigurationen werden zurückgesetzt und gesperrt."
        }

        /** Text shown on the completion action that advances to the next predefined level slot. */
        const val NEXT_LEVEL_BUTTON_TEXT = "Nächstes Level"

        /** Text shown on the completion action that stores the current level as JSON. */
        const val SAVE_LEVEL_BUTTON_TEXT = "Level speichern"

        /** Text shown on the completion action that returns to the level selection menu. */
        const val LEVEL_MENU_BUTTON_TEXT = "Levelauswahl"

        /** Text shown on the failed-result action that reloads the current level setup. */
        const val RETRY_BUTTON_TEXT = "Erneut versuchen"

        /** Green fill used for completion countdown progress and saved-state feedback. */
        val COMPLETION_ACTION_FILL_COLOR: RGBA = RGBA(0, 255, 0, 178)

        /** Inset used so green action fills start below the left/top border. */
        const val COMPLETION_ACTION_FILL_INSET: Double = 2.0

        /**
         * Returns the inner fill width for a completion action button.
         *
         * @param buttonWidth full button width.
         * @param progress normalized fill progress from 0.0 to 1.0.
         * @return fill width inside the button border.
         */
        fun completionActionFillWidth(buttonWidth: Double, progress: Double): Double {
            return (buttonWidth - COMPLETION_ACTION_FILL_INSET) * progress.coerceIn(0.0, 1.0)
        }

        /**
         * Returns the initial save-button fill progress for the completion action row.
         *
         * @param levelAlreadySaved true when the active level matches an existing save.
         * @return 1.0 for a fully green saved button, otherwise 0.0.
         */
        fun saveCompletionFillProgress(levelAlreadySaved: Boolean): Double =
            if (levelAlreadySaved) 1.0 else 0.0

        /**
         * Returns the left x-position of the centered statistics panel.
         *
         * @return left x-coordinate for the statistics panel.
         */
        fun statisticsOverlayX(): Double = TABLE_PX_W / 2.0 - STATISTICS_OVERLAY_W / 2.0

        /**
         * Returns the left x-position that centers the completion action row.
         *
         * @return left x-coordinate for the centered completion button row.
         */
        fun completionActionRowX(): Double {
            val rowWidth = COMPLETION_ACTION_BUTTON_W * 3.0 + COMPLETION_ACTION_BUTTON_GAP * 2.0
            return TABLE_PX_W / 2.0 - rowWidth / 2.0
        }

        /**
         * Returns the title shown for a terminal simulation result.
         *
         * @param success true when all configured vehicles arrived.
         * @return user-facing result title.
         */
        fun simulationResultTitle(success: Boolean): String {
            return if (success) "Level geschafft" else "Netz verstopft"
        }

        /**
         * Returns the localized label for a traffic-flow rating.
         *
         * @param rating domain rating to display.
         * @return German UI label.
         */
        fun trafficFlowLabel(rating: TrafficFlowRating): String {
            return when (rating) {
                TrafficFlowRating.EFFICIENT -> "Effizient"
                TrafficFlowRating.STABLE -> "Stabil"
                TrafficFlowRating.OVERLOADED -> "Überlastet"
            }
        }

        /**
         * Formats simulated seconds as m:ss.
         *
         * @param seconds duration in seconds.
         * @return compact time label.
         */
        fun simulationTimeLabel(seconds: Double): String {
            val totalSeconds = round(seconds.coerceAtLeast(0.0)).toInt()
            val minutes = totalSeconds / 60
            val remainingSeconds = totalSeconds % 60
            return "$minutes:${remainingSeconds.toString().padStart(2, '0')}"
        }

        /**
         * Formats one decimal place without relying on JVM-only formatters.
         *
         * @param value number to format.
         * @return localized decimal label using a comma.
         */
        fun oneDecimalLabel(value: Double): String {
            val roundedTenths = round(value * 10.0).toInt()
            val whole = roundedTenths / 10
            val tenth = kotlin.math.abs(roundedTenths % 10)
            return "$whole,$tenth"
        }

        /**
         * Creates the six statistic rows shown in the result overlay.
         *
         * @param statistics collected simulation metrics.
         * @return label/value pairs in display order.
         */
        fun statisticsRows(statistics: SimulationStatistics): List<Pair<String, String>> {
            return STATISTICS_ROW_SPECS.map { (label, valueForStatistics) ->
                label to valueForStatistics(statistics)
            }
        }

        /**
         * Number of statistic rows the overlay renders.
         *
         * @return number of statistic rows.
         */
        fun statisticsRowCount(): Int = STATISTICS_ROW_SPECS.size

        /**
         * Returns the next predefined level slot if the current level came from the level menu and
         * the following replaceable file exists.
         *
         * @param currentLevelId the currently loaded predefined level slot, or null for saved levels.
         * @param existingLevelIds the predefined slot ids that are currently available.
         * @return the next predefined slot, or null when no next slot exists.
         */
        fun nextPredefinedLevelSlot(
            currentLevelId: Int?,
            existingLevelIds: Set<Int>
        ): LevelMenuScene.PredefinedLevelSlot? {
            val nextLevelId = currentLevelId?.plus(1) ?: return null
            if (nextLevelId > LevelMenuScene.MAX_PREDEFINED_LEVELS) return null
            if (nextLevelId !in existingLevelIds) return null

            return LevelMenuScene.PredefinedLevelSlot(
                nextLevelId,
                LevelMenuScene.predefinedLevelPath(nextLevelId)
            )
        }

        /**
         * Defines when completion-only actions should be visible.
         *
         * @param levelCompleted true after the simulation marks the current level as completed.
         * @return true when completion actions should be shown.
         */
        fun completionActionVisible(levelCompleted: Boolean): Boolean = levelCompleted

        /**
         * Returns whether the simulation has reached a terminal result state.
         *
         * In a result state the simulation controls must no longer start, resume, pause, or change
         * playback speed. Follow-up actions such as next level, reload, and level selection stay
         * responsible for leaving that state.
         *
         * @param levelCompleted true when all configured vehicles reached their targets.
         * @param congestionDetected true when the simulation detected a blocked network.
         * @return true when regular simulation controls should be disabled.
         */
        fun simulationResultState(levelCompleted: Boolean, congestionDetected: Boolean): Boolean {
            return levelCompleted || congestionDetected
        }

        /**
         * Returns the action-button mode while respecting terminal simulation results.
         *
         * @param networkState current validity of the street network.
         * @param simulationState current simulation playback state.
         * @param resultState true when success or congestion has already been reached.
         * @return disabled mode in result state, otherwise the regular action mode.
         */
        fun levelActionModeForResultState(
            networkState: NetworkState,
            simulationState: SimulationState,
            resultState: Boolean
        ): LevelActionButtonMode {
            if (resultState) return LevelActionButtonMode.DISABLED_START
            return ControlButtons.levelActionMode(networkState, simulationState)
        }

        /**
         * Start gate: source configurations that currently have no driving route to a matching
         * target, even though the level may well report [NetworkState.COMPLETE].
         *
         * [LevelValidator] (object-reference graph) and [StreetPathFinder] (its own rebuilt
         * adjacency) can disagree about reachability - e.g. detection artifacts like a street bound
         * to a node with the wrong elevation, or a degenerate single-point path. Starting the
         * simulation in that state guarantees a source that never spawns and an immediate false
         * "congested" result. Checking the actual router right before starting closes that gap, and
         * also re-checks with the *current* network - covering a street that vanished during the
         * countdown's last seconds after validation had already passed.
         *
         * @return The (source, configuration) pairs without any route; empty when starting is safe.
         */
        fun sourceConfigurationsWithoutRoutes(level: Level): List<Pair<SourceNode, SourceNodeConfiguration>> =
            level.nodes.filterIsInstance<SourceNode>().flatMap { source ->
                source.configurations
                    .filter { cfg -> StreetPathFinder.findDrivingRoutes(level.streets, source, cfg.color).isEmpty() }
                    .map { cfg -> source to cfg }
            }

        /** Logs one line per route-less source configuration holding back the start countdown. */
        fun logBlockedSimulationStart(missingRoutes: List<Pair<SourceNode, SourceNodeConfiguration>>) {
            for ((source, cfg) in missingRoutes) {
                println(
                    "[StartGate] Source at ${source.position} (${cfg.vehicleType.name}, ${cfg.color.name}) " +
                        "has no driving route on the committed network - holding the start countdown " +
                        "until every configuration can actually spawn."
                )
            }
        }

        /**
         * Converts real frame time into simulated time for the active speed setting.
         *
         * @param realDeltaSeconds seconds elapsed in real time since the last frame.
         * @param speed selected simulation speed.
         * @return non-negative simulated seconds passed to [Simulation.runPlayback].
         */
        fun scaledSimulationDeltaSeconds(realDeltaSeconds: Double, speed: SimulationSpeed): Double {
            return speed.scaledDeltaSeconds(realDeltaSeconds)
        }

        /**
         * Returns whether simulation speed controls should accept input.
         *
         * @param resultState true after success or congestion was reached.
         * @return false in terminal result states.
         */
        fun simulationSpeedSelectorEnabled(resultState: Boolean): Boolean = !resultState

        /**
         * Returns whether the weather configuration can be opened from the level scene.
         *
         * Weather changes are gameplay configuration. Once a success or congestion result is
         * visible, changing weather would alter the already evaluated simulation result.
         *
         * @param resultState true after success or congestion was reached.
         * @return false in terminal result states.
         */
        fun weatherConfigurationEnabled(resultState: Boolean): Boolean = !resultState

        /**
         * Returns whether playback should stop immediately after a simulation tick reached a result.
         *
         * @param levelCompleted true when the simulation completed successfully.
         * @param congestionDetected true when the simulation detected a blocked network.
         * @return true when the scene must pause further playback.
         */
        fun pausePlaybackAfterResult(levelCompleted: Boolean, congestionDetected: Boolean): Boolean {
            return simulationResultState(levelCompleted, congestionDetected)
        }

        /**
         * Returns the speed that should drive playback when the selector is hidden.
         */
        fun defaultPlaybackSpeed(): SimulationSpeed = SimulationSpeed.DEFAULT

        /**
         * Returns the screen-space position where the missing-connection marker should appear.
         *
         * Level nodes are stored in virtual meters, while scene sprites are drawn in pixels. For
         * connection nodes the marker sits directly on the node. Source and target nodes are offset
         * to the visible connection side of their arrow sprite so the sign appears where a road
         * segment should start or end.
         *
         * @param node The node that should receive a construction marker.
         * @return The marker position in screen pixels.
         */
        fun constructionMarkerPositionForNode(node: Node): Point {
            val nodePositionPx = Point(
                GameAreaScale.virtualMetersToPixels(node.position.x),
                GameAreaScale.virtualMetersToPixels(node.position.y)
            )
            return when (node) {
                is SourceNode -> offsetAlongRenderedArrow(nodePositionPx, node.orientation)
                is TargetNode -> offsetAlongRenderedArrow(nodePositionPx, node.orientation)
                else -> nodePositionPx
            }
        }

        /**
         * Returns the render layer a vehicle should currently use.
         *
         * The simulation tracks the vehicle by its center point. For rendering, the visual footprint
         * matters: a vehicle entering a ramp should move to the higher layer as soon as its front
         * reaches that ramp, and it should stay there until its rear has left the ramp. The method
         * therefore samples the street under the vehicle center, front, and rear, then renders the
         * vehicle on the highest of those elevations so it never visually disappears below a ramp.
         */
        fun vehicleVisualElevation(vehicle: Vehicle): Int {
            val currentStreet = vehicle.currentStreet ?: return 0
            val currentElevation = StreetRenderer.renderedElevation(currentStreet)
            val route = vehicle.selectedRoute ?: return currentElevation
            val currentStreetIndex = currentRouteStreetIndexForRendering(vehicle, route, currentStreet)
                ?: return currentElevation
            val halfVehicleLength = vehicle.type.length / 2.0

            val frontElevation = streetAtRouteOffset(route, currentStreetIndex, vehicle, halfVehicleLength)
                ?.let { StreetRenderer.renderedElevation(it) } ?: currentElevation
            val rearElevation = streetAtRouteOffset(route, currentStreetIndex, vehicle, -halfVehicleLength)
                ?.let { StreetRenderer.renderedElevation(it) } ?: currentElevation

            return maxOf(currentElevation, frontElevation, rearElevation)
        }

        private fun currentRouteStreetIndexForRendering(
            vehicle: Vehicle,
            route: StreetPathFinder.DrivingRoute,
            currentStreet: Street
        ): Int? {
            // Prefer the simulation's route index, but fall back to identity search when the route
            // was recalculated or the hint has not caught up with the current street reference.
            val hintedIndex = vehicle.currentRouteStreetIndex
            if (route.streets.getOrNull(hintedIndex) === currentStreet) return hintedIndex
            return route.streets.indexOfFirst { it === currentStreet }.takeIf { it >= 0 }
        }

        /**
         * Returns the route street under a point that lies [offsetMeters] from the vehicle center.
         *
         * Positive offsets inspect the vehicle front and may walk into following route streets.
         * Negative offsets inspect the rear and may walk back into previous route streets. This is
         * what lets [vehicleVisualElevation] handle vehicles that span two different elevations.
         */
        private fun streetAtRouteOffset(
            route: StreetPathFinder.DrivingRoute,
            currentStreetIndex: Int,
            vehicle: Vehicle,
            offsetMeters: Double
        ): Street? {
            var streetIndex = currentStreetIndex
            var progressOnStreet = distanceAlongCurrentStreet(vehicle) + offsetMeters

            while (progressOnStreet < 0.0 && streetIndex > 0) {
                streetIndex--
                progressOnStreet += streetLength(route.streets[streetIndex].path)
            }

            while (streetIndex < route.streets.lastIndex) {
                val currentStreetLength = streetLength(route.streets[streetIndex].path)
                if (progressOnStreet < currentStreetLength) break
                progressOnStreet -= currentStreetLength
                streetIndex++
            }

            return route.streets.getOrNull(streetIndex)
        }

        /**
         * Calculates the vehicle center's progress along its current street path.
         *
         * The simulation stores progress within the current segment only. Rendering needs progress
         * along the whole street so front/rear offsets can cross segment boundaries correctly.
         */
        private fun distanceAlongCurrentStreet(vehicle: Vehicle): Double {
            val path = vehicle.currentStreet?.path ?: return 0.0
            if (path.size < 2) return 0.0

            val completedSegmentCount = vehicle.currentSegmentIndex
                .coerceIn(0, path.size - 1)
            var distance = 0.0
            for (i in 0 until completedSegmentCount.coerceAtMost(path.size - 1)) {
                distance += path[i].distanceTo(path[i + 1])
            }

            return distance + vehicle.distanceAlongSegment.coerceAtLeast(0.0)
        }

        /**
         * Returns the geometric length of a street path by summing all segment distances.
         */
        private fun streetLength(path: List<Point>): Double {
            var length = 0.0
            for (i in 1 until path.size) {
                length += path[i - 1].distanceTo(path[i])
            }
            return length
        }

        /**
         * Offsets a marker from the node center to the rendered connection side of an arrow.
         *
         * @param origin The node center in screen pixels.
         * @param orientation The source or target orientation stored in the level data.
         * @return The screen-space marker position at the rendered arrow connection side.
         */
        private fun offsetAlongRenderedArrow(origin: Point, orientation: Angle): Point {
            val connectionSideAngle = orientation - Angle.fromDegrees(90.0)
            val rad = connectionSideAngle.radians
            val distancePx = ARROW_L_PX / 2.0
            return Point(
                origin.x + distancePx * cos(rad),
                origin.y + distancePx * sin(rad)
            )
        }
    }

    /**
     * Builds the level scene and wires simulation, rendering, and completion overlays.
     *
     * @return nothing; the scene is created for its side effects.
     */
    override suspend fun SContainer.sceneMain() {
        val level = GameState.levelForSceneOrRecover(this@LevelScene::class.simpleName ?: "LevelScene")
            ?: run {
                recoverToLevelSelection()
                return
            }
        val simulationSoundController = SimulationSoundController.create(coroutineContext).also {
            it.primeCongestionState(GameState.activeSimulation?.congestionDetected == true)
        }
        launch {
            simulationSoundController.loadSounds()
        }

        /** Render background/landscape */
        renderBackground(this)

        /** Reset countdown state for this scene instance */
        countdownValue = 0
        countdownActive = false
        countdownSoundPending = false
        lastCountdownUpdateTime = 0.0
        countdownPrerequisiteStableTime = 0.0
        levelSuccessSoundPlayed = false
        completionCountdownStartDelaySeconds = 0.0

        var timeSinceLastCaptureMs = 0.0
        var isCapturing = false
        var lastDetectionIssues: List<StreetDetectionIssue> = emptyList()

        /**
         * Tracks [GameState.simulationState] frame to frame so [updateSceneFrame] can detect the
         * one-time transition into [SimulationState.SIMULATION] - see its use below for why that
         * transition needs to swap the rendered street network from the live detection preview to
         * the committed network vehicles actually drive on.
         */
        var lastObservedSimulationState: SimulationState? = null
        val networkStabilityTracker = StreetNetworkStabilityTracker()
        val detectionPreviewBuffer = DetectionPreviewBuffer()

        // One shared instance for the scene's lifetime: StreetDetection caches its detected ArUco
        // rectification across captures (see StreetDetection.arucoCache), which only has any effect
        // if the same instance is reused instead of created fresh per capture.
        val streetDetection = StreetDetection()

        /**
         * Interleaved render layers keep elevation ordering stable:
         * streets at elevation 0, vehicles at elevation 0, then the same pair for elevations 1 and
         * 2. Vehicles can be moved between these containers while the simulation runs, which avoids
         * redrawing the whole scene when a vehicle enters or leaves a ramp.
         *
         * The source/target node sprites are inserted between the ground streets (elevation 0) and
         * the elevation-0 vehicle layer. Vehicles must render on top of their garage/flag arrows so
         * they stay visible while spawning and arriving, while higher bridge layers still draw above
         * the nodes.
         */
        val streetLayers = mutableMapOf<Int, Container>()
        val detectedStreetLayers = mutableMapOf<Int, Container>()
        val vehicleLayers = mutableMapOf<Int, Container>()

        streetLayers[0] = container { }
        detectedStreetLayers[0] = container { }

        /** Render level nodes above the ground streets but below all vehicle layers. */
        LevelViewRenderer.renderLevelNodes(this)

        vehicleLayers[0] = container { }
        for (elevation in 1..2) {
            streetLayers[elevation] = container { }
            detectedStreetLayers[elevation] = container { }
            vehicleLayers[elevation] = container { }
        }
        val streetNetworkSprites = mutableMapOf<Pair<Int, List<List<Point>>>, View>()
        val streetRenderer = StreetRenderer()

        /** Wrapper class for elevation group keys to avoid confusion with street-based keys. */
        data class ElevationGroupKey(val elevation: Int)

        /** Separate sprites for camera-detected streets, attached to the matching elevation layer. */
        val detectedStreetSprites = mutableMapOf<ElevationGroupKey, View>()
        val detectedStreetRenderer = StreetRenderer()

        /** Container for construction elements */
        val constructionLayer = container { }
        val constructionSprites = mutableMapOf<Node, View>()

        /** Container for vehicle sprites */
        val vehicleLayer = container { }
        val vehicleSprites = mutableMapOf<Vehicle, View>()
        val vehicleSpriteElevations = mutableMapOf<Vehicle, Int>()

        val levelOnlyEndStateOverlay = container {
            xy(endStateBoxX(), END_STATE_BOX_Y)
            visible = false
            graphics {
                fill(Colors.WHITE) {
                    rect(0.0, 0.0, END_STATE_BOX_W, END_STATE_LEVEL_BOX_H)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = 3.0)) {
                    rect(0.0, 0.0, END_STATE_BOX_W, END_STATE_LEVEL_BOX_H)
                }
            }
        }

        val levelOnlyCompletedText = levelOnlyEndStateOverlay.text(
            completionText(),
            textSize = END_STATE_TEXT_SIZE,
            color = Colors.BLACK,
            font = Assets.bitcountSingle
        ).apply {
            x = END_STATE_BOX_W / 2.0 - width / 2.0
            y = endStateTextY(height)
        }

        val challengeEndStateOverlay = container {
            xy(endStateBoxX(), END_STATE_BOX_Y)
            visible = false
            graphics {
                fill(Colors.WHITE) {
                    rect(0.0, 0.0, END_STATE_BOX_W, END_STATE_CHALLENGE_BOX_H)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = 3.0)) {
                    rect(0.0, 0.0, END_STATE_BOX_W, END_STATE_CHALLENGE_BOX_H)
                }
            }
        }

        val levelCompletedText = challengeEndStateOverlay.text(
            completionText(),
            textSize = 48.0,
            color = Colors.BLACK,
            font = Assets.bitcountSingle
        ).apply {
            x = END_STATE_BOX_W / 2.0 - width / 2.0
            y = 28.0
            visible = false
        }

        val challengeResultText = challengeEndStateOverlay.text(
            "",
            textSize = 38.0,
            color = Colors.BLACK,
            font = Assets.bitcountSingle
        ).apply {
            x = END_STATE_BOX_W / 2.0 - width / 2.0
            y = 96.0
            visible = false
        }

        /**
         * Draws the scene overlay, which contains the simulation controls and game area.
         */
        val sceneOverlay = if (GameState.activeSimulation?.levelCompleted != true) {
            drawSceneOverlay(level)
        } else {
            null
        }

        /**
         * Draws the statistics overlay, which contains the simulation results.
         */
        val statisticsOverlay = if (showResultStatisticsOverlay()) {
            drawStatisticsOverlay()
        } else {
            null
        }

        /**
         * Draws the completion action button.
         */
        val completionAction = drawCompletionAction(level).apply {
            visible = false
        }

        /** Switch to construction mode, if there was no level loaded before */
        if (GameState.simulationState == SimulationState.NO_LEVEL) {
            GameState.simulationState = SimulationState.CONSTRUCTION
        }

        /**
         * Draws the retry action button.
         */
        val retryAction = drawRetryAction().apply {
            visible = false
        }

        val detectionStatusBanner = drawDetectionStatusBanner()

        /**
         * Returns whether the current simulation is already in a terminal result state.
         */
        fun currentResultState(): Boolean {
            if (!usesFiniteSimulationResultFlow()) return false
            val sim = GameState.activeSimulation
            return simulationResultState(
                levelCompleted = sim?.levelCompleted == true,
                congestionDetected = sim?.congestionDetected == true
            )
        }

        /**
         * Handles clicks on the main simulation action button using the latest result state.
         */
        fun handleActionButtonClick() {
            handleLevelActionButtonClick(level, currentResultState())
        }

        /**
         * Draws the action button, which is used to start and stop the simulation.
         */
        var actionButtonMode = levelActionModeForResultState(
            level.networkState,
            GameState.simulationState,
            currentResultState()
        )
        var actionButton = ControlButtons.drawLevelActionButton(
            this,
            actionButtonMode
        ).apply {
            onClickWithSound(interactionSoundForLevelAction(actionButtonMode)) { handleActionButtonClick() }
        }

        /**
         * Determines whether the simulation speed selector should be shown.
         * The speed selector is shown when the simulation is in a terminal result state.
         */
        val speedSelectorEnabled = showSimulationSpeedSelector()
        var speedSelector = if (speedSelectorEnabled) {
            ControlButtons.drawSimulationSpeedSelector(
                this,
                GameState.simulationSpeed,
                simulationSpeedSelectorEnabled(currentResultState())
            ) { selected ->
                GameState.simulationSpeed = selected
            }
        } else {
            null
        }
        var visibleSimulationSpeed = GameState.simulationSpeed
        var visibleSpeedSelectorInputEnabled = simulationSpeedSelectorEnabled(currentResultState())

        var visibleChallengeResult: ChallengeResult? = null
        var challengePopupOpenState = ChallengeMode.active
        var refreshChallengeIndicator: () -> Unit = {}
        challengePopup = drawChallengeModePopup(
            currentModeActive = { challengePopupOpenState },
            activateChallenge = {
                ChallengeMode.activate(level)
            },
            onModeChanged = { active ->
                challengePopupOpenState = active
                refreshChallengeIndicator()
            }
        ).apply {
            visible = false
        }
        /**
         * Opens the challenge popup and refreshes the visible challenge-mode state.
         */
        fun openChallengePopup() {
            challengePopupOpenState = ChallengeMode.active
            challengePopup.visible = true
        }

        /**
         * Adds the challenge popup click behavior to the indicator and returns the same container.
         */
        fun attachChallengeIndicatorClick(indicator: Container): Container {
            indicator.onClickWithSound {
                if (!currentResultState()) {
                    openChallengePopup()
                }
            }
            return indicator
        }

        /**
         * Opens the weather configuration and pauses active playback first.
         */
        suspend fun openWeatherConfiguration() {
            pauseSimulationForConfiguration()
            sceneContainer.changeTo<WeatherConfigurationScene>()
        }

        /**
         * Adds weather-configuration click behavior to the indicator.
         */
        fun attachWeatherIndicatorClick(indicator: Container): Container {
            indicator.onClickWithSound {
                if (weatherConfigurationEnabled(currentResultState())) {
                    launch { openWeatherConfiguration() }
                }
            }
            return indicator
        }
        var challengeIndicator: Container? = if (showChallengeIndicator()) {
            attachChallengeIndicatorClick(
                ControlButtons.drawChallengeModeIndicator(this, ChallengeMode.active)
            )
        } else null
        var challengeIndicatorActive = ChallengeMode.active
        refreshChallengeIndicator = {
            if (showChallengeIndicator()) {
                challengeIndicator?.removeFromParent()
                challengeIndicator = attachChallengeIndicatorClick(
                    ControlButtons.drawChallengeModeIndicator(this, ChallengeMode.active)
                )
                challengeIndicatorActive = ChallengeMode.active
            }
        }

        /** Countdown container with round background and text */
        countdownContainer = container {
            /** Position in horizontal center, similar to speed selector */
            x = TABLE_PX_W / 2.0
            y = ControlButtons.CORNER_OFFSET + ControlButtons.BUTTON_SIZE / 2 + 5  // Adjust for visual centering
            visible = false

            /** Round background like speed selector */
            graphics {
                val center = korlibs.math.geom.Point(0.0, 0.0)
                val radius = ControlButtons.BUTTON_SIZE * 0.7  // 42px radius = 84px diameter for 48px text
                fill(Colors.WHITE) {
                    circle(center, radius)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = ControlButtons.BORDER_STROKE_THICKNESS)) {
                    circle(center, radius)
                }
            }

            /** Countdown text */
            countdownText = text(
                "",
                textSize = 48.0,
                color = Colors.BLACK,
                font = Assets.bitcountSingle
            ).apply {
                /** Center text visually in the circle */
                x = -12.0
                y = -32.0
            }
        }
        var weatherIndicator: Container? = if (showWeatherIndicator()) {
            attachWeatherIndicatorClick(
                ControlButtons.drawWeatherIndicator(this, level.weatherCondition)
            )
        } else null
        var visibleWeatherCondition = level.weatherCondition

        ControlButtons.drawGearButton(this).onClickWithSound {
            launch {
                if (GameState.simulationState == SimulationState.SIMULATION) {
                    GameState.simulationState = SimulationState.PAUSED
                }
                if (ServerConnection.connectionState == ConnectionState.CONNECTED) {
                    sceneContainer.pushTo<MultiTableMenuScene>()
                } else {
                    sceneContainer.pushTo<MenuScene>()
                }
            }
        }
        drawExtraTopRightButtons(this)

        /**
         * Redraws simulation controls when their mode, speed, or enabled state changes.
         */
        fun updateSimulationControlViews() {
            val resultState = currentResultState()
            if (resultState) {
                challengePopup.visible = false
            }
            val nextActionButtonMode = levelActionModeForResultState(
                level.networkState,
                GameState.simulationState,
                resultState
            )
            if (nextActionButtonMode != actionButtonMode) {
                actionButton.removeFromParent()
                actionButton = ControlButtons.drawLevelActionButton(this, nextActionButtonMode).apply {
                    onClickWithSound(interactionSoundForLevelAction(nextActionButtonMode)) { handleActionButtonClick() }
                }
                actionButtonMode = nextActionButtonMode
            }

            val nextSpeedSelectorInputEnabled = simulationSpeedSelectorEnabled(resultState)
            if (
                speedSelectorEnabled &&
                (GameState.simulationSpeed != visibleSimulationSpeed ||
                    nextSpeedSelectorInputEnabled != visibleSpeedSelectorInputEnabled)
            ) {
                speedSelector?.removeFromParent()
                speedSelector = ControlButtons.drawSimulationSpeedSelector(
                    this,
                    GameState.simulationSpeed,
                    nextSpeedSelectorInputEnabled
                ) { selected ->
                    GameState.simulationSpeed = selected
                }
                visibleSimulationSpeed = GameState.simulationSpeed
                visibleSpeedSelectorInputEnabled = nextSpeedSelectorInputEnabled
            }
        }

        /**
         * Renders the level's committed street network into [streetLayers] - the single source of
         * truth vehicles actually drive on ([VehicleSpawnManager]/[StreetPathFinder] route over
         * [level.streets] directly). Must be re-run whenever [level.streets] changes, which currently
         * means: once at setup (below, for levels that already ship a fixed network, e.g. predefined/
         * tutorial levels) and again on the transition into [SimulationState.SIMULATION] (see
         * [updateSceneFrame]) - never on every detection commit during CONSTRUCTION, since during
         * construction the live detection preview ([renderDetectedStreetNetwork], drawn into the
         * separate [detectedStreetLayers]) is the player's feedback loop and both layers being kept in
         * sync at once would double-render every street.
         *
         * The snapshot key contains one entry per elevation so syncViews redraws only the layer
         * whose street paths changed. Each path is copied with toList() because the level can mutate
         * streets during construction, and the key must represent the state of this frame.
         */
        fun syncCommittedStreetNetworkView() {
            val streetNetworkSnapshots = (0..2).map { elevation ->
                elevation to level.streets
                    .filter { StreetRenderer.renderedElevation(it) == elevation }
                    .map { it.path.toList() }
            }.toSet()
            syncViews(streetNetworkSnapshots, streetNetworkSprites) { (elevation, _) ->
                streetRenderer.renderSavedStreetNetworkElevation(
                    streetLayers.getValue(elevation),
                    level.streets.toList(),
                    elevation
                )
            }
        }
        syncCommittedStreetNetworkView()

        /**
         * Cleans up old detected street sprites by removing them from the view hierarchy
         * and clearing the sprite map.
         */
        fun cleanupDetectedStreetSprites() {
            detectedStreetSprites.values.forEach { sprite ->
                sprite.removeFromParent()
            }
            detectedStreetSprites.clear()
        }

        /**
         * Renders the detected street network by drawing all streets together in a single container.
         * The StreetRenderer handles elevation grouping internally, ensuring visual continuity
         * when streets change elevation while maintaining proper layering.
         *
         * @param detectedStreets List of streets detected from the camera image.
         */
        fun renderDetectedStreetNetwork(detectedStreets: List<Street>) {
            // Clear old sprites before creating new ones
            cleanupDetectedStreetSprites()
            
            if (detectedStreets.isNotEmpty()) {
                // Render all streets together in a single container
                // The StreetRenderer will handle elevation grouping internally
                val key = ElevationGroupKey(0) // Use elevation 0 as the key for all streets
                val sprite = detectedStreetRenderer.renderSavedStreetNetwork(
                    detectedStreetLayers.getValue(0),
                    detectedStreets
                )
                detectedStreetSprites[key] = sprite
            }
        }

        /**
         * Captures the physical playfield using the camera and processes it to detect
         * drawn street connections, which are then synced to the view layer.
         *
         * The detected street keys include the rendered elevation. Without that elevation in the
         * key, a detected ramp and a flat street with the same path could reuse the wrong sprite
         * container and render in front of or behind the wrong objects.
         *
         * Camera detection is noisy: a single frame can momentarily show a spurious junction or
         * miss a segment even though nothing changed on the table. The captured frame is therefore
         * only ever detected once per capture ([StreetDetection.detectStreetGraph]) and its result
         * rendered immediately - so the player always gets instant feedback that detection is
         * running rather than staring at an unchanging screen - but is only checked against
         * [networkStabilityTracker] to decide whether anything *real* is touched. Only once the same
         * network has been seen on two matching captures ([StreetNetworkStabilityTracker] tolerates
         * at most one differing capture in between) is the same, already-computed result
         * applied to the live level ([StreetDetection.commitDetection]), so validation, the
         * auto-start countdown and any simulation that starts from it only ever see a network that
         * has proven stable, never a one-frame outlier - even while the on-screen preview already
         * updates live. Detection is the expensive part of this pipeline, so it deliberately never
         * runs twice for the same captured image.
         */
        fun captureStreetNetwork() {
            isCapturing = true
            timeSinceLastCaptureMs = 0.0
            launch {
                try {
                    val capturedImage = performCameraCapture()
                    if (capturedImage == null) {
                        lastDetectionIssues = emptyList()
                        return@launch
                    }
                    /** The capture runs asynchronously and can outlive the construction phase
                     * (e.g. the start countdown fired while the photo was taken). The result must
                     * then be discarded: committing a detection replaces the level's entire street
                     * network, which would rip the streets out from under the running simulation.
                     */
                    if (GameState.simulationState != SimulationState.CONSTRUCTION) {
                        capturedImage.release()
                        return@launch
                    }
                    val currentLevel = GameState.currentLevel!!

                    val pendingDetection = streetDetection.detectStreetGraph(currentLevel, capturedImage)
                    lastDetectionIssues = pendingDetection.detectionResult.issues

                    val previewStreets = streetDetection.toPreviewStreets(pendingDetection)
                    // The rendered preview is smoothed against one-capture dropouts; the stability
                    // tracker below must keep observing the RAW detection, so a genuinely flickering
                    // edge still blocks confirmation instead of being visually papered over and
                    // then wrongly committed.
                    renderDetectedStreetNetwork(detectionPreviewBuffer.update(previewStreets))

                    val action = networkStabilityTracker.observe(streetNetworkSignature(previewStreets))
                    // Re-check here, not just at the top of this coroutine: detectStreetGraph above can
                    // take long enough that the countdown finishes and flips to SIMULATION while this
                    // capture is still in flight. Committing now would replace the street network the
                    // just-started simulation is already using (see this function's class-level comment).
                    if (action == StreetNetworkStabilityTracker.Action.CONFIRMED &&
                        GameState.simulationState == SimulationState.CONSTRUCTION
                    ) {
                        streetDetection.commitDetection(pendingDetection)
                        logCommittedNetworkHealth(currentLevel)
                    }
                } finally {
                    isCapturing = false
                }
            }
        }

        /**
         * Moves an existing vehicle view to the layer matching the vehicle's current elevation.
         *
         * syncViews owns creation/removal of vehicle views, but layer changes are not a new vehicle.
         * This helper keeps the same view instance, reparents it only when the computed elevation
         * changes, and records the last elevation to avoid unnecessary container operations.
         */
        fun syncVehicleElevationLayer(vehicle: Vehicle, view: View, elevation: Int) {
            if (vehicleSpriteElevations[vehicle] == elevation) return

            view.removeFromParent()
            vehicleLayers.getValue(elevation).addChild(view)
            vehicleSpriteElevations[vehicle] = elevation
        }

        /**
         * Runs one playback step and synchronizes all vehicle views with their updated positions.
         *
         * @param sim Simulation object
         * @param realDeltaSeconds Seconds passed since last playback step
         */
        fun runPlaybackFrame(sim: Simulation, realDeltaSeconds: Double) {
            val playbackSpeed = if (speedSelectorEnabled) GameState.simulationSpeed else defaultPlaybackSpeed()
            sim.runPlayback(scaledSimulationDeltaSeconds(realDeltaSeconds, playbackSpeed))

            /** Syncs all vehicle views with their updated positions. */
            syncViews(level.vehicles.toSet(), vehicleSprites) { vehicle ->
                val bmp = Assets.getVehicleGraphic(vehicle.type.graphicRef)
                vehicleLayer.container {
                    addVehiclePreview(
                        bmp,
                        vehicle.color,
                        GameAreaScale.virtualMetersToPixels(vehicle.type.length),
                        GameAreaScale.virtualMetersToPixels(vehicle.type.length) * 0.6328125
                    )
                }
            }

            /** Positions and rotates all vehicle views. */
            for ((vehicle, view) in vehicleSprites) {
                val pos = vehicle.calculatePosition() ?: continue
                val elevation = vehicleVisualElevation(vehicle)
                syncVehicleElevationLayer(vehicle, view, elevation)
                view.position(GameAreaScale.virtualMetersToPixels(pos.x), GameAreaScale.virtualMetersToPixels(pos.y))
                view.rotation = currentSegmentAngle(vehicle)
                view.visible = true
                view.alpha = 1.0
            }

            updateCompletionUi(sim.levelCompleted, sceneOverlay, completionAction)
        }

        /**
         * Refreshes the completion and challenge result labels after a simulation frame.
         *
         * @param simulationStarted Whether the simulation has started
         * @param levelCompleted Whether the level has been completed
         * @return ChallengeResult object with the challenge result and deactivation status
         */
        fun updateEndStateTexts(simulationStarted: Boolean, levelCompleted: Boolean): ChallengeResult? {
            levelCompletedText.visible = levelCompleted
            if (levelCompletedText.visible) {
                levelCompletedText.x = END_STATE_BOX_W / 2.0 - levelCompletedText.width / 2.0
            }

            val currentChallengeResult = ChallengeMode.resultForAndDeactivateOnCompleted(
                level,
                levelCompleted,
                simulationStarted
            )
            if (currentChallengeResult != null) {
                visibleChallengeResult = currentChallengeResult
            }
            val challengeResult = visibleChallengeResult
            challengeResultText.text = ChallengeMode.resultText(challengeResult)
            challengeResultText.visible = challengeResult != null
            if (challengeResultText.visible) {
                challengeResultText.x = END_STATE_BOX_W / 2.0 - challengeResultText.width / 2.0
            }
            levelOnlyCompletedText.x = END_STATE_BOX_W / 2.0 - levelOnlyCompletedText.width / 2.0
            levelOnlyCompletedText.y = endStateTextY(levelOnlyCompletedText.height)
            return challengeResult
        }

        /**
         * Shows the optional result statistics overlay and matches the retry action to congestion.
         */
        fun updateSimulationStatisticsViews() {
            val congestionDetected = GameState.activeSimulation?.congestionDetected == true
            if (statisticsOverlay != null) {
                updateResultStatisticsUi(
                    GameState.activeSimulation,
                    statisticsOverlay,
                    completionAction,
                    retryAction,
                    sceneOverlay
                )
                retryAction.visible = congestionDetected
            } else {
                retryAction.visible = false
            }
        }

        /**
         * Rebuilds mode and weather indicators when their underlying state changes.
         *
         * Both indicators are optional: when a scene returns false from [showChallengeIndicator]
         * or [showWeatherIndicator] the variable stays null and no indicator is (re-)drawn.
         */
        fun updateStatusIndicators() {
            if (showChallengeIndicator() && ChallengeMode.active != challengeIndicatorActive) {
                refreshChallengeIndicator()
            }
            if (showWeatherIndicator() && level.weatherCondition != visibleWeatherCondition) {
                weatherIndicator?.removeFromParent()
                weatherIndicator = attachWeatherIndicatorClick(
                    ControlButtons.drawWeatherIndicator(this, level.weatherCondition)
                )
                visibleWeatherCondition = level.weatherCondition
            }
        }

        /**
         * Updates all UI elements that depend on the current simulation result state.
         * This includes end state texts, completion UI, statistics overlay, simulation controls,
         * and status indicators.
         */
        fun updateSimulationResultUi() {
            if (!usesFiniteSimulationResultFlow()) {
                completionAction.visible = false
                retryAction.visible = false
                levelOnlyEndStateOverlay.visible = false
                challengeEndStateOverlay.visible = false
                sceneOverlay?.visible = true
                updateSimulationControlViews()
                updateStatusIndicators()
                return
            }
            val simulationStarted = GameState.activeSimulation != null
            val levelCompleted = GameState.activeSimulation?.levelCompleted == true
            playLevelSuccessSoundOnce(levelCompleted)
            val challengeResult = updateEndStateTexts(simulationStarted, levelCompleted)
            updateCompletionUi(levelCompleted, sceneOverlay, completionAction)
            updateEndStateUi(levelCompleted, challengeResult, levelOnlyEndStateOverlay, challengeEndStateOverlay)
            updateSimulationStatisticsViews()
            updateSimulationControlViews()
            updateStatusIndicators()
        }

        /**
         * Handles one active simulation frame: stop on terminal results, otherwise advance
         * playback, then refresh all result overlays and controls that depend on simulation state.
         *
         * @param sim Simulation object
         * @param realDeltaSeconds Seconds passed since last playback step
         */
        fun updateActiveSimulationFrame(sim: Simulation, realDeltaSeconds: Double) {
            if (usesFiniteSimulationResultFlow() && pausePlaybackAfterResult(sim.levelCompleted, sim.congestionDetected)) {
                GameState.simulationState = SimulationState.PAUSED
            } else {
                runPlaybackFrame(sim, realDeltaSeconds)
            }

            updateSimulationResultUi()
        }




      /**
         * Refreshes construction markers for nodes that currently fail level validation.
         */
        fun syncConstructionMarkers() {
            syncViews(level.constructionMarkerNodes, constructionSprites) { markerNode ->
                val markerPosition = constructionMarkerPositionForNode(markerNode)
                constructionLayer.image(Assets.construction) {
                    size(CONSTRUCTION_MARKER_PX, CONSTRUCTION_MARKER_PX)
                    anchor(0.5, 0.5)
                    position(markerPosition.x, markerPosition.y)
                    alpha = 0.9
                }
            }
        }

            updateSimulationControlViews()

        /**
         * Triggers periodic camera-based street detection while the level is in construction mode.
         */
        fun updateCameraCapture(dt: Duration) {
            if (!cameraCaptureEnabled() || GameState.simulationState != SimulationState.CONSTRUCTION) return
            timeSinceLastCaptureMs += dt.inWholeMilliseconds.toDouble()
            if (!isCapturing && timeSinceLastCaptureMs >= CAMERA_CAPTURE_INTERVAL_MS) {
                captureStreetNetwork()
            }
        }

        /**
         * Advances the active simulation or moves terminal result states into pause mode.
         */
        fun updateSimulationPlayback(sim: Simulation?, realDeltaSeconds: Double) {
            if (sim != null && GameState.simulationState == SimulationState.SIMULATION) {
                updateActiveSimulationFrame(sim, realDeltaSeconds)
            } else if (sim != null && GameState.simulationState != SimulationState.PAUSED &&
                usesFiniteSimulationResultFlow() &&
                pausePlaybackAfterResult(sim.levelCompleted, sim.congestionDetected)) {
                GameState.simulationState = SimulationState.PAUSED
                updateSimulationResultUi()
            }
        }

        /**
         * Keeps driving, horn, and weather sounds synchronized with the current frame state.
         */
        fun updateSimulationSound(realDeltaSeconds: Double) {
            simulationSoundController.update(
                level = level,
                simulation = GameState.activeSimulation,
                simulationState = GameState.simulationState,
                elapsedSeconds = realDeltaSeconds
            )
        }

        /**
         * Runs all per-frame updates for validation, UI, simulation, camera, and sound.
         */
        fun updateSceneFrame(dt: Duration) {
            val realDeltaSeconds = dt.inWholeMicroseconds / 1_000_000.0
            level.validate()
            processCountdown(level, dt)
            processCompletionCountdown(dt)

            // Detects the transition into SIMULATION, from either the auto-countdown (processCountdown
            // above) or a manual start (handleLevelActionButtonClick, triggered by a button click
            // outside this per-frame function) - both just set GameState.simulationState, so checking
            // it here catches both paths alike. From this point on captures stop (updateCameraCapture
            // is gated on CONSTRUCTION) and level.streets is final for this run, so the committed
            // network becomes the single thing rendered: the live detection preview's last frame would
            // otherwise linger on screen and could show a different network than the one vehicles are
            // now driving on (see syncCommittedStreetNetworkView's doc).
            val currentSimulationState = GameState.simulationState
            if (currentSimulationState == SimulationState.SIMULATION &&
                lastObservedSimulationState != SimulationState.SIMULATION
            ) {
                syncCommittedStreetNetworkView()
                cleanupDetectedStreetSprites()
            }
            lastObservedSimulationState = currentSimulationState

            syncConstructionMarkers()
            updateSimulationControlViews()
            updateDetectionStatusBanner(
                detectionStatusBanner,
                detectionStatusMessage(
                    lastDetectionIssues,
                    GameState.simulationState,
                    startGateBlocked = level.networkState == NetworkState.COMPLETE &&
                        missingRoutesForCurrentNetwork(level).isNotEmpty()
                )
            )
            updateCameraCapture(dt)
            updateSimulationPlayback(GameState.activeSimulation, realDeltaSeconds)
            updateSimulationSound(realDeltaSeconds)
        }

        addUpdater { dt -> updateSceneFrame(dt) }
    }

    /**
     * Draws the construction-mode detection status banner.
     */
    private fun SContainer.drawDetectionStatusBanner(): DetectionStatusBannerViews {
        val overlay = container {
            xy(detectionStatusBannerX(), DETECTION_STATUS_BANNER_Y)
            visible = false
            graphics {
                fill(RGBA(255, 255, 255, 230)) {
                    rect(0.0, 0.0, DETECTION_STATUS_BANNER_W, DETECTION_STATUS_BANNER_H)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = 2.0)) {
                    rect(0.0, 0.0, DETECTION_STATUS_BANNER_W, DETECTION_STATUS_BANNER_H)
                }
            }
        }
        val message = overlay.text("", textSize = DETECTION_STATUS_TEXT_SIZE, color = Colors.BLACK, font = Assets.bitcountSingle)
        return DetectionStatusBannerViews(overlay, message)
    }

    /**
     * Updates the detection status banner visibility and centered text.
     */
    private fun updateDetectionStatusBanner(views: DetectionStatusBannerViews, message: String?) {
        views.overlay.visible = message != null
        if (message == null) return

        views.message.text = message
        views.message.centerOn(views.overlay)
    }

    /**
     * Returns whether this scene should periodically read the physical camera in construction mode.
     */
    protected open fun cameraCaptureEnabled(): Boolean = GameState.automaticStreetDetectionEnabled

    /**
     * Returns whether this scene should show and use the user-selectable speed control.
     * Tutorial scenes override this so the tutorial keeps a fixed, predictable playback speed.
     *
     * @return True if this scene should show and use the user-selectable speed control
     */
    open fun showSimulationSpeedSelector(): Boolean = true

    /**
     * Returns whether this scene should show the generic result statistics overlay.
     * Special scenes with their own completion flow can override this and keep only their
     * scene-specific end UI.
     *
     * @return True if this scene should show the generic result statistics overlay
     */
    open fun showResultStatisticsOverlay(): Boolean = true

    /**
     * Returns whether this scene treats simulation success or congestion as a finite result flow.
     *
     * Regular levels have a defined end state and therefore show result UI, pause playback and run
     * the follow-up countdown. Server-backed worlds are open-ended and override this to keep the
     * simulation scene active after local source counters or congestion flags change.
     *
     * @return true when terminal simulation result handling should be active.
     */
    open fun usesFiniteSimulationResultFlow(): Boolean = true

    /**
     * Creates the simulation instance used by this scene.
     *
     * Subclasses can inject scene-specific adapters, for example server handover ports for
     * multi-table worlds, while normal levels keep the plain local simulation.
     *
     * @param level active level.
     * @return simulation configured for this scene.
     */
    open fun createSimulation(level: Level): Simulation = Simulation(level)

    /**
     * Returns whether a complete network should automatically start the simulation countdown.
     */
    open fun automaticSimulationCountdownEnabled(level: Level): Boolean = true

    /**
     * Returns whether the challenge-mode indicator should be drawn.
     *
     * World scenes override this to hide the indicator because challenge mode is not part of the
     * multi-table world-editing workflow.
     *
     * @return true if the challenge-mode indicator should be visible.
     */
    open fun showChallengeIndicator(): Boolean = true

    /**
     * Returns whether the weather indicator should be drawn.
     *
     * World scenes override this to hide the indicator because weather is not yet supported in
     * multi-table world mode.
     *
     * @return true if the weather indicator should be visible.
     */
    open fun showWeatherIndicator(): Boolean = true

    /**
     * Renders the scene background into [container].
     *
     * The default implementation delegates to [LevelViewRenderer.renderBackground] which paints
     * the weather-dependent landscape texture. World scenes inherit this so dry multi-table worlds
     * use the same grass background as normal dry levels.
     *
     * @param container scene container that receives the background layer.
     */
    open fun renderBackground(container: SContainer) {
        val level = GameState.currentLevel ?: return
        LevelViewRenderer.renderBackground(container, level.weatherCondition)
    }

    /**
     * Draws additional buttons in the top-right corner, to the left of the gear button.
     *
     * The default implementation draws nothing. Subclasses can override this to inject
     * mode-specific indicator buttons; [scenes.WorldScene] uses it to display the globe icon
     * that signals the user is editing a server world in multi-table mode.
     *
     * @param container scene container that receives the extra buttons.
     */
    open fun drawExtraTopRightButtons(container: SContainer) { /* no-op by default */ }

    /**
     * Draws the result statistics overlay shown after success or congestion.
     *
     * @return overlay text handles that start hidden.
     */
    private fun SContainer.drawStatisticsOverlay(): StatisticsOverlayViews {
        val overlay = container {
            xy(statisticsOverlayX(), STATISTICS_OVERLAY_Y)
            visible = false
            drawStatisticsOverlayBackground()
        }
        val rowTexts = overlay.drawStatisticsRows()
        val title = overlay.drawStatisticsTitle()
        return StatisticsOverlayViews(overlay, title, rowTexts)
    }

    /**
     * Draws the statistics overlay frame, inner table border, and dotted row separators.
     */
    private fun Container.drawStatisticsOverlayBackground() {
        graphics {
            fill(Colors.WHITE) {
                rect(0.0, 0.0, STATISTICS_OVERLAY_W, STATISTICS_OVERLAY_H)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = 3.0)) {
                rect(0.0, 0.0, STATISTICS_OVERLAY_W, STATISTICS_OVERLAY_H)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = 2.0)) {
                rect(
                    STATISTICS_TABLE_PADDING_X,
                    STATISTICS_TABLE_Y,
                    STATISTICS_OVERLAY_W - 2 * STATISTICS_TABLE_PADDING_X,
                    STATISTICS_OVERLAY_H - STATISTICS_TABLE_Y - STATISTICS_TABLE_BOTTOM_PADDING
                )
            }
        }
        drawStatisticsDottedLines()
    }

    /**
     * Draws dotted separator lines behind the statistics row values.
     */
    private fun Container.drawStatisticsDottedLines() {
        graphics {
            fill(Colors["#8A8A8A"]) {
                statisticsDotPositions().forEach { dotPosition ->
                    rect(dotPosition.x, dotPosition.y, STATISTICS_DOT_W, STATISTICS_DOT_H)
                }
            }
        }
    }

    /**
     * Returns all dot positions used by the statistics separator lines.
     *
     * @return List of dot positions
     */
    private fun statisticsDotPositions(): List<Point> {
        val positions = mutableListOf<Point>()
        repeat(statisticsRowCount()) { rowIndex ->
            val yPos = STATISTICS_ROW_START_Y +
                rowIndex * STATISTICS_ROW_GAP +
                STATISTICS_DOTTED_LINE_OFFSET_Y
            var xPos = STATISTICS_TEXT_PADDING_X
            while (xPos < STATISTICS_OVERLAY_W - STATISTICS_TEXT_PADDING_X) {
                positions += Point(xPos, yPos)
                xPos += STATISTICS_DOT_STEP_X
            }
        }
        return positions
    }

    /**
     * Draws empty statistic row labels and values and returns their mutable text handles.
     *
     * @return List of StatisticsRowTexts objects
     */
    private fun Container.drawStatisticsRows(): List<StatisticsRowTexts> {
        return List(statisticsRowCount()) { index ->
            val yPos = STATISTICS_ROW_START_Y + index * STATISTICS_ROW_GAP
            val label = text("", textSize = STATISTICS_ROW_TEXT_SIZE, color = Colors.BLACK, font = Assets.bitcountSingle) {
                xy(STATISTICS_TEXT_PADDING_X, yPos)
            }
            val value = text("", textSize = STATISTICS_ROW_TEXT_SIZE, color = Colors.BLACK, font = Assets.bitcountSingle) {
                xy(STATISTICS_OVERLAY_W - STATISTICS_TEXT_PADDING_X, yPos)
            }
            StatisticsRowTexts(label, value)
        }
    }

    /**
     * Draws the statistics title and returns its mutable text handle.
     *
     * @return Mutable text handle
     */
    private fun Container.drawStatisticsTitle(): Text {
        return text("", textSize = STATISTICS_TITLE_TEXT_SIZE, color = Colors.BLACK, font = Assets.bitcountSingle).apply {
            y = STATISTICS_TITLE_Y
        }
    }

    /**
     * Updates the statistics overlay and matching result actions from the current simulation.
     *
     * @param simulation active simulation, or null before playback starts.
     * @param statisticsOverlay overlay text handles.
     * @param completionAction success action row.
     * @param retryAction failed-result action.
     * @param sceneOverlay optional scene-specific overlay.
     */
    private fun updateResultStatisticsUi(
        simulation: Simulation?,
        statisticsOverlay: StatisticsOverlayViews,
        completionAction: View?,
        retryAction: View?,
        sceneOverlay: View?
    ) {
        val success = simulation?.levelCompleted == true
        val failed = simulation?.congestionDetected == true
        val resultVisible = success || failed
        statisticsOverlay.overlay.visible = resultVisible
        completionAction?.visible = success
        retryAction?.visible = failed
        sceneOverlay?.visible = !resultVisible
        if (!resultVisible) return

        /** Start the countdown if not already started */
        if (!completionCountdownActive) {
            startCompletionCountdown()
        }

        statisticsOverlay.title.text = simulationResultTitle(success)
        statisticsOverlay.title.centerXOn(statisticsOverlay.overlay)
        val rows = statisticsRows(simulation.statistics())
        rows.forEachIndexed { index, (label, value) ->
            val row = statisticsOverlay.rows.getOrNull(index) ?: return@forEachIndexed
            row.label.text = label
            row.value.text = value
            row.value.x = STATISTICS_OVERLAY_W - STATISTICS_TEXT_PADDING_X - row.value.width
        }
    }

    /**
     * Resets the level after closing a congestion result.
     *
     * Clears the finished simulation run and the detected street network; the player's vehicle and
     * source edits are kept (issue #162), so the original configuration snapshot is intentionally
     * not restored here.
     */
    private fun resetAfterCongestion() {
        GameState.resetSimulationRunKeepingConfiguration()
        GameState.currentLevel?.clearStreetNetwork()
        GameState.simulationState = SimulationState.CONSTRUCTION
    }

    /**
     * Draws the failed-result retry action.
     *
     * @return retry button container.
     */
    private fun SContainer.drawRetryAction(): Container {
        return container {
            xy(TABLE_PX_W / 2.0 - RETRY_ACTION_BUTTON_W / 2.0, RETRY_ACTION_BUTTON_Y)

            /** Draw background */
            drawActionButtonBackground(RETRY_ACTION_BUTTON_W, RETRY_ACTION_BUTTON_H)

            /** Add green countdown fill layer with 70% opacity */
            val countdownFill = container {
                xy(0.0, 0.0)
                graphics {
                    fill(RGBA(0, 255, 0, 178)) {
                        rect(2.0, 2.0, 0.0, RETRY_ACTION_BUTTON_H - 2.0)
                    }
                }
            }

            /** Draw text on top of everything */
            text(RETRY_BUTTON_TEXT, textSize = 36.0, color = Colors.BLACK, font = Assets.bitcountSingle) {
                centerOn(this@container)
            }

            onClickWithSound {
                completionCountdownActive = false
                InteractionSoundController.stop(InteractionSoundEffect.TIMER_15S)
                resetAfterCongestion()
                launch { sceneContainer.changeTo<LevelScene>() }
            }

            /** Store reference for countdown updates */
            this@LevelScene.retryCountdownButton = this
            this@LevelScene.retryCountdownFill = countdownFill
            this@LevelScene.retryCountdownAction = {
                resetAfterCongestion()
                launch { sceneContainer.changeTo<LevelScene>() }
            }
        }
    }

    /**
     * Pauses the simulation before opening a configuration scene.
     */
    private fun pauseSimulationForConfiguration() {
        if (GameState.simulationState == SimulationState.SIMULATION) {
            GameState.simulationState = SimulationState.PAUSED
        }
    }

    /**
     * Draws the challenge-mode rules popup opened from the lightning indicator.
     *
     * @param currentModeActive callback that reports whether challenge mode is currently active.
     * @param activateChallenge callback that tries to activate challenge mode.
     * @param onModeChanged callback invoked immediately after challenge mode changes.
     * @return the created popup container.
     */
    private fun SContainer.drawChallengeModePopup(
        currentModeActive: () -> Boolean,
        activateChallenge: () -> Boolean,
        onModeChanged: (Boolean) -> Unit
    ): Container {
        return container {
            xy(challengePopupX(), challengePopupY())
            graphics {
                fill(Colors.WHITE) {
                    rect(0.0, 0.0, CHALLENGE_POPUP_W, CHALLENGE_POPUP_H)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = 3.0)) {
                    rect(0.0, 0.0, CHALLENGE_POPUP_W, CHALLENGE_POPUP_H)
                }
            }
            val titleText = drawCenteredPopupText("", 42.0, 42.0)
            drawCenteredPopupText(CHALLENGE_POPUP_RULES_TITLE, 34.0, 120.0)
            drawCenteredPopupText(challengeRulesText(GameState.currentLevel), 25.0, 172.0)
            val questionText = drawCenteredPopupText("", 30.0, 255.0)
            val statusText = drawCenteredPopupText("", 24.0, 300.0)
            var popupRenderedState: Boolean? = null
            var popupWasVisible = false
            /**
             * Updates all popup texts to match the currently active challenge-mode state.
             */
            fun updatePopupCopy(active: Boolean) {
                titleText.text = if (active) CHALLENGE_POPUP_TITLE else CHALLENGE_POPUP_INACTIVE_TITLE
                questionText.text = if (active) CHALLENGE_POPUP_QUESTION else CHALLENGE_POPUP_START_QUESTION
                statusText.text = ""
                titleText.centerXOn(this@container)
                questionText.centerXOn(this@container)
                statusText.centerXOn(this@container)
                popupRenderedState = active
            }
            addUpdater {
                val active = currentModeActive()
                if (visible && (!popupWasVisible || popupRenderedState != active)) {
                    updatePopupCopy(active)
                }
                popupWasVisible = visible
            }
            drawChallengePopupButton(
                CHALLENGE_POPUP_W / 2.0 - 210.0,
                CHALLENGE_POPUP_BUTTON_Y,
                CHALLENGE_POPUP_YES_TEXT
            ).onClickWithSound {
                if (currentModeActive()) {
                    ChallengeMode.deactivate()
                    onModeChanged(ChallengeMode.active)
                    visible = false
                } else if (activateChallenge()) {
                    onModeChanged(ChallengeMode.active)
                    visible = false
                } else {
                    statusText.text = CHALLENGE_POPUP_BLOCKED_TEXT
                    statusText.centerXOn(this@container)
                }
            }
            drawChallengePopupButton(
                CHALLENGE_POPUP_W / 2.0 + 30.0,
                CHALLENGE_POPUP_BUTTON_Y,
                CHALLENGE_POPUP_NO_TEXT
            ).onClickWithSound {
                visible = false
            }
            updatePopupCopy(currentModeActive())
        }
    }

    /**
     * Draws centered text inside a popup container.
     *
     * @param value text content to display.
     * @param textSize font size used for the rendered text.
     * @param yPos top y-position inside the popup.
     * @return the created text view.
     */
    private fun Container.drawCenteredPopupText(value: String, textSize: Double, yPos: Double) =
        text(value, textSize = textSize, color = Colors.BLACK, font = Assets.bitcountSingle).apply {
            centerXOn(this@drawCenteredPopupText)
            y = yPos
        }

    /**
     * Draws one action button inside the challenge-mode popup.
     *
     * @param xPos horizontal position of the button inside the popup.
     * @param yPos vertical position of the button inside the popup.
     * @param label visible label of the button.
     * @return the created button container.
     */
    private fun Container.drawChallengePopupButton(xPos: Double, yPos: Double, label: String): Container {
        return container {
            xy(xPos, yPos)
            graphics {
                fill(Colors.WHITE) {
                    rect(0.0, 0.0, CHALLENGE_POPUP_BUTTON_W, CHALLENGE_POPUP_BUTTON_H)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = 2.0)) {
                    rect(0.0, 0.0, CHALLENGE_POPUP_BUTTON_W, CHALLENGE_POPUP_BUTTON_H)
                }
            }
            text(label, textSize = 28.0, color = Colors.BLACK, font = Assets.bitcountSingle) {
                centerOn(this@container)
            }
        }
    }

    /**
     * Text shown when the active level is completed.
     *
     * @return the default completion text.
     */
    protected open fun completionText(): String = "Level geschafft"

    /**
     * Draws optional scene-specific UI that is visible before completion.
     *
     * @param level the active level for the scene-specific overlay.
     * @return the overlay view, or null if no scene-specific overlay is needed.
     */
    protected open fun SContainer.drawSceneOverlay(level: Level): View? = null

    /**
     * Draws an optional action that is shown when the level is completed.
     *
     * Normal predefined levels show a small button next to the play button if a following
     * predefined level slot exists. Saved levels and the final predefined level have no default
     * follow-up action.
     *
     * @param level the active level used to resolve the next completion action.
     * @return the container that holds the completion actions.
     */
    protected open suspend fun SContainer.drawCompletionAction(level: Level): View {
        val nextLevelSlot = nextCompletionLevelSlot(level)
        return container {
            xy(0.0, COMPLETION_ACTION_BUTTON_Y)

            // Use a three-button row when the next predefined level exists, otherwise center
            // the remaining two buttons on the playfield.
            val hasNext = nextLevelSlot != null
            val buttonCount = if (hasNext) 3 else 2
            val rowWidth = COMPLETION_ACTION_BUTTON_W * buttonCount + COMPLETION_ACTION_BUTTON_GAP * (buttonCount - 1)
            val startX = TABLE_PX_W / 2.0 - rowWidth / 2.0

            var index = 0
            if (hasNext) {
                /** The next-level button loads the following predefined level slot. */
                val nextBtn = drawCompletionActionButton(startX, NEXT_LEVEL_BUTTON_TEXT) {
                    openNextCompletionLevel(level)
                }
                nextBtn.onClickWithSound {
                    completionCountdownActive = false
                    InteractionSoundController.stop(InteractionSoundEffect.TIMER_15S)
                    nextLevelCountdownButton = null
                    nextLevelCountdownFill = null
                    nextLevelCountdownAction = null
                    openNextCompletionLevel(level)
                }
                index++
            }

            /** The save button saves the current level in the folder for user saved levels. */
            val saveX = startX + index * (COMPLETION_ACTION_BUTTON_W + COMPLETION_ACTION_BUTTON_GAP)
            val initialSaveCompletionSaved = MenuScene.levelMatchesRememberedSave(level)
            val initialSaveFillProgress = saveCompletionFillProgress(initialSaveCompletionSaved)
            saveCompletionActionSaved = initialSaveCompletionSaved
            drawCompletionActionButton(saveX, SAVE_LEVEL_BUTTON_TEXT, initialFillProgress = initialSaveFillProgress).onClickWithErrorSound {
                handleSaveCompletionClick(level)
            }
            index++

            /** The menu button switches directly to the level selection scene. */
            val menuX = startX + index * (COMPLETION_ACTION_BUTTON_W + COMPLETION_ACTION_BUTTON_GAP)
            val menuBtn = drawCompletionActionButton(menuX, LEVEL_MENU_BUTTON_TEXT) {
                launch {
                    LevelMenuScene.backDestination = LevelMenuBackDestination.LEVEL_SCENE
                    sceneContainer.changeTo<LevelMenuScene>()
                }
            }
            menuBtn.onClickWithSound {
                completionCountdownActive = false
                InteractionSoundController.stop(InteractionSoundEffect.TIMER_15S)
                menuCountdownButton = null
                menuCountdownFill = null
                menuCountdownAction = null
                launch {
                    LevelMenuScene.backDestination = LevelMenuBackDestination.LEVEL_SCENE
                    sceneContainer.changeTo<LevelMenuScene>()
                }
            }
        }
    }

    /**
     * Loads the next predefined level and switches back into the level scene.
     *
     * @param level the active level used to resolve the next predefined slot.
     */
    private fun openNextCompletionLevel(level: Level) {
        launch {
            val slot = nextCompletionLevelSlot(level) ?: return@launch
            val nextLevel = LevelSerializer.loadFromResources(slot.path)
            LevelMenuScene.assignLoadedLevel(nextLevel, predefinedLevelId = slot.levelId)
            Assets.preloadVehicleGraphics(nextLevel.vehicleTypes)
            sceneContainer.changeTo<LevelScene>()
        }
    }

    /** Starts completion saving when the current level is not already saved. */
    private fun handleSaveCompletionClick(level: Level): Boolean {
        setSaveCompletionButtonSaved(MenuScene.levelMatchesRememberedSave(level))
        if (saveCompletionActionSaved) return false

        launch { handleSaveCompletionAction() }
        return true
    }

    /**
     * Returns the predefined slot that should be loaded by the completion next-level action.
     *
     * @param level the active level used as fallback when no predefined slot marker exists.
     * @return the next predefined slot, or null if none is available.
     */
    protected open suspend fun nextCompletionLevelSlot(level: Level): LevelMenuScene.PredefinedLevelSlot? {
        val currentLevelId = GameState.currentPredefinedLevelId ?: GameState.currentLevel?.number?.takeIf { it in 0..LevelMenuScene.MAX_PREDEFINED_LEVELS }
            ?: return null
        val nextLevelId = currentLevelId + 1
        if (nextLevelId > LevelMenuScene.MAX_PREDEFINED_LEVELS) return null

        val nextLevelPath = LevelMenuScene.predefinedLevelPath(nextLevelId)
        val existingLevelIds = if (resourcesVfs[nextLevelPath].exists()) setOf(nextLevelId) else emptySet()
        return nextPredefinedLevelSlot(currentLevelId, existingLevelIds)
    }

    /**
     * Draws one text-only completion action button at [xPos] inside the completion row.
     *
     * @param xPos horizontal position inside the completion row.
     * @param label visible button label.
     * @param initialFillProgress initial green-fill progress from 0.0 to 1.0.
     * @param action the action to perform when the button is clicked or countdown completes.
     * @return the created button container.
     */
    private fun Container.drawCompletionActionButton(
        xPos: Double,
        label: String,
        initialFillProgress: Double = 0.0,
        action: (() -> Unit)? = null
    ): Container {
        return container {
            xy(xPos, 0.0)

            /** Draw background first */
            drawActionButtonBackground(COMPLETION_ACTION_BUTTON_W, COMPLETION_ACTION_BUTTON_H)

            /** Add green fill layer for countdown progress or saved-state feedback. */
            if (label == NEXT_LEVEL_BUTTON_TEXT || label == LEVEL_MENU_BUTTON_TEXT || label == SAVE_LEVEL_BUTTON_TEXT) {
                val actionFill = container {
                    xy(0.0, 0.0)
                    drawActionButtonFill(
                        completionActionFillWidth(COMPLETION_ACTION_BUTTON_W, initialFillProgress),
                        COMPLETION_ACTION_BUTTON_H
                    )
                }

                /** Store reference for countdown or saved-state updates. */
                if (label == NEXT_LEVEL_BUTTON_TEXT) {
                    this@LevelScene.nextLevelCountdownButton = this
                    this@LevelScene.nextLevelCountdownFill = actionFill
                    this@LevelScene.nextLevelCountdownAction = action
                } else if (label == LEVEL_MENU_BUTTON_TEXT) {
                    this@LevelScene.menuCountdownButton = this
                    this@LevelScene.menuCountdownFill = actionFill
                    this@LevelScene.menuCountdownAction = action
                } else {
                    this@LevelScene.saveCompletionFill = actionFill
                }
            }

            drawActionButtonBorder(COMPLETION_ACTION_BUTTON_W, COMPLETION_ACTION_BUTTON_H)

            /** Draw text on top of everything */
            text(label, textSize = 36.0, color = Colors.BLACK, font = Assets.bitcountSingle) {
                centerOn(this@container)
            }
        }
    }

    /**
     * Draws a bordered white action button background in the current container.
     *
     * @param width button width.
     * @param height button height.
     */
    private fun Container.drawActionButtonBackground(width: Double, height: Double) {
        graphics {
            fill(Colors.WHITE) {
                rect(0.0, 0.0, width, height)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = 2.0)) {
                rect(0.0, 0.0, width, height)
            }
        }
    }

    /**
     * Draws the green inner fill of an action button.
     *
     * @param fillWidth width of the green fill inside the border.
     * @param buttonHeight full button height.
     */
    private fun Container.drawActionButtonFill(fillWidth: Double, buttonHeight: Double) {
        graphics {
            fill(COMPLETION_ACTION_FILL_COLOR) {
                rect(
                    COMPLETION_ACTION_FILL_INSET,
                    COMPLETION_ACTION_FILL_INSET,
                    fillWidth,
                    buttonHeight - COMPLETION_ACTION_FILL_INSET
                )
            }
        }
    }

    /**
     * Draws the action button border above dynamic fill layers.
     *
     * @param width button width.
     * @param height button height.
     */
    private fun Container.drawActionButtonBorder(width: Double, height: Double) {
        graphics {
            stroke(Colors.BLACK, StrokeInfo(thickness = 2.0)) {
                rect(0.0, 0.0, width, height)
            }
        }
    }

    /**
     * Updates whether the completion save action matches the currently remembered save.
     */
    private fun setSaveCompletionButtonSaved(saved: Boolean) {
        saveCompletionActionSaved = saved
        saveCompletionFill?.removeChildren()
        saveCompletionFill?.drawActionButtonFill(
            completionActionFillWidth(COMPLETION_ACTION_BUTTON_W, if (saved) 1.0 else 0.0),
            COMPLETION_ACTION_BUTTON_H
        )
    }

    /**
     * Saves from the completion action and updates inline feedback without changing scenes.
     */
    private suspend fun handleSaveCompletionAction() {
        when (MenuScene.saveCurrentLevel()) {
            is LevelSaveResult.Saved,
            is LevelSaveResult.Unchanged -> {
                setSaveCompletionButtonSaved(true)
                InteractionSoundController.play(InteractionSoundEffect.SUCCESS)
            }
            LevelSaveResult.MissingLevel -> {
                InteractionSoundController.play(InteractionSoundEffect.ERROR)
                sceneContainer.recoverToLevelSelection()
            }
        }
    }

    /** Plays the level-completed success sound once before the automatic result countdown starts. */
    private fun playLevelSuccessSoundOnce(levelCompleted: Boolean) {
        if (!levelCompleted || levelSuccessSoundPlayed) return

        levelSuccessSoundPlayed = true
        completionCountdownStartDelaySeconds = SUCCESS_SOUND_COUNTDOWN_DELAY_SECONDS
        InteractionSoundController.play(InteractionSoundEffect.SUCCESS)
    }

    /**
     * Updates scene-specific overlay visibility after the completion state changes.
     *
     * @param levelCompleted true when the current level has been completed.
     * @param sceneOverlay the optional scene-specific overlay view.
     * @param completionAction the optional completion action view.
     * @return nothing; the views are updated in place.
     */
    protected open fun updateCompletionUi(levelCompleted: Boolean, sceneOverlay: View?, completionAction: View?) {
        completionAction?.visible = completionActionVisible(levelCompleted)
    }

    /**
     * Updates the generic completion message boxes.
     *
     * Special scenes can override this when their completion flow replaces the normal message
     * with scene-specific explanatory UI.
     *
     * @param levelCompleted true when the regular simulation goal is complete.
     * @param challengeResult visible challenge result, or null for normal completion.
     * @param levelOnlyEndStateOverlay the regular completion message box.
     * @param challengeEndStateOverlay the challenge result message box.
     */
    protected open fun updateEndStateUi(
        levelCompleted: Boolean,
        challengeResult: ChallengeResult?,
        levelOnlyEndStateOverlay: View,
        challengeEndStateOverlay: View
    ) {
        levelOnlyEndStateOverlay.visible = levelCompleted && challengeResult == null
        challengeEndStateOverlay.visible = challengeResult != null
    }

    /**
     * Applies the current main action button mode to the simulation state.
     *
     * @param level the active level used to create the simulation on first start.
     * @param resultState true when the level is already completed or congested.
     */
    private fun handleLevelActionButtonClick(level: Level, resultState: Boolean) {
        when (levelActionModeForResultState(level.networkState, GameState.simulationState, resultState)) {
            LevelActionButtonMode.DISABLED_START -> Unit
            LevelActionButtonMode.START -> {
                /** If a paused simulation already exists in the GameState, we continue to use it! */
                /** Only if it is null (during the first start of the level) do we recreate it. */
                if (GameState.activeSimulation == null) {
                    // Start gate for the first start only (resuming a paused simulation must always
                    // work - its vehicles already drive on routes resolved at its original start).
                    // See sourceConfigurationsWithoutRoutes for why COMPLETE alone is not enough.
                    if (missingRoutesForCurrentNetwork(level).isNotEmpty()) {
                        return
                    }
                    GameState.activeSimulation = createSimulation(level)
                }
                GameState.simulationState = SimulationState.SIMULATION
                /** Reset countdown when manually started */
                countdownActive = false
                countdownSoundPending = false
                countdownContainer.visible = false
                countdownPrerequisiteStableTime = 0.0
                InteractionSoundController.stop(InteractionSoundEffect.TIMER_3S)
            }
            LevelActionButtonMode.PAUSE -> {
                GameState.simulationState = SimulationState.PAUSED
            }
        }
    }

    /**
     * Selects the click feedback for the main level action button.
     */
    private fun interactionSoundForLevelAction(mode: LevelActionButtonMode): InteractionSoundEffect {
        return when (mode) {
            LevelActionButtonMode.DISABLED_START -> InteractionSoundEffect.ERROR
            LevelActionButtonMode.START,
            LevelActionButtonMode.PAUSE -> InteractionSoundEffect.CLICK
        }
    }

    /**
     * Starts the automatic post-result transition countdown once.
     */
    private fun startCompletionCountdown() {
        completionCountdownActive = true
        completionCountdownValue = COMPLETION_COUNTDOWN_DURATION.toDouble()
        InteractionSoundController.play(InteractionSoundEffect.TIMER_15S)
    }

    /**
     * Handles the countdown logic for automatically starting the simulation.
     *
     * @param level The current level.
     * @param dt The delta time since the last frame.
     */
    private fun processCountdown(level: Level, dt: Duration) {
        val deltaSeconds = dt.inWholeMicroseconds / 1_000_000.0
        // The countdown may only run when the network is COMPLETE *and* the router can actually
        // serve every (source, configuration) - otherwise the countdown would visibly loop forever:
        // count down, get blocked by the start gate, restart, count down again. The route check is
        // cached per committed network, so this per-frame call stays cheap.
        val prerequisitesMet = level.networkState == NetworkState.COMPLETE &&
            missingRoutesForCurrentNetwork(level).isEmpty()
        val simulationNotRunning = GameState.simulationState != SimulationState.SIMULATION
        val simulationPaused = GameState.simulationState == SimulationState.PAUSED
        val levelCompleted = usesFiniteSimulationResultFlow() && GameState.activeSimulation?.levelCompleted == true
        val networkCongested = usesFiniteSimulationResultFlow() && GameState.activeSimulation?.congestionDetected == true
        val challengePopupVisible = challengePopup.visible

        /** Only start countdown if:
         * 1. Network is complete, 2. Simulation is not running, 3. Simulation is not paused (user didn't manually pause),
         * 4. Level is not completed, 5. Network is not congested, 6. Challenge popup is not open
         */
        val shouldStartCountdown = automaticSimulationCountdownEnabled(level) &&
                prerequisitesMet && simulationNotRunning &&
                !simulationPaused && !levelCompleted && !networkCongested && !challengePopupVisible

        /** Cancel countdown if conditions are no longer met */
        if (this@LevelScene.countdownActive && !shouldStartCountdown) {
            this@LevelScene.countdownActive = false
            countdownSoundPending = false
            countdownContainer.visible = false
            countdownPrerequisiteStableTime = 0.0
            InteractionSoundController.stop(InteractionSoundEffect.TIMER_3S)
            return
        }

        /** Only proceed if countdown should be active */
        if (!shouldStartCountdown) {
            countdownPrerequisiteStableTime = 0.0
            return
        }

        /** Initialize countdown if not already active */
        if (!this@LevelScene.countdownActive) {
            countdownPrerequisiteStableTime += deltaSeconds
            if (countdownPrerequisiteStableTime < COUNTDOWN_RECOGNITION_STABILITY_SECONDS) {
                return
            }
            this@LevelScene.countdownValue = COUNTDOWN_DURATION_SIMULATION_START
            this@LevelScene.countdownActive = true
            countdownSoundPending = true
            countdownContainer.visible = true
            this@LevelScene.lastCountdownUpdateTime = 0.0
            this@LevelScene.countdownText.text = this@LevelScene.countdownValue.toString()
            return
        }

        if (countdownSoundPending) {
            countdownSoundPending = false
            this@LevelScene.lastCountdownUpdateTime = 0.0
            InteractionSoundController.play(InteractionSoundEffect.TIMER_3S)
            return
        }

        /** Update countdown timer */
        this@LevelScene.lastCountdownUpdateTime += deltaSeconds
        if (this@LevelScene.lastCountdownUpdateTime >= 1.0) {
            this@LevelScene.countdownValue--
            this@LevelScene.lastCountdownUpdateTime = 0.0
            this@LevelScene.countdownText.text = this@LevelScene.countdownValue.toString()

            /** If countdown has finished, start simulation */
            if (this@LevelScene.countdownValue <= 0) {
                this@LevelScene.countdownActive = false
                countdownSoundPending = false
                countdownContainer.visible = false
                countdownPrerequisiteStableTime = 0.0
                InteractionSoundController.stop(InteractionSoundEffect.TIMER_3S)

                // Final start-gate re-check: the countdown-start prerequisite above already requires
                // routable networks, but a commit during the countdown's last second can still have
                // replaced the network - never launch a simulation where some source cannot spawn.
                if (missingRoutesForCurrentNetwork(level).isNotEmpty()) {
                    return
                }

                if (GameState.activeSimulation == null) {
                    GameState.activeSimulation = createSimulation(level)
                }
                GameState.simulationState = SimulationState.SIMULATION
            }
        } else {
            /** Update countdown display even when not decrementing */
            this@LevelScene.countdownText.text = this@LevelScene.countdownValue.toString()
        }
    }

    /**
     * Handles the countdown logic for automatically transitioning to next level or retry.
     *
     * @param dt The delta time since the last frame.
     */
    private fun processCompletionCountdown(dt: Duration) {
        if (!usesFiniteSimulationResultFlow()) {
            if (completionCountdownActive) {
                completionCountdownActive = false
                InteractionSoundController.stop(InteractionSoundEffect.TIMER_15S)
            }
            return
        }
        val levelCompleted = GameState.activeSimulation?.levelCompleted == true
        val congestionDetected = GameState.activeSimulation?.congestionDetected == true
        val resultState = levelCompleted || congestionDetected
        /** Determine which button to use based on result type */
        val (countdownButton, countdownFill, countdownAction) = if (levelCompleted) {
            /** Use next level button if available, otherwise use menu button */
            if (nextLevelCountdownButton != null) {
                Triple(nextLevelCountdownButton, nextLevelCountdownFill, nextLevelCountdownAction)
            } else {
                Triple(menuCountdownButton, menuCountdownFill, menuCountdownAction)
            }
        } else {
            Triple(retryCountdownButton, retryCountdownFill, retryCountdownAction)
        }
        /** Start countdown when result state is reached and countdown is not already active */
        if (resultState && !completionCountdownActive && countdownButton != null) {
            if (completionCountdownStartDelaySeconds > 0.0) {
                val deltaSeconds = dt.inWholeMicroseconds / 1_000_000.0
                completionCountdownStartDelaySeconds = (completionCountdownStartDelaySeconds - deltaSeconds).coerceAtLeast(0.0)
                return
            }
            startCompletionCountdown()
        }
        /** Cancel countdown if result state is no longer active */
        if (!resultState && completionCountdownActive) {
            completionCountdownActive = false
            InteractionSoundController.stop(InteractionSoundEffect.TIMER_15S)
            return
        }
        /** Only proceed if countdown should be active */
        if (!completionCountdownActive || countdownButton == null || countdownFill == null) {
            return
        }
        val deltaSeconds = dt.inWholeMicroseconds / 1_000_000.0
        completionCountdownValue -= deltaSeconds
        /** Calculate fill width based on elapsed time (fill from left to right) */
        val elapsedRatio = 1.0 - (completionCountdownValue / COMPLETION_COUNTDOWN_DURATION.toDouble())
        val buttonWidth = if (countdownButton.width == COMPLETION_ACTION_BUTTON_W) {
            COMPLETION_ACTION_BUTTON_W
        } else {
            RETRY_ACTION_BUTTON_W
        }
        val fillWidth = completionActionFillWidth(buttonWidth, elapsedRatio)
        /** Update the fill visualization */
        countdownFill.removeChildren()
        countdownFill.drawActionButtonFill(fillWidth, COMPLETION_ACTION_BUTTON_H)
        if (completionCountdownValue <= 0) {
            completionCountdownActive = false
            InteractionSoundController.stop(InteractionSoundEffect.TIMER_15S)
            countdownAction?.invoke()
        }
    }

    /**
     * Returns the rendered rotation for a vehicle based on its current path segment.
     *
     * @param vehicle the vehicle whose current street segment is rendered.
     * @return the segment angle, or zero when no usable segment is available.
     */
    private fun currentSegmentAngle(vehicle: Vehicle): Angle {
        val path = vehicle.currentStreet?.path ?: return Angle.ZERO
        if (path.size < 2) return Angle.ZERO
        val idx = vehicle.currentSegmentIndex.coerceAtMost(path.size - 2).coerceAtLeast(0)
        val p1 = path[idx]
        val p2 = path[idx + 1]
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return Angle.fromRadians(atan2(dy, dx))
    }

    /**
     * Performs the actual camera capture operation.
     *
     * @return The captured image as a Mat, or null if capture failed
     */
    private suspend fun performCameraCapture(): Mat? {
        return try {
            /** Retrieve a Mat using the non-blocking CameraManager service */
            CameraManager.takePhoto()
        } catch (e: Exception) {
            println("Error capturing camera image: ${e.message}")
            null
        }
    }

    /**
     * Cleans up the active scene by shutting down the camera manager and disposing debug windows.
     */
    override suspend fun sceneDestroy() {
        InteractionSoundController.stopTimers()
        CameraManager.shutdown()
        super.sceneDestroy()
    }
}
