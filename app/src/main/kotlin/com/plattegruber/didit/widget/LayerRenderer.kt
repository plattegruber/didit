package com.plattegruber.didit.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.ui.theme.SwatchColors
import com.plattegruber.didit.ui.viz.GlyphOptions
import com.plattegruber.didit.ui.viz.GlyphPainter
import com.plattegruber.didit.ui.viz.GlyphPalette
import com.plattegruber.didit.ui.viz.Tone

/**
 * A widget drawn as three layers. [text] and [glyphs] are masks, white wherever they draw at the
 * strength of each shade: the launcher tints each for the current theme, so the widget follows
 * dark mode without being redrawn, and words and glyphs can take different colours. [accents]
 * holds the fixed choice colours, and is null when there are none.
 */
class WidgetLayers(val text: Bitmap, val glyphs: Bitmap, val accents: Bitmap?)

enum class WidgetLayer { Text, Glyphs, Accents }

/** Draws a laid-out widget, one layer at a time. Sizes in the drawing are dp; [unit] is pixels per dp. */
class WidgetPainter(private val type: WidgetType, private val unit: Float, private val options: GlyphOptions) {
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(canvas: Canvas, drawing: WidgetDrawing, layer: WidgetLayer) {
        val glyphs =
            GlyphPainter(
                palette =
                    when (layer) {
                        WidgetLayer.Text -> GlyphPalette.LabelMask
                        WidgetLayer.Glyphs -> GlyphPalette.GlyphMask
                        WidgetLayer.Accents -> GlyphPalette.AccentsOnly
                    },
                unit = unit,
                labelTypeface = type.labelTypeface,
                labelSize = type.paint(TextRole.Caption).textSize,
                options = options,
                strongLabelWeight = type::strengthenLabel,
            )
        canvas.withScale(drawing.scale, drawing.scale) {
            for (mark in drawing.marks) {
                when (mark) {
                    is Mark.Words -> {
                        words(this, mark, layer)
                    }

                    is Mark.Dot -> {
                        if (layer == WidgetLayer.Text) {
                            dot.color = WHITE
                            drawCircle(mark.x * unit, mark.y * unit, mark.radius * unit, dot)
                        }
                    }

                    is Mark.Rows -> {
                        withTranslation(mark.left * unit, mark.top * unit) {
                            glyphs.drawRows(this, mark.scene, mark.width * unit, mark.rowHeight, mark.lettersHeight)
                        }
                    }

                    is Mark.Strip -> {
                        withTranslation(mark.left * unit, mark.top * unit) {
                            glyphs.drawStrip(this, mark.scene, mark.width * unit, mark.height * unit, mark.labelHeight)
                        }
                    }

                    is Mark.Calendar -> {
                        withTranslation(mark.left * unit, mark.top * unit) {
                            glyphs.drawCalendar(this, mark.scene, mark.width * unit)
                        }
                    }

                    is Mark.Mix -> {
                        withTranslation(mark.left * unit, mark.top * unit) {
                            glyphs.drawMix(this, mark.parts, mark.width * unit, mark.height * unit)
                        }
                    }
                }
            }
        }
    }

    private fun words(canvas: Canvas, mark: Mark.Words, layer: WidgetLayer) {
        val color =
            when (mark.swatch) {
                null -> WHITE.takeIf { layer == WidgetLayer.Text }?.withAlpha(mark.tone)
                Swatch.Ink -> WHITE.takeIf { layer == WidgetLayer.Glyphs }
                else -> SwatchColors.argb(mark.swatch).takeIf { layer == WidgetLayer.Accents }
            } ?: return
        val paint = type.paint(mark.role)
        paint.color = color
        paint.textAlign =
            when (mark.align) {
                Align.Start -> Paint.Align.LEFT
                Align.Center -> Paint.Align.CENTER
                Align.End -> Paint.Align.RIGHT
            }
        canvas.drawText(mark.text, mark.x * unit, mark.top * unit + type.baseline(mark.role), paint)
    }

    private fun Int.withAlpha(tone: Tone): Int = (this and RGB) or ((tone.alpha * MAX_ALPHA).toInt() shl ALPHA_SHIFT)

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val RGB = 0x00FFFFFF
        const val MAX_ALPHA = 255
        const val ALPHA_SHIFT = 24
    }
}

/** Renders a widget's layers to bitmaps the size of the widget. */
class LayerRenderer(context: Context, config: WidgetConfig, type: WidgetType = WidgetType(context, config)) {
    private val density = context.resources.displayMetrics.density
    private val painter =
        WidgetPainter(
            type = type,
            unit = density,
            options =
                GlyphOptions(
                    highlights = config.shows(WidgetConfig.Element.Highlights),
                    emptyDays = config.shows(WidgetConfig.Element.EmptyDays),
                ),
        )

    fun render(drawing: WidgetDrawing): WidgetLayers {
        val width = (drawing.width * density).toInt().coerceAtLeast(1)
        val height = (drawing.height * density).toInt().coerceAtLeast(1)

        // Masks only need coverage, so a byte a pixel; colour is added by the tint.
        fun layer(layer: WidgetLayer, config: Bitmap.Config): Bitmap = createBitmap(width, height, config).also {
            painter.draw(Canvas(it), drawing, layer)
        }

        return WidgetLayers(
            text = layer(WidgetLayer.Text, Bitmap.Config.ALPHA_8),
            glyphs = layer(WidgetLayer.Glyphs, Bitmap.Config.ALPHA_8),
            accents = if (drawing.usesAccents) layer(WidgetLayer.Accents, Bitmap.Config.ARGB_8888) else null,
        )
    }
}
