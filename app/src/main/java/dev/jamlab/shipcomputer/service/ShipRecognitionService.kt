package dev.jamlab.shipcomputer.service

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

// Stub required by Android: VoiceInteractionService selection only persists when a
// recognitionService is declared in the voice interaction metadata XML. Ship Computer
// handles all voice I/O through WebRTC inside the WebView, not via RecognitionService.
class ShipRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onStopListening(listener: Callback?) {}

    override fun onCancel(listener: Callback?) {}
}
