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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.R
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
    onCloseMenu: () -> Unit,
    onConversationDeleted: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var conversationToDelete by remember { mutableStateOf<ConversationWithMessages?>(null) }

    if (conversationToDelete != null) {
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_message)) },
            confirmButton = {
                Button(onClick = {
                    conversationToDelete?.let {
                        val deletedId = it.conversation.id
                        viewModel.deleteConversation(it)
                        onConversationDeleted(deletedId)
                    }
                    conversationToDelete = null // Dismiss dialog
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                Button(onClick = { conversationToDelete = null }) { Text(stringResource(R.string.common_cancel)) }
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
            val searchQuery by viewModel.searchQuery.collectAsState()
            if (state.conversations.isEmpty()) {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(
                                if (searchQuery.isNotBlank()) R.string.history_no_match
                                else R.string.history_no_conversations
                            )
                        )
                    }
                )
            } else {
                LazyColumn {
                    items(state.conversations) { conversation ->
                        val title = conversation.messages
                            .firstOrNull { it.sender == Sender.USER }
                            ?.let { "'${it.message.take(25)}...'" }
                            ?: stringResource(R.string.history_conversation_label, conversation.conversation.id)

                        ListItem(
                            headlineContent = { Text(title) },
                            supportingContent = {
                                Column {
                                    Text(
                                        text = stringResource(R.string.history_model_prefix, File(conversation.conversation.modelPath).name),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = stringResource(R.string.history_messages_count, conversation.messages.size),
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

