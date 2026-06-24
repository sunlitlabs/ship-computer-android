package dev.jamlab.shipcomputer.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import dev.jamlab.shipcomputer.MainActivity

class ShipVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        ShipVoiceSession(this)
}

// Called when the user triggers the default assistant (badge button / long-press home).
// If the voice app is already open and connected, dispatch MediaPlayPause to unmute.
// Otherwise just bring the app to the foreground.
private class ShipVoiceSession(ctx: Context) : VoiceInteractionSession(ctx) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val webView = AudioForegroundService.webViewRef
        if (webView != null) {
            webView.post {
                webView.evaluateJavascript(
                    "window.dispatchEvent(new KeyboardEvent('keydown',{key:'MediaPlayPause',bubbles:true}))",
                    null
                )
            }
        } else {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        }
        hide()
    }
}
