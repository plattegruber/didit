package com.plattegruber.didit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plattegruber.didit.core.domain.model.Entry
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.summarize
import com.plattegruber.didit.core.domain.repository.HabitRepository
import com.plattegruber.didit.core.domain.time.AppClock
import com.plattegruber.didit.core.domain.time.today
import com.plattegruber.didit.ui.log.LogActions
import com.plattegruber.didit.ui.log.RepositoryLogActions
import com.plattegruber.didit.ui.viz.HabitHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

sealed interface HomeState {
    data object Loading : HomeState

    data class Ready(val today: LocalDate, val rows: List<HomeRow>) : HomeState
}

/** A habit with the days on screen, plus its raw entries so a log sheet can edit them. */
data class HomeRow(val history: HabitHistory, val entries: List<Entry>) {
    val habit: Habit get() = history.habit

    fun entriesOn(date: LocalDate): List<Entry> = entries.filter { it.date == date }
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(private val repository: HabitRepository, private val clock: AppClock) :
    ViewModel() {
        val logActions: LogActions = RepositoryLogActions(repository, viewModelScope)

        private val today = MutableStateFlow(clock.today())

        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<HomeState> =
            today
                .flatMapLatest { day ->
                    combine(
                        repository.observeHabits(),
                        repository.observeEntries(day.minusDays(DAYS - 1L), day),
                    ) { habits, entries ->
                        val byHabit = entries.groupBy(Entry::habitId)
                        HomeState.Ready(
                            today = day,
                            rows =
                                habits.map { habit ->
                                    val own = byHabit[habit.id].orEmpty()
                                    HomeRow(HabitHistory(habit, habit.kind.summarize(own)), own)
                                },
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeState.Loading)

        /** Call when the screen comes back into view, so a screen left open overnight moves on to the new day. */
        fun refreshDay() {
            today.value = clock.today()
        }

        companion object {
            /** The design's two weeks. */
            const val DAYS = 14
            private const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
