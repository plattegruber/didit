package com.plattegruber.didit.ui.viz

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.withSave
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.core.domain.viz.DayColumn
import com.plattegruber.didit.core.domain.viz.Glyph

/** What a drawing leaves out; the widget lets people hide these. */
data class GlyphOptions(
    /** The column highlights behind today and weekends. */
    val highlights: Boolean = true,
    /** Faint placeholders for empty days, and the ring of a yes/no day not done. */
    val emptyDays: Boolean = true,
)

/**
 * Draws scenes onto a plain Android [Canvas]. The app (through Compose's native canvas) and the
 * widget (through bitmaps) share this one implementation, so they can never drift apart.
 *
 * All scene sizes are in dp; [unit] is the number of pixels per dp. Labels are [labelSize] pixels.
 */
class GlyphPainter(
    private val palette: GlyphPalette,
    private val unit: Float,
    labelTypeface: Typeface,
    labelSize: Float = LABEL_SIZE * unit,
    private val options: GlyphOptions = GlyphOptions(),
    private val strongLabelWeight: (Paint) -> Unit = { it.fontVariationSettings = STRONG_WEIGHT },
) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val label =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = labelTypeface
            textSize = labelSize
        }
    private val strongLabel = Paint(label).also(strongLabelWeight)
    private val rect = RectF()
    private val clip = Path()

    /**
     * Rows of [rowHeight] dp, with a band of weekday letters [lettersHeight] dp tall underneath
     * (none when zero).
     */
    fun drawRows(
        canvas: Canvas,
        scene: RowsScene,
        width: Float,
        rowHeight: Float = RowsScene.ROW_HEIGHT,
        lettersHeight: Float = RowsScene.LETTERS_HEIGHT,
    ) {
        val columns = scene.days.size
        if (columns == 0) return
        val gap = RowsScene.COLUMN_GAP * unit
        val columnWidth = (width - gap * (columns - 1)) / columns
        val rowPixels = rowHeight * unit
        val lettersPixels = lettersHeight * unit
        val height = scene.rows.size * rowPixels + lettersPixels
        val metrics = GlyphMetrics.Rows.scaled(unit)

        scene.days.forEachIndexed { index, day ->
            val left = index * (columnWidth + gap)
            drawBand(canvas, day, left, 0f, columnWidth, height, RowsScene.BAND_RADIUS * unit)
            scene.rows.forEachIndexed { row, glyphs ->
                drawGlyph(canvas, glyphs[index], left, row * rowPixels, columnWidth, rowPixels, metrics)
            }
            if (lettersPixels > 0f) {
                drawLabel(
                    canvas,
                    scene.letters[index],
                    left + columnWidth / 2,
                    scene.rows.size * rowPixels + lettersPixels / 2,
                    strong = day.isToday,
                    align = Paint.Align.CENTER,
                )
            }
        }
    }

    /** A strip [height] pixels tall, with its week labels in a band [labelHeight] dp tall. */
    fun drawStrip(
        canvas: Canvas,
        scene: StripScene,
        width: Float,
        height: Float,
        labelHeight: Float = StripScene.LABEL_HEIGHT,
    ) {
        val columns = scene.days.size
        if (columns == 0) return
        val gap = StripScene.COLUMN_GAP * unit
        val columnWidth = (width - gap * (columns - 1)) / columns
        val labelSpace = if (scene.labels.isEmpty()) 0f else (StripScene.LABEL_GAP + labelHeight) * unit
        val barsHeight = height - labelSpace
        val metrics = GlyphMetrics.Strip.scaled(unit)

        scene.days.forEachIndexed { index, day ->
            val left = index * (columnWidth + gap)
            drawBand(canvas, day, left, 0f, columnWidth, barsHeight, StripScene.BAND_RADIUS * unit)
            drawGlyph(canvas, scene.glyphs[index], left, 0f, columnWidth, barsHeight, metrics)
        }
        scene.labels.forEach { (index, text) ->
            drawLabel(
                canvas,
                text,
                index * (columnWidth + gap),
                height - labelHeight * unit / 2,
                strong = false,
                align = Paint.Align.LEFT,
            )
        }
    }

    fun drawCalendar(
        canvas: Canvas,
        scene: CalendarScene,
        width: Float,
        metrics: GlyphMetrics = GlyphMetrics.Calendar,
    ) {
        val columnWidth = scene.columnWidth(width, unit)
        val rowHeight = scene.rowHeight * unit
        val scaled = metrics.scaled(unit)

        scene.letters.forEachIndexed { column, letter ->
            val x = scene.cellLeft(column, width, unit) + columnWidth / 2
            drawLabel(
                canvas,
                letter,
                x,
                CalendarScene.HEADER_HEIGHT * unit / 2,
                strong = false,
                align = Paint.Align.CENTER,
            )
        }
        for ((cell, glyph) in scene.cells) {
            val left = scene.cellLeft(cell.column, width, unit)
            val top = scene.cellTop(cell.row, unit)
            if (cell.isToday && options.highlights) {
                val radius = CalendarScene.BAND_RADIUS * unit
                fillRoundRect(canvas, left, top, columnWidth, rowHeight, radius, Tone.BandToday)
            }
            if (scene.showDayNumbers) {
                drawLabel(
                    canvas,
                    cell.date.dayOfMonth.toString(),
                    left + columnWidth / 2,
                    top + DAY_NUMBER_CENTER * unit,
                    strong = cell.isToday,
                    align = Paint.Align.CENTER,
                    tone = if (cell.isFuture) Tone.Ring else null,
                )
            }
            if (!cell.isFuture) drawGlyph(canvas, glyph, left, top, columnWidth, rowHeight, scaled)
        }
    }

    /**
     * A bar split in proportion to [parts], with hairline gaps between colours: the at-a-glance
     * mix of a choice habit. Sizes are in pixels.
     */
    fun drawMix(canvas: Canvas, parts: List<Pair<Swatch, Int>>, width: Float, height: Float) {
        val visible = parts.filter { it.second > 0 }
        val total = visible.sumOf { it.second }
        if (total == 0) return
        val gap = MIX_GAP * unit
        val usable = width - gap * (visible.size - 1)
        val radius = height / 2
        canvas.withSave {
            rect.set(0f, 0f, width, height)
            clip.reset()
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
            canvas.clipPath(clip)
            var left = 0f
            for ((swatch, count) in visible) {
                val segment = usable * count / total
                palette.swatch(swatch)?.let { color ->
                    fill.color = color
                    canvas.drawRect(left, 0f, left + segment, height, fill)
                }
                left += segment + gap
            }
        }
    }

    /** Draws one day's glyph inside the cell at ([left], [top]); [metrics] must already be in pixels. */
    fun drawGlyph(
        canvas: Canvas,
        glyph: Glyph,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        metrics: GlyphMetrics,
    ) {
        val centerX = left + width / 2
        val bottom = top + height
        when (glyph) {
            is Glyph.Dots -> {
                val colors = glyph.swatches.map(palette::swatch).ifEmpty { listOf(emptyMark(Tone.Faint)) }
                drawStack(
                    canvas,
                    colors,
                    centerX,
                    bottom,
                    height,
                    size = metrics.dot,
                    gap = metrics.dotGap,
                    bottomPadding = metrics.dotBottom,
                    topPadding = metrics.top,
                )
            }

            is Glyph.Pips -> {
                val colors =
                    if (glyph.count > 0) {
                        List(glyph.count) { palette.tone(Tone.Ink) }
                    } else {
                        listOf(emptyMark(Tone.Faint))
                    }
                drawStack(
                    canvas,
                    colors,
                    centerX,
                    bottom,
                    height,
                    size = metrics.pip,
                    gap = metrics.pipGap,
                    bottomPadding = metrics.pipBottom,
                    topPadding = metrics.top,
                )
            }

            is Glyph.Bar -> {
                val barBottom = bottom - metrics.barBottom
                val room = (height - metrics.barBottom - metrics.top).coerceAtLeast(metrics.barMin)
                val (barHeight, tone) =
                    if (glyph.fraction > 0f) {
                        val max = metrics.barMax.coerceAtMost(room)
                        metrics.barMin + glyph.fraction * (max - metrics.barMin) to Tone.Ink
                    } else {
                        if (!options.emptyDays) return
                        metrics.barEmpty to Tone.Faint
                    }
                fillRoundRect(
                    canvas,
                    centerX - metrics.barWidth / 2,
                    barBottom - barHeight,
                    metrics.barWidth,
                    barHeight,
                    metrics.barWidth / 2,
                    tone,
                )
            }

            is Glyph.Check -> {
                val centerY = top + height / 2
                if (glyph.done) {
                    val color = palette.tone(Tone.Ink) ?: return
                    fill.color = color
                    canvas.drawCircle(centerX, centerY, metrics.check / 2, fill)
                } else {
                    val color = emptyMark(Tone.Ring) ?: return
                    stroke.color = color
                    stroke.strokeWidth = metrics.checkStroke
                    canvas.drawCircle(centerX, centerY, (metrics.check - metrics.checkStroke) / 2, stroke)
                }
            }
        }
    }

    /**
     * Circles stacked bottom-up. When a day holds more than the cell fits, the circles shrink
     * together rather than spilling out of the cell.
     */
    private fun drawStack(
        canvas: Canvas,
        colors: List<Int?>,
        centerX: Float,
        bottom: Float,
        height: Float,
        size: Float,
        gap: Float,
        bottomPadding: Float,
        topPadding: Float,
    ) {
        val count = colors.size
        val available = height - bottomPadding - topPadding
        val natural = count * size + (count - 1) * gap
        val scale = if (natural > available && natural > 0f) available / natural else 1f
        val diameter = size * scale
        val spacing = gap * scale
        colors.forEachIndexed { index, color ->
            if (color == null) return@forEachIndexed
            fill.color = color
            val centerY = bottom - bottomPadding - diameter / 2 - index * (diameter + spacing)
            canvas.drawCircle(centerX, centerY, diameter / 2, fill)
        }
    }

    private fun emptyMark(tone: Tone): Int? = if (options.emptyDays) palette.tone(tone) else null

    private fun drawBand(
        canvas: Canvas,
        day: DayColumn,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        radius: Float,
    ) {
        if (!options.highlights) return
        val tone =
            when {
                day.isToday -> Tone.BandToday
                day.isWeekend -> Tone.BandWeekend
                else -> return
            }
        fillRoundRect(canvas, left, top, width, height, radius, tone)
    }

    private fun fillRoundRect(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        radius: Float,
        tone: Tone,
    ) {
        val color = palette.tone(tone) ?: return
        fill.color = color
        rect.set(left, top, left + width, top + height)
        canvas.drawRoundRect(rect, radius, radius, fill)
    }

    /** Labels are ink when [strong], else the label shade, unless [tone] says otherwise. */
    private fun drawLabel(
        canvas: Canvas,
        text: String,
        x: Float,
        centerY: Float,
        strong: Boolean,
        align: Paint.Align,
        tone: Tone? = null,
    ) {
        val paint = if (strong) strongLabel else label
        val color = palette.labelTone(tone ?: if (strong) Tone.Ink else Tone.Label) ?: return
        paint.color = color
        paint.textAlign = align
        val baseline = centerY - (paint.ascent() + paint.descent()) / 2
        canvas.drawText(text, x, baseline, paint)
    }

    companion object {
        /** Label size in dp. */
        const val LABEL_SIZE = 10f
        private const val STRONG_WEIGHT = "'wght' 600"
        private const val MIX_GAP = 1f

        /** Where a month cell's day number sits, measured from the cell's top. */
        private const val DAY_NUMBER_CENTER = 8f
    }
}
