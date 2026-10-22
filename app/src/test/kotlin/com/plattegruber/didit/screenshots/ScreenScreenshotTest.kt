package com.plattegruber.didit.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.plattegruber.didit.core.domain.model.EntryId
import com.plattegruber.didit.core.domain.model.HabitId
import com.plattegruber.didit.core.domain.model.OptionId
import com.plattegruber.didit.core.domain.model.Swatch
import com.plattegruber.didit.core.domain.stats.stats
import com.plattegruber.didit.testing.DesignSamples
import com.plattegruber.didit.ui.editor.EditorContent
import com.plattegruber.didit.ui.editor.EditorState
import com.plattegruber.didit.ui.editor.KindChoice
import com.plattegruber.didit.ui.editor.OptionRow
import com.plattegruber.didit.ui.history.HistoryContent
import com.plattegruber.didit.ui.history.HistoryState
import com.plattegruber.didit.ui.home.HomeContent
import com.plattegruber.didit.ui.home.HomeRow
import com.plattegruber.didit.ui.home.HomeState
import com.plattegruber.didit.ui.log.LogActions
import com.plattegruber.didit.ui.log.LogContent
import com.plattegruber.didit.ui.log.LogTarget
import com.plattegruber.didit.ui.theme.DiditTheme
import com.plattegruber.didit.widget.LauncherTheme
import com.plattegruber.didit.widget.WidgetConfig
import com.plattegruber.didit.widget.WidgetConfigContent
import com.plattegruber.didit.widget.WidgetModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal object NoActions : LogActions {
    override fun toggleDone(habitId: HabitId, date: LocalDate) = Unit

    override fun setTotal(habitId: HabitId, date: LocalDate, total: Double) = Unit

    override fun addPick(habitId: HabitId, date: LocalDate, optionId: OptionId) = Unit

    override fun removeEntry(id: EntryId) = Unit
}

