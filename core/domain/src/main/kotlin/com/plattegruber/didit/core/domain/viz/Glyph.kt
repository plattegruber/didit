package com.plattegruber.didit.core.domain.viz

import com.plattegruber.didit.core.domain.model.DayValue
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.Swatch
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

/** How one day of one habit is drawn. Each kind has its own glyph, and each glyph its own empty state. */
sealed interface Glyph {
    /** A choice day: one dot per pick, stacked bottom-up in the order they were logged. */
    data class Dots(val swatches: List<Swatch>) : Glyph

    /** An amount day: a bar whose height is [fraction] of the view's scale, or a stub at 0. */
    data class Bar(val fraction: Float) : Glyph

    /** A count day: one pip per tally. */
    data class Pips(val count: Int) : Glyph

    /** A yes/no day: a filled dot, or an empty ring. */
    data class Check(val done: Boolean) : Glyph
}

/**
 * Glyphs for [dates], in order. Amount bars share one scale across [dates] so they compare with
 * each other; the scale ignores the top tenth of days, so one huge day doesn't flatten the rest.
 */
fun Habit.glyphs(days: Map<LocalDate, DayValue>, dates: List<LocalDate>): List<Glyph> {
    val reference = if (kind is HabitKind.Amount) amountReference(dates.mapNotNull(days::get)) else 1.0
    return dates.map { glyph(days[it], reference) }
}

private fun Habit.glyph(value: DayValue?, amountReference: Double): Glyph = when (val k = kind) {
    HabitKind.Done -> {
        Glyph.Check(done = value == DayValue.Done)
    }

    is HabitKind.Amount -> {
        val total = (value as? DayValue.Amount)?.total ?: 0.0
        Glyph.Bar(if (total > 0.0) (total / amountReference).toFloat().coerceAtMost(1f) else 0f)
    }

    is HabitKind.Count -> {
        Glyph.Pips(((value as? DayValue.Amount)?.total ?: 0.0).roundToInt().coerceAtLeast(0))
    }

    is HabitKind.Choice -> {
        Glyph.Dots(
            (value as? DayValue.Choices)?.picks.orEmpty().mapNotNull { k.option(it)?.swatch },
        )
    }
}

/** Fraction of days allowed to exceed the automatic scale and draw at full height. */
private const val OUTLIER_FRACTION = 0.1

private fun amountReference(visible: List<DayValue>): Double {
    val totals =
        visible
            .filterIsInstance<DayValue.Amount>()
            .map { it.total }
            .filter { it > 0.0 }
            .sorted()
    if (totals.isEmpty()) return 1.0
    val rank = ceil(totals.size * (1 - OUTLIER_FRACTION)).toInt().coerceIn(1, totals.size)
    return totals[rank - 1]
}
