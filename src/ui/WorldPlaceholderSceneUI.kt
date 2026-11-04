package ui

import network.ServerConnection
import domain.TableTile
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
import scenes.LevelScene.Companion.TABLE_PX_W
import scenes.WorldSelectionScene
import ui.Assets.bitcountSingle

/**
 * UI drawing methods for WorldPlaceholderScene.
 * Contains all methods for rendering UI elements.
 */
internal object WorldPlaceholderSceneUI {

    /**
     * Configuration object for WorldPlaceholderScene.
     */
    object WorldPlaceholderConfig {
        /** Font size used for the scene title. */
        const val TITLE_TEXT_SIZE = 56.0

        /** Top offset for the scene title. */
        const val TITLE_Y = 60.0

        /** Font size used for the opened-world label below the title. */
        const val WORLD_LABEL_TEXT_SIZE = 28.0

        /** Top offset for the opened-world label. */
        const val WORLD_LABEL_Y = 150.0

        /** Side length of one square table-tile button. */
        const val TILE_SIZE = 130.0

        /** Gap between adjacent table tiles. */
        const val TILE_GAP = 14.0

        /** Number of table-tile columns shown in the grid. */
        const val TILE_GRID_COLUMNS = 3

        /** Total width of the tile grid, used for centering. */
        const val TILE_GRID_TOTAL_WIDTH =
            TILE_GRID_COLUMNS * TILE_SIZE + (TILE_GRID_COLUMNS - 1) * TILE_GAP

        /** Left inset for the table-tile grid. */
        const val TILE_GRID_X = TABLE_PX_W / 2.0 - TILE_GRID_TOTAL_WIDTH / 2.0

        /** Top offset for the table-tile grid. */
        const val TILE_GRID_Y = 220.0

        /** Top offset for the "Select table tile:" heading above the grid. */
        const val TILE_GRID_HEADING_Y = TILE_GRID_Y - 40.0

        /** Font size used for table-tile labels. */
        const val TILE_TEXT_SIZE = 20.0

        /** Width of the tile-selection action button. */
        const val TILE_ACTION_BUTTON_W = 240.0

        /** Horizontal gap between action buttons. */
        const val ACTION_BUTTON_ROW_GAP = 20.0

        /** Height of bordered text buttons in this scene. */
        const val ACTION_BUTTON_H = 64.0

        /** Font size used for bordered text button labels. */
        const val ACTION_BUTTON_TEXT_SIZE = 28.0

        /** Font size used for status/error text below the tile actions. */
        const val MESSAGE_TEXT_SIZE = 22.0

        /** Stroke thickness shared by bordered boxes and buttons in this scene. */
        const val STROKE_THICKNESS = 2.0

        /** Label for the table-tile grid heading. */
        const val TILE_GRID_HEADING_TEXT = "Tisch-Kachel wählen:"

        /** Color used to mark a tile already occupied by another table. */
        val OCCUPIED_TILE_COLOR = Colors["#F7C9C9"]

        /** Color used to mark the local table's claimed tile while not yet confirmed. */
        val OWN_TILE_PENDING_COLOR = Colors["#CFE3FA"]

        /** Color used to mark the local table's confirmed tile. */
        val OWN_TILE_CONFIRMED_COLOR = Colors["#CFEAD4"]

        /** Color used for tile-registration errors. */
        val ERROR_TEXT_COLOR = Colors["#B00020"]
    }


    /**
     * Draws the title bar of the scene.
     *
     * @param container The container to draw in.
     * @param title Title text.
     * @param worldLabel Label of the opened world.
     */
    fun drawTitleBar(container: Container, title: String, worldLabel: String) {
        container.text(
            title,
            textSize = WorldPlaceholderConfig.TITLE_TEXT_SIZE,
            color = Colors.BLACK,
            font = bitcountSingle
        ) {
            centerXOn(container)
            y = WorldPlaceholderConfig.TITLE_Y
        }

        container.text(
            worldLabel,
            textSize = WorldPlaceholderConfig.WORLD_LABEL_TEXT_SIZE,
            color = Colors.BLACK,
            font = bitcountSingle
        ) {
            centerXOn(container)
            y = WorldPlaceholderConfig.WORLD_LABEL_Y
        }
    }

