package com.plattegruber.didit.flows

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.plattegruber.didit.core.data.RoomHabitRepository
import com.plattegruber.didit.core.data.db.DiditDatabase
import com.plattegruber.didit.core.domain.model.EntryValue
import com.plattegruber.didit.core.domain.model.HabitKind
import com.plattegruber.didit.testing.DesignSamples
import com.plattegruber.didit.testing.FixedClock
import com.plattegruber.didit.ui.editor.EditorScreen
import com.plattegruber.didit.ui.editor.EditorViewModel
import com.plattegruber.didit.ui.home.HomeScreen
import com.plattegruber.didit.ui.home.HomeViewModel
import com.plattegruber.didit.ui.theme.DiditTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The core loop, end to end against a real database: make a habit in the editor, then log it
 * from the home screen's sheet.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h844dp-night")
class CreateAndLogFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DiditDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val clock = FixedClock()
    private val repository = RoomHabitRepository(database, clock)

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createAChoiceHabitThenLogTodayFromHome() {
        val editor = EditorViewModel(null, repository)
        val home = HomeViewModel(repository, clock)
        var editing by mutableStateOf(true)
        compose.setContent {
            DiditTheme {
                if (editing) {
                    EditorScreen(viewModel = editor, onClose = { editing = false }, onCloseAfterDelete = {})
                } else {
                    HomeScreen(onOpenHistory = {}, onCreateHabit = {}, viewModel = home)
                }
            }
        }

        compose.onNodeWithContentDescription("Name").performTextInput("Meals")
        compose.onNodeWithContentDescription("Choice 1").performTextInput("Home")
        compose.onNodeWithContentDescription("Choice 2").performTextInput("Out")
        compose.onNodeWithContentDescription("Unit").performTextInput("meals")
        compose.onNodeWithText("Add to widget").performClick()
        assertThat(editor.errors).isEmpty()
        awaitUntil { !editing }

        val habit = runBlocking { repository.observeHabits().first().single() }
        val kind = habit.kind as HabitKind.Choice
        assertThat(habit.name).isEqualTo("Meals")
        assertThat(kind.activeOptions.map { it.label }).containsExactly("Home", "Out").inOrder()

        val row = "Meals: 0 meals. Log today."
        awaitUntil { compose.onAllNodesWithContentDescription(row).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(row).performClick()
        compose.onNodeWithContentDescription("Add Home").performClick()
        compose.onNodeWithContentDescription("Add Out").performClick()

        awaitUntil { runBlocking { repository.observeEntries(habit.id).first().size == 2 } }
        // The sheet redraws a beat after the write lands.
        val secondPick = "Remove Out, number 2"
        awaitUntil { compose.onAllNodesWithContentDescription(secondPick).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(secondPick).assertIsDisplayed()
        val logged = runBlocking { repository.observeEntries(habit.id).first() }
        assertThat(logged.map { it.date }.toSet()).containsExactly(DesignSamples.TODAY)
        assertThat(logged.map { it.value }).containsExactly(
            EntryValue.Choice(kind.activeOptions[0].id),
            EntryValue.Choice(kind.activeOptions[1].id),
        )
    }

    /**
     * Room finishes writes on its own executor and hands the result back through the main looper,
     * which `waitUntil` doesn't pump; `waitForIdle` does.
     */
    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (true) {
            compose.waitForIdle()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "Condition not met within $TIMEOUT_MILLIS ms" }
            Thread.sleep(POLL_MILLIS)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 20L
    }
}
