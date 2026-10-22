package com.plattegruber.didit.widget

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue

/** The two families widget text uses, which RemoteViews resolves to the device's own fonts. */
internal enum class Face { Sans, Mono }

/** Measures widget text as the launcher will draw it: the device's fonts at the user's font scale. */
internal interface TextMeasure {
    /** Width of [text] at [sizeSp], in dp. */
    fun width(text: String, sizeSp: Float, face: Face): Float

    /** Height of one line at [sizeSp], in dp, padding included as a TextView adds it. */
    fun lineHeight(sizeSp: Float, face: Face): Float
}

internal class DeviceTextMeasure(context: Context) : TextMeasure {
    private val metrics = context.resources.displayMetrics
    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)

    private fun use(sizeSp: Float, face: Face) = paint.apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, metrics)
        typeface =
            when (face) {
                Face.Sans -> Typeface.SANS_SERIF
                Face.Mono -> Typeface.MONOSPACE
            }
    }

    override fun width(text: String, sizeSp: Float, face: Face): Float =
        use(sizeSp, face).measureText(text) / metrics.density

    override fun lineHeight(sizeSp: Float, face: Face): Float =
        use(sizeSp, face).fontMetrics.let { it.bottom - it.top } / metrics.density
}

/** A "one of" habit's labels broken into lines, and the size that lets them all fit. */
internal data class LegendFit(val lines: List<List<String>>, val sizeSp: Float)

/**
 * RemoteViews can't wrap a row, and clips whatever overflows it, so the legend is laid out up
 * front: the largest size at which every label fits within [width] and every line within [height].
 * If even the smallest size overflows, it still shows every label rather than dropping any.
 */
internal fun fitLegend(labels: List<String>, width: Float, height: Float, measure: TextMeasure): LegendFit {
    fun linesAt(size: Float) = breakLines(labels, width, LEGEND_GAP) { measure.width(it, size, Face.Mono) }
    for (size in LEGEND_SIZES) {
        val lines = linesAt(size)
        val widest = labels.maxOfOrNull { measure.width(it, size, Face.Mono) } ?: 0f
        if (widest <= width && lines.size * measure.lineHeight(size, Face.Mono) <= height) {
            return LegendFit(lines, size)
        }
    }
    val smallest = LEGEND_SIZES.last()
    return LegendFit(linesAt(smallest), smallest)
}

/** Greedily packs [labels] into lines no wider than [width], [gap] apart. */
internal fun breakLines(
    labels: List<String>,
    width: Float,
    gap: Float,
    widthOf: (String) -> Float,
): List<List<String>> {
    val lines = mutableListOf<MutableList<String>>()
    var used = 0f
    for (label in labels) {
        val labelWidth = widthOf(label)
        val line = lines.lastOrNull()
        if (line != null && used + gap + labelWidth <= width) {
            line += label
            used += gap + labelWidth
        } else {
            lines += mutableListOf(label)
            used = labelWidth
        }
    }
    return lines
}

/** Space between labels on a line, in dp. */
internal const val LEGEND_GAP = 5f

/** Legend sizes to try, largest first, in sp. */
private val LEGEND_SIZES = listOf(10f, 9f, 8f)
