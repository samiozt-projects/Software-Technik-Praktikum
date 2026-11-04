package scenes

import audio.InteractionSoundController
import audio.InteractionSoundEffect
import korlibs.image.color.Colors
import audio.onClickWithoutSound
import audio.onClickWithSound
import korlibs.korge.input.onMouseDrag
import korlibs.korge.scene.Scene
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.Text
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.align.centerXOn
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.position
import korlibs.korge.view.text
import korlibs.math.geom.vector.StrokeInfo
import domain.ConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import network.ServerConnection
import network.WorldEditorNodeDTO
import network.WorldEditorNodeKind
import ui.Assets
import ui.ControlButtons
import ui.SceneUIUtils
import worldeditor.WorldEditorConfigurationContext
import worldeditor.WorldEditorRepository
import worldeditor.WorldEditorServerSourceSync
import worldeditor.WorldEditorServerTransitionSync
import worldeditor.WorldEditorSession
import kotlin.time.Duration.Companion.milliseconds

/** Interactive editor for placing and configuring world sources and sinks. */
internal class WorldEditorScene(
    private val session: WorldEditorSession,
    private val backDestination: WorldEditorBackDestination = WorldEditorBackNavigation.currentDestination,
    private val backWorld: WorldSelectionScene.WorldEntry? = WorldEditorBackNavigation.currentWorld,
    private val backExistingWorlds: List<WorldSelectionScene.WorldEntry> = WorldEditorBackNavigation.currentExistingWorlds
) : Scene() {
    companion object {
        /** Title shown at the top of the editor scene. */
        const val TITLE_TEXT = WorldEditorLayout.TITLE_TEXT
        /** Top y-position of the editor title. */
        const val TITLE_Y = WorldEditorLayout.TITLE_Y
        /** Top y-position of the editor header line. */
        const val HEADER_Y = WorldEditorLayout.HEADER_Y
        /** Width and height of one editor node marker. */
        const val NODE_SIZE = WorldEditorLayout.NODE_SIZE
        /** Width of the bottom editor action buttons. */
        const val BUTTON_W = WorldEditorLayout.BUTTON_W
        /** Height of the bottom editor action buttons. */
        const val BUTTON_H = WorldEditorLayout.BUTTON_H
        /** Top y-position of the bottom editor action row. */
        const val BOTTOM_Y = WorldEditorLayout.BOTTOM_Y
        /** Left x-position of the cancel button. */
        const val CANCEL_X = WorldEditorLayout.CANCEL_X
        /** Left x-position of the save button. */
        const val SAVE_X = WorldEditorLayout.SAVE_X
        /** Maximum number of characters shown in the inline error text. */
        const val ERROR_MAX_CHARS = WorldEditorLayout.ERROR_MAX_CHARS
        /** Left x-position of the source-configuration button. */
        const val CONFIG_BUTTON_X = WorldEditorLayout.CONFIG_BUTTON_X
        /** Top y-position of the source-configuration button. */
        const val CONFIG_BUTTON_Y = WorldEditorLayout.CONFIG_BUTTON_Y
        /** Width of the source-configuration button. */
        const val CONFIG_BUTTON_W = WorldEditorLayout.CONFIG_BUTTON_W
        /** Height of the source-configuration button. */
        const val CONFIG_BUTTON_H = WorldEditorLayout.CONFIG_BUTTON_H

        /** Short delay so the success sound can start before the scene changes. */
        const val SUCCESS_SOUND_SCENE_CHANGE_DELAY_MILLIS = 200L
    }

    private lateinit var fieldLayer: Container
    private lateinit var propertiesLayer: Container
    private lateinit var errorText: Text
    private lateinit var errorBackground: Container
    private val streetRenderer = WorldEditorStreetRenderer(
        WorldEditorSceneGeometry::levelToScreenX,
        WorldEditorSceneGeometry::levelToScreenY
    )
    private val propertiesPanel = WorldEditorPropertiesPanel(session, ::redrawDynamicContent)
    private val palette = WorldEditorPalette(::addNodeAtFieldCenter, ::dropPaletteNode)

    /** Editor IDs of sources whose last server transfer was rejected; drawn with a red border. */
    private var rejectedSyncNodeIds: Set<String> = emptySet()

    /** Builds the editor scene and draws static plus dynamic editor controls. */
    override suspend fun SContainer.sceneMain() {
        WorldEditorBackNavigation.remember(backDestination, backWorld, backExistingWorlds)
        WorldEditorConfigurationContext.clear()
        drawWhiteSceneBackground()
        drawNavigation()
        drawTitle()
        drawHeader()
        palette.draw(this)
        WorldEditorSceneFrames.drawFieldFrame(this)
        WorldEditorSceneFrames.drawPropertiesFrame(this)
        drawBottomActions()
        drawSourceConfigurationsButton()

        fieldLayer = container { }
        propertiesLayer = container { }
        errorBackground = container { }
        errorText = WorldEditorSceneFrames.drawErrorText(this, errorBackground)

        redrawDynamicContent()
    }

    /**
     * Draws the button below the palette that opens the existing source-configuration menu on
     * the editor's working level.
     */
    private fun SContainer.drawSourceConfigurationsButton() {
        SceneUIUtils.drawBorderedTextButton(
            this, CONFIG_BUTTON_X, CONFIG_BUTTON_Y, CONFIG_BUTTON_W, CONFIG_BUTTON_H,
            "Quellkonfigurationen", 24.0
        ).onClickWithSound { openSourceConfigurations() }
    }

    /**
     * Opens the configuration menu on the editor's working level. The
     * [WorldEditorConfigurationContext] makes the configuration scenes edit this session's level
     * and return here instead of touching the single-table game state.
     */
    private fun openSourceConfigurations() {
        WorldEditorConfigurationContext.open(session)
        launch {
            sceneContainer.changeTo { ConfigurationScene() }
        }
    }

    private fun SContainer.drawNavigation() {
        ControlButtons.drawBackButton(this).onClickWithSound { cancelEditor() }
        ControlButtons.drawCloseButton(this).onClickWithSound { cancelEditor() }
    }

    private fun SContainer.drawTitle() {
        text(TITLE_TEXT, textSize = 68.0, color = Colors.BLACK, font = Assets.bitcountSingle) {
            centerXOn(this@drawTitle)
            y = TITLE_Y
        }
    }

    private fun SContainer.drawHeader() {
        text(
            "Welt: ${session.worldName} * basiert auf Standard-Vorlage (#224)",
            textSize = 26.0,
            color = Colors.BLACK,
            font = Assets.bitcountSingle
        ) {
            x = 64.0
            y = HEADER_Y
        }
    }

    private fun SContainer.drawBottomActions() {
        SceneUIUtils.drawBorderedTextButton(
            this, CANCEL_X, BOTTOM_Y, BUTTON_W, BUTTON_H, "Abbrechen", 26.0
        ).onClickWithSound { cancelEditor() }
        SceneUIUtils.drawBorderedTextButton(
            this, SAVE_X, BOTTOM_Y, BUTTON_W, BUTTON_H, "Speichern", 26.0
        ).onClickWithoutSound { saveEditor() }
    }

    private fun redrawDynamicContent() {
        fieldLayer.removeChildren()
        propertiesLayer.removeChildren()
        drawStreets(fieldLayer)
        drawEditorNodes(fieldLayer)
        drawProperties(propertiesLayer)

        // Show or hide error message
        val errorMessage = session.errorMessage
        if (!errorMessage.isNullOrEmpty()) {
            errorBackground.visible = true
            errorText.text = visibleErrorMessage(errorMessage)
        } else {
            errorBackground.visible = false
            errorText.text = ""
        }
    }

    private fun drawStreets(container: Container) {
        streetRenderer.draw(container, session.level.streets)
    }

    private fun drawEditorNodes(container: Container) {
        val labels = nodeLabels()
        session.editorNodes.forEach { node ->
            val x = WorldEditorSceneGeometry.nodeViewX(node.x)
            val y = WorldEditorSceneGeometry.nodeViewY(node.y)
            val view = container.container {
                position(x, y)
                drawEditorNodeMarker(node, labels.getValue(node.id))
            }
            attachNodeInteractions(view, node)
        }
    }

    /** Display labels (Q1/S1, ...) per editor node ID, in the order the markers are drawn. */
    private fun nodeLabels(): Map<String, String> {
        var sourceIndex = 0
        var targetIndex = 0
        return session.editorNodes.associate { node ->
            val label = if (node.kind == WorldEditorNodeKind.SOURCE) "Q${++sourceIndex}" else "S${++targetIndex}"
            node.id to label
        }
    }

    private fun Container.drawEditorNodeMarker(node: WorldEditorNodeDTO, label: String) {
        val selected = node.id == session.selectedNodeId
        val rejected = node.id in rejectedSyncNodeIds
        val fills = markerFillColors(node)
        val stripeWidth = NODE_SIZE / fills.size
        graphics {
            fills.forEachIndexed { index, fillColor ->
                fill(fillColor) { rect(index * stripeWidth, 0.0, stripeWidth, NODE_SIZE) }
            }
            stroke(
                when {
                    rejected -> Colors["#D00000"]
                    selected -> Colors["#F2A100"]
                    else -> Colors.BLACK
                },
                StrokeInfo(thickness = if (selected || rejected) 4.0 else 2.0)
            ) {
                rect(0.0, 0.0, NODE_SIZE, NODE_SIZE)
            }
        }
        text(label, textSize = 12.0, color = markerLabelColor(fills), font = Assets.bitcountSingle) {
            centerOn(this@drawEditorNodeMarker)
        }
    }

    /**
     * Resolves the assigned node colors for rendering; multiple colors are drawn as vertical
     * stripes. Unresolvable colors fall back to a neutral grey so the marker stays visible.
     */
    private fun markerFillColors(node: WorldEditorNodeDTO): List<korlibs.image.color.RGBA> {
        val resolved = node.colorNames.mapNotNull { session.colorByName(it)?.value }
        return resolved.ifEmpty { listOf(Colors["#BBBBBB"]) }
    }

    /** Picks a readable label color against the average brightness of the marker fills. */
    private fun markerLabelColor(fills: List<korlibs.image.color.RGBA>): korlibs.image.color.RGBA {
        val averageLuminance = fills
            .map { 0.299 * it.r + 0.587 * it.g + 0.114 * it.b }
            .average()
        return if (averageLuminance < 140.0) Colors.WHITE else Colors.BLACK
    }

    private fun attachNodeInteractions(view: Container, node: WorldEditorNodeDTO) {
        var startX = node.x
        var startY = node.y
        view.onClickWithSound {
            session.selectNode(node.id)
            redrawDynamicContent()
        }
        view.onMouseDrag {
            if (it.start) {
                session.selectNode(node.id)
                startX = node.x
                startY = node.y
            }
            val nextScreenX = WorldEditorSceneGeometry.clampScreenXToField(
                WorldEditorSceneGeometry.levelToScreenX(startX) + it.dx
            )
            val nextScreenY = WorldEditorSceneGeometry.clampScreenYToField(
                WorldEditorSceneGeometry.levelToScreenY(startY) + it.dy
            )
            view.position(nextScreenX - NODE_SIZE / 2.0, nextScreenY - NODE_SIZE / 2.0)
            if (it.end) {
                finishNodeDrag(node.id, nextScreenX, nextScreenY)
            }
        }
    }

    private fun finishNodeDrag(nodeId: String, screenX: Double, screenY: Double) {
        val moved = session.moveNode(
            nodeId,
            WorldEditorSceneGeometry.screenToLevelX(screenX),
            WorldEditorSceneGeometry.screenToLevelY(screenY)
        )
        if (!moved) InteractionSoundController.play(InteractionSoundEffect.ERROR)
        redrawDynamicContent()
    }

    private fun drawProperties(container: Container) {
        propertiesPanel.draw(container)
    }

    private fun addNodeAtFieldCenter(kind: WorldEditorNodeKind) {
        val added = session.addNode(
            kind,
            WorldEditorSceneGeometry.levelCenterX,
            WorldEditorSceneGeometry.levelCenterY
        )
        if (added == null) {
            InteractionSoundController.play(InteractionSoundEffect.ERROR)
        }
        redrawDynamicContent()
    }

    private fun dropPaletteNode(kind: WorldEditorNodeKind, screenX: Double, screenY: Double) {
        if (!WorldEditorSceneGeometry.screenPointInField(screenX, screenY)) {
            session.errorMessage = "Quelle/Senke muss auf dem Spielfeld abgelegt werden."
            InteractionSoundController.play(InteractionSoundEffect.ERROR)
            redrawDynamicContent()
            return
        }
        val added = session.addNode(
            kind,
            WorldEditorSceneGeometry.screenToLevelX(screenX),
            WorldEditorSceneGeometry.screenToLevelY(screenY)
        )
        if (added == null) {
            InteractionSoundController.play(InteractionSoundEffect.ERROR)
        }
        redrawDynamicContent()
    }

    private fun saveEditor() {
        val request = session.saveRequestOrNull()
        if (request == null) {
            InteractionSoundController.play(InteractionSoundEffect.ERROR)
            redrawDynamicContent()
            return
        }

        launch {
            try {
                val savedState = saveRequestToServer(request)
                rememberSavedEditorWorld(savedState)
                completeSave()
            } catch (e: Exception) {
                handleSaveFailure(request, e)
            }
        }
    }

    /**
     * Mirrors the edited sources onto the server world and leaves the editor.
     *
     * The sync respects the server's spawner rules (outer world edges only, minimum spawn
     * interval), so a failure here signals a real server problem: the editor stays open with a
     * visible error so the player can retry. The level itself is already saved at this point.
     */
    private suspend fun completeSave() {
        val sourceOutcome = syncServerSources()
        if (sourceOutcome?.errorMessage != null) {
            rejectedSyncNodeIds = session.editorIdsOf(sourceOutcome.rejectedSources)
            session.errorMessage = syncErrorText(sourceOutcome)
            redrawDynamicContent()
            return
        }
        val transitionOutcome = syncServerTransitions()
        if (transitionOutcome?.errorMessage != null) {
            rejectedSyncNodeIds = emptySet()
            session.errorMessage = transitionSyncErrorText(transitionOutcome)
            redrawDynamicContent()
            return
        }
        session.worldId?.let(ServerConnection::forgetFieldMetadata)
        rejectedSyncNodeIds = emptySet()
        finishSuccessfulEditorSave()
    }

    /** Builds the sync error text, naming the red-marked labels of the rejected sources. */
    private fun syncErrorText(outcome: worldeditor.SourceSyncOutcome): String {
        val labels = nodeLabels()
            .filterKeys { it in rejectedSyncNodeIds }
            .values.sorted()
        val prefix = if (labels.isEmpty()) {
            "Quellen-Uebertragung zum Server fehlgeschlagen"
        } else {
            "Rot markierte Quellen (${labels.joinToString(", ")}) wurden vom Server abgelehnt"
        }
        return "$prefix: ${outcome.errorMessage}"
    }

    /** Builds the sync error text for server transition failures. */
    private fun transitionSyncErrorText(outcome: worldeditor.TransitionSyncOutcome): String {
        return "Uebergaenge zum Server fehlgeschlagen: ${outcome.errorMessage}"
    }

    /**
     * Replaces the server world's per-field sources with the edited source layout.
     *
     * @return null when nothing had to be synced, else the sync outcome.
     */
    private suspend fun syncServerSources(): worldeditor.SourceSyncOutcome? {
        val worldId = session.worldId ?: return null
        val openedWorld = WorldSelectionScene.openedWorld?.takeIf { it.id == worldId } ?: return null
        if (openedWorld.source != "Server") return null
        if (ServerConnection.connectionState != ConnectionState.CONNECTED) return null
        return WorldEditorServerSourceSync.syncSources(worldId, session.level)
    }

    /**
     * Replaces the server world's transitions with the edited edge-sink layout.
     *
     * @return null when nothing had to be synced, else the sync outcome.
     */
    private suspend fun syncServerTransitions(): worldeditor.TransitionSyncOutcome? {
        val worldId = session.worldId ?: return null
        val openedWorld = WorldSelectionScene.openedWorld?.takeIf { it.id == worldId } ?: return null
        if (openedWorld.source != "Server") return null
        if (ServerConnection.connectionState != ConnectionState.CONNECTED) return null
        return WorldEditorServerTransitionSync.syncTransitions(worldId, session.level)
    }

    /** Handles server-save failures by storing temporary worlds locally when possible. */
    private suspend fun handleSaveFailure(request: network.SaveWorldEditorDTO, error: Exception) {
        if (rememberTemporarySave(request)) {
            completeSave()
            return
        }

        session.errorMessage = "Speichern fehlgeschlagen: ${error.message ?: "Server nicht erreichbar"}"
        InteractionSoundController.play(InteractionSoundEffect.ERROR)
        redrawDynamicContent()
    }

    /**
     * Stores the edited level locally when the server cannot persist editor states.
     *
     * The coupling server does not offer the editor-state endpoints yet, so this local copy is
     * what the dial-in flow and the editor restore later — otherwise the edit would be lost.
     */
    private suspend fun rememberTemporarySave(request: network.SaveWorldEditorDTO): Boolean {
        val worldId = session.worldId ?: return false
        val openedWorld = WorldSelectionScene.openedWorld?.takeIf { it.id == worldId } ?: return false
        if (openedWorld.levelSelection == null && openedWorld.savedLevelPath == null) return false

        val savedLevelPath = try {
            persistence.EditorWorldSaves.saveLevel(worldId, session.level)
        } catch (e: Exception) {
            println("Local editor save failed for world $worldId: ${e.message}")
            return false
        }
        WorldSelectionScene.rememberOpenedWorld(
            openedWorld.copy(name = request.name, savedLevelPath = savedLevelPath)
        )
        return true
    }

    /** Plays successful save feedback before leaving the editor. */
    private suspend fun finishSuccessfulEditorSave() {
        InteractionSoundController.play(InteractionSoundEffect.SUCCESS)
        delay(SUCCESS_SOUND_SCENE_CHANGE_DELAY_MILLIS.milliseconds)
        sceneContainer.changeTo { WorldSelectionScene() }
    }

    /** Updates the world-selection state after a successful editor save. */
    private fun rememberSavedEditorWorld(savedState: network.WorldEditorStateDTO) {
        WorldSelectionScene.rememberSavedEditorWorld(savedState)
    }

    /** Sends the current editor save request to the create or update endpoint. */
    private suspend fun saveRequestToServer(request: network.SaveWorldEditorDTO): network.WorldEditorStateDTO {
        if (session.isNewWorld) return WorldEditorRepository.gateway.createEditorWorld(request)
        val worldId = session.worldId ?: error("Bestehende Welt hat keine ID.")
        return WorldEditorRepository.gateway.updateEditorWorld(worldId, request)
    }

    private fun cancelEditor() {
        launch {
            WorldEditorBackNavigation.navigate(
                sceneContainer,
                backDestination,
                backWorld,
                backExistingWorlds
            )
        }
    }

    private fun visibleErrorMessage(message: String?): String {
        val text = message.orEmpty()
        if (text.length <= ERROR_MAX_CHARS) return text
        return text.take(ERROR_MAX_CHARS - 3) + "..."
    }
}
