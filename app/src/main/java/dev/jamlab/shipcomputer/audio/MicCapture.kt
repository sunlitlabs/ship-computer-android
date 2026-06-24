package dev.jamlab.shipcomputer.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Captures mono PCM16 at 24kHz — the format OpenAI Realtime expects.
// VOICE_COMMUNICATION source activates the hardware AEC/NS/AGC pipeline,
// which cancels the speaker output from the mic signal so Astra doesn't
// hear herself. Paired with AudioPlayer using USAGE_VOICE_COMMUNICATION.
class MicCapture(private val onChunk: (ByteArray) -> Unit) {

    companion object {
        const val SAMPLE_RATE = 24000
        private val CHANNEL   = AudioFormat.CHANNEL_IN_MONO
        private val ENCODING  = AudioFormat.ENCODING_PCM_16BIT
        // 100 ms worth of samples per chunk — balances latency and bandwidth
        private val CHUNK_BYTES = SAMPLE_RATE * 2 / 10
    }

    private val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        .coerceAtLeast(CHUNK_BYTES * 2)

    private var record: AudioRecord? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (record != null) return
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE, CHANNEL, ENCODING, minBuf
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return
        }
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
    }
}
