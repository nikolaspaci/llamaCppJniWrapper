package com.nikolaspaci.app.llamallmlocal.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.nikolaspaci.app.llamallmlocal.data.ImageStorageManager
import com.nikolaspaci.app.llamallmlocal.data.database.ChatDao
import com.nikolaspaci.app.llamallmlocal.data.database.Conversation
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.ConversationWithMessages
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val chatDao: ChatDao,
    private val imageStorageManager: ImageStorageManager
) {

    fun getAllConversations(): Flow<List<ConversationWithMessages>> {
        return chatDao.getAllConversationsWithMessages()
    }

    fun getConversation(conversationId: Long): Flow<ConversationWithMessages?> {
        return chatDao.getConversationWithMessages(conversationId)
    }

    suspend fun insertConversation(conversation: Conversation): Long {
        return chatDao.insertConversation(conversation)
    }

    suspend fun addMessageToConversation(message: ChatMessage) {
        chatDao.insertMessage(message)
    }

    suspend fun updateConversationModel(conversationId: Long, modelPath: String) {
        chatDao.updateConversationModelPath(conversationId, modelPath)
    }

    suspend fun deleteConversation(conversation: Conversation) {
        chatDao.deleteConversation(conversation)
        imageStorageManager.deleteConversationDir(conversation.id)
    }

    // Paged methods

    fun getPagedMessages(conversationId: Long): Flow<PagingData<ChatMessage>> {
        return Pager(
            config = PagingConfig(
                pageSize = 30,
                enablePlaceholders = false,
                prefetchDistance = 10,
                initialLoadSize = 50
            ),
            pagingSourceFactory = { chatDao.getPagedMessages(conversationId) }
        ).flow
    }

    fun getPagedConversations(): Flow<PagingData<Conversation>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 5
            ),
            pagingSourceFactory = { chatDao.getPagedConversations() }
        ).flow
    }

    suspend fun addMessageWithMetadata(message: ChatMessage): Long {
        val messageId = chatDao.insertChatMessage(message)

        val preview = message.message.take(100)
        chatDao.updateConversationMetadata(
            conversationId = message.conversationId,
            timestamp = message.timestamp,
            preview = preview
        )

        return messageId
    }

    suspend fun getMessageCount(conversationId: Long): Int {
        return chatDao.getMessageCount(conversationId)
    }

    suspend fun updateConversationModelParameterId(conversationId: Long, modelParameterId: Long) {
        chatDao.updateConversationModelParameterId(conversationId, modelParameterId)
    }

    suspend fun getConversationModelParameterId(conversationId: Long): Long? {
        return chatDao.getConversationModelParameterId(conversationId)
    }
}