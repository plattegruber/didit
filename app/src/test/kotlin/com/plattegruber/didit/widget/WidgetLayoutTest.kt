package com.plattegruber.didit.widget

import com.google.common.truth.Truth.assertThat
import com.plattegruber.didit.core.domain.model.ChoiceOption
import com.plattegruber.didit.core.domain.model.Habit
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.core.domain.model.OptionId
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.core.domain.viz.WindowSummary
import com.plattegruber.didit.testing.DesignSamples
import com.plattegruber.didit.ui.text.SummaryText
import com.plattegruber.didit.ui.viz.HabitHistory
import com.plattegruber.didit.widget.WidgetConfig.Element
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.junit.Test

/** Text measured as if every character were [CHAR] em wide and a line [LINE] em tall, at sp = dp. */
private class FakeType(private val scale: Float = 1f) : TextMetrics {
    override fun width(text: String, role: TextRole) = text.length * role.sizeSp * scale * CHAR

    override fun lineHeight(role: TextRole) = role.sizeSp * scale * LINE

    companion object {
        const val CHAR = 0.6f
        const val LINE = 1.2f
    }
}

private object FakeStrings : WidgetStrings {
    override val wordmark = "didit"
    override val noHabits = "Add a habit"
    override val habitMissing = "Deleted"

    override fun range(from: LocalDate, to: LocalDate) = "$from - $to"

    override fun weeks(count: Int) = "$count weeks"

    override fun summary(habit: Habit, summary: WindowSummary) = SummaryText("42", habit.kind.unit)

    override fun row(habit: Habit, summary: SummaryText) = "${habit.name}: ${summary.value}"

    override fun overview(dayCount: Int) = "last $dayCount days"

    override fun history(habit: Habit, dayCount: Int) = "${habit.name}, last $dayCount days"
}

class WidgetLayoutTest {
    private fun layout(config: WidgetConfig = WidgetConfig(), type: TextMetrics = FakeType()) =
        WidgetLayout(config, type, FakeStrings, Locale.US, DayOfWeek.MONDAY)

    private val all = WidgetModel.AllHabits(DesignSamples.all, DesignSamples.TODAY)

    private fun WidgetDrawing.words(): List<Mark.Words> = marks.filterIsInstance<Mark.Words>()

    private fun WidgetDrawing.texts(): List<String> = words().map { it.text }

    @Test
    fun `every habit gets a row that logs it, under a header that opens the app`() {
        val drawing = layout().layout(all, width = 366f, height = 290f)

        assertThat(drawing.hits.map { it.tap })
            .containsExactly(
                Tap.OpenApp,
                Tap.Log(DesignSamples.meals),
                Tap.Log(DesignSamples.pages),
                Tap.Log(DesignSamples.coffee),
                Tap.Log(DesignSamples.walk),
                Tap.OpenApp,
            ).inOrder()
        assertThat(drawing.texts()).containsAtLeast("didit", "Meals", "Pages", "Coffee", "Walk")
        assertThat(drawing.scale).isEqualTo(1f)
    }

    @Test
    fun `every choice label is drawn, in its own colour`() {
        val drawing = layout().layout(all, 366f, 290f)
        val legend = drawing.words().filter { it.role == TextRole.Caption && it.swatch != null }

        assertThat(legend.map { it.text to it.swatch })
            .containsExactly("home" to Swatch.Ink, "takeout" to Swatch.Blue, "out" to Swatch.Amber)
            .inOrder()
    }

