package com.nikolaspaci.app.llamallmlocal.engine

import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.jni.PredictionEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ModelEngine {
    sealed class LoadState {
        object Idle : LoadState()
        data class Loading(val progress: Float, val modelName: String) : LoadState()
        data class Loaded(val modelName: String) : LoadState()
        data class Error(val message: String) : LoadState()
    }

    val loadState: StateFlow<LoadState>

    suspend fun loadModel(modelPath: String, parameters: ModelParameter): Result<Unit>
    suspend fun unloadModel(): Result<Unit>
    fun predict(prompt: String, parameters: ModelParameter, enableThinking: Boolean = false): Flow<PredictionEvent>
    fun predictWithMedia(prompt: String, imageData: ByteArray?, parameters: ModelParameter, enableThinking: Boolean = false): Flow<PredictionEvent>
    fun stopPredict()
    suspend fun restoreHistory(messages: List<ChatMessage>, systemPrompt: String = "")
    fun isModelLoaded(): Boolean
    fun getCurrentModelPath(): String?
    fun supportsThinking(): Boolean
    fun hasVision(): Boolean
}
