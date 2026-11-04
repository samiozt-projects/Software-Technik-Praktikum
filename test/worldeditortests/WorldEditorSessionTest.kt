package worldeditortests

import domain.Color
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.TargetNode
import domain.WeatherCondition
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import network.WorldEditorNodeKind
import worldeditor.WorldEditorPlacement
import persistence.LevelSerializer
import rendering.GameAreaScale
import worldeditor.WorldEditorDefaults
import worldeditor.WorldEditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests placement, moving and removal behavior of the world-editor session.
 *
 * Color and vehicle-type settings are covered by [WorldEditorSessionSettingsTest]; saving and
 * restoring is covered by [WorldEditorSessionPersistenceTest].
 */
class WorldEditorSessionTest {

    /** Verifies that template editing happens on a cloned level. */
    @Test
    fun fromTemplate_clonesLevelSoCancelCanDiscardWorkingCopy() {
        val template = sampleEditorLevel()
        val templateJsonBefore = LevelSerializer.toJsonString(template)
        val session = WorldEditorSession.fromTemplate(1, template)

        session.addNode(WorldEditorNodeKind.SOURCE, 25.0, 25.0)
        val selectedNodeId = assertNotNull(session.selectedNodeId)
        session.moveNode(selectedNodeId, 35.0, 35.0)

        assertNotEquals(template, session.level)
        assertEquals(templateJsonBefore, LevelSerializer.toJsonString(template))
        assertNotEquals(templateJsonBefore, LevelSerializer.toJsonString(session.level))
    }

    /** Verifies adding nodes, selecting them and updating their editable properties. */
    @Test
    fun addNodeAndPropertyUpdates_areReflectedInSaveDto() {
        val session = WorldEditorSession.fromLevel(
            worldId = null,
            worldName = " Neue Editor Welt ",
            templateLevelId = 4,
            isNewWorld = true,
            sourceLevel = Level("Empty", 4, weatherCondition = WeatherCondition.DRY)
        )
        val source = assertNotNull(session.addNode(WorldEditorNodeKind.SOURCE, 10.0, 20.0))
        val target = assertNotNull(session.addNode(WorldEditorNodeKind.TARGET, 80.0, 30.0))

        session.selectNode(source.id)
        assertTrue(session.toggleSelectedColor("Blau"))
        assertTrue(session.toggleSelectedVehicleType("Lastwagen"))
        session.selectNode(target.id)
        assertTrue(session.toggleSelectedColor("Gelb"))
        assertTrue(session.toggleSelectedVehicleType("Motorrad"))

        val request = assertNotNull(session.saveRequestOrNull())
        val sourceDto = request.editorNodes.first { it.id == source.id }
        val targetDto = request.editorNodes.first { it.id == target.id }

        assertEquals("Neue Editor Welt", request.name)
        assertEquals(4, request.templateLevelId)
        assertEquals(listOf("Rot", "Blau"), sourceDto.colorNames)
        assertEquals(listOf("Auto", "Lastwagen"), sourceDto.vehicleTypeNames)
        assertEquals(listOf("Gelb"), targetDto.colorNames)
        assertEquals(listOf("Auto", "Motorrad"), targetDto.vehicleTypeNames)
        assertTrue(request.levelJson.contains("Lastwagen"))
    }

    /**
     * Verifies that restoring a server state keeps the source configurations from the level JSON
     * while sinks keep only one selected color from the DTOs.
     */
    @Test
    fun fromServerState_keepsLevelConfigurationsAndRestoresSingleSinkColor() {
        val state = colorfulServerState(sampleEditorLevel())

        val session = WorldEditorSession.fromServerState(state)

        val sourceNode = session.level.nodes.filterIsInstance<SourceNode>().single()
        val targetNode = session.level.nodes.filterIsInstance<TargetNode>().single()
        assertEquals(listOf("Rot"), sourceNode.configurations.map { it.color.name })
        assertEquals(listOf("Rot"), session.editorNodes.first { it.id == "source-1" }.colorNames)
        assertEquals(listOf("Gelb"), session.editorNodes.first { it.id == "target-1" }.colorNames)
        assertEquals(listOf("Auto", "Motorrad"), session.editorNodes.first { it.id == "target-1" }.vehicleTypeNames)
        assertEquals("Gelb", targetNode.color.name)
    }

