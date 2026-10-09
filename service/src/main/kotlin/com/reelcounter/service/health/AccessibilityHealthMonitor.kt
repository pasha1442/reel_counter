package com.reelcounter.service.health

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.reelcounter.core.data.AppConfig
import com.reelcounter.core.data.CounterHealthMonitor
import com.reelcounter.core.data.LiveCounterState
import com.reelcounter.core.data.SystemScreens
import com.reelcounter.core.model.CounterHealth
import com.reelcounter.service.ReelAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Compares the user's setting ("enabled") with what the system actually bound ("running").
 * "Enabled but not running" is the classic dead-counter state that OEM battery savers cause.
 */
@Singleton
class AccessibilityHealthMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val live: LiveCounterState,
    private val config: AppConfig,
) : CounterHealthMonitor {

    private val component = ComponentName(context, ReelAccessibilityService::class.java)
    private val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)

    override fun checkNow(): CounterHealth {
        // The screenshot build has no service turned on: a reinstall drops it.
        if (config.demoMode) return CounterHealth.HEALTHY
        if (!isEnabledInSettings()) return CounterHealth.NOT_ENABLED
        val bound = accessibilityManager
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()
            .any { info ->
                val service = info.resolveInfo?.serviceInfo
                service?.packageName == component.packageName && service.name == component.className
            }
        return if (bound || live.serviceConnected.value) CounterHealth.HEALTHY else CounterHealth.ENABLED_NOT_RUNNING
    }

    override val health: Flow<CounterHealth> = callbackFlow {
        trySend(checkNow())
        val stateListener = AccessibilityManager.AccessibilityStateChangeListener { trySend(checkNow()) }
        accessibilityManager?.addAccessibilityStateChangeListener(stateListener)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(checkNow())
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            observer,
        )
        val connectedJob = launch { live.serviceConnected.collect { trySend(checkNow()) } }
        awaitClose {
            accessibilityManager?.removeAccessibilityStateChangeListener(stateListener)
            context.contentResolver.unregisterContentObserver(observer)
            connectedJob.cancel()
        }
    }.distinctUntilChanged()

    override fun openAccessibilitySettings(context: Context) {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Our service's own page. Not in the public SDK, so the list below is the fallback.
                add(
                    Intent(ACTION_ACCESSIBILITY_DETAILS)
                        .putExtra(Intent.EXTRA_COMPONENT_NAME, component.flattenToString()),
                )
            }
            add(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        SystemScreens.launchFirst(context, intents)
    }

    private companion object {
        const val ACTION_ACCESSIBILITY_DETAILS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    }

    private fun isEnabledInSettings(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val full = component.flattenToString()
        val short = component.flattenToShortString()
        return enabled.split(':').any { it.equals(full, ignoreCase = true) || it.equals(short, ignoreCase = true) }
    }
}
