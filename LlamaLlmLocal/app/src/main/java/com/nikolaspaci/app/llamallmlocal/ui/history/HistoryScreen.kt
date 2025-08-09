package com.nikolaspaci.app.llamallmlocal.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.nikolaspaci.app.llamallmlocal.data.database.Sender
import com.nikolaspaci.app.llamallmlocal.ui.common.AppTopAppBar
import com.nikolaspaci.app.llamallmlocal.ui.settings.ModelSelectionDialog
import com.nikolaspaci.app.llamallmlocal.viewmodel.HistoryViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.HistoryUiState
import com.nikolaspaci.app.llamallmlocal.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onConversationClick: (Long) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var conversationToDelete by remember { mutableStateOf<ConversationWithMessages?>(null) }

    if (conversationToDelete != null) {
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text("Delete Conversation") },
            text = { Text("Are you sure you want to permanently delete this conversation?") },
            confirmButton = {
                Button(onClick = {
                    conversationToDelete?.let { viewModel.deleteConversation(it) }
                    conversationToDelete = null // Dismiss dialog
                }) { Text("Delete") }
            },
            dismissButton = {
                Button(onClick = { conversationToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopAppBar(title = "History", onOpenDrawer = onOpenDrawer)
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
                            onConversationClick = onConversationClick,
                            onConversationLongPress = { conversation ->
                                conversationToDelete = conversation
                            }
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
        Text("No conversations yet. Go to the Home screen to start a new one!")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationList(
    conversations: List<ConversationWithMessages>,
    onConversationClick: (Long) -> Unit,
    onConversationLongPress: (ConversationWithMessages) -> Unit
) {
    LazyColumn(modifier = Modifier.padding(8.dp)) {
        items(conversations) { conversation ->
            ConversationRow(
                conversationWithMessages = conversation,
                onClick = { onConversationClick(conversation.conversation.id) },
                onLongClick = { onConversationLongPress(conversation) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationRow(
    conversationWithMessages: ConversationWithMessages,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val title = conversationWithMessages.messages
        .firstOrNull { it.sender == Sender.USER }
        ?.let { "Chat: '${it.message.take(25)}...'" }
        ?: "Conversation #${conversationWithMessages.conversation.id}"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = title,
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
