package com.plattegruber.didit.core.data.db

import com.plattegruber.didit.core.domain.model.ChoiceOption
import com.plattegruber.didit.core.domain.model.Entry
import com.plattegruber.didit.core.domain.model.EntryId
import com.plattegruber.didit.core.domain.model.EntryValue
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.OptionId
import com.plattegruber.didit.core.domain.model.Swatch
import java.time.Instant
import java.time.LocalDate

internal fun HabitWithOptions.toDomain(): Habit =
    Habit(
        id = HabitId(habit.id),
        name = habit.name,
        kind =
            when (habit.kind) {
                HabitEntity.Kind.AMOUNT -> {
                    HabitKind.Amount(habit.unit)
                }

                HabitEntity.Kind.COUNT -> {
                    HabitKind.Count(habit.unit)
                }

                HabitEntity.Kind.CHOICE -> {
                    HabitKind.Choice(
                        habit.unit,
                        options
                            .sortedWith(compareBy(ChoiceOptionEntity::position, ChoiceOptionEntity::id))
                            .map { ChoiceOption(OptionId(it.id), it.label, Swatch.fromKey(it.swatch), it.archived) },
                    )
                }

                else -> {
                    HabitKind.Done
                }
            },
        createdAt = Instant.ofEpochMilli(habit.createdAt),
    )

internal fun EntryEntity.toDomain(): Entry =
    Entry(
        id = EntryId(id),
        habitId = HabitId(habitId),
        date = LocalDate.ofEpochDay(date),
        value =
            when {
                optionId != null -> EntryValue.Choice(OptionId(optionId))
                amount != null -> EntryValue.Amount(amount)
                else -> EntryValue.Done
            },
        loggedAt = Instant.ofEpochMilli(loggedAt),
    )

internal fun EntryValue.toEntity(habitId: HabitId, date: LocalDate, loggedAt: Instant): EntryEntity =
    EntryEntity(
        habitId = habitId.value,
        date = date.toEpochDay(),
        amount = (this as? EntryValue.Amount)?.amount,
        optionId = (this as? EntryValue.Choice)?.optionId?.value,
        loggedAt = loggedAt.toEpochMilli(),
    )
