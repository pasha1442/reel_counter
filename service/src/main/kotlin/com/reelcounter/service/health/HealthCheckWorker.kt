package com.reelcounter.service.health

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.reelcounter.core.data.CounterHealthMonitor
import com.reelcounter.core.data.settings.RuntimeStateRepository
import com.reelcounter.core.data.settings.SettingsRepository
import com.reelcounter.core.model.CounterHealth
import com.reelcounter.service.notify.CounterNotifications
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Every 15 minutes (WorkManager's minimum), check the counter is really running. Each incident
 * alerts once: "Counting stopped. Tap to fix." The alert clears itself once counting resumes.
 */
@HiltWorker
class HealthCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val monitor: CounterHealthMonitor,
    private val settings: SettingsRepository,
    private val runtime: RuntimeStateRepository,
    private val notifications: CounterNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val current = settings.settings.first()
        if (!current.onboardingComplete || !current.disclosureAccepted) return Result.success()

        val health = monitor.checkNow()
        val lastAlerted = runtime.heartbeat.first().lastAlerted
        if (health == CounterHealth.HEALTHY) {
            if (lastAlerted != null) {
                runtime.setLastAlerted(null)
                notifications.cancelHealth()
            }
        } else if (lastAlerted != health) {
            notifications.counterStopped(health)
            runtime.setLastAlerted(health)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "counter-health"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthCheckWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
