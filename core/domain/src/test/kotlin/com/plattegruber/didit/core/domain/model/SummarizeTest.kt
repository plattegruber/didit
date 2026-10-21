package com.plattegruber.didit.core.domain.model

import com.google.common.truth.Truth.assertThat
import com.plattegruber.didit.core.domain.HOME
import com.plattegruber.didit.core.domain.OUT
import com.plattegruber.didit.core.domain.TODAY
import com.plattegruber.didit.core.domain.coffeeHabit
import com.plattegruber.didit.core.domain.daysAgo
import com.plattegruber.didit.core.domain.entry
import com.plattegruber.didit.core.domain.mealsHabit
import com.plattegruber.didit.core.domain.pagesHabit
import org.junit.Test
import java.time.Instant

class SummarizeTest {
    @Test
    fun `done entries collapse to one done per day`() {
        val days =
            HabitKind.Done.summarize(
                listOf(
                    entry(TODAY, EntryValue.Done),
                    entry(TODAY, EntryValue.Done),
                    entry(daysAgo(2), EntryValue.Done),
                ),
            )

        assertThat(days).containsExactly(TODAY, DayValue.Done, daysAgo(2), DayValue.Done)
    }

    @Test
    fun `amounts and counts on the same day are summed`() {
        val entries =
            listOf(
                entry(TODAY, EntryValue.Amount(12.0)),
                entry(TODAY, EntryValue.Amount(8.5)),
                entry(daysAgo(1), EntryValue.Amount(3.0)),
            )

        val expected = mapOf(TODAY to DayValue.Amount(20.5), daysAgo(1) to DayValue.Amount(3.0))
        assertThat(pagesHabit().kind.summarize(entries)).isEqualTo(expected)
        assertThat(coffeeHabit().kind.summarize(entries)).isEqualTo(expected)
    }

    @Test
    fun `choices keep every pick in logged order`() {
        val days =
            mealsHabit().kind.summarize(
                listOf(
                    entry(TODAY, EntryValue.Choice(OUT.id), loggedAt = Instant.ofEpochSecond(200)),
                    entry(TODAY, EntryValue.Choice(HOME.id), loggedAt = Instant.ofEpochSecond(100)),
                    entry(TODAY, EntryValue.Choice(OUT.id), loggedAt = Instant.ofEpochSecond(300)),
                ),
            )

        assertThat(days).containsExactly(TODAY, DayValue.Choices(listOf(HOME.id, OUT.id, OUT.id)))
    }

    @Test
    fun `entries of the wrong kind are ignored`() {
        val days =
            mealsHabit().kind.summarize(
                listOf(
                    entry(TODAY, EntryValue.Amount(3.0)),
                    entry(TODAY, EntryValue.Done),
                    entry(TODAY, EntryValue.Choice(OptionId(99))),
                    entry(daysAgo(1), EntryValue.Choice(HOME.id)),
                ),
            )

        assertThat(days).containsExactly(daysAgo(1), DayValue.Choices(listOf(HOME.id)))
    }

    @Test
    fun `a day is a hit when anything that counts was logged`() {
        assertThat(isHit(DayValue.Done)).isTrue()
        assertThat(isHit(DayValue.Amount(0.5))).isTrue()
        assertThat(isHit(DayValue.Amount(0.0))).isFalse()
        assertThat(isHit(DayValue.Choices(listOf(OUT.id)))).isTrue()
        assertThat(isHit(DayValue.Choices(emptyList()))).isFalse()
        assertThat(isHit(null)).isFalse()
    }

    @Test
    fun `entry values know which habits accept them`() {
        val choice = mealsHabit().kind

        assertThat(EntryValue.Done.isCompatibleWith(HabitKind.Done)).isTrue()
        assertThat(EntryValue.Done.isCompatibleWith(choice)).isFalse()
        assertThat(EntryValue.Amount(1.0).isCompatibleWith(pagesHabit().kind)).isTrue()
        assertThat(EntryValue.Amount(1.0).isCompatibleWith(coffeeHabit().kind)).isTrue()
        assertThat(EntryValue.Amount(1.0).isCompatibleWith(HabitKind.Done)).isFalse()
        assertThat(EntryValue.Choice(HOME.id).isCompatibleWith(choice)).isTrue()
        assertThat(EntryValue.Choice(OptionId(42)).isCompatibleWith(choice)).isFalse()
    }
}
