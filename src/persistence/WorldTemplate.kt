package persistence

import domain.Color
import domain.GameSimulationConfig
import domain.Level
import domain.SourceNode
import domain.SourceNodeConfiguration
import domain.TargetNode
import domain.VehicleType
import domain.WeatherCondition
import korlibs.image.color.RGBA
import korlibs.math.geom.Angle
import korlibs.math.geom.Point

/**
 * Centralized standard template for blank multi-table worlds.
 *
 * Every new world created in the world-selection menu is based on this definition, so all worlds
 * share a consistent starting structure without duplicating the configuration at each call site.
 *
 * The template deliberately omits challenge mode and speed-selection configuration (those controls
 * are hidden by [scenes.WorldScene]). Weather is set to [WeatherCondition.DRY] so the world uses
 * the same green grass background as a normal dry level.
 *
 * No server sources are included; all nodes are local definitions that can be freely edited after
 * the world is created.
 *
 * **Node layout** (game area: 225 m wide × 125 m tall):
 * ```
 *   ┌─────────────────────────────────────────────────┐
 *   │                    ↓ Grün (Top)                 │
 *   │ → Rot (Left/Up)            Rot (Right/Up) →    │
 *   │                  ● Gelb (Center, Excess)        │
 *   │ → Blau (Left/Down)       Blau (Right/Down) →   │
 *   │                    ↑ Grün (Bottom)              │
 *   └─────────────────────────────────────────────────┘
 * ```
 * The yellow excess source (center) has no local target; its vehicles are intended to be routed
 * to a target on a neighboring table in the multi-table setup.
 */
object WorldTemplate {

    /** Default display name given to a newly created world. */
    const val DEFAULT_WORLD_NAME = "Neue Welt"

    /**
     * Level number reserved for worlds created from this template.
     *
     * Template worlds are not part of the predefined level catalog (which uses 1–8) and must not
     * collide with the tutorial (number 0), so a negative sentinel value is used.
     */
    const val TEMPLATE_LEVEL_NUMBER = -1

    /** Name of the default vehicle type placed in template source configurations. */
    private const val DEFAULT_VEHICLE_TYPE_NAME = "Auto"

    // -------------------------------------------------------------------------
    // Color names — used as the shared key in the level's color table
    // -------------------------------------------------------------------------

    /** Left-border source paired with right-border target (upper row). */
    private const val COLOR_RED_NAME = "Rot"

    /** Left-border source paired with right-border target (lower row). */
    private const val COLOR_BLUE_NAME = "Blau"

    /** Top-border source paired with bottom-border target. */
    private const val COLOR_GREEN_NAME = "Gruen"

    /**
     * Center (excess) source — no local target.
     *
     * Vehicles of this color are meant to be forwarded to a neighboring table; they will not
     * reach any destination in the template world until a cross-table target is wired up.
     */
    private const val COLOR_YELLOW_NAME = "Gelb"

    // -------------------------------------------------------------------------
    // Node positions in virtual meters (game area: 225 m wide × 125 m tall)
    // -------------------------------------------------------------------------

    /**
     * Half the rendered arrow sprite length in virtual meters.
     *
     * Derived from [scenes.LevelScene]'s `ARROW_L_PX = virtualMetersToPixels(14.0 / 3.0)`.
     * Halving it gives the offset needed to place the node centre so the outer arrow edge is
     * perfectly flush with the application-window border (no gap).
     */
    internal const val ARROW_HALF_M = 14.0 / 3.0 / 2.0   // = 7/3 ≈ 2.333 m

    /** X-coordinate for source nodes on the left border (arrow flush with window edge). */
    private const val LEFT_X = ARROW_HALF_M

    /** X-coordinate for target nodes on the right border (arrow flush with window edge). */
    private const val RIGHT_X = 225.0 - ARROW_HALF_M

    /** Y-coordinate for the upper left-source / right-target pair (one-third of total height). */
    private const val UPPER_Y = 41.67

    /** Y-coordinate for the lower left-source / right-target pair (two-thirds of total height). */
    private const val LOWER_Y = 83.33

    /** Y-coordinate for the top-border source (arrow flush with top window edge). */
    private const val TOP_Y = ARROW_HALF_M

    /** Y-coordinate for the bottom-border target (arrow flush with bottom window edge). */
    private const val BOTTOM_Y = 125.0 - ARROW_HALF_M

