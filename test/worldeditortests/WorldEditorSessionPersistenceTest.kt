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
import persistence.LevelSerializer
import worldeditor.WorldEditorDefaults
import worldeditor.WorldEditorNodeSettings
import worldeditor.WorldEditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests saving, restoring and validating world-editor sessions.
 */
class WorldEditorSessionPersistenceTest {

    /**
     * Verifies that restoring a server state keeps the source configurations from the level JSON
     * (the authoritative store for per-configuration tuning) while sinks keep only one selected
     * color from the editor-node DTOs.
     */
    @Test
    fun fromServerState_keepsLevelConfigurationsAndRestoresSingleSinkColor() {
        val state = editorState(
            worldId = "world-1",
            name = "Bunte Welt",
            levelJson = LevelSerializer.toJsonString(sampleEditorLevel()),
            nodes = listOf(
                editorNodeDto("source-1", WorldEditorNodeKind.SOURCE, 10.0, 20.0, listOf("Rot", "Blau")),
                editorNodeDto(
                    "target-1", WorldEditorNodeKind.TARGET, 90.0, 20.0,
                    colorNames = listOf("Gelb", "Gruen"),
                    vehicleTypeNames = listOf("Auto", "Motorrad")
                )
            )
        )

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
     * Verifies that per-configuration tuning (e.g. spawn rates edited through the source
     * configuration menu) survives a save-and-restore roundtrip via the level JSON.
     */
    @Test
    fun saveAndRestore_preservesPerConfigurationTuning() {
        val session = sourceOnlySession()
        assertTrue(session.toggleSelectedColor("Blau"))
        assertNotNull(session.addNode(WorldEditorNodeKind.TARGET, 60.0, 30.0))
        val sourceNode = session.level.nodes.filterIsInstance<SourceNode>().single()
        sourceNode.configurations = sourceNode.configurations.mapIndexed { index, cfg ->
            SourceNodeConfiguration(cfg.color, if (index == 0) 2.0 else 6.0, cfg.initialVehicleSpeed, cfg.vehicleType)
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
        val first = SourceNodeConfiguration(Color("Rot", Colors.RED), 1.0, car.maxSpeed, car)
        val duplicate = SourceNodeConfiguration(Color("rot", Colors.RED), 9.0, car.maxSpeed, car)

        val deduped = WorldEditorNodeSettings.dedupedConfigurations(listOf(first, duplicate))

        assertEquals(listOf(first), deduped)
    }

    /** Verifies that a source stripped of every color fails validation before saving. */
    @Test
    fun saveRequestOrNull_rejectsSourceWithoutAnyColor() {
        val session = sampleLevelSession(worldId = "world-colorless", worldName = "Alte Welt")
        session.level.nodes.filterIsInstance<SourceNode>().single().configurations = emptyList()

        assertNull(session.saveRequestOrNull())
        assertEquals("Jede Quelle/Senke braucht mindestens eine Farbe.", session.errorMessage)
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
        val session = sampleLevelSession(worldId = "world-overlap", worldName = "Alte Welt")
        val sourcePosition = session.level.nodes.filterIsInstance<SourceNode>().single().position
        session.level.nodes.filterIsInstance<TargetNode>().single().position = sourcePosition

        assertNull(session.saveRequestOrNull())
        assertEquals("Quellen/Senken brauchen mindestens 12m Abstand.", session.errorMessage)
    }

    /** Verifies that valid inner-field source and sink positions can be saved. */
    @Test
    fun saveRequestOrNull_allowsNodesAwayFromFieldEdge() {
        val session = sampleLevelSession(worldId = "world-inner", worldName = "Alte Welt")
        session.level.nodes.filterIsInstance<SourceNode>().single().position = Point(50.0, 50.0)

        assertNotNull(session.saveRequestOrNull())
        assertNull(session.errorMessage)
    }
}
