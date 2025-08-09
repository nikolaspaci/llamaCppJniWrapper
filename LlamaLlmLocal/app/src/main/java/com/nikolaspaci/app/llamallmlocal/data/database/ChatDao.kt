package com.nikolaspaci.app.llamallmlocal.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert
    suspend fun insertConversation(conversation: Conversation): Long

    @Insert
    suspend fun insertMessage(message: ChatMessage)

    @Transaction
    @Query("SELECT * FROM conversations ORDER BY timestamp DESC")
    fun getAllConversationsWithMessages(): Flow<List<ConversationWithMessages>>

    @Transaction
    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    fun getConversationWithMessages(conversationId: Long): Flow<ConversationWithMessages?>

    @Query("UPDATE conversations SET modelPath = :modelPath WHERE id = :conversationId")
    suspend fun updateConversationModelPath(conversationId: Long, modelPath: String)

    @Delete
    suspend fun deleteConversation(conversation: Conversation)
}

data class ConversationWithMessages(
    @androidx.room.Embedded
    val conversation: Conversation,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "conversationId"
    )
    val messages: List<ChatMessage>
)
