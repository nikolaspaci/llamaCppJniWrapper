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
import com.nikolaspaci.app.llamallmlocal.ui.home.HomeChatScreen
import com.nikolaspaci.app.llamallmlocal.viewmodel.ChatViewModelFactory
import com.nikolaspaci.app.llamallmlocal.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object History : Screen("history")
    object Chat : Screen("chat/{conversationId}?initialMessage={initialMessage}") {
        fun createRoute(conversationId: Long, initialMessage: String? = null): String {
            val route = "chat/$conversationId"
            return if (initialMessage != null) {
                "$route?initialMessage=$initialMessage"
            } else {
                route
            }
        }
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
                    label = { Text("Home") },
                    selected = navController.currentDestination?.route == Screen.Home.route,
                    onClick = {
                        navController.navigate(Screen.Home.route)
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    label = { Text("History") },
                    selected = navController.currentDestination?.route == Screen.History.route,
                    onClick = {
                        navController.navigate(Screen.History.route)
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        NavHost(navController = navController, startDestination = Screen.Home.route) {
            composable(Screen.Home.route) {
                HomeChatScreen(
                    homeViewModel = viewModel(factory = factory),
                    settingsViewModel = viewModel(factory = factory),
                    onStartChat = { conversationId, initialMessage ->
                        navController.navigate(Screen.Chat.createRoute(conversationId, initialMessage))
                    },
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(
                    viewModel = viewModel(factory = factory),
                    onConversationClick = { conversationId ->
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
                    navArgument("conversationId") { type = NavType.LongType },
                    navArgument("initialMessage") {
                        type = NavType.StringType
                        nullable = true
                    }
                )
            ) { backStackEntry ->
                val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: 0
                val initialMessage = backStackEntry.arguments?.getString("initialMessage")

                val context = LocalContext.current
                val db = AppDatabase.getDatabase(context)
                val chatRepository = ChatRepository(db.chatDao())
                val llamaJniService = LlamaJniService()

                val chatViewModelFactory = ChatViewModelFactory(
                    chatRepository,
                    llamaJniService,
                    conversationId,
                    initialMessage
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
