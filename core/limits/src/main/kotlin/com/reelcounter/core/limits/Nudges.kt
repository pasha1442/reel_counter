package com.reelcounter.core.limits

import kotlin.math.ceil

enum class Threshold(val percent: Int) { HALF(50), MOST(80), REACHED(100) }

object Nudges {
    fun mark(threshold: Threshold, limit: Int): Int = ceil(limit * threshold.percent / 100.0).toInt().coerceAtLeast(1)

    /** Thresholds passed by going from [before] to [after] reels today. */
    fun crossed(before: Int, after: Int, limit: Int): List<Threshold> {
        if (limit <= 0) return emptyList()
        return Threshold.entries.filter { threshold ->
            val mark = mark(threshold, limit)
            before < mark && after >= mark
        }
    }
}
