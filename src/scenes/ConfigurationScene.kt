package scenes

import domain.GameState
import domain.ChallengeMode
import domain.GameState.tutorialActivated
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.VehicleType
import ui.addVehiclePreview
import ui.vehicleBitmapForGraphicRef
import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import audio.onClickWithSound
import korlibs.korge.scene.Scene
import korlibs.korge.view.*
import korlibs.korge.view.align.centerXOn
import korlibs.math.geom.vector.StrokeInfo
import kotlinx.coroutines.launch
import ui.Assets
import ui.Assets.bitcountSingle
import ui.ControlButtons
import ui.Scrollbar
import ui.attachScrollbarInput
import worldeditor.WorldEditorConfigurationContext
import scenes.LevelScene.Companion.TABLE_PX_W
import scenes.LevelScene.Companion.TABLE_PX_H
import scenes.LevelScene.Companion.MENU_BUTTON_PX

/**
 * Scene that displays vehicle types and source node configurations
 * available in the current level, and allows navigation to their detail views.
 *
 * Layout
 * ------
 * +-----------------------------------------------+
 * |                           Konfiguration [ X ] |
 * |                                               |
 * |  Fahrzeugtypen                                |
 * |  [ Auto ]  [ LKW ]  [ Motorrad ] ...          |
 * |                                               |
 * |  Quellkonfigurationen                         |
 * |  [Quelle1▸Red/Car] [Quelle1▸Yel/Truck] ...    |
 * +-----------------------------------------------+
 *
 * The close button (top-right ×) returns to the [MenuScene].
 *
 * Clicking a vehicle-type card or a source-config card opens the
 * respective detail/edit scene.
 *
 * Note: Only vehicle types that exist in the current level are displayed.
 */
class ConfigurationScene : Scene() {

    /**
     * One UI card per [SourceNodeConfiguration], labelled with its parent source
     * (e.g. "Quelle 1").
     *
     * @property label The label to display on the card (e.g. "Quelle 1").
     * @property source The [SourceNode] associated with this configuration.
     * @property configuration The [SourceNodeConfiguration] associated with this card.
     */
    private data class SourceConfigCardUI(
        val label: String,
        val source: SourceNode,
        val configuration: SourceNodeConfiguration
    )

    /** -------------------------------------------------------------------------
     * Layout constants
     * ------------------------------------------------------------------------- */

