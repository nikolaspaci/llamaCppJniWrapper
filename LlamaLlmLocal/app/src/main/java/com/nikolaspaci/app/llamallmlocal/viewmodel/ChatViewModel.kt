package com.nikolaspaci.app.llamallmlocal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.Sender
import com.nikolaspaci.app.llamallmlocal.data.repository.ChatRepository
import com.nikolaspaci.app.llamallmlocal.jni.LlamaJniService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.logging.Logger

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val llamaJniService: LlamaJniService,
    private val conversationId: Long,
    initialMessage: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private var initialMessageSent = false

    init {
        // Observe messages and update UI
        viewModelScope.launch {
            chatRepository.getConversation(conversationId).collect { conversationWithMessages ->
                if (conversationWithMessages != null) {
                    val currentUiState = _uiState.value
                    val streamingMessage = if (currentUiState is ChatUiState.Success) {
                        currentUiState.streamingMessage
                    } else {
                        null
                    }
                    _uiState.value = ChatUiState.Success(
                        messages = conversationWithMessages.messages,
                        streamingMessage = streamingMessage
                    )
                }
            }
        }

        // Observe model path and load model when it changes
        viewModelScope.launch {
            chatRepository.getConversation(conversationId)
                .map { it?.conversation?.modelPath }
                .distinctUntilChanged()
                .collect { modelPath ->
                    if (modelPath?.isNotEmpty() == true) {
                        _isModelReady.value = false
                        withContext(Dispatchers.IO) {
                            llamaJniService.loadModel(modelPath)
                        }
                        _isModelReady.value = true
                        // If there's an initial message, send it after the model is loaded.
                        if (initialMessage != null && !initialMessageSent) {
                            sendMessage(initialMessage)
                            initialMessageSent = true
                        }
                    } else {
                        _isModelReady.value = false
                    }
                }
        }
    }

    fun sendMessage(text: String) {
        val userMessage = ChatMessage(
            conversationId = conversationId,
            sender = Sender.USER,
            message = text
        )
        viewModelScope.launch {
            chatRepository.addMessageToConversation(userMessage)

            // Show loading indicator for bot response
            (_uiState.value as? ChatUiState.Success)?.let {
                _uiState.value = it.copy(streamingMessage = "")
            }

            // Get bot response
            val botMessageFlow = llamaJniService.predict(text)
            var accumulatedResponse = ""
            botMessageFlow.collect { response ->
                accumulatedResponse += response
                (_uiState.value as? ChatUiState.Success)?.let {
                    _uiState.value = it.copy(streamingMessage = accumulatedResponse)
                }
            }

            // Save final bot message
            val botChatMessage = ChatMessage(
                conversationId = conversationId,
                sender = Sender.BOT,
                message = accumulatedResponse
            )
            chatRepository.addMessageToConversation(botChatMessage)

            // Hide loading/streaming indicator
            (_uiState.value as? ChatUiState.Success)?.let {
                _uiState.value = it.copy(streamingMessage = null)
            }
        }
    }

    fun changeModel(modelPath: String) {
        viewModelScope.launch {
            chatRepository.updateConversationModel(conversationId, modelPath)
        }
    }

    suspend fun deleteConversation() {
        // We need to fetch the conversation object first to delete it
        chatRepository.getConversation(conversationId).firstOrNull()?.let {
            chatRepository.deleteConversation(it.conversation)
        }
    }

    override fun onCleared() {
        super.onCleared()
        llamaJniService.unloadModel()
    }
}

sealed class ChatUiState {
    object Loading : ChatUiState()
    data class Success(
        val messages: List<ChatMessage>,
        val streamingMessage: String? = null
    ) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}