package com.reelcounter.core.model

/** How hard the block is to get past. MVP ships FIRM; GENTLE and LOCKED unlock in V1. */
enum class Strictness { GENTLE, FIRM, LOCKED }

data class LimitSettings(
    val dailyLimit: Int = DEFAULT_DAILY_LIMIT,
    val perAppLimits: Map<ReelApp, Int> = emptyMap(),
    val blockAtLimit: Boolean = true,
    val strictness: Strictness = Strictness.FIRM,
    val nudgesEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_DAILY_LIMIT = 50
        const val MIN_LIMIT = 5
        const val MAX_LIMIT = 300
    }
}

/** Unlocks the user has used today, and how far the latest one lets them go. */
data class UnlockState(
    val unlocksUsed: Int = 0,
    val allowedUntilCount: Int? = null,
)
