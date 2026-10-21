package com.plattegruber.didit.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plattegruber.didit.R
import com.plattegruber.didit.core.domain.format.AmountFormat
import com.plattegruber.didit.core.domain.stats.HabitStats
import com.plattegruber.didit.ui.components.Dot
import com.plattegruber.didit.ui.components.MixBar
import com.plattegruber.didit.ui.components.Overline
import com.plattegruber.didit.ui.components.RoundIconButton
import com.plattegruber.didit.ui.log.LogActions
import com.plattegruber.didit.ui.log.LogSheet
import com.plattegruber.didit.ui.log.LogTarget
import com.plattegruber.didit.ui.text.DayText
import com.plattegruber.didit.ui.theme.DiditShapes
import com.plattegruber.didit.ui.theme.DiditTheme
import com.plattegruber.didit.ui.viz.CalendarCanvas
import com.plattegruber.didit.ui.viz.GlyphMetrics
import com.plattegruber.didit.ui.viz.HabitScenes
import com.plattegruber.didit.ui.viz.rememberLocale
import com.plattegruber.didit.ui.viz.rememberWeekStart
import java.time.LocalDate
import kotlin.math.roundToInt

private const val PERCENT = 100

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onAddWidget: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshDay()
        onPauseOrDispose { }
    }
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(state) { if (state == HistoryState.Missing) currentOnBack() }
    HistoryContent(
        state = state,
        actions = viewModel.logActions,
        onBack = onBack,
        onEdit = onEdit,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onAddWidget = onAddWidget,
        modifier = modifier,
    )
}

@Composable
fun HistoryContent(
    state: HistoryState,
    actions: LogActions,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddWidget: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = DiditTheme.colors
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    Surface(modifier.fillMaxSize(), color = colors.background, contentColor = colors.ink) {
        val ready = state as? HistoryState.Ready ?: return@Surface
        val habit = ready.history.habit
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(Modifier.offset(x = (-12).dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(R.drawable.ic_back, stringResource(R.string.action_back), onBack, iconSize = 22.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    habit.name,
                    style = DiditTheme.type.screenTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                RoundIconButton(
                    icon = R.drawable.ic_edit,
                    contentDescription = stringResource(R.string.action_edit),
                    onClick = onEdit,
                    modifier = Modifier.offset(x = 24.dp),
                )
            }

            StreakStats(ready.stats)
            DetailStats(ready, habit.kind.unit)
            Month(ready, onPreviousMonth, onNextMonth) { selected = it.toEpochDay() }

            if (onAddWidget != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoundIconButton(
                        icon = R.drawable.ic_widget,
                        contentDescription = stringResource(R.string.history_add_widget),
                        onClick = onAddWidget,
                        modifier = Modifier.offset(x = (-12).dp),
                    )
                    Text(
                        stringResource(R.string.history_add_widget),
                        style = DiditTheme.type.chip,
                        color = colors.inkSoft,
                    )
                }
            }
        }

        val day = selected?.let(LocalDate::ofEpochDay)
        if (day != null) {
            LogSheet(
                target = LogTarget(habit, day, ready.entriesOn(day)),
                today = ready.today,
                actions = actions,
                onDismiss = { selected = null },
            )
        }
    }
}

@Composable
private fun StreakStats(stats: HabitStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat(stringResource(R.string.history_streak), days(stats.currentStreak), Modifier.weight(1f))
        Stat(stringResource(R.string.history_longest), days(stats.longestStreak), Modifier.weight(1f))
        Stat(stringResource(R.string.history_active), days(stats.activeDays), Modifier.weight(1f))
    }
}

@Composable
private fun days(count: Int): String = pluralStringResource(R.plurals.history_days, count, count)

@Composable
private fun DetailStats(state: HistoryState.Ready, unit: String) {
    when (val detail = state.stats.detail) {
        HabitStats.Detail.None -> {}

        is HabitStats.Detail.Totals -> {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(
                    stringResource(R.string.history_total),
                    AmountFormat.withUnit(detail.total, unit),
                    Modifier.weight(1f),
                )
                Stat(
                    stringResource(R.string.history_average),
                    AmountFormat.format(detail.dailyAverage),
                    Modifier.weight(1f),
                )
                Stat(
                    stringResource(R.string.history_best),
                    detail.best?.let { AmountFormat.format(it.total) } ?: "–",
                    Modifier.weight(1f),
                    caption = detail.best?.let { DayText.shortDate(it.date) },
                )
            }
        }

        is HabitStats.Detail.Mix -> {
            Mix(detail)
        }
    }
}

@Composable
private fun Mix(mix: HabitStats.Detail.Mix) {
    val colors = DiditTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Overline(stringResource(R.string.history_mix))
        MixBar(mix.shares.map { colors.swatch(it.option.swatch) to it.count }, Modifier.fillMaxWidth())
        mix.shares.forEach { share ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(colors.swatch(share.option.swatch), 9.dp)
                Spacer(Modifier.width(10.dp))
                Text(share.option.label, style = DiditTheme.type.chip, modifier = Modifier.weight(1f))
                Text(
                    "${share.count} · ${(share.fraction * PERCENT).roundToInt()}%",
                    style = DiditTheme.type.monoMedium,
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, caption: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Overline(label)
        Text(value, style = DiditTheme.type.total, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (caption != null) Text(caption, style = DiditTheme.type.mono, color = DiditTheme.colors.subtle)
    }
}

@Composable
private fun Month(
    state: HistoryState.Ready,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    val locale = rememberLocale()
    val weekStart = rememberWeekStart()
    val scene =
        remember(state.history, state.month, state.today, weekStart, locale) {
            HabitScenes.month(state.history, state.month, state.today, weekStart, locale)
        }
    val title = DayText.month(state.month.atDay(1), locale)
    Surface(color = DiditTheme.colors.surface, shape = DiditShapes.card) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = DiditTheme.type.option, modifier = Modifier.weight(1f).padding(start = 4.dp))
                RoundIconButton(
                    R.drawable.ic_chevron_left,
                    stringResource(R.string.history_previous_month),
                    onPrevious,
                    enabled = state.canGoBack,
                )
                RoundIconButton(
                    R.drawable.ic_chevron_right,
                    stringResource(R.string.history_next_month),
                    onNext,
                    enabled = state.canGoForward,
                )
            }
            CalendarCanvas(
                scene = scene,
                contentDescription =
                    stringResource(
                        R.string.history_month_description,
                        state.history.habit.name,
                        title,
                    ),
                metrics = GlyphMetrics.Month,
                onDayClick = onDayClick,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
