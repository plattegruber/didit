package com.plattegruber.didit.ui.log

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.plattegruber.didit.R
import com.plattegruber.didit.core.domain.draft.HabitLimits
import com.plattegruber.didit.core.domain.format.AmountFormat
import com.plattegruber.didit.core.domain.model.ChoiceOption
import com.plattegruber.didit.core.domain.model.DayValue
import com.plattegruber.didit.core.domain.model.Entry
import com.plattegruber.didit.core.domain.model.EntryId
import com.plattegruber.didit.core.domain.model.EntryValue
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.OptionId
import com.plattegruber.didit.core.domain.model.summarize
import com.plattegruber.didit.ui.components.DiditTextField
import com.plattegruber.didit.ui.components.Dot
import com.plattegruber.didit.ui.components.OutlineButton
import com.plattegruber.didit.ui.components.Overline
import com.plattegruber.didit.ui.components.RoundIconButton
import com.plattegruber.didit.ui.text.DayText
import com.plattegruber.didit.ui.theme.DiditTheme
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** What a log sheet can ask of whoever owns the data. */
interface LogActions {
    fun toggleDone(habitId: HabitId, date: LocalDate)

    fun setTotal(habitId: HabitId, date: LocalDate, total: Double)

    fun addPick(habitId: HabitId, date: LocalDate, optionId: OptionId)

    fun removeEntry(id: EntryId)
}

/** One habit on one day, with the entries already logged for that day. */
data class LogTarget(val habit: Habit, val date: LocalDate, val entries: List<Entry>)

/** The design's amount steps. */
private val AmountSteps = listOf(-5.0, 5.0, 10.0, 25.0)

/** Count slots shown before the row starts growing, and the most it grows to. */
private const val COUNT_SLOTS = 5
private const val MAX_COUNT_SLOTS = 8
private const val MAX_COUNT = 99

