package com.plattegruber.didit.widget

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WidgetPaletteTest {
    private val niagara = LauncherTheme(accent = ACCENT, text = WHITE, surface = SURFACE, onSurface = ON_SURFACE)
    private val system = SystemColors(accent = DayNight(SYSTEM_ACCENT), surface = DayNight(SYSTEM_SURFACE))

    private fun resolve(
        config: WidgetConfig,
        launcher: LauncherTheme = LauncherTheme.None,
        system: SystemColors? = null,
    ) = WidgetPalette.resolve(config, launcher, system)

    @Test
    fun `didit's own colours follow the theme, with text and glyphs alike`() {
        val palette = resolve(WidgetConfig())

        assertThat(palette.text.day).isNotEqualTo(palette.text.night)
        assertThat(palette.glyphs).isEqualTo(palette.text)
        assertThat(palette.card).isNull()
    }

    @Test
    fun `matching the launcher takes niagara's colours`() {
        val clear = resolve(WidgetConfig(palette = WidgetConfig.Palette.Launcher), niagara, system)
        val card = resolve(WidgetConfig(palette = WidgetConfig.Palette.Launcher, card = true), niagara, system)

        assertThat(clear.text).isEqualTo(DayNight(WHITE))
        assertThat(clear.glyphs).isEqualTo(DayNight(ACCENT))
        assertThat(card.text).isEqualTo(DayNight(ON_SURFACE))
        assertThat(card.card).isEqualTo(DayNight(SURFACE))
    }

    @Test
    fun `without niagara, the launcher palette falls back to the system's colours`() {
        val palette = resolve(WidgetConfig(palette = WidgetConfig.Palette.Launcher, card = true), system = system)

        assertThat(palette.glyphs).isEqualTo(DayNight(SYSTEM_ACCENT))
        assertThat(palette.card).isEqualTo(DayNight(SYSTEM_SURFACE))
    }

    @Test
    fun `picked ink wins over the launcher's text colour`() {
        val palette =
            resolve(WidgetConfig(palette = WidgetConfig.Palette.Launcher, ink = WidgetConfig.Ink.Dark), niagara)

        assertThat(palette.text).isNotEqualTo(DayNight(WHITE))
        assertThat(palette.glyphs).isEqualTo(DayNight(ACCENT))
    }

    @Test
    fun `custom colours are used as picked, and opacity applies to the card`() {
        val custom = WidgetConfig.CustomColors(text = WHITE, glyphs = ACCENT, card = SURFACE)
        val palette =
            resolve(WidgetConfig(palette = WidgetConfig.Palette.Custom, custom = custom, card = true, cardOpacity = 50))

        assertThat(palette.text).isEqualTo(DayNight(WHITE))
        assertThat(palette.glyphs).isEqualTo(DayNight(ACCENT))
        assertThat(palette.card!!.day ushr ALPHA_SHIFT).isEqualTo(HALF_ALPHA)
        assertThat(palette.card.day and RGB).isEqualTo(SURFACE and RGB)
    }

    private companion object {
        const val ACCENT = 0xFF7FD1C7.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val SURFACE = 0xFF22272B.toInt()
        const val ON_SURFACE = 0xFFE4E9EC.toInt()
        const val SYSTEM_ACCENT = 0xFFAAC7FF.toInt()
        const val SYSTEM_SURFACE = 0xFF1B1B1F.toInt()
        const val ALPHA_SHIFT = 24
        const val HALF_ALPHA = 127
        const val RGB = 0x00FFFFFF
    }
}
