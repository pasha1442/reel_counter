package com.reelcounter.core.data.settings

import androidx.datastore.core.DataStore
import com.reelcounter.core.model.AppLook
import com.reelcounter.core.model.LimitSettings
import com.reelcounter.core.model.ReelApp
import com.reelcounter.core.model.Strictness
import com.reelcounter.core.model.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<AppSettings>,
) {
    val settings: Flow<AppSettings> = store.data

    val limitSettings: Flow<LimitSettings> = store.data.map { it.toLimitSettings() }.distinctUntilChanged()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.updateData(transform)
    }

    suspend fun setDailyLimit(limit: Int) = update {
        it.copy(dailyLimit = limit.coerceIn(LimitSettings.MIN_LIMIT, LimitSettings.MAX_LIMIT))
    }

    suspend fun setPerAppLimit(app: ReelApp, limit: Int?) = update {
        val limits = if (limit == null) it.perAppLimits - app.name else it.perAppLimits + (app.name to limit)
        it.copy(perAppLimits = limits)
    }

    suspend fun setBlockAtLimit(enabled: Boolean) = update { it.copy(blockAtLimit = enabled) }

    suspend fun setNudgesEnabled(enabled: Boolean) = update { it.copy(nudgesEnabled = enabled) }

    suspend fun setLiveNotification(enabled: Boolean) = update { it.copy(liveNotification = enabled) }

    suspend fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    suspend fun setLook(look: AppLook) = update { it.copy(look = look) }

    suspend fun setDailyGuess(guess: Int?) = update { it.copy(dailyGuess = guess) }

    suspend fun setStrictness(strictness: Strictness) = update { it.copy(strictness = strictness) }

    suspend fun setDisclosureAccepted(accepted: Boolean) = update { it.copy(disclosureAccepted = accepted) }

    suspend fun completeOnboarding(firstRunEpochDay: Long) = update {
        it.copy(onboardingComplete = true, firstRunEpochDay = it.firstRunEpochDay ?: firstRunEpochDay)
    }

    suspend fun setDebugPro(enabled: Boolean) = update { it.copy(debugPro = enabled) }

    suspend fun setDebugDemoFriends(enabled: Boolean) = update { it.copy(debugDemoFriends = enabled) }

    suspend fun setDebugRecordDetection(enabled: Boolean) = update { it.copy(debugRecordDetection = enabled) }

    /** Back to first launch: onboarding shows again, limits return to defaults. */
    /** One answer for both, from the one-time question; Profile can change each separately later. */
    suspend fun setPrivacyChoice(share: Boolean) = update {
        it.copy(shareCrashReports = share, shareUsageStats = share, privacyChoiceMade = true)
    }

    suspend fun setShareCrashReports(enabled: Boolean) = update { it.copy(shareCrashReports = enabled, privacyChoiceMade = true) }

    suspend fun setShareUsageStats(enabled: Boolean) = update { it.copy(shareUsageStats = enabled, privacyChoiceMade = true) }

    suspend fun setTestAccount(account: TestAccount?) = update { it.copy(testAccount = account) }

    suspend fun confirmAge(accountId: String) = update { it.copy(ageConfirmedAccounts = it.ageConfirmedAccounts + accountId) }

    suspend fun forgetAccount(accountId: String) = update { it.copy(ageConfirmedAccounts = it.ageConfirmedAccounts - accountId) }

    suspend fun resetAll() = update { AppSettings() }
}