    @Test
    fun `no text spills past the widget's edges`() {
        val many = (1..4).map { ChoiceOption(OptionId(it.toLong()), "option$it", Swatch.entries[it]) }
        val habit = Habit(HabitId(9), "A rather long habit", HabitKind.Choice("things", many), Instant.EPOCH)
        val model = WidgetModel.AllHabits(listOf(HabitHistory(habit, emptyMap())), DesignSamples.TODAY)
        val type = FakeType(scale = 1.3f)
        val drawing = layout(WidgetConfig(textSize = WidgetConfig.TextSize.ExtraLarge), type).layout(model, 250f, 200f)

        for (word in drawing.words()) {
            val width = type.width(word.text, word.role)
            val left =
                when (word.align) {
                    Align.Start -> word.x
                    Align.Center -> word.x - width / 2
                    Align.End -> word.x - width
                }
            // Names may be wider than their column only if the column is at its widest.
            if (word.role != TextRole.Name) {
                assertThat(left).isAtLeast(0f)
                assertThat(left + width).isAtMost(250f + 0.01f)
            }
        }
        assertThat(drawing.words().filter { it.swatch != null }.map { it.text })
            .containsExactly("option1", "option2", "option3", "option4")
    }

    @Test
    fun `rows grow to fit large text, and the whole drawing shrinks rather than clip`() {
        val roomy = layout().layout(all, 366f, 290f)
        val crowded = layout(type = FakeType(scale = 2f)).layout(all, 366f, 290f)

        assertThat(roomy.scale).isEqualTo(1f)
        assertThat(crowded.scale).isLessThan(1f)
        val rows = crowded.marks.filterIsInstance<Mark.Rows>().single()
        val natural = rows.top + DesignSamples.all.size * rows.rowHeight + rows.lettersHeight
        assertThat(natural * crowded.scale).isAtMost(290f + 0.01f)
        // Laid out wider before shrinking, so the shrunk drawing still reaches the right edge.
        val right = crowded.words().filter { it.align == Align.End }.maxOf { it.x }
        assertThat(right * crowded.scale).isWithin(0.5f).of(366f)
    }

    @Test
    fun `hidden parts are left out`() {
        val config =
            WidgetConfig(
                hidden = setOf(Element.Header, Element.DateRange, Element.Legend, Element.Captions, Element.Weekdays),
            )
        val drawing = layout(config).layout(all, 366f, 290f)

        assertThat(drawing.texts()).containsNoneOf("didit", "home", "takeout", "meals", "pages")
        assertThat(drawing.marks.filterIsInstance<Mark.Dot>()).isEmpty()
        assertThat(drawing.hits.first().tap).isEqualTo(Tap.Log(DesignSamples.meals))
        assertThat(drawing.marks.filterIsInstance<Mark.Rows>().single().lettersHeight).isEqualTo(0f)
    }

    @Test
    fun `a fixed number of days is honoured`() {
        val drawing = layout(WidgetConfig(days = WidgetConfig.Days.OneWeek)).layout(all, 366f, 290f)

        assertThat(drawing.marks.filterIsInstance<Mark.Rows>().single().scene.days).hasSize(7)
        assertThat(drawing.description).isEqualTo("last 7 days")
    }

    @Test
    fun `one habit shows as a strip when wide and a calendar when tall`() {
        val one = WidgetModel.OneHabit(DesignSamples.mealsHistory, DesignSamples.TODAY)

        val wide = layout().layout(one, 366f, 110f)
        val tall = layout().layout(one, 250f, 250f)

        assertThat(wide.marks.filterIsInstance<Mark.Strip>()).hasSize(1)
        assertThat(tall.marks.filterIsInstance<Mark.Calendar>()).hasSize(1)
        assertThat(wide.hits.single().tap).isEqualTo(Tap.Log(DesignSamples.meals))
        assertThat(tall.words().filter { it.swatch != null }.map { it.text }).containsExactly("home", "takeout", "out")
    }

    @Test
    fun `empty states say why and where a tap goes`() {
        val none = layout().layout(WidgetModel.NoHabits, 300f, 200f)
        val missing = layout().layout(WidgetModel.HabitMissing, 300f, 200f)

        assertThat(none.texts()).containsExactly("Add a habit")
        assertThat(none.hits.single().tap).isEqualTo(Tap.OpenApp)
        assertThat(missing.hits.single().tap).isEqualTo(Tap.Configure)
    }
}
