package com.nikolaspaci.app.llamallmlocal.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed class TranscriptionEvent {
    data class Segment(val text: String, val startMs: Long, val endMs: Long) : TranscriptionEvent()
    data class Completed(val fullText: String, val durationSec: Double) : TranscriptionEvent()
    data class Error(val message: String) : TranscriptionEvent()
}

interface SpeechEngine {

    sealed class LoadState {
        data object Idle : LoadState()
        data class Loading(val modelName: String) : LoadState()
        data class Loaded(val modelName: String) : LoadState()
        data class Error(val message: String) : LoadState()
    }

    val loadState: StateFlow<LoadState>

    suspend fun loadModel(modelPath: String, nThreads: Int = 4, useGpu: Boolean = false): Result<Unit>

    suspend fun unloadModel(): Result<Unit>

    fun transcribe(pcm: FloatArray, language: String = "auto", translate: Boolean = false): Flow<TranscriptionEvent>

    fun stop()

    fun isLoaded(): Boolean

    fun getCurrentModelPath(): String?
}
