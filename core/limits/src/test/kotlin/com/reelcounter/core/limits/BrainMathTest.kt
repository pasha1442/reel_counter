package com.reelcounter.core.limits

import com.reelcounter.core.model.BrainStage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrainMathTest {

    @Test
    fun `rot scales with the user's own limit`() {
        assertEquals(50f, BrainMath.rot(todayCount = 25, dailyLimit = 50, morningResidual = 0f))
        assertEquals(50f, BrainMath.rot(todayCount = 100, dailyLimit = 200, morningResidual = 0f))
    }

    @Test
    fun `rot is capped at 100`() {
        assertEquals(100f, BrainMath.rot(todayCount = 500, dailyLimit = 50, morningResidual = 20f))
    }

    @Test
    fun `stages follow the plan's ranges`() {
        assertEquals(BrainStage.FRESH, BrainStage.fromRot(20f))
        assertEquals(BrainStage.FOGGY, BrainStage.fromRot(21f))
        assertEquals(BrainStage.CRACKING, BrainStage.fromRot(60f))
        assertEquals(BrainStage.MELTING, BrainStage.fromRot(61f))
        assertEquals(BrainStage.ROTTEN, BrainStage.fromRot(100f))
    }

    @Test
    fun `the brain does not reset to fresh the morning after a bad day`() {
        val residual = BrainMath.morningResidual(listOf(120), dailyLimit = 50)
        assertEquals(35f, residual, 0.01f)
        assertEquals(BrainStage.FOGGY, BrainMath.state(0, 50, listOf(120)).stage)
    }

    @Test
    fun `a day under the limit heals faster than a day over it`() {
        val afterGood = BrainMath.morningResidual(listOf(45), dailyLimit = 50)
        val afterBad = BrainMath.morningResidual(listOf(55), dailyLimit = 50)
        assertTrue(afterGood < afterBad / 3, "good=$afterGood bad=$afterBad")
    }

    @Test
    fun `a week of good days heals completely`() {
        val residual = BrainMath.morningResidual(listOf(300, 300, 0, 0, 0, 0, 0), dailyLimit = 50)
        assertTrue(residual < 0.01f, "residual=$residual")
    }
}
