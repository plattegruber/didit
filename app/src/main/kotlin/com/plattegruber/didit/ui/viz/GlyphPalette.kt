package com.plattegruber.didit.ui.viz

import androidx.core.graphics.ColorUtils
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.ui.theme.SwatchColors

/**
 * The quieter shades in the design are all ink at reduced strength, which is what lets one drawing
 * work on any ground. Each alpha is measured from the dark design (ink #ECE8DF over #111519).
 */
enum class Tone(val alpha: Float) {
    Ink(1f),

    /** Empty-day placeholders. */
    Faint(0.123f),

    /** The outline of a yes/no day not yet done. */
    Ring(0.187f),

    /** Weekday letters and date labels. */
    Label(0.557f),

    /** The column highlight behind today. */
    BandToday(0.046f),

    /** The softer column highlight behind weekends. */
    BandWeekend(0.023f),
}

/**
 * The colours a drawing uses. A null colour means "leave this part out", which is how the widget
 * splits one scene into layers: glyphs and labels as separate masks (each tinted by the launcher,
 * so they can follow the theme and differ from each other) and an accent layer whose colours never
 * change.
 */
class GlyphPalette private constructor(
    private val ink: Int?,
    private val labels: Int?,
    private val drawAccents: Boolean,
) {
    /** A glyph shape (dots, bars, rings, highlights) in [tone]. */
    fun tone(tone: Tone): Int? = ink?.withTone(tone)

    /** Weekday letters, date labels and day numbers in [tone]. */
    fun labelTone(tone: Tone): Int? = labels?.withTone(tone)

    fun swatch(swatch: Swatch): Int? = when {
        swatch == Swatch.Ink -> ink
        drawAccents -> SwatchColors.argb(swatch)
        else -> null
    }

    private fun Int.withTone(tone: Tone) = ColorUtils.setAlphaComponent(this, (tone.alpha * MAX_ALPHA).toInt())

    companion object {
        private const val MAX_ALPHA = 255
        private const val WHITE = 0xFFFFFFFF.toInt()

        /** Everything in its real colour, for drawing straight onto the screen. */
        fun full(ink: Int): GlyphPalette = GlyphPalette(ink, ink, drawAccents = true)

        /** Ink-based glyph shapes in white at their tone's strength, ready to be tinted. */
        val GlyphMask: GlyphPalette = GlyphPalette(WHITE, null, drawAccents = false)

        /** Labels in white at their tone's strength, ready to be tinted. */
        val LabelMask: GlyphPalette = GlyphPalette(null, WHITE, drawAccents = false)

        /** Only the accent-coloured shapes. */
        val AccentsOnly: GlyphPalette = GlyphPalette(null, null, drawAccents = true)
    }
}
