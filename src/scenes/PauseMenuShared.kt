package scenes

import domain.colorBlindnessModeActivated
import korlibs.image.bitmap.Bitmap32
import korlibs.image.color.Colors
import korlibs.korge.ui.uiHorizontalStack
import korlibs.korge.view.Container
import korlibs.korge.view.SContainer
import korlibs.korge.view.View
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.container
import korlibs.korge.view.filter.ColorMatrixFilter
import korlibs.korge.view.filter.filter
import korlibs.korge.view.graphics
import korlibs.korge.view.image
import korlibs.korge.view.size
import korlibs.korge.view.solidRect
import korlibs.korge.view.text
import korlibs.korge.view.xy
import audio.onClickWithSound
import korlibs.math.geom.Matrix4
import korlibs.math.geom.vector.StrokeInfo
import ui.Assets.bitcountSingle
import ui.ControlButtons

/**
 * Layout constants shared by both pause-menu variants ([MenuScene] and [MultiTableMenuScene]).
 *
 * Moving them here eliminates the duplicate `const val` blocks that previously existed in
 * [MenuScene.MenuConfig] and [MultiTableMenuScene.MultiTableMenuConfig]. Each scene's own config
 * object still exposes these via delegation so the stable public API (e.g. [MenuScene.MenuConfig.BOX_LENGTH])
 * used by unit tests is preserved unchanged.
 */
internal object PauseMenuConfig {
    /** Length of the side of the square icon box. */
    const val BOX_LENGTH = 220.0
    /** Spacing between the icon boxes. */
    const val BOX_SPACING = 50.0
    /** Height of a button's descriptive label field below the icon box. */
    const val LABEL_HEIGHT = 80.0
    /** Font size used for button label text. */
    const val LABEL_TEXT_SIZE = 24.0
    /** Maximum character count per line before a button label wraps onto the next line. */
    const val LABEL_MAX_CHARS_PER_LINE = 15
    /** Vertical spacing between wrapped lines within a button label. */
    const val LABEL_LINE_HEIGHT = 28.0
    /** Scale factor for icons. */
    const val ICON_SCALE = 0.62
    /** Scale factor for the car icon. */
    const val CAR_ICON_SCALE = 0.78
    /** Thickness of the stroke around icon boxes. */
    const val STROKE_THICKNESS = 2.0

    /** Color-matrix filter simulating deuteranopia (green-weak color blindness). */
    val DEUTERANOPIA_FILTER = ColorMatrixFilter(
        Matrix4.fromColumns(
            1.0f,  0.7f,  0.0f,  0.0f,
            0.0f,  0.3f,  0.0f,  0.0f,
            0.0f,  0.7f,  1.0f,  0.0f,
            0.0f,  0.0f,  0.0f,  1.0f
        )
    )

    /**
     * Returns the icon size for a large square menu button.
     *
     * @param iconScale scale factor relative to [BOX_LENGTH].
     * @return icon size in pixels.
     */
    fun iconSize(iconScale: Double = ICON_SCALE): Double = BOX_LENGTH * iconScale

