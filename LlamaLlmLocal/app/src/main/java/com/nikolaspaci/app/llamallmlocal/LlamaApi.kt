package com.nikolaspaci.app.llamallmlocal

object LlamaApi {
    init {
        System.loadLibrary("jniLlamaCppWrapper")
    }

    external fun init(modelPath: String): Long
    external fun predict(sessionPtr: Long, prompt: String): String
    external fun free(sessionPtr: Long)
}
