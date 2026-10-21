package com.plattegruber.didit.core.domain.stats

import com.google.common.truth.Truth.assertThat
import com.plattegruber.didit.core.domain.HOME
import com.plattegruber.didit.core.domain.OUT
import com.plattegruber.didit.core.domain.TAKEOUT
import com.plattegruber.didit.core.domain.TODAY
import com.plattegruber.didit.core.domain.coffeeHabit
import com.plattegruber.didit.core.domain.daysAgo
import com.plattegruber.didit.core.domain.habit
import com.plattegruber.didit.core.domain.mealsHabit
import com.plattegruber.didit.core.domain.model.ChoiceOption
import com.plattegruber.didit.core.domain.model.DayValue
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.OptionId
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.core.domain.pagesHabit
import java.time.LocalDate
import org.junit.Test

class HabitStatsTest {
    private fun done(vararg daysAgo: Long): Map<LocalDate, DayValue> =
        daysAgo.associate { daysAgo(it) to DayValue.Done }

    @Test
    fun `current streak counts back from today`() {
        assertThat(habit().stats(done(0, 1, 2, 4), TODAY).currentStreak).isEqualTo(3)
    }

    @Test
    fun `an unlogged today does not break the streak`() {
        assertThat(habit().stats(done(1, 2, 3), TODAY).currentStreak).isEqualTo(3)
    }

    @Test
    fun `a missed yesterday ends the streak`() {
        assertThat(habit().stats(done(2, 3), TODAY).currentStreak).isEqualTo(0)
        assertThat(habit().stats(emptyMap(), TODAY).currentStreak).isEqualTo(0)
    }

    @Test
    fun `longest streak spans the whole history`() {
        assertThat(habit().stats(done(0, 10, 11, 12, 13, 50, 51), TODAY).longestStreak).isEqualTo(4)
    }

    @Test
    fun `active days only count the trailing window`() {
        assertThat(habit().stats(done(0, 29, 30, 31), TODAY).activeDays).isEqualTo(2)
    }

    @Test
    fun `amounts and counts total the window and find the best day`() {
        val days =
            mapOf(
                daysAgo(0) to DayValue.Amount(10.0),
                daysAgo(3) to DayValue.Amount(40.0),
                daysAgo(8) to DayValue.Amount(40.0),
                daysAgo(40) to DayValue.Amount(500.0),
            )
        val expected =
            HabitStats.Detail.Totals(
                total = 90.0,
                dailyAverage = 3.0,
                // Ties go to the most recent day.
                best = HabitStats.Best(daysAgo(3), 40.0),
            )

        assertThat(pagesHabit().stats(days, TODAY).detail).isEqualTo(expected)
        assertThat(coffeeHabit().stats(days, TODAY).detail).isEqualTo(expected)
    }

    @Test
    fun `totals with nothing logged have no best day`() {
        val detail = pagesHabit().stats(emptyMap(), TODAY).detail as HabitStats.Detail.Totals

        assertThat(detail.best).isNull()
        assertThat(detail.total).isEqualTo(0.0)
    }

    @Test
    fun `choices share picks between options, largest first`() {
        val days =
            mapOf(
                daysAgo(0) to DayValue.Choices(listOf(OUT.id)),
                daysAgo(1) to DayValue.Choices(listOf(HOME.id, HOME.id)),
                daysAgo(2) to DayValue.Choices(listOf(HOME.id)),
            )

        val detail = mealsHabit().stats(days, TODAY).detail

        assertThat(detail).isEqualTo(
            HabitStats.Detail.Mix(
                listOf(
                    HabitStats.Share(HOME, 3, 0.75),
                    HabitStats.Share(OUT, 1, 0.25),
                    HabitStats.Share(TAKEOUT, 0, 0.0),
                ),
            ),
        )
    }

    @Test
    fun `archived options only appear while they still have picks`() {
        val skipped = ChoiceOption(OptionId(8), "Skipped", Swatch.Sage, archived = true)
        val gone = ChoiceOption(OptionId(9), "Gone", Swatch.Lilac, archived = true)
        val days = mapOf(daysAgo(0) to DayValue.Choices(listOf(skipped.id)))

        val shares = mix(HabitKind.Choice("", listOf(HOME, skipped, gone)), days.values)

        assertThat(shares.map { it.option }).containsExactly(skipped, HOME).inOrder()
    }

    @Test
    fun `yes or no habits have no extra detail`() {
        assertThat(habit().stats(done(0), TODAY).detail).isEqualTo(HabitStats.Detail.None)
    }
}
