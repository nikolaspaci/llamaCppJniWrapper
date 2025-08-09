package com.nikolaspaci.app.llamallmlocal.data.repository

import com.nikolaspaci.app.llamallmlocal.data.database.ChatDao
import com.nikolaspaci.app.llamallmlocal.data.database.Conversation
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.ConversationWithMessages
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {

    fun getAllConversations(): Flow<List<ConversationWithMessages>> {
        return chatDao.getAllConversationsWithMessages()
    }

    fun getConversation(conversationId: Long): Flow<ConversationWithMessages> {
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
}