    companion object {
        /** Scene target used by back and close when no world-editor configuration context is active. */
        var backDestination: ConfigurationBackDestination = ConfigurationBackDestination.GAMEPLAY_SCENE

        /** Color of the title text. */
        private val TITLE_COLOR = Colors.BLACK
        /** Color of the section headers. */
        private val SECTION_COLOR = Colors.BLACK

        /** Card dimensions */
        /** Size of each card (width and height). */
        const val CARD_SIZE = 220.0
        /** Horizontal spacing between cards in a row. */
        const val CARD_GAP = 50.0
        /** Internal padding within each card (space between icon/label and border). */
        const val CARD_PADDING = 112.0
        /** Width of the vehicle icon displayed in the card. */
        const val ICON_SIZE = 150.0
        /** Size of the vehicle-type and source-config labels. */
        const val LABEL_TEXT_SIZE = 30.0

        /** Size of the section header text. */
        const val SECTION_TEXT_SIZE = 50.0
        /** Size of the title text. */
        const val TITLE_TEXT_SIZE = 78.0

        /** Vertical layout offsets */
        /** Vertical distance from the top of the screen to the title. */
        const val MARGIN_TOP = 56.0
        /** Horizontal distance from the left and right edges of the screen to the content. */
        const val MARGIN_X = 180.0
        /** Vertical distance between the two main sections (Vehicle Types and Source Configurations). */
        const val SECTION_GAP = 50.0
        /** Space below section header to first card row */
        const val CARDS_TOP_OFFSET = 100.0

        /** Mouse wheel scroll multiplier for the source-card list. */
        const val SCROLL_SPEED = 20.0

        /** Y position of the "Quellkonfigurationen" section heading. */
        const val SOURCE_SECTION_HEADER_Y: Double =
            MARGIN_TOP + TITLE_TEXT_SIZE + SECTION_GAP + CARDS_TOP_OFFSET + CARD_SIZE + SECTION_GAP * 1.5

        /** Y position where the first row of source cards is drawn (before scrolling). */
        const val SOURCE_CARDS_START_Y: Double = SOURCE_SECTION_HEADER_Y + CARDS_TOP_OFFSET

        /** Height of the fixed header band above the scrollable source cards. */
        const val SOURCE_SCROLL_HEADER_BAND_H: Double = SOURCE_CARDS_START_Y

        /** Height of the content area the source cards scroll within. */
        const val SOURCE_VIEWPORT_HEIGHT: Double = TABLE_PX_H - SOURCE_CARDS_START_Y

        /** Height of the source-card scrollbar, matching the visible card area. */
        const val SOURCE_SCROLLBAR_HEIGHT: Double = SOURCE_VIEWPORT_HEIGHT - 30.0

        /** Left position of the source-card scrollbar, centered in the right page margin. */
        const val SOURCE_SCROLLBAR_X: Double = TABLE_PX_W - MARGIN_X + (MARGIN_X - Scrollbar.WIDTH) / 2.0

        /**
         * Total height of all source-config card rows for [cardCount] cards at [maxPerRow] per row.
         */
        fun sourceCardsContentHeight(cardCount: Int, maxPerRow: Int): Double {
            if (cardCount <= 0) return 0.0
            val rowCount = (cardCount + maxPerRow - 1) / maxPerRow
            return rowCount * CARD_SIZE + (rowCount - 1) * CARD_GAP
        }

        /** Lowest [ scrollY ] so the last source row stays reachable (negative or zero). */
        fun minSourceScrollY(cardCount: Int, maxPerRow: Int): Double {
            val overflow = SOURCE_CARDS_START_Y + sourceCardsContentHeight(cardCount, maxPerRow) - TABLE_PX_H
            return if (overflow > 0.0) -overflow else 0.0
        }

        /**
         * Resolves the editable vehicle-type cards to the actual [VehicleType] instances referenced
         * by [levelVehicleTypes].
         *
         * Binding each card to the level's own instance is what makes edits persist:
         * [VehicleConfigurationScene] mutates the returned instance in place, and every source
         * configuration that uses a type shares the same instance (see LevelSerializer
         * deserialization).
         *
         * @param levelVehicleTypes the distinct vehicle types referenced anywhere in the current level.
         * @return the ordered editable vehicle types that actually exist in the level.
         */
        fun resolveDisplayVehicleTypes(levelVehicleTypes: List<VehicleType>): List<VehicleType> {
            val autoType = levelVehicleTypes.find { isCarName(it.name) }
            val lkwType = levelVehicleTypes.find { isTruckName(it.name) }
            val motorradType = levelVehicleTypes.find { isMotorcycleName(it.name) }
            
            // Only return vehicle types that actually exist in the level
            return listOfNotNull(autoType, lkwType, motorradType)
        }

        /** Whether [name] denotes the car vehicle type. */
        private fun isCarName(name: String): Boolean =
            name.equals("Auto", ignoreCase = true) || name.equals("Car", ignoreCase = true)

        /**
         * Whether [name] denotes the truck vehicle type.
         *
         * The bundled levels store the truck as "Lastwagen", so that name must resolve to the
         * level's own truck instance; otherwise its parameters could only be edited in levels that
         * happen to use the names "LKW"/"Truck" (issue #162).
         */
        private fun isTruckName(name: String): Boolean =
            name.equals("LKW", ignoreCase = true) ||
                name.equals("Truck", ignoreCase = true) ||
                name.equals("Lastwagen", ignoreCase = true)

        /** Whether [name] denotes the motorcycle vehicle type. */
        private fun isMotorcycleName(name: String): Boolean =
            name.equals("Motorrad", ignoreCase = true) || name.equals("Motorcycle", ignoreCase = true)
    }

    /** -------------------------------------------------------------------------
     * Scene entry point
     * ------------------------------------------------------------------------- */

