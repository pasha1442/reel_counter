package com.reelcounter.core.data.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.reelcounter.core.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Crash reports and usage events, both opt-in. What may be sent is deliberately small: event
 * names from [Events] with a few plain parameters. Never reel counts, never anything read from
 * the screen, never account details.
 */
interface Telemetry {
    /** False when the build has no Firebase project, so there is nothing to opt in to. */
    val isAvailable: Boolean

    /** Starts following the user's choices. Call once, at app start. */
    fun start()

    fun event(name: String, params: Map<String, String> = emptyMap())

    /** A caught problem worth a look (only sent with crash reports switched on). */
    fun recordError(error: Throwable)
}

/** Event names. Keep this list short: each one must have a clear question it answers. */
object Events {
    const val PAYWALL_VIEW = "paywall_view"
    const val TRIAL_OR_PURCHASE = "purchase_done"
    const val PURCHASE_CANCELLED = "purchase_cancelled"
    const val RESTORE = "restore_purchases"
    const val SIGN_IN = "login"
    const val SIGN_UP = "sign_up"
    const val SIGN_OUT = "sign_out"
    const val ACCOUNT_DELETED = "account_deleted"
    const val LOOK_CHANGED = "look_changed"
}

/** Used when the build has no Firebase project: everything is a no-op. */
object NoTelemetry : Telemetry {
    override val isAvailable = false

    override fun start() = Unit

    override fun event(name: String, params: Map<String, String>) = Unit

    override fun recordError(error: Throwable) = Unit
}

/**
 * Firebase Analytics and Crashlytics. Collection is off in the manifest and is only switched on
 * here, after the user has said yes in the app.
 */
@Singleton
class FirebaseTelemetry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : Telemetry {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val isAvailable = true

    @Volatile private var usageAllowed = false

    @Volatile private var crashesAllowed = false

    override fun start() {
        scope.launch {
            settings.settings
                .map { it.shareUsageStats to it.shareCrashReports }
                .distinctUntilChanged()
                .collect { (usage, crashes) ->
                    usageAllowed = usage
                    crashesAllowed = crashes
                    FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(usage)
                    FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = crashes
                }
        }
    }

    override fun event(name: String, params: Map<String, String>) {
        if (!usageAllowed) return
        val bundle = Bundle().apply { params.forEach { (key, value) -> putString(key, value) } }
        FirebaseAnalytics.getInstance(context).logEvent(name, bundle)
    }

    override fun recordError(error: Throwable) {
        if (crashesAllowed) FirebaseCrashlytics.getInstance().recordException(error)
    }

    companion object {
        /** True when the build was made with a google-services.json (Firebase initialised itself). */
        fun isAvailable(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()
    }
}
