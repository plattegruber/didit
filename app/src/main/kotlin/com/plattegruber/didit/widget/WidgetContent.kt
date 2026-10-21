// Glance composables take a GlanceModifier, if any, which the Compose modifier rule doesn't know.
@file:Suppress("ktlint:compose:modifier-missing-check")

package com.plattegruber.didit.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.plattegruber.didit.R
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.core.domain.viz.DayRange
import com.plattegruber.didit.core.domain.viz.WindowSummary
import com.plattegruber.didit.core.domain.viz.windowSummary
import com.plattegruber.didit.ui.text.DayText
import com.plattegruber.didit.ui.theme.DarkDiditColors
import com.plattegruber.didit.ui.theme.LightDiditColors
import com.plattegruber.didit.ui.theme.SwatchColors
import com.plattegruber.didit.ui.viz.CalendarScene
import com.plattegruber.didit.ui.viz.HabitHistory
import com.plattegruber.didit.ui.viz.HabitScenes
import com.plattegruber.didit.ui.viz.RowsScene
import com.plattegruber.didit.ui.viz.Tone
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.floor

/** Where taps on the widget go. Kept apart from the layout so the layout can be tested alone. */
interface WidgetActions {
    /** Logs today for [habit]: a direct toggle for yes/no habits, the log sheet for the rest. */
    fun log(habit: Habit): Action

    fun openApp(): Action

    fun openHistory(habit: Habit): Action

    fun configure(): Action
}

/** The design's widget layouts, in dp. */
private object Layout {
    val NameWidth = 72.dp
    val SummaryWidth = 52.dp
    val ColumnGap = 10.dp
    val HeaderHeight = 18.dp
    val HeaderGap = 10.dp
    val CardRadius = 24.dp
    val CardPadding = 12.dp
    val ClearPadding = 4.dp
    val MixWidth = 44.dp
    val MixHeight = 4.dp

    /** Width of one day in the multi-habit rows (14 days across a phone). */
    const val ROW_DAY_WIDTH = 14.5f

    /** Width of one day in the single-habit strip (four weeks across a phone). */
    const val STRIP_DAY_WIDTH = 12f

    /** Roughly how wide one 10sp monospaced character is, for breaking legends into lines. */
    const val LEGEND_CHAR_WIDTH = 6.2f
    const val LEGEND_GAP = 5f
    const val MIN_ROW_DAYS = 5
    const val MAX_ROW_DAYS = 31
    const val MIN_WEEKS = 2
    const val MAX_WEEKS = 6
    const val MAX_CALENDAR_ROW = 40f

    /** Taller than this fraction of its width, a single habit shows as a calendar. */
    const val CALENDAR_ASPECT = 0.7f
}

/** The widget's colours: ink that follows the theme (or the user's pick), and its quieter shades. */
class WidgetColors(config: WidgetConfig) {
    /** Pale ink, for a dark theme or wallpaper. */
    private val paleInk = DarkDiditColors.ink

    /** Deep ink, for a light theme or wallpaper. */
    private val deepInk = LightDiditColors.ink

    val ink: ColorProvider = provider(config.ink, Tone.Ink.alpha)
    val subtle: ColorProvider = provider(config.ink, Tone.Label.alpha)
    val background: ColorProvider? =
        if (config.card) {
            when (config.ink) {
                WidgetConfig.Ink.Auto -> {
                    DayNightColorProvider(
                        day = LightDiditColors.surface,
                        night = DarkDiditColors.surface,
                    )
                }

                WidgetConfig.Ink.Light -> {
                    ColorProvider(DarkDiditColors.surface)
                }

                WidgetConfig.Ink.Dark -> {
                    ColorProvider(LightDiditColors.surface)
                }
            }
        } else {
            null
        }

    fun swatch(swatch: Swatch): ColorProvider =
        if (swatch == Swatch.Ink) ink else ColorProvider(Color(SwatchColors.argb(swatch)))

    private fun provider(ink: WidgetConfig.Ink, alpha: Float) = when (ink) {
        WidgetConfig.Ink.Auto -> {
            DayNightColorProvider(
                day = deepInk.copy(alpha = alpha),
                night = paleInk.copy(alpha = alpha),
            )
        }

        WidgetConfig.Ink.Light -> {
            ColorProvider(paleInk.copy(alpha = alpha))
        }

        WidgetConfig.Ink.Dark -> {
            ColorProvider(deepInk.copy(alpha = alpha))
        }
    }
}

