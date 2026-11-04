package domain

import korlibs.image.color.RGBA

/**
 * Represents a named color for a vehicle or target node.
 *
 * @property name The human-readable name of the color (e.g., "Red", "Blue").
 * @property value The actual RGBA color value used for rendering.
 */
class Color(
    val name: String,
    val value: RGBA
)
