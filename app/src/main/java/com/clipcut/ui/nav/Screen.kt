package com.clipcut.ui.nav

sealed class Screen(val route: String) {
    object ProjectList : Screen("projects")
    object Editor : Screen("editor/{projectId}") {
        fun createRoute(projectId: String) = "editor/$projectId"
    }
    object LogViewer : Screen("logs")
}
