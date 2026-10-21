package com.plattegruber.didit.ui.viz

import com.plattegruber.didit.core.domain.model.DayValue
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.viz.DayRange
import com.plattegruber.didit.core.domain.viz.glyphs
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** A habit together with its logged days. */
data class HabitHistory(val habit: Habit, val days: Map<LocalDate, DayValue>)

/** Builds scenes from habits. The one place that decides what a view shows; the painter only draws. */
object HabitScenes {
    fun rows(histories: List<HabitHistory>, today: LocalDate, dayCount: Int, locale: Locale): RowsScene {
        val days = DayRange.lastDays(today, dayCount)
        val dates = days.map { it.date }
        return RowsScene(
            rows = histories.map { it.habit.glyphs(it.days, dates) },
            days = days,
            letters = days.map { it.date.dayOfWeek.letter(locale) },
        )
    }

    /** A long strip of days, labelled at the start of each week counted back from today. */
    fun strip(history: HabitHistory, today: LocalDate, dayCount: Int, locale: Locale): StripScene {
        val days = DayRange.lastDays(today, dayCount)
        val format = DateTimeFormatter.ofPattern("MMM d", locale)
        val labels =
            days.indices
                .filter { (days.size - 1 - it) % DayRange.DAYS_PER_WEEK == DayRange.DAYS_PER_WEEK - 1 }
                .associateWith { days[it].date.format(format) }
        return StripScene(history.habit.glyphs(history.days, days.map { it.date }), days, labels)
    }

    fun lastWeeks(
        history: HabitHistory,
        today: LocalDate,
        weeks: Int,
        weekStart: DayOfWeek,
        locale: Locale,
    ): CalendarScene {
        val cells = DayRange.lastWeeks(today, weeks, weekStart)
        val glyphs = history.habit.glyphs(history.days, cells.map { it.date })
        return CalendarScene(cells.zip(glyphs), weekStart.letters(locale), rows = weeks)
    }

    fun month(
        history: HabitHistory,
        month: YearMonth,
        today: LocalDate,
        weekStart: DayOfWeek,
        locale: Locale,
    ): CalendarScene {
        val cells = DayRange.month(month, today, weekStart)
        val glyphs = history.habit.glyphs(history.days, cells.map { it.date })
        return CalendarScene(
            cells = cells.zip(glyphs),
            letters = weekStart.letters(locale),
            rows = cells.maxOf { it.row } + 1,
            rowHeight = MONTH_ROW_HEIGHT,
            showDayNumbers = true,
        )
    }

    const val MONTH_ROW_HEIGHT = 52f

    private fun DayOfWeek.letter(locale: Locale): String = getDisplayName(TextStyle.NARROW, locale)

    private fun DayOfWeek.letters(locale: Locale): List<String> = DayRange.weekdays(this).map { it.letter(locale) }
}
