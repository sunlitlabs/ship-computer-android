package dev.jamlab.shipcomputer.realtime

import android.util.Base64
import dev.jamlab.shipcomputer.model.RealtimeSessionData
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenAIRealtimeClient(
    private val session: RealtimeSessionData,
    private val onReady: () -> Unit,
    private val onUserSpeechStart: () -> Unit,
    private val onUserSpeechEnd: () -> Unit,
    private val onAudioChunk: (String) -> Unit,   // base64 PCM16
    private val onResponseDone: () -> Unit,
    private val onTranscript: (String) -> Unit,   // Astra's text output
    private val onError: (String) -> Unit,
    private val onClosed: () -> Unit,
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private var receivingAudio = false

    fun connect() {
        val req = Request.Builder()
            .url("wss://api.openai.com/v1/realtime?model=${session.model}")
            .header("Authorization", "Bearer ${session.clientSecret}")
            .build()

        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Session is pre-configured by the server when it creates the client_secret
                // (POST /v1/realtime/client_secrets) — voice, VAD, prompt, tools are already set.
                // No session.update needed; just start streaming audio.
                onReady()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleEvent(JSONObject(text))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onError(t.message ?: "Connection to OpenAI failed")
                onClosed()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onClosed()
            }
        })
    }

    private fun handleEvent(event: JSONObject) {
        when (event.optString("type")) {
            "input_audio_buffer.speech_started" -> onUserSpeechStart()
            "input_audio_buffer.speech_stopped" -> onUserSpeechEnd()

            "response.audio.delta" -> {
                receivingAudio = true
                onAudioChunk(event.optString("delta"))
            }

            "response.audio.done" -> receivingAudio = false

            "response.done" -> onResponseDone()

            "response.output_item.done" -> {
                val item = event.optJSONObject("item") ?: return
                val content = item.optJSONArray("content") ?: return
                for (i in 0 until content.length()) {
                    val c = content.optJSONObject(i) ?: continue
                    if (c.optString("type") == "text") {
                        val text = c.optString("text")
                        if (text.isNotBlank()) onTranscript(text)
                    }
                }
            }

            "error" -> {
                val err = event.optJSONObject("error")
                onError(err?.optString("message") ?: "OpenAI error")
            }
        }
    }

    fun sendAudio(pcm16Bytes: ByteArray) {
        val b64 = Base64.encodeToString(pcm16Bytes, Base64.NO_WRAP)
        ws?.send("""{"type":"input_audio_buffer.append","audio":"$b64"}""")
    }

    fun mute(muted: Boolean) {
        // When muting, clear the audio buffer so queued audio isn't processed
        if (muted) ws?.send("""{"type":"input_audio_buffer.clear"}""")
    }

    fun triggerResponse() {
        // Manual VAD: commit buffer and request response
        ws?.send("""{"type":"input_audio_buffer.commit"}""")
        ws?.send("""{"type":"response.create"}""")
    }

    fun disconnect() {
        ws?.close(1000, "Disconnected")
        ws = null
        client.dispatcher.executorService.shutdown()
    }
}
