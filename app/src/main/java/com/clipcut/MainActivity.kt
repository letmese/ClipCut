package com.clipcut

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.AudioTrackItem
import com.clipcut.core.model.MediaType
import com.clipcut.core.model.Project
import com.clipcut.core.model.TimelineItem
import com.clipcut.data.db.ClipCutDatabase
import com.clipcut.data.db.ProjectEntity
import com.clipcut.ui.nav.Screen
import com.clipcut.ui.screens.EditorScreen
import com.clipcut.ui.screens.LogViewerScreen
import com.clipcut.ui.screens.ProjectListScreen
import com.clipcut.ui.theme.ClipCutTheme
import com.clipcut.ui.viewmodel.EditorViewModel
import com.clipcut.ui.viewmodel.ProjectListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val pendingShare = mutableStateOf<Pair<Uri, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLog.i(TAG, "MainActivity created")
        handleSendIntent(intent)

        setContent {
            ClipCutTheme {
                val navController = rememberNavController()
                val shareTarget by pendingShare

                LaunchedEffect(shareTarget) {
                    val target = shareTarget ?: return@LaunchedEffect
                    val (uri, type) = target
                    pendingShare.value = null

                    val projectId = withContext(Dispatchers.IO) {
                        val db = ClipCutDatabase.getInstance(applicationContext)
                        val dao = db.projectDao()
                        val isImage = type.startsWith("image/")
                        val isAudio = type.startsWith("audio/")
                        val durMs = if (isImage) 5000L else {
                            try {
                                val mmr = MediaMetadataRetriever()
                                mmr.setDataSource(applicationContext, uri)
                                val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5000L
                                mmr.release()
                                d
                            } catch (_: Exception) {
                                5000L
                            }
                        }
                        val durUs = durMs * 1000L
                        val initialProject = if (isAudio) {
                            val track = AudioTrackItem(
                                uri = uri.toString(),
                                name = uri.lastPathSegment ?: "Audio",
                                sourceDurationUs = durUs,
                                timelineStartUs = 0L,
                                trimStartUs = 0L,
                                trimEndUs = durUs
                            )
                            Project(name = "Shared Audio", audioTracks = listOf(track))
                        } else {
                            val clip = TimelineItem(
                                uri = uri.toString(),
                                mediaType = if (isImage) MediaType.IMAGE else MediaType.VIDEO,
                                sourceDurationUs = durUs,
                                trimStartUs = 0L,
                                trimEndUs = durUs
                            )
                            Project(name = if (isImage) "Shared Image" else "Shared Video", videoClips = listOf(clip))
                        }
                        dao.insertProject(ProjectEntity.fromDomain(initialProject))
                        AppLog.i(TAG, "Created project from share intent: ${initialProject.id}")
                        initialProject.id
                    }

                    navController.navigate(Screen.Editor.createRoute(projectId))
                }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSendIntent(intent)
    }

    private fun handleSendIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type != null) {
            val uri = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)

            if (uri != null) {
                AppLog.i(TAG, "Received shared media: $uri (type=${intent.type})")
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, flags)
                } catch (_: SecurityException) {}
                pendingShare.value = Pair(uri, intent.type ?: "video/*")
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

