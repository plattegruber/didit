package com.plattegruber.didit.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plattegruber.didit.R
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.ui.components.DashedButton
import com.plattegruber.didit.ui.log.LogActions
import com.plattegruber.didit.ui.log.LogSheet
import com.plattegruber.didit.ui.log.LogTarget
import com.plattegruber.didit.ui.theme.DiditShapes
import com.plattegruber.didit.ui.theme.DiditTheme

@Composable
fun HomeScreen(
    onOpenHistory: (HabitId) -> Unit,
    onCreateHabit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshDay()
        onPauseOrDispose { }
    }
    HomeContent(state, viewModel.logActions, onOpenHistory, onCreateHabit, modifier)
}

@Composable
fun HomeContent(
    state: HomeState,
    actions: LogActions,
    onOpenHistory: (HabitId) -> Unit,
    onCreateHabit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var logging by rememberSaveable { mutableStateOf<Long?>(null) }
    Box(
        modifier
            .fillMaxSize()
            .background(DiditTheme.colors.background),
    ) {
        when (state) {
            HomeState.Loading -> {}

            is HomeState.Ready -> {
                if (state.rows.isEmpty()) {
                    EmptyHome(onCreateHabit)
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(start = 12.dp, end = 12.dp, top = 32.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        HabitRowsCard(
                            histories = state.rows.map { it.history },
                            today = state.today,
                            dayCount = HomeViewModel.DAYS,
                            onRowClick = { logging = it.habit.id.value },
                            onRowLongClick = { onOpenHistory(it.habit.id) },
                            modifier = Modifier.background(DiditTheme.colors.surface, DiditShapes.card),
                        )
                        DashedButton(
                            stringResource(R.string.home_new_habit),
                            onCreateHabit,
                            Modifier.padding(horizontal = 8.dp),
                        )
                    }

                    val row = state.rows.firstOrNull { it.habit.id.value == logging }
                    if (row != null) {
                        LogSheet(
                            target = LogTarget(row.habit, state.today, row.entriesOn(state.today)),
                            today = state.today,
                            actions = actions,
                            onDismiss = { logging = null },
                            onOpenHistory = {
                                logging = null
                                onOpenHistory(row.habit.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHome(onCreateHabit: () -> Unit) {
    val colors = DiditTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        WordMark()
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.home_empty_title), style = DiditTheme.type.sheetTitle)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.home_empty_body), style = DiditTheme.type.body, color = colors.muted)
        Spacer(Modifier.height(32.dp))
        DashedButton(stringResource(R.string.home_new_habit), onCreateHabit)
    }
}
