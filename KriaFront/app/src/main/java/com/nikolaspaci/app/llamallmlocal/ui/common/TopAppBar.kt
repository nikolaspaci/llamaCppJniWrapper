package com.nikolaspaci.app.llamallmlocal.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nikolaspaci.app.llamallmlocal.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveTopBar(
    modelName: String = "",
    onOpenDrawer: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    onNewChat: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
) {
    CenterAlignedTopAppBar(
        title = {
            if (modelName.isNotEmpty()) {
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
            } else if (onOpenDrawer != null) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Rounded.Menu, contentDescription = stringResource(R.string.nav_menu))
                }
            }
        },
        actions = {
            if (onNewChat != null) {
                IconButton(onClick = onNewChat) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.nav_new_chat))
                }
            }
            if (onNavigateToSettings != null) {
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.nav_settings))
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

// Keep old name for any remaining references
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopAppBar(
    title: String,
    onOpenDrawer: (() -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
) {
    AdaptiveTopBar(
        modelName = title,
        onOpenDrawer = onOpenDrawer,
        onNavigateBack = onNavigateBack,
        onNavigateToSettings = onNavigateToSettings
    )
}
