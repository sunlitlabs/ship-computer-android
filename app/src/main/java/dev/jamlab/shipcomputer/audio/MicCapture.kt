package dev.jamlab.shipcomputer.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Captures mono PCM16 at 24kHz — the format OpenAI Realtime expects.
//
// VOICE_RECOGNITION rather than VOICE_COMMUNICATION: the former is tuned
// for speech-to-text (lighter touch, calibrated for recognition quality);
// the latter is tuned for live phone calls with aggressive AEC that can
// over-cancel and distort the user's voice before it reaches OpenAI.
//
// We attach software AEC/NS/AGC explicitly so we get consistent behaviour
// regardless of what the hardware source default enables or disables.
class MicCapture(private val onChunk: (ByteArray) -> Unit) {

    companion object {
        const val SAMPLE_RATE = 24000
        private val CHANNEL  = AudioFormat.CHANNEL_IN_MONO
        private val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        // 100 ms worth of samples — balances VAD latency and bandwidth
        private val CHUNK_BYTES = SAMPLE_RATE * 2 / 10  // 4 800 bytes
    }

    private val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        .coerceAtLeast(CHUNK_BYTES * 2)

    private var record: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (record != null) return

        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, CHANNEL, ENCODING, minBuf
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return
        }

        val sid = rec.audioSessionId
        // Attach software effects to the same audio session so we get
        // clean, consistent audio regardless of the device's hardware defaults.
        if (AcousticEchoCanceler.isAvailable())  aec = AcousticEchoCanceler.create(sid)?.apply { enabled = true }
        if (NoiseSuppressor.isAvailable())       ns  = NoiseSuppressor.create(sid)?.apply { enabled = true }
        if (AutomaticGainControl.isAvailable())  agc = AutomaticGainControl.create(sid)?.apply { enabled = true }

        record = rec
        rec.startRecording()

        job = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(CHUNK_BYTES)
            while (isActive) {
                val read = rec.read(buf, 0, buf.size)
                if (read > 0) onChunk(buf.copyOf(read))
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        record?.stop()
        record?.release()
        record = null
        aec?.release(); aec = null
        ns?.release();  ns  = null
        agc?.release(); agc = null
    }
}
