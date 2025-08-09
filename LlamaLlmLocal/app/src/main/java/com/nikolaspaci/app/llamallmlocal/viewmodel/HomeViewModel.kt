package com.nikolaspaci.app.llamallmlocal.viewmodel

import androidx.lifecycle.ViewModel
import com.nikolaspaci.app.llamallmlocal.data.database.Conversation
import com.nikolaspaci.app.llamallmlocal.data.repository.ChatRepository

class HomeViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    suspend fun startNewConversation(modelPath: String): Long {
        val conversation = Conversation(modelPath = modelPath)
        return chatRepository.insertConversation(conversation)
    }
}
