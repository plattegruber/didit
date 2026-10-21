package com.plattegruber.didit.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Transaction
    @Query("SELECT * FROM habits ORDER BY position, id")
    fun observeAll(): Flow<List<HabitWithOptions>>

    @Transaction
    @Query("SELECT * FROM habits WHERE id = :id")
    fun observe(id: Long): Flow<HabitWithOptions?>

    @Transaction
    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun get(id: Long): HabitWithOptions?

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM habits")
    suspend fun nextPosition(): Int

    @Query("SELECT id FROM habits ORDER BY position, id")
    suspend fun orderedIds(): List<Long>

    @Query("UPDATE habits SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Insert
    suspend fun insertOptions(options: List<ChoiceOptionEntity>)

    @Update
    suspend fun updateOptions(options: List<ChoiceOptionEntity>)
}

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE date BETWEEN :fromEpochDay AND :toEpochDay ORDER BY logged_at, id")
    fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE habit_id = :habitId ORDER BY logged_at, id")
    fun observeForHabit(habitId: Long): Flow<List<EntryEntity>>

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM entries WHERE habit_id = :habitId AND date = :epochDay")
    suspend fun countOnDay(habitId: Long, epochDay: Long): Int

    /** Returns the number of entries removed. */
    @Query("DELETE FROM entries WHERE habit_id = :habitId AND date = :epochDay")
    suspend fun deleteOnDay(habitId: Long, epochDay: Long): Int
}
