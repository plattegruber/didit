package com.plattegruber.didit.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plattegruber.didit.core.domain.model.Entry
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.core.domain.model.summarize
import com.plattegruber.didit.core.domain.repository.HabitRepository
import com.plattegruber.didit.core.domain.stats.HabitStats
import com.plattegruber.didit.core.domain.stats.stats
import com.plattegruber.didit.core.domain.time.AppClock
import com.plattegruber.didit.core.domain.time.today
import com.plattegruber.didit.ui.log.LogActions
import com.plattegruber.didit.ui.log.RepositoryLogActions
import com.plattegruber.didit.ui.viz.HabitHistory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

sealed interface HistoryState {
    data object Loading : HistoryState

    /** The habit no longer exists, e.g. it was deleted from the editor. */
    data object Missing : HistoryState

    data class Ready(
        val history: HabitHistory,
        val entries: List<Entry>,
        val stats: HabitStats,
        val today: LocalDate,
        val month: YearMonth,
        val canGoBack: Boolean,
        val canGoForward: Boolean,
    ) : HistoryState {
        fun entriesOn(date: LocalDate): List<Entry> = entries.filter { it.date == date }
    }
}

@HiltViewModel(assistedFactory = HistoryViewModel.Factory::class)
class HistoryViewModel
    @AssistedInject
    constructor(
        @Assisted rawHabitId: Long,
        private val repository: HabitRepository,
        private val clock: AppClock,
    ) : ViewModel() {
        val logActions: LogActions = RepositoryLogActions(repository, viewModelScope)

        private val habitId = HabitId(rawHabitId)
        private val today = MutableStateFlow(clock.today())
        private val month = MutableStateFlow(YearMonth.from(clock.today()))

        val state: StateFlow<HistoryState> =
            combine(
                repository.observeHabit(habitId),
                repository.observeEntries(habitId),
                today,
                month,
            ) { habit, entries, day, shown ->
                if (habit == null) {
                    HistoryState.Missing
                } else {
                    val days = habit.kind.summarize(entries)
                    // History can be browsed back to whichever came first: the habit, or anything logged.
                    val created = habit.createdAt.atZone(clock.zone()).toLocalDate()
                    val earliest = YearMonth.from(minOf(created, entries.minOfOrNull(Entry::date) ?: created))
                    HistoryState.Ready(
                        history = HabitHistory(habit, days),
                        entries = entries,
                        stats = habit.stats(days, day),
                        today = day,
                        month = shown,
                        canGoBack = shown > earliest,
                        canGoForward = shown < YearMonth.from(day),
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HistoryState.Loading)

        fun refreshDay() {
            today.value = clock.today()
        }

        fun showPreviousMonth() {
            if ((state.value as? HistoryState.Ready)?.canGoBack == true) month.value = month.value.minusMonths(1)
        }

        fun showNextMonth() {
            if ((state.value as? HistoryState.Ready)?.canGoForward == true) month.value = month.value.plusMonths(1)
        }

        @AssistedFactory
        interface Factory {
            /** Takes the raw id: Dagger can't generate factories for Kotlin value classes. */
            fun create(habitId: Long): HistoryViewModel
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
