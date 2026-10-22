package com.plattegruber.didit.widget

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.plattegruber.didit.ui.theme.DarkDiditColors
import com.plattegruber.didit.ui.theme.LightDiditColors

/** One colour for a light theme and one for a dark theme; the launcher shows whichever applies. */
data class DayNight(val day: Int, val night: Int) {
    constructor(both: Int) : this(both, both)

    fun pick(night: Boolean): Int = if (night) this.night else day

    /** Both colours at [alpha], 0 to 1. */
    fun withAlpha(alpha: Float): DayNight {
        val value = (alpha.coerceIn(0f, 1f) * MAX_ALPHA).toInt()
        return DayNight(ColorUtils.setAlphaComponent(day, value), ColorUtils.setAlphaComponent(night, value))
    }

    private companion object {
        const val MAX_ALPHA = 255
    }
}

/** The system's Material You colours, Android 12 and up. */
data class SystemColors(val accent: DayNight, val surface: DayNight) {
    companion object {
        fun of(context: Context): SystemColors? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            fun color(id: Int) = context.getColor(id)
            return SystemColors(
                accent =
                    DayNight(
                        day = color(android.R.color.system_accent1_600),
                        night = color(android.R.color.system_accent1_200),
                    ),
                surface =
                    DayNight(
                        day = color(android.R.color.system_neutral1_50),
                        night = color(android.R.color.system_neutral1_900),
                    ),
            )
        }
    }
}

/**
 * The widget's three colours: [text] for words and labels, [glyphs] for the dots, bars and
 * tallies (and the highlights drawn with them), and [card] behind it all, or null for none.
 */
data class WidgetPalette(val text: DayNight, val glyphs: DayNight, val card: DayNight?) {
    companion object {
        private val paleInk = DarkDiditColors.ink.toArgb()
        private val deepInk = LightDiditColors.ink.toArgb()
        private val darkSurface = DarkDiditColors.surface.toArgb()
        private val lightSurface = LightDiditColors.surface.toArgb()

        /**
         * With the launcher palette, text takes the launcher's text colour (its on-surface colour
         * on a card), glyphs its accent and the card its surface; each falls back to [system]'s
         * Material You colours, then to Didit's own. Light or dark ink, when picked, wins over the
         * launcher's text colour.
         */
        fun resolve(config: WidgetConfig, launcher: LauncherTheme, system: SystemColors?): WidgetPalette {
            val ink =
                when (config.ink) {
                    WidgetConfig.Ink.Auto -> DayNight(day = deepInk, night = paleInk)
                    WidgetConfig.Ink.Light -> DayNight(paleInk)
                    WidgetConfig.Ink.Dark -> DayNight(deepInk)
                }
            val diditSurface =
                when (config.ink) {
                    WidgetConfig.Ink.Auto -> DayNight(day = lightSurface, night = darkSurface)
                    WidgetConfig.Ink.Light -> DayNight(darkSurface)
                    WidgetConfig.Ink.Dark -> DayNight(lightSurface)
                }
            val (text, glyphs, card) =
                when (config.palette) {
                    WidgetConfig.Palette.Didit -> {
                        Triple(ink, ink, diditSurface)
                    }

                    WidgetConfig.Palette.Launcher -> {
                        val launcherText = if (config.card) launcher.onSurface else launcher.text
                        val text =
                            launcherText?.takeIf { config.ink == WidgetConfig.Ink.Auto }?.let(::DayNight) ?: ink
                        Triple(
                            text,
                            launcher.accent?.let(::DayNight) ?: system?.accent ?: text,
                            launcher.surface?.let(::DayNight) ?: system?.surface ?: diditSurface,
                        )
                    }

                    WidgetConfig.Palette.Custom -> {
                        Triple(
                            DayNight(config.custom.text),
                            DayNight(config.custom.glyphs),
                            DayNight(config.custom.card),
                        )
                    }
                }
            val opacity = config.cardOpacity / WidgetConfig.FULL_OPACITY.toFloat()
            return WidgetPalette(text, glyphs, card.takeIf { config.card }?.withAlpha(opacity))
        }
    }
}
