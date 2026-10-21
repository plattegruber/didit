package com.plattegruber.didit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.plattegruber.didit.R
import com.plattegruber.didit.core.domain.model.Swatch

/**
 * Didit's colours. The dark set is the design's own; the light set mirrors it value for value so
 * that every role keeps its contrast.
 */
@Immutable
data class DiditColors(
    val background: Color,
    /** Sheets and cards that sit above the background. */
    val surface: Color,
    /** Buttons and chips on a sheet. */
    val raised: Color,
    val field: Color,
    val ink: Color,
    /** Ink on the primary button, i.e. the background colour. */
    val onInk: Color,
    val inkSoft: Color,
    val muted: Color,
    val subtle: Color,
    val line: Color,
    val lineStrong: Color,
    val scrim: Color,
    val danger: Color,
) {
    fun swatch(swatch: Swatch): Color =
        when (swatch) {
            Swatch.Ink -> ink
            else -> Color(SwatchColors.argb(swatch))
        }
}

/** Accent colours for choice options. Mid-tones, so they hold up on both light and dark grounds. */
object SwatchColors {
    fun argb(swatch: Swatch): Int =
        when (swatch) {
            Swatch.Ink -> error("Ink follows the theme; resolve it through DiditColors")
            Swatch.Blue -> 0xFF7FA3DB.toInt()
            Swatch.Amber -> 0xFFE8A54B.toInt()
            Swatch.Coral -> 0xFFE3876B.toInt()
            Swatch.Lilac -> 0xFFC99AE0.toInt()
            Swatch.Sage -> 0xFF8FBF9A.toInt()
        }
}

val DarkDiditColors =
    DiditColors(
        background = Color(0xFF111519),
        surface = Color(0xFF1A1F26),
        raised = Color(0xFF252B33),
        field = Color(0xFF171C22),
        ink = Color(0xFFECE8DF),
        onInk = Color(0xFF111519),
        inkSoft = Color(0xFFCFCBC3),
        muted = Color(0xFF9AA0A8),
        subtle = Color(0xFF8B9097),
        line = Color(0xFF2A3038),
        lineStrong = Color(0xFF3A424C),
        scrim = Color(0xA806080B),
        danger = Color(0xFFE3876B),
    )

val LightDiditColors =
    DiditColors(
        background = Color(0xFFF6F3EE),
        surface = Color(0xFFFFFFFF),
        raised = Color(0xFFECE8E1),
        field = Color(0xFFFFFFFF),
        ink = Color(0xFF1B1F24),
        onInk = Color(0xFFF6F3EE),
        inkSoft = Color(0xFF3A3F46),
        muted = Color(0xFF5E636B),
        subtle = Color(0xFF6B7078),
        line = Color(0xFFDDD8D0),
        lineStrong = Color(0xFFC6C0B7),
        scrim = Color(0x66151719),
        danger = Color(0xFFB4492C),
    )

/** Geist ships as variable fonts; each weight picks its point on the weight axis. */
private fun FontWeight.variation() = FontVariation.Settings(FontVariation.weight(weight))

val Geist =
    FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold).map { weight ->
            Font(R.font.geist, weight, variationSettings = weight.variation())
        },
    )

val GeistMono =
    FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold).map { weight ->
            Font(R.font.geist_mono, weight, variationSettings = weight.variation())
        },
    )

/** The type scale from the design, named for what it is used for. */
@Immutable
data class DiditType(
    /** "64" pages today. */
    val hero: TextStyle =
        TextStyle(
            fontFamily = Geist,
            fontWeight = FontWeight.Medium,
            fontSize = 64.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.045).em,
            fontFeatureSettings = "tnum",
        ),
    /** "pages today", beside the hero number. */
    val heroLabel: TextStyle = TextStyle(fontFamily = GeistMono, fontSize = 13.sp),
    val sheetTitle: TextStyle =
        TextStyle(
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            letterSpacing = (-0.015).em,
        ),
    val screenTitle: TextStyle =
        TextStyle(
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            letterSpacing = (-0.015).em,
        ),
    val launcher: TextStyle = TextStyle(fontFamily = Geist, fontSize = 22.sp, letterSpacing = (-0.01).em),
    val button: TextStyle = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    val body: TextStyle = TextStyle(fontFamily = Geist, fontSize = 16.sp),
    val option: TextStyle = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    val chip: TextStyle = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    val wordmark: TextStyle =
        TextStyle(
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            letterSpacing = (-0.01).em,
        ),
    val total: TextStyle =
        TextStyle(
            fontFamily = Geist,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 18.sp,
            letterSpacing = (-0.01).em,
            fontFeatureSettings = "tnum",
        ),
    val rowName: TextStyle = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    val hint: TextStyle = TextStyle(fontFamily = Geist, fontSize = 13.sp, lineHeight = 19.sp),
    val caption: TextStyle = TextStyle(fontFamily = Geist, fontSize = 12.sp, lineHeight = 17.sp),
    /** Small monospaced labels: units, dates, legends. */
    val mono: TextStyle = TextStyle(fontFamily = GeistMono, fontSize = 10.sp, lineHeight = 12.sp),
    val monoMedium: TextStyle = TextStyle(fontFamily = GeistMono, fontSize = 11.sp, lineHeight = 14.sp),
    /** Upper-case section labels such as "NAME" and "SHOW IT AS". */
    val overline: TextStyle =
        TextStyle(
            fontFamily = GeistMono,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.06.em,
        ),
    val step: TextStyle = TextStyle(fontFamily = GeistMono, fontSize = 15.sp),
)

object DiditShapes {
    val card = RoundedCornerShape(24.dp)
}

private val LocalDiditColors = staticCompositionLocalOf { DarkDiditColors }
private val LocalDiditType = staticCompositionLocalOf { DiditType() }

object DiditTheme {
    val colors: DiditColors
        @Composable @ReadOnlyComposable
        get() = LocalDiditColors.current

    val type: DiditType
        @Composable @ReadOnlyComposable
        get() = LocalDiditType.current
}

@Composable
fun DiditTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkDiditColors else LightDiditColors
    // Material components (sheets, dialogs, text selection) pick up the same palette.
    val scheme =
        (if (darkTheme) darkColorScheme() else lightColorScheme()).copy(
            primary = colors.ink,
            onPrimary = colors.onInk,
            background = colors.background,
            onBackground = colors.ink,
            surface = colors.background,
            onSurface = colors.ink,
            onSurfaceVariant = colors.muted,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface,
            outline = colors.lineStrong,
            outlineVariant = colors.line,
            error = colors.danger,
            scrim = colors.scrim,
        )
    CompositionLocalProvider(LocalDiditColors provides colors, LocalDiditType provides DiditType()) {
        MaterialTheme(colorScheme = scheme) {
            // Text outside a Surface still reads as ink rather than Material's default black.
            CompositionLocalProvider(LocalContentColor provides colors.ink, content = content)
        }
    }
}
