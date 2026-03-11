package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun StreamingMessageBubble(
    text: String,
    tokensGenerated: Int,
    currentThinking: String = "",
    isThinking: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showCursor by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            showCursor = !showCursor
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .animateContentSize()
    ) {
        // Show thinking content if available
        if (currentThinking.isNotEmpty()) {
            CollapsibleThinkingContent(
                thinkingContent = currentThinking,
                isStreaming = isThinking
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (text.isEmpty() && !isThinking && currentThinking.isEmpty()) {
            ThinkingIndicator()
        } else if (text.isNotEmpty()) {
            StreamingMarkdownContent(
                content = text + if (showCursor) "\u258B" else " ",
                isStreaming = true
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$tokensGenerated tokens",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        } else if (isThinking) {
            // Still thinking, no response text yet - just show token count
            Text(
                text = "$tokensGenerated tokens",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
