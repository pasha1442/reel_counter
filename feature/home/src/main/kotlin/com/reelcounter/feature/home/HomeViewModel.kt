package com.reelcounter.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.data.AppConfig
import com.reelcounter.core.data.CounterHealthMonitor
import com.reelcounter.core.data.LiveCounterState
import com.reelcounter.core.data.repository.BrainRepository
import com.reelcounter.core.data.repository.EntitlementRepository
import com.reelcounter.core.data.repository.ReelRepository
import com.reelcounter.core.data.settings.SettingsRepository
import com.reelcounter.core.data.telemetry.Telemetry
import kotlinx.coroutines.launch
import com.reelcounter.core.data.time.TimeSource
import com.reelcounter.core.limits.Milestones
import com.reelcounter.core.limits.Streaks
import com.reelcounter.core.limits.WeekStreak
import com.reelcounter.core.limits.WeekStreaks
import com.reelcounter.core.model.AppLook
import com.reelcounter.core.model.WatchTime
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import com.reelcounter.core.model.BrainState
import com.reelcounter.core.model.CounterHealth
import com.reelcounter.core.model.CountingStatus
import com.reelcounter.core.model.ReelApp
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val todayTotal: Int = 0,
    val perApp: Map<ReelApp, Int> = emptyMap(),
    val limit: Int = 50,
    val brain: BrainState = BrainState.Fresh,
    val health: CounterHealth = CounterHealth.HEALTHY,
    val statuses: Map<ReelApp, CountingStatus> = emptyMap(),
    val streak: Int = 0,
    val weekStreak: WeekStreak = WeekStreak(0, 0, 0),
    /** Day streak needed for the next badge, or null when every badge is earned. */
    val nextBadge: Int? = null,
    val isPro: Boolean = false,
    val blockAtLimit: Boolean = true,
    val isDebug: Boolean = false,
    val lateNight: Boolean = false,
    /** The user chose "Not now" on the disclosure: counting needs their consent first. */
    val needsConsent: Boolean = false,
    /** This calendar week, Monday to Sunday. */
    val week: List<WeekDay> = emptyList(),
    val look: AppLook = AppLook.MIDNIGHT,
    /** Ask once whether to share crash reports and usage stats (only when there is something to share). */
    val askPrivacyChoice: Boolean = false,
) {
    val remaining: Int get() = limit - todayTotal
    val minutes: Int get() = WatchTime.minutes(todayTotal)

    /** Part of today's rot is left over from earlier days (the brain heals overnight, not instantly). */
    val healing: Boolean get() = brain.morningResidual >= HEALING_THRESHOLD

    private companion object {
        const val HEALING_THRESHOLD = 5f
    }
}

enum class DayResult { NO_DATA, UNDER, OVER, TODAY, FUTURE }

data class WeekDay(val date: LocalDate, val total: Int, val result: DayResult)

private data class Counts(
    val perApp: Map<ReelApp, Int>,
    val brain: BrainState,
    val streak: Int,
    val week: List<WeekDay>,
    val weekStreak: WeekStreak,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val reels: ReelRepository,
    brainRepository: BrainRepository,
    settings: SettingsRepository,
    entitlements: EntitlementRepository,
    private val monitor: CounterHealthMonitor,
    live: LiveCounterState,
    private val time: TimeSource,
    private val config: AppConfig,
    private val settingsRepository: SettingsRepository,
    private val telemetry: Telemetry,
) : ViewModel() {

    private val counts = combine(
        reels.todayPerApp,
        brainRepository.brain,
        reels.recentDays(STREAK_WINDOW_DAYS),
        settings.settings,
    ) { perApp, brain, days, s ->
        val today = time.today()
        val firstDay = s.firstRunEpochDay?.let(LocalDate::ofEpochDay) ?: today
        val totals = days.associate { it.date to it.total }
        val windowStart = maxOf(firstDay, today.minusDays(STREAK_WINDOW_DAYS - 1L))
        val streak = Streaks.summarize(totals = totals, limit = s.dailyLimit, firstDay = windowStart, today = today).current
        val weekStreak = WeekStreaks.summarize(totals = totals, limit = s.dailyLimit, firstDay = windowStart, today = today)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = (0L..6L).map { offset ->
            val date = monday.plusDays(offset)
            val total = totals[date] ?: 0
            val result = when {
                date == today -> DayResult.TODAY
                date.isAfter(today) -> DayResult.FUTURE
                date.isBefore(firstDay) -> DayResult.NO_DATA
                total <= s.dailyLimit -> DayResult.UNDER
                else -> DayResult.OVER
            }
            WeekDay(date, total, result)
        }
        Counts(perApp, brain, streak, week, weekStreak)
    }

    val state: StateFlow<HomeUiState> = combine(
        counts,
        settings.settings,
        monitor.health,
        live.appStatuses,
        entitlements.isPro,
    ) { c, s, health, statuses, pro ->
        val hour = LocalTime.now(time.clock()).hour
        HomeUiState(
            loading = false,
            todayTotal = c.perApp.values.sum(),
            perApp = c.perApp,
            limit = s.dailyLimit,
            brain = c.brain,
            health = if (s.disclosureAccepted) health else CounterHealth.NOT_ENABLED,
            needsConsent = !s.disclosureAccepted,
            statuses = statuses,
            streak = c.streak,
            weekStreak = c.weekStreak,
            nextBadge = Milestones.next(c.streak),
            look = s.look,
            isPro = pro,
            blockAtLimit = s.blockAtLimit,
            isDebug = config.isDebug,
            lateNight = hour >= 23 || hour < 5,
            week = c.week,
            askPrivacyChoice = !s.privacyChoiceMade && (telemetry.isAvailable || config.isDebug),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun openAccessibilitySettings(context: Context) = monitor.openAccessibilitySettings(context)

    fun setPrivacyChoice(share: Boolean) {
        viewModelScope.launch { settingsRepository.setPrivacyChoice(share) }
    }

    private companion object {
        const val STREAK_WINDOW_DAYS = 60
    }
}
