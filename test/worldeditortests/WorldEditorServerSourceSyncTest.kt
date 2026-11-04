package worldeditortests

import domain.Color
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import korlibs.image.color.Colors
import korlibs.io.async.suspendTest
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import network.CoordinateDTO
import network.CreateQuelleDTO
import network.FieldPropertiesDTO
import network.QuelleNode
import network.WorldPropertiesDTO
import rendering.GameAreaScale
import worldeditor.WorldEditorDefaults
import worldeditor.WorldEditorServerSourceSync
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the per-field server source sync that mirrors edited worlds onto the coupling server
 * via the existing source endpoints (the server has no editor-state endpoints). The server
 * expects world-global coordinates on outer world edges — verified against the live server.
 */
class WorldEditorServerSourceSyncTest {

    private companion object {
        const val WORLD_ID = "world-sync-test"
        const val FIELD_A = "$WORLD_ID-field-0-0"
        const val FIELD_B = "$WORLD_ID-field-1-0"
    }

    private val deletedSources = mutableListOf<Pair<String, String>>()
    private val createdSources = mutableListOf<Pair<String, CreateQuelleDTO>>()

    /** Restores the replaceable server calls after each test. */
    @AfterTest
    fun resetHooks() {
        WorldEditorServerSourceSync.resetTestHooks()
    }

    /** Installs in-memory server hooks with a 2x1 world and one stale source on field A. */
    private fun installFakeServer() {
        WorldEditorServerSourceSync.loadWorldCall = {
            WorldPropertiesDTO(WORLD_ID, columns = 2, rows = 1, fieldHeight = 100, fieldWidth = 100)
        }
        WorldEditorServerSourceSync.loadFieldsCall = {
            listOf(
                FieldPropertiesDTO(FIELD_A, false, CoordinateDTO(0, 0), WORLD_ID),
                FieldPropertiesDTO(FIELD_B, false, CoordinateDTO(100, 0), WORLD_ID)
            )
        }
        WorldEditorServerSourceSync.loadSourcesCall = { _, fieldId ->
            if (fieldId == FIELD_A) {
                listOf(QuelleNode(FIELD_A, "quelle-alt", CoordinateDTO(10, 0), 0.5))
            } else {
                emptyList()
            }
        }
        WorldEditorServerSourceSync.deleteSourceCall = { _, fieldId, nodeId ->
            deletedSources += fieldId to nodeId
        }
        WorldEditorServerSourceSync.createSourceCall = { _, fieldId, request ->
            createdSources += fieldId to request
        }
    }

    /** Builds a level with one source at [x], [y] using the given spawn interval. */
    private fun levelWithSource(x: Double, y: Double, generationSpeed: Double = 2.0): Level {
        val level = Level("Sync", 1)
        level.addNode(sourceAt(x, y, generationSpeed))
        return level
    }

    /** Builds one source node at [x], [y] with a single red configuration. */
    private fun sourceAt(x: Double, y: Double, generationSpeed: Double = 2.0): SourceNode {
        val car = WorldEditorDefaults.vehicleTypes.first()
        return SourceNode(
            position = Point(x, y),
            elevation = 0,
            orientation = Angle.ZERO,
            configurations = listOf(
                SourceNodeConfiguration(Color("Rot", Colors.RED), generationSpeed, car.maxSpeed, car)
            )
        )
    }

    /** Verifies that stale server sources are deleted and edited sources created per field. */
    @Test
    fun syncSources_replacesServerSourcesOnMatchingFields() = suspendTest {
        installFakeServer()
        val level = levelWithSource(x = 0.0, y = 0.0)

        val outcome = WorldEditorServerSourceSync.syncSources(WORLD_ID, level)

        assertNull(outcome.errorMessage)
        assertEquals(listOf(FIELD_A to "quelle-alt"), deletedSources)
        assertEquals(listOf(FIELD_A, FIELD_B), createdSources.map { it.first })
        assertTrue(createdSources.all { (fieldId, request) -> request.fieldId == fieldId })
    }

    /**
     * Verifies that coordinates are sent world-globally: the same top-edge source lands at the
     * field origin plus its in-field position — the live server rejects field-local values.
     */
    @Test
    fun syncSources_translatesCoordinatesIntoWorldSpace() = suspendTest {
        installFakeServer()
        val level = levelWithSource(x = GameAreaScale.GAME_AREA_W_METER / 2.0, y = 0.0)

        val outcome = WorldEditorServerSourceSync.syncSources(WORLD_ID, level)

        assertNull(outcome.errorMessage)
        assertEquals(
            listOf(FIELD_A to CoordinateDTO(50, 0), FIELD_B to CoordinateDTO(150, 0)),
            createdSources.map { it.first to it.second.coordinate }
        )
    }