    /**
     * Verifies that per-configuration tuning survives a save-and-restore roundtrip via the level
     * JSON, which remains the authoritative store for source configurations.
     */
    @Test
    fun saveAndRestore_preservesPerConfigurationTuning() {
        val session = emptyEditorSession()
        assertNotNull(session.addNode(WorldEditorNodeKind.SOURCE, 30.0, 30.0))
        assertTrue(session.toggleSelectedColor("Blau"))
        assertNotNull(session.addNode(WorldEditorNodeKind.TARGET, 60.0, 30.0))
        val sourceNode = session.level.nodes.filterIsInstance<SourceNode>().single()
        sourceNode.configurations = sourceNode.configurations.mapIndexed { index, cfg ->
            SourceNodeConfiguration(
                color = cfg.color,
                generationSpeed = if (index == 0) 2.0 else 6.0,
                initialVehicleSpeed = cfg.initialVehicleSpeed,
                vehicleType = cfg.vehicleType,
                vehiclesToSpawn = cfg.vehiclesToSpawn
            )
        }
        val request = assertNotNull(session.saveRequestOrNull())

        val restored = WorldEditorSession.fromServerState(
            editorState("world-tuned", "Getunte Welt", request.levelJson, request.editorNodes)
        )

        val restoredSource = restored.level.nodes.filterIsInstance<SourceNode>().single()
        assertEquals(2, restoredSource.configurations.size)
        assertEquals(setOf(2.0, 6.0), restoredSource.configurations.map { it.generationSpeed }.toSet())
        assertEquals(listOf("Rot", "Blau"), restoredSource.configurations.map { it.color.name })
    }

    /**
     * Verifies that a session removes duplicate color-and-type configurations at creation, so a
     * source never shows the same configuration card twice in the configuration menu.
     */
    @Test
    fun fromLevel_removesDuplicateSourceConfigurations() {
        val car = WorldEditorDefaults.vehicleTypes.first { it.name == "Auto" }
        val red = Color("Rot", Colors.RED)
        val blue = Color("Blau", Colors.BLUE)
        val source = SourceNode(
            position = Point(10.0, 20.0),
            elevation = 0,
            orientation = Angle.ZERO,
            configurations = listOf(
                SourceNodeConfiguration(red, 1.0, car.maxSpeed, car),
                SourceNodeConfiguration(red, 2.0, car.maxSpeed, car),
                SourceNodeConfiguration(blue, 1.0, car.maxSpeed, car)
            )
        )
        val level = Level("Duplikate", 1, weatherCondition = WeatherCondition.DRY)
        level.addNode(source)

        val session = WorldEditorSession.fromLevel(
            worldId = "world-dup",
            worldName = "Alte Welt",
            templateLevelId = 1,
            isNewWorld = false,
            sourceLevel = level
        )

        val sessionSource = session.level.nodes.filterIsInstance<SourceNode>().single()
        assertEquals(
            listOf("Rot" to "Auto", "Blau" to "Auto"),
            sessionSource.configurations.map { it.color.name to it.vehicleType.name }
        )
    }

    /**
     * Verifies that the shared dedupe helper keeps the first configuration of each
     * color-and-type pair, matching names case-insensitively.
     */
    @Test
    fun dedupedConfigurations_dropRepeatedColorTypePairs() {
        val car = WorldEditorDefaults.vehicleTypes.first { it.name == "Auto" }
        val red = Color("Rot", Colors.RED)
        val redLower = Color("rot", Colors.RED)
        val first = SourceNodeConfiguration(red, 1.0, car.maxSpeed, car)
        val duplicate = SourceNodeConfiguration(redLower, 9.0, car.maxSpeed, car)

        val deduped = WorldEditorSession.dedupedConfigurations(listOf(first, duplicate))

        assertEquals(listOf(first), deduped)
    }

    /** Verifies that a source stripped of every color fails validation before saving. */
    @Test
    fun saveRequestOrNull_rejectsSourceWithoutAnyColor() {
        val session = WorldEditorSession.fromLevel(
            worldId = "world-colorless",
            worldName = "Alte Welt",
            templateLevelId = 1,
            isNewWorld = false,
            sourceLevel = sampleEditorLevel()
        )
        session.level.nodes.filterIsInstance<SourceNode>().single().configurations = emptyList()

        assertNull(session.saveRequestOrNull())
        assertEquals("Jede Quelle/Senke braucht mindestens eine Farbe.", session.errorMessage)
    }

    /** Verifies that dragging a node keeps directly connected street geometry attached. */
    @Test
    fun moveNode_movesConnectedStreetEndpoint() {
        val session = sampleLevelSession()

        assertTrue(session.moveNode("source-1", 15.0, 25.0))
        val source = session.editorNodes.first { it.id == "source-1" }

        assertEquals(15.0, source.x)
        assertEquals(25.0, source.y)
        assertEquals(Point(source.x, source.y), session.level.streets.single().path.first())
    }

