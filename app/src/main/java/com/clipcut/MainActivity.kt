package com.clipcut

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.clipcut.core.log.AppLog
import com.clipcut.ui.nav.Screen
import com.clipcut.ui.screens.EditorScreen
import com.clipcut.ui.screens.LogViewerScreen
import com.clipcut.ui.screens.OnboardingScreen
import com.clipcut.ui.screens.ProjectListScreen
import com.clipcut.ui.screens.SettingsScreen
import com.clipcut.ui.theme.ClipCutTheme
import com.clipcut.ui.viewmodel.EditorViewModel
import com.clipcut.ui.viewmodel.ProjectListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLog.i(TAG, "MainActivity.onCreate")
        setContent {
            ClipCutTheme {
                val navController = rememberNavController()
                val startRoute = remember { getStartRoute() }

                NavHost(navController = navController, startDestination = startRoute) {
                    composable(Screen.Onboarding.route) {
                        OnboardingScreen(onDone = {
                            writeOnboardingComplete()
                            navController.navigate(Screen.ProjectList.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        })
                    }
                    composable(Screen.ProjectList.route) {
                        val vm: ProjectListViewModel = viewModel()
                        ProjectListScreen(
                            viewModel = vm,
                            onOpenProject = { id ->
                                navController.navigate(Screen.Editor.createRoute(id))
                            },
                            onOpenSettings = { navController.navigate(Screen.Settings.route) }
                        )
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onOpenLogs = { navController.navigate(Screen.LogViewer.route) }
                        )
                    }
                    composable(Screen.Editor.route) { backStackEntry ->
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
                        LogViewerScreen(onNavigateBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppLog.d(TAG, "onNewIntent: $intent")
        setIntent(intent)
    }

    private fun getStartRoute(): String {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        return if (prefs.getBoolean(KEY_ONBOARDED, false)) {
            Screen.ProjectList.route
        } else {
            Screen.Onboarding.route
        }
    }

    private fun writeOnboardingComplete() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS = "clipcut_prefs"
        private const val KEY_ONBOARDED = "onboarding_complete"
    }
}
