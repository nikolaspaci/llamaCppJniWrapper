package com.nikolaspaci.app.llamallmlocal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.data.database.AppDatabase
import com.nikolaspaci.app.llamallmlocal.data.repository.ChatRepository
import com.nikolaspaci.app.llamallmlocal.data.repository.ModelParameterRepository
import com.nikolaspaci.app.llamallmlocal.jni.LlamaJniService
import com.nikolaspaci.app.llamallmlocal.ui.chat.ChatScreen
import com.nikolaspaci.app.llamallmlocal.ui.common.HistoryMenuItems
import com.nikolaspaci.app.llamallmlocal.ui.home.HomeChatScreen
import com.nikolaspaci.app.llamallmlocal.ui.settings.SettingsScreen
import com.nikolaspaci.app.llamallmlocal.viewmodel.ChatViewModelFactory
import com.nikolaspaci.app.llamallmlocal.viewmodel.HistoryViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelFileViewModel
import com.nikolaspaci.app.llamallmlocal.viewmodel.ModelFileViewModelFactory
import com.nikolaspaci.app.llamallmlocal.viewmodel.SettingsViewModelFactory
import com.nikolaspaci.app.llamallmlocal.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: Long): String {
            return "chat/$conversationId"
        }
    }
    object Settings : Screen("settings/{modelId}") {
        fun createRoute(modelId: String): String {
            return "settings/$modelId"
        }
    }
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
            LocalContext.current.getSharedPreferences("app_prefs", 0),
            LlamaJniService
        )
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column {
                    NavigationDrawerItem(
                        label = { Text("New Chat") },
                        selected = navController.currentDestination?.route == Screen.Home.route,
                        onClick = {
                            navController.navigate(Screen.Home.route)
                            scope.launch { drawerState.close() }
                        }
                    )
                    HorizontalDivider()
                    Text(
                        text = "Chats",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    HistoryMenuItems(
                        viewModel = historyViewModel,
                        onConversationClick = { conversationId ->
                            navController.navigate(Screen.Chat.createRoute(conversationId))
                            scope.launch { drawerState.close() }
                        },
                        onCloseMenu = {
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        }
    ) {
        NavHost(navController = navController, startDestination = Screen.Home.route) {
            composable(Screen.Home.route) {
                HomeChatScreen(
                    homeViewModel = viewModel(factory = factory),
                    modelFileViewModel = modelFileViewModel,
                    onStartChat = { conversationId ->
                        navController.navigate(Screen.Chat.createRoute(conversationId))
                    },
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
            composable(
                route = Screen.Chat.route,
                arguments = listOf(
                    navArgument("conversationId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: 0

                val context = LocalContext.current
                val db = AppDatabase.getDatabase(context)
                val chatRepository = ChatRepository(db.chatDao())

                val chatViewModelFactory = ChatViewModelFactory(
                    chatRepository,
                    conversationId
                )

                ChatScreen(
                    viewModel = viewModel(factory = chatViewModelFactory),
                    modelFileViewModel = modelFileViewModel,
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    },
                    onNavigateToSettings = { modelId ->
                        navController.navigate(Screen.Settings.createRoute(modelId))
                    }
                )
            }
            composable(
                route = Screen.Settings.route,
                arguments = listOf(
                    navArgument("modelId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val modelId = backStackEntry.arguments?.getString("modelId") ?: ""
                val context = LocalContext.current
                val db = AppDatabase.getDatabase(context)
                val modelParameterRepository = ModelParameterRepository(db.modelParameterDao())
                val settingsViewModelFactory = SettingsViewModelFactory(modelParameterRepository, modelId)
                SettingsScreen(
                    navController = navController,
                    viewModel = viewModel(factory = settingsViewModelFactory)
                )
            }
        }
    }
}