    /**
     * Draws the table tile grid for the opened world.
     *
     * @param container The container to draw in.
     * @param worldId ID of the current world.
     * @param confirmedTileId ID of the confirmed tile, or null if none.
     * @param onTileSelected Callback for tile selection.
     * @return A container grouping the grid.
     */
    fun drawTileGrid(
        container: Container,
        worldId: String,
        confirmedTileId: String?,
        onTileSelected: (String) -> Unit
    ): Container {
        val world = WorldSelectionScene.openedWorld ?: return container.container { }
        val columns = world.columns.coerceAtLeast(1)
        val rows = world.rows.coerceAtLeast(1)
        val gridWidth = tileGridWidth(columns)
        val gridLeftX = TABLE_PX_W / 2.0 - gridWidth / 2.0

        return container.container {
            drawTileGridHeading(this)
            drawTileGridCells(this, worldId, confirmedTileId, columns, rows, gridLeftX, onTileSelected)
        }
    }

    private fun drawTileGridHeading(container: Container) {
        container.text(
            WorldPlaceholderConfig.TILE_GRID_HEADING_TEXT,
            textSize = WorldPlaceholderConfig.ACTION_BUTTON_TEXT_SIZE,
            color = Colors.BLACK,
            font = bitcountSingle
        ) {
            y = WorldPlaceholderConfig.TILE_GRID_HEADING_Y
            x = TABLE_PX_W / 2.0 - width / 2.0
        }
    }

    private fun drawTileGridCells(
        container: Container, worldId: String, confirmedTileId: String?, columns: Int,
        rows: Int, gridLeftX: Double, onTileSelected: (String) -> Unit
    ) {
        val ownTileId = ServerConnection.ownTileIdFor(worldId)
        ServerConnection.tilesFor(worldId).take(tileCapacity(columns, rows)).forEachIndexed { index, tile ->
            drawTileGridCell(container, worldId, tile, index, columns, gridLeftX, confirmedTileId, ownTileId, onTileSelected)
        }
    }

    private fun drawTileGridCell(
        container: Container,
        worldId: String,
        tile: TableTile,
        index: Int,
        columns: Int,
        gridLeftX: Double,
        confirmedTileId: String?,
        ownTileId: String?,
        onTileSelected: (String) -> Unit
    ) {
        when (tile.id) {
            confirmedTileId -> drawOwnTableTile(container, tile.id, index, columns, gridLeftX, confirmed = true)
            ownTileId -> drawOwnTableTile(container, tile.id, index, columns, gridLeftX, confirmed = false)
            else -> drawTableTile(
                container,
                worldId,
                tile,
                index,
                columns,
                gridLeftX,
                selectable = confirmedTileId == null,
                onTileSelected = onTileSelected
            )
        }
    }

    /**
     * Draws one grid cell for a tile in the world.
     *
     * @param container The container to draw in.
     * @param worldId ID of the current world.
     * @param tile The tile data.
     * @param index Position of the tile in the grid.
     * @param columns Number of columns in the grid.
     * @param gridLeftX Left edge of the grid, used for centering.
     * @param onTileSelected Callback for tile selection.
     */
    private fun drawTableTile(
        container: Container,
        worldId: String,
        tile: TableTile,
        index: Int,
        columns: Int,
        gridLeftX: Double,
        selectable: Boolean,
        onTileSelected: (String) -> Unit
    ) {
        val fillColor = if (tile.occupiedByOtherTable) WorldPlaceholderConfig.OCCUPIED_TILE_COLOR else Colors.WHITE
        val label = if (tile.occupiedByOtherTable) tile.id else "frei"
        val tileContainer = drawTileBox(container, index, columns, gridLeftX, fillColor, label)
        if (!tile.occupiedByOtherTable && selectable) {
            tileContainer.onClickWithSound { onTileSelected(tile.id) }
        }
    }

