package com.reelcounter.app.work

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.reelcounter.app.widget.ReelCountWidget
import com.reelcounter.core.data.repository.ReelRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Runs just after midnight: resets the widget to the new day and keeps individual reel events
 * for 90 days only (daily totals stay). Then books itself for the next midnight.
 */
@HiltWorker
class MidnightWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reels: ReelRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        reels.pruneOldEvents(retainDays = 90)
        ReelCountWidget().updateAll(applicationContext)
        scheduleNext(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "midnight"

        fun scheduleNext(context: Context) {
            val now = ZonedDateTime.now()
            val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusMinutes(1)
            val request = OneTimeWorkRequestBuilder<MidnightWorker>()
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
