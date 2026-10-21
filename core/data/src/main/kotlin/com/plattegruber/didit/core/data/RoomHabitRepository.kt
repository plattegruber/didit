package com.plattegruber.didit.core.data

import androidx.room.withTransaction
import com.plattegruber.didit.core.data.db.ChoiceOptionEntity
import com.plattegruber.didit.core.data.db.DiditDatabase
import com.plattegruber.didit.core.data.db.EntryEntity
import com.plattegruber.didit.core.data.db.HabitEntity
import com.plattegruber.didit.core.data.db.HabitWithOptions
import com.plattegruber.didit.core.data.db.toDomain
import com.plattegruber.didit.core.data.db.toEntity
import com.plattegruber.didit.core.domain.draft.HabitLimits
import com.plattegruber.didit.core.domain.draft.KindDraft
import com.plattegruber.didit.core.domain.draft.ValidHabitDraft
import com.plattegruber.didit.core.domain.draft.sameKindAs
import com.plattegruber.didit.core.domain.model.Entry
import com.plattegruber.didit.core.domain.model.EntryId
import com.plattegruber.didit.core.domain.model.EntryValue
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.OptionId
import com.plattegruber.didit.core.domain.repository.HabitRepository
import com.plattegruber.didit.core.domain.time.AppClock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomHabitRepository
@Inject
constructor(private val database: DiditDatabase, private val clock: AppClock) :
    HabitRepository {
    private val habits = database.habitDao()
    private val entries = database.entryDao()

    override fun observeHabits(): Flow<List<Habit>> =
        habits.observeAll().map { list -> list.map(HabitWithOptions::toDomain) }.distinctUntilChanged()

    override fun observeHabit(id: HabitId): Flow<Habit?> =
        habits.observe(id.value).map { it?.toDomain() }.distinctUntilChanged()

    override fun observeEntries(from: LocalDate, to: LocalDate): Flow<List<Entry>> = entries
        .observeBetween(from.toEpochDay(), to.toEpochDay())
        .map { list -> list.map(EntryEntity::toDomain) }
        .distinctUntilChanged()

    override fun observeEntries(habitId: HabitId): Flow<List<Entry>> = entries
        .observeForHabit(habitId.value)
        .map { list -> list.map(EntryEntity::toDomain) }
        .distinctUntilChanged()

    override fun observeChanges(): Flow<Unit> = database.invalidationTracker
        .createFlow(HABITS_TABLE, OPTIONS_TABLE, ENTRIES_TABLE, emitInitialState = false)
        .map { }

    override suspend fun getHabit(id: HabitId): Habit? = habits.get(id.value)?.toDomain()

    override suspend fun createHabit(draft: ValidHabitDraft): HabitId = database.withTransaction {
        val id =
            habits.insert(
                HabitEntity(
                    name = draft.name,
                    kind = draft.kind.storageKey(),
                    unit = draft.kind.unit(),
                    position = habits.nextPosition(),
                    createdAt = clock.now().toEpochMilli(),
                ),
            )
        val choice = draft.kind as? KindDraft.Choice
        if (choice != null) {
            habits.insertOptions(
                choice.options.mapIndexed { index, option ->
                    ChoiceOptionEntity(
                        habitId = id,
                        label = option.label,
                        swatch = option.swatch.key,
                        position = index,
                        archived = false,
                    )
                },
            )
        }
        HabitId(id)
    }

    override suspend fun updateHabit(id: HabitId, draft: ValidHabitDraft) = database.withTransaction {
        val existing = requireNotNull(habits.get(id.value)) { "No habit with id ${id.value}" }
        require(draft.kind.sameKindAs(existing.toDomain().kind)) { "A habit's kind cannot change" }

        habits.update(existing.habit.copy(name = draft.name, unit = draft.kind.unit()))
        val choice = draft.kind as? KindDraft.Choice
        if (choice != null) updateOptions(existing, choice)
    }

    private suspend fun updateOptions(existing: HabitWithOptions, choice: KindDraft.Choice) {
        val current = existing.options.associateBy(ChoiceOptionEntity::id)
        val kept = choice.options.mapNotNull { it.id?.value }.toSet()
        require(kept.all(current::containsKey)) { "Options must belong to the habit being edited" }

        val updated =
            choice.options.mapIndexedNotNull { index, option ->
                option.id?.let { optionId ->
                    current.getValue(optionId.value).copy(
                        label = option.label,
                        swatch = option.swatch.key,
                        position = index,
                        archived = false,
                    )
                }
            }
        // Removed options are archived so the days logged against them keep their colour.
        val archived = current.values.filter { it.id !in kept }.map { it.copy(archived = true) }
        habits.updateOptions(updated + archived)

        val added =
            choice.options.mapIndexedNotNull { index, option ->
                if (option.id != null) {
                    null
                } else {
                    ChoiceOptionEntity(
                        habitId = existing.habit.id,
                        label = option.label,
                        swatch = option.swatch.key,
                        position = index,
                        archived = false,
                    )
                }
            }
        habits.insertOptions(added)
    }

    override suspend fun deleteHabit(id: HabitId) = habits.delete(id.value)

    override suspend fun moveHabit(id: HabitId, toIndex: Int) = database.withTransaction {
        val order = habits.orderedIds().toMutableList()
        if (order.remove(id.value)) {
            order.add(toIndex.coerceIn(0, order.size), id.value)
            order.forEachIndexed { position, habitId -> habits.setPosition(habitId, position) }
        }
    }

    override suspend fun addPick(habitId: HabitId, date: LocalDate, optionId: OptionId): EntryId =
        database.withTransaction {
            val kind = requireHabit(habitId).kind
            require(kind is HabitKind.Choice) { "Only choice habits take picks" }
            require(kind.option(optionId)?.archived == false) {
                "Option ${optionId.value} can't take new picks"
            }
            require(entries.countOnDay(habitId.value, date.toEpochDay()) < HabitLimits.MAX_PICKS_PER_DAY) {
                "A day holds at most ${HabitLimits.MAX_PICKS_PER_DAY} picks"
            }
            EntryId(entries.insert(EntryValue.Choice(optionId).toEntity(habitId, date, clock.now())))
        }

    override suspend fun deleteEntry(id: EntryId) = entries.delete(id.value)

    override suspend fun setTotal(habitId: HabitId, date: LocalDate, total: Double) = database.withTransaction {
        val kind = requireHabit(habitId).kind
        require(kind is HabitKind.Amount || kind is HabitKind.Count) {
            "Only amount and count habits have totals"
        }
        require(total.isFinite() && total >= 0.0) { "Totals can't be negative, was $total" }
        require(kind !is HabitKind.Count || total % 1.0 == 0.0) { "Counts are whole numbers, was $total" }

        // A day's total lives in a single entry, so stepping it up and down never leaves a trail.
        entries.deleteOnDay(habitId.value, date.toEpochDay())
        if (total > 0.0) entries.insert(EntryValue.Amount(total).toEntity(habitId, date, clock.now()))
    }

    override suspend fun toggleDone(habitId: HabitId, date: LocalDate): Boolean = database.withTransaction {
        require(requireHabit(habitId).kind is HabitKind.Done) { "Only yes/no habits can be toggled" }
        val cleared = entries.deleteOnDay(habitId.value, date.toEpochDay()) > 0
        if (!cleared) entries.insert(EntryValue.Done.toEntity(habitId, date, clock.now()))
        !cleared
    }

    private suspend fun requireHabit(id: HabitId): Habit =
        requireNotNull(getHabit(id)) { "No habit with id ${id.value}" }

    private fun KindDraft.storageKey(): String = when (this) {
        KindDraft.Done -> HabitEntity.Kind.DONE
        is KindDraft.Amount -> HabitEntity.Kind.AMOUNT
        is KindDraft.Count -> HabitEntity.Kind.COUNT
        is KindDraft.Choice -> HabitEntity.Kind.CHOICE
    }

    private fun KindDraft.unit(): String = when (this) {
        KindDraft.Done -> ""
        is KindDraft.Amount -> unit
        is KindDraft.Count -> unit
        is KindDraft.Choice -> unit
    }

    private companion object {
        const val HABITS_TABLE = "habits"
        const val OPTIONS_TABLE = "choice_options"
        const val ENTRIES_TABLE = "entries"
    }
}