@Composable
fun WidgetContent(model: WidgetModel, config: WidgetConfig, actions: WidgetActions) {
    val colors = remember(config) { WidgetColors(config) }
    val padding = if (config.card) Layout.CardPadding else Layout.ClearPadding
    val background = colors.background
    val frame =
        if (background != null) {
            GlanceModifier.fillMaxSize().background(background).cornerRadius(Layout.CardRadius)
        } else {
            GlanceModifier.fillMaxSize()
        }
    val size = LocalSize.current
    val width = size.width - padding * 2
    val height = size.height - padding * 2

    Box(frame.padding(padding)) {
        when (model) {
            WidgetModel.Loading -> {}

            WidgetModel.NoHabits -> {
                Message(R.string.widget_empty, colors, actions.openApp())
            }

            WidgetModel.HabitMissing -> {
                Message(R.string.widget_habit_missing, colors, actions.configure())
            }

            is WidgetModel.AllHabits -> {
                AllHabits(model, colors, actions, width, height)
            }

            is WidgetModel.OneHabit -> {
                if (height.value >= width.value * Layout.CALENDAR_ASPECT) {
                    OneHabitCalendar(model, colors, actions, width, height)
                } else {
                    OneHabitStrip(model, colors, actions, width, height)
                }
            }
        }
    }
}

@Composable
private fun Message(text: Int, colors: WidgetColors, action: Action) {
    Box(GlanceModifier.fillMaxSize().clickable(action), contentAlignment = Alignment.Center) {
        Text(LocalContext.current.getString(text), style = textStyle(colors.subtle, 13.sp))
    }
}

