package worldeditortests

import domain.TargetNode
import persistence.WorldTemplate
import worldeditor.WorldEditorDefaults
import worldeditor.WorldEditorSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests color and vehicle-type assignment behavior of [WorldEditorSession]. */
class WorldEditorSessionAssignmentTest {

    /** Verifies that the standard template's green nodes are accepted by the editor palette. */
    @Test
    fun fromTemplate_acceptsStandardTemplateGreenColorAlias() {
        val session = WorldEditorSession.fromTemplate(
            templateLevelId = WorldTemplate.TEMPLATE_LEVEL_NUMBER,
            templateLevel = WorldTemplate.createBlankWorld("Neue Standard Welt")
        )
        val greenSource = session.editorNodes.first { it.id == "source-3" }
        val greenTarget = session.editorNodes.first { it.id == "target-3" }

        assertTrue(greenSource.colorNames.any { WorldEditorDefaults.colorNamesMatch("Gruen", it) })
        assertTrue(greenTarget.colorNames.any { WorldEditorDefaults.colorNamesMatch("Gruen", it) })
        assertNotNull(WorldEditorDefaults.colorByName("gr\u00fcn"))

        session.selectNode(greenSource.id)
        assertFalse(session.toggleSelectedColor("Grün"))
        assertEquals(listOf("Gruen"), session.selectedNode?.colorNames)
        assertEquals("Mindestens eine Farbe muss ausgewaehlt bleiben.", session.errorMessage)

        assertNotNull(session.saveRequestOrNull())
        assertNull(session.errorMessage)
    }

    /** Verifies that toggling a second color onto a source adds one configuration per color. */
    @Test
    fun toggleSelectedColor_addsOneSourceConfigurationPerColor() {
        val session = sessionWithSourceNode()

        assertTrue(session.toggleSelectedColor("Blau"))

        val sourceNode = session.assertSourceColors("Rot", "Blau")
        assertTrue(sourceNode.configurations.all { it.vehicleType.name == "Auto" })
    }

    /** Verifies that toggling an already assigned color removes it again. */
    @Test
    fun toggleSelectedColor_removesPreviouslyAssignedColor() {
        val session = sourceSessionAfterReplacingColor(addedColor = "Blau", removedColor = "Rot")

        session.assertSourceColors("Blau")
    }

    /** Verifies that the last remaining color cannot be removed, so no neutral nodes can exist. */
    @Test
    fun toggleSelectedColor_keepsAtLeastOneColor() {
        val session = sessionWithSourceNode()

        assertFalse(session.toggleSelectedColor("Rot"))

        session.assertSelectedColors("Rot")
        assertEquals("Mindestens eine Farbe muss ausgewaehlt bleiben.", session.errorMessage)
    }

    /** Verifies that selecting a sink color replaces the previous sink color. */
    @Test
    fun toggleSelectedColor_onSink_replacesColorAndSyncsDomainColor() {
        val session = sessionWithSinkNode()
        assertTrue(session.toggleSelectedColor("Blau"))

        assertTrue(session.toggleSelectedColor("Rot"))

        val targetNode = session.level.nodes.filterIsInstance<TargetNode>().single()
        assertEquals(listOf("Rot"), session.selectedNode?.colorNames)
        assertEquals("Rot", targetNode.color.name)
    }

    /** Verifies that toggling a second vehicle type adds one configuration per color and type. */
    @Test
    fun toggleSelectedVehicleType_addsConfigurationsForEveryColor() {
        val session = sessionWithSourceNode()
        assertTrue(session.toggleSelectedColor("Blau"))

        assertTrue(session.toggleSelectedVehicleType("Lastwagen"))

        val sourceNode = session.assertSourceConfigurationPairs(
            "Rot" to "Auto",
            "Blau" to "Auto",
            "Rot" to "Lastwagen",
            "Blau" to "Lastwagen"
        )
        sourceNode.assertInitialSpeedsWithinTypeLimits()
        session.assertSelectedVehicleTypes("Auto", "Lastwagen")
    }

    /** Verifies that toggling an assigned vehicle type removes its configurations again. */
    @Test
    fun toggleSelectedVehicleType_removesPreviouslyAssignedType() {
        val session = sourceSessionAfterReplacingVehicleType(addedType = "Lastwagen", removedType = "Auto")

        session.assertSourceVehicleTypes("Lastwagen")
    }

    /** Verifies that the last remaining vehicle type cannot be removed. */
    @Test
    fun toggleSelectedVehicleType_keepsAtLeastOneType() {
        val session = sessionWithSourceNode()

        assertFalse(session.toggleSelectedVehicleType("Auto"))

        session.assertSelectedVehicleTypes("Auto")
        assertEquals("Mindestens ein Fahrzeugtyp muss ausgewaehlt bleiben.", session.errorMessage)
    }

    /** Verifies that a sink tracks multiple vehicle types in its editor metadata. */
    @Test
    fun toggleSelectedVehicleType_onSink_tracksTypeList() {
        val session = sessionWithSinkNode()
        assertTrue(session.toggleSelectedVehicleType("Motorrad"))

        assertTrue(session.toggleSelectedVehicleType("Auto"))

        assertEquals(listOf("Motorrad"), session.selectedNode?.vehicleTypeNames)
    }

    /** Verifies that toggling a color adds one configuration per already assigned vehicle type. */
    @Test
    fun toggleSelectedColor_addsConfigurationsForEveryAssignedVehicleType() {
        val session = sessionWithSourceNode()
        assertTrue(session.toggleSelectedVehicleType("Lastwagen"))

        assertTrue(session.toggleSelectedColor("Blau"))

        session.assertSourceConfigurationPairs(
            "Rot" to "Auto",
            "Rot" to "Lastwagen",
            "Blau" to "Auto",
            "Blau" to "Lastwagen"
        )
    }
}
