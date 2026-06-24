package dev.jamlab.shipcomputer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.jamlab.shipcomputer.audio.AudioPlayer
import dev.jamlab.shipcomputer.audio.MicCapture
import dev.jamlab.shipcomputer.auth.AuthManager
import dev.jamlab.shipcomputer.realtime.OpenAIRealtimeClient
import dev.jamlab.shipcomputer.service.AudioForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class VoiceStatus { DISCONNECTED, CONNECTING, READY, USER_SPEAKING, RESPONDING, ERROR }

data class ActivityEntry(val time: String, val text: String, val isError: Boolean = false)

class VoiceViewModel(private val authManager: AuthManager) : ViewModel() {

    private val _status  = MutableStateFlow(VoiceStatus.DISCONNECTED)
    val status: StateFlow<VoiceStatus> = _status.asStateFlow()

    private val _muted   = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _error   = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _persona = MutableStateFlow("Astra")
    val persona: StateFlow<String> = _persona.asStateFlow()

    private val _activity = MutableStateFlow<List<ActivityEntry>>(emptyList())
    val activity: StateFlow<List<ActivityEntry>> = _activity.asStateFlow()

    private var realtimeClient: OpenAIRealtimeClient? = null
    private val mic    = MicCapture { bytes -> if (!_muted.value) realtimeClient?.sendAudio(bytes) }
    private val player = AudioPlayer()

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    // Called by the badge button and screen button alike
    fun toggleMic() {
        val nowMuted = !_muted.value
        _muted.value = nowMuted
        realtimeClient?.mute(nowMuted)
        if (nowMuted) {
            mic.stop()
            log("Microphone muted")
        } else {
            mic.start(viewModelScope)
            log("Microphone active")
        }
    }

    fun connect(context: Context) {
        if (_status.value != VoiceStatus.DISCONNECTED && _status.value != VoiceStatus.ERROR) return
        _status.value = VoiceStatus.CONNECTING
        _error.value  = null
        log("Connecting to Astra…")

        viewModelScope.launch {
            val sessionResult = authManager.fetchRealtimeSession()
            if (sessionResult.isFailure) {
                val msg = sessionResult.exceptionOrNull()?.message ?: "Could not start session"
                _error.value  = msg
                _status.value = VoiceStatus.ERROR
                log(msg, isError = true)
                return@launch
            }

            val session = sessionResult.getOrThrow()
            _persona.value = session.personaName

            player.init()
            AudioForegroundService.prepareAudio(context)

            val client = OpenAIRealtimeClient(
                session         = session,
                onReady         = {
                    _status.value = VoiceStatus.READY
                    _muted.value  = false
                    mic.start(viewModelScope)
                    log("Connected — ${session.personaName} is listening")
                },
                onUserSpeechStart = { _status.value = VoiceStatus.USER_SPEAKING },
                onUserSpeechEnd   = { _status.value = VoiceStatus.READY },
                onAudioChunk      = { b64 ->
                    _status.value = VoiceStatus.RESPONDING
                    player.playChunk(b64)
                },
                onResponseDone    = { _status.value = VoiceStatus.READY },
                onTranscript      = { text -> log("${session.personaName}: $text") },
                onError           = { msg ->
                    _error.value  = msg
                    _status.value = VoiceStatus.ERROR
                    log(msg, isError = true)
                },
                onClosed          = {
                    if (_status.value != VoiceStatus.ERROR) {
                        _status.value = VoiceStatus.DISCONNECTED
                    }
                    mic.stop()
                    player.release()
                    AudioForegroundService.releaseAudio(context)
                },
            )
            realtimeClient = client
            client.connect()
        }
    }

    fun disconnect(context: Context) {
        realtimeClient?.disconnect()
        realtimeClient = null
        mic.stop()
        player.release()
        AudioForegroundService.releaseAudio(context)
        _status.value = VoiceStatus.DISCONNECTED
        _muted.value  = false
        log("Session ended")
    }

    private fun log(text: String, isError: Boolean = false) {
        val time = LocalTime.now().format(timeFmt)
        val entry = ActivityEntry(time, text, isError)
        _activity.value = (_activity.value + entry).takeLast(20)
    }

    override fun onCleared() {
        super.onCleared()
        mic.stop()
        player.release()
        realtimeClient?.disconnect()
    }
}
