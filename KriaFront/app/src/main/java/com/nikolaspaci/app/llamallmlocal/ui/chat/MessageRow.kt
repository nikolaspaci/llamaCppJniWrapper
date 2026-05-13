package com.nikolaspaci.app.llamallmlocal.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.nikolaspaci.app.llamallmlocal.R
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.Sender
import com.nikolaspaci.app.llamallmlocal.viewmodel.Stats
import java.io.File

@Composable
fun MessageRow(
    message: ChatMessage,
    stats: Stats? = null,
    onCopyMessage: ((String) -> Unit)? = null,
    onRegenerateMessage: (() -> Unit)? = null,
    onImageClick: ((File) -> Unit)? = null
) {
    val context = LocalContext.current

    if (message.sender == Sender.USER) {
        // User message: pill-shaped bubble aligned right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    // Show attached image if present
                    val imageFile = message.mediaPath?.let { File(context.filesDir, it) }
                    if (imageFile != null && imageFile.exists()) {
                        Image(
                            painter = rememberAsyncImagePainter(imageFile),
                            contentDescription = stringResource(R.string.chat_attached_image),
                            modifier = Modifier
                                .size(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (onImageClick != null) {
                                        Modifier.clickable { onImageClick(imageFile) }
                                    } else Modifier
                                ),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = message.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    } else {
        // Bot message: full-width, no card, directly on background
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Show thinking content if available
            if (message.thinkingContent.isNotEmpty()) {
                CollapsibleThinkingContent(
                    thinkingContent = message.thinkingContent,
                    isStreaming = false
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            MarkdownContent(content = message.message)

            // AI Action Row
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                IconButton(
                    onClick = {
                        if (onCopyMessage != null) {
                            onCopyMessage(message.message)
                        } else {
                            copyToClipboard(context, message.message)
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.common_copy),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                if (onRegenerateMessage != null) {
                    IconButton(
                        onClick = onRegenerateMessage,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.chat_regenerate),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Stats line
            if (stats != null) {
                Text(
                    text = stringResource(
                        R.string.chat_stats_line,
                        stats.tokensPerSecond,
                        stats.durationInSeconds,
                        stats.totalTokens
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("message", text)
    clipboard.setPrimaryClip(clip)
}
