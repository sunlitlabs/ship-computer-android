package dev.jamlab.shipcomputer.bluetooth

import android.content.Context
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import dev.jamlab.shipcomputer.service.AudioForegroundService

class BadgeButtonManager(private val context: Context) {

    private var mediaSession: MediaSessionCompat? = null

    fun setup() {
        val session = MediaSessionCompat(context, "ShipComputerBadge")
        session.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
                .build()
        )
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay()  = fire()
            override fun onPause() = fire()
            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) = fire()
        })
        session.isActive = true
        mediaSession = session
    }

    private fun fire() {
        AudioForegroundService.onBadgePress?.invoke()
    }

    fun release() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
    }
}