@Composable
private fun AllHabits(
    model: WidgetModel.AllHabits,
    colors: WidgetColors,
    actions: WidgetActions,
    width: Dp,
    height: Dp,
) {
    val context = LocalContext.current
    val locale = context.locale()
    val stripWidth = width - Layout.NameWidth - Layout.SummaryWidth - Layout.ColumnGap * 2
    val dayCount =
        floor(stripWidth.value / Layout.ROW_DAY_WIDTH)
            .toInt()
            .coerceIn(Layout.MIN_ROW_DAYS, Layout.MAX_ROW_DAYS)
    val roomForRows = height - Layout.HeaderHeight - Layout.HeaderGap - RowsScene.LETTERS_HEIGHT.dp
    val rowCount = floor(roomForRows.value / RowsScene.ROW_HEIGHT).toInt().coerceIn(1, model.histories.size)
    val histories = model.histories.take(rowCount)
    val days = DayRange.lastDays(model.today, dayCount)
    val dates = days.map { it.date }

    val scene =
        remember(histories, model.today, dayCount, locale) {
            HabitScenes.rows(histories, model.today, dayCount, locale)
        }
    val layers =
        remember(scene, stripWidth) {
            val hasAccents = histories.any { it.usesAccents() }
            LayerRenderer(context).render(stripWidth.value, scene.height, hasAccents) { canvas, w, _ ->
                drawRows(canvas, scene, w)
            }
        }

    Column(GlanceModifier.fillMaxSize()) {
        Row(
            GlanceModifier.fillMaxWidth().height(Layout.HeaderHeight).clickable(actions.openApp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(context.getString(R.string.wordmark), style = textStyle(colors.ink, 15.sp, FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            Text(DayText.range(dates.first(), dates.last(), locale), style = monoStyle(colors.subtle, 11.sp))
        }
        Spacer(GlanceModifier.height(Layout.HeaderGap))
        Box {
            Row {
                Column(GlanceModifier.width(Layout.NameWidth)) {
                    histories.forEach { NameCell(it.habit, colors) }
                }
                Spacer(GlanceModifier.width(Layout.ColumnGap))
                LayerImage(
                    layers,
                    colors.ink,
                    stripWidth,
                    scene.height.dp,
                    context.resources.getQuantityString(R.plurals.home_rows_description, dayCount, dayCount),
                )
                Spacer(GlanceModifier.width(Layout.ColumnGap))
                Column(GlanceModifier.width(Layout.SummaryWidth)) {
                    val rowHeight = GlanceModifier.height(RowsScene.ROW_HEIGHT.dp)
                    histories.forEach { SummaryCell(it, dates, colors, rowHeight) }
                }
            }
            Column {
                histories.forEach { history ->
                    Box(
                        GlanceModifier
                            .fillMaxWidth()
                            .height(RowsScene.ROW_HEIGHT.dp)
                            .clickable(actions.log(history.habit)),
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun NameCell(habit: Habit, colors: WidgetColors) {
    Column(
        GlanceModifier.height(RowsScene.ROW_HEIGHT.dp).width(Layout.NameWidth),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(habit.name, style = textStyle(colors.ink, 13.sp, FontWeight.Medium), maxLines = 1)
        Legend(habit, colors)
    }
}

@Composable
private fun Legend(habit: Habit, colors: WidgetColors) {
    val kind = habit.kind as? HabitKind.Choice ?: return
    // RemoteViews can't wrap a row, so the legend is broken into lines up front.
    legendLines(kind.activeOptions.map { it.label.lowercase() }, Layout.NameWidth.value).forEach { line ->
        Row {
            line.forEachIndexed { index, label ->
                if (index > 0) Spacer(GlanceModifier.width(Layout.LEGEND_GAP.dp))
                val option = kind.activeOptions.first { it.label.lowercase() == label }
                Text(label, style = monoStyle(colors.swatch(option.swatch), 10.sp), maxLines = 1)
            }
        }
    }
}

/** Greedily packs [labels] into lines no wider than [width] dp. */
internal fun legendLines(labels: List<String>, width: Float): List<List<String>> {
    val lines = mutableListOf<MutableList<String>>()
    var used = 0f
    for (label in labels) {
        val labelWidth = label.length * Layout.LEGEND_CHAR_WIDTH
        val line = lines.lastOrNull()
        if (line != null && used + Layout.LEGEND_GAP + labelWidth <= width) {
            line += label
            used += Layout.LEGEND_GAP + labelWidth
        } else {
            lines += mutableListOf(label)
            used = labelWidth
        }
    }
    return lines
}

@Composable
private fun SummaryCell(
    history: HabitHistory,
    dates: List<LocalDate>,
    colors: WidgetColors,
    modifier: GlanceModifier = GlanceModifier,
) {
    val context = LocalContext.current
    val summary = history.habit.windowSummary(history.days, dates)
    val text = DayText.summary(context.resources, summary, history.habit.kind.unit, context.locale())
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.value, style = textStyle(colors.ink, 16.sp, FontWeight.Medium), maxLines = 1)
        if (summary is WindowSummary.Picks && summary.mix.isNotEmpty()) {
            Spacer(GlanceModifier.height(3.dp))
            MixImage(summary, colors)
            Spacer(GlanceModifier.height(3.dp))
        }
        if (text.label.isNotBlank()) Text(text.label, style = monoStyle(colors.subtle, 10.sp), maxLines = 1)
    }
}

@Composable
private fun MixImage(summary: WindowSummary.Picks, colors: WidgetColors, width: Dp = Layout.MixWidth) {
    val context = LocalContext.current
    val parts = summary.mix.map { it.swatch to it.count }
    val layers =
        remember(parts, width) {
            val hasAccents = parts.any { it.first != Swatch.Ink }
            LayerRenderer(context).render(width.value, Layout.MixHeight.value, hasAccents) { canvas, w, h ->
                drawMix(canvas, parts, w, h)
            }
        }
    LayerImage(layers, colors.ink, width, Layout.MixHeight, contentDescription = null)
}

@Composable
private fun OneHabitStrip(
    model: WidgetModel.OneHabit,
    colors: WidgetColors,
    actions: WidgetActions,
    width: Dp,
    height: Dp,
) {
    val context = LocalContext.current
    val locale = context.locale()
    val history = model.history
    val weeks =
        floor(width.value / Layout.STRIP_DAY_WIDTH / DayRange.DAYS_PER_WEEK)
            .toInt()
            .coerceIn(1, Layout.MAX_WEEKS)
    val dayCount = weeks * DayRange.DAYS_PER_WEEK
    val dates = DayRange.lastDays(model.today, dayCount).map { it.date }
    val stripHeight = height - Layout.HeaderHeight - Layout.HeaderGap
    val scene =
        remember(history, model.today, dayCount, locale) {
            HabitScenes.strip(history, model.today, dayCount, locale)
        }
    val layers =
        remember(scene, width, stripHeight) {
            LayerRenderer(context).render(width.value, stripHeight.value, history.usesAccents()) { canvas, w, h ->
                drawStrip(canvas, scene, w, h)
            }
        }
    val summary =
        DayText.summary(
            resources = context.resources,
            summary = history.habit.windowSummary(history.days, dates),
            unit = history.habit.kind.unit,
            locale = locale,
        )

    Column(GlanceModifier.fillMaxSize().clickable(actions.log(history.habit))) {
        Row(
            GlanceModifier.fillMaxWidth().height(Layout.HeaderHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(history.habit.name, style = textStyle(colors.ink, 13.sp, FontWeight.Medium), maxLines = 1)
            Spacer(GlanceModifier.width(8.dp))
            Text(
                context.resources.getQuantityString(R.plurals.widget_weeks, weeks, weeks),
                style = monoStyle(colors.subtle, 10.sp),
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(summary.value, style = textStyle(colors.ink, 16.sp, FontWeight.Medium))
            if (summary.label.isNotBlank()) {
                Spacer(GlanceModifier.width(6.dp))
                Text(summary.label, style = monoStyle(colors.subtle, 10.sp))
            }
        }
        Spacer(GlanceModifier.height(Layout.HeaderGap))
        LayerImage(
            layers,
            colors.ink,
            width,
            stripHeight,
            context.resources.getQuantityString(R.plurals.widget_history, dayCount, history.habit.name, dayCount),
        )
    }
}

@Composable
private fun OneHabitCalendar(
    model: WidgetModel.OneHabit,
    colors: WidgetColors,
    actions: WidgetActions,
    width: Dp,
    height: Dp,
) {
    val context = LocalContext.current
    val locale = context.locale()
    val history = model.history
    val kind = history.habit.kind
    val legendSpace = if (kind is HabitKind.Choice) LEGEND_SPACE else 0f
    val gridRoom =
        height.value - Layout.HeaderHeight.value - CALENDAR_HEADER_GAP - legendSpace -
            CalendarScene.HEADER_HEIGHT - CalendarScene.HEADER_GAP
    val rowPitch = CalendarScene.ROW_HEIGHT + CalendarScene.ROW_GAP
    val weeks =
        floor((gridRoom + CalendarScene.ROW_GAP) / rowPitch)
            .toInt()
            .coerceIn(Layout.MIN_WEEKS, Layout.MAX_WEEKS)
    val rowHeight =
        ((gridRoom - (weeks - 1) * CalendarScene.ROW_GAP) / weeks)
            .coerceIn(CalendarScene.ROW_HEIGHT, Layout.MAX_CALENDAR_ROW)
    val weekStart = WeekFields.of(locale).firstDayOfWeek

    val scene =
        remember(history, model.today, weeks, rowHeight, locale) {
            HabitScenes.lastWeeks(history, model.today, weeks, weekStart, locale).copy(rowHeight = rowHeight)
        }
    val layers =
        remember(scene, width) {
            LayerRenderer(context).render(width.value, scene.height, history.usesAccents()) { canvas, w, _ ->
                drawCalendar(canvas, scene, w)
            }
        }
    val dates =
        scene.cells
            .map { it.first }
            .filterNot { it.isFuture }
            .map { it.date }
    val summary = history.habit.windowSummary(history.days, dates)
    val text = DayText.summary(context.resources, summary, kind.unit, locale)

    Column(GlanceModifier.fillMaxSize().clickable(actions.log(history.habit))) {
        Row(
            GlanceModifier.fillMaxWidth().height(Layout.HeaderHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(history.habit.name, style = textStyle(colors.ink, 13.sp, FontWeight.Medium), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            if (summary is WindowSummary.Picks && summary.mix.isNotEmpty()) {
                MixImage(summary, colors, width = 32.dp)
                Spacer(GlanceModifier.width(7.dp))
            }
            Text(text.value, style = textStyle(colors.ink, 14.sp, FontWeight.Medium))
        }
        Spacer(GlanceModifier.height(CALENDAR_HEADER_GAP.dp))
        LayerImage(
            layers,
            colors.ink,
            width,
            scene.height.dp,
            context.resources.getQuantityString(R.plurals.widget_history, dates.size, history.habit.name, dates.size),
        )
        if (kind is HabitKind.Choice) {
            Spacer(GlanceModifier.defaultWeight())
            Row {
                kind.activeOptions.forEachIndexed { index, option ->
                    if (index > 0) Spacer(GlanceModifier.width(8.dp))
                    Text(
                        option.label.lowercase(),
                        style = monoStyle(colors.swatch(option.swatch), 10.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** An [InkLayers] drawing: the ink mask tinted for the theme, the accents laid over it as they are. */
@Composable
private fun LayerImage(layers: InkLayers, ink: ColorProvider, width: Dp, height: Dp, contentDescription: String?) {
    Box(GlanceModifier.size(width, height)) {
        Image(
            provider = ImageProvider(layers.ink),
            contentDescription = contentDescription,
            modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(ink),
        )
        layers.accents?.let { accents ->
            Image(
                provider = ImageProvider(accents),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

private const val LEGEND_SPACE = 18f
private const val CALENDAR_HEADER_GAP = 6f

private fun HabitHistory.usesAccents(): Boolean =
    (habit.kind as? HabitKind.Choice)?.options?.any { it.swatch != Swatch.Ink } == true

private fun android.content.Context.locale(): Locale = resources.configuration.locales[0]

private fun textStyle(color: ColorProvider, size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = FontFamily.SansSerif)

private fun monoStyle(color: ColorProvider, size: TextUnit) =
    TextStyle(color = color, fontSize = size, fontFamily = FontFamily.Monospace)
