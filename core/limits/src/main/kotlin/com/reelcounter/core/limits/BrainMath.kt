package com.reelcounter.core.limits

import com.reelcounter.core.model.BrainState

/**
 * Rot level = the share of today's limit used, plus a little damage carried over from yesterday.
 *
 * The carry-over is what makes healing real: the brain starts each morning with part of
 * yesterday's rot, and a day under the limit heals it much faster than a day over it.
 */
object BrainMath {
    const val MAX_ROT = 100f

    /** Share of yesterday's final rot still showing next morning after a day at or under the limit. */
    const val CARRY_AFTER_GOOD_DAY = 0.10f

    /** ... and after a day over the limit. */
    const val CARRY_AFTER_BAD_DAY = 0.35f

    /** Days of history worth folding; older days have decayed to nothing. */
    const val HISTORY_DAYS = 7

    fun rot(todayCount: Int, dailyLimit: Int, morningResidual: Float): Float {
        if (dailyLimit <= 0) return MAX_ROT
        val fromToday = todayCount.toFloat() / dailyLimit * MAX_ROT
        return (morningResidual + fromToday).coerceIn(0f, MAX_ROT)
    }

    /**
     * Damage left this morning, from previous days' totals ordered oldest to newest
     * (the last item is yesterday). Days with no reels count as 0.
     */
    fun morningResidual(previousDayCounts: List<Int>, dailyLimit: Int): Float {
        var residual = 0f
        for (count in previousDayCounts.takeLast(HISTORY_DAYS)) {
            val finalRot = rot(count, dailyLimit, residual)
            val carry = if (count <= dailyLimit) CARRY_AFTER_GOOD_DAY else CARRY_AFTER_BAD_DAY
            residual = finalRot * carry
        }
        return residual
    }

    fun state(todayCount: Int, dailyLimit: Int, previousDayCounts: List<Int>): BrainState {
        val residual = morningResidual(previousDayCounts, dailyLimit)
        return BrainState(rot = rot(todayCount, dailyLimit, residual), morningResidual = residual)
    }
}
