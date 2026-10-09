package com.reelcounter.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.reelcounter.app.R
import java.util.concurrent.TimeUnit

/**
 * The reminder the paywall promises: two days before a free trial turns into a paid subscription,
 * a notification says so and links straight to Google Play's cancel page. Scheduled whenever the
 * trial's end date is known (see the Application class).
 */
class TrialReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_subscription), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val manage = PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?package=${context.packageName}")),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val body = context.getString(R.string.trial_reminder_body)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.reelcounter.service.R.drawable.ic_stat_brain)
            .setContentTitle(context.getString(R.string.trial_reminder_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(manage)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "trial-reminder"
        private const val CHANNEL = "subscription"
        private const val NOTIFICATION_ID = 300
        private val REMIND_BEFORE_MS = TimeUnit.DAYS.toMillis(2)

        /** Books (or moves) the reminder for [trialEndsAt]; cancels it when there is no trial. */
        fun schedule(context: Context, trialEndsAt: Long?) {
            val work = WorkManager.getInstance(context)
            val delay = trialEndsAt?.let { it - REMIND_BEFORE_MS - System.currentTimeMillis() }
            if (delay == null || delay <= 0) {
                work.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = OneTimeWorkRequestBuilder<TrialReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            work.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
