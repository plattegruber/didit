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
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Test

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
        val config =
            WidgetConfig(
                WidgetConfig.Target.OneHabit(HabitId(7)),
                card = true,
                ink = WidgetConfig.Ink.Dark,
                palette = WidgetConfig.Palette.Launcher,
            )
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
    fun `every habit with nothing set up says so`() = runTest {
        WidgetModel.observe(repository, WidgetConfig.Target.AllHabits, today).test {
            assertThat(awaitItem()).isEqualTo(WidgetModel.NoHabits)
        }
    }

    @Test
    fun `every habit carries each one's recent days`() = runTest {
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
    fun `one habit shows only its own days`() = runTest {
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
    fun `a deleted habit is reported missing`() = runTest {
        WidgetModel.observe(repository, WidgetConfig.Target.OneHabit(HabitId(404)), today).test {
            assertThat(awaitItem()).isEqualTo(WidgetModel.HabitMissing)
        }
    }

    /** Monospaced: every character is 0.6 em wide, and a line is 1.2 em tall. */
    private val mono =
        object : TextMeasure {
            override fun width(text: String, sizeSp: Float, face: Face) = text.length * sizeSp * 0.6f

            override fun lineHeight(sizeSp: Float, face: Face) = sizeSp * 1.2f
        }

    @Test
    fun `labels break onto as few lines as fit`() {
        val lines = breakLines(listOf("home", "takeout", "out"), width = 70f, gap = 5f) { it.length * 6f }
        assertThat(lines).containsExactly(listOf("home"), listOf("takeout", "out")).inOrder()
        assertThat(breakLines(listOf("a", "b"), 72f, 5f) { 6f }).containsExactly(listOf("a", "b"))
        assertThat(breakLines(emptyList(), 72f, 5f) { 6f }).isEmpty()
    }

    @Test
    fun `a legend that fits keeps its full size`() {
        val fit = fitLegend(listOf("home", "takeout", "out"), width = 72f, height = 30f, measure = mono)
        assertThat(fit.sizeSp).isEqualTo(10f)
        assertThat(fit.lines.flatten()).containsExactly("home", "takeout", "out").inOrder()
    }

    @Test
    fun `a legend that would be clipped shrinks until every label shows`() {
        // Two lines of four labels need 24dp at 10sp, more than the 22dp of room; at 9sp, 21.6dp.
        val labels = listOf("home", "takeout", "out", "work")
        val fit = fitLegend(labels, width = 72f, height = 22f, measure = mono)
        assertThat(fit.sizeSp).isEqualTo(9f)
        assertThat(fit.lines.flatten()).containsExactlyElementsIn(labels).inOrder()
    }

    @Test
    fun `a label wider than the column shrinks it too`() {
        val fit = fitLegend(listOf("twelve chars"), width = 70f, height = 40f, measure = mono)
        assertThat(fit.sizeSp).isEqualTo(9f)
    }

    @Test
    fun `when nothing fits, every label still shows at the smallest size`() {
        val labels = listOf("breakfast", "lunch", "dinner", "snacks")
        val fit = fitLegend(labels, width = 72f, height = 5f, measure = mono)
        assertThat(fit.sizeSp).isEqualTo(8f)
        assertThat(fit.lines.flatten()).containsExactlyElementsIn(labels).inOrder()
    }
}
