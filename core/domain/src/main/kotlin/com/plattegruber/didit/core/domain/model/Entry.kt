package com.plattegruber.didit.core.domain.model

import java.time.Instant
import java.time.LocalDate

/** A single thing logged against a habit on a given day. A day may hold several entries. */
data class Entry(
    val id: EntryId,
    val habitId: HabitId,
    val date: LocalDate,
    val value: EntryValue,
    val loggedAt: Instant,
)

sealed interface EntryValue {
    data object Done : EntryValue

    /** A day's total for an amount or count habit. */
    data class Amount(val amount: Double) : EntryValue

    data class Choice(val optionId: OptionId) : EntryValue
}

/** Whether this value is the kind of thing [kind] records. */
fun EntryValue.isCompatibleWith(kind: HabitKind): Boolean = when (this) {
    EntryValue.Done -> kind is HabitKind.Done
    is EntryValue.Amount -> kind is HabitKind.Amount || kind is HabitKind.Count
    is EntryValue.Choice -> kind is HabitKind.Choice && kind.option(optionId) != null
}
