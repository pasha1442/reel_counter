package com.reelcounter.core.data.repository

import com.reelcounter.core.data.settings.SettingsRepository
import com.reelcounter.core.limits.BrainMath
import com.reelcounter.core.model.BrainState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class BrainRepository @Inject constructor(
    reels: ReelRepository,
    settings: SettingsRepository,
) {
    val brain: Flow<BrainState> = combine(
        reels.todayTotal,
        reels.previousDayTotals(BrainMath.HISTORY_DAYS),
        settings.limitSettings,
    ) { today, previous, limits ->
        BrainMath.state(todayCount = today, dailyLimit = limits.dailyLimit, previousDayCounts = previous)
    }.distinctUntilChanged()
}
