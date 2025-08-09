package com.nikolaspaci.app.llamallmlocal.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val modelPath: String,
    val timestamp: Long = System.currentTimeMillis()
)
