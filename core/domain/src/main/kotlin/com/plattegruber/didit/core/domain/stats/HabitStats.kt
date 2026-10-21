package com.plattegruber.didit.core.domain.stats

import com.plattegruber.didit.core.domain.model.ChoiceOption
import com.plattegruber.didit.core.domain.model.DayValue
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.isHit
import java.time.LocalDate

/** The longer view of a habit, for its detail screen. */
data class HabitStats(
    /** Consecutive logged days ending today, or yesterday if today hasn't been logged yet. */
    val currentStreak: Int,
    val longestStreak: Int,
    /** Logged days within the trailing [WINDOW_DAYS] days, today included. */
    val activeDays: Int,
    val detail: Detail,
) {
    sealed interface Detail {
        data object None : Detail

        data class Totals(
            val total: Double,
            /** Average across the whole window, empty days included. */
            val dailyAverage: Double,
            val best: Best?,
        ) : Detail

        /** Share of picks per option over the window, largest first. */
        data class Mix(val shares: List<Share>) : Detail
    }

    data class Best(val date: LocalDate, val total: Double)

    data class Share(val option: ChoiceOption, val count: Int, val fraction: Double)

    companion object {
        const val WINDOW_DAYS = 30
    }
}

fun Habit.stats(days: Map<LocalDate, DayValue>, today: LocalDate): HabitStats {
    val windowStart = today.minusDays(HabitStats.WINDOW_DAYS - 1L)
    val window = days.filterKeys { it in windowStart..today }
    return HabitStats(
        currentStreak = currentStreak(days, today),
        longestStreak = longestStreak(days),
        activeDays = window.values.count(::isHit),
        detail =
            when (val k = kind) {
                HabitKind.Done -> HabitStats.Detail.None
                is HabitKind.Amount, is HabitKind.Count -> totals(window)
                is HabitKind.Choice -> HabitStats.Detail.Mix(mix(k, window.values))
            },
    )
}

/** Each option's share of the picks in [days], largest first. Archived options drop out once unused. */
fun mix(kind: HabitKind.Choice, days: Collection<DayValue>): List<HabitStats.Share> {
    val counts =
        days
            .filterIsInstance<DayValue.Choices>()
            .flatMap(DayValue.Choices::picks)
            .groupingBy { it }
            .eachCount()
    val total = counts.values.sum()
    return kind.options
        .mapNotNull { option ->
            val count = counts[option.id] ?: 0
            if (count == 0 && option.archived) {
                null
            } else {
                HabitStats.Share(option, count, if (total == 0) 0.0 else count.toDouble() / total)
            }
        }.sortedByDescending(HabitStats.Share::count)
}

private fun currentStreak(days: Map<LocalDate, DayValue>, today: LocalDate): Int {
    // An unlogged today doesn't break the streak yet; the day isn't over.
    var day = if (isHit(days[today])) today else today.minusDays(1)
    var streak = 0
    while (isHit(days[day])) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

private fun longestStreak(days: Map<LocalDate, DayValue>): Int {
    var longest = 0
    var run = 0
    var previous: LocalDate? = null
    for (day in days.filterValues(::isHit).keys.sorted()) {
        run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
        longest = maxOf(longest, run)
        previous = day
    }
    return longest
}

private fun totals(window: Map<LocalDate, DayValue>): HabitStats.Detail.Totals {
    val totals = window.mapNotNull { (date, value) -> (value as? DayValue.Amount)?.let { date to it.total } }
    val total = totals.sumOf { it.second }
    val best =
        totals
            .filter { it.second > 0.0 }
            .maxWithOrNull(compareBy<Pair<LocalDate, Double>> { it.second }.thenBy { it.first })
    return HabitStats.Detail.Totals(
        total = total,
        dailyAverage = total / HabitStats.WINDOW_DAYS,
        best = best?.let { HabitStats.Best(it.first, it.second) },
    )
}
