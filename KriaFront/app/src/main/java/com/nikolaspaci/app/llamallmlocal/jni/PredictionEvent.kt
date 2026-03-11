package com.nikolaspaci.app.llamallmlocal.jni

sealed class PredictionEvent {
    data class Token(
        val value: String,
        val tokenIndex: Int = 0
    ) : PredictionEvent()

    data class ThinkingToken(
        val value: String
    ) : PredictionEvent()

    data class Progress(
        val tokensGenerated: Int,
        val estimatedTotal: Int? = null
    ) : PredictionEvent()

    data class Completion(
        val tokensPerSecond: Double,
        val durationInSeconds: Long,
        val totalTokens: Int = 0
    ) : PredictionEvent()

    data class Error(
        val message: String,
        val isRecoverable: Boolean = false
    ) : PredictionEvent()
}
