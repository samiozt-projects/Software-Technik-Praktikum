package scenestests

import domain.Color
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.VehicleType
import korlibs.image.color.RGBA
import korlibs.math.geom.Angle
import korlibs.math.geom.Point
import scenes.ConfigurationScene
import scenes.ConfigurationGameplayTarget
import scenes.VehicleConfigurationScene
import scenes.configurationGameplayTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Covers issue #162: vehicle parameters must be editable in every level, not only the tutorial.
 *
 * The configuration menu only persists edits when its cards are bound to the level's own
 * [VehicleType] instances (every source configuration shares one instance per name). These tests
 * exercise [ConfigurationScene.resolveDisplayVehicleTypes] plus the actual edit path
 * [VehicleConfigurationScene.applyInputValues] to verify each acceptance criterion.
 */
class ConfigurationSceneVehicleTypeTest {

    private fun car() = VehicleType("Auto", 80.0, 4.0, 0.7, 3.0, "images/car.png")

    /** The bundled levels name the truck "Lastwagen" (not "LKW"/"Truck"). */
    private fun truckLastwagen() = VehicleType("Lastwagen", 60.0, 12.0, 0.7, 1.5, "images/truck.png")

    private fun motorcycle() = VehicleType("Motorrad", 100.0, 2.0, 0.8, 4.5, "images/motorcycle.png")

    /** Configuration back navigation must not depend on stale pause-menu scene history. */
    @Test
    fun configurationGameplayTarget_resolvesWithoutSceneHistory() {
        assertEquals(ConfigurationGameplayTarget.LEVEL_SCENE, configurationGameplayTarget(tutorialActive = false))
        assertEquals(ConfigurationGameplayTarget.TUTORIAL_SCENE, configurationGameplayTarget(tutorialActive = true))
    }

    /** Builds a non-tutorial level whose single source spawns all given vehicle types. */
    private fun levelWithVehicleTypes(vararg types: VehicleType): Level {
        val level = Level(name = "Kreuzung", number = 3)
        val red = Color("Rot", RGBA(255, 0, 0, 255))
        val configs = types.map {
            SourceNodeConfiguration(
                color = red,
                generationSpeed = 2.0,
                initialVehicleSpeed = 30.0,
                vehicleType = it
            )
        }
        level.addNode(
            SourceNode(
                position = Point(10.0, 10.0),
                elevation = 0,
                orientation = Angle.fromDegrees(0.0),
                configurations = configs
            )
        )
        return level
    }

    /**
     * Resolving the editable vehicle-type cards must return the level's own truck instance when the
     * truck is named "Lastwagen", rather than a freshly created default placeholder. Only then does
     * editing the truck card mutate the very instance the simulation reads.
     */
    @Test
    fun truckNamedLastwagenResolvesToTheLevelInstanceNotADefault() {
        val levelTruck = truckLastwagen()
        val resolved = ConfigurationScene.resolveDisplayVehicleTypes(
            listOf(car(), levelTruck, motorcycle())
        )

        // The truck card must be the level's own instance so edits made through it persist.
        assertSame(levelTruck, resolved[1])
        assertEquals(3, resolved.size) // All three vehicle types should be present
    }

    /**
     * The car and motorcycle cards must bind to the level's own vehicle-type instances as well, so
     * that every standard vehicle type displayed in the configuration menu can be edited and the
     * change reaches the level's data.
     */
    @Test
    fun carAndMotorcycleAlsoResolveToLevelInstances() {
        val levelCar = car()
        val levelMotorcycle = motorcycle()
        val resolved = ConfigurationScene.resolveDisplayVehicleTypes(
            listOf(levelCar, truckLastwagen(), levelMotorcycle)
        )

        assertEquals(3, resolved.size) // All three vehicle types should be present
        assertSame(levelCar, resolved[0])
        assertSame(levelMotorcycle, resolved[2])
    }

    /**
     * Editing a vehicle type's maximum speed through the configuration menu must be written back to
     * the level's shared vehicle-type instance, so the new value persists for any level (not just
     * the tutorial) regardless of the truck's name.
     */
    @Test
    fun maximumSpeedIsEditableOutsideTheTutorialLevel() {
        val level = levelWithVehicleTypes(car(), truckLastwagen(), motorcycle())
        val truckCard = ConfigurationScene.resolveDisplayVehicleTypes(level.vehicleTypes)[1]

        VehicleConfigurationScene.applyInputValues(
            truckCard,
            maxSpeedText = "55.0",
            accelerationText = truckCard.acceleration.toString(),
            adhesionCoefficientText = truckCard.adhesionCoefficient.toString()
        )

        val persisted = level.vehicleTypes.first { it.name == "Lastwagen" }
        assertEquals(55.0, persisted.maxSpeed)
    }