    /**
     * Wraps a menu label into short lines that fit into the fixed-width label field.
     *
     * Existing explicit line breaks are preserved. Without them, wrapping prefers natural
     * separators in German compound labels such as spaces, hyphens, and slashes.
     *
     * @param label unwrapped label text.
     * @return one or more lines for centered rendering.
     */
    fun labelLines(label: String): List<String> {
        val explicitLines = label.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (explicitLines.size > 1) return explicitLines

        val tokens = labelTokens(label.trim())
        if (tokens.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        var currentLine = ""
        tokens.forEach { token ->
            val candidate = currentLine + token
            if (currentLine.isNotEmpty() && candidate.trim().length > LABEL_MAX_CHARS_PER_LINE) {
                lines += currentLine.trim()
                currentLine = token.trimStart()
            } else {
                currentLine = candidate
            }
        }
        if (currentLine.isNotBlank()) lines += currentLine.trim()
        return lines
    }

    private fun labelTokens(label: String): List<String> {
        val tokens = mutableListOf<String>()
        val token = StringBuilder()
        label.forEach { char ->
            token.append(char)
            if (char == ' ' || char == '-' || char == '/') {
                tokens += token.toString()
                token.clear()
            }
        }
        if (token.isNotEmpty()) tokens += token.toString()
        return tokens
    }
}

/**
 * Represents a point in projected menu coordinates.
 * @property x horizontal menu coordinate.
 * @property y vertical menu coordinate.
 */
data class MenuPoint(val x: Double, val y: Double)

/**
 * Represents a rectangle in projected menu coordinates.
 * @property x left edge of the rectangle.
 * @property y top edge of the rectangle.
 * @property width rectangle width.
 * @property height rectangle height.
 */
data class MenuRect(val x: Double, val y: Double, val width: Double, val height: Double) {
    /** Right edge of the rectangle. */
    val right: Double get() = x + width
    /** Horizontal center of the rectangle. */
    val centerX: Double get() = x + width / 2.0
    /** Vertical center of the rectangle. */
    val centerY: Double get() = y + height / 2.0
}

/**
 * Represents a menu label below a square action button.
 * @property bounds label field bounds.
 * @property text label text.
 */
data class ButtonLabelLayout(
    /** Label field bounds. */
    val bounds: MenuRect,
    /** Label text. */
    val text: String
)

/**
 * Represents a pause-menu save button with a saved-state fill layer.
 *
 * @property root clickable icon container.
 * @property savedFill green label fill shown when the current level is saved.
 */
internal data class PauseMenuSaveButton(
    /** Clickable icon container. */
    val root: Container,
    /** Green fill inside the label, visible when the level is saved. */
    val savedFill: View,
    /** Whether the represented level currently matches a saved snapshot. */
    var levelSaved: Boolean
) {
    /** Updates the saved-state fill to match the currently recognized level. */
    fun setSaved(saved: Boolean) {
        levelSaved = saved
        savedFill.visible = saved
    }

    /** Shows the saved-state fill. */
    fun markSaved() {
        setSaved(true)
    }
}

/**
 * Layout geometry for the four-button pause-menu row.
 *
 * @property rowX left edge of the button row.
 * @property rowY top edge of the button row (accounts for both box and label height so the
 *   whole block is vertically centered).
 * @property rowWidth total width of the button row.
 * @property step horizontal distance from one button origin to the next.
 */
internal data class PauseMenuButtonRowGeometry(
    val rowX: Double,
    val rowY: Double,
    val rowWidth: Double,
    val step: Double
)

/**
 * Computes the layout geometry shared by both pause-menu [ButtonRowLayout] factories.
 *
 * Extracted here to remove the duplicate calculation that previously existed in both
 * [MenuScene.MenuLayout.buttonRow] and [MultiTableMenuScene.MenuLayout.buttonRow].
 *
 * @return the button-row geometry.
 */
internal fun pauseMenuButtonRowGeometry(): PauseMenuButtonRowGeometry {
    val rowWidth = PauseMenuConfig.BOX_LENGTH * 4.0 + PauseMenuConfig.BOX_SPACING * 3.0
    val rowX = LevelScene.TABLE_PX_W / 2.0 - rowWidth / 2.0
    val labelGap = PauseMenuConfig.BOX_SPACING / 2.0
    val blockHeight = PauseMenuConfig.BOX_LENGTH + labelGap + PauseMenuConfig.LABEL_HEIGHT
    val rowY = LevelScene.TABLE_PX_H / 2.0 - blockHeight / 2.0
    val step = PauseMenuConfig.BOX_LENGTH + PauseMenuConfig.BOX_SPACING
    return PauseMenuButtonRowGeometry(rowX, rowY, rowWidth, step)
}

/**
 * Returns the top-right close button position shared by both pause-menu variants.
 *
 * Extracted here to avoid the identical implementation that previously existed in
 * both [MenuScene.MenuLayout] and [MultiTableMenuScene.MenuLayout].
 */
internal fun pauseMenuCloseButtonPosition(): MenuPoint = MenuPoint(
    LevelScene.TABLE_PX_W - ControlButtons.BUTTON_SIZE - ControlButtons.CORNER_OFFSET,
    ControlButtons.CORNER_OFFSET
)

/**
 * Builds the [ButtonLabelLayout] for a pause-menu button.
 *
 * Placing this helper at package level removes the identical private `buttonLabel()` that
 * previously existed inside both [MenuScene.ButtonRowLayout] and
 * [MultiTableMenuScene.ButtonRowLayout].
 *
 * @param button top-left corner of the icon box above the label.
 * @param text label text to display.
 * @return layout describing the label field and its centered text.
 */
internal fun buttonLabelLayout(button: MenuPoint, text: String): ButtonLabelLayout =
    ButtonLabelLayout(
        bounds = MenuRect(
            button.x,
            button.y + PauseMenuConfig.BOX_LENGTH + PauseMenuConfig.BOX_SPACING / 2,
            PauseMenuConfig.BOX_LENGTH,
            PauseMenuConfig.LABEL_HEIGHT
        ),
        text = text
    )

/**
 * Draws a square icon box for a pause-menu button.
 *
 * @param xPos x-coordinate of the box's top-left corner.
 * @param yPos y-coordinate of the box's top-left corner.
 * @param icon icon bitmap displayed inside the box.
 * @param iconScale scale factor relative to [PauseMenuConfig.BOX_LENGTH].
 * @return container representing the box with the icon.
 */
internal fun Container.drawPauseMenuButtonWithIcon(
    xPos: Double,
    yPos: Double,
    icon: Bitmap32,
    iconScale: Double = PauseMenuConfig.ICON_SCALE
): Container {
    return container {
        xy(xPos, yPos)
        graphics {
            stroke(Colors.BLACK, StrokeInfo(thickness = PauseMenuConfig.STROKE_THICKNESS)) {
                rect(0.0, 0.0, PauseMenuConfig.BOX_LENGTH, PauseMenuConfig.BOX_LENGTH)
            }
        }
        image(icon) {
            val iconSize = PauseMenuConfig.iconSize(iconScale)
            size(iconSize, iconSize)
            centerOn(this@container)
        }
    }
}

/**
 * Draws a save icon box and its label whose label background can turn green when saved.
 *
 * @param button button position.
 * @param label label layout below the button.
 * @param icon icon bitmap displayed inside the box.
 * @param initiallySaved true when the green saved-state fill should be visible immediately.
 * @param iconScale scale factor relative to [PauseMenuConfig.BOX_LENGTH].
 * @return button handles for wiring clicks and saved-state feedback.
 */
internal fun Container.drawPauseMenuSaveButton(
    button: MenuPoint,
    label: ButtonLabelLayout,
    icon: Bitmap32,
    initiallySaved: Boolean,
    iconScale: Double = PauseMenuConfig.ICON_SCALE
): PauseMenuSaveButton {
    val root = drawPauseMenuButtonWithIcon(button.x, button.y, icon, iconScale)
    val savedFill = drawPauseMenuButtonLabel(label.bounds.x, label.bounds.y, label.text, initiallySaved)
    return PauseMenuSaveButton(root, savedFill, initiallySaved)
}

/**
 * Draws a descriptive label field below a pause-menu action button.
 *
 * @param xPos x-coordinate of the label's top-left corner.
 * @param yPos y-coordinate of the label's top-left corner.
 * @param label label text; wrapped automatically by [PauseMenuConfig.labelLines].
 * @return container representing the label field.
 */
internal fun Container.drawPauseMenuButtonLabel(
    xPos: Double,
    yPos: Double,
    label: String,
    saved: Boolean = false
): View {
    lateinit var savedFill: View
    container {
        xy(xPos, yPos)
        val lines = PauseMenuConfig.labelLines(label)
        savedFill = solidRect(PauseMenuConfig.BOX_LENGTH, PauseMenuConfig.LABEL_HEIGHT, LevelScene.COMPLETION_ACTION_FILL_COLOR) {
            visible = saved
        }
        graphics {
            stroke(Colors.BLACK, StrokeInfo(thickness = PauseMenuConfig.STROKE_THICKNESS)) {
                rect(0.0, 0.0, PauseMenuConfig.BOX_LENGTH, PauseMenuConfig.LABEL_HEIGHT)
            }
        }
        val textBlockHeight = lines.size * PauseMenuConfig.LABEL_LINE_HEIGHT
        val textStartY = PauseMenuConfig.LABEL_HEIGHT / 2.0 - textBlockHeight / 2.0
        lines.forEachIndexed { index, line ->
            text(line, textSize = PauseMenuConfig.LABEL_TEXT_SIZE, color = Colors.BLACK, font = bitcountSingle) {
                x = PauseMenuConfig.BOX_LENGTH / 2.0 - width / 2.0
                y = textStartY + index * PauseMenuConfig.LABEL_LINE_HEIGHT
            }
        }
    }
    return savedFill
}

/**
 * Draws the color-blindness accessibility toggle shared by both pause-menu variants.
 *
 * Clicking the checkbox updates [colorBlindnessModeActivated], synchronizes the visible
 * checkmark, and applies or removes the stage-wide [PauseMenuConfig.DEUTERANOPIA_FILTER].
 */
internal fun SContainer.drawPauseMenuColorBlindnessToggle() {
    val colorBlindnessCt = uiHorizontalStack {
        x = LevelScene.TABLE_PX_W / 2.0 - 360.0
        y = LevelScene.TABLE_PX_H - 200
    }
    colorBlindnessCt.text("Modus für Farbenblindheit: ", 40, Colors.BLACK, font = bitcountSingle)
    colorBlindnessCt.container {
        val checkBox = solidRect(35, 35, Colors.BLACK)
        solidRect(32, 32, Colors.WHITE).centerOn(checkBox)
        val checkMark = solidRect(22.0, 22.0, Colors.BLACK) {
            visible = colorBlindnessModeActivated
            centerOn(checkBox)
        }
        checkBox.onClickWithSound {
            colorBlindnessModeActivated = !colorBlindnessModeActivated
            checkMark.visible = colorBlindnessModeActivated
            if (colorBlindnessModeActivated) {
                stage?.filter = PauseMenuConfig.DEUTERANOPIA_FILTER
            } else {
                stage?.filter = ColorMatrixFilter(ColorMatrixFilter.IDENTITY_MATRIX)
            }
        }
        y += 10
    }
}
