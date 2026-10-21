package com.plattegruber.didit.core.domain.draft

import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.OptionId
import com.plattegruber.didit.core.domain.model.Swatch

/** A habit as typed into the editor: unvalidated and untrimmed. */
data class HabitDraft(val name: String, val kind: KindDraft)

sealed interface KindDraft {
    data object Done : KindDraft

    data class Amount(val unit: String) : KindDraft

    data class Count(val unit: String) : KindDraft

    data class Choice(val unit: String, val options: List<OptionDraft>) : KindDraft
}

/** [id] is null for an option that hasn't been saved yet. */
data class OptionDraft(val id: OptionId?, val label: String, val swatch: Swatch)

enum class DraftError {
    NameBlank,
    NameTooLong,
    UnitTooLong,
    TooFewOptions,
    TooManyOptions,
    OptionLabelBlank,
    OptionLabelTooLong,
    DuplicateOptionLabels,
}

/**
 * A draft that has passed [HabitDraft.validate]. Only this module can construct one, so the
 * repository can rely on every invariant holding.
 */
class ValidHabitDraft internal constructor(val name: String, val kind: KindDraft) {
    override fun equals(other: Any?): Boolean =
        other is ValidHabitDraft && name == other.name && kind == other.kind

    override fun hashCode(): Int = name.hashCode() * 31 + kind.hashCode()

    override fun toString(): String = "ValidHabitDraft(name=$name, kind=$kind)"
}

sealed interface DraftValidation {
    data class Valid(val draft: ValidHabitDraft) : DraftValidation

    data class Invalid(val errors: Set<DraftError>) : DraftValidation
}

object HabitLimits {
    const val MAX_NAME_LENGTH = 24
    const val MAX_UNIT_LENGTH = 12
    const val MAX_OPTION_LABEL_LENGTH = 16
    const val MIN_OPTIONS = 2
    const val MAX_OPTIONS = 4

    /** Enough for every meal of a day, few enough that a day's dots still stack legibly. */
    const val MAX_PICKS_PER_DAY = 6
}

fun HabitDraft.validate(): DraftValidation {
    val errors = mutableSetOf<DraftError>()
    val trimmedName = name.trim()
    when {
        trimmedName.isEmpty() -> errors += DraftError.NameBlank
        trimmedName.length > HabitLimits.MAX_NAME_LENGTH -> errors += DraftError.NameTooLong
    }

    fun unit(raw: String): String =
        raw.trim().also {
            if (it.length > HabitLimits.MAX_UNIT_LENGTH) errors += DraftError.UnitTooLong
        }

    val normalizedKind =
        when (kind) {
            KindDraft.Done -> {
                kind
            }

            is KindDraft.Amount -> {
                KindDraft.Amount(unit(kind.unit))
            }

            is KindDraft.Count -> {
                KindDraft.Count(unit(kind.unit))
            }

            is KindDraft.Choice -> {
                val options = kind.options.map { it.copy(label = it.label.trim()) }
                if (options.size < HabitLimits.MIN_OPTIONS) errors += DraftError.TooFewOptions
                if (options.size > HabitLimits.MAX_OPTIONS) errors += DraftError.TooManyOptions
                if (options.any { it.label.isEmpty() }) errors += DraftError.OptionLabelBlank
                if (options.any { it.label.length > HabitLimits.MAX_OPTION_LABEL_LENGTH }) {
                    errors += DraftError.OptionLabelTooLong
                }
                val labels = options.map { it.label.lowercase() }.filter(String::isNotEmpty)
                if (labels.size != labels.toSet().size) errors += DraftError.DuplicateOptionLabels
                KindDraft.Choice(unit(kind.unit), options)
            }
        }

    return if (errors.isEmpty()) {
        DraftValidation.Valid(ValidHabitDraft(trimmedName, normalizedKind))
    } else {
        DraftValidation.Invalid(errors)
    }
}

/** Whether this draft records the same kind of thing as [kind]; a habit's kind can never change. */
fun KindDraft.sameKindAs(kind: HabitKind): Boolean =
    when (this) {
        KindDraft.Done -> kind is HabitKind.Done
        is KindDraft.Amount -> kind is HabitKind.Amount
        is KindDraft.Count -> kind is HabitKind.Count
        is KindDraft.Choice -> kind is HabitKind.Choice
    }

/** The editable draft for an existing habit. Archived options are not offered for editing. */
fun Habit.toDraft(): HabitDraft =
    HabitDraft(
        name = name,
        kind =
            when (val k = kind) {
                HabitKind.Done -> {
                    KindDraft.Done
                }

                is HabitKind.Amount -> {
                    KindDraft.Amount(k.unit)
                }

                is HabitKind.Count -> {
                    KindDraft.Count(k.unit)
                }

                is HabitKind.Choice -> {
                    val options = k.activeOptions.map { OptionDraft(it.id, it.label, it.swatch) }
                    KindDraft.Choice(k.unit, options)
                }
            },
    )
