package worldeditortests

import domain.Level
import domain.SourceNode
import domain.TargetNode
import korlibs.math.geom.Point
import network.WorldEditorNodeKind
import rendering.GameAreaScale
import worldeditor.WorldEditorPlacement
import worldeditor.WorldEditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests placement and save validation behavior of [WorldEditorSession]. */
class WorldEditorSessionPlacementTest {

    /** Verifies that dragging a node keeps directly connected street geometry attached. */
    @Test
    fun moveNode_movesConnectedStreetEndpoint() {
        val session = WorldEditorSession.fromLevel(
            worldId = "world-1",
            worldName = "Bestehend",
            templateLevelId = 1,
            isNewWorld = false,
            sourceLevel = sampleLevel()
        )

        assertTrue(session.moveNode("source-1", 15.0, 25.0))
        val source = session.editorNodes.first { it.id == "source-1" }

        assertEquals(15.0, source.x)
        assertEquals(25.0, source.y)
        assertEquals(Point(source.x, source.y), session.level.streets.single().path.first())
    }

    /** Verifies that dropping two nodes onto the same position resolves to the minimum distance. */
    @Test
    fun addNode_resolvesOverlapToMinimumDistance() {
        val session = emptySession()
        val source = assertNotNull(session.addNode(WorldEditorNodeKind.SOURCE, 20.0, 20.0))
        val target = assertNotNull(session.addNode(WorldEditorNodeKind.TARGET, 20.0, 20.0))

        val distance = Point(source.x, source.y).distanceTo(Point(target.x, target.y))

        assertTrue(distance >= WorldEditorSession.MIN_NODE_DISTANCE_METER - 0.0001)
    }

    /** Verifies that overlap correction near a field edge stays inside the field. */
    @Test
    fun addNode_nearFieldEdgeKeepsCorrectedPositionInsideField() {
        val session = emptySession()
        val source = assertNotNull(
            session.addNode(WorldEditorNodeKind.SOURCE, GameAreaScale.GAME_AREA_W_METER - 1.0, 5.0)
        )
        val target = assertNotNull(
            session.addNode(WorldEditorNodeKind.TARGET, GameAreaScale.GAME_AREA_W_METER - 1.0, 5.0)
        )

        assertTrue(target.x in 0.0..GameAreaScale.GAME_AREA_W_METER)
        assertTrue(target.y in 0.0..GameAreaScale.GAME_AREA_H_METER)
        assertTrue(Point(source.x, source.y).distanceTo(Point(target.x, target.y)) >=
            WorldEditorSession.MIN_NODE_DISTANCE_METER - 0.0001)
    }

