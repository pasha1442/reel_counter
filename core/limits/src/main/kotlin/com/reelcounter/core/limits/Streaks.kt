package com.reelcounter.core.limits

import java.time.LocalDate

data class StreakSummary(
    /** Days in a row at or under the limit, ending today (today counts while still under). */
    val current: Int,
    val best: Int,
    /** The lowest-count finished day in the window, if any. */
    val bestDay: LocalDate?,
    val bestDayCount: Int?,
)

object Streaks {
    /**
     * [totals] maps a day to its reel total; days missing from the map had no reels.
     * Only days from [firstDay] (first day the counter ran) up to [today] are considered.
     */
    fun summarize(totals: Map<LocalDate, Int>, limit: Int, firstDay: LocalDate, today: LocalDate): StreakSummary {
        if (firstDay.isAfter(today)) return StreakSummary(0, 0, null, null)

        val days = generateSequence(firstDay) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        var best = 0
        var run = 0
        for (day in days) {
            val underLimit = (totals[day] ?: 0) <= limit
            run = if (underLimit) run + 1 else 0
            if (underLimit) best = maxOf(best, run)
        }

        var current = 0
        for (day in days.asReversed()) {
            if ((totals[day] ?: 0) <= limit) current++ else break
        }

        val finished = days.filter { it.isBefore(today) }
        val bestDay = finished.minByOrNull { totals[it] ?: 0 }
        return StreakSummary(
            current = current,
            best = best,
            bestDay = bestDay,
            bestDayCount = bestDay?.let { totals[it] ?: 0 },
        )
    }
}
