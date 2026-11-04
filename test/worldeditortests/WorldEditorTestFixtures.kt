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
import network.WorldEditorNodeDTO
import network.WorldEditorNodeKind
import network.WorldEditorStateDTO
import worldeditor.WorldEditorDefaults
import worldeditor.WorldEditorSession

/**
 * Shared fixtures for the world-editor test suites, so setup code lives in one place.
 */

/** Builds a level with one red car source connected to one red target via a single street. */
internal fun sampleEditorLevel(): Level {
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

/** Creates a new-world session on an empty level. */
internal fun emptyEditorSession(): WorldEditorSession =
    WorldEditorSession.fromLevel(
        worldId = null,
        worldName = "Neue Welt",
        templateLevelId = 1,
        isNewWorld = true,
        sourceLevel = Level("Empty", 1)
    )

/** Creates an edit session for [sampleEditorLevel]. */
internal fun sampleLevelSession(
    worldId: String? = "world-1",
    worldName: String = "Bestehend"
): WorldEditorSession =
    WorldEditorSession.fromLevel(
        worldId = worldId,
        worldName = worldName,
        templateLevelId = 1,
        isNewWorld = false,
        sourceLevel = sampleEditorLevel()
    )

/** Creates a new-world session with one selected source placed at (30, 30). */
internal fun sourceOnlySession(): WorldEditorSession =
    emptyEditorSession().also { it.addNode(WorldEditorNodeKind.SOURCE, 30.0, 30.0) }

/** Builds one editor-node DTO with car defaults. */
internal fun editorNodeDto(
    id: String,
    kind: WorldEditorNodeKind,
    x: Double,
    y: Double,
    colorNames: List<String>,
    vehicleTypeNames: List<String> = listOf("Auto")
): WorldEditorNodeDTO = WorldEditorNodeDTO(id, kind, x, y, colorNames, vehicleTypeNames)

/** Builds a server editor state around an already serialized level. */
internal fun editorState(
    worldId: String,
    name: String,
    levelJson: String,
    nodes: List<WorldEditorNodeDTO>
): WorldEditorStateDTO = WorldEditorStateDTO(worldId, name, 1, levelJson, nodes)
