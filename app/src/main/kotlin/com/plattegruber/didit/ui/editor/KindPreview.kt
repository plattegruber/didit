package com.plattegruber.didit.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.core.domain.viz.Glyph
import com.plattegruber.didit.ui.viz.GlyphMetrics
import com.plattegruber.didit.ui.viz.rememberGlyphPainter

/** Ten made-up days per kind, straight from the design, so each card shows what it will look like. */
private object Samples {
    private val H = Swatch.Ink
    private val T = Swatch.Blue
    private val O = Swatch.Amber

    val choice =
        listOf(
            listOf(H, H, H, H, H),
            listOf(H, H, T),
            listOf(H, H, H, H),
            listOf(O, H),
            listOf(H, T, H),
            listOf(H, O, O),
            listOf(O, T),
            listOf(H, H, H, H, H),
            listOf(H, H, H),
            listOf(H, H),
        ).map { Glyph.Dots(it) }

    private const val PAGES_SCALE = 60f
    val amount = listOf(12, 20, 0, 25, 14, 0, 52, 30, 45, 18).map { Glyph.Bar(it / PAGES_SCALE) }

    val count = listOf(1, 2, 2, 3, 2, 2, 1, 0, 2, 1).map { Glyph.Pips(it) }

    val done = listOf(1, 0, 1, 0, 1, 1, 1, 0, 1, 1).map { Glyph.Check(it == 1) }
}

private val PreviewMetrics =
    GlyphMetrics(
        dot = 4f,
        dotGap = 1.5f,
        dotBottom = 0f,
        barWidth = 4f,
        barMin = 4f,
        barMax = 26f,
        barBottom = 0f,
        barEmpty = 3f,
        pip = 5f,
        pipGap = 2f,
        pipBottom = 0f,
        check = 8f,
        checkStroke = 1.5f,
        top = 0f,
    )

private const val COLUMNS = 10
private const val COLUMN_GAP = 2f

@Composable
fun KindPreview(kind: KindChoice, modifier: Modifier = Modifier) {
    val glyphs =
        when (kind) {
            KindChoice.Choice -> Samples.choice
            KindChoice.Amount -> Samples.amount
            KindChoice.Count -> Samples.count
            KindChoice.Done -> Samples.done
        }
    val painter = rememberGlyphPainter()
    Canvas(modifier.fillMaxWidth().height(30.dp)) {
        val gap = COLUMN_GAP.dp.toPx()
        val width = (size.width - gap * (COLUMNS - 1)) / COLUMNS
        val metrics = PreviewMetrics.scaled(density)
        drawIntoCanvas { canvas ->
            glyphs.forEachIndexed { index, glyph ->
                painter.drawGlyph(canvas.nativeCanvas, glyph, index * (width + gap), 0f, width, size.height, metrics)
            }
        }
    }
}
