package com.plattegruber.didit.ui.viz

import android.content.Context
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.plattegruber.didit.R
import com.plattegruber.didit.ui.theme.DiditTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/** Geist Mono for labels drawn onto a canvas, or the platform monospace if it can't be loaded. */
fun labelTypeface(context: Context): Typeface =
    runCatching { ResourcesCompat.getFont(context, R.font.geist_mono) }.getOrNull() ?: Typeface.MONOSPACE

@Composable
fun rememberGlyphPainter(): GlyphPainter {
    val context = LocalContext.current
    val ink = DiditTheme.colors.ink
    val unit = LocalDensity.current.density
    return remember(ink, unit) { GlyphPainter(GlyphPalette.full(ink.toArgb()), unit, labelTypeface(context)) }
}

@Composable
fun rememberLocale(): Locale = LocalConfiguration.current.locales[0]

/** The first day of the week for the user's locale. */
@Composable
fun rememberWeekStart(): DayOfWeek {
    val locale = rememberLocale()
    return remember(locale) { WeekFields.of(locale).firstDayOfWeek }
}

@Composable
fun RowsCanvas(scene: RowsScene, contentDescription: String, modifier: Modifier = Modifier) {
    val painter = rememberGlyphPainter()
    Canvas(
        modifier
            .height(scene.height.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        drawIntoCanvas { painter.drawRows(it.nativeCanvas, scene, size.width) }
    }
}

@Composable
fun CalendarCanvas(
    scene: CalendarScene,
    contentDescription: String,
    modifier: Modifier = Modifier,
    metrics: GlyphMetrics = GlyphMetrics.Calendar,
    onDayClick: ((LocalDate) -> Unit)? = null,
) {
    val painter = rememberGlyphPainter()
    val unit = LocalDensity.current.density
    val currentOnDayClick by rememberUpdatedState(onDayClick)
    val taps =
        if (onDayClick == null) {
            Modifier
        } else {
            Modifier.pointerInput(scene, unit) {
                detectTapGestures { offset ->
                    val cell = scene.cellAt(offset.x, offset.y, size.width.toFloat(), unit)
                    if (cell != null && !cell.isFuture) currentOnDayClick?.invoke(cell.date)
                }
            }
        }
    Canvas(
        modifier
            .height(scene.height.dp)
            .semantics { this.contentDescription = contentDescription }
            .then(taps),
    ) {
        drawIntoCanvas { painter.drawCalendar(it.nativeCanvas, scene, size.width, metrics) }
    }
}
