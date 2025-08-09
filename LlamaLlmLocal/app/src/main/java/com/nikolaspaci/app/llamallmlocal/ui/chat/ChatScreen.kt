package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

import com.nikolaspaci.app.llamallmlocal.ui.settings.ModelSelectionDialog
import com.nikolaspaci.app.llamallmlocal.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showModelSelectionDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text("Delete Chat") },
            text = { Text("Are you sure you want to delete this chat? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.deleteConversation()
                            showDeleteConfirmationDialog = false
                            onNavigateBack()
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showModelSelectionDialog) {
        ModelSelectionDialog(
            viewModel = settingsViewModel,
            onDismissRequest = { showModelSelectionDialog = false },
            onModelSelected = { modelPath ->
                viewModel.changeModel(modelPath)
                showModelSelectionDialog = false
            }
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Change Model") },
                            onClick = {
                                showModelSelectionDialog = true
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Chat") },
                            onClick = {
                                showDeleteConfirmationDialog = true
                                showMenu = false
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            val isModelReady by viewModel.isModelReady.collectAsState()
            MessageInput(
                onSendMessage = { viewModel.sendMessage(it) },
                isEnabled = isModelReady
            )
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
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        state = listState,
        modifier = modifier.padding(8.dp)
    ) {
        items(messages) { message ->
            MessageRow(message)
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
fun MessageRow(message: ChatMessage) {
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
            Text(
                text = message.message,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun MessageInput(onSendMessage: (String) -> Unit, isEnabled: Boolean) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            label = { Text("Type a message") },
            enabled = isEnabled
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                if (text.isNotBlank()) {
                    onSendMessage(text)
                    text = ""
                }
            },
            enabled = isEnabled && text.isNotBlank()
        ) {
            Text("Send")
        }
    }
}