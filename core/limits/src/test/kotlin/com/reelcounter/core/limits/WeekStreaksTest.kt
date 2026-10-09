package com.reelcounter.core.limits

import java.time.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class WeekStreaksTest {
    // Wednesday 30 Sep 2026.
    private val today = LocalDate.of(2026, 9, 30)
    private val lastMonday = LocalDate.of(2026, 9, 21)

    @Test
    fun lastWeekWithFiveGoodDaysCounts() {
        val totals = mapOf(
            lastMonday to 18, lastMonday.plusDays(1) to 27, lastMonday.plusDays(2) to 12,
            lastMonday.plusDays(3) to 9, lastMonday.plusDays(4) to 15, lastMonday.plusDays(5) to 22,
            lastMonday.plusDays(6) to 13, today.minusDays(2) to 14, today.minusDays(1) to 9, today to 13,
        )
        val result = WeekStreaks.summarize(totals, limit = 20, firstDay = lastMonday, today = today)
        assertEquals(1, result.current)
        assertEquals(3, result.goodDaysThisWeek)
        assertEquals(4, result.daysLeftThisWeek)
        assertEquals(2, result.goodDaysNeeded)
    }

    @Test
    fun thisWeekCountsOnceItHasFiveGoodDays() {
        val friday = LocalDate.of(2026, 10, 2)
        val result = WeekStreaks.summarize(emptyMap(), limit = 20, firstDay = friday.minusDays(4), today = friday)
        assertEquals(1, result.current)
        assertEquals(0, result.goodDaysNeeded)
    }

    @Test
    fun daysBeforeTheCounterStartedDoNotCount() {
        val result = WeekStreaks.summarize(emptyMap(), limit = 20, firstDay = today, today = today)
        assertEquals(0, result.current)
        assertEquals(1, result.goodDaysThisWeek)
    }

    @Test
    fun milestonesPointToTheNextBadge() {
        assertEquals(7, Milestones.next(3))
        assertEquals(3, Milestones.next(0))
        assertEquals(listOf(3, 7), Milestones.earned(9))
    }
}
