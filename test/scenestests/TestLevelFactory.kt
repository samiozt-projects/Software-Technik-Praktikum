package scenestests

import domain.Color
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street
import domain.TargetNode
import domain.VehicleType
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point

/**
 * Builds a minimal level that validates as [domain.NetworkState.COMPLETE]: one red source → red target.
 *
 * @param name level name embedded in the returned [Level].
 * @return a complete, valid level ready to load into [domain.GameState].
 */
fun minimalCompleteLevel(name: String): Level {
    val car = VehicleType("Car", 50.0, 4.0, 0.8, 3.0)
    val red = Color("Rot", Colors.RED)
    val source = SourceNode(
        Point(100, 100), 0, Angle.ZERO,
        listOf(SourceNodeConfiguration(red, 1.0, 10.0, car, vehiclesToSpawn = 1))
    )
    val target = TargetNode(Point(500, 100), 0, Angle.ZERO, red)
    val level = Level(name, 1)
    level.addNode(source)
    level.addNode(target)
    level.addStreet(Street(source, target, listOf(Point(100, 100), Point(500, 100))))
    return level
}
