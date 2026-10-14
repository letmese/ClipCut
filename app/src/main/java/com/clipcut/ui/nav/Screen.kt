package com.clipcut.ui.nav

/**
 * Type-safe routing constants. MainActivity's NavHost references these strings
 * for `composable(...)` and `navController.navigate(...)`.
 *
 * Editor uses a `{projectId}` path arg that NavHost resolves via
 * `backStackEntry.arguments?.getString("projectId")`.
 */
sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object ProjectList : Screen("projects")
    object Settings : Screen("settings")
    object Editor : Screen("editor/{projectId}") {
        fun createRoute(projectId: String): String = "editor/$projectId"
    }
    object LogViewer : Screen("logs")
    object Collage : Screen("collage")
    object PhotoEditor : Screen("photo_editor")
    object Teleprompter : Screen("teleprompter")
}
