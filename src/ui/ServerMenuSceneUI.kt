package ui

import network.ServerConnection
import korlibs.image.color.Colors
import audio.onClickWithSound
import korlibs.korge.view.Container
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.container
import korlibs.korge.view.align.centerXOn
import korlibs.korge.view.graphics
import korlibs.korge.view.text
import korlibs.korge.view.xy
import korlibs.math.geom.vector.StrokeInfo
import scenes.LevelScene.Companion.TABLE_PX_H
import scenes.LevelScene.Companion.TABLE_PX_W
import scenes.ServerMenuScene.ServerMenuConfig
import ui.Assets.bitcountSingle

/**
 * UI drawing methods for ServerMenuScene.
 * Contains all methods for rendering UI elements.
 */
internal object ServerMenuSceneUI {
    /**
     * Draws the server address and connection status lines.
     *
     * @param container The container to draw in.
     * @return A container grouping the info lines.
     */
    fun drawInfoColumn(container: Container): Container {
        // First remove any existing info column to prevent accumulation
        container.children.find { 
            it is Container && 
            it.x == 0.0 && 
            it.y == ServerMenuConfig.INFO_COLUMN_Y 
        }?.let { existing ->
            container.removeChild(existing)
        }
        
        return container.container {
            xy(0.0, ServerMenuConfig.INFO_COLUMN_Y)

            val statusText = ServerConnection.connectionState.label

            text(
                "Server: ${ServerConnection.serverAddress}",
                textSize = ServerMenuConfig.INFO_TEXT_SIZE,
                color = Colors.BLACK,
                font = bitcountSingle
            ) {
                x = TABLE_PX_W / 2.0 - width / 2.0
                y = 0.0
            }
            text(
                "Status: $statusText",
                textSize = ServerMenuConfig.INFO_TEXT_SIZE,
                color = Colors.BLACK,
                font = bitcountSingle
            ) {
                x = TABLE_PX_W / 2.0 - width / 2.0
                y = ServerMenuConfig.INFO_LINE_GAP
            }
        }
    }

