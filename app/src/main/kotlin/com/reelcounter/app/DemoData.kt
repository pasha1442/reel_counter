package com.reelcounter.app

import com.reelcounter.core.data.repository.ReelRepository
import com.reelcounter.core.data.settings.SettingsRepository
import com.reelcounter.core.data.time.TimeSource
import com.reelcounter.core.model.ReelApp
import com.reelcounter.core.model.ReelEvent
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * Sample history for the store-screenshot build (BuildConfig.DEMO_MODE): two weeks of a habit
 * shrinking from about 100 reels a day to under the limit, with today a little foggy.
 * Runs once per install, so every goldie capture (which reinstalls) starts from the same story.
 */
class DemoData @Inject constructor(
    private val reels: ReelRepository,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) {
    suspend fun seedOnce() {
        if (settings.settings.first().onboardingComplete) return
        val random = Random(SEED)
        val today = time.today()
        var session = 1L

        PAST_DAYS.forEachIndexed { index, total ->
            val day = today.minusDays((PAST_DAYS.size - index).toLong())
            val start = time.startOfDay(day)
            val sittings = splitIntoSittings(total, random).sorted()
            val hours = SITTING_HOURS.shuffled(random).take(sittings.size).sorted()
            // Biggest sitting latest: late-night scrolling, like most people.
            sittings.zip(hours).forEach { (count, hour) ->
                val at = start + hour * HOUR + random.nextLong(0, 30) * MINUTE
                record(at, count, appFor(random), session++, random)
            }
        }

        // Today: three sittings spread over the part of the day that has passed.
        val startToday = time.startOfDay(today)
        val elapsed = time.now() - startToday
        TODAY_SITTINGS.forEach { (fraction, count, app) ->
            val at = maxOf(startToday, startToday + (elapsed * fraction).toLong() - count * REEL_GAP)
            record(at, count, app, session++, random)
        }

        settings.update {
            it.copy(
                disclosureAccepted = true,
                privacyChoiceMade = true,
                dailyGuess = 90,
            )
        }
        settings.completeOnboarding(today.minusDays(PAST_DAYS.size.toLong()).toEpochDay())
    }

    private suspend fun record(start: Long, count: Int, app: ReelApp, session: Long, random: Random) {
        var at = start
        repeat(count) {
            reels.record(ReelEvent(app, at, session, estimated = false))
            at += REEL_GAP + random.nextLong(-8_000, 15_000)
        }
    }

    private fun appFor(random: Random) = if (random.nextFloat() < 0.6f) ReelApp.INSTAGRAM else ReelApp.YOUTUBE

    /** Three to six sittings that add up to [total]. */
    private fun splitIntoSittings(total: Int, random: Random): List<Int> {
        val parts = (total / 18).coerceIn(3, 6)
        val weights = List(parts) { 0.5f + random.nextFloat() }
        val sizes = weights.map { (it / weights.sum() * total).toInt().coerceAtLeast(1) }.toMutableList()
        sizes[sizes.lastIndex] += total - sizes.sum()
        return sizes
    }

    private companion object {
        const val SEED = 7
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val REEL_GAP = 27_000L

        /**
         * Oldest first; yesterday last. The default limit is 50. Twenty days, so last week and the
         * week before are complete whatever weekday the capture runs on.
         */
        val PAST_DAYS = listOf(
            118, 104, 121, 97, 109, 126, 101,
            92, 88, 96, 79, 84, 102, 71,
            66, 58, 61, 47, 52, 41,
        )

        val SITTING_HOURS = listOf(7, 9, 12, 13, 17, 19, 20, 21, 22, 23)

        /** Fraction of today so far, reels, app: 14 of 50 across both apps, a foggy brain. */
        val TODAY_SITTINGS = listOf(
            Triple(0.35, 4, ReelApp.YOUTUBE),
            Triple(0.7, 6, ReelApp.INSTAGRAM),
            Triple(0.95, 4, ReelApp.INSTAGRAM),
        )
    }
}
