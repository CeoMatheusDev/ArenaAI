package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_ID_RESPONSES = "arena_ai_responses"
    const val CHANNEL_ID_SERVICE = "arena_ai_service"
    const val CHANNEL_ID_SCAN = "arena_ai_addons_scan"
    const val CHANNEL_ID_ADDONS_FOUND = "arena_ai_addons_found"

    const val SERVICE_NOTIFICATION_ID = 1001
    private const val RESPONSE_NOTIFICATION_ID = 2002
    private const val SCAN_NOTIFICATION_ID = 3003
    private const val ADDONS_FOUND_NOTIFICATION_ID = 3004

    const val EXTRA_OPEN_EXTENSIONS = "OPEN_EXTENSIONS_CONFIG"
    const val EXTRA_OPEN_FROM_NOTIFICATION = "OPEN_FROM_NOTIFICATION"

    fun isNotificationsAllowed(context: Context): Boolean {
        return ArenaLocaleHelper.isNotifyPreferenceEnabled(context)
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val localized = ArenaLocaleHelper.localizedContext(context)
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val responseChannel = NotificationChannel(
                CHANNEL_ID_RESPONSES,
                localized.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = localized.getString(R.string.notification_channel_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                localized.getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = localized.getString(R.string.service_channel_desc)
                setShowBadge(false)
            }

            val scanChannel = NotificationChannel(
                CHANNEL_ID_SCAN,
                localized.getString(R.string.scan_addons_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = localized.getString(R.string.addons_channel_desc)
                setShowBadge(false)
                setSound(null, null)
            }

            val addonsFoundChannel = NotificationChannel(
                CHANNEL_ID_ADDONS_FOUND,
                localized.getString(R.string.addons_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = localized.getString(R.string.addons_channel_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(responseChannel)
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(scanChannel)
            notificationManager.createNotificationChannel(addonsFoundChannel)
        }
    }

    fun buildServiceNotification(context: Context): Notification {
        val localized = ArenaLocaleHelper.localizedContext(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(localized.getString(R.string.service_notification_title))
            .setContentText(localized.getString(R.string.service_notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun showResponseNotification(context: Context, previewText: String?) {
        if (!isNotificationsAllowed(context)) {
            cancelAllNotifications(context)
            return
        }

        val localized = ArenaLocaleHelper.localizedContext(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_FROM_NOTIFICATION, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val bodyText = if (!previewText.isNullOrBlank() && previewText.length > 5) {
            val clean = previewText.trim()
            if (clean.length > 180) clean.take(180) + "…" else clean
        } else {
            localized.getString(R.string.response_completed_default_body)
        }

        val titleText = localized.getString(R.string.response_completed_title)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_RESPONSES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bodyText)
                    .setBigContentTitle(titleText)
                    .setSummaryText(localized.getString(R.string.app_name))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 150, 300))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(RESPONSE_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    fun showScanningAddonsNotification(context: Context) {
        if (!isNotificationsAllowed(context)) {
            cancelScanningNotification(context)
            return
        }

        val localized = ArenaLocaleHelper.localizedContext(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SCAN)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(localized.getString(R.string.scan_addons_title))
            .setContentText(localized.getString(R.string.scan_addons_progress))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(SCAN_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelScanningNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(SCAN_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    fun showAddonsFoundNotification(context: Context, extensionsCount: Int, pluginsCount: Int) {
        cancelScanningNotification(context)
        if (!isNotificationsAllowed(context)) {
            return
        }

        val localized = ArenaLocaleHelper.localizedContext(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_EXTENSIONS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val parts = mutableListOf<String>()
        if (extensionsCount > 0) parts.add(localized.getString(R.string.addons_count_extensions, extensionsCount))
        if (pluginsCount > 0) parts.add(localized.getString(R.string.addons_count_plugins, pluginsCount))
        val summary = localized.getString(R.string.scan_addons_found_summary, parts.joinToString(", "))

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ADDONS_FOUND)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(localized.getString(R.string.scan_addons_found_title))
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(ADDONS_FOUND_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    fun cancelAllNotifications(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancelAll()
        } catch (_: Exception) {
        }
        stopBackgroundService(context)
    }

    fun startBackgroundService(context: Context) {
        // When notifications are turned off in Settings, do not display any foreground notification
        if (!isNotificationsAllowed(context)) {
            stopBackgroundService(context)
            return
        }
        val serviceIntent = Intent(context, ArenaBackgroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun stopBackgroundService(context: Context) {
        try {
            val serviceIntent = Intent(context, ArenaBackgroundService::class.java)
            context.stopService(serviceIntent)
        } catch (_: Exception) {
        }
    }
}
