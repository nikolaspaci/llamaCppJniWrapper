package com.nikolaspaci.app.llamallmlocal.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.R
import com.nikolaspaci.app.llamallmlocal.data.huggingface.WhisperModelVariant

@Composable
fun WhisperModelPickerDialog(
    onPick: (WhisperModelVariant) -> Unit,
    onDismiss: () -> Unit,
    currentVariant: WhisperModelVariant? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whisper_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.whisper_picker_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                VariantOption(
                    label = stringResource(R.string.whisper_picker_tiny),
                    description = stringResource(R.string.whisper_picker_tiny_desc),
                    onClick = { onPick(WhisperModelVariant.TINY) },
                    isRecommended = false,
                    isCurrent = currentVariant == WhisperModelVariant.TINY
                )
                VariantOption(
                    label = stringResource(R.string.whisper_picker_base),
                    description = stringResource(R.string.whisper_picker_base_desc),
                    onClick = { onPick(WhisperModelVariant.BASE) },
                    isRecommended = true,
                    isCurrent = currentVariant == WhisperModelVariant.BASE
                )
                VariantOption(
                    label = stringResource(R.string.whisper_picker_small),
                    description = stringResource(R.string.whisper_picker_small_desc),
                    onClick = { onPick(WhisperModelVariant.SMALL) },
                    isRecommended = false,
                    isCurrent = currentVariant == WhisperModelVariant.SMALL
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun VariantOption(
    label: String,
    description: String,
    onClick: () -> Unit,
    isRecommended: Boolean = false,
    isCurrent: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isRecommended)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
        border = if (isCurrent)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isRecommended)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isRecommended)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isCurrent) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
