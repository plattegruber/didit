package com.plattegruber.didit.core.domain.model

import java.time.Instant

/** Something worth seeing a pattern in: a yes/no, a quantity, a tally, or which way it went. */
data class Habit(val id: HabitId, val name: String, val kind: HabitKind, val createdAt: Instant)

/** How a habit is recorded, which in turn decides how its days are drawn. */
sealed interface HabitKind {
    /** The noun shown beside totals ("pages", "cups", "meals"); blank when there is none. */
    val unit: String

    /** Did it or didn't: a filled dot or an empty ring. */
    data object Done : HabitKind {
        override val unit: String = ""
    }

    /** A number that changes, like pages read: drawn as a bar. */
    data class Amount(override val unit: String) : HabitKind

    /** Small whole tallies, like cups of coffee: drawn as a stack of pips. */
    data class Count(override val unit: String) : HabitKind

    /** A few outcomes, logged as often as they happen: one coloured dot per pick. */
    data class Choice(override val unit: String, val options: List<ChoiceOption>) : HabitKind {
        /** Options offered for new entries. Archived options remain only to render history. */
        val activeOptions: List<ChoiceOption> get() = options.filterNot(ChoiceOption::archived)

        fun option(id: OptionId): ChoiceOption? = options.firstOrNull { it.id == id }
    }
}

data class ChoiceOption(val id: OptionId, val label: String, val swatch: Swatch, val archived: Boolean = false)
