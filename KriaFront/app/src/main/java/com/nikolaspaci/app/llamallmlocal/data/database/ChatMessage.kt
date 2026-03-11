package com.nikolaspaci.app.llamallmlocal.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Sender {
    USER, BOT
}

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["timestamp"]),
        Index(value = ["conversationId", "timestamp"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val sender: Sender,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val thinkingContent: String = "",
    val mediaPath: String? = null,
    val mediaType: String? = null
)
