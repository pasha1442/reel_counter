package com.reelcounter.core.limits

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class WeekStreak(
    /** Weeks in a row that counted, including this week once it has enough good days. */
    val current: Int,
    /** Good days (at or under the limit) so far this week, today included while it's still under. */
    val goodDaysThisWeek: Int,
    /** Days of this week still to come after today. */
    val daysLeftThisWeek: Int,
) {
    /** Good days still needed for this week to count; 0 when it already does. */
    val goodDaysNeeded: Int get() = (WeekStreaks.GOOD_DAYS_PER_WEEK - goodDaysThisWeek).coerceAtLeast(0)

    /** Whether this week can still count (enough days left to reach the target). */
    val stillPossible: Boolean get() = goodDaysNeeded <= daysLeftThisWeek + 1
}

/**
 * A week (Monday to Sunday) counts toward the week streak when at least [GOOD_DAYS_PER_WEEK] of its
 * days were at or under the limit. Forgiving on purpose: one or two bad days don't break it.
 */
object WeekStreaks {
    const val GOOD_DAYS_PER_WEEK = 5

    fun summarize(totals: Map<LocalDate, Int>, limit: Int, firstDay: LocalDate, today: LocalDate): WeekStreak {
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        fun good(day: LocalDate) = !day.isBefore(firstDay) && (totals[day] ?: 0) <= limit

        val goodThisWeek = generateSequence(thisMonday) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .count { good(it) }
        val daysLeft = 7 - (today.dayOfWeek.value)

        var streak = 0
        var monday = thisMonday.minusWeeks(1)
        // Weeks entirely before the first day can't count; stop there.
        while (!monday.plusDays(6).isBefore(firstDay)) {
            val goodDays = (0L..6L).count { good(monday.plusDays(it)) }
            if (goodDays >= GOOD_DAYS_PER_WEEK) streak++ else break
            monday = monday.minusWeeks(1)
        }
        if (goodThisWeek >= GOOD_DAYS_PER_WEEK) streak++
        return WeekStreak(current = streak, goodDaysThisWeek = goodThisWeek, daysLeftThisWeek = daysLeft)
    }
}

/** Day-streak badges, and how far away the next one is. */
object Milestones {
    val DAY_BADGES = listOf(3, 7, 14, 30, 60, 100, 365)

    fun next(currentStreak: Int): Int? = DAY_BADGES.firstOrNull { it > currentStreak }

    fun earned(bestStreak: Int): List<Int> = DAY_BADGES.filter { it <= bestStreak }
}
