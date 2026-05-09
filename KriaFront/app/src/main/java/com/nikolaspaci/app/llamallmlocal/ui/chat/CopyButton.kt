package com.nikolaspaci.app.llamallmlocal.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nikolaspaci.app.llamallmlocal.R

@Composable
fun CopyButton(
    textToCopy: String,
    modifier: Modifier = Modifier,
    onCopied: () -> Unit = {}
) {
    val context = LocalContext.current
    val copiedToast = stringResource(R.string.common_copied_toast)
    val copyDescription = stringResource(R.string.common_copy)

    IconButton(
        onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("text", textToCopy)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
            onCopied()
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = copyDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
