package org.zenconverter.app.conversion

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Pure layout math shared by the editor, preview, and final renderer. */
object ContactSheetGeometry {
    const val MIN_ROWS = 1
    const val MAX_ROWS = 10
    const val MIN_COLUMNS = 1
    const val MAX_COLUMNS = 10
    const val MAX_FRAME_COUNT = 100
    const val MIN_CANVAS_WIDTH = 320
    const val MAX_CANVAS_WIDTH = 8192
    const val MIN_CELL_WIDTH = 80
    const val MAX_CELL_WIDTH = 2048
    const val MIN_CELL_HEIGHT = 80
    const val MAX_CELL_HEIGHT = 2048
    const val MIN_GAP = 0
    const val MAX_GAP = 256
    const val MIN_MARGIN = 0
    const val MAX_MARGIN = 256
    const val MIN_HEADER_HEIGHT = 96
    const val MAX_HEADER_HEIGHT = 512
    const val MAX_DIMENSION = 8192
    const val MAX_PIXELS = 32_000_000L

    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    data class Result(
        val width: Int,
        val height: Int,
        val cellWidth: Int,
        val cellHeight: Int,
        val headerHeight: Int,
        val rows: Int,
        val columns: Int,
        val frameCount: Int,
        val cells: List<Rect>,
        val violations: List<String>
    ) {
        val isValid: Boolean get() = violations.isEmpty()
        val pixelCount: Long get() = width.toLong() * height.toLong()
    }

    fun calculate(
        options: VideoContactSheetOptions,
        sourceWidth: Int,
        sourceHeight: Int
    ): Result {
        val rows = options.rows.coerceIn(MIN_ROWS, MAX_ROWS)
        val columns = options.columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        val frameCount = (rows * columns).coerceAtMost(MAX_FRAME_COUNT)
        val gap = options.gapPx.coerceIn(MIN_GAP, MAX_GAP)
        val margin = options.outerMarginPx.coerceIn(MIN_MARGIN, MAX_MARGIN)
        val aspect = if (sourceWidth > 0 && sourceHeight > 0) {
            sourceHeight.toFloat() / sourceWidth.toFloat()
        } else {
            9f / 16f
        }
        val requestedCellWidth = options.cellWidthPx.coerceIn(MIN_CELL_WIDTH, MAX_CELL_WIDTH)
        val requestedCanvasWidth = options.canvasWidthPx.coerceIn(MIN_CANVAS_WIDTH, MAX_CANVAS_WIDTH)
        val minGridWidth = columns * MIN_CELL_WIDTH + (columns - 1) * gap

        val cellWidth: Int
        val width: Int
        when (options.widthMode) {
            ContactSheetWidthMode.Canvas -> {
                width = max(requestedCanvasWidth, margin * 2 + minGridWidth)
                val available = width - margin * 2 - (columns - 1) * gap
                cellWidth = (available.toFloat() / columns.toFloat()).toInt()
                    .coerceIn(MIN_CELL_WIDTH, MAX_CELL_WIDTH)
            }
            ContactSheetWidthMode.Cell -> {
                cellWidth = requestedCellWidth
                width = margin * 2 + columns * cellWidth + (columns - 1) * gap
            }
        }

        val cellHeight = when (options.cellHeightMode) {
            ContactSheetCellHeightMode.AspectRatio ->
                ceil(cellWidth * aspect).toInt().coerceAtLeast(MIN_CELL_HEIGHT)
            ContactSheetCellHeightMode.Fixed ->
                options.cellHeightPx.coerceIn(MIN_CELL_HEIGHT, MAX_CELL_HEIGHT)
        }
        val headerHeight = if (options.includeHeader) {
            options.headerHeightPx.coerceIn(MIN_HEADER_HEIGHT, MAX_HEADER_HEIGHT)
        } else {
            0
        }
        val gridWidth = columns * cellWidth + (columns - 1) * gap
        val gridHeight = rows * cellHeight + (rows - 1) * gap
        val height = headerHeight + margin * 2 + gridHeight
        val startX = when (options.alignment) {
            ContactSheetAlignment.Start -> margin
            ContactSheetAlignment.Center -> ((width - gridWidth) / 2f).roundToInt()
            ContactSheetAlignment.End -> width - margin - gridWidth
        }.coerceAtLeast(0)
        val cells = buildList(frameCount) {
            repeat(frameCount) { index ->
                val row = index / columns
                val column = index % columns
                val left = startX + column * (cellWidth + gap)
                val top = headerHeight + margin + row * (cellHeight + gap)
                add(Rect(left, top, left + cellWidth, top + cellHeight))
            }
        }

        val violations = buildList {
            if (rows * columns > MAX_FRAME_COUNT) add("frame_count")
            if (width > MAX_DIMENSION || height > MAX_DIMENSION) add("dimension")
            if (width.toLong() * height.toLong() > MAX_PIXELS) add("pixels")
        }
        return Result(
            width = width,
            height = height,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            headerHeight = headerHeight,
            rows = rows,
            columns = columns,
            frameCount = frameCount,
            cells = cells,
            violations = violations
        )
    }
}
