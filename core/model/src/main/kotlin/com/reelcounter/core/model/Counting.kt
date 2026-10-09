package com.reelcounter.core.model

import java.time.LocalDate

/** One counted reel. Holds no content: which app, when, and in which sitting. */
data class ReelEvent(
    val app: ReelApp,
    val timeMillis: Long,
    val surfaceSessionId: Long,
    val estimated: Boolean,
)

data class DaySummary(
    val date: LocalDate,
    val perApp: Map<ReelApp, Int>,
) {
    val total: Int get() = perApp.values.sum()
}

/** Whether the rules pack can count an app right now. */
enum class CountingStatus {
    /** A rule matched this app version: exact counts. */
    ACTIVE,

    /** No rule matched; the generic fallback is counting and results are marked estimated. */
    ESTIMATED,

    /** The rules pack marks this app version as broken: we show "paused" instead of wrong numbers. */
    PAUSED,
}

/** Is the accessibility service actually running? */
enum class CounterHealth {
    HEALTHY,

    /** The user has not turned the service on (or turned it off). */
    NOT_ENABLED,

    /** Turned on in Settings, but the system has not bound it: the classic "counter stopped" failure. */
    ENABLED_NOT_RUNNING,
}

/** Rough watch time. We count reels, not seconds, so every minutes figure is shown with "≈". */
object WatchTime {
    /** Average seconds people spend on one reel or short. */
    const val SECONDS_PER_REEL = 27

    fun minutes(reels: Int): Int = if (reels <= 0) 0 else ((reels * SECONDS_PER_REEL) / 60f).toInt().coerceAtLeast(1)
}

/** A continuous run of reels in one app, for the Day detail list. */
data class Sitting(
    val app: ReelApp,
    val startMillis: Long,
    val endMillis: Long,
    val reels: Int,
)
