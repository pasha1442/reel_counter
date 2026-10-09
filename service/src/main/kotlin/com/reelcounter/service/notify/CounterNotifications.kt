package com.reelcounter.service.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reelcounter.core.data.DeepLinks
import com.reelcounter.core.limits.Threshold
import com.reelcounter.core.model.CounterHealth
import com.reelcounter.service.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Every notification the app posts. Copy is playful, never shaming. */
@Singleton
class CounterNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannels(
            listOf(
                NotificationChannel(CH_NUDGES, context.getString(R.string.channel_nudges), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_nudges_desc) },
                NotificationChannel(CH_HEALTH, context.getString(R.string.channel_health), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_health_desc) },
                NotificationChannel(CH_LIVE, context.getString(R.string.channel_live), NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        description = context.getString(R.string.channel_live_desc)
                        setShowBadge(false)
                    },
            ),
        )
    }

    fun nudge(threshold: Threshold, count: Int, limit: Int, blocking: Boolean) {
        val (title, body, destination) = when (threshold) {
            Threshold.HALF -> Triple(
                context.getString(R.string.nudge_half_title),
                context.getString(R.string.nudge_half_body, count, limit),
                null,
            )
            Threshold.MOST -> Triple(
                context.getString(R.string.nudge_most_title),
                context.getString(R.string.nudge_most_body, count, limit),
                null,
            )
            Threshold.REACHED -> if (blocking) {
                Triple(context.getString(R.string.nudge_blocked_title), context.getString(R.string.nudge_blocked_body, limit), null)
            } else {
                // The highest-intent moment for the upgrade offer.
                Triple(context.getString(R.string.nudge_free_title, limit), context.getString(R.string.nudge_free_body), DeepLinks.PAYWALL)
            }
        }
        post(
            id = ID_NUDGE,
            builder = base(CH_NUDGES, destination)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true),
        )
    }

    fun counterStopped(health: CounterHealth) {
        val stopped = health == CounterHealth.ENABLED_NOT_RUNNING
        post(
            id = ID_HEALTH,
            builder = base(CH_HEALTH, DeepLinks.HEALTH)
                .setContentTitle(context.getString(if (stopped) R.string.health_stopped_title else R.string.health_off_title))
                .setContentText(context.getString(if (stopped) R.string.health_stopped_body else R.string.health_off_body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true),
        )
    }

    fun cancelHealth() = manager.cancel(ID_HEALTH)

    fun live(count: Int, limit: Int, stageName: String) {
        post(
            id = ID_LIVE,
            builder = base(CH_LIVE, null)
                .setContentTitle(context.getString(R.string.live_title, count))
                .setContentText(context.getString(R.string.live_body, limit, stageName))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setProgress(limit, count.coerceAtMost(limit), false),
        )
    }

    fun cancelLive() = manager.cancel(ID_LIVE)

    private fun base(channel: String, destination: String?): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_brain)
            .setColor(0xFFE0A21A.toInt())
            .setContentIntent(openApp(destination))

    private fun openApp(destination: String?): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        if (destination != null) launch.putExtra(DeepLinks.EXTRA_DESTINATION, destination)
        return PendingIntent.getActivity(
            context,
            destination?.hashCode() ?: 0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun post(id: Int, builder: NotificationCompat.Builder) {
        if (!canPost()) return
        try {
            manager.notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    fun canPost(): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val CH_NUDGES = "nudges"
        const val CH_HEALTH = "health"
        const val CH_LIVE = "live"
        const val ID_NUDGE = 100
        const val ID_HEALTH = 200
        const val ID_LIVE = 300
    }
}