    /**
     * Draws a grid cell for a tile in the world.
     *
     * @param container The container to draw in.
     * @param index Position of the tile in the grid.
     * @param columns Number of columns in the grid.
     * @param gridLeftX Left edge of the grid, used for centering.
     */
    private fun drawTileBox(
        container: Container,
        index: Int,
        columns: Int,
        gridLeftX: Double,
        fillColor: korlibs.image.color.RGBA,
        label: String
    ): Container {
        val (col, row) = gridPosition(index, columns)
        val x = gridLeftX +
            col * (WorldPlaceholderConfig.TILE_SIZE + WorldPlaceholderConfig.TILE_GAP)
        val y = WorldPlaceholderConfig.TILE_GRID_Y +
            row * (WorldPlaceholderConfig.TILE_SIZE + WorldPlaceholderConfig.TILE_GAP)
        return container.container {
            xy(x, y)
            graphics {
                fill(fillColor) {
                    rect(0.0, 0.0, WorldPlaceholderConfig.TILE_SIZE, WorldPlaceholderConfig.TILE_SIZE)
                }
                stroke(Colors.BLACK, StrokeInfo(thickness = WorldPlaceholderConfig.STROKE_THICKNESS)) {
                    rect(0.0, 0.0, WorldPlaceholderConfig.TILE_SIZE, WorldPlaceholderConfig.TILE_SIZE)
                }
            }
            text(label, textSize = WorldPlaceholderConfig.TILE_TEXT_SIZE, color = Colors.BLACK, font = bitcountSingle) {
                centerOn(this@container)
            }
        }
    }

    /**
     * Draws the grid cell representing the local table's own claimed tile.
     *
     * @param container The container to draw in.
     * @param ownTileId Identifier of the tile claimed by the local table.
     * @param index Position of the tile in the grid.
     * @param columns Number of columns in the grid.
     * @param gridLeftX Left edge of the grid, used for centering.
     * @param confirmed Whether the tile selection is confirmed.
     * @return The tile container.
     */
    fun drawOwnTableTile(
        container: Container,
        ownTileId: String,
        index: Int,
        columns: Int,
        gridLeftX: Double,
        confirmed: Boolean
    ): Container {
        val fillColor = if (confirmed) WorldPlaceholderConfig.OWN_TILE_CONFIRMED_COLOR
                        else WorldPlaceholderConfig.OWN_TILE_PENDING_COLOR
        return drawTileBox(container, index, columns, gridLeftX, fillColor, "Dein Tisch\n(${ServerConnection.OWN_TABLE_ID})")
    }

    /**
     * Draws the "Select" / "Remove" button below the tile grid.
     *
     * @param container The container to draw in.
     * @param worldId ID of the current world.
     * @param hasSelection Whether a tile is selected.
     * @param confirmed Whether the selection is confirmed.
     * @param onConfirm Callback for confirming the selection.
     * @param onRemove Callback for removing the selection.
     * @param onNavigate Callback for navigation.
     */
    fun drawTileSelectionActionButton(
        container: Container,
        worldId: String,
        hasSelection: Boolean,
        confirmed: Boolean,
        onConfirm: () -> Unit,
        onRemove: () -> Unit,
        onNavigate: () -> Unit
    ) {
        val gridCenterX = TABLE_PX_W / 2.0
        val y = tileActionRowY(worldId)

        if (confirmed) {
            drawConfirmedTileButtons(container, gridCenterX, y, onRemove, onNavigate)
        } else {
            drawPendingTileButton(container, gridCenterX, y, hasSelection, onConfirm)
        }
    }

    /** Returns the Y position of the action-button row directly below the tile grid. */
    private fun tileActionRowY(worldId: String): Double {
        val world = WorldSelectionScene.openedWorld
        val columns = world?.columns?.coerceAtLeast(1) ?: WorldPlaceholderConfig.TILE_GRID_COLUMNS
        val rows = world?.rows?.coerceAtLeast(1) ?: tileRowCount(worldId, columns)
        val gridBottom = WorldPlaceholderConfig.TILE_GRID_Y +
            rows * (WorldPlaceholderConfig.TILE_SIZE + WorldPlaceholderConfig.TILE_GAP)
        return gridBottom + WorldPlaceholderConfig.TILE_GAP
    }

