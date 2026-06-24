package dev.jamlab.shipcomputer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64

// Plays PCM16 24kHz mono audio — the format OpenAI Realtime returns.
// USAGE_VOICE_COMMUNICATION pairs with MicCapture's VOICE_COMMUNICATION
// source so the OS hardware AEC can cancel this playback from the mic.
class AudioPlayer {

    private var track: AudioTrack? = null

    fun init() {
        val minBuf = AudioTrack.getMinBufferSize(
            MicCapture.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(MicCapture.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track?.play()
    }

    fun playChunk(base64Audio: String) {
        val bytes = Base64.decode(base64Audio, Base64.DEFAULT)
        track?.write(bytes, 0, bytes.size)
    }

    fun release() {
        track?.stop()
        track?.release()
        track = null
    }
}