    /**
     * Editing a vehicle type's acceleration through the configuration menu must be written back to
     * the level's shared vehicle-type instance, so the new value persists for any level rather than
     * being discarded.
     */
    @Test
    fun accelerationIsEditableOutsideTheTutorialLevel() {
        val level = levelWithVehicleTypes(car(), truckLastwagen(), motorcycle())
        val truckCard = ConfigurationScene.resolveDisplayVehicleTypes(level.vehicleTypes)[1]

        VehicleConfigurationScene.applyInputValues(
            truckCard,
            maxSpeedText = truckCard.maxSpeed.toString(),
            accelerationText = "2.75",
            adhesionCoefficientText = truckCard.adhesionCoefficient.toString()
        )

        val persisted = level.vehicleTypes.first { it.name == "Lastwagen" }
        assertEquals(2.75, persisted.acceleration)
    }

    /**
     * Vehicle parameter edits must remain in place after the configuration menu is closed and
     * reopened: resolving the cards a second time returns the same instance carrying the edited
     * values instead of resetting them to defaults.
     */
    @Test
    fun editedValuesRemainAfterReopeningConfigurationInIntersectionLevel() {
        val level = levelWithVehicleTypes(car(), truckLastwagen(), motorcycle())

        val firstOpen = ConfigurationScene.resolveDisplayVehicleTypes(level.vehicleTypes)
        VehicleConfigurationScene.applyInputValues(
            firstOpen[1],
            maxSpeedText = "48.0",
            accelerationText = "1.9",
            adhesionCoefficientText = "0.65"
        )

        // Reopening the configuration must keep the edited values instead of resetting them.
        val secondOpen = ConfigurationScene.resolveDisplayVehicleTypes(level.vehicleTypes)
        assertSame(firstOpen[1], secondOpen[1])
        assertEquals(48.0, secondOpen[1].maxSpeed)
        assertEquals(1.9, secondOpen[1].acceleration)
    }

    /**
     * Only vehicle types that exist in the level should be displayed in the configuration menu.
     * This ensures that users can only edit vehicle types that are actually used in the level.
     */
    @Test
    fun onlyExistingVehicleTypesAreDisplayed() {
        // Tutorial-like level: only a car type exists.
        val levelCar = car()
        val resolved = ConfigurationScene.resolveDisplayVehicleTypes(listOf(levelCar))

        // Only the car should be displayed, not truck or motorcycle
        assertEquals(1, resolved.size)
        assertSame(levelCar, resolved[0])
        assertEquals("Auto", resolved[0].name)
    }

    /**
     * When a level has no vehicle types, the configuration menu should show no vehicle type cards.
     */
    @Test
    fun emptyLevelShowsNoVehicleTypes() {
        val resolved = ConfigurationScene.resolveDisplayVehicleTypes(emptyList())
        
        // No vehicle types should be displayed
        assertEquals(0, resolved.size)
    }

    /**
     * Test that all three standard vehicle types are returned when present.
     */
    @Test
    fun returnsAllThreeVehicleTypesWhenPresent() {
        val allTypes = listOf(car(), truckLastwagen(), motorcycle())
        val result = ConfigurationScene.resolveDisplayVehicleTypes(allTypes)
        
        assertEquals(3, result.size, "Should return all three vehicle types")
        assertSame(allTypes[0], result[0], "Car should be the same instance")
        assertSame(allTypes[1], result[1], "Truck should be the same instance")
        assertSame(allTypes[2], result[2], "Motorcycle should be the same instance")
    }

    /**
     * Test that only car type is returned when only car exists.
     */
    @Test
    fun returnsOnlyCarWhenOnlyCarExists() {
        val onlyCar = listOf(car())
        val result = ConfigurationScene.resolveDisplayVehicleTypes(onlyCar)
        
        assertEquals(1, result.size, "Should return only car")
        assertSame(onlyCar[0], result[0], "Car should be the same instance")
    }

    /**
     * Test that only truck type is returned when only truck exists.
     */
    @Test
    fun returnsOnlyTruckWhenOnlyTruckExists() {
        val onlyTruck = listOf(truckLastwagen())
        val result = ConfigurationScene.resolveDisplayVehicleTypes(onlyTruck)
        
        assertEquals(1, result.size, "Should return only truck")
        assertSame(onlyTruck[0], result[0], "Truck should be the same instance")
    }

    /**
     * Test that only motorcycle type is returned when only motorcycle exists.
     */
    @Test
    fun returnsOnlyMotorcycleWhenOnlyMotorcycleExists() {
        val onlyMotorcycle = listOf(motorcycle())
        val result = ConfigurationScene.resolveDisplayVehicleTypes(onlyMotorcycle)
        
        assertEquals(1, result.size, "Should return only motorcycle")
        assertSame(onlyMotorcycle[0], result[0], "Motorcycle should be the same instance")
    }

    /**
     * Test that car and truck are returned when motorcycle is missing.
     */
    @Test
    fun returnsCarAndTruckWhenMotorcycleMissing() {
        val carAndTruck = listOf(car(), truckLastwagen())
        val result = ConfigurationScene.resolveDisplayVehicleTypes(carAndTruck)
        
        assertEquals(2, result.size, "Should return car and truck")
        assertSame(carAndTruck[0], result[0], "Car should be the same instance")
        assertSame(carAndTruck[1], result[1], "Truck should be the same instance")
    }

