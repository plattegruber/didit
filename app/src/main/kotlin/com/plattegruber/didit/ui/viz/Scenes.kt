package com.plattegruber.didit.ui.viz

import com.plattegruber.didit.core.domain.viz.CalendarCell
import com.plattegruber.didit.core.domain.viz.DayColumn
import com.plattegruber.didit.core.domain.viz.Glyph

/*
 * Scenes are complete drawings, sized in dp and turned into pixels by the painter. Each one is a
 * layout from the design; the numbers below are the design's own.
 */

/** Several habits stacked as rows over the same days, with weekday letters underneath. */
data class RowsScene(val rows: List<List<Glyph>>, val days: List<DayColumn>, val letters: List<String>) {
    init {
        require(rows.all { it.size == days.size }) { "every row needs one glyph per day" }
        require(letters.size == days.size) { "every day needs a letter" }
    }

    val height: Float get() = rows.size * ROW_HEIGHT + LETTERS_HEIGHT

    companion object {
        const val ROW_HEIGHT = 48f
        const val LETTERS_HEIGHT = 22f
        const val COLUMN_GAP = 2f
        const val BAND_RADIUS = 4f
    }
}

/** One habit as a long run of days, with the start of each week labelled underneath. */
data class StripScene(
    val glyphs: List<Glyph>,
    val days: List<DayColumn>,
    /** Labels keyed by the index of the day they start at. */
    val labels: Map<Int, String>,
) {
    init {
        require(glyphs.size == days.size) { "every day needs a glyph" }
    }

    companion object {
        const val COLUMN_GAP = 2f
        const val BAND_RADIUS = 3f
        const val LABEL_GAP = 10f
        const val LABEL_HEIGHT = 12f
    }
}

/** One habit as a weekday-by-week calendar, with weekday letters on top. */
data class CalendarScene(
    val cells: List<Pair<CalendarCell, Glyph>>,
    val letters: List<String>,
    val rows: Int,
    val rowHeight: Float = ROW_HEIGHT,
    val showDayNumbers: Boolean = false,
) {
    val height: Float get() = HEADER_HEIGHT + HEADER_GAP + rows * rowHeight + (rows - 1) * ROW_GAP

    fun columnWidth(width: Float, unit: Float): Float = (width - COLUMN_GAP * unit * (COLUMNS - 1)) / COLUMNS

    fun cellLeft(column: Int, width: Float, unit: Float): Float =
        column * (columnWidth(width, unit) + COLUMN_GAP * unit)

    fun cellTop(row: Int, unit: Float): Float = (HEADER_HEIGHT + HEADER_GAP + row * (rowHeight + ROW_GAP)) * unit

    /** The cell under a point, or null for gaps, the header and empty slots. */
    fun cellAt(x: Float, y: Float, width: Float, unit: Float): CalendarCell? {
        val columnWidth = columnWidth(width, unit)
        return cells
            .firstOrNull { (cell, _) ->
                val left = cellLeft(cell.column, width, unit)
                val top = cellTop(cell.row, unit)
                x >= left && x < left + columnWidth && y >= top && y < top + rowHeight * unit
            }?.first
    }

    companion object {
        const val COLUMNS = 7
        const val ROW_HEIGHT = 26f
        const val ROW_GAP = 2f
        const val COLUMN_GAP = 4f
        const val HEADER_HEIGHT = 12f
        const val HEADER_GAP = 6f
        const val BAND_RADIUS = 4f
    }
}

/**
 * Glyph proportions for each kind of scene, in dp. They differ because a glyph in a narrow
 * 28-day strip can't be as fat as one in a 14-day row.
 */
data class GlyphMetrics(
    val dot: Float,
    val dotGap: Float,
    val dotBottom: Float,
    val barWidth: Float,
    val barMin: Float,
    val barMax: Float,
    val barBottom: Float,
    val barEmpty: Float,
    val pip: Float,
    val pipGap: Float,
    val pipBottom: Float,
    val check: Float,
    val checkStroke: Float,
    /** Space kept clear above the tallest stack. */
    val top: Float,
) {
    fun scaled(unit: Float): GlyphMetrics = GlyphMetrics(
        dot * unit,
        dotGap * unit,
        dotBottom * unit,
        barWidth * unit,
        barMin * unit,
        barMax * unit,
        barBottom * unit,
        barEmpty * unit,
        pip * unit,
        pipGap * unit,
        pipBottom * unit,
        check * unit,
        checkStroke * unit,
        top * unit,
    )

    companion object {
        val Rows =
            GlyphMetrics(
                dot = 6f,
                dotGap = 2f,
                dotBottom = 6f,
                barWidth = 5f,
                barMin = 5f,
                barMax = 28f,
                barBottom = 10f,
                barEmpty = 4f,
                pip = 5f,
                pipGap = 2f,
                pipBottom = 10f,
                check = 9f,
                checkStroke = 1.5f,
                top = 4f,
            )

        val Strip =
            GlyphMetrics(
                dot = 4f,
                dotGap = 1.5f,
                dotBottom = 4f,
                barWidth = 4f,
                barMin = 5f,
                barMax = 48f,
                barBottom = 4f,
                barEmpty = 4f,
                pip = 4f,
                pipGap = 1.5f,
                pipBottom = 4f,
                check = 7f,
                checkStroke = 1.5f,
                top = 4f,
            )

        /** The design's 2x2 widget: five 4dp dots fill a 26dp day exactly. */
        val Calendar =
            GlyphMetrics(
                dot = 4f,
                dotGap = 1.5f,
                dotBottom = 1f,
                barWidth = 4f,
                barMin = 4f,
                barMax = 22f,
                barBottom = 2f,
                barEmpty = 3f,
                pip = 4f,
                pipGap = 1.5f,
                pipBottom = 1f,
                check = 8f,
                checkStroke = 1.5f,
                top = 1f,
            )

        /** The detail screen's month, roomy enough to tap and to carry a day number. */
        val Month =
            GlyphMetrics(
                dot = 6f,
                dotGap = 2f,
                dotBottom = 6f,
                barWidth = 6f,
                barMin = 5f,
                barMax = 26f,
                barBottom = 6f,
                barEmpty = 4f,
                pip = 5f,
                pipGap = 2f,
                pipBottom = 6f,
                check = 12f,
                checkStroke = 1.5f,
                top = 16f,
            )
    }
}