/** The design's ease for things arriving: quick out of the gate, soft landing. */
private val Arrive = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private const val ARRIVE_MILLIS = 220
private const val CHIP_START_SCALE = 0.85f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheet(
    target: LogTarget,
    today: LocalDate,
    actions: LogActions,
    onDismiss: () -> Unit,
    onOpenHistory: (() -> Unit)? = null,
) {
    val colors = DiditTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = colors.surface,
        contentColor = colors.ink,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.lineStrong, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        LogContent(
            target = target,
            today = today,
            actions = actions,
            onClose = onDismiss,
            onOpenHistory = onOpenHistory,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
fun LogContent(
    target: LogTarget,
    today: LocalDate,
    actions: LogActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHistory: (() -> Unit)? = null,
) {
    val (habit, date, entries) = target
    val isToday = date == today
    val day = remember(habit, entries) { habit.kind.summarize(entries)[date] }
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Header(habit, date, today, onClose, onOpenHistory)
        when (val kind = habit.kind) {
            is HabitKind.Choice -> {
                ChoiceLog(kind, entries, isToday, onPick = { actions.addPick(habit.id, date, it) }) {
                    actions.removeEntry(it)
                }
            }

            is HabitKind.Amount -> {
                AmountLog(kind.unit, (day as? DayValue.Amount)?.total ?: 0.0, isToday) {
                    actions.setTotal(habit.id, date, it)
                }
            }

            is HabitKind.Count -> {
                CountLog(kind.unit, (day as? DayValue.Amount)?.total?.roundToInt() ?: 0, isToday) {
                    actions.setTotal(habit.id, date, it.toDouble())
                }
            }

            HabitKind.Done -> {
                DoneLog(done = day == DayValue.Done, isToday = isToday) {
                    actions.toggleDone(habit.id, date)
                }
            }
        }
    }
}

@Composable
private fun Header(habit: Habit, date: LocalDate, today: LocalDate, onClose: () -> Unit, onOpenHistory: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(habit.name, style = DiditTheme.type.sheetTitle)
            Text(
                DayText.sheetDate(LocalResources.current, date, today),
                style = DiditTheme.type.monoMedium,
                color = DiditTheme.colors.muted,
            )
        }
        if (onOpenHistory != null) {
            RoundIconButton(R.drawable.ic_calendar, stringResource(R.string.action_history), onOpenHistory)
            Spacer(Modifier.width(8.dp))
        }
        RoundIconButton(
            R.drawable.ic_close,
            stringResource(R.string.action_close),
            onClose,
            filled = true,
            iconSize = 18.dp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceLog(
    kind: HabitKind.Choice,
    entries: List<Entry>,
    isToday: Boolean,
    onPick: (OptionId) -> Unit,
    onRemove: (EntryId) -> Unit,
) {
    val colors = DiditTheme.colors
    val haptics = LocalHapticFeedback.current
    val picks =
        entries
            .sortedBy(Entry::loggedAt)
            .mapNotNull { entry ->
                (entry.value as? EntryValue.Choice)?.let { kind.option(it.optionId) }?.let { entry to it }
            }
    val full = picks.size >= HabitLimits.MAX_PICKS_PER_DAY

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Overline(
                    stringResource(if (isToday) R.string.log_so_far_today else R.string.log_so_far),
                    Modifier.weight(1f),
                )
                Text(
                    if (kind.unit.isBlank()) {
                        pluralStringResource(R.plurals.log_picks_count, picks.size, picks.size)
                    } else {
                        stringResource(R.string.log_picks_count_unit, picks.size, kind.unit)
                    },
                    style = DiditTheme.type.monoMedium,
                    color = colors.muted,
                )
            }
            FlowRow(
                Modifier.fillMaxWidth().heightIn(min = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (picks.isEmpty()) {
                    Box(Modifier.height(44.dp), contentAlignment = Alignment.CenterStart) {
                        Text(
                            stringResource(R.string.log_nothing_yet),
                            style = DiditTheme.type.chip,
                            color = colors.muted,
                        )
                    }
                }
                picks.forEachIndexed { index, (entry, option) ->
                    key(entry.id.value) {
                        PickChip(option, index + 1) {
                            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            onRemove(entry.id)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            kind.activeOptions.forEach { option ->
                ChoiceButton(
                    option = option,
                    enabled = !full,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onPick(option.id)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            stringResource(if (full) R.string.log_choice_full else R.string.log_choice_hint),
            style = DiditTheme.type.hint,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PickChip(option: ChoiceOption, number: Int, onRemove: () -> Unit) {
    val colors = DiditTheme.colors
    val scale = remember { Animatable(CHIP_START_SCALE) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(ARRIVE_MILLIS, easing = Arrive)) }
    val description = stringResource(R.string.log_remove_pick, option.label, number)
    Surface(
        onClick = onRemove,
        modifier =
            Modifier
                .height(44.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    alpha = (scale.value - CHIP_START_SCALE) / (1f - CHIP_START_SCALE)
                }.semantics { contentDescription = description },
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        contentColor = colors.ink,
        border = BorderStroke(1.5.dp, colors.lineStrong),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(colors.swatch(option.swatch), 9.dp)
            Spacer(Modifier.width(8.dp))
            Text(option.label, style = DiditTheme.type.chip)
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_close), null, Modifier.size(14.dp), tint = colors.subtle)
        }
    }
}

@Composable
private fun ChoiceButton(option: ChoiceOption, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DiditTheme.colors
    val description = stringResource(R.string.log_add_pick, option.label)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(72.dp).semantics { contentDescription = description },
        shape = RoundedCornerShape(18.dp),
        color = colors.raised,
        contentColor = if (enabled) colors.ink else colors.muted,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Dot(colors.swatch(option.swatch), 12.dp)
            Spacer(Modifier.height(8.dp))
            Text(option.label, style = DiditTheme.type.chip, maxLines = 1)
        }
    }
}

@Composable
private fun AmountLog(unit: String, total: Double, isToday: Boolean, onSet: (Double) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var editing by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (editing) {
            ExactTotal(initial = total) { value ->
                editing = false
                if (value != null) onSet(value)
            }
        } else {
            Hero(
                value = total,
                label = unitLabel(unit, isToday),
                onClick = { editing = true },
                clickLabel = stringResource(R.string.log_edit_total),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmountSteps.forEach { step ->
                val text = AmountFormat.format(abs(step))
                val description =
                    stringResource(
                        if (step <
                            0
                        ) {
                            R.string.log_step_remove
                        } else {
                            R.string.log_step_add
                        },
                        text,
                    )
                OutlineButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onSet((total + step).coerceAtLeast(0.0))
                    },
                    modifier = Modifier.weight(1f).height(52.dp).semantics { contentDescription = description },
                ) {
                    Text(if (step < 0) "−$text" else "+$text", style = DiditTheme.type.step)
                }
            }
        }
    }
}

@Composable
private fun ExactTotal(initial: Double, onDone: (Double?) -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (initial > 0) AmountFormat.format(initial) else "") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val value = AmountFormat.parse(text)?.takeIf { it >= 0.0 }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DiditTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = "0",
            label = stringResource(R.string.log_edit_total),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone(value) }),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        OutlineButton(onClick = { onDone(value) }, modifier = Modifier.height(52.dp).width(72.dp)) {
            Text(stringResource(R.string.log_set), style = DiditTheme.type.chip)
        }
    }
}

