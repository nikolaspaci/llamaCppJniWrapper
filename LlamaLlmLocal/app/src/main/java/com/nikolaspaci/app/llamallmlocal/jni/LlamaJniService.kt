package com.nikolaspaci.app.llamallmlocal.jni

import com.nikolaspaci.app.llamallmlocal.LlamaApi
import com.nikolaspaci.app.llamallmlocal.PredictCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

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

    fun predict(prompt: String): Flow<String> = callbackFlow {
        if (sessionPtr == 0L) {
            close(IllegalStateException("Error: Model not loaded"))
            return@callbackFlow
        }

        val callback = object : PredictCallback {
            override fun onToken(token: String) {
                trySend(token)
            }

            override fun onComplete() {
                close()
            }

            override fun onError(error: String) {
                close(RuntimeException(error))
            }
        }

        // This call is blocking on the current thread until the native side calls onComplete or onError
        LlamaApi.predict(sessionPtr, prompt, callback)

        // awaitClose is needed to keep the flow open until close() is called
        awaitClose {
            // You can add cleanup logic here if needed
        }
    }.flowOn(Dispatchers.IO)

    fun unloadModel() {
        if (sessionPtr != 0L) {
            LlamaApi.free(sessionPtr)
            sessionPtr = 0
        }
    }
}