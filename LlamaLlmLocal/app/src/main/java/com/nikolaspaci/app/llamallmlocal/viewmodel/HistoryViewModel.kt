package com.nikolaspaci.app.llamallmlocal.viewmodel

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.database.Conversation
import com.nikolaspaci.app.llamallmlocal.data.database.ConversationWithMessages
import com.nikolaspaci.app.llamallmlocal.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val chatRepository: ChatRepository,
    private val sharedPreferences: SharedPreferences // Keep for other potential settings
) : ViewModel() {

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.getAllConversations().collect { conversations ->
                _uiState.value = HistoryUiState.Success(conversations)
            }
        }
    }

    suspend fun startNewConversation(modelPath: String): Long {
        val conversation = Conversation(modelPath = modelPath)
        return chatRepository.insertConversation(conversation)
    }
}

sealed class HistoryUiState {
    object Loading : HistoryUiState()
    data class Success(val conversations: List<ConversationWithMessages>) : HistoryUiState()
    data class Error(val message: String) : HistoryUiState()
}
