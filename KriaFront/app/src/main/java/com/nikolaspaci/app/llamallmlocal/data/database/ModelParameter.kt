package com.nikolaspaci.app.llamallmlocal.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "model_parameters")
data class ModelParameter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Paramètres de sampling
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val minP: Float = 0.05f,
    // Nouveaux paramètres
    val contextSize: Int = 2048,
    val maxTokens: Int = 256,
    val threadCount: Int = 4,
    val repeatPenalty: Float = 1.1f,
    val useGpu: Boolean = false,
    val gpuLayers: Int = 99,
    val systemPrompt: String = "",
    val enableThinking: Boolean = true
) {
    companion object {
        // Limites de validation
        val TEMPERATURE_RANGE = 0f..2f
        val TOP_K_RANGE = 1..100
        val TOP_P_RANGE = 0f..1f
        val MIN_P_RANGE = 0f..1f
        val CONTEXT_SIZE_VALUES = listOf(512, 1024, 2048, 4096, 8192)
        val MAX_TOKENS_RANGE = 64..2048
        val REPEAT_PENALTY_RANGE = 1f..2f

        const val SYSTEM_PROMPT_MAX_LENGTH = 4096

        fun getMaxThreads(): Int = Runtime.getRuntime().availableProcessors()
    }
}
