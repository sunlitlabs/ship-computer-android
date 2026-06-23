package dev.jamlab.shipcomputer.bluetooth

import android.content.Context
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.webkit.WebView

class BadgeButtonManager(private val context: Context) {

    private var mediaSession: MediaSessionCompat? = null
    private var webView: WebView? = null

    fun attach(webView: WebView) {
        this.webView = webView
        setupMediaSession()
    }

    private fun setupMediaSession() {
        val session = MediaSessionCompat(context, "ShipComputerBadge")
        session.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
            .build()
        session.setPlaybackState(state)
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = bridgeToWebView()
            override fun onPause() = bridgeToWebView()
            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) = bridgeToWebView()
        })
        session.isActive = true
        mediaSession = session
    }

    private fun bridgeToWebView() {
        webView?.post {
            webView?.evaluateJavascript(
                "window.dispatchEvent(new KeyboardEvent('keydown',{key:'MediaPlayPause',bubbles:true}))",
                null
            )
        }
    }

    fun release() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        webView = null
    }
}
