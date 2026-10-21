package com.plattegruber.didit.core.domain.viz

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** One day in a row of days. */
data class DayColumn(val date: LocalDate, val isToday: Boolean, val isWeekend: Boolean)

/** One slot of a weekday-by-week calendar grid. */
data class CalendarCell(val column: Int, val row: Int, val date: LocalDate, val isToday: Boolean, val isFuture: Boolean)

object DayRange {
    const val DAYS_PER_WEEK = 7

    /** The last [count] days, oldest first, ending with [today]. */
    fun lastDays(today: LocalDate, count: Int): List<DayColumn> {
        require(count > 0) { "count must be positive, was $count" }
        return (count - 1 downTo 0).map { back ->
            val date = today.minusDays(back.toLong())
            DayColumn(date, isToday = back == 0, isWeekend = date.dayOfWeek.isWeekend)
        }
    }

    /**
     * The last [weeks] calendar weeks, one row per week with today's week last. Days after today
     * are included, flagged [CalendarCell.isFuture], so the grid stays rectangular.
     */
    fun lastWeeks(today: LocalDate, weeks: Int, weekStart: DayOfWeek): List<CalendarCell> {
        require(weeks > 0) { "weeks must be positive, was $weeks" }
        val start = today.minusDays(today.dayIndex(weekStart).toLong()).minusWeeks(weeks - 1L)
        return (0 until weeks * DAYS_PER_WEEK).map { index ->
            val date = start.plusDays(index.toLong())
            CalendarCell(
                column = index % DAYS_PER_WEEK,
                row = index / DAYS_PER_WEEK,
                date = date,
                isToday = date == today,
                isFuture = date.isAfter(today),
            )
        }
    }

    /** A wall-calendar month. Only the month's own days appear; leading and trailing slots stay empty. */
    fun month(month: YearMonth, today: LocalDate, weekStart: DayOfWeek): List<CalendarCell> {
        val leading = month.atDay(1).dayIndex(weekStart)
        return (1..month.lengthOfMonth()).map { dayOfMonth ->
            val date = month.atDay(dayOfMonth)
            val slot = leading + dayOfMonth - 1
            CalendarCell(
                column = slot % DAYS_PER_WEEK,
                row = slot / DAYS_PER_WEEK,
                date = date,
                isToday = date == today,
                isFuture = date.isAfter(today),
            )
        }
    }

    /** The weekdays in display order, starting from [weekStart]. */
    fun weekdays(weekStart: DayOfWeek): List<DayOfWeek> = (0L until DAYS_PER_WEEK).map(weekStart::plus)
}

val DayOfWeek.isWeekend: Boolean get() = this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY

private fun LocalDate.dayIndex(weekStart: DayOfWeek): Int =
    (dayOfWeek.value - weekStart.value + DayRange.DAYS_PER_WEEK) % DayRange.DAYS_PER_WEEK
