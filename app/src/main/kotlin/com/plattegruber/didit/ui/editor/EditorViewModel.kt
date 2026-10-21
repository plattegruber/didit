package com.plattegruber.didit.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plattegruber.didit.core.domain.draft.DraftError
import com.plattegruber.didit.core.domain.draft.DraftValidation
import com.plattegruber.didit.core.domain.draft.HabitDraft
import com.plattegruber.didit.core.domain.draft.HabitLimits
import com.plattegruber.didit.core.domain.draft.KindDraft
import com.plattegruber.didit.core.domain.draft.OptionDraft
import com.plattegruber.didit.core.domain.draft.toDraft
import com.plattegruber.didit.core.domain.draft.validate
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.core.domain.model.OptionId
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.core.domain.repository.HabitRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

/** The four ways a habit can be shown, in the design's order. */
enum class KindChoice { Choice, Amount, Count, Done }

data class OptionRow(
    /** Stable across edits, so the right field keeps focus as rows come and go. */
    val key: Int,
    val id: OptionId?,
    val label: String,
    val swatch: Swatch,
)

data class EditorState(
    val isNew: Boolean,
    val loading: Boolean = false,
    val name: String = "",
    val kind: KindChoice = KindChoice.Choice,
    val unit: String = "",
    val options: List<OptionRow> = emptyList(),
    /** Errors are only shown once the user has tried to save. */
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val finished: Boolean = false,
    val deleted: Boolean = false,
)

/**
 * Edits a new or existing habit. Choice rows left blank are simply left out, so a spare row never
 * blocks saving.
 */
@HiltViewModel(assistedFactory = EditorViewModel.Factory::class)
class EditorViewModel
@AssistedInject
constructor(
    @Assisted rawHabitId: Long?,
    private val repository: HabitRepository,
) : ViewModel() {
    private val habitId: HabitId? = rawHabitId?.let(::HabitId)

    var state by mutableStateOf(EditorState(isNew = habitId == null, loading = habitId != null))
        private set

    private var nextKey = 0
    private var initialDraft: HabitDraft? = null

    val errors: Set<DraftError>
        get() = (draft().validate() as? DraftValidation.Invalid)?.errors.orEmpty()

    /** Whether leaving now would lose anything. */
    val hasChanges: Boolean
        get() = !state.loading && draft() != initialDraft

    val canAddOption: Boolean get() = state.options.size < HabitLimits.MAX_OPTIONS

    init {
        if (habitId == null) {
            state = state.copy(options = DefaultSwatches.take(DEFAULT_OPTIONS).map { newRow(it) })
            initialDraft = draft()
        } else {
            viewModelScope.launch { load(habitId) }
        }
    }

    private suspend fun load(id: HabitId) {
        val habit = repository.getHabit(id)
        if (habit == null) {
            state = state.copy(loading = false, finished = true)
            return
        }
        val draft = habit.toDraft()
        val (kind, unit, options) =
            when (val k = draft.kind) {
                is KindDraft.Choice -> Triple(KindChoice.Choice, k.unit, k.options)
                is KindDraft.Amount -> Triple(KindChoice.Amount, k.unit, emptyList())
                is KindDraft.Count -> Triple(KindChoice.Count, k.unit, emptyList())
                KindDraft.Done -> Triple(KindChoice.Done, "", emptyList())
            }
        state =
            state.copy(
                loading = false,
                name = draft.name,
                kind = kind,
                unit = unit,
                options = options.map { OptionRow(nextKey++, it.id, it.label, it.swatch) },
            )
        initialDraft = draft()
    }

    fun setName(name: String) {
        state = state.copy(name = name)
    }

    /** Only a new habit can change kind; an existing one's history is drawn for the kind it has. */
    fun setKind(kind: KindChoice) {
        if (state.isNew) state = state.copy(kind = kind)
    }

    fun setUnit(unit: String) {
        state = state.copy(unit = unit)
    }

    fun setOptionLabel(key: Int, label: String) = updateOption(key) { it.copy(label = label) }

    /** Moves an option on to the next colour of the palette. */
    fun cycleOptionSwatch(key: Int) = updateOption(key) { row ->
        val swatches = Swatch.entries
        row.copy(swatch = swatches[(swatches.indexOf(row.swatch) + 1) % swatches.size])
    }

    fun addOption() {
        if (!canAddOption) return
        val used = state.options.map { it.swatch }.toSet()
        val swatch = DefaultSwatches.firstOrNull { it !in used } ?: Swatch.Ink
        state = state.copy(options = state.options + newRow(swatch))
    }

    fun removeOption(key: Int) {
        state = state.copy(options = state.options.filterNot { it.key == key })
    }

    fun save() {
        if (state.saving || state.loading) return
        when (val result = draft().validate()) {
            is DraftValidation.Invalid -> {
                state = state.copy(showErrors = true)
            }

            is DraftValidation.Valid -> {
                state = state.copy(saving = true)
                viewModelScope.launch {
                    if (habitId ==
                        null
                    ) {
                        repository.createHabit(result.draft)
                    } else {
                        repository.updateHabit(habitId, result.draft)
                    }
                    state = state.copy(saving = false, finished = true)
                }
            }
        }
    }

    fun delete() {
        val id = habitId ?: return
        viewModelScope.launch {
            repository.deleteHabit(id)
            state = state.copy(deleted = true, finished = true)
        }
    }

    private fun draft(): HabitDraft {
        val s = state
        val kind =
            when (s.kind) {
                KindChoice.Choice -> {
                    KindDraft.Choice(
                        s.unit,
                        s.options.filter { it.label.isNotBlank() }.map { OptionDraft(it.id, it.label, it.swatch) },
                    )
                }

                KindChoice.Amount -> {
                    KindDraft.Amount(s.unit)
                }

                KindChoice.Count -> {
                    KindDraft.Count(s.unit)
                }

                KindChoice.Done -> {
                    KindDraft.Done
                }
            }
        return HabitDraft(s.name, kind)
    }

    private fun updateOption(key: Int, change: (OptionRow) -> OptionRow) {
        state = state.copy(options = state.options.map { if (it.key == key) change(it) else it })
    }

    private fun newRow(swatch: Swatch) = OptionRow(nextKey++, id = null, label = "", swatch = swatch)

    @AssistedFactory
    interface Factory {
        /** Takes the raw id: Dagger can't generate factories for Kotlin value classes. */
        fun create(habitId: Long?): EditorViewModel
    }

    private companion object {
        /** The design starts with three choices: ink, then blue, then amber. */
        const val DEFAULT_OPTIONS = 3
        val DefaultSwatches = listOf(Swatch.Ink, Swatch.Blue, Swatch.Amber, Swatch.Coral)
    }
}
