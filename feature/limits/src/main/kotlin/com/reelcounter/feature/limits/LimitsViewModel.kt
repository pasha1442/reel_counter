package com.reelcounter.feature.limits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.data.repository.EntitlementRepository
import com.reelcounter.core.data.repository.ReelRepository
import com.reelcounter.core.data.settings.AppSettings
import com.reelcounter.core.data.settings.SettingsRepository
import com.reelcounter.core.limits.BrainMath
import com.reelcounter.core.model.ReelApp
import com.reelcounter.core.model.Strictness
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LimitsUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val isPro: Boolean = false,
    val todayTotal: Int = 0,
    /** The limit on screen: follows the stepper instantly, saved a moment after the last change. */
    val dailyLimit: Int = settings.dailyLimit,
    /** How rotten the brain would be right now with [dailyLimit]. */
    val previewRot: Float = 0f,
) {
    val perAppLimits: Map<ReelApp, Int> get() = settings.toLimitSettings().perAppLimits
}

@HiltViewModel
class LimitsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    entitlements: EntitlementRepository,
    reels: ReelRepository,
) : ViewModel() {

    private val draftLimit = MutableStateFlow<Int?>(null)
    private var saveJob: Job? = null

    val state: StateFlow<LimitsUiState> = combine(
        settings.settings,
        entitlements.isPro,
        reels.todayTotal,
        reels.previousDayTotals(BrainMath.HISTORY_DAYS),
        draftLimit,
    ) { s, pro, today, previous, draft ->
        val limit = draft ?: s.dailyLimit
        LimitsUiState(
            loading = false,
            settings = s,
            isPro = pro,
            todayTotal = today,
            dailyLimit = limit,
            previewRot = BrainMath.state(todayCount = today, dailyLimit = limit, previousDayCounts = previous).rot,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LimitsUiState())

    /** Updates the screen at once; writes to storage once the user pauses (holding +/- steps fast). */
    fun setDailyLimit(value: Int) {
        draftLimit.value = value
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            settings.setDailyLimit(value)
            draftLimit.value = null
        }
    }

    fun setNudges(enabled: Boolean) {
        viewModelScope.launch { settings.setNudgesEnabled(enabled) }
    }

    /** Pro features return false for free users so the screen can open the paywall instead. */
    fun setBlockAtLimit(enabled: Boolean): Boolean {
        if (!state.value.isPro) return false
        viewModelScope.launch { settings.setBlockAtLimit(enabled) }
        return true
    }

    fun setStrictness(strictness: Strictness): Boolean {
        if (!state.value.isPro) return false
        viewModelScope.launch { settings.setStrictness(strictness) }
        return true
    }

    fun setPerAppLimit(app: ReelApp, limit: Int?): Boolean {
        if (!state.value.isPro) return false
        viewModelScope.launch { settings.setPerAppLimit(app, limit) }
        return true
    }

    override fun onCleared() {
        // Leaving the screen mid-edit still keeps the new limit.
        draftLimit.value?.let { pending -> CoroutineScope(Dispatchers.IO).launch { settings.setDailyLimit(pending) } }
    }

    private companion object {
        const val SAVE_DELAY_MS = 400L
    }
}
