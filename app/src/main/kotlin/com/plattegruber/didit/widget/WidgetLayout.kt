package com.plattegruber.didit.widget

import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.core.domain.viz.DayRange
import com.plattegruber.didit.core.domain.viz.WindowSummary
import com.plattegruber.didit.core.domain.viz.windowSummary
import com.plattegruber.didit.ui.text.SummaryText
import com.plattegruber.didit.ui.viz.CalendarScene
import com.plattegruber.didit.ui.viz.HabitHistory
import com.plattegruber.didit.ui.viz.HabitScenes
import com.plattegruber.didit.ui.viz.RowsScene
import com.plattegruber.didit.ui.viz.StripScene
import com.plattegruber.didit.ui.viz.Tone
import com.plattegruber.didit.widget.WidgetConfig.Element
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** The words the widget shows, kept apart so layout can be tested without resources. */
interface WidgetStrings {
    val wordmark: String
    val noHabits: String
    val habitMissing: String

    fun range(from: LocalDate, to: LocalDate): String

    fun weeks(count: Int): String

    fun summary(habit: Habit, summary: WindowSummary): SummaryText

    /** What a screen reader says for a habit's row: its name, total and what a tap does. */
    fun row(habit: Habit, summary: SummaryText): String

    /** What a screen reader says for the whole widget. */
    fun overview(dayCount: Int): String

    fun history(habit: Habit, dayCount: Int): String
}

/** Where a tap goes. */
sealed interface Tap {
    data object OpenApp : Tap

    data object Configure : Tap

    data class Log(val habit: Habit) : Tap
}

/** A full-width band of the widget that responds to a tap, in dp before [WidgetDrawing.scale]. */
data class HitArea(val top: Float, val height: Float, val tap: Tap, val description: String? = null)

enum class Align { Start, Center, End }

/** One thing the widget draws, positioned in dp. */
sealed interface Mark {
    /**
     * Text whose line starts at [top]. Without a [swatch] it's in the text colour at [tone];
     * a swatch draws it in a choice's colour (Ink meaning the glyph colour).
     */
    data class Words(
        val text: String,
        val role: TextRole,
        val x: Float,
        val top: Float,
        val align: Align = Align.Start,
        val tone: Tone = Tone.Ink,
        val swatch: Swatch? = null,
    ) : Mark

    /** The word mark's full stop. */
    data class Dot(val x: Float, val y: Float, val radius: Float) : Mark

    data class Rows(
        val scene: RowsScene,
        val left: Float,
        val top: Float,
        val width: Float,
        val rowHeight: Float,
        val lettersHeight: Float,
    ) : Mark

    data class Strip(
        val scene: StripScene,
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val labelHeight: Float,
    ) : Mark

    /** A calendar whose own coordinates start at [top]; letters may sit above the visible grid. */
    data class Calendar(val scene: CalendarScene, val left: Float, val top: Float, val width: Float) : Mark

    data class Mix(
        val parts: List<Pair<Swatch, Int>>,
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
    ) : Mark
}

/**
 * A complete widget, laid out at its natural size in dp and then shrunk by [scale] (at most 1)
 * when that is taller than the room it has. Nothing is ever clipped.
 */
data class WidgetDrawing(
    val width: Float,
    val height: Float,
    val scale: Float,
    val marks: List<Mark>,
    val hits: List<HitArea>,
    val description: String,
    val usesAccents: Boolean,
) {
    companion object {
        val Empty = WidgetDrawing(0f, 0f, 1f, emptyList(), emptyList(), "", usesAccents = false)
    }
}

