package worldeditor

import domain.Color
import domain.VehicleType
import korlibs.image.color.RGBA

/** Shared defaults and helpers used by the world editor. */
object WorldEditorDefaults {
    /** Default spacing in seconds between generated vehicles for newly placed sources. */
    const val DEFAULT_GENERATION_SPEED = 2.0

    /** Default name used while creating a new world. */
    const val DEFAULT_NEW_WORLD_NAME = "Neue Welt"

    /** Colors shown in the editor properties palette. */
    val paletteColors = listOf(
        Color("Rot", RGBA(255, 0, 0, 255)),
        Color("Blau", RGBA(0, 0, 255, 255)),
        Color("Gruen", RGBA(0, 200, 0, 255)),
        Color("Gelb", RGBA(255, 255, 0, 255)),
        Color("Schwarz", RGBA(0, 0, 0, 255))
    )

    /** Vehicle types selectable in the editor. */
    val vehicleTypes = listOf(
        VehicleType("Auto", 80.0, 4.0, 0.7, 3.0, "images/car.png"),
        VehicleType("Lastwagen", 60.0, 12.0, 0.7, 1.5, "images/truck.png"),
        VehicleType("Motorrad", 100.0, 2.0, 0.8, 4.5, "images/motorcycle.png")
    )

    /** Finds an editor color by name. Umlaut and ASCII spellings are treated alike. */
    fun colorByName(name: String): Color? =
        paletteColors.firstOrNull { colorNamesMatch(it.name, name) }

    /** Returns true when two color names refer to the same editor palette color. */
    fun colorNamesMatch(left: String, right: String): Boolean =
        normalizedColorName(left) == normalizedColorName(right)

    /** Finds a default editor vehicle by name. */
    fun vehicleTypeByName(name: String): VehicleType? =
        vehicleTypes.firstOrNull { it.name.equals(name, ignoreCase = true) }?.copyVehicleType()

    private fun VehicleType.copyVehicleType() =
        VehicleType(name, maxSpeed, length, adhesionCoefficient, acceleration, graphicRef)

    private fun normalizedColorName(name: String): String =
        name.trim()
            .lowercase()
            .replace("\u00e4", "ae")
            .replace("\u00f6", "oe")
            .replace("\u00fc", "ue")
            .replace("\u00df", "ss")
}
