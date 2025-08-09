package com.nikolaspaci.app.llamallmlocal.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.data.database.ConversationWithMessages
import com.nikolaspaci.app.llamallmlocal.ui.settings.ModelSelectionDialog
import com.nikolaspaci.app.llamallmlocal.viewmodel.HistoryViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.HistoryUiState
import com.nikolaspaci.app.llamallmlocal.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    settingsViewModel: SettingsViewModel,
    onConversationClick: (Long) -> Unit,
    onNewConversation: (Long) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        ModelSelectionDialog(
            viewModel = settingsViewModel,
            onDismissRequest = { showDialog = false },
            onModelSelected = { modelPath ->
                showDialog = false
                // Load the model into memory via the JNI
                settingsViewModel.saveModelPath(modelPath)
                // Create the conversation record in the database
                scope.launch {
                    val newConversationId = viewModel.startNewConversation(modelPath)
                    onNewConversation(newConversationId)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New Conversation")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val state = uiState) {
                is HistoryUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is HistoryUiState.Success -> {
                    if (state.conversations.isEmpty()) {
                        EmptyHistoryView()
                    } else {
                        ConversationList(
                            conversations = state.conversations,
                            onConversationClick = onConversationClick
                        )
                    }
                }
                is HistoryUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No conversations yet. Tap the + button to start a new one!")
    }
}

@Composable
fun ConversationList(
    conversations: List<ConversationWithMessages>,
    onConversationClick: (Long) -> Unit
) {
    LazyColumn(modifier = Modifier.padding(8.dp)) {
        items(conversations) { conversation ->
            ConversationRow(conversation, onConversationClick)
        }
    }
}

@Composable
fun ConversationRow(
    conversationWithMessages: ConversationWithMessages,
    onConversationClick: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onConversationClick(conversationWithMessages.conversation.id) }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "Conversation #${conversationWithMessages.conversation.id}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Model: ${File(conversationWithMessages.conversation.modelPath).name}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            Text(
                text = "Messages: ${conversationWithMessages.messages.size}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}