    /**
     * Draws the bordered label showing the currently opened world's name.
     *
     * [name] is truncated with an ellipsis when it would otherwise overflow the fixed-size label
     * (server-provided world IDs are not length-bounded, unlike locally chosen world names).
     *
     * @param container The container to draw in.
     * @param name Display name of the currently opened world.
     * @param buttonY Top offset of the "World Selection" button.
     * @return The label container.
     */
    fun drawWorldNameLabel(container: Container, name: String, buttonY: Double): Container {
        val width = ServerMenuConfig.WORLD_NAME_LABEL_W
        val height = ServerMenuConfig.WORLD_NAME_LABEL_H
        val x = TABLE_PX_W / 2.0 - width / 2.0
        val y = buttonY - ServerMenuConfig.WORLD_NAME_LABEL_GAP - height

        return container.container {
            xy(x, y)
            graphics {
                fill(Colors.WHITE) {
                    rect(0.0, 0.0, width, height)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = ServerMenuConfig.STROKE_THICKNESS)) {
                    rect(0.0, 0.0, width, height)
                }
            }
            val label = text(
                name,
                textSize = ServerMenuConfig.WORLD_NAME_LABEL_TEXT_SIZE,
                color = Colors.BLACK,
                font = bitcountSingle
            )
            SceneUIUtils.truncateWithEllipsis(label, width - ServerMenuConfig.WORLD_NAME_LABEL_PADDING_X)
            label.centerOn(this)
        }
    }

    /**
     * Draws the connect/disconnect button.
     *
     * @param container The container to draw in.
     * @param connected Whether a connection is currently established.
     * @param transitioning Whether a connect/disconnect transition is in progress.
     * @return The button container.
     */
    fun drawConnectionButton(container: Container, connected: Boolean, transitioning: Boolean): Container {
        val label = if (connected) "Trennen" else "Verbinden"
        val x = TABLE_PX_W / 2.0 - ServerMenuConfig.CONNECTION_BUTTON_W / 2.0
        val y = TABLE_PX_H - ServerMenuConfig.CONNECTION_BUTTON_BOTTOM_OFFSET

        return drawBorderedTextButton(
            container,
            x,
            y,
            ServerMenuConfig.CONNECTION_BUTTON_W,
            ServerMenuConfig.ACTION_BUTTON_H,
            label,
            disabled = transitioning
        )
    }

    /**
     * Draws the "World Selection" button.
     *
     * @param container The container to draw in.
     * @param prominent When true, renders a large button centered on screen.
     * @return The button container.
     */
    fun drawWorldSelectionButton(container: Container, prominent: Boolean): Container {
        val width = if (prominent) {
            ServerMenuConfig.WORLD_SELECT_BUTTON_PROMINENT_W
        } else {
            ServerMenuConfig.WORLD_SELECT_BUTTON_W
        }
        val height = if (prominent) {
            ServerMenuConfig.WORLD_SELECT_BUTTON_PROMINENT_H
        } else {
            ServerMenuConfig.WORLD_SELECT_BUTTON_H
        }
        val textSize = if (prominent) {
            ServerMenuConfig.WORLD_SELECT_BUTTON_PROMINENT_TEXT_SIZE
        } else {
            ServerMenuConfig.ACTION_BUTTON_TEXT_SIZE
        }
        val x = if (prominent) {
            TABLE_PX_W / 2.0 - width / 2.0
        } else {
            ServerMenuConfig.INFO_COLUMN_X
        }
        val y = if (prominent) {
            TABLE_PX_H / 2.0 - height / 2.0
        } else {
            TABLE_PX_H / 2.0 - height - ServerMenuConfig.NAV_BUTTON_GAP / 2.0
        }

        return drawFilledNavButton(container, x, y, width, height, "Weltauswahl", textSize)
    }

    /**
     * Draws a centered prominent navigation button.
     *
     * @param container The container to draw in.
     * @param label Button label.
     * @param yPos Top position of the button.
     * @return The button container.
     */
    fun drawCenteredProminentNavButton(
        container: Container,
        label: String,
        yPos: Double
    ): Container {
        return drawFilledNavButton(
            container,
            TABLE_PX_W / 2.0 - ServerMenuConfig.WORLD_SELECT_BUTTON_PROMINENT_W / 2.0,
            yPos,
            ServerMenuConfig.WORLD_SELECT_BUTTON_PROMINENT_W,
            ServerMenuConfig.WORLD_SELECT_BUTTON_PROMINENT_H,
            label,
            ServerMenuConfig.WORLD_SELECT_BUTTON_PROMINENT_TEXT_SIZE
        )
    }

    /**
     * Draws a filled navigation button with a centered label.
     *
     * @param container The container to draw in.
     * @param xPos Left edge of the button.
     * @param yPos Top edge of the button.
     * @param width Button width.
     * @param height Button height.
     * @param label Centered button text.
     * @param textSize Font size for the label.
     * @return The button container.
     */
    fun drawFilledNavButton(
        container: Container,
        xPos: Double,
        yPos: Double,
        width: Double,
        height: Double,
        label: String,
        textSize: Double
    ): Container {
        return SceneUIUtils.drawBorderedTextButton(
            container,
            xPos,
            yPos,
            width,
            height,
            label,
            textSize,
            strokeThickness = ServerMenuConfig.STROKE_THICKNESS,
            fillColor = ServerMenuConfig.WORLD_SELECT_BUTTON_COLOR
        )
    }

    /**
     * Draws the modal overlay shown during connection transitions.
     *
     * @param container The container to draw in.
     * @param message Status text shown inside the overlay.
     * @param onCancel Called when the player taps the overlay's cancel button.
     * @return The overlay container.
     */
    fun drawTransitionOverlay(container: Container, message: String, onCancel: () -> Unit): Container {
        return container.container {
            xy(
                TABLE_PX_W / 2.0 - ServerMenuConfig.OVERLAY_WIDTH / 2.0,
                TABLE_PX_H / 2.0 - ServerMenuConfig.OVERLAY_HEIGHT / 2.0
            )
            drawOverlayBackground(this, ServerMenuConfig.OVERLAY_HEIGHT)
            text(
                message,
                textSize = ServerMenuConfig.INFO_TEXT_SIZE,
                color = Colors.BLACK,
                font = bitcountSingle
            ) {
                centerXOn(this@container)
                y = 36.0
            }
            drawOverlayCancelButton(this, ServerMenuConfig.OVERLAY_HEIGHT, onCancel)
        }
    }

    /**
     * Draws the modal "connection lost" overlay with an automatic-reconnect hint and a cancel
     * button, shown while [domain.ConnectionState.RECONNECTING].
     *
     * @param container The container to draw in.
     * @param title Red headline text (e.g. "Verbindung zum Server verloren").
     * @param hint Grey hint text shown below the headline while the reconnect runs.
     * @param onCancel Called when the player taps "Abbrechen" to stop the reconnect attempt.
     * @return The overlay container.
     */
    fun drawConnectionLostOverlay(
        container: Container,
        title: String,
        hint: String,
        onCancel: () -> Unit
    ): Container {
        val height = ServerMenuConfig.CONNECTION_LOST_OVERLAY_HEIGHT
        return container.container {
            xy(
                TABLE_PX_W / 2.0 - ServerMenuConfig.OVERLAY_WIDTH / 2.0,
                TABLE_PX_H / 2.0 - height / 2.0
            )
            drawOverlayBackground(this, height)
            text(
                title,
                textSize = ServerMenuConfig.CONNECTION_LOST_TITLE_SIZE,
                color = ServerMenuConfig.CONNECTION_LOST_COLOR,
                font = bitcountSingle
            ) {
                centerXOn(this@container)
                y = ServerMenuConfig.CONNECTION_LOST_TITLE_Y
            }
            text(
                hint,
                textSize = ServerMenuConfig.INFO_TEXT_SIZE,
                color = ServerMenuConfig.RECONNECT_HINT_COLOR,
                font = bitcountSingle
            ) {
                centerXOn(this@container)
                y = ServerMenuConfig.CONNECTION_LOST_HINT_Y
            }
            drawOverlayCancelButton(this, height, onCancel)
        }
    }

    /**
     * Draws the "Abbrechen" button horizontally centered near the bottom of an overlay box.
     *
     * The button is positioned relative to the overlay box, not the screen container.
     *
     * @param overlay The overlay container the button belongs to.
     * @param overlayHeight Height of the overlay box the button is anchored to.
     * @param onCancel Called when the button is tapped.
     */
    private fun drawOverlayCancelButton(overlay: Container, overlayHeight: Double, onCancel: () -> Unit) {
        val cancelButton = drawBorderedTextButton(
            overlay,
            ServerMenuConfig.OVERLAY_WIDTH / 2.0 - ServerMenuConfig.CONNECTION_BUTTON_W / 2.0,
            overlayHeight - ServerMenuConfig.ACTION_BUTTON_H - 30.0,
            ServerMenuConfig.CONNECTION_BUTTON_W,
            ServerMenuConfig.ACTION_BUTTON_H,
            "Abbrechen"
        )
        cancelButton.onClickWithSound { onCancel() }
    }

    private fun drawOverlayBackground(container: Container, height: Double) {
        container.graphics {
            fill(Colors.WHITE) {
                rect(0.0, 0.0, ServerMenuConfig.OVERLAY_WIDTH, height)
            }
            stroke(Colors.BLACK, StrokeInfo(thickness = ServerMenuConfig.STROKE_THICKNESS)) {
                rect(0.0, 0.0, ServerMenuConfig.OVERLAY_WIDTH, height)
            }
        }
    }

    /**
     * Draws a bordered text button.
     *
     * @param container The container to draw in.
     * @param xPos Left edge of the button.
     * @param yPos Top edge of the button.
     * @param width Button width.
     * @param height Button height.
     * @param label Centered button text.
     * @param disabled Whether the button is shown grayed-out and inert.
     * @return The button container.
     */
    fun drawBorderedTextButton(
        container: Container,
        xPos: Double,
        yPos: Double,
        width: Double,
        height: Double,
        label: String,
        disabled: Boolean = false
    ): Container {
        return SceneUIUtils.drawBorderedTextButton(
            container,
            xPos,
            yPos,
            width,
            height,
            label,
            ServerMenuConfig.ACTION_BUTTON_TEXT_SIZE,
            disabled,
            ServerMenuConfig.STROKE_THICKNESS
        )
    }
}
