package com.plattegruber.didit.core.domain.viz

import com.google.common.truth.Truth.assertThat
import com.plattegruber.didit.core.domain.HOME
import com.plattegruber.didit.core.domain.OUT
import com.plattegruber.didit.core.domain.TODAY
import com.plattegruber.didit.core.domain.coffeeHabit
import com.plattegruber.didit.core.domain.daysAgo
import com.plattegruber.didit.core.domain.habit
import com.plattegruber.didit.core.domain.mealsHabit
import com.plattegruber.didit.core.domain.model.DayValue
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.core.domain.pagesHabit
import org.junit.Test

class WindowSummaryTest {
    private val window = (3L downTo 0L).map(::daysAgo)

    @Test
    fun `done habits count done days out of the window`() {
        val days = mapOf(TODAY to DayValue.Done, daysAgo(2) to DayValue.Done, daysAgo(20) to DayValue.Done)

        assertThat(habit().windowSummary(days, window)).isEqualTo(WindowSummary.DaysDone(done = 2, of = 4))
    }

    @Test
    fun `amounts total the window`() {
        val days =
            mapOf(
                TODAY to DayValue.Amount(12.0),
                daysAgo(3) to DayValue.Amount(30.0),
                daysAgo(9) to DayValue.Amount(5.0),
            )

        assertThat(pagesHabit().windowSummary(days, window)).isEqualTo(WindowSummary.Total(42.0))
    }

    @Test
    fun `counts average over every day in the window`() {
        val days = mapOf(TODAY to DayValue.Amount(2.0), daysAgo(1) to DayValue.Amount(3.0))

        assertThat(coffeeHabit().windowSummary(days, window)).isEqualTo(WindowSummary.PerDay(1.25))
    }

    @Test
    fun `choices total the picks and split them in option order`() {
        val days =
            mapOf(
                TODAY to DayValue.Choices(listOf(OUT.id, HOME.id)),
                daysAgo(1) to DayValue.Choices(listOf(HOME.id, HOME.id)),
            )

        assertThat(mealsHabit().windowSummary(days, window)).isEqualTo(
            WindowSummary.Picks(
                total = 4,
                mix = listOf(WindowSummary.Slice(Swatch.Ink, 3), WindowSummary.Slice(Swatch.Amber, 1)),
            ),
        )
    }

    @Test
    fun `an empty window summarises to zero`() {
        assertThat(mealsHabit().windowSummary(emptyMap(), window)).isEqualTo(WindowSummary.Picks(0, emptyList()))
        assertThat(coffeeHabit().windowSummary(emptyMap(), window)).isEqualTo(WindowSummary.PerDay(0.0))
    }
}
