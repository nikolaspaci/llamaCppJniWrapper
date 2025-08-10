package com.nikolaspaci.app.llamallmlocal.viewmodel

import androidx.lifecycle.ViewModel
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.Conversation
import com.nikolaspaci.app.llamallmlocal.data.database.Sender
import com.nikolaspaci.app.llamallmlocal.data.repository.ChatRepository

class HomeViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    suspend fun startNewConversation(modelPath: String, firstMessage: String): Long {
        val conversation = Conversation(modelPath = modelPath)
        val conversationId = chatRepository.insertConversation(conversation)
        val chatMessage = ChatMessage(
            conversationId = conversationId,
            sender = Sender.USER,
            message = firstMessage
        )
        chatRepository.addMessageToConversation(chatMessage)
        return conversationId
    }
}
