package com.plattegruber.didit.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plattegruber.didit.R
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.viz.DayRange
import com.plattegruber.didit.core.domain.viz.WindowSummary
import com.plattegruber.didit.core.domain.viz.windowSummary
import com.plattegruber.didit.ui.components.Dot
import com.plattegruber.didit.ui.components.MixBar
import com.plattegruber.didit.ui.text.DayText
import com.plattegruber.didit.ui.theme.DiditTheme
import com.plattegruber.didit.ui.viz.HabitHistory
import com.plattegruber.didit.ui.viz.HabitScenes
import com.plattegruber.didit.ui.viz.RowsCanvas
import com.plattegruber.didit.ui.viz.RowsScene
import com.plattegruber.didit.ui.viz.rememberLocale
import java.time.LocalDate

/** Widths of the design's three columns: name, days, summary. */
private val NameWidth = 72.dp
private val SummaryWidth = 52.dp
private val ColumnGap = 10.dp

/**
 * The design's "didit" card: one row per habit over the same days, a word mark and the date
 * range on top, weekday letters underneath. Tapping a row logs today; a long press opens history.
 */
@Composable
fun HabitRowsCard(
    histories: List<HabitHistory>,
    today: LocalDate,
    dayCount: Int,
    onRowClick: (HabitHistory) -> Unit,
    onRowLongClick: (HabitHistory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = rememberLocale()
    val scene = remember(histories, today, dayCount, locale) { HabitScenes.rows(histories, today, dayCount, locale) }
    val dates = remember(today, dayCount) { DayRange.lastDays(today, dayCount).map { it.date } }

    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            WordMark(Modifier.weight(1f))
            Text(
                DayText.range(dates.first(), dates.last(), locale),
                style = DiditTheme.type.monoMedium,
                color = DiditTheme.colors.subtle,
            )
        }
        Box {
            Row(horizontalArrangement = Arrangement.spacedBy(ColumnGap)) {
                Column(Modifier.width(NameWidth)) {
                    histories.forEach { NameCell(it) }
                }
                RowsCanvas(
                    scene = scene,
                    contentDescription = pluralStringResource(R.plurals.home_rows_description, dayCount, dayCount),
                    modifier = Modifier.weight(1f),
                )
                Column(Modifier.width(SummaryWidth)) {
                    histories.forEach { SummaryCell(it, dates) }
                }
            }
            // Whole-row touch targets laid over the three columns.
            Column {
                histories.forEach { history -> RowTarget(history, dates, onRowClick, onRowLongClick) }
            }
        }
    }
}

@Composable
fun WordMark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.wordmark), style = DiditTheme.type.wordmark)
        Spacer(Modifier.width(3.dp))
        Dot(DiditTheme.colors.ink, 5.dp, Modifier.padding(top = 5.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameCell(history: HabitHistory) {
    val kind = history.habit.kind
    Column(
        Modifier.height(RowsScene.ROW_HEIGHT.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Text(
            history.habit.name,
            style = DiditTheme.type.rowName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (kind is HabitKind.Choice) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), maxLines = 2) {
                kind.activeOptions.forEach { option ->
                    Text(
                        option.label.lowercase(),
                        style = DiditTheme.type.mono,
                        color = DiditTheme.colors.swatch(option.swatch),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCell(history: HabitHistory, dates: List<LocalDate>) {
    val colors = DiditTheme.colors
    val summary = remember(history, dates) { history.habit.windowSummary(history.days, dates) }
    val text = DayText.summary(LocalResources.current, summary, history.habit.kind.unit)
    Column(
        Modifier.height(RowsScene.ROW_HEIGHT.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Text(text.value, style = DiditTheme.type.total, maxLines = 1)
        if (summary is WindowSummary.Picks && summary.mix.isNotEmpty()) {
            MixBar(summary.mix.map { colors.swatch(it.swatch) to it.count }, Modifier.width(44.dp))
        }
        if (text.label.isNotBlank()) {
            Text(text.label, style = DiditTheme.type.mono, color = colors.subtle, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowTarget(
    history: HabitHistory,
    dates: List<LocalDate>,
    onClick: (HabitHistory) -> Unit,
    onLongClick: (HabitHistory) -> Unit,
) {
    val summary = remember(history, dates) { history.habit.windowSummary(history.days, dates) }
    val text = DayText.summary(LocalResources.current, summary, history.habit.kind.unit)
    val description = stringResource(R.string.home_row_description, history.habit.name, text.value, text.label)
    val historyLabel = stringResource(R.string.action_history)
    Box(
        Modifier
            .fillMaxWidth()
            .height(RowsScene.ROW_HEIGHT.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { onClick(history) },
                onLongClick = { onLongClick(history) },
                onLongClickLabel = historyLabel,
            ).clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            },
    )
}
