package com.nikolaspaci.app.llamallmlocal.ui.models

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.R
import com.nikolaspaci.app.llamallmlocal.data.repository.CachedModelEntry

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModelRow(
    entry: CachedModelEntry,
    isSelected: Boolean,
    isActive: Boolean,
    inSelectionMode: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    ListItem(
        leadingContent = if (inSelectionMode) {
            { Checkbox(checked = isSelected, onCheckedChange = { onToggle() }) }
        } else null,
        headlineContent = {
            Text(
                text = entry.file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = Formatter.formatFileSize(context, entry.sizeBytes),
                    style = MaterialTheme.typography.bodySmall
                )
                if (isActive) {
                    BadgePill(
                        text = stringResource(R.string.models_badge_active),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                if (entry.isMmproj) {
                    BadgePill(
                        text = stringResource(R.string.models_badge_mmproj),
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        },
        colors = if (isSelected) ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ) else ListItemDefaults.colors(),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (inSelectionMode) onToggle() },
                onLongClick = onLongPress
            )
    )
}

@Composable
private fun BadgePill(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = Modifier
            .background(color = container, shape = RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
