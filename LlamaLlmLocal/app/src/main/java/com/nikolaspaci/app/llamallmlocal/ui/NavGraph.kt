package com.nikolaspaci.app.llamallmlocal.ui

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
import com.nikolaspaci.app.llamallmlocal.data.database.AppDatabase
import com.nikolaspaci.app.llamallmlocal.data.repository.ChatRepository
import com.nikolaspaci.app.llamallmlocal.jni.LlamaJniService
import com.nikolaspaci.app.llamallmlocal.ui.chat.ChatScreen
import com.nikolaspaci.app.llamallmlocal.ui.history.HistoryScreen
import com.nikolaspaci.app.llamallmlocal.ui.settings.SettingsScreen
import com.nikolaspaci.app.llamallmlocal.viewmodel.ChatViewModelFactory
import com.nikolaspaci.app.llamallmlocal.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object History : Screen("history")
    object Settings : Screen("settings")
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: Long) = "chat/$conversationId"
    }
}

@Composable
fun AppNavigation(factory: ViewModelFactory) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                NavigationDrawerItem(
                    label = { Text("History") },
                    selected = navController.currentDestination?.route == Screen.History.route,
                    onClick = {
                        navController.navigate(Screen.History.route)
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = navController.currentDestination?.route == Screen.Settings.route,
                    onClick = {
                        navController.navigate(Screen.Settings.route)
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        NavHost(navController = navController, startDestination = Screen.History.route) {
            composable(Screen.History.route) {
                HistoryScreen(
                    viewModel = viewModel(factory = factory),
                    settingsViewModel = viewModel(factory = factory),
                    onConversationClick = { conversationId ->
                        navController.navigate(Screen.Chat.createRoute(conversationId))
                    },
                    onNewConversation = { newConversationId ->
                        navController.navigate(Screen.Chat.createRoute(newConversationId))
                    },
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel(factory = factory))
            }
            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("conversationId") { type = NavType.LongType })
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: 0

                val context = LocalContext.current
                val db = AppDatabase.getDatabase(context)
                val chatRepository = ChatRepository(db.chatDao())
                val llamaJniService = LlamaJniService()

                val chatViewModelFactory = ChatViewModelFactory(
                    chatRepository,
                    llamaJniService,
                    conversationId
                )

                ChatScreen(
                    viewModel = viewModel(factory = chatViewModelFactory),
                    settingsViewModel = viewModel(factory = factory),
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}