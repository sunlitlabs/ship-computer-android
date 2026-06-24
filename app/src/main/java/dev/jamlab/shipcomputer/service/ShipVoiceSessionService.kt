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

// Called when the user triggers Ship Computer as the default assistant
// (badge button, long-press home, or lock-screen assistant shortcut).
private class ShipVoiceSession(ctx: Context) : VoiceInteractionSession(ctx) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        val webView = AudioForegroundService.webViewRef
        if (webView != null) {
            // App has a running WebView — dispatch startListening() directly.
            // Works even if the WebView is paused (screen off): the post() queues until
            // the Activity resumes when we bring it to front below.
            webView.post {
                webView.evaluateJavascript("""
                    (function(){
                        var app=window.shipVoiceApp;
                        if(app&&typeof app.startListening==='function'&&!app.micOpen&&app.status!=='connecting'){
                            app.startListening();
                        }
                    })()
                """.trimIndent(), null)
            }
            // Bring the Activity to front so the screen wakes up
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra(MainActivity.EXTRA_ASSISTANT_LAUNCH, true)
                }
            )
        } else {
            // App is not running or page hasn't loaded yet.
            // Mark auto-start so onPageFinished triggers listening after load.
            AudioForegroundService.pendingAutoStart = true
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_ASSISTANT_LAUNCH, true)
                }
            )
        }

        hide()
    }
}
