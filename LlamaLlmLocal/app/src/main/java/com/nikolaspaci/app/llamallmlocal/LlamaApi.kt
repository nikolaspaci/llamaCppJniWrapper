package com.nikolaspaci.app.llamallmlocal

interface PredictCallback {
    fun onToken(token: String)
    fun onComplete()
    fun onError(error: String)
}
object LlamaApi {
    init {
        System.loadLibrary("jniLlamaCppWrapper")
    }

    external fun init(modelPath: String): Long
    external fun free(sessionPtr: Long)
    external fun predict(sessionPtr: Long, prompt: String, callback: PredictCallback)

}