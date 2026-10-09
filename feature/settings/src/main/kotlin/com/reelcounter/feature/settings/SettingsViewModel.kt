package com.reelcounter.feature.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.reelcounter.core.data.AppConfig
import com.reelcounter.core.data.CounterHealthMonitor
import com.reelcounter.core.data.account.AccountUser
import com.reelcounter.core.data.account.AuthMode
import com.reelcounter.core.data.account.AuthRepository
import com.reelcounter.core.data.repository.EntitlementRepository
import com.reelcounter.core.data.repository.ReelRepository
import com.reelcounter.core.data.repository.RestoreResult
import com.reelcounter.core.data.settings.AppSettings
import com.reelcounter.core.data.settings.SettingsRepository
import com.reelcounter.core.data.telemetry.Events
import com.reelcounter.core.data.telemetry.Telemetry
import com.reelcounter.core.data.time.TimeSource
import com.reelcounter.core.model.AppLook
import com.reelcounter.core.model.CounterHealth
import com.reelcounter.core.model.ReelApp
import com.reelcounter.core.model.ReelEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val health: CounterHealth = CounterHealth.HEALTHY,
    val isPro: Boolean = false,
    val isDebug: Boolean = false,
    val versionName: String = "",
    val user: AccountUser? = null,
    /** The signed-in account has a name and passed the 18+ check on this phone. */
    val accountSetupComplete: Boolean = false,
    val authMode: AuthMode = AuthMode.OFF,
    /** Crash reports and usage stats can be sent (the build has Firebase); else the toggles are hidden. */
    val telemetryAvailable: Boolean = false,
    val billingConnected: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val reels: ReelRepository,
    private val monitor: CounterHealthMonitor,
    private val entitlements: EntitlementRepository,
    private val auth: AuthRepository,
    private val telemetry: Telemetry,
    private val config: AppConfig,
    private val time: TimeSource,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        settings.settings,
        monitor.health,
        entitlements.isPro,
        auth.user,
    ) { s, health, pro, user ->
        SettingsUiState(
            settings = s,
            health = health,
            isPro = pro,
            isDebug = config.isDebug,
            versionName = config.versionName,
            user = user,
            accountSetupComplete = user != null && user.hasName && user.id in s.ageConfirmedAccounts,
            authMode = auth.mode,
            telemetryAvailable = telemetry.isAvailable || config.isDebug,
            billingConnected = entitlements.billingConnected,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(isDebug = config.isDebug, versionName = config.versionName, authMode = auth.mode),
    )

    private val messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val message: SharedFlow<String> = messages

    fun openAccessibilitySettings(context: Context) = monitor.openAccessibilitySettings(context)

    fun setLiveNotification(enabled: Boolean) = launch { settings.setLiveNotification(enabled) }

    fun setLook(look: AppLook) = launch {
        settings.setLook(look)
        telemetry.event(Events.LOOK_CHANGED, mapOf("look" to look.name.lowercase()))
    }

    fun setShareCrashReports(enabled: Boolean) = launch { settings.setShareCrashReports(enabled) }

    fun setShareUsageStats(enabled: Boolean) = launch { settings.setShareUsageStats(enabled) }

    fun setDebugDemoFriends(enabled: Boolean) = launch { settings.setDebugDemoFriends(enabled) }

    fun setDebugPro(enabled: Boolean) = launch { settings.setDebugPro(enabled) }

    fun setDebugRecording(enabled: Boolean) = launch { settings.setDebugRecordDetection(enabled) }

    fun openManageSubscription(context: Context) = entitlements.openManageSubscription(context)

    fun restorePurchases() = launch {
        telemetry.event(Events.RESTORE)
        messages.emit(
            when (val result = entitlements.restore()) {
                RestoreResult.Restored -> "Pro restored. Welcome back!"
                RestoreResult.NothingToRestore -> "No Pro purchase found for this Google account."
                is RestoreResult.Failed -> result.reason
            },
        )
    }

    /** Google Play's review sheet; falls back to the store page (Play decides when the sheet shows). */
    fun rate(activity: Activity) = launch {
        val shown = runCatching {
            val manager = ReviewManagerFactory.create(activity)
            manager.launchReview(activity, manager.requestReview())
        }.isSuccess
        if (!shown) openStorePage(activity)
    }

    fun shareApp(context: Context) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "I'm counting my reels with Reel Counter. It keeps your scrolling in check: " + storeUrl(context.packageName),
            )
        }
        context.startActivity(Intent.createChooser(send, "Share Reel Counter"))
    }

    /** Opens an email to support, or says there is no support address yet. */
    fun contactSupport(context: Context) {
        if (config.supportEmail.isBlank()) {
            launch { messages.emit("Support email isn't set up yet.") }
            return
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${config.supportEmail}"))
            .putExtra(Intent.EXTRA_SUBJECT, "Reel Counter ${config.versionName}: help")
        runCatching { context.startActivity(intent) }.onFailure {
            launch { messages.emit("Write to us at ${config.supportEmail}") }
        }
    }

    fun deleteAllData() = launch {
        reels.deleteAll()
        messages.emit("All reel history deleted")
    }

    fun replayOnboarding() = launch { settings.update { it.copy(onboardingComplete = false) } }

    /** Debug builds: add a reel without opening Instagram, to try the brain and limits. */
    fun simulateReel(app: ReelApp) {
        if (!config.isDebug) return
        launch {
            reels.record(ReelEvent(app = app, timeMillis = time.now(), surfaceSessionId = 0, estimated = false))
            messages.emit("Added a test ${app.displayName} reel")
        }
    }

    private fun openStorePage(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName.removeSuffix(".debug")}"))
        runCatching { context.startActivity(market) }.onFailure {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl(context.packageName)))) }
        }
    }

    private fun storeUrl(packageName: String) =
        "https://play.google.com/store/apps/details?id=${packageName.removeSuffix(".debug")}"

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
