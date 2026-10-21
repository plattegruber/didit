package com.plattegruber.didit.testing

import com.plattegruber.didit.core.domain.time.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Runs `viewModelScope` work on a test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** A clock that stays put until told otherwise, in UTC. */
class FixedClock(var instant: Instant = DesignSamples.TODAY.atTime(9, 0).toInstant(ZoneOffset.UTC)) : AppClock {
    override fun now(): Instant = instant

    override fun zone(): ZoneId = ZoneOffset.UTC

    fun moveTo(date: LocalDate) {
        instant = date.atTime(9, 0).toInstant(ZoneOffset.UTC)
    }
}
