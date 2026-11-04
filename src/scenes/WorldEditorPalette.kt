package scenes

import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import audio.onClickWithSound
import korlibs.korge.input.onMouseDrag
import korlibs.korge.view.SContainer
import korlibs.korge.view.align.centerOn
import korlibs.korge.view.container
import korlibs.korge.view.graphics
import korlibs.korge.view.position
import korlibs.korge.view.text
import korlibs.math.geom.vector.StrokeInfo
import network.WorldEditorNodeKind
import ui.Assets

/** Draws and wires the source/sink palette in the world editor. */
internal class WorldEditorPalette(
    private val addNodeAtFieldCenter: (WorldEditorNodeKind) -> Unit,
    private val dropPaletteNode: (WorldEditorNodeKind, Double, Double) -> Unit
) {
    /** Draws the palette frame, drag buttons and short placement hint. */
    fun draw(container: SContainer) {
        container.graphics {
            fill(Colors["#F7F7F7"]) { rect(PALETTE_X, PALETTE_Y, PALETTE_W, PALETTE_H) }
            stroke(Colors.BLACK, StrokeInfo(thickness = 1.5)) { rect(PALETTE_X, PALETTE_Y, PALETTE_W, PALETTE_H) }
        }
        container.text("Palette", textSize = 28.0, color = Colors.BLACK, font = Assets.bitcountSingle) {
            x = PALETTE_X + PALETTE_W / 2.0 - width / 2.0
            y = PALETTE_Y + 24.0
        }

        drawPaletteButton(container, "Quelle (ziehen)", WorldEditorNodeKind.SOURCE, PALETTE_Y + 92.0, Colors["#2DD12D"])
        drawPaletteButton(container, "Senke (ziehen)", WorldEditorNodeKind.TARGET, PALETTE_Y + 212.0, Colors["#FF3535"])

        container.text(
            "Quelle/Senke aus der\nPalette auf das\nSpielfeld ziehen und\nauf dem Feld ablegen.",
            textSize = 21.0,
            color = Colors.BLACK,
            font = Assets.bitcountSingle
        ) {
            x = PALETTE_X + 34.0
            y = PALETTE_Y + 338.0
        }
    }

    private fun drawPaletteButton(
        parent: SContainer,
        label: String,
        kind: WorldEditorNodeKind,
        yPos: Double,
        fillColor: RGBA
    ) {
        val button = parent.container {
            position(PALETTE_X + 52.0, yPos)
            graphics {
                fill(fillColor) { rect(0.0, 0.0, 240.0, 80.0) }
                stroke(Colors.BLACK, StrokeInfo(thickness = 2.0)) { rect(0.0, 0.0, 240.0, 80.0) }
            }
            text(label, textSize = 20.0, color = Colors.BLACK, font = Assets.bitcountSingle) {
                centerOn(this@container)
            }
        }
        button.onClickWithSound {
            addNodeAtFieldCenter(kind)
        }
        button.onMouseDrag {
            if (it.end) {
                dropPaletteNode(kind, it.cx, it.cy)
            }
        }
    }

    private companion object {
        const val PALETTE_X = WorldEditorLayout.PALETTE_X
        const val PALETTE_Y = WorldEditorLayout.PALETTE_Y
        const val PALETTE_W = WorldEditorLayout.PALETTE_W
        const val PALETTE_H = WorldEditorLayout.PALETTE_H
    }
}
