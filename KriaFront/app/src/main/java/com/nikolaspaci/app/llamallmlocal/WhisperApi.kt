package com.nikolaspaci.app.llamallmlocal

interface TranscribeCallback {
    fun onSegment(text: String, startMs: Long, endMs: Long)
    fun onComplete(fullText: String, durationSec: Double)
    fun onError(error: String)
}

object WhisperApi {

    init {
        System.loadLibrary("jniKriaCppWrapper")
    }

    external fun init(modelPath: String, nThreads: Int, useGpu: Boolean): Long
    external fun free(sessionPtr: Long)
    external fun transcribePcm(
        sessionPtr: Long,
        pcmData: FloatArray,
        language: String,
        translate: Boolean,
        callback: TranscribeCallback
    )
    external fun stopTranscribe(sessionPtr: Long)
}
