package com.plattegruber.didit.core.domain.model

import java.time.LocalDate

/** Everything logged for one habit on one day, reduced to what the drawing needs. */
sealed interface DayValue {
    data object Done : DayValue

    /** The day's total, for amount and count habits. */
    data class Amount(val total: Double) : DayValue

    /** Each pick in the order it was logged; the same option may appear more than once. */
    data class Choices(val picks: List<OptionId>) : DayValue
}

/**
 * Folds raw entries into one [DayValue] per day.
 *
 * Entries that don't belong to this kind of habit are ignored rather than trusted, so a bad row
 * can never turn into a misleading mark.
 */
fun HabitKind.summarize(entries: Iterable<Entry>): Map<LocalDate, DayValue> {
    val byDay =
        entries
            .filter { it.value.isCompatibleWith(this) }
            .sortedBy(Entry::loggedAt)
            .groupBy(Entry::date)
    return byDay.mapValues { (_, dayEntries) ->
        when (this) {
            HabitKind.Done -> {
                DayValue.Done
            }

            is HabitKind.Amount, is HabitKind.Count -> {
                DayValue.Amount(dayEntries.sumOf { (it.value as EntryValue.Amount).amount })
            }

            is HabitKind.Choice -> {
                DayValue.Choices(dayEntries.map { (it.value as EntryValue.Choice).optionId })
            }
        }
    }
}

/** Whether anything that counts was logged, for streaks and "active day" tallies. */
fun isHit(value: DayValue?): Boolean =
    when (value) {
        null -> false
        DayValue.Done -> true
        is DayValue.Amount -> value.total > 0.0
        is DayValue.Choices -> value.picks.isNotEmpty()
    }
