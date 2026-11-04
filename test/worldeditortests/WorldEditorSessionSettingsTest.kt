package worldeditortests

import domain.Level
import domain.SourceNode
import domain.TargetNode
import domain.WeatherCondition
import network.WorldEditorNodeKind
import worldeditor.WorldEditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests the color and vehicle-type toggles of the world-editor session.
 *
 * Sources always carry one configuration per color-and-type combination; sinks track one color
 * and their vehicle-type list as editor metadata.
 */
class WorldEditorSessionSettingsTest {

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

    /** Verifies that toggling a second color onto a source adds one configuration per color. */
    @Test
    fun toggleSelectedColor_addsOneSourceConfigurationPerColor() {
        val session = sourceOnlySession()

        assertTrue(session.toggleSelectedColor("Blau"))

        val sourceNode = session.level.nodes.filterIsInstance<SourceNode>().single()
        assertEquals(listOf("Rot", "Blau"), sourceNode.configurations.map { it.color.name })
        assertEquals(listOf("Rot", "Blau"), session.selectedNode?.colorNames)
        assertTrue(sourceNode.configurations.all { it.vehicleType.name == "Auto" })
    }

    /** Verifies that toggling an already assigned color removes it again. */
    @Test
    fun toggleSelectedColor_removesPreviouslyAssignedColor() {
        val session = sourceOnlySession()
        assertTrue(session.toggleSelectedColor("Blau"))

        assertTrue(session.toggleSelectedColor("Rot"))

        val sourceNode = session.level.nodes.filterIsInstance<SourceNode>().single()
        assertEquals(listOf("Blau"), sourceNode.configurations.map { it.color.name })
        assertEquals(listOf("Blau"), session.selectedNode?.colorNames)
    }

    /** Verifies that the last remaining color cannot be removed, so no neutral nodes can exist. */
    @Test
    fun toggleSelectedColor_keepsAtLeastOneColor() {
        val session = sourceOnlySession()

        assertFalse(session.toggleSelectedColor("Rot"))

        assertEquals(listOf("Rot"), session.selectedNode?.colorNames)
        assertEquals("Mindestens eine Farbe muss ausgewaehlt bleiben.", session.errorMessage)
    }

    /** Verifies that selecting a sink color replaces the previous sink color. */
    @Test
    fun toggleSelectedColor_onSink_replacesColorAndSyncsDomainColor() {
        val session = emptyEditorSession()
        assertNotNull(session.addNode(WorldEditorNodeKind.TARGET, 30.0, 30.0))
        assertTrue(session.toggleSelectedColor("Blau"))

        assertTrue(session.toggleSelectedColor("Rot"))

        val targetNode = session.level.nodes.filterIsInstance<TargetNode>().single()
        assertEquals(listOf("Rot"), session.selectedNode?.colorNames)
        assertEquals("Rot", targetNode.color.name)
    }

    /** Verifies that toggling a color adds one configuration per already assigned vehicle type. */
    @Test
    fun toggleSelectedColor_addsConfigurationsForEveryAssignedVehicleType() {
        val session = sourceOnlySession()
        assertTrue(session.toggleSelectedVehicleType("Lastwagen"))

        assertTrue(session.toggleSelectedColor("Blau"))

        val sourceNode = session.level.nodes.filterIsInstance<SourceNode>().single()
        val pairs = sourceNode.configurations.map { it.color.name to it.vehicleType.name }.toSet()
        assertEquals(
            setOf("Rot" to "Auto", "Rot" to "Lastwagen", "Blau" to "Auto", "Blau" to "Lastwagen"),
            pairs
        )
    }

    /** Verifies that toggling a second vehicle type adds one configuration per color and type. */
    @Test
    fun toggleSelectedVehicleType_addsConfigurationsForEveryColor() {
        val session = sourceOnlySession()
        assertTrue(session.toggleSelectedColor("Blau"))

        assertTrue(session.toggleSelectedVehicleType("Lastwagen"))

        val sourceNode = session.level.nodes.filterIsInstance<SourceNode>().single()
        val pairs = sourceNode.configurations.map { it.color.name to it.vehicleType.name }.toSet()
        assertEquals(
            setOf("Rot" to "Auto", "Blau" to "Auto", "Rot" to "Lastwagen", "Blau" to "Lastwagen"),
            pairs
        )
        assertTrue(sourceNode.configurations.all { it.initialVehicleSpeed <= it.vehicleType.maxSpeed })
        assertEquals(listOf("Auto", "Lastwagen"), session.selectedNode?.vehicleTypeNames)
    }

    /**
     * Verifies the vehicle-type toggle lifecycle on a source: an assigned type can be removed
     * again, and removing the last remaining type is rejected with a message.
     */
    @Test
    fun toggleSelectedVehicleType_removesTypeButKeepsLastType() {
        val session = sourceOnlySession()
        assertTrue(session.toggleSelectedVehicleType("Lastwagen"))

        assertTrue(session.toggleSelectedVehicleType("Auto"))
        val sourceNode = session.level.nodes.filterIsInstance<SourceNode>().single()
        assertEquals(listOf("Lastwagen"), sourceNode.configurations.map { it.vehicleType.name })

        assertFalse(session.toggleSelectedVehicleType("Lastwagen"))
        assertEquals(listOf("Lastwagen"), session.selectedNode?.vehicleTypeNames)
        assertEquals("Mindestens ein Fahrzeugtyp muss ausgewaehlt bleiben.", session.errorMessage)
    }

    /** Verifies that a sink tracks multiple vehicle types in its editor metadata. */
    @Test
    fun toggleSelectedVehicleType_onSink_tracksTypeList() {
        val session = emptyEditorSession()
        assertNotNull(session.addNode(WorldEditorNodeKind.TARGET, 30.0, 30.0))
        assertTrue(session.toggleSelectedVehicleType("Motorrad"))

        assertTrue(session.toggleSelectedVehicleType("Auto"))

        assertEquals(listOf("Motorrad"), session.selectedNode?.vehicleTypeNames)
    }
}
