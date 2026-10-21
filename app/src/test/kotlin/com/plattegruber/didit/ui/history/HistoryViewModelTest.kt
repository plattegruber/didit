package com.plattegruber.didit.ui.history

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.plattegruber.didit.core.domain.model.Entry
import com.plattegruber.didit.core.domain.model.EntryId
import com.plattegruber.didit.core.domain.model.EntryValue
import com.plattegruber.didit.testing.DesignSamples
import com.plattegruber.didit.testing.FakeHabitRepository
import com.plattegruber.didit.testing.FixedClock
import com.plattegruber.didit.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class HistoryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeHabitRepository()
    private val clock = FixedClock()
    private val today = DesignSamples.TODAY
    private val thisMonth = YearMonth.from(today)

    /** Walk, created two months ago, with one day logged three months ago. */
    private fun seedWalk() {
        val created = today.minusMonths(2).atStartOfDay().toInstant(ZoneOffset.UTC)
        repository.seed(
            DesignSamples.walk.copy(createdAt = created),
            Entry(EntryId(1), DesignSamples.walk.id, today.minusMonths(3), EntryValue.Done, Instant.EPOCH),
            Entry(EntryId(2), DesignSamples.walk.id, today, EntryValue.Done, Instant.EPOCH),
        )
    }

    @Test
    fun `shows this month with stats over everything logged`() =
        runTest {
            seedWalk()
            val viewModel = HistoryViewModel(DesignSamples.walk.id.value, repository, clock)

            viewModel.state.test {
                val ready = awaitItem() as HistoryState.Ready
                assertThat(ready.month).isEqualTo(thisMonth)
                assertThat(ready.stats.currentStreak).isEqualTo(1)
                assertThat(ready.canGoForward).isFalse()
                assertThat(ready.canGoBack).isTrue()
                assertThat(ready.entriesOn(today).map { it.id }).containsExactly(EntryId(2))
            }
        }

    @Test
    fun `months go back to the earliest entry and never past this month`() =
        runTest {
            seedWalk()
            val viewModel = HistoryViewModel(DesignSamples.walk.id.value, repository, clock)

            viewModel.state.test {
                awaitItem()
                viewModel.showNextMonth()
                repeat(5) { viewModel.showPreviousMonth() }

                val earliest = expectMostRecentItem() as HistoryState.Ready
                assertThat(earliest.month).isEqualTo(thisMonth.minusMonths(3))
                assertThat(earliest.canGoBack).isFalse()
                assertThat(earliest.canGoForward).isTrue()
            }
        }

    @Test
    fun `a deleted habit reports itself missing`() =
        runTest {
            seedWalk()
            val viewModel = HistoryViewModel(DesignSamples.walk.id.value, repository, clock)

            viewModel.state.test {
                awaitItem()
                repository.deleteHabit(DesignSamples.walk.id)
                assertThat(awaitItem()).isEqualTo(HistoryState.Missing)
            }
        }
}
