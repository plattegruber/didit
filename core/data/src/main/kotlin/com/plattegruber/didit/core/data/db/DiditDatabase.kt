package com.plattegruber.didit.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HabitEntity::class, ChoiceOptionEntity::class, EntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DiditDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao

    abstract fun entryDao(): EntryDao

    companion object {
        const val NAME = "didit.db"
    }
}
