package com.reelcounter.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.reelcounter.app.work.MidnightWorker
import com.reelcounter.app.work.TrialReminderWorker
import com.reelcounter.core.data.account.AuthRepository
import com.reelcounter.core.data.repository.EntitlementRepository
import com.reelcounter.core.data.telemetry.Telemetry
import com.reelcounter.service.health.HealthCheckWorker
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class ReelCounterApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // Lazy: RevenueCat has to be configured before the repositories that use it are created.
    @Inject lateinit var telemetry: Lazy<Telemetry>

    @Inject lateinit var auth: Lazy<AuthRepository>

    @Inject lateinit var entitlements: Lazy<EntitlementRepository>

    @Inject lateinit var demoData: Lazy<DemoData>

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Screenshot build only, and before any screen reads the settings it seeds.
        if (BuildConfig.DEMO_MODE) runBlocking { demoData.get().seedOnce() }
        configurePayments()
        telemetry.get().start()
        keepPurchasesWithAccount()
        remindBeforeTrialEnds()
        HealthCheckWorker.schedule(this)
        MidnightWorker.scheduleNext(this)
    }

    /** Google Play Billing through RevenueCat, once a key is set in local.properties. */
    private fun configurePayments() {
        if (BuildConfig.REVENUECAT_API_KEY.isBlank() || Purchases.isConfigured) return
        Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
        Purchases.configure(PurchasesConfiguration.Builder(this, BuildConfig.REVENUECAT_API_KEY).build())
    }

    /** Keeps the paywall's promise: a reminder 2 days before a free trial becomes paid. */
    private fun remindBeforeTrialEnds() {
        appScope.launch {
            entitlements.get().trialEndsAt.distinctUntilChanged().collect { end ->
                TrialReminderWorker.schedule(this@ReelCounterApplication, end)
            }
        }
    }

    /** Pro follows the person: purchases attach to the account on sign-in and detach on sign-out. */
    private fun keepPurchasesWithAccount() {
        appScope.launch {
            auth.get().user.map { it?.id }.distinctUntilChanged().collect { id ->
                if (id != null) entitlements.get().onSignedIn(id) else entitlements.get().onSignedOut()
            }
        }
    }
}
