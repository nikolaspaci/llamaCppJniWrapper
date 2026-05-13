package com.nikolaspaci.app.llamallmlocal.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nikolaspaci.app.llamallmlocal.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nikolaspaci.app.llamallmlocal.ui.chat.ChatScreen
import com.nikolaspaci.app.llamallmlocal.ui.common.HistoryMenuItems
import com.nikolaspaci.app.llamallmlocal.ui.common.SearchBar
import com.nikolaspaci.app.llamallmlocal.ui.home.HomeChatScreen
import com.nikolaspaci.app.llamallmlocal.ui.huggingface.HuggingFaceScreen
import com.nikolaspaci.app.llamallmlocal.ui.models.ModelManagementScreen
import com.nikolaspaci.app.llamallmlocal.ui.settings.AppSettingsScreen
import com.nikolaspaci.app.llamallmlocal.ui.settings.ImagesSettingsScreen
import com.nikolaspaci.app.llamallmlocal.ui.settings.SettingsScreen
import com.nikolaspaci.app.llamallmlocal.ui.settings.VoiceSettingsScreen
import com.nikolaspaci.app.llamallmlocal.viewmodel.ChatViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.HistoryViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.HuggingFaceViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelFileViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelFileViewModelFactory
import com.nikolaspaci.app.llamallmlocal.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: Long): String {
            return "chat/$conversationId"
        }
    }
    object Settings : Screen("settings/{modelId}?conversationId={conversationId}") {
        fun createRoute(modelId: String): String {
            return "settings/${Uri.encode(modelId)}"
        }
        fun createRouteForConversation(modelId: String, conversationId: Long): String {
            return "settings/${Uri.encode(modelId)}?conversationId=$conversationId"
        }
    }
    object HuggingFace : Screen("huggingface")
    object ModelManagement : Screen("model_management")
    object AppSettings : Screen("app_settings")
    object VoiceSettings : Screen("voice_settings")
    object ImagesSettings : Screen("images_settings")
}

@Composable
fun AppNavigation(factory: ViewModelFactory) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val historyViewModel: HistoryViewModel = viewModel(factory = factory)
    val modelFileViewModel: ModelFileViewModel = viewModel(
        factory = ModelFileViewModelFactory(
            LocalContext.current,
            LocalContext.current.getSharedPreferences("app_prefs", 0)
        )
    )

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(Screen.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column {
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(R.string.nav_new_chat)) },
                        selected = currentRoute == Screen.Home.route,
                        onClick = {
                            navigateTopLevel(Screen.Home.route)
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(R.string.nav_app_settings)) },
                        selected = currentRoute == Screen.AppSettings.route,
                        onClick = {
                            navigateTopLevel(Screen.AppSettings.route)
                            scope.launch { drawerState.close() }
                        }
                    )
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.nav_chats),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                    val searchQuery by historyViewModel.searchQuery.collectAsState()
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = historyViewModel::updateSearchQuery,
                        placeholder = stringResource(R.string.nav_search_conversations),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    HistoryMenuItems(
                        viewModel = historyViewModel,
                        onConversationClick = { conversationId ->
                            navigateTopLevel(Screen.Chat.createRoute(conversationId))
                            scope.launch { drawerState.close() }
                        },
                        onCloseMenu = {
                            scope.launch { drawerState.close() }
                        },
                        onConversationDeleted = { deletedId ->
                            val currentRoute = navController.currentDestination?.route
                            val currentArgs = navController.currentBackStackEntry?.arguments
                            if (currentRoute == Screen.Chat.route &&
                                currentArgs?.getLong("conversationId") == deletedId
                            ) {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Home.route) { inclusive = true }
                                }
                                scope.launch { drawerState.close() }
                            }
                        }
                    )
                }
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Screen.Home.route) { backStackEntry ->
                val savedStateHandle = backStackEntry.savedStateHandle
                val selectedModelFromSettings by savedStateHandle.getStateFlow<String?>("selected_model_path", null)
                    .collectAsState()

                HomeChatScreen(
                    homeViewModel = hiltViewModel(),
                    modelFileViewModel = modelFileViewModel,
                    onStartChat = { conversationId ->
                        navController.navigate(Screen.Chat.createRoute(conversationId))
                    },
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    },
                    onNavigateToManageModels = {
                        navController.navigate(Screen.ModelManagement.route)
                    },
                    onNavigateToSettings = { modelId ->
                        navController.navigate(Screen.Settings.createRoute(modelId))
                    },
                    updatedModelPath = selectedModelFromSettings,
                    onUpdatedModelPathConsumed = {
                        savedStateHandle.remove<String>("selected_model_path")
                    }
                )
            }
            composable(
                route = Screen.Chat.route,
                arguments = listOf(
                    navArgument("conversationId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val chatViewModel: ChatViewModel = hiltViewModel()
                val savedStateHandle = backStackEntry.savedStateHandle
                val selectedModelPath by savedStateHandle.getStateFlow<String?>("selected_model_path", null)
                    .collectAsState()

                LaunchedEffect(selectedModelPath) {
                    selectedModelPath?.let { path ->
                        chatViewModel.changeModel(path)
                        savedStateHandle.remove<String>("selected_model_path")
                    }
                }

                val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: 0L

                ChatScreen(
                    viewModel = chatViewModel,
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    },
                    onNewChat = {
                        navigateTopLevel(Screen.Home.route)
                    },
                    onNavigateToSettings = { modelId ->
                        navController.navigate(Screen.Settings.createRouteForConversation(modelId, conversationId))
                    }
                )
            }
            composable(Screen.HuggingFace.route) {
                val huggingFaceViewModel: HuggingFaceViewModel = hiltViewModel()
                HuggingFaceScreen(
                    viewModel = huggingFaceViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onModelDownloaded = {
                        modelFileViewModel.loadCachedModels()
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.ModelManagement.route) {
                ModelManagementScreen(
                    viewModel = hiltViewModel(),
                    modelFileViewModel = modelFileViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToHuggingFace = { navController.navigate(Screen.HuggingFace.route) }
                )
            }
            composable(Screen.AppSettings.route) {
                AppSettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToManageModels = { navController.navigate(Screen.ModelManagement.route) },
                    onNavigateToVoiceSettings = { navController.navigate(Screen.VoiceSettings.route) },
                    onNavigateToImagesSettings = { navController.navigate(Screen.ImagesSettings.route) }
                )
            }
            composable(Screen.VoiceSettings.route) {
                VoiceSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.ImagesSettings.route) {
                ImagesSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.Settings.route,
                arguments = listOf(
                    navArgument("modelId") { type = NavType.StringType },
                    navArgument("conversationId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { backStackEntry ->
                val modelId = backStackEntry.arguments?.getString("modelId") ?: ""
                val settingsConversationId = backStackEntry.arguments?.getLong("conversationId") ?: -1L
                SettingsScreen(
                    modelId = modelId,
                    conversationId = if (settingsConversationId != -1L) settingsConversationId else null,
                    modelFileViewModel = modelFileViewModel,
                    onModelChanged = { newPath ->
                        navController.previousBackStackEntry?.savedStateHandle?.set("selected_model_path", newPath)
                    },
                    onNavigateToManageModels = {
                        navController.navigate(Screen.ModelManagement.route)
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
