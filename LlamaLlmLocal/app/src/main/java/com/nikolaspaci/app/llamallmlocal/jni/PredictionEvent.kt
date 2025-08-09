package com.nikolaspaci.app.llamallmlocal.jni

sealed class PredictionEvent {
    data class Token(val value: String) : PredictionEvent()
    data class Completion(val tokensPerSecond: Double, val durationInSeconds: Long) : PredictionEvent()
}
