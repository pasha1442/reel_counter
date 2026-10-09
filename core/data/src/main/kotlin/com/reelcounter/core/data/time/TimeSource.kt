package com.reelcounter.core.data.time

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Wall clock and local calendar days. Reads the zone on every call so travel is handled. */
@Singleton
open class TimeSource @Inject constructor() {
    open fun clock(): Clock = Clock.systemDefaultZone()

    fun now(): Long = clock().millis()

    fun today(): LocalDate = LocalDate.now(clock())

    fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(clock().zone).toLocalDate()

    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(clock().zone).toInstant().toEpochMilli()

    /** Emits today's date now and again just after each midnight, so screens roll over on their own. */
    val todayFlow: Flow<LocalDate> = flow {
        while (true) {
            val now = ZonedDateTime.now(clock())
            emit(now.toLocalDate())
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000) + 500)
        }
    }.distinctUntilChanged()
}
