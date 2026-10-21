package com.plattegruber.didit.testing

import com.plattegruber.didit.core.domain.model.ChoiceOption
import com.plattegruber.didit.core.domain.model.DayValue
import com.plattegruber.didit.core.domain.model.Entry
import com.plattegruber.didit.core.domain.model.EntryId
import com.plattegruber.didit.core.domain.model.EntryValue
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.OptionId
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.ui.viz.HabitHistory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The data drawn in the design canvas, so screenshots can be held up against it. */
object DesignSamples {
    /** A Tuesday, as in the design. */
    val TODAY: LocalDate = LocalDate.of(2026, 10, 20)

    val HOME = ChoiceOption(OptionId(1), "Home", Swatch.Ink)
    val TAKEOUT = ChoiceOption(OptionId(2), "Takeout", Swatch.Blue)
    val OUT = ChoiceOption(OptionId(3), "Out", Swatch.Amber)

    val meals = Habit(HabitId(1), "Meals", HabitKind.Choice("meals", listOf(HOME, TAKEOUT, OUT)), Instant.EPOCH)
    val pages = Habit(HabitId(2), "Pages", HabitKind.Amount("pages"), Instant.EPOCH)
    val coffee = Habit(HabitId(3), "Coffee", HabitKind.Count("cups"), Instant.EPOCH)
    val walk = Habit(HabitId(4), "Walk", HabitKind.Done, Instant.EPOCH)

    // Oldest first, ending today; from the design's Main artboard, then extended back for longer views.
    private val mealDays =
        listOf(
            "HHH",
            "HO",
            "HHT",
            "HHHH",
            "OO",
            "HHHHH",
            "HTH",
            "HHH",
            "HOH",
            "HHH",
            "HOH",
            "HTO",
            "OH",
            "HHHHH",
            "HHH",
            "HOH",
            "HTO",
            "OH",
            "HHHHH",
            "HHT",
            "HHHH",
            "OH",
            "HTH",
            "HOO",
            "OT",
            "HHHHH",
            "HHH",
            "HH",
        )
    private val pageDays =
        listOf(
            20,
            35,
            0,
            40,
            55,
            10,
            15,
            22,
            0,
            30,
            48,
            64,
            12,
            0,
            32,
            18,
            0,
            45,
            60,
            12,
            20,
            0,
            25,
            14,
            0,
            52,
            30,
            12,
        )
    private val coffeeDays =
        listOf(
            2,
            1,
            2,
            3,
            2,
            1,
            1,
            2,
            2,
            3,
            1,
            1,
            2,
            2,
            2,
            2,
            3,
            1,
            1,
            2,
            2,
            3,
            2,
            2,
            1,
            0,
            2,
            1,
        )
    private val walkDays =
        listOf(
            1,
            1,
            0,
            1,
            1,
            0,
            1,
            1,
            0,
            1,
            1,
            1,
            0,
            1,
            1,
            0,
            1,
            1,
            1,
            0,
            1,
            0,
            1,
            1,
            1,
            0,
            1,
            0,
        )

    private fun dateAt(index: Int, size: Int): LocalDate = TODAY.minusDays((size - 1 - index).toLong())

    val mealsHistory: HabitHistory =
        HabitHistory(
            meals,
            mealDays.withIndex().associate { (i, day) ->
                dateAt(i, mealDays.size) to
                    DayValue.Choices(
                        day.map {
                            when (it) {
                                'H' -> HOME.id
                                'T' -> TAKEOUT.id
                                else -> OUT.id
                            }
                        },
                    )
            },
        )

    val pagesHistory: HabitHistory =
        HabitHistory(
            pages,
            pageDays.withIndex().filter { it.value > 0 }.associate { (i, v) ->
                dateAt(i, pageDays.size) to DayValue.Amount(v.toDouble())
            },
        )

    val coffeeHistory: HabitHistory =
        HabitHistory(
            coffee,
            coffeeDays.withIndex().filter { it.value > 0 }.associate { (i, v) ->
                dateAt(i, coffeeDays.size) to DayValue.Amount(v.toDouble())
            },
        )

    val walkHistory: HabitHistory =
        HabitHistory(
            walk,
            walkDays.withIndex().filter { it.value > 0 }.associate { (i, _) ->
                dateAt(i, walkDays.size) to DayValue.Done
            },
        )

    val all: List<HabitHistory> = listOf(mealsHistory, pagesHistory, coffeeHistory, walkHistory)

    /** Today's meals as entries, for the log sheet. */
    val mealsToday: List<Entry> =
        listOf(HOME, HOME).mapIndexed { index, option ->
            Entry(
                id = EntryId(index + 1L),
                habitId = meals.id,
                date = TODAY,
                value = EntryValue.Choice(option.id),
                loggedAt = TODAY.atTime(8 + index * 4, 0).toInstant(ZoneOffset.UTC),
            )
        }

    fun amountToday(habit: Habit, total: Double): List<Entry> =
        listOf(
            Entry(
                id = EntryId(100),
                habitId = habit.id,
                date = TODAY,
                value = EntryValue.Amount(total),
                loggedAt = TODAY.atTime(9, 0).toInstant(ZoneOffset.UTC),
            ),
        )
}
