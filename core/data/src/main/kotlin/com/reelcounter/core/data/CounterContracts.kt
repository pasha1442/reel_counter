package com.reelcounter.core.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.reelcounter.core.model.BrainState
import com.reelcounter.core.model.CounterHealth
import com.reelcounter.core.model.CountingStatus
import com.reelcounter.core.model.ReelApp
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Build facts the app module provides. */
data class AppConfig(
    val isDebug: Boolean,
    val versionName: String,
    /** RevenueCat's public SDK key for Google Play; blank until billing is set up. */
    val revenueCatApiKey: String = "",
    /** Shown in "Help & feedback"; blank until there is a support address. */
    val supportEmail: String = "",
    /** Where the docs/ legal pages are hosted, without a trailing slash; blank until published. */
    val legalBaseUrl: String = "",
    /** The store-screenshot build: seeded sample data, counter shown as running. Never shipped. */
    val demoMode: Boolean = false,
) {
    val billingConfigured: Boolean get() = revenueCatApiKey.isNotBlank()
}

/** Implemented by the service module: is the accessibility service really running? */
interface CounterHealthMonitor {
    /** Re-checks and emits whenever something may have changed. */
    val health: Flow<CounterHealth>

    fun checkNow(): CounterHealth

    /** Opens our service's page in Accessibility settings (or the list, where that page isn't available). */
    fun openAccessibilitySettings(context: Context)
}

/** Where a notification or widget tap should land inside the app. */
object DeepLinks {
    const val EXTRA_DESTINATION = "com.reelcounter.DESTINATION"
    const val PAYWALL = "paywall"
    const val HEALTH = "health"
}

/** System screens we send people to instead of asking for broad permissions. */
object SystemScreens {
    fun openBatterySettings(context: Context) {
        val candidates = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            appDetails(context),
        )
        launchFirst(context, candidates)
    }

    fun openAppDetails(context: Context) = launchFirst(context, listOf(appDetails(context)))

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        launchFirst(context, listOf(intent, appDetails(context)))
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return launchFirst(context, listOf(intent))
    }

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    fun launchFirst(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }
}

/** Anything that shows the live count outside the app (home-screen widget, status bar). */
interface ReelCountListener {
    suspend fun onCountChanged(todayTotal: Int, dailyLimit: Int, brain: BrainState)
}

/** In-process state the service publishes for the UI. */
@Singleton
class LiveCounterState @Inject constructor() {
    private val connected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = connected.asStateFlow()

    private val statuses = MutableStateFlow<Map<ReelApp, CountingStatus>>(emptyMap())
    val appStatuses: StateFlow<Map<ReelApp, CountingStatus>> = statuses.asStateFlow()

    fun setServiceConnected(value: Boolean) {
        connected.value = value
    }

    fun setStatus(app: ReelApp, status: CountingStatus) {
        statuses.value = statuses.value + (app to status)
    }
}
