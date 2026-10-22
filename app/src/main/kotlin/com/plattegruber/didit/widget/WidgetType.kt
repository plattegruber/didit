package com.plattegruber.didit.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import com.plattegruber.didit.R

/** The kinds of text the widget draws, at their design sizes (sp) and weights. */
enum class TextRole(val sizeSp: Float, val weight: Int, val isLabel: Boolean) {
    /** The "didit" word mark. */
    Wordmark(15f, BOLD, isLabel = false),

    /** Habit names. */
    Name(13f, MEDIUM, isLabel = false),
    Total(16f, MEDIUM, isLabel = false),

    /** The total in a single-habit calendar, where room is tighter. */
    SmallTotal(14f, MEDIUM, isLabel = false),
    Message(13f, REGULAR, isLabel = false),

    /** The date range, and the weeks a single habit shows. */
    Label(11f, REGULAR, isLabel = true),

    /** Units under totals, choice names, weekday letters. */
    Caption(10f, REGULAR, isLabel = true),
}

private const val REGULAR = 400
private const val MEDIUM = 500
private const val BOLD = 700

/** Today's weekday letter stands out by weight. */
internal const val STRONG_WEIGHT = 600

/** Text measured in dp, exactly as [WidgetType] will draw it. */
interface TextMetrics {
    fun width(text: String, role: TextRole): Float

    /** From the top of the tallest letter to the bottom of the lowest. */
    fun lineHeight(role: TextRole): Float
}

/**
 * The widget's fonts at the user's chosen size, which is applied on top of the phone's own font
 * size setting. Measuring and drawing go through the same paints, so layout is exact.
 */
class WidgetType(context: Context, config: WidgetConfig) : TextMetrics {
    private val metrics = context.resources.displayMetrics
    private val density = metrics.density
    private val scale = config.textSize.scale
    private val text = Face.text(context, config.font)
    private val label = if (config.labelFont ==
        WidgetConfig.LabelFont.SameAsText
    ) {
        text
    } else {
        Face.label(context, config.labelFont)
    }
    private val paints = TextRole.entries.associateWith { role -> paint(role) }

    private fun paint(role: TextRole): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, role.sizeSp * scale, metrics)
        face(role).applyTo(this, role.weight)
    }

    private fun face(role: TextRole) = if (role.isLabel) label else text

    /** A paint for [role], in pixels; callers set its colour and alignment. */
    fun paint(role: TextRole, weight: Int = role.weight): Paint = if (weight ==
        role.weight
    ) {
        paints.getValue(role)
    } else {
        Paint(paints.getValue(role)).also { face(role).applyTo(it, weight) }
    }

    /** Makes [paint] (already in the label face) stand out, for today's weekday letter. */
    fun strengthenLabel(paint: Paint) = label.applyTo(paint, STRONG_WEIGHT)

    val labelTypeface: Typeface get() = label.typeface

    /** Distance from a line's top to its baseline, in pixels. */
    fun baseline(role: TextRole): Float = -paints.getValue(role).ascent()

    override fun width(text: String, role: TextRole): Float = paints.getValue(role).measureText(text) / density

    override fun lineHeight(role: TextRole): Float = paints.getValue(role).let { it.descent() - it.ascent() } / density
}

/**
 * A typeface and how to weight it: bundled faces are variable, weighted along their 'wght' axis;
 * system faces are weighted the way the platform allows.
 */
private class Face(val typeface: Typeface, private val variable: Boolean) {
    fun applyTo(paint: Paint, weight: Int) {
        if (variable) {
            paint.typeface = typeface
            paint.fontVariationSettings = "'wght' $weight"
        } else {
            paint.typeface =
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> Typeface.create(typeface, weight, false)
                    weight >= STRONG_WEIGHT -> Typeface.create(typeface, Typeface.BOLD)
                    else -> typeface
                }
        }
    }

    companion object {
        fun text(context: Context, font: WidgetConfig.TextFont): Face = when (font) {
            WidgetConfig.TextFont.System -> Face(Typeface.SANS_SERIF, variable = false)
            WidgetConfig.TextFont.Serif -> Face(Typeface.SERIF, variable = false)
            WidgetConfig.TextFont.Geist -> bundled(context, R.font.geist, Typeface.SANS_SERIF)
            WidgetConfig.TextFont.Inter -> bundled(context, R.font.inter, Typeface.SANS_SERIF)
            WidgetConfig.TextFont.SpaceGrotesk -> bundled(context, R.font.space_grotesk, Typeface.SANS_SERIF)
            WidgetConfig.TextFont.Manrope -> bundled(context, R.font.manrope, Typeface.SANS_SERIF)
            WidgetConfig.TextFont.Fraunces -> bundled(context, R.font.fraunces, Typeface.SERIF)
        }

        fun label(context: Context, font: WidgetConfig.LabelFont): Face = when (font) {
            WidgetConfig.LabelFont.GeistMono -> bundled(context, R.font.geist_mono, Typeface.MONOSPACE)
            WidgetConfig.LabelFont.JetBrainsMono -> bundled(context, R.font.jetbrains_mono, Typeface.MONOSPACE)
            WidgetConfig.LabelFont.SystemMono -> Face(Typeface.MONOSPACE, variable = false)
            WidgetConfig.LabelFont.SameAsText -> error("same as text is resolved by the caller")
        }

        /** Bundled fonts cover Latin; anything else falls back to the system's fonts. */
        private fun bundled(context: Context, @FontRes id: Int, fallback: Typeface): Face =
            runCatching { ResourcesCompat.getFont(context, id) }
                .getOrNull()
                ?.let { Face(it, variable = true) }
                ?: Face(fallback, variable = false)
    }
}
