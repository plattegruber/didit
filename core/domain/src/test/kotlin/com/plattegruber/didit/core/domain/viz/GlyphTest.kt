package com.plattegruber.didit.core.domain.viz

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
import com.plattegruber.didit.core.domain.model.OptionId
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.core.domain.pagesHabit
import org.junit.Test

class GlyphTest {
    private val week = (6L downTo 0L).map(::daysAgo)

    @Test
    fun `done days are filled and the rest are rings`() {
        val glyphs = habit().glyphs(mapOf(TODAY to DayValue.Done), listOf(daysAgo(1), TODAY))

        assertThat(glyphs).containsExactly(Glyph.Check(done = false), Glyph.Check(done = true)).inOrder()
    }

    @Test
    fun `choice days stack one dot per pick in the option's colour`() {
        val days = mapOf(TODAY to DayValue.Choices(listOf(HOME.id, OUT.id, HOME.id, TAKEOUT.id)))

        val glyphs = mealsHabit().glyphs(days, listOf(daysAgo(1), TODAY))

        assertThat(glyphs)
            .containsExactly(
                Glyph.Dots(emptyList()),
                Glyph.Dots(listOf(Swatch.Ink, Swatch.Amber, Swatch.Ink, Swatch.Blue)),
            ).inOrder()
    }

    @Test
    fun `archived options still draw and unknown ones are dropped`() {
        val skipped = ChoiceOption(OptionId(8), "Skipped", Swatch.Sage, archived = true)
        val days = mapOf(TODAY to DayValue.Choices(listOf(skipped.id, OptionId(99))))

        assertThat(mealsHabit(skipped).glyphs(days, listOf(TODAY))).containsExactly(Glyph.Dots(listOf(Swatch.Sage)))
    }

    @Test
    fun `count days draw one pip per tally`() {
        val days = mapOf(TODAY to DayValue.Amount(3.0))

        assertThat(coffeeHabit().glyphs(days, listOf(daysAgo(1), TODAY)))
            .containsExactly(Glyph.Pips(0), Glyph.Pips(3))
            .inOrder()
    }

    @Test
    fun `amount bars scale to the busiest ordinary day on screen`() {
        // Nine ordinary days of 10..90 pages and one 300-page binge: the binge caps out instead of
        // flattening everything else.
        val dates = (9L downTo 0L).map(::daysAgo)
        val totals = (1..9).map { it * 10.0 } + 300.0
        val days = dates.zip(totals).associate { it.first to DayValue.Amount(it.second) }

        val bars = pagesHabit().glyphs(days, dates).map { (it as Glyph.Bar).fraction }

        assertThat(bars.first()).isWithin(1e-6f).of(1f / 9)
        assertThat(bars[4]).isWithin(1e-6f).of(5f / 9)
        assertThat(bars.takeLast(2)).containsExactly(1f, 1f)
    }

    @Test
    fun `an amount habit with nothing logged draws stubs`() {
        assertThat(pagesHabit().glyphs(emptyMap(), week)).containsExactlyElementsIn(List(7) { Glyph.Bar(0f) })
    }
}
