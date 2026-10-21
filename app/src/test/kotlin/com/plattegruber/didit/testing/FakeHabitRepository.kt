package com.plattegruber.didit.testing

import com.plattegruber.didit.core.domain.draft.KindDraft
import com.plattegruber.didit.core.domain.draft.ValidHabitDraft
import com.plattegruber.didit.core.domain.model.ChoiceOption
import com.plattegruber.didit.core.domain.model.Entry
import com.plattegruber.didit.core.domain.model.EntryId
import com.plattegruber.didit.core.domain.model.EntryValue
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.OptionId
import com.plattegruber.didit.core.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate

/** An in-memory [HabitRepository] for view model tests. The real one is tested against Room. */
class FakeHabitRepository(private val now: Instant = Instant.EPOCH) : HabitRepository {
    private val habits = MutableStateFlow<List<Habit>>(emptyList())
    private val entries = MutableStateFlow<List<Entry>>(emptyList())
    private var nextId = 1L

    val currentHabits: List<Habit> get() = habits.value
    val currentEntries: List<Entry> get() = entries.value

    fun seed(habit: Habit, vararg logged: Entry) {
        habits.update { it + habit }
        entries.update { it + logged }
    }

    override fun observeHabits(): Flow<List<Habit>> = habits

    override fun observeHabit(id: HabitId): Flow<Habit?> = habits.map { list -> list.firstOrNull { it.id == id } }

    override fun observeEntries(from: LocalDate, to: LocalDate): Flow<List<Entry>> =
        entries.map { list -> list.filter { it.date in from..to } }

    override fun observeEntries(habitId: HabitId): Flow<List<Entry>> =
        entries.map { list -> list.filter { it.habitId == habitId } }

    override fun observeChanges(): Flow<Unit> = entries.drop(1).map { }

    override suspend fun getHabit(id: HabitId): Habit? = habits.value.firstOrNull { it.id == id }

    override suspend fun createHabit(draft: ValidHabitDraft): HabitId {
        val id = HabitId(nextId++)
        habits.update { it + Habit(id, draft.name, draft.kind.toKind(emptyList()), now) }
        return id
    }

    override suspend fun updateHabit(id: HabitId, draft: ValidHabitDraft) {
        habits.update { list ->
            list.map { habit ->
                if (habit.id ==
                    id
                ) {
                    habit.copy(name = draft.name, kind = draft.kind.toKind(habit.kind.optionsOrEmpty()))
                } else {
                    habit
                }
            }
        }
    }

    override suspend fun deleteHabit(id: HabitId) {
        habits.update { list -> list.filterNot { it.id == id } }
        entries.update { list -> list.filterNot { it.habitId == id } }
    }

    override suspend fun moveHabit(id: HabitId, toIndex: Int) {
        habits.update { list ->
            val moving = list.first { it.id == id }
            (list - moving).toMutableList().apply { add(toIndex.coerceIn(0, size), moving) }
        }
    }

    override suspend fun addPick(habitId: HabitId, date: LocalDate, optionId: OptionId): EntryId =
        log(habitId, date, EntryValue.Choice(optionId))

    override suspend fun deleteEntry(id: EntryId) {
        entries.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun setTotal(habitId: HabitId, date: LocalDate, total: Double) {
        entries.update { list -> list.filterNot { it.habitId == habitId && it.date == date } }
        if (total > 0.0) log(habitId, date, EntryValue.Amount(total))
    }

    override suspend fun toggleDone(habitId: HabitId, date: LocalDate): Boolean {
        val existing = entries.value.filter { it.habitId == habitId && it.date == date }
        return if (existing.isEmpty()) {
            log(habitId, date, EntryValue.Done)
            true
        } else {
            entries.update { it - existing.toSet() }
            false
        }
    }

    private fun log(habitId: HabitId, date: LocalDate, value: EntryValue): EntryId {
        val id = EntryId(nextId++)
        entries.update { it + Entry(id, habitId, date, value, now) }
        return id
    }

    private fun HabitKind.optionsOrEmpty(): List<ChoiceOption> = (this as? HabitKind.Choice)?.options.orEmpty()

    private fun KindDraft.toKind(existing: List<ChoiceOption>): HabitKind =
        when (this) {
            KindDraft.Done -> {
                HabitKind.Done
            }

            is KindDraft.Amount -> {
                HabitKind.Amount(unit)
            }

            is KindDraft.Count -> {
                HabitKind.Count(unit)
            }

            is KindDraft.Choice -> {
                HabitKind.Choice(
                    unit,
                    options.map { draft ->
                        existing.firstOrNull { it.id == draft.id }?.copy(label = draft.label, swatch = draft.swatch)
                            ?: ChoiceOption(OptionId(nextId++), draft.label, draft.swatch)
                    },
                )
            }
        }
}
