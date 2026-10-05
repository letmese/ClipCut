package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipcut.core.model.Project
import com.clipcut.ui.components.PrimaryButton
import com.clipcut.ui.theme.*

/**
 * ProjectListScreen — home screen. Per spec:
 *   - hero section "Create something amazing" with large title
 *   - "New Project" primary CTA
 *   - templates carousel (horizontal cards with thumbnails)
 *   - recent projects grid (2-column, thumbnail from first clip, duration, date)
 *   - empty state illustration
 *   - bottom nav (Projects | Templates | Me)
 *   - settings icon top-right
 *
 * The bottom-nav and templates tabs are present but the tabs currently route
 * to the same ProjectList layout. EditorViewModel owns project creation.
 */
@Composable
fun ProjectListScreen(
    viewModel: com.clipcut.ui.viewmodel.ProjectListViewModel,
    onOpenProject: (String) -> Unit,
    onOpenLogs: () -> Unit
) {
    val projects by viewModel.projects.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = { HomeBottomBar(tab = tab, onTabChange = { tab = it }) },
        topBar = {
            Box(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("ClipCut", color = AccentPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                IconButton(
                    onClick = onOpenLogs,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White) }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            HeroSection(onCreateNew = { showCreateDialog = true })
            Spacer(Modifier.height(24.dp))
            TemplatesCarousel(onOpenTemplate = { t ->
                viewModel.createProjectFromTemplate(t) { id -> onOpenProject(id) }
            })
            Spacer(Modifier.height(24.dp))
            RecentProjectsGrid(projects = projects, onOpen = onOpenProject)
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Surface2,
            title = { Text("New Project", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    placeholder = { Text("Project name", color = TextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Surface3,
                        unfocusedContainerColor = Surface3,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = AccentPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = projectName
                    showCreateDialog = false
                    projectName = ""
                    viewModel.createProject(name) { id -> onOpenProject(id) }
                }) { Text("Create", color = AccentPrimary) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }
}

@Composable
private fun HeroSection(onCreateNew: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(
                Brush.verticalGradient(listOf(AccentPrimary.copy(alpha = 0.18f), DarkBackground))
            )
            .padding(24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text("Create something amazing", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Edit like a pro, anywhere.", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = "New Project", onClick = onCreateNew, icon = Icons.Filled.Add)
        }
    }
}

@Composable
private fun TemplatesCarousel(onOpenTemplate: (com.clipcut.core.templates.Template) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val registry = remember { com.clipcut.core.templates.TemplateRegistry(context) }
    val loadedTemplates = remember { registry.listTemplates() }

    Text("Templates", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp))
    Spacer(Modifier.height(12.dp))
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (loadedTemplates.isNotEmpty()) {
            val colors = listOf(AccentPrimary, AccentSecondary, AccentGold, AccentViolet, AccentBlue)
            items(loadedTemplates.size) { idx ->
                val t = loadedTemplates[idx]
                val color = colors[idx % colors.size]
                Box(
                    modifier = Modifier
                        .size(width = 160.dp, height = 200.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(color)
                        .clickable { onOpenTemplate(t) }
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Text(t.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        val secs = t.durationUs / 1_000_000L
                        Text("${secs}s • ${t.aspectRatio}", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            }
        } else {
            items(templates) { t ->
                Box(
                    modifier = Modifier
                        .size(width = 160.dp, height = 200.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(t.color)
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Text(t.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${t.duration} • ${t.aspect}", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentProjectsGrid(projects: List<Project>, onOpen: (String) -> Unit) {
    if (projects.isEmpty()) {
        com.clipcut.ui.components.EmptyState(
            title = "No projects yet",
            description = "Tap \"New Project\" to begin your first edit",
            icon = Icons.Filled.Movie,
            modifier = Modifier.padding(24.dp)
        )
        return
    }
    Text("Recent Projects", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp))
    Spacer(Modifier.height(12.dp))
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (i in projects.indices step 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ProjectCard(projects[i], onOpen, Modifier.weight(1f))
                if (i + 1 < projects.size) ProjectCard(projects[i + 1], onOpen, Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProjectCard(project: Project, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onOpen(project.id) }
            .padding(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(Surface3),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Movie, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(project.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            val secs = project.totalDurationUs / 1_000_000L
            Text("${secs}s • ${project.aspectRatio.label}", color = TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HomeBottomBar(tab: Int, onTabChange: (Int) -> Unit) {
    val tabs = listOf("Projects" to Icons.Filled.Movie, "Templates" to Icons.Filled.PlayArrow, "Me" to Icons.Filled.Settings)
    Surface(color = DarkSurface, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).navigationBarsPadding().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { i, (label, icon) ->
                val selected = tab == i
                val color = if (selected) AccentPrimary else TextSecondary
                Column(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onTabChange(i) }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
                    Text(label, color = color, fontSize = 10.sp)
                }
            }
        }
    }
}

private data class TemplatePreview(val name: String, val duration: String, val aspect: String, val color: Color)
private val templates = listOf(
    TemplatePreview("Vlog Intro", "15s", "16:9", AccentPrimary),
    TemplatePreview("Reel Drop", "30s", "9:16", AccentSecondary),
    TemplatePreview("Cinematic", "60s", "21:9", AccentGold),
    TemplatePreview("Lyrical", "45s", "1:1", AccentViolet),
    TemplatePreview("Trailer", "90s", "16:9", AccentBlue)
)