    /** Verifies that dropping two nodes onto the same position resolves to the minimum distance. */
    @Test
    fun addNode_resolvesOverlapToMinimumDistance() {
        val session = emptyEditorSession()
        val source = assertNotNull(session.addNode(WorldEditorNodeKind.SOURCE, 20.0, 20.0))
        val target = assertNotNull(session.addNode(WorldEditorNodeKind.TARGET, 20.0, 20.0))

        val distance = Point(source.x, source.y).distanceTo(Point(target.x, target.y))

        assertTrue(distance >= WorldEditorSession.MIN_NODE_DISTANCE_METER - 0.0001)
    }

    /** Verifies that overlap correction near a field edge stays inside the field. */
    @Test
    fun addNode_nearFieldEdgeKeepsCorrectedPositionInsideField() {
        val session = emptyEditorSession()
        val source = assertNotNull(
            session.addNode(WorldEditorNodeKind.SOURCE, GameAreaScale.GAME_AREA_W_METER - 1.0, 5.0)
        )
        val target = assertNotNull(
            session.addNode(WorldEditorNodeKind.TARGET, GameAreaScale.GAME_AREA_W_METER - 1.0, 5.0)
        )

        assertTrue(target.x in 0.0..GameAreaScale.GAME_AREA_W_METER)
        assertTrue(target.y in 0.0..GameAreaScale.GAME_AREA_H_METER)
        assertTrue(
            Point(source.x, source.y).distanceTo(Point(target.x, target.y)) >=
                WorldEditorSession.MIN_NODE_DISTANCE_METER - 0.0001
        )
    }

    /** Verifies that a drop in the field center keeps the requested free-field position. */
    @Test
    fun addNode_inFieldCenterKeepsRequestedPosition() {
        val session = emptyEditorSession()
        val source = assertNotNull(
            session.addNode(
                WorldEditorNodeKind.SOURCE,
                GameAreaScale.GAME_AREA_W_METER / 2.0,
                GameAreaScale.GAME_AREA_H_METER / 2.0
            )
        )

        assertEquals(GameAreaScale.GAME_AREA_W_METER / 2.0, source.x)
        assertEquals(GameAreaScale.GAME_AREA_H_METER / 2.0, source.y)
    }

    /**
     * Verifies that a drop outside the field is clamped into the placement bounds: the level
     * inset by the node-edge margin, so the node graphic is never clipped by the screen edge.
     */
    @Test
    fun addNode_outsideFieldClampsToFieldBounds() {
        val session = emptyEditorSession()
        val source = assertNotNull(
            session.addNode(WorldEditorNodeKind.SOURCE, -10.0, GameAreaScale.GAME_AREA_H_METER + 15.0)
        )

        assertEquals(WorldEditorPlacement.NODE_EDGE_MARGIN_METER, source.x)
        assertEquals(GameAreaScale.GAME_AREA_H_METER - WorldEditorPlacement.NODE_EDGE_MARGIN_METER, source.y)
    }

    /** Verifies that moving a connected source into another node resolves spacing and updates the street. */
    @Test
    fun moveNode_resolvesOverlapAndMovesConnectedStreetEndpoint() {
        val session = sampleLevelSession()

        assertTrue(session.moveNode("source-1", 90.0, 20.0))
        val source = session.editorNodes.first { it.id == "source-1" }
        val target = session.editorNodes.first { it.id == "target-1" }

        assertTrue(
            Point(source.x, source.y).distanceTo(Point(target.x, target.y)) >=
                WorldEditorSession.MIN_NODE_DISTANCE_METER - 0.0001
        )
        assertTrue(source.x in 0.0..GameAreaScale.GAME_AREA_W_METER)
        assertTrue(source.y in 0.0..GameAreaScale.GAME_AREA_H_METER)
        assertEquals(Point(source.x, source.y), session.level.streets.single().path.first())
    }

    /** Verifies that deleting a source or sink also removes streets depending on it. */
    @Test
    fun removeNode_deletesConnectedStreets() {
        val session = sampleLevelSession()

        assertTrue(session.removeNode("source-1"))

        assertTrue(session.level.streets.isEmpty())
        assertFalse(session.level.nodes.any { it is SourceNode })
    }

    /** Builds a server state with separate source and sink DTO color lists. */
    private fun colorfulServerState(stateLevel: Level): network.WorldEditorStateDTO =
        editorState(
            worldId = "world-1",
            name = "Bunte Welt",
            levelJson = LevelSerializer.toJsonString(stateLevel),
            nodes = listOf(
                editorNodeDto("source-1", WorldEditorNodeKind.SOURCE, 10.0, 20.0, listOf("Rot", "Blau")),
                editorNodeDto(
                    "target-1",
                    WorldEditorNodeKind.TARGET,
                    90.0,
                    20.0,
                    listOf("Gelb", "Gruen"),
                    listOf("Auto", "Motorrad")
                )
            )
        )
}
