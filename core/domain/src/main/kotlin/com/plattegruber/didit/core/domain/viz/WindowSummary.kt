package com.plattegruber.didit.core.domain.viz

import com.plattegruber.didit.core.domain.model.DayValue
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.core.domain.stats.mix
import java.time.LocalDate

/** The one number that sums a habit up over the days on screen. */
sealed interface WindowSummary {
    /** Choices: how many picks, and how they split (in option order, empty options left out). */
    data class Picks(val total: Int, val mix: List<Slice>) : WindowSummary

    /** Amounts: everything added up. */
    data class Total(val total: Double) : WindowSummary

    /** Counts: the average per day, empty days included. */
    data class PerDay(val average: Double) : WindowSummary

    /** Yes/no: how many of the days were done. */
    data class DaysDone(val done: Int, val of: Int) : WindowSummary

    data class Slice(val swatch: Swatch, val count: Int)
}

fun Habit.windowSummary(days: Map<LocalDate, DayValue>, dates: List<LocalDate>): WindowSummary {
    val values = dates.mapNotNull(days::get)
    val totals = values.filterIsInstance<DayValue.Amount>().sumOf(DayValue.Amount::total)
    return when (val k = kind) {
        HabitKind.Done -> {
            WindowSummary.DaysDone(done = values.count { it == DayValue.Done }, of = dates.size)
        }

        is HabitKind.Amount -> {
            WindowSummary.Total(totals)
        }

        is HabitKind.Count -> {
            WindowSummary.PerDay(if (dates.isEmpty()) 0.0 else totals / dates.size)
        }

        is HabitKind.Choice -> {
            val shares = mix(k, values).filter { it.count > 0 }
            val inOptionOrder =
                k.options.mapNotNull { option ->
                    val share = shares.firstOrNull { it.option.id == option.id }
                    share?.let { WindowSummary.Slice(option.swatch, it.count) }
                }
            WindowSummary.Picks(total = shares.sumOf { it.count }, mix = inOptionOrder)
        }
    }
}
