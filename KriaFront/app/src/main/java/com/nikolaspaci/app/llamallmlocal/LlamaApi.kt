package com.nikolaspaci.app.llamallmlocal

import android.util.Log
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter

interface PredictCallback {
    fun onToken(token: String)
    fun onThinkingToken(token: String)
    fun onComplete(tokensPerSecond: Double, durationInSeconds: Long)
    fun onError(error: String)
}
object LlamaApi {

    private const val TAG = "LlamaApi"

    init {
        Log.i(TAG, "Loading jniKriaCppWrapper library")
        System.loadLibrary("jniKriaCppWrapper")
    }

    external fun loadBackends(nativeLibDir: String)
    external fun init(modelPath: String, modelParameters: ModelParameter): Long
    external fun free(sessionPtr: Long)
    external fun predict(sessionPtr: Long, prompt: String, modelParameters: ModelParameter, enableThinking: Boolean, callback: PredictCallback)
    external fun predictWithMedia(sessionPtr: Long, prompt: String, imageData: ByteArray?, modelParameters: ModelParameter, enableThinking: Boolean, callback: PredictCallback)
    external fun stopPredict(sessionPtr: Long)
    external fun restoreHistory(sessionPtr: Long, messages: Array<ChatMessage>, systemPrompt: String)

    // Capabilities
    external fun supportsThinking(sessionPtr: Long): Boolean
    external fun hasVision(sessionPtr: Long): Boolean
    external fun initMultimodal(sessionPtr: Long, mmprojPath: String): Boolean

    // Hardware info methods
    external fun isVulkanAvailable(): Boolean
    external fun getVulkanDeviceInfo(): String
    external fun getRecommendedGpuLayers(): Int
    external fun getVulkanVramBytes(): Long

}
