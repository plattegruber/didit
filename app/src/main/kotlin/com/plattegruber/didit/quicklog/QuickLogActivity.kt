package com.plattegruber.didit.quicklog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.plattegruber.didit.MainActivity
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.core.domain.repository.HabitRepository
import com.plattegruber.didit.core.domain.time.AppClock
import com.plattegruber.didit.core.domain.time.today
import com.plattegruber.didit.ui.log.LogActions
import com.plattegruber.didit.ui.log.LogSheet
import com.plattegruber.didit.ui.log.LogTarget
import com.plattegruber.didit.ui.log.RepositoryLogActions
import com.plattegruber.didit.ui.theme.DiditTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface QuickLogState {
    data object Loading : QuickLogState

    data object Missing : QuickLogState

    data class Ready(val target: LogTarget) : QuickLogState
}

@HiltViewModel
class QuickLogViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        repository: HabitRepository,
        clock: AppClock,
    ) : ViewModel() {
        val habitId = HabitId(savedState.get<Long>(QuickLogActivity.EXTRA_HABIT_ID) ?: -1L)

        /** Fixed when the sheet opens: a sheet left open across midnight keeps logging the day it showed. */
        private val today = clock.today()

        val logActions: LogActions = RepositoryLogActions(repository, viewModelScope)

        val state: StateFlow<QuickLogState> =
            combine(
                repository.observeHabit(habitId),
                repository.observeEntries(today, today),
            ) { habit, entries ->
                if (habit == null) {
                    QuickLogState.Missing
                } else {
                    QuickLogState.Ready(LogTarget(habit, today, entries.filter { it.habitId == habitId }))
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), QuickLogState.Loading)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** The log sheet on its own, over whatever is behind it: what a tap on the widget opens. */
@AndroidEntryPoint
class QuickLogActivity : ComponentActivity() {
    private val viewModel: QuickLogViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DiditTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                when (val current = state) {
                    QuickLogState.Loading -> {}

                    QuickLogState.Missing -> {
                        LaunchedEffect(Unit) { finish() }
                    }

                    is QuickLogState.Ready -> {
                        LogSheet(
                            target = current.target,
                            today = current.target.date,
                            actions = viewModel.logActions,
                            onDismiss = ::finish,
                            onOpenHistory = {
                                startActivity(MainActivity.openHabit(this, viewModel.habitId))
                                finish()
                            },
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_HABIT_ID = "com.plattegruber.didit.extra.QUICK_LOG_HABIT_ID"

        fun intent(context: Context, habitId: HabitId): Intent =
            Intent(context, QuickLogActivity::class.java)
                .putExtra(EXTRA_HABIT_ID, habitId.value)
                // Always a fresh sheet for the habit tapped, never one left over from an earlier tap.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
