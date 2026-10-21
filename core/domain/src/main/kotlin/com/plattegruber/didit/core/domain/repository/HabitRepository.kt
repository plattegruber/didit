package com.plattegruber.didit.core.domain.repository

import com.plattegruber.didit.core.domain.draft.ValidHabitDraft
import com.plattegruber.didit.core.domain.model.Entry
import com.plattegruber.didit.core.domain.model.EntryId
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.core.domain.model.OptionId
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface HabitRepository {
    /** All habits, in the order the user arranged them. */
    fun observeHabits(): Flow<List<Habit>>

    fun observeHabit(id: HabitId): Flow<Habit?>

    /** Entries for every habit with a date in [from]..[to], inclusive. */
    fun observeEntries(from: LocalDate, to: LocalDate): Flow<List<Entry>>

    /** Every entry ever logged for [habitId]. */
    fun observeEntries(habitId: HabitId): Flow<List<Entry>>

    /** Emits after every committed change to habits or entries. Carries no state; re-query what you need. */
    fun observeChanges(): Flow<Unit>

    suspend fun getHabit(id: HabitId): Habit?

    suspend fun createHabit(draft: ValidHabitDraft): HabitId

    /**
     * Applies [draft] to an existing habit. Choice options missing from the draft are archived,
     * not deleted, so that history logged against them keeps rendering.
     *
     * @throws IllegalArgumentException if the draft would change what kind of habit this is.
     */
    suspend fun updateHabit(id: HabitId, draft: ValidHabitDraft)

    suspend fun deleteHabit(id: HabitId)

    /** Moves [id] to [toIndex] in the user's ordering. */
    suspend fun moveHabit(id: HabitId, toIndex: Int)

    /** Logs one pick for a choice habit. @throws IllegalArgumentException for archived or unknown options. */
    suspend fun addPick(habitId: HabitId, date: LocalDate, optionId: OptionId): EntryId

    suspend fun deleteEntry(id: EntryId)

    /**
     * Sets an amount or count habit's total for [date]; zero clears the day.
     *
     * @throws IllegalArgumentException if the total is negative, or fractional for a count.
     */
    suspend fun setTotal(habitId: HabitId, date: LocalDate, total: Double)

    /** Marks a yes/no habit done for [date], or clears it if it already was. Returns the new state. */
    suspend fun toggleDone(habitId: HabitId, date: LocalDate): Boolean
}
