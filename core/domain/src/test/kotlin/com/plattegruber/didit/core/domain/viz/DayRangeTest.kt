package com.plattegruber.didit.core.domain.viz

import com.google.common.truth.Truth.assertThat
import com.plattegruber.didit.core.domain.TODAY
import com.plattegruber.didit.core.domain.daysAgo
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class DayRangeTest {
    @Test
    fun `last days run oldest first and end today`() {
        val days = DayRange.lastDays(TODAY, 14)

        assertThat(days).hasSize(14)
        assertThat(days.first().date).isEqualTo(daysAgo(13))
        assertThat(days.last()).isEqualTo(DayColumn(TODAY, isToday = true, isWeekend = false))
        assertThat(days.count { it.isToday }).isEqualTo(1)
    }

    @Test
    fun `weekends are flagged`() {
        val weekend = DayRange.lastDays(TODAY, 7).filter { it.isWeekend }.map { it.date.dayOfWeek }

        assertThat(weekend).containsExactly(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).inOrder()
    }

    @Test
    fun `last weeks is a full grid with today's week last`() {
        val cells = DayRange.lastWeeks(TODAY, weeks = 4, weekStart = DayOfWeek.MONDAY)

        assertThat(cells).hasSize(28)
        assertThat(cells.first().date).isEqualTo(LocalDate.of(2026, 9, 28))
        assertThat(cells.first().date.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        val today = cells.single { it.isToday }
        // Wednesday is the third column of a Monday-first week, in the last row.
        assertThat(today.column to today.row).isEqualTo(2 to 3)
        assertThat(cells.count { it.isFuture }).isEqualTo(4)
    }

    @Test
    fun `last weeks respects the week start`() {
        val cells = DayRange.lastWeeks(TODAY, weeks = 1, weekStart = DayOfWeek.SUNDAY)

        assertThat(cells.first().date).isEqualTo(LocalDate.of(2026, 10, 18))
        assertThat(cells.single { it.isToday }.column).isEqualTo(3)
    }

    @Test
    fun `month places days like a wall calendar`() {
        // October 2026 starts on a Thursday.
        val cells = DayRange.month(YearMonth.of(2026, 10), TODAY, DayOfWeek.MONDAY)

        assertThat(cells).hasSize(31)
        assertThat(cells.first().column to cells.first().row).isEqualTo(3 to 0)
        assertThat(cells.last().column to cells.last().row).isEqualTo(5 to 4)
        assertThat(cells.count { it.isFuture }).isEqualTo(10)
    }

    @Test
    fun `weekdays start on the given day`() {
        assertThat(DayRange.weekdays(DayOfWeek.SUNDAY).take(2)).containsExactly(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)
        assertThat(DayRange.weekdays(DayOfWeek.MONDAY).last()).isEqualTo(DayOfWeek.SUNDAY)
    }
}