    /** X-coordinate shared by the top-border source and bottom-border target (horizontal centre). */
    private const val TOP_BOTTOM_X = 112.5

    /** X-coordinate of the excess centre source (horizontal centre of the game area). */
    private const val CENTER_X = 112.5

    /** Y-coordinate of the excess centre source (vertical centre of the game area). */
    private const val CENTER_Y = 62.5

    // -------------------------------------------------------------------------
    // Arrow orientations in degrees
    // -------------------------------------------------------------------------

    /**
     * Orientation of source nodes on the left border.
     *
     * 90° causes the rendered arrow to point right, matching the left-to-right travel direction
     * established by the tutorial level convention (`tutorial.json`).
     */
    private const val SOURCE_LEFT_ORIENTATION_DEG = 90.0

    /**
     * Orientation of target nodes on the right border.
     *
     * 270° is the mirror of [SOURCE_LEFT_ORIENTATION_DEG]; the tutorial target uses the same value.
     */
    private const val TARGET_RIGHT_ORIENTATION_DEG = 270.0

    /**
     * Orientation of the source node on the top border.
     *
     * 180° points the arrow downward into the game area. Derived from the connection-side formula:
     * `connectionSideAngle = orientation - 90°`; setting connectionSideAngle=90° (↓) gives
     * `orientation = 180°`.
     */
    private const val SOURCE_TOP_ORIENTATION_DEG = 180.0

    /**
     * Orientation of the target node on the bottom border.
     *
     * 0° makes the target accept vehicles arriving from above. Connection side = 270° (↑), so
     * `orientation = 270° + 90° = 360° ≡ 0°`.
     */
    private const val TARGET_BOTTOM_ORIENTATION_DEG = 0.0

    /**
     * Arbitrary orientation for the excess centre source.
     *
     * The visual direction of this node is not significant because it has no fixed local target;
     * a non-axis-aligned angle makes it visually distinct from border nodes.
     */
    private const val CENTER_ORIENTATION_DEG = 57.0

    // -------------------------------------------------------------------------
    // Default vehicle configuration
    // -------------------------------------------------------------------------

    /** Maximum speed in km/h of the default template vehicle. */
    private const val DEFAULT_MAX_SPEED = 80.0

    /** Physical length in virtual meters of the default template vehicle. */
    private const val DEFAULT_VEHICLE_LENGTH = 4.0

    /** Adhesion coefficient (friction multiplier) of the default template vehicle. */
    private const val DEFAULT_ADHESION_COEFFICIENT = 0.7

    /** Acceleration in m/s² of the default template vehicle. */
    private const val DEFAULT_ACCELERATION = 3.0

    /** Resource path for the default vehicle graphic. */
    private const val DEFAULT_GRAPHIC_REF = "images/car.png"

    /** Vehicle spawn interval in seconds (one vehicle every 2 s). */
    private const val DEFAULT_GENERATION_SPEED = 2.0

