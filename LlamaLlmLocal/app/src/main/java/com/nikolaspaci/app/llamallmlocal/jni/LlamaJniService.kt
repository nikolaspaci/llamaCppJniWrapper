package com.nikolaspaci.app.llamallmlocal.jni

import com.nikolaspaci.app.llamallmlocal.LlamaApi
import com.nikolaspaci.app.llamallmlocal.PredictCallback
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

object LlamaJniService {

    private var sessionPtr: Long = 0

    fun loadModel(modelPath: String, modelParameter: ModelParameter) {
        if (sessionPtr != 0L) {
            LlamaApi.free(sessionPtr)
        }
        sessionPtr = LlamaApi.init(modelPath, modelParameter)
    }

    fun isModelLoaded(): Boolean {
        return sessionPtr != 0L
    }

    fun predict(prompt: String, modelParameter: ModelParameter): Flow<PredictionEvent> = callbackFlow {
        if (sessionPtr == 0L) {
            close(IllegalStateException("Error: Model not loaded"))
            return@callbackFlow
        }

        val callback = object : PredictCallback {
            override fun onToken(token: String) {
                trySend(PredictionEvent.Token(token))
            }

            override fun onComplete(tokensPerSecond: Double, durationInSeconds: Long) {
                trySend(PredictionEvent.Completion(tokensPerSecond, durationInSeconds))
                close()
            }

            override fun onError(error: String) {
                close(RuntimeException(error))
            }
        }

        // This call is blocking on the current thread until the native side calls onComplete or onError
        LlamaApi.predict(sessionPtr, prompt, modelParameter, callback)

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

    fun restoreHistory(messages: Array<Any>) {
        if (sessionPtr != 0L) {
            LlamaApi.restoreHistory(sessionPtr, messages)
        }
    }
}