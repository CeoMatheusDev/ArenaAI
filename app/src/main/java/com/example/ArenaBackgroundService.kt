package com.example

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ArenaBackgroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        var isRunning by mutableStateOf(false)
            private set
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ArenaAgent:ExecutionWakeLock"
        )?.apply {
            setReferenceCounted(false)
            // Safety timeout of 45 minutes to prevent infinite battery drain
            acquire(45 * 60 * 1000L)
        }

        val notification = NotificationHelper.buildServiceNotification(this)
        startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
