package com.plattegruber.didit.widget

import com.plattegruber.didit.core.domain.model.Entry
import com.plattegruber.didit.core.domain.model.summarize
import com.plattegruber.didit.core.domain.repository.HabitRepository
import com.plattegruber.didit.ui.viz.HabitHistory
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Everything a widget needs to draw itself. */
sealed interface WidgetModel {
    data object Loading : WidgetModel

    /** Showing every habit, but there are none yet. */
    data object NoHabits : WidgetModel

    /** Showing one habit that has since been deleted. */
    data object HabitMissing : WidgetModel

    data class AllHabits(val histories: List<HabitHistory>, val today: LocalDate) : WidgetModel

    data class OneHabit(val history: HabitHistory, val today: LocalDate) : WidgetModel

    companion object {
        /** The most history any widget layout can show: six weeks of calendar. */
        const val MAX_DAYS = 42L

        fun observe(repository: HabitRepository, target: WidgetConfig.Target, today: LocalDate): Flow<WidgetModel> {
            val entries = repository.observeEntries(today.minusDays(MAX_DAYS - 1), today)
            return when (target) {
                WidgetConfig.Target.AllHabits -> {
                    combine(repository.observeHabits(), entries) { habits, logged ->
                        if (habits.isEmpty()) {
                            NoHabits
                        } else {
                            val byHabit = logged.groupBy(Entry::habitId)
                            val histories = habits.map { HabitHistory(it, it.kind.summarize(byHabit[it.id].orEmpty())) }
                            AllHabits(histories, today)
                        }
                    }
                }

                is WidgetConfig.Target.OneHabit -> {
                    combine(
                        repository.observeHabit(target.id),
                        entries,
                    ) { habit, logged ->
                        if (habit == null) {
                            HabitMissing
                        } else {
                            val own = logged.filter { it.habitId == habit.id }
                            OneHabit(HabitHistory(habit, habit.kind.summarize(own)), today)
                        }
                    }
                }
            }
        }
    }
}