    /**
     * Test that car and motorcycle are returned when truck is missing.
     */
    @Test
    fun returnsCarAndMotorcycleWhenTruckMissing() {
        val carAndMotorcycle = listOf(car(), motorcycle())
        val result = ConfigurationScene.resolveDisplayVehicleTypes(carAndMotorcycle)
        
        assertEquals(2, result.size, "Should return car and motorcycle")
        assertSame(carAndMotorcycle[0], result[0], "Car should be the same instance")
        assertSame(carAndMotorcycle[1], result[1], "Motorcycle should be the same instance")
    }

    /**
     * Test that truck and motorcycle are returned when car is missing.
     */
    @Test
    fun returnsTruckAndMotorcycleWhenCarMissing() {
        val truckAndMotorcycle = listOf(truckLastwagen(), motorcycle())
        val result = ConfigurationScene.resolveDisplayVehicleTypes(truckAndMotorcycle)
        
        assertEquals(2, result.size, "Should return truck and motorcycle")
        assertSame(truckAndMotorcycle[0], result[0], "Truck should be the same instance")
        assertSame(truckAndMotorcycle[1], result[1], "Motorcycle should be the same instance")
    }

    /**
     * Test that different naming variations are handled correctly.
     */
    @Test
    fun handlesDifferentNamingVariations() {
        val carVariation = VehicleType("Car", 80.0, 4.0, 0.7, 3.0, "images/car.png")
        val truckVariation = VehicleType("Truck", 90.0, 6.0, 0.7, 3.0, "images/truck.png")
        val motorcycleVariation = VehicleType("Motorcycle", 100.0, 2.0, 0.8, 4.5, "images/motorcycle.png")
        
        val result = ConfigurationScene.resolveDisplayVehicleTypes(listOf(carVariation, truckVariation, motorcycleVariation))
        
        assertEquals(3, result.size, "Should handle different naming variations")
        assertSame(carVariation, result[0], "Car variation should be the same instance")
        assertSame(truckVariation, result[1], "Truck variation should be the same instance")
        assertSame(motorcycleVariation, result[2], "Motorcycle variation should be the same instance")
    }

    /**
     * Test that case-insensitive matching works correctly.
     */
    @Test
    fun handlesCaseInsensitiveMatching() {
        val mixedCaseCar = VehicleType("AUTO", 80.0, 4.0, 0.7, 3.0, "images/car.png")
        val mixedCaseTruck = VehicleType("lkw", 90.0, 6.0, 0.7, 3.0, "images/truck.png")
        val mixedCaseMotorcycle = VehicleType("MoToRrAd", 100.0, 2.0, 0.8, 4.5, "images/motorcycle.png")
        
        val result = ConfigurationScene.resolveDisplayVehicleTypes(listOf(mixedCaseCar, mixedCaseTruck, mixedCaseMotorcycle))
        
        assertEquals(3, result.size, "Should handle case-insensitive matching")
        assertSame(mixedCaseCar, result[0], "Mixed case car should be the same instance")
        assertSame(mixedCaseTruck, result[1], "Mixed case truck should be the same instance")
        assertSame(mixedCaseMotorcycle, result[2], "Mixed case motorcycle should be the same instance")
    }

    /**
     * Test that vehicle types are always returned in the correct order: car, truck, motorcycle.
     */
    @Test
    fun preservesCorrectOrderRegardlessOfInputOrder() {
        val unorderedTypes = listOf(truckLastwagen(), car(), motorcycle())
        val result = ConfigurationScene.resolveDisplayVehicleTypes(unorderedTypes)
        
        assertEquals(3, result.size, "Should return all three vehicle types")
        assertEquals("Auto", result[0].name, "Car should be first")
        assertEquals("Lastwagen", result[1].name, "Truck should be second")
        assertEquals("Motorrad", result[2].name, "Motorcycle should be third")
        assertSame(unorderedTypes[1], result[0], "Should find car regardless of input order")
        assertSame(unorderedTypes[0], result[1], "Should find truck regardless of input order")
        assertSame(unorderedTypes[2], result[2], "Should find motorcycle regardless of input order")
    }

    /**
     * Editing vehicle parameters in the configuration menu should persist for all levels,
     * not just the tutorial level.
     */
    @Test
    fun editedValuesPersistAcrossAllLevels() {
        val level = levelWithVehicleTypes(car(), truckLastwagen(), motorcycle())

        // Open configuration and edit vehicle parameters
        val firstOpen = ConfigurationScene.resolveDisplayVehicleTypes(level.vehicleTypes)
        VehicleConfigurationScene.applyInputValues(
            firstOpen[1], // Truck
            maxSpeedText = "48.0",
            accelerationText = "1.9",
            adhesionCoefficientText = "0.65"
        )

        // Verify the values are persisted in the level's vehicle-type instances
        val persisted = level.vehicleTypes.first { it.name == "Lastwagen" }
        assertEquals(48.0, persisted.maxSpeed)
        assertEquals(1.9, persisted.acceleration)
        assertEquals(0.65, persisted.adhesionCoefficient)
    }
}
