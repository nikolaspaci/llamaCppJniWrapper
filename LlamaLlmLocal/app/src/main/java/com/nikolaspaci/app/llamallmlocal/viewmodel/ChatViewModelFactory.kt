package com.nikolaspaci.app.llamallmlocal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nikolaspaci.app.llamallmlocal.data.repository.ChatRepository
import com.nikolaspaci.app.llamallmlocal.jni.LlamaJniService

class ChatViewModelFactory(
    private val chatRepository: ChatRepository,
    private val llamaJniService: LlamaJniService,
    private val conversationId: Long,
    private val initialMessage: String?
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(chatRepository, llamaJniService, conversationId, initialMessage) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
