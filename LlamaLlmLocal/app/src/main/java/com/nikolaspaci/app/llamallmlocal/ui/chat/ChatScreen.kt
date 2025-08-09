package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.ChatViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.ChatUiState
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.Sender

import com.nikolaspaci.app.llamallmlocal.ui.common.AppTopAppBar
import com.nikolaspaci.app.llamallmlocal.ui.common.MessageInput
import com.nikolaspaci.app.llamallmlocal.ui.common.ModelSelector
import com.nikolaspaci.app.llamallmlocal.ui.settings.ModelSelectionDialog
import com.nikolaspaci.app.llamallmlocal.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenDrawer: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val conversation by viewModel.conversation.collectAsState()
    var selectedModelPath by remember(conversation) {
        mutableStateOf(conversation?.modelPath ?: "")
    }


    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            AppTopAppBar(
                title = "",
                onOpenDrawer = onOpenDrawer
            )
        },
        bottomBar = {
            Column(modifier = Modifier.padding(8.dp)) {
                ModelSelector(
                    settingsViewModel = settingsViewModel,
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


@Composable
fun MessageList(
    messages: List<ChatMessage>,
    streamingMessage: String?,
    lastMessageStats: com.nikolaspaci.app.llamallmlocal.viewmodel.Stats?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        state = listState,
        modifier = modifier.padding(8.dp)
    ) {
        itemsIndexed(messages) { index, message ->
            val isLastMessage = index == messages.lastIndex
            MessageRow(
                message = message,
                stats = if (isLastMessage && message.sender == Sender.BOT) lastMessageStats else null
            )
        }
        if (streamingMessage != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        if (streamingMessage.isEmpty()) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        } else {
                            Text(
                                text = streamingMessage,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(messages.size, streamingMessage) {
        val totalItems = messages.size + if (streamingMessage != null) 1 else 0
        if (totalItems > 0) {
            coroutineScope.launch {
                listState.animateScrollToItem(totalItems - 1)
            }
        }
    }
}

@Composable
fun MessageRow(message: ChatMessage, stats: com.nikolaspaci.app.llamallmlocal.viewmodel.Stats? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (message.sender == Sender.USER) Arrangement.End else Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (message.sender == Sender.USER) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = message.message
                )
                if (stats != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "%.2f t/s, %d s".format(stats.tokensPerSecond, stats.durationInSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
