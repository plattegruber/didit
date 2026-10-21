package com.plattegruber.didit.ui.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.plattegruber.didit.core.domain.model.DayValue
import com.plattegruber.didit.core.domain.model.Entry
import com.plattegruber.didit.core.domain.model.EntryId
import com.plattegruber.didit.core.domain.model.EntryValue
import com.plattegruber.didit.testing.DesignSamples
import com.plattegruber.didit.testing.FakeHabitRepository
import com.plattegruber.didit.testing.FixedClock
import com.plattegruber.didit.testing.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeHabitRepository()
    private val clock = FixedClock()
    private val today = DesignSamples.TODAY

    private fun entry(id: Long, daysAgo: Long, value: EntryValue) =
        Entry(EntryId(id), DesignSamples.pages.id, today.minusDays(daysAgo), value, Instant.EPOCH)

    @Test
    fun `rows hold each habit's last two weeks`() = runTest {
        repository.seed(
            DesignSamples.pages,
            entry(1, 0, EntryValue.Amount(12.0)),
            entry(2, 13, EntryValue.Amount(5.0)),
            entry(3, 14, EntryValue.Amount(99.0)),
        )
        repository.seed(DesignSamples.walk)
        val viewModel = HomeViewModel(repository, clock)

        viewModel.state.test {
            val ready = awaitItem() as HomeState.Ready
            assertThat(ready.today).isEqualTo(today)
            assertThat(ready.rows.map { it.habit })
                .containsExactly(DesignSamples.pages, DesignSamples.walk)
                .inOrder()
            assertThat(
                ready.rows
                    .first()
                    .history.days,
            ).containsExactly(
                today,
                DayValue.Amount(12.0),
                today.minusDays(13),
                DayValue.Amount(5.0),
            )
            assertThat(
                ready.rows
                    .first()
                    .entriesOn(today)
                    .map { it.id },
            ).containsExactly(EntryId(1))
        }
    }

    @Test
    fun `logging through the sheet reaches the repository and the rows`() = runTest {
        repository.seed(DesignSamples.walk)
        val viewModel = HomeViewModel(repository, clock)

        viewModel.state.test {
            awaitItem()
            viewModel.logActions.toggleDone(DesignSamples.walk.id, today)
            val ready = awaitItem() as HomeState.Ready
            assertThat(
                ready.rows
                    .single()
                    .history.days,
            ).containsExactly(today, DayValue.Done)
        }
    }

    @Test
    fun `coming back the next day moves the window on`() = runTest {
        repository.seed(DesignSamples.pages)
        val viewModel = HomeViewModel(repository, clock)

        viewModel.state.test {
            assertThat((awaitItem() as HomeState.Ready).today).isEqualTo(today)
            clock.moveTo(today.plusDays(1))
            viewModel.refreshDay()
            assertThat((awaitItem() as HomeState.Ready).today).isEqualTo(today.plusDays(1))
        }
    }

    @Test
    fun `no habits is an empty, ready home`() = runTest {
        HomeViewModel(repository, clock).state.test {
            assertThat(awaitItem()).isEqualTo(HomeState.Ready(today, emptyList()))
        }
    }
}
