package com.nikolaspaci.app.llamallmlocal.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val TAG = "AudioRecorder"
        private const val MAX_DURATION_SEC = 60
    }

    private val isRecording = AtomicBoolean(false)

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Capture audio synchronously until [stop] is called (or [MAX_DURATION_SEC] elapses).
     * Returns 16 kHz mono float32 PCM in [-1, 1], ready for whisper.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(): FloatArray = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            throw SecurityException("RECORD_AUDIO permission not granted")
        }
        if (!isRecording.compareAndSet(false, true)) {
            throw IllegalStateException("AudioRecorder already running")
        }

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) {
            isRecording.set(false)
            throw IllegalStateException("AudioRecord.getMinBufferSize returned $minBuf")
        }
        val bufferSize = minBuf * 4

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            isRecording.set(false)
            throw IllegalStateException("AudioRecord init failed (state=${recorder.state})")
        }

        val maxSamples = SAMPLE_RATE * MAX_DURATION_SEC
        val collected = ArrayList<Short>(SAMPLE_RATE * 5)
        val chunk = ShortArray(bufferSize / 2)

        try {
            recorder.startRecording()
            while (isRecording.get() && collected.size < maxSamples) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read > 0) {
                    val toCopy = minOf(read, maxSamples - collected.size)
                    for (i in 0 until toCopy) {
                        collected.add(chunk[i])
                    }
                } else if (read < 0) {
                    Log.w(TAG, "AudioRecord.read returned $read")
                    break
                }
            }
        } finally {
            try { recorder.stop() } catch (_: IllegalStateException) { /* ignore */ }
            recorder.release()
            isRecording.set(false)
        }

        val out = FloatArray(collected.size)
        for (i in collected.indices) {
            out[i] = collected[i] / 32768f
        }
        out
    }

    fun stop() {
        isRecording.set(false)
    }

    fun isActive(): Boolean = isRecording.get()
}
