package worldeditortests

import domain.Color
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street
import domain.TargetNode
import domain.WeatherCondition
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import network.WorldEditorNodeKind
import worldeditor.WorldEditorDefaults
import worldeditor.WorldEditorSession
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Builds a minimal level with one source, one target and one street. */
internal fun sampleLevel(): Level {
    val car = WorldEditorDefaults.vehicleTypes.first { it.name == "Auto" }
    val red = Color("Rot", Colors.RED)
    val source = SourceNode(
        position = Point(10.0, 20.0),
        elevation = 0,
        orientation = Angle.ZERO,
        configurations = listOf(SourceNodeConfiguration(red, 1.0, car.maxSpeed, car))
    )
    val target = TargetNode(
        position = Point(90.0, 20.0),
        elevation = 0,
        orientation = Angle.ZERO,
        color = red
    )
    val level = Level("Sample", 1, weatherCondition = WeatherCondition.DRY)
    level.addNode(source)
    level.addNode(target)
    level.addStreet(Street(source, target, listOf(source.position, Point(50.0, 30.0), target.position)))
    return level
}

/** Creates an empty editor session for focused add/toggle tests. */
internal fun emptySession(): WorldEditorSession =
    WorldEditorSession.fromLevel(
        worldId = null,
        worldName = "Neue Welt",
        templateLevelId = 1,
        isNewWorld = true,
        sourceLevel = Level("Empty", 1)
    )

/** Creates an empty session with one selected source node. */
internal fun sessionWithSourceNode(): WorldEditorSession =
    emptySession().also { session ->
        assertNotNull(session.addNode(WorldEditorNodeKind.SOURCE, 30.0, 30.0))
    }

/** Creates an empty session with one selected sink node. */
internal fun sessionWithSinkNode(): WorldEditorSession =
    emptySession().also { session ->
        assertNotNull(session.addNode(WorldEditorNodeKind.TARGET, 30.0, 30.0))
    }

/** Creates a source session where one added color replaces the initial color. */
internal fun sourceSessionAfterReplacingColor(addedColor: String, removedColor: String): WorldEditorSession {
    val session = sessionWithSourceNode()
    assertTrue(session.toggleSelectedColor(addedColor))
    assertTrue(session.toggleSelectedColor(removedColor))
    return session
}

/** Creates a source session where one added vehicle type replaces the initial vehicle type. */
internal fun sourceSessionAfterReplacingVehicleType(addedType: String, removedType: String): WorldEditorSession {
    val session = sessionWithSourceNode()
    assertTrue(session.toggleSelectedVehicleType(addedType))
    assertTrue(session.toggleSelectedVehicleType(removedType))
    return session
}

/** Asserts the source configurations and selected node metadata use the expected colors. */
internal fun WorldEditorSession.assertSourceColors(vararg expectedNames: String): SourceNode {
    val sourceNode = singleSourceNode()
    assertEquals(expectedNames.toList(), sourceNode.configurations.map { it.color.name })
    assertSelectedColors(*expectedNames)
    return sourceNode
}

/** Asserts the source configurations and selected node metadata use the expected vehicle types. */
internal fun WorldEditorSession.assertSourceVehicleTypes(vararg expectedNames: String): SourceNode {
    val sourceNode = singleSourceNode()
    assertEquals(expectedNames.toList(), sourceNode.configurations.map { it.vehicleType.name })
    assertSelectedVehicleTypes(*expectedNames)
    return sourceNode
}

/** Asserts the color metadata of the currently selected editor node. */
internal fun WorldEditorSession.assertSelectedColors(vararg expectedNames: String) {
    assertEquals(expectedNames.toList(), selectedNode?.colorNames)
}

/** Asserts the vehicle-type metadata of the currently selected editor node. */
internal fun WorldEditorSession.assertSelectedVehicleTypes(vararg expectedNames: String) {
    assertEquals(expectedNames.toList(), selectedNode?.vehicleTypeNames)
}

/** Asserts all color-and-vehicle-type combinations configured on the single source node. */
internal fun WorldEditorSession.assertSourceConfigurationPairs(
    vararg expectedPairs: Pair<String, String>
): SourceNode {
    val sourceNode = singleSourceNode()
    val pairs = sourceNode.configurations.map { it.color.name to it.vehicleType.name }.toSet()
    assertEquals(expectedPairs.toSet(), pairs)
    return sourceNode
}

/** Asserts all source configurations obey their vehicle type's maximum speed. */
internal fun SourceNode.assertInitialSpeedsWithinTypeLimits() {
    assertTrue(configurations.all { it.initialVehicleSpeed <= it.vehicleType.maxSpeed })
}

/** Returns the single source node from a focused editor-session test. */
internal fun WorldEditorSession.singleSourceNode(): SourceNode =
    level.nodes.filterIsInstance<SourceNode>().single()
