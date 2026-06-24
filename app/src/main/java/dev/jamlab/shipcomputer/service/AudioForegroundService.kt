package dev.jamlab.shipcomputer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.jamlab.shipcomputer.MainActivity
import dev.jamlab.shipcomputer.R

class AudioForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START       -> startFg()
            ACTION_END_SESSION -> { onBadgePress?.invoke(); stopSelf() }
        }
        return START_STICKY
    }

    private fun startFg() {
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Voice Session", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Ship Computer active session" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
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

    companion object {
        const val CHANNEL_ID       = "ship_computer_session"
        const val NOTIFICATION_ID  = 1
        const val ACTION_START       = "dev.jamlab.shipcomputer.ACTION_START"
        const val ACTION_END_SESSION = "dev.jamlab.shipcomputer.ACTION_END_SESSION"

        // Set by VoiceViewModel or BadgeButtonManager for badge / notification End Session
        @Volatile var onBadgePress: (() -> Unit)? = null

        // Set by ShipVoiceSessionService for assistant trigger auto-start
        @Volatile var pendingAutoStart = false

        // Keep WebView ref for any remaining WebView paths (assistant launch)
        @Volatile var webViewRef: android.webkit.WebView? = null

        private var audioManager: AudioManager? = null
        private var focusRequest: AudioFocusRequest? = null
        private var prevMode = AudioManager.MODE_NORMAL

        // Called before WebSocket connect — sets MODE_IN_COMMUNICATION and
        // requests AUDIOFOCUS_GAIN so background services release AudioRecord.
        fun prepareAudio(context: Context) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager = am
            prevMode = am.mode
            am.mode = AudioManager.MODE_IN_COMMUNICATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener {}
                    .build()
                focusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
            }

            context.startForegroundService(
                Intent(context, AudioForegroundService::class.java).apply { action = ACTION_START }
            )
        }

        fun releaseAudio(context: Context) {
            val am = audioManager ?: return
            am.mode = prevMode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
            audioManager = null
            focusRequest = null
            context.stopService(Intent(context, AudioForegroundService::class.java))
        }
    }
}