    /** Initial speed in km/h for spawned vehicles. */
    private const val DEFAULT_INITIAL_SPEED = 55.0

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Creates a new blank world level from the standard template.
     *
     * The returned level:
     * - uses [WeatherCondition.DRY] for the standard green grass background (no weather effects)
     * - has an empty street network; the user builds connections after creation
     * - contains pre-placed source and target nodes as a scaffold (see class-level diagram)
     * - does not activate [domain.ChallengeMode]; the challenge limit is set to [Int.MAX_VALUE]
     *   so the limit can never be accidentally reached before the user opts in
     * - contains no server-sourced nodes (neutral server sources are intentionally excluded)
     *
     * One of the sources (yellow, center) intentionally has no local matching target; it is the
     * "excess" source whose vehicles are forwarded to a target on a neighboring table.
     *
     * @param name display name shown for this world.
     * @return a fresh [Level] ready for user editing.
     */
    fun createBlankWorld(name: String = DEFAULT_WORLD_NAME): Level {
        val level = Level(
            name = name,
            number = TEMPLATE_LEVEL_NUMBER,
            weatherCondition = WeatherCondition.DRY,
            challengeConnectionNodeLimit = Int.MAX_VALUE
        )

        val vehicleType = createDefaultVehicleType()
        val redColor   = createColor(COLOR_RED_NAME,    r = 255, g = 0,   b = 0)
        val blueColor  = createColor(COLOR_BLUE_NAME,   r = 0,   g = 0,   b = 255)
        val greenColor = createColor(COLOR_GREEN_NAME,  r = 0,   g = 200, b = 0)
        val yellowColor = createColor(COLOR_YELLOW_NAME, r = 255, g = 200, b = 0)

        // Left-border sources ↔ right-border targets (horizontal pairs)
        level.addNode(createSourceNode(LEFT_X,  UPPER_Y, SOURCE_LEFT_ORIENTATION_DEG,  redColor,  vehicleType))
        level.addNode(createTargetNode(RIGHT_X, UPPER_Y, TARGET_RIGHT_ORIENTATION_DEG, redColor))
        level.addNode(createSourceNode(LEFT_X,  LOWER_Y, SOURCE_LEFT_ORIENTATION_DEG,  blueColor, vehicleType))
        level.addNode(createTargetNode(RIGHT_X, LOWER_Y, TARGET_RIGHT_ORIENTATION_DEG, blueColor))

        // Top-border source ↔ bottom-border target (vertical pair)
        level.addNode(createSourceNode(TOP_BOTTOM_X, TOP_Y,    SOURCE_TOP_ORIENTATION_DEG,    greenColor, vehicleType))
        level.addNode(createTargetNode(TOP_BOTTOM_X, BOTTOM_Y, TARGET_BOTTOM_ORIENTATION_DEG, greenColor))

        // Excess centre source — no local target; vehicles are routed to a neighboring table
        level.addNode(createSourceNode(CENTER_X, CENTER_Y, CENTER_ORIENTATION_DEG, yellowColor, vehicleType))

        return level
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Creates the default vehicle type shared by all template source configurations.
     *
     * @return a new [VehicleType] with standard car properties.
     */
    private fun createDefaultVehicleType(): VehicleType = VehicleType(
        name = DEFAULT_VEHICLE_TYPE_NAME,
        maxSpeed = DEFAULT_MAX_SPEED,
        length = DEFAULT_VEHICLE_LENGTH,
        adhesionCoefficient = DEFAULT_ADHESION_COEFFICIENT,
        acceleration = DEFAULT_ACCELERATION,
        graphicRef = DEFAULT_GRAPHIC_REF
    )

    /**
     * Creates a named [Color] from individual 8-bit RGB components (alpha is always 255).
     *
     * @param name human-readable color name used as the shared key in the level's color table.
     * @param r red component (0–255).
     * @param g green component (0–255).
     * @param b blue component (0–255).
     * @return the constructed [Color].
     */
    private fun createColor(name: String, r: Int, g: Int, b: Int): Color =
        Color(name, RGBA(r, g, b, 255))

    /**
     * Creates a [SourceNode] at the given position with a single default vehicle configuration.
     *
     * @param x x-coordinate in virtual meters.
     * @param y y-coordinate in virtual meters.
     * @param orientationDeg arrow orientation in degrees.
     * @param color vehicle tint color (should match a paired target, except for excess sources).
     * @param vehicleType vehicle type used for spawning.
     * @return the configured [SourceNode].
     */
    private fun createSourceNode(
        x: Double,
        y: Double,
        orientationDeg: Double,
        color: Color,
        vehicleType: VehicleType
    ): SourceNode {
        val configuration = SourceNodeConfiguration(
            color = color,
            generationSpeed = DEFAULT_GENERATION_SPEED,
            initialVehicleSpeed = DEFAULT_INITIAL_SPEED,
            vehicleType = vehicleType,
            vehiclesToSpawn = GameSimulationConfig.DEFAULT_VEHICLES_TO_SPAWN
        )
        return SourceNode(
            position = Point(x, y),
            elevation = 0,
            orientation = Angle.fromDegrees(orientationDeg),
            configurations = listOf(configuration)
        )
    }

    /**
     * Creates a [TargetNode] at the given position.
     *
     * @param x x-coordinate in virtual meters.
     * @param y y-coordinate in virtual meters.
     * @param orientationDeg arrow orientation in degrees.
     * @param color color vehicles must have to count as arrived at this target.
     * @return the configured [TargetNode].
     */
    private fun createTargetNode(
        x: Double,
        y: Double,
        orientationDeg: Double,
        color: Color
    ): TargetNode = TargetNode(
        position = Point(x, y),
        elevation = 0,
        orientation = Angle.fromDegrees(orientationDeg),
        color = color
    )
}
