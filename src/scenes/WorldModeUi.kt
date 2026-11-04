package scenes

import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.view.SContainer
import korlibs.korge.view.Text
import korlibs.korge.view.graphics

/**
 * Shared UI and validation helpers for the multi-table world scenes.
 */
object WorldModeUi {
    /** Message shown when a required world name is missing. */
    const val BLANK_WORLD_NAME_ERROR = "Bitte Namen eintragen"

    /** Message shown when a world name is already used by another world. */
    const val DUPLICATE_WORLD_NAME_ERROR = "Weltname existiert bereits"

    /** Shared width of world-mode text input fields. */
    const val FORM_INPUT_W = 1080.0

    /** Shared height of world-mode text input fields. */
    const val FORM_INPUT_H = 66.0

    /** Shared text size used for world-mode form labels. */
    const val FORM_LABEL_TEXT_SIZE = 30.0

    /** Shared width of world-mode bottom action buttons. */
    const val ACTION_BUTTON_W = 330.0

    /** Shared height of world-mode bottom action buttons. */
    const val ACTION_BUTTON_H = 82.0

    /** Shared top y-position of world-mode bottom action buttons. */
    const val ACTION_BUTTON_Y = 792.0

    /** Shared border thickness for world-mode boxes and buttons. */
    const val STROKE_THICKNESS = 2.0

    /** Shared top y-position of world-mode titles. */
    const val TITLE_Y = 82.0

    /** Shared text size used for world-mode titles. */
    const val TITLE_TEXT_SIZE = 78.0

    /** Shared vertical position of centered world-mode status and error messages. */
    const val MESSAGE_Y = 560.0

    /** Validation state for world-name inputs. */
    enum class WorldNameStatus {
        /** The name is non-blank and not used by another visible world. */
        VALID,

        /** The name is blank or contains only whitespace. */
        BLANK,

        /** The name is already used by another visible world. */
        DUPLICATE
    }

    /** Full validation result for a world-name input. */
    data class WorldNameValidationResult(
        /** Machine-readable validation status. */
        val status: WorldNameStatus,

        /** User-facing validation message, or null when the name is valid. */
        val message: String? = null
    ) {
        /** True when the input can be submitted. */
        val valid: Boolean get() = status == WorldNameStatus.VALID
    }

    /** Returns the stable key used for user-facing world-name comparisons. */
    fun normalizedWorldName(name: String): String = name.trim().lowercase()

    /** Returns whether [name] is already used by another world in [worlds]. */
    fun isDuplicateWorldName(
        name: String,
        worlds: List<WorldSelectionScene.WorldEntry>,
        ignoredWorldId: String? = null
    ): Boolean {
        val normalizedName = normalizedWorldName(name)
        if (normalizedName.isEmpty()) return false
        return worlds.any { world ->
            world.id != ignoredWorldId && normalizedWorldName(world.name) == normalizedName
        }
    }

    /** Validates a world-name input against the given visible/server worlds. */
    fun validateWorldName(
        name: String,
        worlds: List<WorldSelectionScene.WorldEntry>,
        ignoredWorldId: String? = null
    ): WorldNameValidationResult {
        if (name.trim().isEmpty()) return WorldNameValidationResult(WorldNameStatus.BLANK, BLANK_WORLD_NAME_ERROR)
        if (isDuplicateWorldName(name, worlds, ignoredWorldId)) {
            return WorldNameValidationResult(WorldNameStatus.DUPLICATE, DUPLICATE_WORLD_NAME_ERROR)
        }
        return WorldNameValidationResult(WorldNameStatus.VALID)
    }

    /** Returns the action-button fill color for [validation]. */
    fun buttonFillForValidation(
        validation: WorldNameValidationResult,
        validFill: RGBA,
        invalidFill: RGBA
    ): RGBA = if (validation.valid) validFill else invalidFill

    /** Computes the x-coordinate for horizontally centered content. */
    fun centeredMessageX(sceneWidth: Double, contentWidth: Double): Double =
        sceneWidth / 2.0 - contentWidth / 2.0

    /** Draws the plain full-table background shared by world form scenes. */
    fun SContainer.drawWorldFormBackground() {
        graphics {
            fill(Colors.WHITE) {
                rect(0.0, 0.0, LevelScene.TABLE_PX_W, LevelScene.TABLE_PX_H)
            }
        }
    }

    /** Updates [messageText] as a horizontally centered status/error message. */
    fun setCenteredMessage(
        messageText: Text,
        message: String?,
        color: RGBA,
        sceneWidth: Double,
        y: Double
    ) {
        messageText.text = message.orEmpty()
        messageText.color = color
        messageText.visible = !message.isNullOrBlank()
        messageText.x = centeredMessageX(sceneWidth, messageText.width)
        messageText.y = y
    }
}