    override suspend fun SContainer.sceneMain() {
        drawWhiteSceneBackground()
        if (ChallengeMode.active && !WorldEditorConfigurationContext.isActive) {
            text("Konfiguration im Challenge-Modus gesperrt", textSize = TITLE_TEXT_SIZE, color = TITLE_COLOR, font = bitcountSingle) {
                centerXOn(this@sceneMain)
                y = MARGIN_TOP
            }
            ControlButtons.drawCloseButton(this).onClickWithSound {
                launch {
                    closeChallengeModeConfiguration()
                }
            }
            return
        }

        /** In world-editor context the menu edits the editor's working level, not the game state. */
        val level = WorldEditorConfigurationContext.levelOrNull()
            ?: GameState.levelForSceneOrRecover(this@ConfigurationScene::class.simpleName ?: "ConfigurationScene")
            ?: run {
                recoverToLevelSelection()
                return
            }

        /**
         * Types added in the world editor are not covered by the level-loading preload, so their
         * graphics must be cached here or their cards would fall back to the generic car icon.
         */
        Assets.preloadVehicleGraphics(level.vehicleTypes)

        /** Editable vehicle-type cards bound to the level's own instances so edits persist. */
        val displayVehicleTypes = resolveDisplayVehicleTypes(level.vehicleTypes)

        /** One card per configuration, grouped under sequential source labels. */
        val configCards: List<SourceConfigCardUI> = level.nodes
            .filterIsInstance<SourceNode>().flatMapIndexed { idx, sourceNode ->
                val sourceLabel = "Quelle ${idx + 1}"
                sourceNode.configurations.map { cfg ->
                    SourceConfigCardUI(sourceLabel, sourceNode, cfg)
                }
            }

        val maxPerRow = ((TABLE_PX_W - 2 * MARGIN_X) / (CARD_SIZE + CARD_GAP)).toInt().coerceAtLeast(1)

        val scrollContainer = container {
            y = SOURCE_CARDS_START_Y
            renderSourceConfigCards(this, configCards, maxPerRow)
        }

        /** Mask scrolled source cards behind the fixed header area. */
        graphics {
            fill(Colors.WHITE) {
                rect(0.0, 0.0, TABLE_PX_W, SOURCE_SCROLL_HEADER_BAND_H)
            }
        }

        // The scrollbar owns the scroll offset; the thumb is draggable and reports back here.
        val scrollbar = Scrollbar(
            parent = this,
            x = SOURCE_SCROLLBAR_X,
            y = SOURCE_CARDS_START_Y,
            barHeight = SOURCE_SCROLLBAR_HEIGHT,
            viewportHeight = SOURCE_VIEWPORT_HEIGHT,
            contentHeight = sourceCardsContentHeight(configCards.size, maxPerRow),
        ) { scrollY ->
            scrollContainer.y = SOURCE_CARDS_START_Y + scrollY
        }

        attachScrollbarInput(scrollbar, SCROLL_SPEED)

        text("Konfiguration", textSize = TITLE_TEXT_SIZE, color = TITLE_COLOR, font = bitcountSingle) {
            centerXOn(this@sceneMain)
            y = MARGIN_TOP
        }

        ControlButtons.drawBackButton(this).onClickWithSound {
            leaveConfiguration { navigateToBackDestination() }
        }

        image(Assets.close) {
            size(MENU_BUTTON_PX, MENU_BUTTON_PX)
            anchor(0.5, 1.0)
            position(TABLE_PX_W - MENU_BUTTON_PX * 1 + 10.0, MENU_BUTTON_PX + 20.0)
            onClickWithSound {
                leaveConfiguration { navigateToBackDestination() }
            }
        }

        val vehicleTypesHeaderY = MARGIN_TOP + TITLE_TEXT_SIZE + SECTION_GAP
        text("Fahrzeugtypen", textSize = SECTION_TEXT_SIZE, color = SECTION_COLOR, font = bitcountSingle) {
            centerXOn(this@sceneMain)
            y = vehicleTypesHeaderY
        }

        val vtRowContainer = container { y = vehicleTypesHeaderY + CARDS_TOP_OFFSET }
        renderVehicleTypeCards(vtRowContainer, displayVehicleTypes)

        text("Quellkonfigurationen", textSize = SECTION_TEXT_SIZE, color = SECTION_COLOR, font = bitcountSingle) {
            centerXOn(this@sceneMain)
            y = SOURCE_SECTION_HEADER_Y
        }
    }

    /** Closes the challenge-mode message to the tutorial or level scene. */
    private suspend fun closeChallengeModeConfiguration() {
        navigateToBackDestination()
    }

    /**
     * Leaves the configuration menu: back to the world editor when it opened this menu,
     * otherwise via [fallback].
     */
    private fun leaveConfiguration(fallback: suspend () -> Unit) {
        launch {
            val editorSession = WorldEditorConfigurationContext.activeSession
            if (editorSession != null) {
                sceneContainer.changeTo { WorldEditorScene(editorSession) }
            } else {
                fallback()
            }
        }
    }

    /** Navigates to the scene that opened the configuration menu. */
    private suspend fun navigateToBackDestination() {
        when (backDestination) {
            ConfigurationBackDestination.MENU_SCENE -> sceneContainer.changeTo<MenuScene>()
            ConfigurationBackDestination.MULTI_TABLE_MENU_SCENE -> sceneContainer.changeTo<MultiTableMenuScene>()
            ConfigurationBackDestination.GAMEPLAY_SCENE ->
                changeToConfigurationGameplayTarget(configurationGameplayTarget(tutorialActivated))
        }
    }

    /** -------------------------------------------------------------------------
     * Vehicle type cards
     * ------------------------------------------------------------------------- */

    /**
     * Renders one card per [VehicleType] in a centered horizontal row.
     * Clicking a card will open the vehicle-type detail scene.
     */
    private fun renderVehicleTypeCards(
        row: Container,
        vehicleTypes: List<VehicleType>
    ) {
        if (vehicleTypes.isEmpty()) {
            row.text(
                "Keine Fahrzeugtypen vorhanden",
                textSize = LABEL_TEXT_SIZE,
                color = RGBA(0x88, 0x88, 0x88, 0xFF),
                font = bitcountSingle
            ) { centerXOn(row) }
            return
        }

        val totalW = vehicleTypes.size * CARD_SIZE + (vehicleTypes.size - 1) * CARD_GAP
        val startX = (TABLE_PX_W - totalW) / 2.0

        vehicleTypes.forEachIndexed { idx, vt ->
            row.container {
                position(startX + idx * (CARD_SIZE + CARD_GAP), 0.0)
                drawVehicleTypeCard(vt)
            }
        }
    }

