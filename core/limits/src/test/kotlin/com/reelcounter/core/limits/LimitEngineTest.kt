package com.reelcounter.core.limits

import com.reelcounter.core.model.LimitSettings
import com.reelcounter.core.model.ReelApp
import com.reelcounter.core.model.Strictness
import com.reelcounter.core.model.UnlockState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LimitEngineTest {
    private val engine = LimitEngine()
    private val settings = LimitSettings(dailyLimit = 40)

    private fun decide(
        total: Int,
        forApp: Int = total,
        isPro: Boolean = true,
        unlock: UnlockState = UnlockState(),
        s: LimitSettings = settings,
    ) = engine.decide(ReelApp.INSTAGRAM, total, forApp, s, isPro, unlock)

    @Test
    fun `reels under the limit play`() {
        assertEquals(BlockDecision.Allow, decide(total = 39))
    }

    @Test
    fun `the reel after the limit is blocked`() {
        assertEquals(BlockDecision.Block(BlockReason.DAILY_LIMIT), decide(total = 40))
    }

    @Test
    fun `free users are never blocked`() {
        assertEquals(BlockDecision.Allow, decide(total = 400, isPro = false))
    }

    @Test
    fun `turning blocking off allows everything`() {
        assertEquals(BlockDecision.Allow, decide(total = 400, s = settings.copy(blockAtLimit = false)))
    }

    @Test
    fun `a per-app limit blocks that app first`() {
        val s = settings.copy(perAppLimits = mapOf(ReelApp.INSTAGRAM to 10))
        assertEquals(BlockDecision.Block(BlockReason.APP_LIMIT), decide(total = 12, forApp = 10, s = s))
    }

    @Test
    fun `an unlock allows exactly five more reels`() {
        val unlocked = UnlockRules.grant(todayTotal = 40, state = UnlockState())
        assertEquals(BlockDecision.Allow, decide(total = 44, unlock = unlocked))
        assertTrue(decide(total = 45, unlock = unlocked) is BlockDecision.Block)
    }

    @Test
    fun `firm mode allows three unlocks a day`() {
        assertEquals(UnlockMethod.BUILD_PHRASE, UnlockRules.offer(Strictness.FIRM, unlocksUsed = 2).method)
        assertEquals(1, UnlockRules.offer(Strictness.FIRM, unlocksUsed = 2).unlocksLeft)
        assertEquals(UnlockMethod.NONE, UnlockRules.offer(Strictness.FIRM, unlocksUsed = 3).method)
    }

    @Test
    fun `shuffled promise words are never already in order`() {
        repeat(200) { seed ->
            val words = UnlockRules.shuffledWords(kotlin.random.Random(seed))
            assertTrue(words != UnlockRules.PHRASE_WORDS)
            assertEquals(UnlockRules.PHRASE_WORDS.sorted(), words.sorted())
        }
    }

    @Test
    fun `locked mode never offers an unlock`() {
        assertEquals(UnlockMethod.NONE, UnlockRules.offer(Strictness.LOCKED, unlocksUsed = 0).method)
    }

    @Test
    fun `the typed promise ignores case and punctuation`() {
        assertTrue(UnlockRules.phraseMatches("i choose my time over the scroll."))
        assertTrue(UnlockRules.phraseMatches("  I   choose my time over the SCROLL "))
        assertFalse(UnlockRules.phraseMatches("I choose the scroll"))
    }

    @Test
    fun `nudges fire once at 50, 80 and 100 percent`() {
        assertEquals(listOf(Threshold.HALF), Nudges.crossed(before = 19, after = 20, limit = 40))
        assertEquals(emptyList<Threshold>(), Nudges.crossed(before = 20, after = 21, limit = 40))
        assertEquals(listOf(Threshold.MOST), Nudges.crossed(before = 31, after = 32, limit = 40))
        assertEquals(listOf(Threshold.REACHED), Nudges.crossed(before = 39, after = 40, limit = 40))
    }
}
