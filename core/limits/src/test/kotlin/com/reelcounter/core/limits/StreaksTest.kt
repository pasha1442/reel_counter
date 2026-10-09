package com.reelcounter.core.limits

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StreaksTest {
    private val today = LocalDate.of(2026, 9, 29)

    @Test
    fun `days with no reels count toward the streak`() {
        val summary = Streaks.summarize(emptyMap(), limit = 40, firstDay = today.minusDays(2), today = today)
        assertEquals(3, summary.current)
        assertEquals(3, summary.best)
    }

    @Test
    fun `going over the limit breaks the streak`() {
        val totals = mapOf(
            today.minusDays(4) to 10,
            today.minusDays(3) to 12,
            today.minusDays(2) to 90,
            today.minusDays(1) to 30,
            today to 5,
        )
        val summary = Streaks.summarize(totals, limit = 40, firstDay = today.minusDays(4), today = today)
        assertEquals(2, summary.current)
        assertEquals(2, summary.best)
        assertEquals(today.minusDays(4), summary.bestDay)
        assertEquals(10, summary.bestDayCount)
    }

    @Test
    fun `today over the limit ends the current streak`() {
        val totals = mapOf(today.minusDays(1) to 1, today to 41)
        val summary = Streaks.summarize(totals, limit = 40, firstDay = today.minusDays(1), today = today)
        assertEquals(0, summary.current)
        assertEquals(1, summary.best)
    }
}
