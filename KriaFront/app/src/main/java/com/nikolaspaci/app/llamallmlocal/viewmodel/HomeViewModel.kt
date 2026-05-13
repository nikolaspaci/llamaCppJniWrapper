package com.nikolaspaci.app.llamallmlocal.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.nikolaspaci.app.llamallmlocal.data.ImageStorageManager
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.Conversation
import com.nikolaspaci.app.llamallmlocal.data.database.Sender
import com.nikolaspaci.app.llamallmlocal.data.repository.ChatRepository
import com.nikolaspaci.app.llamallmlocal.engine.ModelParameterProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val parameterProvider: ModelParameterProvider,
    private val imageStorageManager: ImageStorageManager
) : ViewModel() {

    suspend fun startNewConversation(
        modelPath: String,
        firstMessage: String,
        imageUri: Uri? = null
    ): Long {
        val conversation = Conversation(modelPath = modelPath)
        val conversationId = chatRepository.insertConversation(conversation)
        parameterProvider.ensureConversationParameters(conversationId, modelPath)

        val stored = imageUri?.let { uri ->
            runCatching { imageStorageManager.copyFromUri(uri, conversationId) }.getOrNull()
        }

        val chatMessage = ChatMessage(
            conversationId = conversationId,
            sender = Sender.USER,
            message = firstMessage,
            mediaPath = stored?.relativePath,
            mediaType = stored?.mimeType
        )
        chatRepository.addMessageToConversation(chatMessage)
        return conversationId
    }
}
