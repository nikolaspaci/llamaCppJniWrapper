package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.ui.common.AppTopAppBar
import com.nikolaspaci.app.llamallmlocal.ui.common.MessageInput
import com.nikolaspaci.app.llamallmlocal.ui.common.ModelSelector
import com.nikolaspaci.app.llamallmlocal.viewmodel.ChatUiState
import com.nikolaspaci.app.llamallmlocal.viewmodel.ChatViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelFileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modelFileViewModel: ModelFileViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToSettings: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val conversation by viewModel.conversation.collectAsState()
    var selectedModelPath by remember(conversation) {
        mutableStateOf(conversation?.modelPath ?: "")
    }


    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            AppTopAppBar(
                title = "",
                onOpenDrawer = onOpenDrawer,
                onNavigateToSettings = {
                    onNavigateToSettings(selectedModelPath)
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.padding(8.dp)) {
                ModelSelector(
                    modelFileViewModel = modelFileViewModel,
                    selectedModelPath = selectedModelPath,
                    onModelSelected = {
                        selectedModelPath = it
                        viewModel.changeModel(it)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                val isModelReady by viewModel.isModelReady.collectAsState()
                MessageInput(
                    onSendMessage = { viewModel.sendMessage(it) },
                    isEnabled = isModelReady
                )
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is ChatUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)
                )
            }

            is ChatUiState.Success -> {
                MessageList(
                    messages = state.messages,
                    streamingMessage = state.streamingMessage,
                    lastMessageStats = state.lastMessageStats,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            is ChatUiState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)
                )
            }
        }
    }
}

