package com.plattegruber.didit.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.plattegruber.didit.core.domain.stats.stats
import com.plattegruber.didit.testing.DesignSamples
import com.plattegruber.didit.ui.history.HistoryContent
import com.plattegruber.didit.ui.history.HistoryState
import com.plattegruber.didit.ui.home.HomeContent
import com.plattegruber.didit.ui.home.HomeRow
import com.plattegruber.didit.ui.home.HomeState
import com.plattegruber.didit.ui.log.LogContent
import com.plattegruber.didit.ui.log.LogTarget
import com.plattegruber.didit.ui.theme.DiditTheme
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val SCREENSHOTS = "src/main/play/listings/en-US/graphics/phone-screenshots"

/** Play's phone screenshots: 1170 x 2340, inside its 2:1 aspect limit. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h780dp-night-xxhdpi")
class StoreScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent { DiditTheme(darkTheme = true) { content() } }
        compose.onRoot().captureRoboImage("$SCREENSHOTS/$name.png")
    }

    @Test
    fun home() = capture("1-home") {
        HomeContent(
            state = HomeState.Ready(DesignSamples.TODAY, DesignSamples.all.map { HomeRow(it, emptyList()) }),
            actions = NoActions,
            onOpenHistory = {},
            onCreateHabit = {},
        )
    }

    @Test
    fun logChoice() = capture("2-log-choice") {
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
    fun logAmount() = capture("3-log-amount") {
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
    fun history() = capture("4-history") {
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

    @Composable
    private fun Sheet(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(DiditTheme.colors.scrim), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.background(DiditTheme.colors.surface)) { content() }
        }
    }
}
