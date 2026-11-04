import domain.Color
import domain.Level
import simulation.Simulation
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.Street
import domain.TargetNode
import domain.VehicleType
import korlibs.image.color.Colors
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests that each source configuration spawns vehicles with that configuration's colour only.
 */
class MultiColorVehicleTest {

  /**
   * Tests that the simulation spawns vehicles with each configuration's colour only.
   */
  @Test
  fun simulationSpawnUsesEachConfigurationColor() {
    val level = Level("MultiColor", 1)
    val red = Color("Rot", Colors.RED)
    val blue = Color("Blau", Colors.BLUE)
    val auto = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)
    val configs =
        listOf(
            SourceNodeConfiguration(red, 30.0, 50.0, auto),
            SourceNodeConfiguration(blue, 30.0, 50.0, auto),
        )
    val source = SourceNode(Point(0, 0), 0, Angle.fromDegrees(0.0), configs)
    val target = TargetNode(Point(800, 0), 0, Angle.fromDegrees(0.0), red)
    val blueTarget = TargetNode(Point(800, 100), 0, Angle.fromDegrees(0.0), blue)
    level.addNode(source)
    level.addNode(target)
    level.addNode(blueTarget)
    level.addStreet(Street(source, target, listOf(Point(0, 0), Point(800, 0))))
    level.addStreet(Street(source, blueTarget, listOf(Point(0, 0), Point(800, 100))))
    level.validate()

    Simulation(level).tick(0.0)

    assertEquals(2, level.vehicles.size)
    val spawnedColors = level.vehicles.map { it.color.name }.toSet()
    assertEquals(setOf("Rot", "Blau"), spawnedColors)
    level.vehicles.forEach { vehicle ->
      assertEquals(1, vehicle.sourceNode.configurations.count { it.color.name == vehicle.color.name })
    }
  }

  /**
   * Tests that the source configurations can be replaced at runtime.
   */
  @Test
  fun sourceConfigurationsCanBeReplacedAtRuntime() {
    val red = Color("Rot", Colors.RED)
    val blue = Color("Blau", Colors.BLUE)
    val auto = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0)
    val source =
        SourceNode(
            Point(0, 0),
            0,
            Angle.fromDegrees(0.0),
            listOf(SourceNodeConfiguration(red, 10.0, 50.0, auto)),
        )

    source.configurations =
        listOf(
            SourceNodeConfiguration(red, 5.0, 60.0, auto),
            SourceNodeConfiguration(blue, 5.0, 60.0, auto),
        )

    assertEquals(2, source.configurations.size)
    assertEquals(5.0, source.configurations.first().generationSpeed)
    assertEquals(60.0, source.configurations.first().initialVehicleSpeed)
    assertEquals("Blau", source.configurations[1].color.name)
  }
}
