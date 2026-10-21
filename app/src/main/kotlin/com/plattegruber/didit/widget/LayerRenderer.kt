package com.plattegruber.didit.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import com.plattegruber.didit.ui.viz.GlyphPainter
import com.plattegruber.didit.ui.viz.GlyphPalette
import com.plattegruber.didit.ui.viz.labelTypeface

/**
 * A drawing split in two. [ink] is white wherever the scene uses ink, at the strength of each
 * shade; the launcher tints it with the right ink for the current theme, so the widget follows
 * dark mode without being redrawn. [accents] holds the fixed-colour parts, and is null when the
 * drawing has none.
 */
class InkLayers(val ink: Bitmap, val accents: Bitmap?)

class LayerRenderer(private val context: Context) {
    private val density = context.resources.displayMetrics.density
    private val typeface by lazy { labelTypeface(context) }

    /**
     * Renders [draw] at [widthDp] x [heightDp].
     *
     * @param hasAccents whether anything in the drawing uses an accent colour; when false the
     *   accent layer is skipped entirely.
     */
    fun render(
        widthDp: Float,
        heightDp: Float,
        hasAccents: Boolean,
        draw: GlyphPainter.(canvas: Canvas, width: Float, height: Float) -> Unit,
    ): InkLayers {
        val width = (widthDp * density).toInt().coerceAtLeast(1)
        val height = (heightDp * density).toInt().coerceAtLeast(1)

        fun layer(palette: GlyphPalette): Bitmap = createBitmap(width, height).also { bitmap ->
            GlyphPainter(palette, density, typeface).draw(Canvas(bitmap), width.toFloat(), height.toFloat())
        }

        return InkLayers(
            ink = layer(GlyphPalette.InkMask),
            accents = if (hasAccents) layer(GlyphPalette.AccentsOnly) else null,
        )
    }
}
