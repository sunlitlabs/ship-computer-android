package dev.jamlab.shipcomputer.service

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
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import dev.jamlab.shipcomputer.MainActivity
import dev.jamlab.shipcomputer.R

class AudioForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession()
            ACTION_END_SESSION -> endSession()
            ACTION_MUTE -> mute()
        }
        return START_STICKY
    }

    private fun startSession() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun endSession() {
        webViewRef?.post {
            webViewRef?.evaluateJavascript("window.voiceApp?.disconnect()", null)
        }
        stopSelf()
    }

    private fun mute() {
        webViewRef?.post {
            webViewRef?.evaluateJavascript(
                "window.dispatchEvent(new KeyboardEvent('keydown',{key:'MediaPlayPause',bubbles:true}))",
                null
            )
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Voice Session",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Ship Computer active session" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val endIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AudioForegroundService::class.java).apply { action = ACTION_END_SESSION },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ship Computer")
            .setContentText("Voice session active")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(0, "End Session", endIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        webViewRef = null
    }

    companion object {
        const val CHANNEL_ID = "ship_computer_session"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "dev.jamlab.shipcomputer.ACTION_START"
        const val ACTION_END_SESSION = "dev.jamlab.shipcomputer.ACTION_END_SESSION"
        const val ACTION_MUTE = "dev.jamlab.shipcomputer.ACTION_MUTE"

        var webViewRef: WebView? = null

        fun start(context: Context, webView: WebView) {
            webViewRef = webView
            context.startForegroundService(
                Intent(context, AudioForegroundService::class.java).apply { action = ACTION_START }
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AudioForegroundService::class.java))
            webViewRef = null
        }
    }
}
