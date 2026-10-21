package com.plattegruber.didit.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.plattegruber.didit.R
import com.plattegruber.didit.core.domain.draft.DraftError
import com.plattegruber.didit.core.domain.draft.HabitLimits
import com.plattegruber.didit.ui.components.DashedButton
import com.plattegruber.didit.ui.components.DiditTextField
import com.plattegruber.didit.ui.components.Dot
import com.plattegruber.didit.ui.components.Overline
import com.plattegruber.didit.ui.components.PrimaryButton
import com.plattegruber.didit.ui.components.RadioMark
import com.plattegruber.didit.ui.components.RoundIconButton
import com.plattegruber.didit.ui.components.SelectableCard
import com.plattegruber.didit.ui.text.DayText
import com.plattegruber.didit.ui.theme.DiditTheme

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onClose: () -> Unit,
    onCloseAfterDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = viewModel.state
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnDeleted by rememberUpdatedState(onCloseAfterDelete)
    LaunchedEffect(state.finished, state.deleted) {
        when {
            state.deleted -> currentOnDeleted()
            state.finished -> currentOnClose()
        }
    }
    val leave = { if (viewModel.hasChanges) confirmDiscard = true else onClose() }
    BackHandler(enabled = !state.finished, onBack = leave)

    EditorContent(
        state = state,
        errors = if (state.showErrors) viewModel.errors else emptySet(),
        canAddOption = viewModel.canAddOption,
        onBack = leave,
        onNameChange = viewModel::setName,
        onKindChange = viewModel::setKind,
        onUnitChange = viewModel::setUnit,
        onOptionLabelChange = viewModel::setOptionLabel,
        onOptionSwatchClick = viewModel::cycleOptionSwatch,
        onAddOption = viewModel::addOption,
        onSave = viewModel::save,
        onDelete = { confirmDelete = true },
        modifier = modifier,
    )

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.editor_discard_title),
            body = null,
            confirm = stringResource(R.string.editor_discard),
            dismiss = stringResource(R.string.editor_keep_editing),
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.editor_delete_title, state.name.trim()),
            body = stringResource(R.string.editor_delete_body),
            confirm = stringResource(R.string.action_delete),
            dismiss = stringResource(R.string.action_cancel),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun EditorContent(
    state: EditorState,
    errors: Set<DraftError>,
    canAddOption: Boolean,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onKindChange: (KindChoice) -> Unit,
    onUnitChange: (String) -> Unit,
    onOptionLabelChange: (Int, String) -> Unit,
    onOptionSwatchClick: (Int) -> Unit,
    onAddOption: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DiditTheme.colors
    val resources = LocalResources.current

    fun messages(vararg of: DraftError) = errors.filter { it in of }.map { DayText.draftError(resources, it) }

    Surface(modifier.fillMaxSize(), color = colors.background, contentColor = colors.ink) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Row(Modifier.offset(x = (-12).dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIconButton(R.drawable.ic_back, stringResource(R.string.action_back), onBack, iconSize = 22.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(if (state.isNew) R.string.editor_new_title else R.string.editor_edit_title),
                        style = DiditTheme.type.screenTitle,
                    )
                }
                if (state.loading) return@Column

                Field(stringResource(R.string.editor_name), messages(DraftError.NameBlank, DraftError.NameTooLong)) {
                    DiditTextField(
                        value = state.name,
                        onValueChange = onNameChange,
                        placeholder = stringResource(R.string.editor_name_placeholder),
                        label = stringResource(R.string.editor_name),
                        isError = it,
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                KindPicker(state.kind, enabled = state.isNew, onKindChange)

                when (state.kind) {
                    KindChoice.Choice -> {
                        ChoiceOptions(
                            state = state,
                            errors =
                                messages(
                                    DraftError.TooFewOptions,
                                    DraftError.TooManyOptions,
                                    DraftError.OptionLabelBlank,
                                    DraftError.OptionLabelTooLong,
                                    DraftError.DuplicateOptionLabels,
                                ),
                            canAddOption = canAddOption,
                            onLabelChange = onOptionLabelChange,
                            onSwatchClick = onOptionSwatchClick,
                            onAdd = onAddOption,
                        )
                        UnitField(
                            state.unit,
                            R.string.editor_unit_choice_placeholder,
                            messages(DraftError.UnitTooLong),
                            onUnitChange,
                        )
                    }

                    KindChoice.Amount -> {
                        UnitField(
                            state.unit,
                            R.string.editor_unit_amount_placeholder,
                            messages(DraftError.UnitTooLong),
                            onUnitChange,
                        )
                    }

                    KindChoice.Count -> {
                        UnitField(
                            state.unit,
                            R.string.editor_unit_count_placeholder,
                            messages(DraftError.UnitTooLong),
                            onUnitChange,
                        )
                    }

                    KindChoice.Done -> {
                        Text(
                            stringResource(R.string.editor_done_hint),
                            style = DiditTheme.type.hint,
                            color = colors.muted,
                        )
                    }
                }

                if (!state.isNew) {
                    TextButton(onClick = onDelete, modifier = Modifier.offset(x = (-12).dp)) {
                        Text(
                            stringResource(R.string.editor_delete),
                            style = DiditTheme.type.chip,
                            color = colors.danger,
                        )
                    }
                }
            }
            PrimaryButton(
                text = stringResource(if (state.isNew) R.string.editor_create else R.string.editor_save),
                onClick = onSave,
                enabled = !state.loading && !state.saving,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun Field(label: String, errors: List<String>, content: @Composable (isError: Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Overline(label)
        content(errors.isNotEmpty())
        Errors(errors)
    }
}

@Composable
private fun Errors(errors: List<String>) {
    errors.forEach { Text(it, style = DiditTheme.type.caption, color = DiditTheme.colors.danger) }
}

@Composable
private fun KindPicker(selected: KindChoice, enabled: Boolean, onSelect: (KindChoice) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Overline(stringResource(R.string.editor_show_as))
        KindChoice.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { kind ->
                    KindCard(
                        kind = kind,
                        selected = kind == selected,
                        enabled = enabled || kind == selected,
                        onClick = { onSelect(kind) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (!enabled) {
            Text(
                stringResource(R.string.editor_locked),
                style = DiditTheme.type.caption,
                color = DiditTheme.colors.muted,
            )
        }
    }
}

@Composable
private fun KindCard(
    kind: KindChoice,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, hint) =
        when (kind) {
            KindChoice.Choice -> R.string.kind_choice to R.string.kind_choice_hint
            KindChoice.Amount -> R.string.kind_amount to R.string.kind_amount_hint
            KindChoice.Count -> R.string.kind_count to R.string.kind_count_hint
            KindChoice.Done -> R.string.kind_done to R.string.kind_done_hint
        }
    SelectableCard(selected = selected, onClick = onClick, enabled = enabled, modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(title), style = DiditTheme.type.option, modifier = Modifier.weight(1f))
                RadioMark(selected)
            }
            Text(
                stringResource(hint),
                style = DiditTheme.type.caption,
                color = DiditTheme.colors.muted,
                minLines = 2,
            )
            KindPreview(kind, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ChoiceOptions(
    state: EditorState,
    errors: List<String>,
    canAddOption: Boolean,
    onLabelChange: (Int, String) -> Unit,
    onSwatchClick: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    val colors = DiditTheme.colors
    val placeholders = stringArrayResource(R.array.editor_choice_placeholders)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Overline(stringResource(R.string.editor_choices), Modifier.weight(1f))
            Text(
                stringResource(R.string.editor_choices_limit, HabitLimits.MAX_OPTIONS),
                style = DiditTheme.type.monoMedium,
                color = colors.muted,
            )
        }
        // Two per row, with "Add choice" taking the next free slot, as in the design.
        val slots: List<OptionRow?> = state.options + if (canAddOption) listOf(null) else emptyList()
        slots.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { row ->
                    if (row == null) {
                        DashedButton(stringResource(R.string.editor_add_choice), onAdd, Modifier.weight(1f))
                    } else {
                        val index = state.options.indexOf(row)
                        DiditTextField(
                            value = row.label,
                            onValueChange = { onLabelChange(row.key, it) },
                            placeholder = placeholders.getOrElse(index) { "" },
                            label = stringResource(R.string.editor_choice_label, index + 1),
                            keyboardOptions =
                                KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Next,
                                ),
                            modifier = Modifier.weight(1f),
                            leading = { SwatchDot(row, index) { onSwatchClick(row.key) } },
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Errors(errors)
    }
}

/** The choice's colour, inside its field. Tapping it moves on to the next colour. */
@Composable
private fun SwatchDot(row: OptionRow, index: Int, onClick: () -> Unit) {
    val description = stringResource(R.string.editor_choice_colour, index + 1)
    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .offset(x = (-8).dp)
                .size(28.dp)
                .semantics { contentDescription = description },
        shape = CircleShape,
        color = Color.Transparent,
    ) {
        Box(contentAlignment = Alignment.Center) { Dot(DiditTheme.colors.swatch(row.swatch), 10.dp) }
    }
}

@Composable
private fun UnitField(unit: String, placeholder: Int, errors: List<String>, onChange: (String) -> Unit) {
    Field(stringResource(R.string.editor_unit), errors) {
        DiditTextField(
            value = unit,
            onValueChange = onChange,
            placeholder = stringResource(placeholder),
            label = stringResource(R.string.editor_unit),
            isError = it,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String?,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = DiditTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.ink,
        textContentColor = colors.muted,
        title = { Text(title, style = DiditTheme.type.sheetTitle) },
        text = body?.let { { Text(it, style = DiditTheme.type.body) } },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(confirm, style = DiditTheme.type.chip, color = colors.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(dismiss, style = DiditTheme.type.chip, color = colors.ink)
            }
        },
    )
}