    /** Draws a centered tile-selection status or error message below the action row. */
    fun drawTileSelectionMessage(container: Container, worldId: String, message: String?) {
        if (message.isNullOrBlank()) return

        val y = tileActionRowY(worldId) +
            WorldPlaceholderConfig.ACTION_BUTTON_H + WorldPlaceholderConfig.TILE_GAP

        container.text(
            message,
            textSize = WorldPlaceholderConfig.MESSAGE_TEXT_SIZE,
            color = WorldPlaceholderConfig.ERROR_TEXT_COLOR,
            font = bitcountSingle
        ) {
            centerXOn(container)
            this.y = y
        }
    }

    private fun drawConfirmedTileButtons(
        container: Container,
        gridCenterX: Double,
        y: Double,
        onRemove: () -> Unit,
        onNavigate: () -> Unit
    ) {
        val pairWidth = WorldPlaceholderConfig.TILE_ACTION_BUTTON_W * 2 +
            WorldPlaceholderConfig.ACTION_BUTTON_ROW_GAP
        val leftX = gridCenterX - pairWidth / 2.0
        val rightX = leftX + WorldPlaceholderConfig.TILE_ACTION_BUTTON_W +
            WorldPlaceholderConfig.ACTION_BUTTON_ROW_GAP

        drawBorderedTextButton(container, leftX, y, WorldPlaceholderConfig.TILE_ACTION_BUTTON_W,
            WorldPlaceholderConfig.ACTION_BUTTON_H, "Entfernen").onClickWithSound { onRemove() }
        drawBorderedTextButton(container, rightX, y, WorldPlaceholderConfig.TILE_ACTION_BUTTON_W,
            WorldPlaceholderConfig.ACTION_BUTTON_H, "Weiter").onClickWithSound { onNavigate() }
    }

    private fun drawPendingTileButton(
        container: Container,
        gridCenterX: Double,
        y: Double,
        hasSelection: Boolean,
        onConfirm: () -> Unit
    ) {
        val x = gridCenterX - WorldPlaceholderConfig.TILE_ACTION_BUTTON_W / 2.0
        val button = drawBorderedTextButton(container, x, y, WorldPlaceholderConfig.TILE_ACTION_BUTTON_W,
            WorldPlaceholderConfig.ACTION_BUTTON_H, "Auswählen", disabled = !hasSelection)
        if (hasSelection) {
            button.onClickWithSound { onConfirm() }
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
            WorldPlaceholderConfig.ACTION_BUTTON_TEXT_SIZE,
            disabled,
            WorldPlaceholderConfig.STROKE_THICKNESS
        )
    }

    /**
     * Returns the zero-based (column, row) grid position for a tile at the given index.
     *
     * @param index Position of the tile in reading order.
     * @return The column and row for the index.
     */
    private fun gridPosition(index: Int, columns: Int): Pair<Int, Int> {
        val safeColumns = columns.coerceAtLeast(1)
        val col = index % safeColumns
        val row = index / safeColumns
        return col to row
    }

    /**
     * Returns the width of the tile grid for the given number of columns.
     *
     * @param columns Number of columns in the grid.
     * @return The grid width.
     */
    private fun tileGridWidth(columns: Int): Double =
        columns * WorldPlaceholderConfig.TILE_SIZE + (columns - 1) * WorldPlaceholderConfig.TILE_GAP

    private fun tileCapacity(columns: Int, rows: Int): Int =
        columns.coerceAtLeast(1) * rows.coerceAtLeast(1)

    /**
     * Returns the number of grid rows needed for the given world's tiles.
     *
     * @param worldId ID of the current world.
     * @return The row count.
     */
    private fun tileRowCount(worldId: String, columns: Int): Int {
        val world = WorldSelectionScene.openedWorld
        val tileCount = if (world != null) {
            minOf(ServerConnection.tilesFor(worldId).size, tileCapacity(world.columns, world.rows))
        } else {
            ServerConnection.tilesFor(worldId).size
        }
        val safeColumns = columns.coerceAtLeast(1)
        return (tileCount + safeColumns - 1) /
            safeColumns
    }


}