@Composable
private fun CountLog(unit: String, count: Int, isToday: Boolean, onSet: (Int) -> Unit) {
    val colors = DiditTheme.colors
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Hero(value = count.toDouble(), label = unitLabel(unit, isToday))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundStepButton(
                R.drawable.ic_minus,
                stringResource(R.string.log_one_less),
                filled = false,
                enabled =
                    count > 0,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onSet(count - 1)
            }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                repeat(count.coerceIn(COUNT_SLOTS, MAX_COUNT_SLOTS)) { slot ->
                    val filled = slot < count
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(if (filled) colors.ink else Color.Transparent, CircleShape)
                            .border(1.5.dp, if (filled) colors.ink else colors.lineStrong, CircleShape),
                    )
                }
            }
            RoundStepButton(
                R.drawable.ic_plus,
                stringResource(R.string.log_one_more),
                filled = true,
                enabled =
                    count < MAX_COUNT,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onSet(count + 1)
            }
        }
    }
}

@Composable
private fun RoundStepButton(icon: Int, description: String, filled: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = DiditTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp).semantics { contentDescription = description },
        shape = CircleShape,
        color = if (filled) colors.ink else Color.Transparent,
        contentColor = if (filled) colors.onInk else colors.ink,
        border = if (filled) null else BorderStroke(1.5.dp, colors.lineStrong),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), null, Modifier.size(20.dp)) }
    }
}

@Composable
private fun DoneLog(done: Boolean, isToday: Boolean, onToggle: () -> Unit) {
    val colors = DiditTheme.colors
    val haptics = LocalHapticFeedback.current
    val state = stringResource(if (done) R.string.kind_done else R.string.log_not_yet_today)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            onClick = {
                haptics.performHapticFeedback(if (done) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onToggle()
            },
            modifier = Modifier.fillMaxWidth().height(64.dp).semantics { stateDescription = state },
            shape = RoundedCornerShape(18.dp),
            color = if (done) colors.ink else Color.Transparent,
            contentColor = if (done) colors.onInk else colors.ink,
            border = BorderStroke(1.5.dp, if (done) colors.ink else colors.line),
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_check), null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.log_did_it), style = DiditTheme.type.button)
            }
        }
        Text(
            stringResource(
                when {
                    done && isToday -> R.string.log_done_today
                    done -> R.string.log_done_on_day
                    isToday -> R.string.log_not_yet_today
                    else -> R.string.log_not_on_day
                },
            ),
            style = DiditTheme.type.hint,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The big number, which rolls up or down as it changes. */
@Composable
private fun Hero(value: Double, label: String, onClick: (() -> Unit)? = null, clickLabel: String? = null) {
    val content = @Composable {
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    val up = targetState > initialState
                    val enter = slideInVertically(tween(ARRIVE_MILLIS, easing = Arrive)) { if (up) it / 2 else -it / 2 }
                    val exit = slideOutVertically(tween(ARRIVE_MILLIS, easing = Arrive)) { if (up) -it / 2 else it / 2 }
                    (enter + fadeIn(tween(ARRIVE_MILLIS))) togetherWith (exit + fadeOut(tween(ARRIVE_MILLIS)))
                },
                label = "total",
            ) { shown ->
                Text(AmountFormat.format(shown), style = DiditTheme.type.hero)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                style = DiditTheme.type.heroLabel,
                color = DiditTheme.colors.muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
    if (onClick == null) {
        content()
    } else {
        Surface(
            onClick = onClick,
            color = Color.Transparent,
            contentColor = DiditTheme.colors.ink,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.semantics { if (clickLabel != null) contentDescription = clickLabel },
        ) { content() }
    }
}

@Composable
private fun unitLabel(unit: String, isToday: Boolean): String =
    stringResource(if (isToday) R.string.log_unit_today else R.string.log_unit_on_day, unit).trim()
