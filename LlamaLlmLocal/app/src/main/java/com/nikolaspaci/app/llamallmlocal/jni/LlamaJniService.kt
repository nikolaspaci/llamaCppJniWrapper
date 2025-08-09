package com.nikolaspaci.app.llamallmlocal.jni

import com.nikolaspaci.app.llamallmlocal.LlamaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class LlamaJniService {

    private var sessionPtr: Long = 0

    fun loadModel(modelPath: String) {
        if (sessionPtr != 0L) {
            LlamaApi.free(sessionPtr)
        }
        sessionPtr = LlamaApi.init(modelPath)
    }

    fun isModelLoaded(): Boolean {
        return sessionPtr != 0L
    }

    suspend fun predict(prompt: String): Flow<String> = withContext(Dispatchers.IO) {
        if (sessionPtr == 0L) {
            return@withContext flow { emit("Error: Model not loaded") }
        }

        // The native function is blocking and returns the full response.
        // We'll run it in a background thread.
        // For now, we'll keep it synchronous within this suspend function,
        // as the native call itself is blocking.
        // To make it truly asynchronous and stream results, the native code
        // would need to support callbacks or a similar mechanism.
        val fullResponse = LlamaApi.predict(sessionPtr, prompt)

        //return full response
        flow { emit(fullResponse) }
    }

    fun unloadModel() {
        if (sessionPtr != 0L) {
            LlamaApi.free(sessionPtr)
            sessionPtr = 0
        }
    }
}