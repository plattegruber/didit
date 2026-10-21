package com.plattegruber.didit.core.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The single source of "now". The zone is read on every call so that travelling across
 * time zones moves "today" without restarting the process.
 */
interface AppClock {
    fun now(): Instant

    fun zone(): ZoneId
}

fun AppClock.today(): LocalDate = now().atZone(zone()).toLocalDate()

object SystemClock : AppClock {
    override fun now(): Instant = Instant.now()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
