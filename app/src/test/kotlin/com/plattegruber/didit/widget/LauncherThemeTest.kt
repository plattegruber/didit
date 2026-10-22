package com.plattegruber.didit.widget

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class LauncherThemeTest {
    @Test
    fun `colours read in the forms a launcher might write them`() {
        assertThat(parseLauncherColor("#FF8AB4F8")).isEqualTo(0xFF8AB4F8.toInt())
        assertThat(parseLauncherColor("#8AB4F8")).isEqualTo(0xFF8AB4F8.toInt())
        assertThat(parseLauncherColor("80ffffff")).isEqualTo(0x80FFFFFF.toInt())
        assertThat(parseLauncherColor(" #000000 ")).isEqualTo(0xFF000000.toInt())
        assertThat(parseLauncherColor("-16777216")).isEqualTo(0xFF000000.toInt())
    }

    @Test
    fun `anything else is not a colour`() {
        assertThat(parseLauncherColor("")).isNull()
        assertThat(parseLauncherColor("blue")).isNull()
        assertThat(parseLauncherColor("#FFF")).isNull()
        assertThat(parseLauncherColor("-99999999999")).isNull()
    }

    @Test
    fun `a broadcast carries one variable or arrays of them`() {
        assertThat(KustomBroadcast.variables("screen", "0", null, null)).containsExactly("screen", "0")
        assertThat(
            KustomBroadcast.variables(
                name = null,
                value = null,
                names = arrayOf("accent-color", "text-color"),
                values = arrayOf("#FF8AB4F8", "#FFFFFFFF"),
            ),
        ).containsExactly("accent-color", "#FF8AB4F8", "text-color", "#FFFFFFFF")
    }

    @Test
    fun `mismatched arrays are ignored`() {
        assertThat(KustomBroadcast.variables(null, null, arrayOf("a", "b"), arrayOf("1"))).isEmpty()
    }

    @Test
    fun `stored values fold into a theme, unreadable ones dropped`() {
        val heardAt = Instant.ofEpochSecond(1_000)
        val theme =
            launcherTheme(
                mapOf(LauncherColor.Accent to "#FF8AB4F8", LauncherColor.Surface to "nonsense"),
                heardAt,
            )

        assertThat(theme).isEqualTo(LauncherTheme(accent = 0xFF8AB4F8.toInt(), heardAt = heardAt))
        assertThat(theme.isEmpty).isFalse()
        assertThat(launcherTheme(emptyMap(), null).isEmpty).isTrue()
    }
}
