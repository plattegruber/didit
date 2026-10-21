package com.plattegruber.didit.core.domain

import com.plattegruber.didit.core.domain.model.ChoiceOption
import com.plattegruber.didit.core.domain.model.Entry
import com.plattegruber.didit.core.domain.model.EntryId
import com.plattegruber.didit.core.domain.model.EntryValue
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.OptionId
import com.plattegruber.didit.core.domain.model.Swatch
import java.time.Instant
import java.time.LocalDate

/** Wednesday. */
val TODAY: LocalDate = LocalDate.of(2026, 10, 21)

val HOME = ChoiceOption(OptionId(1), "Home", Swatch.Ink)
val TAKEOUT = ChoiceOption(OptionId(2), "Takeout", Swatch.Blue)
val OUT = ChoiceOption(OptionId(3), "Out", Swatch.Amber)

fun habit(kind: HabitKind = HabitKind.Done) = Habit(HabitId(1), "Habit", kind, Instant.EPOCH)

fun pagesHabit() = habit(HabitKind.Amount("pages"))

fun coffeeHabit() = habit(HabitKind.Count("cups"))

fun mealsHabit(vararg extra: ChoiceOption) = habit(HabitKind.Choice("meals", listOf(HOME, TAKEOUT, OUT) + extra))

private var nextEntryId = 1L

fun entry(date: LocalDate, value: EntryValue, loggedAt: Instant = Instant.ofEpochSecond(nextEntryId)) =
    Entry(EntryId(nextEntryId++), HabitId(1), date, value, loggedAt)

fun daysAgo(n: Long): LocalDate = TODAY.minusDays(n)
