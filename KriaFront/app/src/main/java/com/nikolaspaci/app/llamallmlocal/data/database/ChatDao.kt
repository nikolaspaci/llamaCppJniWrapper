package com.nikolaspaci.app.llamallmlocal.data.database

import androidx.paging.PagingSource
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
    suspend fun insertChatMessage(message: ChatMessage): Long

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

    // Paged queries

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getPagedMessages(conversationId: Long): PagingSource<Int, ChatMessage>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: Long): Int

    @Query("""
        SELECT * FROM conversations
        ORDER BY lastUpdatedAt DESC
    """)
    fun getPagedConversations(): PagingSource<Int, Conversation>

    @Query("""
        UPDATE conversations
        SET lastUpdatedAt = :timestamp,
            lastMessagePreview = :preview,
            messageCount = messageCount + 1
        WHERE id = :conversationId
    """)
    suspend fun updateConversationMetadata(
        conversationId: Long,
        timestamp: Long,
        preview: String
    )

    @Query("UPDATE conversations SET title = :title WHERE id = :conversationId")
    suspend fun updateConversationTitle(conversationId: Long, title: String)

    @Query("UPDATE conversations SET modelParameterId = :modelParameterId WHERE id = :conversationId")
    suspend fun updateConversationModelParameterId(conversationId: Long, modelParameterId: Long)

    @Query("SELECT modelParameterId FROM conversations WHERE id = :conversationId")
    suspend fun getConversationModelParameterId(conversationId: Long): Long?

    @Query("UPDATE chat_messages SET mediaPath = NULL, mediaType = NULL WHERE mediaPath IS NOT NULL")
    suspend fun clearAllMediaReferences(): Int
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
