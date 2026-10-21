package com.plattegruber.didit.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,
    val unit: String,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    object Kind {
        const val DONE = "done"
        const val AMOUNT = "amount"
        const val COUNT = "count"
        const val CHOICE = "choice"
    }
}

@Entity(
    tableName = "choice_options",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habit_id")],
)
data class ChoiceOptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "habit_id") val habitId: Long,
    val label: String,
    val swatch: String,
    val position: Int,
    val archived: Boolean,
)

/**
 * One logged value. The populated column says what was logged: an option id for a choice, an
 * amount for an amount or count day's total, neither for a plain "done".
 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChoiceOptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["option_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habit_id", "date"), Index("date"), Index("option_id")],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "habit_id") val habitId: Long,
    /** Days since 1970-01-01 in the user's zone at the time of logging. */
    val date: Long,
    val amount: Double?,
    @ColumnInfo(name = "option_id") val optionId: Long?,
    @ColumnInfo(name = "logged_at") val loggedAt: Long,
)

data class HabitWithOptions(
    @Embedded val habit: HabitEntity,
    @Relation(parentColumn = "id", entityColumn = "habit_id")
    val options: List<ChoiceOptionEntity>,
)