/** Every screen at the design's 390 x 844, in the design's dark theme. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-night-xxhdpi")
class ScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(content: @Composable () -> Unit) {
        compose.setContent { DiditTheme(darkTheme = true) { content() } }
        compose.onRoot().captureRoboImage()
    }

    @Test
    fun home() = capture {
        HomeContent(
            state = HomeState.Ready(DesignSamples.TODAY, DesignSamples.all.map { HomeRow(it, emptyList()) }),
            actions = NoActions,
            onOpenHistory = {},
            onCreateHabit = {},
        )
    }

    @Test
    fun homeEmpty() = capture {
        HomeContent(HomeState.Ready(DesignSamples.TODAY, emptyList()), NoActions, {}, {})
    }

    @Composable
    private fun Sheet(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(DiditTheme.colors.scrim), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.background(DiditTheme.colors.surface)) { content() }
        }
    }

    @Test
    fun logChoice() = capture {
        Sheet {
            LogContent(
                LogTarget(DesignSamples.meals, DesignSamples.TODAY, DesignSamples.mealsToday),
                DesignSamples.TODAY,
                NoActions,
                onClose = {},
            )
        }
    }

    @Test
    fun logAmount() = capture {
        Sheet {
            LogContent(
                LogTarget(
                    DesignSamples.pages,
                    DesignSamples.TODAY,
                    DesignSamples.amountToday(DesignSamples.pages, 12.0),
                ),
                DesignSamples.TODAY,
                NoActions,
                onClose = {},
            )
        }
    }

    @Test
    fun logCount() = capture {
        Sheet {
            LogContent(
                LogTarget(
                    DesignSamples.coffee,
                    DesignSamples.TODAY,
                    DesignSamples.amountToday(DesignSamples.coffee, 1.0),
                ),
                DesignSamples.TODAY,
                NoActions,
                onClose = {},
            )
        }
    }

    @Test
    fun logDone() = capture {
        Sheet {
            LogContent(
                LogTarget(
                    DesignSamples.walk,
                    DesignSamples.TODAY,
                    emptyList(),
                ),
                DesignSamples.TODAY,
                NoActions,
                onClose = {
                },
            )
        }
    }

    @Test
    fun editorNew() = capture {
        EditorContent(
            state =
                EditorState(
                    isNew = true,
                    options =
                        listOf(
                            OptionRow(0, null, "", Swatch.Ink),
                            OptionRow(1, null, "", Swatch.Blue),
                            OptionRow(2, null, "", Swatch.Amber),
                        ),
                ),
            errors = emptySet(),
            canAddOption = true,
            onBack = {},
            onNameChange = {},
            onKindChange = {},
            onUnitChange = {},
            onOptionLabelChange = { _, _ -> },
            onOptionSwatchClick = {},
            onAddOption = {},
            onSave = {},
            onDelete = {},
        )
    }

    @Test
    fun editorAmount() = capture {
        EditorContent(
            state = EditorState(isNew = false, name = "Pages", kind = KindChoice.Amount, unit = "pages"),
            errors = emptySet(),
            canAddOption = true,
            onBack = {},
            onNameChange = {},
            onKindChange = {},
            onUnitChange = {},
            onOptionLabelChange = { _, _ -> },
            onOptionSwatchClick = {},
            onAddOption = {},
            onSave = {},
            onDelete = {},
        )
    }

    @Test
    fun history() = capture {
        val history = DesignSamples.mealsHistory
        HistoryContent(
            state =
                HistoryState.Ready(
                    history = history,
                    entries = emptyList(),
                    stats = history.habit.stats(history.days, DesignSamples.TODAY),
                    today = DesignSamples.TODAY,
                    month = YearMonth.from(DesignSamples.TODAY),
                    canGoBack = true,
                    canGoForward = false,
                ),
            actions = NoActions,
            onBack = {},
            onEdit = {},
            onPreviousMonth = {},
            onNextMonth = {},
            onAddWidget = {},
        )
    }

    @Test
    fun widgetConfigMatchLauncher() = capture {
        WidgetConfigContent(
            habits = DesignSamples.all.map { it.habit },
            config = WidgetConfig(palette = WidgetConfig.Palette.Launcher),
            launcher =
                LauncherTheme(
                    accent = 0xFF7FD1C7.toInt(),
                    text = 0xFFFFFFFF.toInt(),
                    surface = 0xFF22272B.toInt(),
                    heardAt = NOW.minusSeconds(120),
                ),
            model = WidgetModel.AllHabits(DesignSamples.all, DesignSamples.TODAY),
            widgetSize = DpSize(366.dp, 290.dp),
            onChange = {},
            onOpenApp = {},
            onDone = {},
            now = NOW,
        )
    }

    /** Every setting at once, on a screen tall enough to show them all. */
    @Test
    @Config(qualifiers = "w390dp-h2400dp-night-xxhdpi")
    fun widgetConfigEverything() = capture {
        WidgetConfigContent(
            habits = DesignSamples.all.map { it.habit },
            config =
                WidgetConfig(
                    card = true,
                    palette = WidgetConfig.Palette.Custom,
                    custom = WidgetConfig.CustomColors(text = 0xFFF4F0E9.toInt(), glyphs = 0xFFE8A54B.toInt()),
                    cardOpacity = 80,
                    font = WidgetConfig.TextFont.SpaceGrotesk,
                    labelFont = WidgetConfig.LabelFont.JetBrainsMono,
                    textSize = WidgetConfig.TextSize.Large,
                    hidden = setOf(WidgetConfig.Element.Captions),
                ),
            launcher = LauncherTheme.None,
            model = WidgetModel.AllHabits(DesignSamples.all, DesignSamples.TODAY),
            widgetSize = DpSize(366.dp, 290.dp),
            onChange = {},
            onOpenApp = {},
            onDone = {},
            now = NOW,
        )
    }

    private companion object {
        val NOW: Instant = DesignSamples.TODAY.atTime(9, 0).toInstant(ZoneOffset.UTC)
    }
}
