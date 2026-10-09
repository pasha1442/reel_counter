package com.reelcounter.core.data.repository

import com.reelcounter.core.data.AppConfig
import com.reelcounter.core.data.settings.SettingsRepository
import com.reelcounter.core.data.time.TimeSource
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One person in today's battle. Ranked by the share of their own limit, so limits of 20 and 40 compete fairly. */
data class Standing(
    val name: String,
    val reels: Int,
    val limit: Int,
    val isYou: Boolean = false,
    /** Their counter was off for part of the day, so they aren't ranked today. */
    val trackingOffHours: Int? = null,
) {
    val sharePercent: Int get() = if (limit <= 0) 0 else reels * 100 / limit
    val initial: String get() = if (isYou) "You" else name.take(1).uppercase()
}

data class PastBattle(val date: LocalDate, val winner: String, val youWon: Boolean, val yourPercent: Int)

data class FriendsState(
    /** Battles need a server and an account; false until that's connected. */
    val battlesAvailable: Boolean,
    /** True when showing the debug sample battle. */
    val demo: Boolean,
    /** Ranked, lowest share first; people with tracking off come last. */
    val standings: List<Standing>,
    val past: List<PastBattle>,
)

/**
 * Friends and battles. Real battles need an account and a small server (planned: Firebase); until
 * that's set up this reports battles as unavailable, and debug builds can show a sample battle so the
 * screen can be tried. The user's own row always uses their real numbers.
 */
interface FriendsRepository {
    val state: Flow<FriendsState>

    /** What to send a friend. */
    fun inviteMessage(): String
}

@Singleton
class LocalFriendsRepository @Inject constructor(
    reels: ReelRepository,
    settings: SettingsRepository,
    private val config: AppConfig,
    private val time: TimeSource,
) : FriendsRepository {

    override val state: Flow<FriendsState> = combine(reels.todayTotal, settings.settings) { today, s ->
        val you = Standing("You", today, s.dailyLimit, isYou = true)
        if (config.isDebug && s.debugDemoFriends) {
            val ranked = listOf(
                Standing("Riya", 6, 30),
                you,
                Standing("Kabir", 27, 40),
            ).sortedBy { it.sharePercent } + Standing("Dev", 0, 25, trackingOffHours = 3)
            val yesterday = time.today().minusDays(1)
            FriendsState(
                battlesAvailable = true,
                demo = true,
                standings = ranked,
                past = listOf(
                    PastBattle(yesterday, "You", youWon = true, yourPercent = 45),
                    PastBattle(yesterday.minusDays(1), "Riya", youWon = false, yourPercent = 70),
                ),
            )
        } else {
            FriendsState(battlesAvailable = false, demo = false, standings = listOf(you), past = emptyList())
        }
    }

    override fun inviteMessage(): String =
        "I'm trying to scroll less. Count your reels with me and let's see who watches fewer: " +
            "https://play.google.com/store/apps/details?id=com.reelcounter.android"
}
