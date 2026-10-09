package com.reelcounter.feature.onboarding

import android.content.Context
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.data.CounterHealthMonitor
import com.reelcounter.core.data.repository.ReelRepository
import com.reelcounter.core.data.settings.SettingsRepository
import com.reelcounter.core.data.time.TimeSource
import com.reelcounter.core.model.AppLook
import com.reelcounter.core.model.CounterHealth
import com.reelcounter.core.model.LimitSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep {
    WELCOME,
    GUESS,
    LOOK,
    DISCLOSURE,
    ACCESSIBILITY,
    NOTIFICATIONS,
    BATTERY,
    LIMIT,
    LIVE_TEST,
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val health: CounterHealth = CounterHealth.NOT_ENABLED,
    val dailyLimit: Int = 40,
    val testProgress: Int = 0,
    /** Index of the chosen guess tile, or null before choosing. */
    val guess: Int? = null,
    val look: AppLook = AppLook.MIDNIGHT,
) {
    val testPassed: Boolean get() = testProgress >= LIVE_TEST_REELS

    companion object {
        const val LIVE_TEST_REELS = 3
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val settings: SettingsRepository,
    private val reels: ReelRepository,
    private val monitor: CounterHealthMonitor,
    private val time: TimeSource,
) : ViewModel() {

    private val step = MutableStateFlow(
        if (savedState.get<Boolean>("startAtDisclosure") == true) OnboardingStep.DISCLOSURE else OnboardingStep.WELCOME,
    )
    private val limit = MutableStateFlow(40)
    private val testBaseline = MutableStateFlow<Int?>(null)
    private val guess = MutableStateFlow<Int?>(null)

    private val base = combine(
        step,
        monitor.health,
        limit,
        reels.todayTotal,
        testBaseline,
    ) { step, health, limit, today, baseline ->
        OnboardingUiState(
            step = step,
            health = health,
            dailyLimit = limit,
            testProgress = baseline?.let { (today - it).coerceAtLeast(0) } ?: 0,
        )
    }

    val state: StateFlow<OnboardingUiState> = combine(base, guess, settings.settings) { b, g, s ->
        b.copy(guess = g, look = s.look)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState(step = step.value))

    /** Steps shown to this user (notifications only exist as a runtime permission on Android 13+). */
    val steps: List<OnboardingStep> = OnboardingStep.entries.filter {
        it != OnboardingStep.NOTIFICATIONS || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    fun next() {
        val index = steps.indexOf(step.value)
        var target = steps.getOrNull(index + 1) ?: return
        // Already on? Skip straight past the settings step.
        if (target == OnboardingStep.ACCESSIBILITY && monitor.checkNow() == CounterHealth.HEALTHY) {
            target = steps[steps.indexOf(target) + 1]
        }
        goTo(target)
    }

    fun back(): Boolean {
        val index = steps.indexOf(step.value)
        if (index <= 0) return false
        goTo(steps[index - 1])
        return true
    }

    private fun goTo(target: OnboardingStep) {
        if (target == OnboardingStep.LIVE_TEST) startLiveTest()
        step.value = target
    }

    fun chooseGuess(index: Int?) {
        guess.value = index
        viewModelScope.launch { settings.setDailyGuess(index?.let { GUESS_VALUES[it] }) }
    }

    fun chooseLook(look: AppLook) {
        viewModelScope.launch { settings.setLook(look) }
    }

    fun agreeToDisclosure() {
        viewModelScope.launch {
            settings.setDisclosureAccepted(true)
            next()
        }
    }

    /** "Not now": respect it. Finish setup with counting off; Home offers to set it up later. */
    fun declineDisclosure(onFinished: () -> Unit) {
        viewModelScope.launch {
            settings.setDisclosureAccepted(false)
            settings.completeOnboarding(time.today().toEpochDay())
            onFinished()
        }
    }

    fun openAccessibilitySettings(context: Context) = monitor.openAccessibilitySettings(context)

    fun setLimit(value: Int) {
        limit.update { value.coerceIn(LimitSettings.MIN_LIMIT, LimitSettings.MAX_LIMIT) }
    }

    fun saveLimitAndContinue() {
        viewModelScope.launch {
            settings.setDailyLimit(limit.value)
            next()
        }
    }

    private fun startLiveTest() {
        viewModelScope.launch { testBaseline.value = reels.todayTotal.first() }
    }

    fun finish(onFinished: () -> Unit) {
        viewModelScope.launch {
            settings.setDailyLimit(limit.value)
            settings.completeOnboarding(time.today().toEpochDay())
            onFinished()
        }
    }
}

/** Reels a day each guess tile stands for (used to compare with the real number later). */
internal val GUESS_VALUES = listOf(15, 35, 75, 120)
