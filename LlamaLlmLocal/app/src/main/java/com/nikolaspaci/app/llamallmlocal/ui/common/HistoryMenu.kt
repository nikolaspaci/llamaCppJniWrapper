package com.nikolaspaci.app.llamallmlocal.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.data.database.ConversationWithMessages
import com.nikolaspaci.app.llamallmlocal.data.database.Sender
import com.nikolaspaci.app.llamallmlocal.viewmodel.HistoryUiState
import com.nikolaspaci.app.llamallmlocal.viewmodel.HistoryViewModel
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryMenuItems(
    viewModel: HistoryViewModel,
    onConversationClick: (Long) -> Unit,
    onCloseMenu: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
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

    when (val state = uiState) {
        is HistoryUiState.Loading -> {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is HistoryUiState.Success -> {
            if (state.conversations.isEmpty()) {
                ListItem(
                    headlineContent = { Text("No conversations yet.") }
                )
            } else {
                LazyColumn {
                    items(state.conversations) { conversation ->
                        val title = conversation.messages
                            .firstOrNull { it.sender == Sender.USER }
                            ?.let { "'${it.message.take(25)}...'" }
                            ?: "Conversation #${conversation.conversation.id}"

                        ListItem(
                            headlineContent = { Text(title) },
                            supportingContent = {
                                Column {
                                    Text(
                                        text = "Model: ${File(conversation.conversation.modelPath).name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Messages: ${conversation.messages.size}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = {
                                    onConversationClick(conversation.conversation.id)
                                    onCloseMenu()
                                },
                                onLongClick = {
                                    conversationToDelete = conversation
                                }
                            )
                        )
                    }
                }
            }
        }
        is HistoryUiState.Error -> {
            ListItem(
                headlineContent = { Text(state.message, color = MaterialTheme.colorScheme.error) }
            )
        }
    }
}