/** Lays a widget out from its model, its configuration and the room it has. */
class WidgetLayout(
    private val config: WidgetConfig,
    private val type: TextMetrics,
    private val strings: WidgetStrings,
    private val locale: Locale,
    private val weekStart: DayOfWeek,
) {
    /**
     * Lays [model] out in [width] x [height] dp. When the natural layout is too tall, it is laid
     * out again proportionally wider before shrinking, so the shrunk drawing still fills the width.
     */
    fun layout(model: WidgetModel, width: Float, height: Float): WidgetDrawing {
        val first = natural(model, width, height)
        if (first.scale >= 1f) return first
        val shrink = first.scale
        val second = natural(model, width / shrink, height / shrink)
        return second.copy(width = width, height = height, scale = shrink * second.scale)
    }

    private fun natural(model: WidgetModel, width: Float, height: Float): WidgetDrawing = when (model) {
        WidgetModel.Loading -> WidgetDrawing.Empty

        WidgetModel.NoHabits -> message(strings.noHabits, Tap.OpenApp, width, height)

        WidgetModel.HabitMissing -> message(strings.habitMissing, Tap.Configure, width, height)

        is WidgetModel.AllHabits -> rows(model, width, height)

        is WidgetModel.OneHabit -> if (height >=
            width * CALENDAR_ASPECT
        ) {
            calendar(model, width, height)
        } else {
            strip(model, width, height)
        }
    }

    private fun shows(element: Element) = config.shows(element)

    private fun lineHeight(role: TextRole) = type.lineHeight(role)

    private fun fit(natural: Float, height: Float) = if (natural > height && natural > 0f) height / natural else 1f

    private fun message(text: String, tap: Tap, width: Float, height: Float): WidgetDrawing {
        val words = Mark.Words(
            text,
            TextRole.Message,
            width / 2,
            (height - lineHeight(TextRole.Message)) / 2,
            Align.Center,
            Tone.Label,
        )
        return WidgetDrawing(
            width,
            height,
            1f,
            listOf(words),
            listOf(HitArea(0f, height, tap, text)),
            text,
            usesAccents = false,
        )
    }

    // All habits, one row each.

    private fun rows(model: WidgetModel.AllHabits, width: Float, height: Float): WidgetDrawing {
        val histories = model.histories
        val marks = mutableListOf<Mark>()
        val hits = mutableListOf<HitArea>()

        val legends = histories.map { legendLabels(it.habit) }
        val nameWidth =
            if (shows(Element.Names) || legends.any { it.isNotEmpty() }) {
                val names = if (shows(Element.Names)) {
                    histories.maxOf {
                        type.width(it.habit.name, TextRole.Name)
                    }
                } else {
                    0f
                }
                val widestLabel = legends.flatten().maxOfOrNull { type.width(it.first, TextRole.Caption) } ?: 0f
                max(names, widestLabel).coerceIn(min(MIN_NAME_WIDTH, width * MAX_NAME_SHARE), width * MAX_NAME_SHARE)
            } else {
                0f
            }

        // The summary column depends on how many days are shown, which depends on the room the
        // summary column leaves; two passes settle it.
        var summaryWidth = DEFAULT_SUMMARY_WIDTH
        var dayCount = 0
        var summaries = emptyList<SummaryText>()
        var picks = emptyList<WindowSummary.Picks?>()
        repeat(2) {
            val glyphWidth = width - nameWidth - summaryWidth - gaps(nameWidth, summaryWidth)
            dayCount =
                config.days.count ?: floor(glyphWidth / ROW_DAY_WIDTH).toInt().coerceIn(MIN_ROW_DAYS, MAX_ROW_DAYS)
            val dates = DayRange.lastDays(model.today, dayCount).map { it.date }
            val windows = histories.map { it.habit.windowSummary(it.days, dates) }
            summaries = histories.zip(windows) { history, window -> strings.summary(history.habit, window) }
            picks = windows.map { (it as? WindowSummary.Picks)?.takeIf { picks -> picks.mix.isNotEmpty() } }
            summaryWidth = summaryWidth(summaries, picks, width)
        }
        val glyphWidth = width - nameWidth - summaryWidth - gaps(nameWidth, summaryWidth)
        val dates = DayRange.lastDays(model.today, dayCount).map { it.date }

        // Header.
        var top = 0f
        val headerHeight = header(marks, dates, width)
        if (headerHeight > 0f) {
            hits += HitArea(0f, headerHeight + HEADER_GAP, Tap.OpenApp)
            top = headerHeight + HEADER_GAP
        }

        // Rows: as tall as the tallest name or total needs, and never shorter than the design.
        val legendLines = legends.map { breakLines(it, nameWidth) }
        val nameBlocks = legendLines.map { nameBlockHeight(it) }
        val summaryBlocks = summaries.zip(picks) { summary, mix -> summaryBlockHeight(summary, mix) }
        val rowHeight = max(
            RowsScene.ROW_HEIGHT,
            max(nameBlocks.maxOrNull() ?: 0f, summaryBlocks.maxOrNull() ?: 0f) + ROW_PADDING,
        )
        val lettersHeight = if (shows(Element.Weekdays)) {
            max(
                RowsScene.LETTERS_HEIGHT,
                lineHeight(TextRole.Caption) + LETTERS_PADDING,
            )
        } else {
            0f
        }

        histories.forEachIndexed { index, history ->
            val rowTop = top + index * rowHeight
            var y = rowTop + (rowHeight - nameBlocks[index]) / 2
            if (shows(Element.Names)) {
                marks += Mark.Words(history.habit.name, TextRole.Name, 0f, y)
                y += lineHeight(TextRole.Name) + LEGEND_TOP_GAP
            }
            legendLines[index].forEach { line ->
                var x = 0f
                line.forEach { (label, swatch) ->
                    marks += Mark.Words(label, TextRole.Caption, x, y, swatch = swatch)
                    x += type.width(label, TextRole.Caption) + LEGEND_GAP
                }
                y += lineHeight(TextRole.Caption)
            }
            summaryMarks(
                marks,
                summaries[index],
                picks[index],
                width,
                summaryWidth,
                rowTop + (rowHeight - summaryBlocks[index]) / 2,
            )
            hits += HitArea(rowTop, rowHeight, Tap.Log(history.habit), strings.row(history.habit, summaries[index]))
        }

        val glyphLeft = if (nameWidth > 0f) nameWidth + COLUMN_GAP else 0f
        val scene = HabitScenes.rows(histories, model.today, dayCount, locale)
        marks += Mark.Rows(scene, glyphLeft, top, glyphWidth, rowHeight, lettersHeight)
        val rowsBottom = top + histories.size * rowHeight
        if (lettersHeight > 0f) hits += HitArea(rowsBottom, lettersHeight, Tap.OpenApp)

        val natural = rowsBottom + lettersHeight
        return WidgetDrawing(
            width = width,
            height = height,
            scale = fit(natural, height),
            marks = marks,
            hits = hits,
            description = strings.overview(dayCount),
            usesAccents = histories.any { it.usesAccents() },
        )
    }

    private fun gaps(nameWidth: Float, summaryWidth: Float) =
        (if (nameWidth > 0f) COLUMN_GAP else 0f) + (if (summaryWidth > 0f) COLUMN_GAP else 0f)

    /** The word mark and date range, if shown; returns the header's height. */
    private fun header(marks: MutableList<Mark>, dates: List<LocalDate>, width: Float): Float {
        val wordmark = shows(Element.Header)
        val range = shows(Element.DateRange)
        if (!wordmark && !range) return 0f
        val height =
            max(if (wordmark) lineHeight(TextRole.Wordmark) else 0f, if (range) lineHeight(TextRole.Label) else 0f)
        if (wordmark) {
            val top = (height - lineHeight(TextRole.Wordmark)) / 2
            marks += Mark.Words(strings.wordmark, TextRole.Wordmark, 0f, top)
            val dotX = type.width(strings.wordmark, TextRole.Wordmark) + WORDMARK_DOT_GAP + WORDMARK_DOT_RADIUS
            marks += Mark.Dot(dotX, top + lineHeight(TextRole.Wordmark) * WORDMARK_DOT_HEIGHT, WORDMARK_DOT_RADIUS)
        }
        if (range) {
            val top = (height - lineHeight(TextRole.Label)) / 2
            marks +=
                Mark.Words(
                    strings.range(dates.first(), dates.last()),
                    TextRole.Label,
                    width,
                    top,
                    Align.End,
                    Tone.Label,
                )
        }
        return height
    }

    /** A choice habit's active options, lower-cased, with their colours; empty when hidden. */
    private fun legendLabels(habit: Habit): List<Pair<String, Swatch>> {
        val kind = habit.kind as? HabitKind.Choice ?: return emptyList()
        if (!shows(Element.Legend)) return emptyList()
        return kind.activeOptions.map { it.label.lowercase(locale) to it.swatch }
    }

    private fun breakLines(labels: List<Pair<String, Swatch>>, width: Float) =
        breakLines(labels, width, LEGEND_GAP) { type.width(it.first, TextRole.Caption) }

    private fun nameBlockHeight(legendLines: List<List<Pair<String, Swatch>>>): Float {
        val name = if (shows(Element.Names)) lineHeight(TextRole.Name) else 0f
        val legend = legendLines.size * lineHeight(TextRole.Caption)
        return name + if (legend > 0f && name > 0f) LEGEND_TOP_GAP + legend else legend
    }

    private fun showsMix(mix: WindowSummary.Picks?) = mix != null && shows(Element.Mix)

    private fun showsCaption(summary: SummaryText) = shows(Element.Captions) && summary.label.isNotBlank()

    private fun summaryWidth(summaries: List<SummaryText>, picks: List<WindowSummary.Picks?>, width: Float): Float {
        val totals = if (shows(Element.Totals)) {
            summaries.maxOfOrNull {
                type.width(it.value, TextRole.Total)
            } ?: 0f
        } else {
            0f
        }
        val captions = summaries.filter(::showsCaption).maxOfOrNull { type.width(it.label, TextRole.Caption) } ?: 0f
        val mix = if (picks.any(::showsMix)) MIX_WIDTH else 0f
        val natural = max(totals, max(captions, mix))
        return if (natural == 0f) 0f else natural.coerceAtMost(width * MAX_SUMMARY_SHARE)
    }

    private fun summaryBlockHeight(summary: SummaryText, mix: WindowSummary.Picks?): Float {
        var height = if (shows(Element.Totals)) lineHeight(TextRole.Total) else 0f
        if (showsMix(mix)) height += MIX_GAP * 2 + MIX_HEIGHT
        if (showsCaption(summary)) height += lineHeight(TextRole.Caption)
        return height
    }

    private fun summaryMarks(
        marks: MutableList<Mark>,
        summary: SummaryText,
        mix: WindowSummary.Picks?,
        right: Float,
        columnWidth: Float,
        top: Float,
    ) {
        var y = top
        if (shows(Element.Totals)) {
            marks += Mark.Words(summary.value, TextRole.Total, right, y, Align.End)
            y += lineHeight(TextRole.Total)
        }
        if (mix != null && showsMix(mix)) {
            val mixWidth = min(MIX_WIDTH, columnWidth)
            marks +=
                Mark.Mix(mix.mix.map { it.swatch to it.count }, right - mixWidth, y + MIX_GAP, mixWidth, MIX_HEIGHT)
            y += MIX_GAP * 2 + MIX_HEIGHT
        }
        if (showsCaption(summary)) {
            marks += Mark.Words(summary.label, TextRole.Caption, right, y, Align.End, Tone.Label)
        }
    }

    // One habit, wide: a strip of days.

    private fun strip(model: WidgetModel.OneHabit, width: Float, height: Float): WidgetDrawing {
        val history = model.history
        val habit = history.habit
        val marks = mutableListOf<Mark>()
        val weeks =
            config.days.count?.let { ceil(it / DAYS_PER_WEEK).toInt() }
                ?: floor(width / STRIP_DAY_WIDTH / DAYS_PER_WEEK).toInt().coerceIn(1, MAX_WEEKS)
        val dayCount = config.days.count ?: (weeks * DayRange.DAYS_PER_WEEK)
        val dates = DayRange.lastDays(model.today, dayCount).map { it.date }
        val summary = strings.summary(habit, habit.windowSummary(history.days, dates))

        val headerHeight = singleHeader(marks, habit, summary, mix = null, width, TextRole.Total, strings.weeks(weeks))
        val stripTop = if (headerHeight > 0f) headerHeight + HEADER_GAP else 0f
        val labelHeight = if (shows(
                Element.Weekdays,
            )
        ) {
            max(StripScene.LABEL_HEIGHT, lineHeight(TextRole.Caption))
        } else {
            0f
        }
        val minimum = MIN_STRIP_HEIGHT + if (labelHeight > 0f) StripScene.LABEL_GAP + labelHeight else 0f
        val stripHeight = max(height - stripTop, minimum)
        val scene =
            HabitScenes.strip(history, model.today, dayCount, locale).let {
                if (shows(Element.Weekdays)) it else it.copy(labels = emptyMap())
            }
        marks += Mark.Strip(scene, 0f, stripTop, width, stripHeight, labelHeight)

        val natural = stripTop + stripHeight
        return WidgetDrawing(
            width = width,
            height = height,
            scale = fit(natural, height),
            marks = marks,
            hits = listOf(HitArea(0f, natural, Tap.Log(habit), strings.row(habit, summary))),
            description = strings.history(habit, dayCount),
            usesAccents = history.usesAccents(),
        )
    }

    /**
     * A single habit's header: its name and [subtitle] on the left, the total (with its mix and
     * caption) on the right. Returns its height.
     */
    private fun singleHeader(
        marks: MutableList<Mark>,
        habit: Habit,
        summary: SummaryText,
        mix: WindowSummary.Picks?,
        width: Float,
        totalRole: TextRole,
        subtitle: String?,
    ): Float {
        val name = shows(Element.Header)
        val sub = subtitle != null && shows(Element.DateRange)
        val total = shows(Element.Totals)
        val caption = showsCaption(summary)
        val heights =
            listOfNotNull(
                lineHeight(TextRole.Name).takeIf { name },
                lineHeight(TextRole.Label).takeIf { sub },
                lineHeight(totalRole).takeIf { total },
                lineHeight(TextRole.Caption).takeIf { caption },
            )
        val height = heights.maxOrNull() ?: return if (showsMix(mix)) MIX_HEIGHT else 0f
        fun centred(role: TextRole) = (height - lineHeight(role)) / 2

        var left = 0f
        if (name) {
            marks += Mark.Words(habit.name, TextRole.Name, left, centred(TextRole.Name))
            left += type.width(habit.name, TextRole.Name) + SUBTITLE_GAP
        }
        if (sub) marks += Mark.Words(subtitle, TextRole.Label, left, centred(TextRole.Label), tone = Tone.Label)

        var right = width
        if (caption) {
            marks +=
                Mark.Words(summary.label, TextRole.Caption, right, centred(TextRole.Caption), Align.End, Tone.Label)
            right -= type.width(summary.label, TextRole.Caption) + CAPTION_GAP
        }
        if (total) {
            marks += Mark.Words(summary.value, totalRole, right, centred(totalRole), Align.End)
            right -= type.width(summary.value, totalRole) + MIX_TOTAL_GAP
        }
        if (mix != null && showsMix(mix)) {
            marks +=
                Mark.Mix(
                    mix.mix.map {
                        it.swatch to it.count
                    },
                    right - HEADER_MIX_WIDTH,
                    (height - MIX_HEIGHT) / 2,
                    HEADER_MIX_WIDTH,
                    MIX_HEIGHT,
                )
        }
        return height
    }

    // One habit, tall: a calendar of recent weeks.

    private fun calendar(model: WidgetModel.OneHabit, width: Float, height: Float): WidgetDrawing {
        val history = model.history
        val habit = history.habit
        val marks = mutableListOf<Mark>()

        val legend = legendLabels(habit)
        val legendLines = breakLines(legend, width)
        val legendHeight = if (legendLines.isEmpty()) {
            0f
        } else {
            CALENDAR_GAP +
                legendLines.size * lineHeight(TextRole.Caption)
        }
        val lettersHeight = if (shows(Element.Weekdays)) CalendarScene.HEADER_HEIGHT + CalendarScene.HEADER_GAP else 0f

        // The header's height doesn't depend on the weeks shown, so measure it first.
        val probe = singleHeader(mutableListOf(), habit, SummaryText("0", ""), null, width, TextRole.SmallTotal, null)
        val gridTop = if (probe > 0f) probe + CALENDAR_GAP else 0f
        val gridRoom = height - gridTop - lettersHeight - legendHeight
        val pitch = CalendarScene.ROW_HEIGHT + CalendarScene.ROW_GAP
        val weeks =
            config.days.count?.let { ceil(it / DAYS_PER_WEEK).toInt().coerceIn(1, MAX_WEEKS) }
                ?: floor((gridRoom + CalendarScene.ROW_GAP) / pitch).toInt().coerceIn(MIN_WEEKS, MAX_WEEKS)
        val rowHeight = ((gridRoom - (weeks - 1) * CalendarScene.ROW_GAP) / weeks).coerceIn(
            CalendarScene.ROW_HEIGHT,
            MAX_CALENDAR_ROW,
        )

        val full = HabitScenes.lastWeeks(history, model.today, weeks, weekStart, locale).copy(rowHeight = rowHeight)
        val scene = if (shows(Element.Weekdays)) full else full.copy(letters = emptyList())
        val dates = scene.cells.map { it.first }.filterNot { it.isFuture }.map { it.date }
        val window = habit.windowSummary(history.days, dates)
        val summary = strings.summary(habit, window)
        val mix = (window as? WindowSummary.Picks)?.takeIf { it.mix.isNotEmpty() }
        singleHeader(marks, habit, summary, mix, width, TextRole.SmallTotal, subtitle = null)

        // A hidden letters row still occupies the scene's own coordinates, so lift the scene.
        val hiddenLetters = if (shows(Element.Weekdays)) 0f else CalendarScene.HEADER_HEIGHT + CalendarScene.HEADER_GAP
        marks += Mark.Calendar(scene, 0f, gridTop - hiddenLetters, width)
        val gridBottom = gridTop - hiddenLetters + scene.height

        var y = gridBottom + if (legendLines.isEmpty()) 0f else CALENDAR_GAP
        legendLines.forEach { line ->
            var x = 0f
            line.forEach { (label, swatch) ->
                marks += Mark.Words(label, TextRole.Caption, x, y, swatch = swatch)
                x += type.width(label, TextRole.Caption) + LEGEND_GAP
            }
            y += lineHeight(TextRole.Caption)
        }

        val natural = y
        return WidgetDrawing(
            width = width,
            height = height,
            scale = fit(natural, height),
            marks = marks,
            hits = listOf(HitArea(0f, natural, Tap.Log(habit), strings.row(habit, summary))),
            description = strings.history(habit, dates.size),
            usesAccents = history.usesAccents(),
        )
    }

    private companion object {
        const val HEADER_GAP = 10f
        const val COLUMN_GAP = 10f
        const val ROW_PADDING = 4f
        const val LETTERS_PADDING = 10f
        const val MIN_NAME_WIDTH = 44f
        const val MAX_NAME_SHARE = 0.36f
        const val MAX_SUMMARY_SHARE = 0.3f
        const val DEFAULT_SUMMARY_WIDTH = 52f
        const val LEGEND_GAP = 5f
        const val LEGEND_TOP_GAP = 2f
        const val MIX_WIDTH = 44f
        const val HEADER_MIX_WIDTH = 32f
        const val MIX_HEIGHT = 4f
        const val MIX_GAP = 3f
        const val MIX_TOTAL_GAP = 7f
        const val CAPTION_GAP = 6f
        const val SUBTITLE_GAP = 8f
        const val WORDMARK_DOT_GAP = 3f
        const val WORDMARK_DOT_RADIUS = 2.5f

        /** Where the word mark's dot sits, as a fraction of its line from the top. */
        const val WORDMARK_DOT_HEIGHT = 0.68f

        /** Width of one day in the rows (14 days across a phone) and the strip (four weeks). */
        const val ROW_DAY_WIDTH = 14.5f
        const val STRIP_DAY_WIDTH = 12f
        const val MIN_ROW_DAYS = 5
        const val MAX_ROW_DAYS = 31
        const val MIN_WEEKS = 2
        const val MAX_WEEKS = 6
        const val DAYS_PER_WEEK = 7f
        const val MIN_STRIP_HEIGHT = 24f
        const val CALENDAR_GAP = 6f
        const val MAX_CALENDAR_ROW = 40f

        /** Taller than this fraction of its width, a single habit shows as a calendar. */
        const val CALENDAR_ASPECT = 0.7f
    }
}

/** Greedily packs [items] into lines no wider than [width], [gap] apart. */
internal fun <T> breakLines(items: List<T>, width: Float, gap: Float, widthOf: (T) -> Float): List<List<T>> {
    val lines = mutableListOf<MutableList<T>>()
    var used = 0f
    for (item in items) {
        val itemWidth = widthOf(item)
        val line = lines.lastOrNull()
        if (line != null && used + gap + itemWidth <= width) {
            line += item
            used += gap + itemWidth
        } else {
            lines += mutableListOf(item)
            used = itemWidth
        }
    }
    return lines
}

internal fun HabitHistory.usesAccents(): Boolean =
    (habit.kind as? HabitKind.Choice)?.options?.any { it.swatch != Swatch.Ink } == true
