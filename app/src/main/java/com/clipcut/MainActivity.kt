package com.clipcut

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clipcut.core.log.AppLog
import com.clipcut.ui.nav.Screen
import com.clipcut.ui.screens.EditorScreen
import com.clipcut.ui.screens.LogViewerScreen
import com.clipcut.ui.screens.ProjectListScreen
import com.clipcut.ui.theme.ClipCutTheme
import com.clipcut.ui.viewmodel.EditorViewModel
import com.clipcut.ui.viewmodel.ProjectListViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLog.i(TAG, "MainActivity created")

        // Handle share intent
        if (intent?.action == android.content.Intent.ACTION_SEND && intent.type != null) {
            val uri = if (android.os.Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
            if (uri != null) {
                AppLog.i(TAG, "Received shared media: $uri (type=${intent.type})")
            }
        }

        setContent {
            ClipCutTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = Screen.ProjectList.route
                ) {
                    composable(Screen.ProjectList.route) {
                        val vm: ProjectListViewModel = viewModel()
                        ProjectListScreen(
                            viewModel = vm,
                            onOpenProject = { projectId ->
                                navController.navigate(Screen.Editor.createRoute(projectId))
                            },
                            onOpenLogs = {
                                navController.navigate(Screen.LogViewer.route)
                            }
                        )
                    }

                    composable(
                        route = Screen.Editor.route,
                        arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                        val vm: EditorViewModel = viewModel()
                        EditorScreen(
                            projectId = projectId,
                            viewModel = vm,
                            onNavigateBack = { navController.popBackStack() },
                            onOpenLogs = { navController.navigate(Screen.LogViewer.route) }
                        )
                    }

                    composable(Screen.LogViewer.route) {
                        LogViewerScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