    /** Verifies that a drop in the field center keeps the requested free-field position. */
    @Test
    fun addNode_inFieldCenterKeepsRequestedPosition() {
        val session = emptySession()
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
    fun addNode_outsideFieldClampsToPlacementBounds() {
        val session = emptySession()
        val source = assertNotNull(
            session.addNode(WorldEditorNodeKind.SOURCE, -10.0, GameAreaScale.GAME_AREA_H_METER + 15.0)
        )

        assertEquals(WorldEditorPlacement.NODE_EDGE_MARGIN_METER, source.x)
        assertEquals(GameAreaScale.GAME_AREA_H_METER - WorldEditorPlacement.NODE_EDGE_MARGIN_METER, source.y)
    }

    /** Verifies that a drop directly on the field corner keeps the full graphic visible. */
    @Test
    fun addNode_onFieldCornerClampsToPlacementBounds() {
        val session = emptySession()
        val source = assertNotNull(session.addNode(WorldEditorNodeKind.SOURCE, 0.0, 0.0))

        assertEquals(WorldEditorPlacement.NODE_EDGE_MARGIN_METER, source.x)
        assertEquals(WorldEditorPlacement.NODE_EDGE_MARGIN_METER, source.y)
    }

    /** Verifies that the exact border positions of the predefined template nodes stay allowed. */
    @Test
    fun moveNode_allowsExactTemplateBorderPosition() {
        val session = sessionWithSourceNode()
        val id = assertNotNull(session.selectedNode).id

        assertTrue(session.moveNode(id, WorldEditorPlacement.NODE_EDGE_MARGIN_METER, 41.67))

        val node = session.editorNodes.single()
        assertEquals(WorldEditorPlacement.NODE_EDGE_MARGIN_METER, node.x)
        assertEquals(41.67, node.y)
    }

    /** Verifies that moving a connected source into another node resolves spacing and updates the street. */
    @Test
    fun moveNode_resolvesOverlapAndMovesConnectedStreetEndpoint() {
        val session = WorldEditorSession.fromLevel(
            worldId = "world-1",
            worldName = "Bestehend",
            templateLevelId = 1,
            isNewWorld = false,
            sourceLevel = sampleLevel()
        )

        assertTrue(session.moveNode("source-1", 90.0, 20.0))
        val source = session.editorNodes.first { it.id == "source-1" }
        val target = session.editorNodes.first { it.id == "target-1" }

        assertTrue(Point(source.x, source.y).distanceTo(Point(target.x, target.y)) >=
            WorldEditorSession.MIN_NODE_DISTANCE_METER - 0.0001)
        assertTrue(source.x in 0.0..GameAreaScale.GAME_AREA_W_METER)
        assertTrue(source.y in 0.0..GameAreaScale.GAME_AREA_H_METER)
        assertEquals(Point(source.x, source.y), session.level.streets.single().path.first())
    }

    /** Verifies that deleting a source or sink also removes streets depending on it. */
    @Test
    fun removeNode_deletesConnectedStreets() {
        val session = WorldEditorSession.fromLevel(
            worldId = "world-1",
            worldName = "Bestehend",
            templateLevelId = 1,
            isNewWorld = false,
            sourceLevel = sampleLevel()
        )

        assertTrue(session.removeNode("source-1"))

        assertTrue(session.level.streets.isEmpty())
        assertFalse(session.level.nodes.any { it is SourceNode })
    }

    /** Verifies save validation for required endpoints and names. */
    @Test
    fun saveRequestOrNull_reportsValidationErrors() {
        val session = WorldEditorSession.fromLevel(
            worldId = null,
            worldName = "   ",
            templateLevelId = 1,
            isNewWorld = true,
            sourceLevel = Level("Empty", 1)
        )

        assertNull(session.saveRequestOrNull())
        assertEquals("Der Weltname darf nicht leer sein.", session.errorMessage)

        session.worldName = "Neue Welt"
        assertNull(session.saveRequestOrNull())
        assertEquals("Die Welt braucht mindestens eine Quelle.", session.errorMessage)

        session.addNode(WorldEditorNodeKind.SOURCE, 10.0, 10.0)
        assertNull(session.saveRequestOrNull())
        assertEquals("Die Welt braucht mindestens eine Senke.", session.errorMessage)
    }

    /** Verifies that manually malformed overlapping editor states fail validation before saving. */
    @Test
    fun saveRequestOrNull_rejectsOverlappingNodes() {
        val session = WorldEditorSession.fromLevel(
            worldId = "world-overlap",
            worldName = "Alte Welt",
            templateLevelId = 1,
            isNewWorld = false,
            sourceLevel = sampleLevel()
        )
        val sourcePosition = session.level.nodes.filterIsInstance<SourceNode>().single().position
        session.level.nodes.filterIsInstance<TargetNode>().single().position = sourcePosition

        assertNull(session.saveRequestOrNull())
        assertEquals("Quellen/Senken brauchen mindestens 12m Abstand.", session.errorMessage)
    }

    /** Verifies that valid inner-field source and sink positions can be saved. */
    @Test
    fun saveRequestOrNull_allowsNodesAwayFromFieldEdge() {
        val session = WorldEditorSession.fromLevel(
            worldId = "world-inner",
            worldName = "Alte Welt",
            templateLevelId = 1,
            isNewWorld = false,
            sourceLevel = sampleLevel()
        )
        session.level.nodes.filterIsInstance<SourceNode>().single().position = Point(50.0, 50.0)

        assertNotNull(session.saveRequestOrNull())
        assertNull(session.errorMessage)
    }
}