    /** Verifies that level meters are scaled into the server's field grid. */
    @Test
    fun syncSources_scalesLevelCoordinatesIntoFieldGrid() = suspendTest {
        installFakeServer()
        val level = levelWithSource(
            x = GameAreaScale.GAME_AREA_W_METER / 2.0,
            y = GameAreaScale.GAME_AREA_H_METER
        )

        WorldEditorServerSourceSync.syncSources(WORLD_ID, level)

        assertEquals(
            listOf(FIELD_A to CoordinateDTO(50, 100), FIELD_B to CoordinateDTO(150, 100)),
            createdSources.map { it.first to it.second.coordinate }
        )
    }

    /** Verifies that the configuration interval carries over as the server spawn interval. */
    @Test
    fun syncSources_usesConfigurationSpawnInterval() = suspendTest {
        installFakeServer()
        val level = levelWithSource(x = 0.0, y = 0.0, generationSpeed = 4.0)

        WorldEditorServerSourceSync.syncSources(WORLD_ID, level)

        assertEquals(4.0, createdSources.first().second.spawnInterval)
    }

    /** Verifies that a failing server call surfaces as an error with the rejected source. */
    @Test
    fun syncSources_reportsRejectedSourcesInTheOutcome() = suspendTest {
        installFakeServer()
        WorldEditorServerSourceSync.createSourceCall = { _, _, _ ->
            throw IOException("Quelle abgelehnt")
        }
        val level = levelWithSource(0.0, 0.0)
        val source = level.nodes.filterIsInstance<SourceNode>().single()

        val outcome = WorldEditorServerSourceSync.syncSources(WORLD_ID, level)

        assertNotNull(outcome.errorMessage)
        assertTrue("Quelle abgelehnt" in outcome.errorMessage!!)
        assertEquals(listOf(source), outcome.rejectedSources)
    }

    /** Verifies that one rejected source does not prevent the remaining sources from syncing. */
    @Test
    fun syncSources_skipsRejectedSourceAndTransfersTheRest() = suspendTest {
        installFakeServer()
        val level = levelWithSource(x = 0.0, y = 0.0)
        level.addNode(sourceAt(GameAreaScale.GAME_AREA_W_METER, GameAreaScale.GAME_AREA_H_METER))
        WorldEditorServerSourceSync.createSourceCall = { _, fieldId, request ->
            if (request.coordinate.y == 0) {
                throw IOException("Quelle abgelehnt")
            }
            createdSources += fieldId to request
        }

        val outcome = WorldEditorServerSourceSync.syncSources(WORLD_ID, level)

        assertNotNull(outcome.errorMessage, "rejected source must be reported")
        assertEquals(1, outcome.rejectedSources.size)
        assertEquals(
            listOf(FIELD_A to CoordinateDTO(100, 100), FIELD_B to CoordinateDTO(200, 100)),
            createdSources.map { it.first to it.second.coordinate }
        )
    }

    /** Verifies that near-edge sources are snapped onto the exact field edge. */
    @Test
    fun syncSources_snapsNearEdgeSourcesOntoTheEdge() = suspendTest {
        installFakeServer()
        val level = levelWithSource(
            x = 0.05 * GameAreaScale.GAME_AREA_W_METER,
            y = GameAreaScale.GAME_AREA_H_METER / 2.0
        )

        WorldEditorServerSourceSync.syncSources(WORLD_ID, level)

        assertEquals(CoordinateDTO(0, 50), createdSources.first().second.coordinate)
    }

    /**
     * Verifies that a left-border source is only created on fields whose left edge is an outer
     * world edge — the server rejects spawners on inner field borders with HTTP 400.
     */
    @Test
    fun syncSources_sendsEdgeSourcesOnlyToMatchingOuterEdgeFields() = suspendTest {
        installFakeServer()
        val level = levelWithSource(x = 0.0, y = GameAreaScale.GAME_AREA_H_METER / 2.0)

        val outcome = WorldEditorServerSourceSync.syncSources(WORLD_ID, level)

        assertNull(outcome.errorMessage)
        assertEquals(listOf(FIELD_A), createdSources.map { it.first })
        assertEquals(CoordinateDTO(0, 50), createdSources.single().second.coordinate)
    }

    /** Verifies that sources away from the level border are skipped without an error. */
    @Test
    fun syncSources_skipsCenterSourcesWithoutError() = suspendTest {
        installFakeServer()
        val level = levelWithSource(
            x = GameAreaScale.GAME_AREA_W_METER / 2.0,
            y = GameAreaScale.GAME_AREA_H_METER / 2.0
        )

        val outcome = WorldEditorServerSourceSync.syncSources(WORLD_ID, level)

        assertNull(outcome.errorMessage)
        assertTrue(createdSources.isEmpty())
    }

    /** Verifies that the spawn interval never falls below the server's minimum of 0.1 seconds. */
    @Test
    fun syncSources_clampsSpawnIntervalToServerMinimum() = suspendTest {
        installFakeServer()
        val level = levelWithSource(x = 0.0, y = 0.0, generationSpeed = 0.05)

        WorldEditorServerSourceSync.syncSources(WORLD_ID, level)

        assertEquals(0.1, createdSources.first().second.spawnInterval)
    }
}