    /**
     * Draws a single vehicle-type card: bordered box, vehicle icon (or placeholder),
     * and the type name as label.
     */
    private fun Container.drawVehicleTypeCard(vt: VehicleType) {
        graphics {
            fill(Colors.WHITE) {
                rect(0.0, 0.0, CARD_SIZE, CARD_SIZE)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = 1.5)) {
                rect(0.0, 0.0, CARD_SIZE, CARD_SIZE)
            }
        }

        val bitmap = when (vt.name.lowercase()) {
            "auto", "car" -> Assets.car
            "lkw", "truck", "lastwagen" -> Assets.truck
            "motorrad", "motorcycle" -> Assets.motorcycle
            else -> Assets.car
        }

        image(bitmap) {
            val scaleW = ICON_SIZE / bitmap.width.toDouble()
            val scaleH = (ICON_SIZE - 45.0) / bitmap.height.toDouble()
            scale = kotlin.math.min(scaleW, scaleH)
            anchor(0.5, 0.5)
            position(CARD_SIZE / 2, CARD_SIZE / 2 - LABEL_TEXT_SIZE / 2)
            rotation = korlibs.math.geom.Angle.fromDegrees(0.0)
        }

        text(vt.name, textSize = LABEL_TEXT_SIZE, color = Colors.BLACK, font = bitcountSingle) {
            centerXOn(this@drawVehicleTypeCard)
            y = CARD_SIZE - LABEL_TEXT_SIZE - CARD_PADDING + 95.0
        }

        onClickWithSound {
            launch {
                sceneContainer.pushTo<VehicleConfigurationScene>(vt)
                println("Opened detail for vehicle type: ${vt.name}")
            }
        }
    }

    /** -------------------------------------------------------------------------
     * Source-configuration cards
     * ------------------------------------------------------------------------- */

    /**
     * Renders one card per source configuration entry.
     */
    private fun renderSourceConfigCards(
        row: Container,
        cards: List<SourceConfigCardUI>,
        maxPerRow: Int
    ) {
        if (cards.isEmpty()) {
            row.text(
                "Keine Quellen vorhanden",
                textSize = LABEL_TEXT_SIZE,
                color = RGBA(0x88, 0x88, 0x88, 0xFF),
                font = bitcountSingle
            ) { centerXOn(row) }
            return
        }

        val rows = cards.chunked(maxPerRow)

        rows.forEachIndexed { rowIdx, rowCards ->
            val totalW = rowCards.size * CARD_SIZE + (rowCards.size - 1) * CARD_GAP
            val startX = (TABLE_PX_W - totalW) / 2.0
            val rowY = rowIdx * (CARD_SIZE + CARD_GAP)

            rowCards.forEachIndexed { colIdx, cardUI ->
                row.container {
                    position(startX + colIdx * (CARD_SIZE + CARD_GAP), rowY)
                    drawSourceConfigCard(cardUI)
                }
            }
        }
    }

    /**
     * Draws a single-configuration card: one solid-colour vehicle icon for that config.
     */
    private fun Container.drawSourceConfigCard(cardUI: SourceConfigCardUI) {
        val cfg = cardUI.configuration
        graphics {
            fill(Colors.WHITE) {
                rect(0.0, 0.0, CARD_SIZE, CARD_SIZE)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = 1.5)) {
                rect(0.0, 0.0, CARD_SIZE, CARD_SIZE)
            }
        }

        val bitmap = vehicleBitmapForGraphicRef(cfg.vehicleType.graphicRef)
        val iconH = ICON_SIZE - 45.0
        container {
            position(CARD_SIZE / 2, CARD_SIZE / 2 - LABEL_TEXT_SIZE / 2)
            addVehiclePreview(bitmap, cfg.color, ICON_SIZE, iconH)
        }

        text(cardUI.label, textSize = LABEL_TEXT_SIZE, color = Colors.BLACK, font = bitcountSingle) {
            centerXOn(this@drawSourceConfigCard)
            y = CARD_SIZE - LABEL_TEXT_SIZE - CARD_PADDING + 95.0
        }

        onClickWithSound {
            launch {
                sceneContainer.pushTo<SourceConfigDetailScene>(cardUI.source, cardUI.label, cardUI.configuration)
                println("Opened detail for ${cardUI.label}: ${cfg.color.name} ${cfg.vehicleType.name}")
            }
        }
    }
}
