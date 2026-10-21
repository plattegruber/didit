package com.plattegruber.didit.core.domain.draft

import com.google.common.truth.Truth.assertThat
import com.plattegruber.didit.core.domain.HOME
import com.plattegruber.didit.core.domain.OUT
import com.plattegruber.didit.core.domain.TAKEOUT
import com.plattegruber.didit.core.domain.habit
import com.plattegruber.didit.core.domain.model.ChoiceOption
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.OptionId
import com.plattegruber.didit.core.domain.model.Swatch
import org.junit.Test

class HabitDraftTest {
    private fun draft(name: String = "Read", kind: KindDraft = KindDraft.Done) = HabitDraft(name, kind)

    private fun options(vararg labels: String, unit: String = "") =
        KindDraft.Choice(unit, labels.map { OptionDraft(null, it, Swatch.Ink) })

    private fun HabitDraft.errors(): Set<DraftError> = (validate() as? DraftValidation.Invalid)?.errors.orEmpty()

    @Test
    fun `a valid draft is trimmed`() {
        val result = draft(name = "  Pages  ", kind = KindDraft.Amount(unit = " pages ")).validate()

        val valid = (result as DraftValidation.Valid).draft
        assertThat(valid.name).isEqualTo("Pages")
        assertThat(valid.kind).isEqualTo(KindDraft.Amount("pages"))
    }

    @Test
    fun `choice labels and unit are trimmed`() {
        val valid = (draft(kind = options(" Home ", "Out", unit = " meals ")).validate() as DraftValidation.Valid).draft

        assertThat(valid.kind).isEqualTo(
            KindDraft.Choice(
                "meals",
                listOf(OptionDraft(null, "Home", Swatch.Ink), OptionDraft(null, "Out", Swatch.Ink)),
            ),
        )
    }

    @Test
    fun `name must not be blank`() {
        assertThat(draft(name = "   ").errors()).containsExactly(DraftError.NameBlank)
    }

    @Test
    fun `name has a length limit`() {
        assertThat(draft(name = "x".repeat(HabitLimits.MAX_NAME_LENGTH)).errors()).isEmpty()
        assertThat(draft(name = "x".repeat(HabitLimits.MAX_NAME_LENGTH + 1)).errors())
            .containsExactly(DraftError.NameTooLong)
    }

    @Test
    fun `units have a length limit for every kind that has one`() {
        val unit = "x".repeat(HabitLimits.MAX_UNIT_LENGTH + 1)

        assertThat(draft(kind = KindDraft.Amount(unit)).errors()).containsExactly(DraftError.UnitTooLong)
        assertThat(draft(kind = KindDraft.Count(unit)).errors()).containsExactly(DraftError.UnitTooLong)
        assertThat(draft(kind = options("a", "b", unit = unit)).errors()).containsExactly(DraftError.UnitTooLong)
    }

    @Test
    fun `choice needs between two and four options`() {
        assertThat(draft(kind = options("Home")).errors()).containsExactly(DraftError.TooFewOptions)
        assertThat(draft(kind = options("a", "b")).errors()).isEmpty()
        assertThat(draft(kind = options("a", "b", "c", "d")).errors()).isEmpty()
        assertThat(draft(kind = options("a", "b", "c", "d", "e")).errors()).containsExactly(DraftError.TooManyOptions)
    }

    @Test
    fun `option labels must be present, short and unique ignoring case`() {
        assertThat(draft(kind = options("Home", " ")).errors()).containsExactly(DraftError.OptionLabelBlank)
        assertThat(draft(kind = options("Home", "x".repeat(HabitLimits.MAX_OPTION_LABEL_LENGTH + 1))).errors())
            .containsExactly(DraftError.OptionLabelTooLong)
        assertThat(draft(kind = options("Home", " home")).errors()).containsExactly(DraftError.DuplicateOptionLabels)
    }

    @Test
    fun `every error is reported at once`() {
        assertThat(draft(name = "", kind = options("")).errors())
            .containsExactly(DraftError.NameBlank, DraftError.TooFewOptions, DraftError.OptionLabelBlank)
    }

    @Test
    fun `kind changes are detected`() {
        assertThat(KindDraft.Done.sameKindAs(HabitKind.Done)).isTrue()
        assertThat(KindDraft.Amount("").sameKindAs(HabitKind.Count(""))).isFalse()
        assertThat(KindDraft.Count("").sameKindAs(HabitKind.Count(""))).isTrue()
        assertThat(options("a", "b").sameKindAs(HabitKind.Choice("", emptyList()))).isTrue()
    }

    @Test
    fun `an existing habit round-trips to a draft without its archived options`() {
        val archived = ChoiceOption(OptionId(9), "Skipped", Swatch.Sage, archived = true)
        val habit = habit(HabitKind.Choice("meals", listOf(HOME, TAKEOUT, OUT, archived)))

        assertThat(habit.toDraft()).isEqualTo(
            HabitDraft(
                name = habit.name,
                kind =
                    KindDraft.Choice(
                        "meals",
                        listOf(HOME, TAKEOUT, OUT).map { OptionDraft(it.id, it.label, it.swatch) },
                    ),
            ),
        )
    }
}
