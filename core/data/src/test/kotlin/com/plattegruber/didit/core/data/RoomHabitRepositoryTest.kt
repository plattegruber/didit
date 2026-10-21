package com.plattegruber.didit.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.plattegruber.didit.core.data.db.DiditDatabase
import com.plattegruber.didit.core.domain.draft.DraftValidation
import com.plattegruber.didit.core.domain.draft.HabitDraft
import com.plattegruber.didit.core.domain.draft.HabitLimits
import com.plattegruber.didit.core.domain.draft.KindDraft
import com.plattegruber.didit.core.domain.draft.OptionDraft
import com.plattegruber.didit.core.domain.draft.ValidHabitDraft
import com.plattegruber.didit.core.domain.draft.validate
import com.plattegruber.didit.core.domain.model.ChoiceOption
import com.plattegruber.didit.core.domain.model.EntryValue
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.core.domain.time.AppClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class RoomHabitRepositoryTest {
    private val day = LocalDate.of(2026, 10, 21)
    private val clock =
        object : AppClock {
            var now: Instant = Instant.parse("2026-10-21T08:00:00Z")

            override fun now(): Instant = now

            override fun zone(): ZoneId = ZoneOffset.UTC
        }

    private lateinit var database: DiditDatabase
    private lateinit var repository: RoomHabitRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DiditDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomHabitRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun valid(name: String, kind: KindDraft): ValidHabitDraft =
        (HabitDraft(name, kind).validate() as DraftValidation.Valid).draft

    private fun meals(vararg labels: String) =
        KindDraft.Choice("meals", labels.map { OptionDraft(id = null, label = it, swatch = Swatch.Ink) })

    private suspend fun choiceOptions(id: HabitId): List<ChoiceOption> =
        (repository.getHabit(id)!!.kind as HabitKind.Choice).options

    private suspend fun failure(block: suspend () -> Unit): Throwable? = runCatching { block() }.exceptionOrNull()

    @Test
    fun createsEachKindOfHabit() =
        runTest {
            repository.createHabit(valid("Walk", KindDraft.Done))
            repository.createHabit(valid("Pages", KindDraft.Amount("pages")))
            repository.createHabit(valid("Coffee", KindDraft.Count("cups")))
            repository.createHabit(valid("Meals", meals("Home", "Out")))

            val habits = repository.observeHabits().first()

            assertThat(habits.map { it.name }).containsExactly("Walk", "Pages", "Coffee", "Meals").inOrder()
            assertThat(habits[0].kind).isEqualTo(HabitKind.Done)
            assertThat(habits[1].kind).isEqualTo(HabitKind.Amount("pages"))
            assertThat(habits[2].kind).isEqualTo(HabitKind.Count("cups"))
            val choice = habits[3].kind as HabitKind.Choice
            assertThat(choice.unit).isEqualTo("meals")
            assertThat(choice.options.map { it.label }).containsExactly("Home", "Out").inOrder()
            assertThat(habits[0].createdAt).isEqualTo(clock.now)
        }

    @Test
    fun updatingAChoiceArchivesRemovedOptionsAndAddsNewOnes() =
        runTest {
            val id = repository.createHabit(valid("Meals", meals("Home", "Out", "Skipped")))
            val (home, out, skipped) = choiceOptions(id)

            repository.updateHabit(
                id,
                valid(
                    "Meals",
                    KindDraft.Choice(
                        "meals",
                        listOf(
                            OptionDraft(out.id, "Restaurant", Swatch.Amber),
                            OptionDraft(home.id, "Home", Swatch.Ink),
                            OptionDraft(null, "Takeout", Swatch.Blue),
                        ),
                    ),
                ),
            )

            val options = choiceOptions(id)
            assertThat(options.filterNot(ChoiceOption::archived).map { it.label })
                .containsExactly("Restaurant", "Home", "Takeout")
                .inOrder()
            assertThat(options.single { it.archived }.id).isEqualTo(skipped.id)
            assertThat(options.single { it.id == out.id }.swatch).isEqualTo(Swatch.Amber)
        }

    @Test
    fun archivedOptionsKeepTheirHistoryButRejectNewPicks() =
        runTest {
            val id = repository.createHabit(valid("Meals", meals("Home", "Out", "Skipped")))
            val (home, out, skipped) = choiceOptions(id)
            repository.addPick(id, day, skipped.id)

            repository.updateHabit(
                id,
                valid(
                    "Meals",
                    KindDraft.Choice("meals", listOf(home, out).map { OptionDraft(it.id, it.label, it.swatch) }),
                ),
            )

            assertThat(repository.observeEntries(id).first().map { it.value })
                .containsExactly(EntryValue.Choice(skipped.id))
            assertThat(failure { repository.addPick(id, day, skipped.id) })
                .isInstanceOf(IllegalArgumentException::class.java)
        }

    @Test
    fun updateCannotChangeTheKind() =
        runTest {
            val id = repository.createHabit(valid("Pages", KindDraft.Amount("pages")))

            assertThat(failure { repository.updateHabit(id, valid("Pages", KindDraft.Count("pages"))) })
                .isInstanceOf(IllegalArgumentException::class.java)
        }

    @Test
    fun updateRejectsOptionsFromAnotherHabit() =
        runTest {
            val first = repository.createHabit(valid("Meals", meals("Home", "Out")))
            val second = repository.createHabit(valid("Commute", meals("Bike", "Bus")))
            val foreign = choiceOptions(first).first()

            val draft =
                valid(
                    "Commute",
                    KindDraft.Choice(
                        "",
                        listOf(OptionDraft(foreign.id, "Bike", Swatch.Ink), OptionDraft(null, "Bus", Swatch.Blue)),
                    ),
                )

            assertThat(failure { repository.updateHabit(second, draft) })
                .isInstanceOf(IllegalArgumentException::class.java)
        }

    @Test
    fun updatesNameAndUnit() =
        runTest {
            val id = repository.createHabit(valid("Pages", KindDraft.Amount("pages")))

            repository.updateHabit(id, valid("Reading", KindDraft.Amount("chapters")))

            val habit = repository.getHabit(id)!!
            assertThat(habit.name).isEqualTo("Reading")
            assertThat(habit.kind).isEqualTo(HabitKind.Amount("chapters"))
        }

    @Test
    fun toggleDoneFlipsTheDay() =
        runTest {
            val id = repository.createHabit(valid("Walk", KindDraft.Done))

            assertThat(repository.toggleDone(id, day)).isTrue()
            assertThat(repository.observeEntries(id).first()).hasSize(1)
            assertThat(repository.toggleDone(id, day)).isFalse()
            assertThat(repository.observeEntries(id).first()).isEmpty()
        }

    @Test
    fun toggleDoneOnlyAppliesToYesNoHabits() =
        runTest {
            val id = repository.createHabit(valid("Pages", KindDraft.Amount("pages")))

            assertThat(failure { repository.toggleDone(id, day) })
                .isInstanceOf(IllegalArgumentException::class.java)
        }

    @Test
    fun setTotalKeepsOneEntryPerDay() =
        runTest {
            val id = repository.createHabit(valid("Pages", KindDraft.Amount("pages")))

            repository.setTotal(id, day, 12.0)
            repository.setTotal(id, day, 17.5)
            repository.setTotal(id, day.minusDays(1), 4.0)

            val entries = repository.observeEntries(id).first()
            assertThat(entries.map { it.date to it.value }).containsExactly(
                day to EntryValue.Amount(17.5),
                day.minusDays(1) to EntryValue.Amount(4.0),
            )
            assertThat(entries.first().loggedAt).isEqualTo(clock.now)
        }

    @Test
    fun setTotalToZeroClearsTheDay() =
        runTest {
            val id = repository.createHabit(valid("Coffee", KindDraft.Count("cups")))
            repository.setTotal(id, day, 2.0)

            repository.setTotal(id, day, 0.0)

            assertThat(repository.observeEntries(id).first()).isEmpty()
        }

    @Test
    fun setTotalRejectsInvalidTotals() =
        runTest {
            val pages = repository.createHabit(valid("Pages", KindDraft.Amount("pages")))
            val coffee = repository.createHabit(valid("Coffee", KindDraft.Count("cups")))
            val walk = repository.createHabit(valid("Walk", KindDraft.Done))

            assertThat(failure { repository.setTotal(pages, day, -1.0) })
                .isInstanceOf(IllegalArgumentException::class.java)
            assertThat(failure { repository.setTotal(pages, day, Double.NaN) })
                .isInstanceOf(IllegalArgumentException::class.java)
            assertThat(failure { repository.setTotal(coffee, day, 1.5) })
                .isInstanceOf(IllegalArgumentException::class.java)
            assertThat(failure { repository.setTotal(walk, day, 1.0) })
                .isInstanceOf(IllegalArgumentException::class.java)
            assertThat(failure { repository.setTotal(HabitId(999), day, 1.0) })
                .isInstanceOf(IllegalArgumentException::class.java)
        }

    @Test
    fun picksAccumulateAndCanBeRemovedOneByOne() =
        runTest {
            val id = repository.createHabit(valid("Meals", meals("Home", "Out")))
            val (home, out) = choiceOptions(id)

            val first = repository.addPick(id, day, home.id)
            repository.addPick(id, day, out.id)
            repository.addPick(id, day, home.id)
            repository.deleteEntry(first)

            assertThat(repository.observeEntries(id).first().map { it.value })
                .containsExactly(EntryValue.Choice(out.id), EntryValue.Choice(home.id))
                .inOrder()
        }

    @Test
    fun aDayHoldsALimitedNumberOfPicks() =
        runTest {
            val id = repository.createHabit(valid("Meals", meals("Home", "Out")))
            val home = choiceOptions(id).first()
            repeat(HabitLimits.MAX_PICKS_PER_DAY) { repository.addPick(id, day, home.id) }

            assertThat(failure { repository.addPick(id, day, home.id) })
                .isInstanceOf(IllegalArgumentException::class.java)
            repository.addPick(id, day.minusDays(1), home.id)
        }

    @Test
    fun addPickOnlyAppliesToChoiceHabits() =
        runTest {
            val meals = repository.createHabit(valid("Meals", meals("Home", "Out")))
            val walk = repository.createHabit(valid("Walk", KindDraft.Done))
            val home = choiceOptions(meals).first()

            assertThat(failure { repository.addPick(walk, day, home.id) })
                .isInstanceOf(IllegalArgumentException::class.java)
        }

    @Test
    fun entriesBetweenDatesAreInclusiveAndSpanHabits() =
        runTest {
            val pages = repository.createHabit(valid("Pages", KindDraft.Amount("pages")))
            val walk = repository.createHabit(valid("Walk", KindDraft.Done))
            repository.setTotal(pages, day.minusDays(7), 1.0)
            repository.setTotal(pages, day.minusDays(6), 2.0)
            repository.toggleDone(walk, day)
            repository.toggleDone(walk, day.plusDays(1))

            val entries = repository.observeEntries(day.minusDays(6), day).first()

            assertThat(entries.map { it.habitId to it.date }).containsExactly(pages to day.minusDays(6), walk to day)
        }

    @Test
    fun deletingAHabitCascadesToItsOptionsAndEntries() =
        runTest {
            val id = repository.createHabit(valid("Meals", meals("Home", "Out")))
            repository.addPick(id, day, choiceOptions(id).first().id)

            repository.deleteHabit(id)

            assertThat(repository.getHabit(id)).isNull()
            assertThat(repository.observeEntries(day, day).first()).isEmpty()
        }

    @Test
    fun movingAHabitReordersTheRest() =
        runTest {
            val a = repository.createHabit(valid("A", KindDraft.Done))
            val b = repository.createHabit(valid("B", KindDraft.Done))
            val c = repository.createHabit(valid("C", KindDraft.Done))

            repository.moveHabit(c, 0)
            assertThat(repository.observeHabits().first().map { it.id }).containsExactly(c, a, b).inOrder()

            repository.moveHabit(c, 99)
            assertThat(repository.observeHabits().first().map { it.id }).containsExactly(a, b, c).inOrder()
        }

    @Test
    fun observersSeeChanges() =
        runTest {
            val id = repository.createHabit(valid("Walk", KindDraft.Done))

            repository.observeEntries(day, day).test {
                assertThat(awaitItem()).isEmpty()
                repository.toggleDone(id, day)
                assertThat(awaitItem()).hasSize(1)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun changesAreSignalledForHabitsAndEntries() =
        runTest {
            repository.observeChanges().test {
                val id = repository.createHabit(valid("Walk", KindDraft.Done))
                awaitItem()
                repository.toggleDone(id, day)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }
}
