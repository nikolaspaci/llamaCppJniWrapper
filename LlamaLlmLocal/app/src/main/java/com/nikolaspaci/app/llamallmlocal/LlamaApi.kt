package com.nikolaspaci.app.llamallmlocal

import android.os.Build

interface PredictCallback {
    fun onToken(token: String)
    fun onComplete(tokensPerSecond: Double, durationInSeconds: Long)
    fun onError(error: String)
}
object LlamaApi {
    init {
        when (Build.SUPPORTED_ABIS.firstOrNull()) {
            "arm64-v8a" -> {
                try {
                    // Attempt to load the most optimized library first.
                    System.loadLibrary("jniLlamaCppWrapper_armv9-a")
                } catch (e: UnsatisfiedLinkError) {
                    // If it fails, the CPU likely doesn't support v9 instructions.
                    // Fall back to the more compatible v8.2-a library.
                    System.loadLibrary("jniLlamaCppWrapper_v82a")
                }
            }
            else -> {
                // For other architectures (x86_64, etc.), load the generic library.
                System.loadLibrary("jniLlamaCppWrapper")
            }
        }
    }

    external fun init(modelPath: String): Long
    external fun free(sessionPtr: Long)
    external fun predict(sessionPtr: Long, prompt: String, callback: PredictCallback)

}
