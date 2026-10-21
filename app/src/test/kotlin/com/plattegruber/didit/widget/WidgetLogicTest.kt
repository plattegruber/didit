package com.plattegruber.didit.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.plattegruber.didit.core.domain.model.DayValue
import com.plattegruber.didit.core.domain.model.Entry
import com.plattegruber.didit.core.domain.model.EntryId
import com.plattegruber.didit.core.domain.model.EntryValue
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.testing.DesignSamples
import com.plattegruber.didit.testing.FakeHabitRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class WidgetLogicTest {
    private val repository = FakeHabitRepository()
    private val today = DesignSamples.TODAY

    @Test
    fun `a fresh widget shows every habit on the wallpaper`() {
        assertThat(WidgetConfig.from(mutablePreferencesOf())).isEqualTo(
            WidgetConfig(WidgetConfig.Target.AllHabits, card = false, ink = WidgetConfig.Ink.Auto),
        )
    }

    @Test
    fun `config survives a round trip through preferences`() {
        val config = WidgetConfig(WidgetConfig.Target.OneHabit(HabitId(7)), card = true, ink = WidgetConfig.Ink.Dark)
        val preferences = mutablePreferencesOf()

        config.writeTo(preferences)

        assertThat(WidgetConfig.from(preferences)).isEqualTo(config)
    }

    @Test
    fun `switching back to every habit forgets the single one`() {
        val preferences = mutablePreferencesOf()
        WidgetConfig(WidgetConfig.Target.OneHabit(HabitId(7))).writeTo(preferences)

        WidgetConfig().writeTo(preferences)

        assertThat(WidgetConfig.from(preferences).target).isEqualTo(WidgetConfig.Target.AllHabits)
    }

    @Test
    fun `every habit with nothing set up says so`() =
        runTest {
            WidgetModel.observe(repository, WidgetConfig.Target.AllHabits, today).test {
                assertThat(awaitItem()).isEqualTo(WidgetModel.NoHabits)
            }
        }

    @Test
    fun `every habit carries each one's recent days`() =
        runTest {
            repository.seed(
                DesignSamples.pages,
                Entry(EntryId(1), DesignSamples.pages.id, today, EntryValue.Amount(12.0), Instant.EPOCH),
                Entry(
                    EntryId(2),
                    DesignSamples.pages.id,
                    today.minusDays(WidgetModel.MAX_DAYS),
                    EntryValue.Amount(9.0),
                    Instant.EPOCH,
                ),
            )
            repository.seed(DesignSamples.walk)

            WidgetModel.observe(repository, WidgetConfig.Target.AllHabits, today).test {
                val model = awaitItem() as WidgetModel.AllHabits
                assertThat(
                    model.histories.map { it.habit },
                ).containsExactly(DesignSamples.pages, DesignSamples.walk).inOrder()
                assertThat(model.histories.first().days).containsExactly(today, DayValue.Amount(12.0))
            }
        }

    @Test
    fun `one habit shows only its own days`() =
        runTest {
            repository.seed(
                DesignSamples.walk,
                Entry(EntryId(1), DesignSamples.walk.id, today, EntryValue.Done, Instant.EPOCH),
            )
            repository.seed(
                DesignSamples.pages,
                Entry(EntryId(2), DesignSamples.pages.id, today, EntryValue.Amount(3.0), Instant.EPOCH),
            )

            WidgetModel.observe(repository, WidgetConfig.Target.OneHabit(DesignSamples.walk.id), today).test {
                val model = awaitItem() as WidgetModel.OneHabit
                assertThat(model.history.days).containsExactly(today, DayValue.Done)
            }
        }

    @Test
    fun `a deleted habit is reported missing`() =
        runTest {
            WidgetModel.observe(repository, WidgetConfig.Target.OneHabit(HabitId(404)), today).test {
                assertThat(awaitItem()).isEqualTo(WidgetModel.HabitMissing)
            }
        }

    @Test
    fun `legends wrap onto as few lines as fit`() {
        assertThat(legendLines(listOf("home", "takeout", "out"), width = 72f))
            .containsExactly(listOf("home"), listOf("takeout", "out"))
            .inOrder()
        assertThat(legendLines(listOf("a", "b"), width = 72f)).containsExactly(listOf("a", "b"))
        assertThat(legendLines(emptyList(), width = 72f)).isEmpty()
    }
}
