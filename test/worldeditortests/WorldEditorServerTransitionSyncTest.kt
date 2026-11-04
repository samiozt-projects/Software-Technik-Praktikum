package worldeditortests

import domain.Color
import domain.Level
import domain.TargetNode
import korlibs.image.color.Colors
import korlibs.io.async.suspendTest
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import network.CoordinateDTO
import network.CreateTransitionDTO
import network.FieldPropertiesDTO
import network.TransitionNode
import network.WorldPropertiesDTO
import rendering.GameAreaScale
import worldeditor.WorldEditorServerTransitionSync
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the per-field server transition sync that mirrors edited edge sinks onto the coupling
 * server, so vehicles can leave one table and arrive at a neighboring table.
 */
class WorldEditorServerTransitionSyncTest {

    private companion object {
        const val WORLD_ID = "world-transition-sync-test"
        const val FIELD_A = "$WORLD_ID-field-0-0"
        const val FIELD_B = "$WORLD_ID-field-0-1"
        const val FIELD_C = "$WORLD_ID-field-1-0"
        const val FIELD_D = "$WORLD_ID-field-1-1"
    }

    private val deletedTransitions = mutableListOf<Pair<String, String>>()
    private val createdTransitions = mutableListOf<CreateTransitionDTO>()

    /** Restores the replaceable server calls after each test. */
    @AfterTest
    fun resetHooks() {
        WorldEditorServerTransitionSync.resetTestHooks()
        deletedTransitions.clear()
        createdTransitions.clear()
    }

    /** Installs in-memory server hooks with a 2x2 world and one stale transition. */
    private fun installFakeServer() {
        WorldEditorServerTransitionSync.loadWorldCall = {
            WorldPropertiesDTO(WORLD_ID, columns = 2, rows = 2, fieldHeight = 100, fieldWidth = 100)
        }
        WorldEditorServerTransitionSync.loadFieldsCall = {
            val stale = TransitionNode(
                nodeId = "transition-old",
                fromFieldId = FIELD_A,
                toFieldId = FIELD_B,
                coordinate = CoordinateDTO(100, 50),
                biDirectionalWay = false,
                level = 0
            )
            listOf(
                FieldPropertiesDTO(FIELD_A, false, CoordinateDTO(0, 0), WORLD_ID, fieldTransitions = listOf(stale)),
                FieldPropertiesDTO(FIELD_B, false, CoordinateDTO(100, 0), WORLD_ID, fieldTransitions = listOf(stale)),
                FieldPropertiesDTO(FIELD_C, false, CoordinateDTO(0, 100), WORLD_ID),
                FieldPropertiesDTO(FIELD_D, false, CoordinateDTO(100, 100), WORLD_ID)
            )
        }
        WorldEditorServerTransitionSync.deleteTransitionCall = { _, fieldId, nodeId ->
            deletedTransitions += fieldId to nodeId
        }
        WorldEditorServerTransitionSync.createTransitionCall = { _, request ->
            createdTransitions += request
        }
    }

    /** Verifies that stale transitions are deleted and edge sinks produce server transitions. */
    @Test
    fun syncTransitions_replacesServerTransitionsFromEdgeTargets() = suspendTest {
        installFakeServer()
        val level = levelWithTarget(GameAreaScale.GAME_AREA_W_METER, GameAreaScale.GAME_AREA_H_METER / 2.0)

        val outcome = WorldEditorServerTransitionSync.syncTransitions(WORLD_ID, level)

        assertNull(outcome.errorMessage)
        assertEquals(listOf(FIELD_A to "transition-old"), deletedTransitions)
        assertEquals(listOf(FIELD_A to FIELD_B, FIELD_C to FIELD_D), createdTransitions.map { it.fromFieldId to it.toFieldId })
        assertTrue(createdTransitions.all { it.coordinate.x in listOf(100, 200) })
    }

    /** Verifies that target positions are scaled into world-global transition coordinates. */
    @Test
    fun syncTransitions_scalesCoordinatesIntoWorldSpace() = suspendTest {
        installFakeServer()
        val level = levelWithTarget(GameAreaScale.GAME_AREA_W_METER, GameAreaScale.GAME_AREA_H_METER / 4.0)

        val outcome = WorldEditorServerTransitionSync.syncTransitions(WORLD_ID, level)

        assertNull(outcome.errorMessage)
        assertEquals(
            listOf(CoordinateDTO(100, 25), CoordinateDTO(100, 125)),
            createdTransitions.map { it.coordinate }
        )
    }

    /** Verifies that no transition is created when no target lies on a field edge. */
    @Test
    fun syncTransitions_skipsTargetsAwayFromEdges() = suspendTest {
        installFakeServer()
        val level = levelWithTarget(
            GameAreaScale.GAME_AREA_W_METER / 2.0,
            GameAreaScale.GAME_AREA_H_METER / 2.0
        )

        val outcome = WorldEditorServerTransitionSync.syncTransitions(WORLD_ID, level)

        assertNull(outcome.errorMessage)
        assertTrue(createdTransitions.isEmpty())
    }

    /** Verifies that server rejection is reported to the editor. */
    @Test
    fun syncTransitions_reportsRejectedTransition() = suspendTest {
        installFakeServer()
        WorldEditorServerTransitionSync.createTransitionCall = { _, _ -> throw IOException("Transition abgelehnt") }
        val level = levelWithTarget(GameAreaScale.GAME_AREA_W_METER, GameAreaScale.GAME_AREA_H_METER / 2.0)

        val outcome = WorldEditorServerTransitionSync.syncTransitions(WORLD_ID, level)

        assertNotNull(outcome.errorMessage)
        assertTrue("Transition abgelehnt" in outcome.errorMessage!!)
    }

    private fun levelWithTarget(x: Double, y: Double): Level {
        val level = Level("Transition Sync", 1)
        level.addNode(
            TargetNode(
                position = Point(x, y),
                elevation = 0,
                orientation = Angle.ZERO,
                color = Color("Rot", Colors.RED)
            )
        )
        return level
    }
}
