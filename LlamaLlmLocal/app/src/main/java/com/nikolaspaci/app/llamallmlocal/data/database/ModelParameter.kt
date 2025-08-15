package com.nikolaspaci.app.llamallmlocal.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "model_parameters")
data class ModelParameter(
    @PrimaryKey val modelId: String,
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val minP: Float = 0.05f
)
