package com.plattegruber.didit.ui.editor

import com.google.common.truth.Truth.assertThat
import com.plattegruber.didit.core.domain.draft.DraftError
import com.plattegruber.didit.core.domain.draft.HabitLimits
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.testing.DesignSamples
import com.plattegruber.didit.testing.FakeHabitRepository
import com.plattegruber.didit.testing.MainDispatcherRule
import org.junit.Rule
import org.junit.Test

class EditorViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeHabitRepository()

    private fun newEditor() = EditorViewModel(null, repository)

    @Test
    fun `a new habit starts as a choice with ink, blue and amber options`() {
        val editor = newEditor()

        assertThat(editor.state.isNew).isTrue()
        assertThat(editor.state.kind).isEqualTo(KindChoice.Choice)
        assertThat(
            editor.state.options.map { it.swatch },
        ).containsExactly(Swatch.Ink, Swatch.Blue, Swatch.Amber).inOrder()
        assertThat(editor.hasChanges).isFalse()
    }

    @Test
    fun `saving an invalid draft shows errors and saves nothing`() {
        val editor = newEditor()

        editor.save()

        assertThat(editor.state.showErrors).isTrue()
        assertThat(editor.errors).containsExactly(DraftError.NameBlank, DraftError.TooFewOptions)
        assertThat(repository.currentHabits).isEmpty()
        assertThat(editor.state.finished).isFalse()
    }

    @Test
    fun `blank choice rows are left out rather than blocking the save`() {
        val editor = newEditor()
        editor.setName("Meals")
        val (home, takeout) = editor.state.options
        editor.setOptionLabel(home.key, "Home")
        editor.setOptionLabel(takeout.key, "Takeout")
        editor.setUnit("meals")

        editor.save()

        val habit = repository.currentHabits.single()
        val kind = habit.kind as HabitKind.Choice
        assertThat(habit.name).isEqualTo("Meals")
        assertThat(kind.unit).isEqualTo("meals")
        assertThat(kind.options.map { it.label to it.swatch })
            .containsExactly("Home" to Swatch.Ink, "Takeout" to Swatch.Blue)
            .inOrder()
        assertThat(editor.state.finished).isTrue()
    }

    @Test
    fun `other kinds save with their unit`() {
        val editor = newEditor()
        editor.setName("Coffee")
        editor.setKind(KindChoice.Count)
        editor.setUnit("cups")

        editor.save()

        assertThat(repository.currentHabits.single().kind).isEqualTo(HabitKind.Count("cups"))
    }

    @Test
    fun `adding options takes the next unused colour and stops at the limit`() {
        val editor = newEditor()

        editor.addOption()
        assertThat(
            editor.state.options
                .last()
                .swatch,
        ).isEqualTo(Swatch.Coral)
        assertThat(editor.state.options).hasSize(HabitLimits.MAX_OPTIONS)
        assertThat(editor.canAddOption).isFalse()

        editor.addOption()
        assertThat(editor.state.options).hasSize(HabitLimits.MAX_OPTIONS)
    }

    @Test
    fun `tapping a colour moves to the next one in the palette`() {
        val editor = newEditor()
        val first = editor.state.options.first()

        editor.cycleOptionSwatch(first.key)

        assertThat(
            editor.state.options
                .first()
                .swatch,
        ).isEqualTo(Swatch.Blue)
    }

    @Test
    fun `removing an option keeps the others in order`() {
        val editor = newEditor()
        val (_, takeout, out) = editor.state.options

        editor.removeOption(takeout.key)

        assertThat(
            editor.state.options.map { it.key },
        ).containsExactly(
            editor.state.options
                .first()
                .key,
            out.key,
        ).inOrder()
    }

    @Test
    fun `an existing habit loads with its kind locked`() {
        repository.seed(DesignSamples.meals)

        val editor = EditorViewModel(DesignSamples.meals.id.value, repository)
        editor.setKind(KindChoice.Done)

        assertThat(editor.state.isNew).isFalse()
        assertThat(editor.state.loading).isFalse()
        assertThat(editor.state.name).isEqualTo("Meals")
        assertThat(editor.state.kind).isEqualTo(KindChoice.Choice)
        assertThat(editor.state.options.map { it.label }).containsExactly("Home", "Takeout", "Out").inOrder()
        assertThat(editor.hasChanges).isFalse()
    }

    @Test
    fun `editing an existing habit updates it in place`() {
        repository.seed(DesignSamples.pages)
        val editor = EditorViewModel(DesignSamples.pages.id.value, repository)

        editor.setName("Reading")
        assertThat(editor.hasChanges).isTrue()
        editor.save()

        assertThat(repository.currentHabits.single().name).isEqualTo("Reading")
        assertThat(editor.state.finished).isTrue()
    }

    @Test
    fun `deleting finishes the editor as deleted`() {
        repository.seed(DesignSamples.walk)
        val editor = EditorViewModel(DesignSamples.walk.id.value, repository)

        editor.delete()

        assertThat(repository.currentHabits).isEmpty()
        assertThat(editor.state.deleted).isTrue()
    }

    @Test
    fun `a habit that no longer exists closes the editor`() {
        val editor = EditorViewModel(404L, repository)

        assertThat(editor.state.finished).isTrue()
    }
}
