package com.localnotes.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.localnotes.MainActivity
import com.localnotes.NotesApplication
import com.localnotes.R

class LiveSyncService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        (application as NotesApplication).syncClient.startAutoSync()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        (application as NotesApplication).syncClient.startAutoSync()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mac sync",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps notes and widgets in sync with your Mac"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync_notification)
            .setContentTitle("Notes is live")
            .setContentText("Widgets update when you type on your Mac")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "live_sync"
        private const val NOTIFICATION_ID = 17
        private const val PREFS = "notes_sync"
        private const val KEY_TOKEN = "token"
        private const val KEY_LIVE_WIDGETS = "live_widgets"

        fun notificationsAllowed(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 33) return true
            return ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        fun optedIn(context: Context): Boolean {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_LIVE_WIDGETS, false)
        }

        fun setOptedIn(context: Context, enabled: Boolean) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_LIVE_WIDGETS, enabled)
                .apply()
        }

        fun startIfAllowed(context: Context) {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getString(KEY_TOKEN, null).isNullOrBlank()) return
            if (!optedIn(app)) return
            if (!notificationsAllowed(app)) return
            ContextCompat.startForegroundService(app, Intent(app, LiveSyncService::class.java))
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(Intent(context.applicationContext, LiveSyncService::class.java))
        }
    }
}
