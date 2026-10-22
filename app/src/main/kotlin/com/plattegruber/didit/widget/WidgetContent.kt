// Glance composables take a GlanceModifier, if any, which the Compose modifier rule doesn't know.
@file:Suppress("ktlint:compose:modifier-missing-check")

package com.plattegruber.didit.widget

import android.content.Context
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.plattegruber.didit.R
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.viz.WindowSummary
import com.plattegruber.didit.ui.text.DayText
import com.plattegruber.didit.ui.text.SummaryText
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/** What the widget's taps do, kept apart so the layouts can be rendered in tests. */
interface WidgetActions {
    /** Logs today for [habit]: a direct toggle for yes/no habits, the log sheet for the rest. */
    fun log(habit: Habit): Action

    fun openApp(): Action

    fun openHistory(habit: Habit): Action

    fun configure(): Action
}

private val CardRadius = 24.dp
private val CardPadding = 12.dp
private val ClearPadding = 4.dp

/**
 * The widget: drawn whole as tinted layers (so fonts, sizes and layout are exactly Didit's), with
 * invisible tap targets over it that also carry what a screen reader says.
 */
@Composable
fun WidgetContent(
    model: WidgetModel,
    config: WidgetConfig,
    actions: WidgetActions,
    launcher: LauncherTheme = LauncherTheme.None,
) {
    val context = LocalContext.current
    val palette = remember(config, launcher) { WidgetPalette.resolve(config, launcher, SystemColors.of(context)) }
    val padding = if (config.card) CardPadding else ClearPadding
    val size = LocalSize.current
    val width = (size.width - padding * 2).value
    val height = (size.height - padding * 2).value
    val type = remember(config) { WidgetType(context, config) }
    val drawing =
        remember(model, config, width, height) { widgetLayout(context, config, type).layout(model, width, height) }
    val layers = remember(drawing) { LayerRenderer(context, config, type).render(drawing) }

    val card = palette.card
    val frame =
        if (card != null) {
            GlanceModifier.fillMaxSize().background(card.provider()).cornerRadius(CardRadius)
        } else {
            GlanceModifier.fillMaxSize()
        }
    Box(frame.padding(padding)) {
        if (model == WidgetModel.Loading) return@Box
        Layer(layers.text, palette.text, drawing.description)
        Layer(layers.glyphs, palette.glyphs, contentDescription = null)
        layers.accents?.let { accents ->
            Image(
                provider = ImageProvider(accents),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }
        HitAreas(drawing, actions)
    }
}

@Composable
private fun Layer(mask: android.graphics.Bitmap, color: DayNight, contentDescription: String?) {
    Image(
        provider = ImageProvider(mask),
        contentDescription = contentDescription,
        modifier = GlanceModifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(color.provider()),
    )
}

@Composable
private fun HitAreas(drawing: WidgetDrawing, actions: WidgetActions) {
    Column(GlanceModifier.fillMaxSize()) {
        var y = 0f
        for (hit in drawing.hits.sortedBy { it.top }) {
            val top = hit.top * drawing.scale
            if (top > y) Spacer(GlanceModifier.height((top - y).dp))
            val area =
                GlanceModifier
                    .fillMaxWidth()
                    .height((hit.height * drawing.scale).dp)
                    .clickable(hit.tap.action(actions))
            Box(if (hit.description != null) area.semantics { contentDescription = hit.description } else area) {}
            y = top + hit.height * drawing.scale
        }
    }
}

private fun Tap.action(actions: WidgetActions): Action = when (this) {
    Tap.OpenApp -> actions.openApp()
    Tap.Configure -> actions.configure()
    is Tap.Log -> actions.log(habit)
}

private fun DayNight.provider() = ColorProvider(day = Color(day), night = Color(night))

internal fun widgetLayout(context: Context, config: WidgetConfig, type: WidgetType): WidgetLayout {
    val locale = context.resources.configuration.locales[0]
    return WidgetLayout(
        config,
        type,
        ResourceStrings(context.resources, locale),
        locale,
        WeekFields.of(locale).firstDayOfWeek,
    )
}

/** The widget's words, from the app's resources. */
internal class ResourceStrings(private val resources: Resources, private val locale: Locale) : WidgetStrings {
    override val wordmark: String = resources.getString(R.string.wordmark)
    override val noHabits: String = resources.getString(R.string.widget_empty)
    override val habitMissing: String = resources.getString(R.string.widget_habit_missing)

    override fun range(from: LocalDate, to: LocalDate): String = DayText.range(from, to, locale)

    override fun weeks(count: Int): String = resources.getQuantityString(R.plurals.widget_weeks, count, count)

    override fun summary(habit: Habit, summary: WindowSummary): SummaryText =
        DayText.summary(resources, summary, habit.kind.unit, locale)

    override fun row(habit: Habit, summary: SummaryText): String =
        resources.getString(R.string.home_row_description, habit.name, summary.value, summary.label)

    override fun overview(dayCount: Int): String =
        resources.getQuantityString(R.plurals.home_rows_description, dayCount, dayCount)

    override fun history(habit: Habit, dayCount: Int): String =
        resources.getQuantityString(R.plurals.widget_history, dayCount, habit.name, dayCount)
}
