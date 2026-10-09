package com.reelcounter.core.limits

import com.reelcounter.core.model.LimitSettings
import com.reelcounter.core.model.ReelApp
import com.reelcounter.core.model.Strictness
import com.reelcounter.core.model.UnlockState
import javax.inject.Inject

sealed interface BlockDecision {
    data object Allow : BlockDecision

    data class Block(val reason: BlockReason) : BlockDecision
}

enum class BlockReason { DAILY_LIMIT, APP_LIMIT }

/**
 * Decides whether a new reel may play. Called before a reel is counted and whenever the
 * player appears, with the totals counted so far today.
 */
class LimitEngine @Inject constructor() {

    fun decide(
        app: ReelApp,
        todayTotal: Int,
        todayForApp: Int,
        settings: LimitSettings,
        isPro: Boolean,
        unlock: UnlockState,
    ): BlockDecision {
        // Free users get nudges only; blocking is Pro.
        if (!isPro || !settings.blockAtLimit) return BlockDecision.Allow

        val allowance = unlock.allowedUntilCount
        if (allowance != null && todayTotal < allowance) return BlockDecision.Allow

        val appLimit = settings.perAppLimits[app]
        if (appLimit != null && todayForApp >= appLimit) return BlockDecision.Block(BlockReason.APP_LIMIT)
        if (todayTotal >= settings.dailyLimit) return BlockDecision.Block(BlockReason.DAILY_LIMIT)
        return BlockDecision.Allow
    }
}

enum class UnlockMethod {
    /** Gentle: wait a few seconds, then continue. */
    WAIT,

    /** Firm: rebuild a short promise by tapping its words in order (no keyboard needed in the overlay). */
    BUILD_PHRASE,

    /** Locked, or no unlocks left today. */
    NONE,
}

data class UnlockOffer(
    val method: UnlockMethod,
    val extraReels: Int,
    val waitSeconds: Int,
    /** Null when unlimited. */
    val unlocksLeft: Int?,
)

object UnlockRules {
    const val EXTRA_REELS = 5
    const val FIRM_DAILY_UNLOCKS = 3
    const val GENTLE_WAIT_SECONDS = 10
    const val PHRASE = "I choose my time over the scroll"

    fun offer(strictness: Strictness, unlocksUsed: Int): UnlockOffer = when (strictness) {
        Strictness.GENTLE -> UnlockOffer(UnlockMethod.WAIT, EXTRA_REELS, GENTLE_WAIT_SECONDS, unlocksLeft = null)
        Strictness.FIRM -> {
            val left = (FIRM_DAILY_UNLOCKS - unlocksUsed).coerceAtLeast(0)
            if (left == 0) {
                UnlockOffer(UnlockMethod.NONE, 0, 0, unlocksLeft = 0)
            } else {
                UnlockOffer(UnlockMethod.BUILD_PHRASE, EXTRA_REELS, 0, unlocksLeft = left)
            }
        }
        Strictness.LOCKED -> UnlockOffer(UnlockMethod.NONE, 0, 0, unlocksLeft = 0)
    }

    val PHRASE_WORDS: List<String> = PHRASE.split(" ")

    /** The promise's words in a random order that is never already correct. */
    fun shuffledWords(random: kotlin.random.Random = kotlin.random.Random.Default): List<String> {
        var words = PHRASE_WORDS.shuffled(random)
        while (words == PHRASE_WORDS) words = PHRASE_WORDS.shuffled(random)
        return words
    }

    /** Case, spacing and punctuation don't matter; the words do. */
    fun phraseMatches(input: String): Boolean = normalize(input) == normalize(PHRASE)

    fun grant(todayTotal: Int, state: UnlockState): UnlockState = state.copy(
        unlocksUsed = state.unlocksUsed + 1,
        allowedUntilCount = todayTotal + EXTRA_REELS,
    )

    private fun normalize(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }.trim().split(Regex("\\s+")).joinToString(" ")
}
