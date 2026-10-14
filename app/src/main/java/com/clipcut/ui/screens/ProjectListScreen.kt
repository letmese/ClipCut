package com.clipcut.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipcut.BuildConfig
import com.clipcut.core.model.AspectRatio
import com.clipcut.core.model.Project
import com.clipcut.ui.components.PrimaryButton
import com.clipcut.ui.theme.*

/**
 * ProjectListScreen — ClipCut Pro Mobile Home Hub (Google Stitch UI).
 * Incorporates 6-tile creation grid, aspect ratio pills, recent drafts with 3-dot
 * management menus (Open, Duplicate, Rename, Delete), and 4-tab docked bottom navigation.
 */
@Composable
fun ProjectListScreen(
    viewModel: com.clipcut.ui.viewmodel.ProjectListViewModel,
    onOpenProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCollage: () -> Unit = {},
    onOpenPhotoEditor: () -> Unit = {},
    onOpenTeleprompter: () -> Unit = {}
) {
    val projects by viewModel.projects.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }
    var projectToRename by remember { mutableStateOf<Project?>(null) }
    var renameText by remember { mutableStateOf("") }
    var projectName by remember { mutableStateOf("") }
    var selectedRatio by remember { mutableStateOf(AspectRatio.R_9_16) }
    var selectedBottomTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            HomeBottomBar(
                tab = selectedBottomTab,
                onTabChange = { tabIdx ->
                    if (tabIdx == 2) {
                        onOpenSettings()
                    } else {
                        selectedBottomTab = tabIdx
                    }
                }
            )
        },
        topBar = {
            Surface(
                color = Surface1,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "ClipCut",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentPrimary.copy(alpha = 0.2f))
                                .border(1.dp, AccentPrimary.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "PRO",
                                color = AccentPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Surface3)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "v${BuildConfig.VERSION_NAME}",
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp
                            )
                        }
                    }

                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (selectedBottomTab == 1) {
            TemplatesShowcaseView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onOpenTemplate = { t ->
                    viewModel.createProjectFromTemplate(t) { id -> onOpenProject(id) }
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
            Spacer(Modifier.height(16.dp))

            // Hero Section: New Project Card
            HeroNewProjectCard(
                selectedRatio = selectedRatio,
                onSelectRatio = { selectedRatio = it },
                onCreateNew = { showCreateDialog = true }
            )

            Spacer(Modifier.height(20.dp))

            // 6-Tile Quick Tools Grid (Stitch Home Hub)
            QuickToolsGrid(
                onOpenCollage = onOpenCollage,
                onOpenPhotoEditor = onOpenPhotoEditor,
                onOpenTeleprompter = onOpenTeleprompter,
                onSpeedRamp = {
                    viewModel.createProject("Speed Ramp Clip", selectedRatio) { id ->
                        onOpenProject(id)
                    }
                },
                onAudioExtractor = {
                    viewModel.createProject("Audio Studio", selectedRatio) { id ->
                        onOpenProject(id)
                    }
                },
                onBrandKit = {
                    viewModel.createProject("Branded Video", selectedRatio) { id ->
                        onOpenProject(id)
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            // Templates Carousel
            TemplatesCarousel(onOpenTemplate = { t ->
                viewModel.createProjectFromTemplate(t) { id -> onOpenProject(id) }
            })

            Spacer(Modifier.height(24.dp))

            // Recent Projects with 3-dot management menu
            RecentProjectsGrid(
                projects = projects,
                onOpen = onOpenProject,
                onDuplicate = { viewModel.duplicateProject(it) },
                onRename = {
                    projectToRename = it
                    renameText = it.name
                },
                onDelete = { projectToDelete = it }
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

    projectToDelete?.let { proj ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            containerColor = Surface2,
            title = { Text("Delete Project?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${proj.name}\"? All edits will be permanently lost.", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProject(proj.id)
                        projectToDelete = null
                    }
                ) { Text("Delete", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    projectToRename?.let { proj ->
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            containerColor = Surface2,
            title = { Text("Rename Project", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
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
                TextButton(
                    onClick = {
                        viewModel.renameProject(proj.id, renameText)
                        projectToRename = null
                    }
                ) { Text("Rename", color = AccentPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) { Text("Cancel", color = TextSecondary) }
            }
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Surface2,
            title = { Text("New Project", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it },
                        placeholder = { Text("Project name (e.g. My Reel)", color = TextTertiary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Surface3,
                            unfocusedContainerColor = Surface3,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = AccentPrimary
                        )
                    )
                    Text("Canvas Aspect Ratio", color = TextSecondary, fontSize = 12.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            AspectRatio.R_9_16 to "9:16",
                            AspectRatio.R_16_9 to "16:9",
                            AspectRatio.R_1_1 to "1:1",
                            AspectRatio.R_4_5 to "4:5"
                        ).forEach { (ratio, label) ->
                            val isSel = selectedRatio == ratio
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) AccentPrimary.copy(alpha = 0.2f) else Surface3)
                                    .border(
                                        1.dp,
                                        if (isSel) AccentPrimary else HairlineBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedRatio = ratio }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (isSel) AccentPrimary else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = projectName
                    showCreateDialog = false
                    projectName = ""
                    viewModel.createProject(name, selectedRatio) { id -> onOpenProject(id) }
                }) { Text("Create", color = AccentPrimary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }
}

@Composable
private fun HeroNewProjectCard(
    selectedRatio: AspectRatio,
    onSelectRatio: (AspectRatio) -> Unit,
    onCreateNew: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Surface2, Surface1)
                )
            )
            .border(1.dp, AccentPrimary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Start Creating",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Import video, photos, or RAW media",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // Aspect ratio badges
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        AspectRatio.R_9_16 to "9:16",
                        AspectRatio.R_16_9 to "16:9",
                        AspectRatio.R_1_1 to "1:1"
                    ).forEach { (ratio, label) ->
                        val isSel = selectedRatio == ratio
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) AccentPrimary else Surface3)
                                .clickable { onSelectRatio(ratio) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (isSel) DarkBackground else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            PrimaryButton(
                text = "+ New Project",
                onClick = onCreateNew,
                icon = Icons.Filled.Add,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun QuickToolsGrid(
    onOpenCollage: () -> Unit,
    onOpenPhotoEditor: () -> Unit,
    onOpenTeleprompter: () -> Unit,
    onSpeedRamp: () -> Unit,
    onAudioExtractor: () -> Unit,
    onBrandKit: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Quick Studios & Benchmark Tools",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(12.dp))

        val tools = listOf(
            ToolTileData("Collage Maker", "Multi-clip layout", Icons.Filled.GridView, AccentPrimary, onOpenCollage),
            ToolTileData("Photo Studio", "ColorMatrix FX", Icons.Filled.PhotoCamera, AccentViolet, onOpenPhotoEditor),
            ToolTileData("Teleprompter", "Glass rig scroller", Icons.Filled.PlayArrow, ProGold, onOpenTeleprompter),
            ToolTileData("Speed Ramp", "VN 2D curves", Icons.Filled.Speed, AccentBlue, onSpeedRamp),
            ToolTileData("Audio Studio", "10-Band EQ & FX", Icons.Filled.GraphicEq, AccentGold, onAudioExtractor),
            ToolTileData("Brand Kit", "VN watermark & logo", Icons.Filled.Verified, Color(0xFF00E676), onBrandKit)
        )

        for (row in 0 until 2) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (col in 0 until 3) {
                    val item = tools[row * 3 + col]
                    ToolTile(item = item, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class ToolTileData(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit
)

@Composable
private fun ToolTile(item: ToolTileData, modifier: Modifier = Modifier) {
    Surface(
        onClick = item.onClick,
        modifier = modifier.height(86.dp),
        shape = RoundedCornerShape(12.dp),
        color = Surface2,
        border = androidx.compose.foundation.BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(item.tint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, contentDescription = item.title, tint = item.tint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(item.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(item.subtitle, color = TextSecondary, fontSize = 9.sp, maxLines = 1)
        }
    }
}

@Composable
private fun TemplatesCarousel(onOpenTemplate: (com.clipcut.core.templates.Template) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val registry = remember { com.clipcut.core.templates.TemplateRegistry(context) }
    val loadedTemplates = remember { registry.listTemplates() }

    Text(
        "Creator Templates",
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Spacer(Modifier.height(10.dp))
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
                        .size(width = 150.dp, height = 160.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface2)
                        .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable { onOpenTemplate(t) }
                        .padding(14.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(color.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("TEMPLATE", color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(t.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        val secs = t.durationUs / 1_000_000L
                        Text("${secs}s • ${t.aspectRatio}", color = TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentProjectsGrid(
    projects: List<Project>,
    onOpen: (String) -> Unit,
    onDuplicate: (Project) -> Unit,
    onRename: (Project) -> Unit,
    onDelete: (Project) -> Unit
) {
    if (projects.isEmpty()) {
        com.clipcut.ui.components.EmptyState(
            title = "No projects yet",
            description = "Tap \"+ New Project\" above to begin your first edit",
            icon = Icons.Filled.Movie,
            modifier = Modifier.padding(24.dp)
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Recent Projects (${projects.size})",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
    Spacer(Modifier.height(12.dp))
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (i in projects.indices step 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ProjectCard(projects[i], onOpen, onDuplicate, onRename, onDelete, Modifier.weight(1f))
                if (i + 1 < projects.size) {
                    ProjectCard(projects[i + 1], onOpen, onDuplicate, onRename, onDelete, Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onOpen: (String) -> Unit,
    onDuplicate: (Project) -> Unit,
    onRename: (Project) -> Unit,
    onDelete: (Project) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(160.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onOpen(project.id) }
            .padding(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface3),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(32.dp)
                )

                // Duration badge
                val secs = project.totalDurationUs / 1_000_000L
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${secs}s",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 3-dot management button
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp).padding(2.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Project Options",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(Surface2)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open Project", color = Color.White) },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = AccentPrimary) },
                            onClick = {
                                menuExpanded = false
                                onOpen(project.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate", color = Color.White) },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = AccentViolet) },
                            onClick = {
                                menuExpanded = false
                                onDuplicate(project)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename", color = Color.White) },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = ProGold) },
                            onClick = {
                                menuExpanded = false
                                onRename(project)
                            }
                        )
                        HorizontalDivider(color = HairlineBorder)
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFFF5252)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFFF5252)) },
                            onClick = {
                                menuExpanded = false
                                onDelete(project)
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                project.name,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                project.aspectRatio.label,
                color = AccentPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun HomeBottomBar(tab: Int, onTabChange: (Int) -> Unit) {
    val tabs = listOf(
        "Create" to Icons.Filled.Movie,
        "Templates" to Icons.Filled.AutoAwesome,
        "Settings" to Icons.Filled.Settings
    )
    Surface(
        color = DarkSurface,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            tabs.forEachIndexed { i, (label, icon) ->
                val selected = tab == i
                val color = if (selected) AccentPrimary else TextSecondary
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabChange(i) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(
                        label,
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun TemplatesShowcaseView(
    modifier: Modifier = Modifier,
    onOpenTemplate: (com.clipcut.core.templates.Template) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val registry = remember { com.clipcut.core.templates.TemplateRegistry(context) }
    val loadedTemplates = remember { registry.listTemplates() }

    // Fallback/rich curated templates if assets are limited
    val curatedTemplates = remember(loadedTemplates) {
        val list = loadedTemplates.toMutableList()
        val existingIds = list.map { it.id }.toSet()

        if (!existingIds.contains("velocity_hero")) {
            list.add(
                com.clipcut.core.templates.Template(
                    id = "velocity_hero",
                    name = "Velocity Speed Ramp",
                    aspectRatio = "9:16",
                    durationUs = 12_000_000L,
                    clipSlots = listOf(
                        com.clipcut.core.templates.Template.ClipSlot(0, 3_000_000L, "Punchy", "motion_blur"),
                        com.clipcut.core.templates.Template.ClipSlot(1, 2_000_000L, "Cinematic", "zoom_blur"),
                        com.clipcut.core.templates.Template.ClipSlot(2, 4_000_000L, "Moody", "vignette_pulse"),
                        com.clipcut.core.templates.Template.ClipSlot(3, 3_000_000L, "Vibrant", "bloom")
                    ),
                    transitions = listOf("rotate_zoom", "shake", "spin"),
                    effects = listOf("motion_blur", "zoom_blur", "vignette_pulse"),
                    filters = listOf("Punchy", "Cinematic", "Moody"),
                    musicUrl = null,
                    textOverlays = listOf(
                        com.clipcut.core.templates.Template.TextOverlay("FAST FORWARD", 0L, 3_000_000L, "center")
                    )
                )
            )
        }
        if (!existingIds.contains("beat_sync_montage")) {
            list.add(
                com.clipcut.core.templates.Template(
                    id = "beat_sync_montage",
                    name = "Beat Sync Montage",
                    aspectRatio = "9:16",
                    durationUs = 16_000_000L,
                    clipSlots = listOf(
                        com.clipcut.core.templates.Template.ClipSlot(0, 2_000_000L, "Cyberpunk", "rgb_glitch"),
                        com.clipcut.core.templates.Template.ClipSlot(1, 2_000_000L, "Vibrant", "flash"),
                        com.clipcut.core.templates.Template.ClipSlot(2, 2_000_000L, "Moody", "zoom_blur"),
                        com.clipcut.core.templates.Template.ClipSlot(3, 2_000_000L, "Cinematic", "shake"),
                        com.clipcut.core.templates.Template.ClipSlot(4, 4_000_000L, "Punchy", "halation"),
                        com.clipcut.core.templates.Template.ClipSlot(5, 4_000_000L, "Cyberpunk", "bloom")
                    ),
                    transitions = listOf("blink", "zoom_blur", "shake", "floodlight", "split_h"),
                    effects = listOf("rgb_glitch", "flash", "shake", "halation"),
                    filters = listOf("Cyberpunk", "Vibrant", "Punchy"),
                    musicUrl = null,
                    textOverlays = listOf(
                        com.clipcut.core.templates.Template.TextOverlay("DROP THE BEAT", 0L, 4_000_000L, "center")
                    )
                )
            )
        }
        if (!existingIds.contains("gaming_highlights")) {
            list.add(
                com.clipcut.core.templates.Template(
                    id = "gaming_highlights",
                    name = "Gaming Apex Highlights",
                    aspectRatio = "16:9",
                    durationUs = 24_000_000L,
                    clipSlots = listOf(
                        com.clipcut.core.templates.Template.ClipSlot(0, 6_000_000L, "Cyberpunk", "shake"),
                        com.clipcut.core.templates.Template.ClipSlot(1, 6_000_000L, "Punchy", "rgb_glitch"),
                        com.clipcut.core.templates.Template.ClipSlot(2, 6_000_000L, "Cinematic", "motion_blur"),
                        com.clipcut.core.templates.Template.ClipSlot(3, 6_000_000L, "Vibrant", "bloom")
                    ),
                    transitions = listOf("shake", "split_v", "spin"),
                    effects = listOf("shake", "rgb_glitch", "bloom"),
                    filters = listOf("Cyberpunk", "Punchy"),
                    musicUrl = null,
                    textOverlays = listOf(
                        com.clipcut.core.templates.Template.TextOverlay("VICTORY ROYALE", 0L, 4_000_000L, "top")
                    )
                )
            )
        }
        if (!existingIds.contains("minimal_aesthetic")) {
            list.add(
                com.clipcut.core.templates.Template(
                    id = "minimal_aesthetic",
                    name = "Minimalist Mood",
                    aspectRatio = "1:1",
                    durationUs = 15_000_000L,
                    clipSlots = listOf(
                        com.clipcut.core.templates.Template.ClipSlot(0, 5_000_000L, "Soft Tone", null),
                        com.clipcut.core.templates.Template.ClipSlot(1, 5_000_000L, "Moody", "film_grain"),
                        com.clipcut.core.templates.Template.ClipSlot(2, 5_000_000L, "Cinematic", "halation")
                    ),
                    transitions = listOf("dissolve", "fade"),
                    effects = listOf("film_grain", "halation"),
                    filters = listOf("Soft Tone", "Moody"),
                    musicUrl = null,
                    textOverlays = listOf(
                        com.clipcut.core.templates.Template.TextOverlay("SERENITY", 0L, 5_000_000L, "bottom")
                    )
                )
            )
        }
        list
    }

    val categories = listOf("All", "Cinematic", "Reels & Shorts", "Beat Sync", "Travel Vlog", "Gaming")
    var selectedCategory by remember { mutableStateOf("All") }

    val filteredTemplates = remember(curatedTemplates, selectedCategory) {
        when (selectedCategory) {
            "Cinematic" -> curatedTemplates.filter { it.name.contains("Cinematic", ignoreCase = true) || it.aspectRatio == "21:9" }
            "Reels & Shorts" -> curatedTemplates.filter { it.name.contains("Reel", ignoreCase = true) || it.aspectRatio == "9:16" }
            "Beat Sync" -> curatedTemplates.filter { it.name.contains("Beat", ignoreCase = true) || it.name.contains("Velocity", ignoreCase = true) }
            "Travel Vlog" -> curatedTemplates.filter { it.name.contains("Vlog", ignoreCase = true) || it.name.contains("Aesthetic", ignoreCase = true) }
            "Gaming" -> curatedTemplates.filter { it.name.contains("Gaming", ignoreCase = true) }
            else -> curatedTemplates
        }
    }

    Column(
        modifier = modifier
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // Top Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                "Creator Templates Hub",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "One-tap professional edits with pre-tuned curves, transitions, and audio sync",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // Category Pills
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories.size) { i ->
                val cat = categories[i]
                val selected = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) AccentPrimary else Surface2)
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        cat,
                        color = if (selected) Color.Black else Color.White,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Templates Cards
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            filteredTemplates.forEachIndexed { idx, t ->
                TemplateShowcaseCard(
                    template = t,
                    index = idx,
                    onUse = { onOpenTemplate(t) }
                )
            }
        }
    }
}

@Composable
private fun TemplateShowcaseCard(
    template: com.clipcut.core.templates.Template,
    index: Int,
    onUse: () -> Unit
) {
    val accentColors = listOf(AccentPrimary, AccentSecondary, AccentGold, AccentViolet, AccentBlue)
    val cardColor = accentColors[index % accentColors.size]
    val durationSec = template.durationUs / 1_000_000L
    val clipCount = template.clipSlots.size

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2)
            .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cardColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = cardColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            template.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            "${durationSec}s • $clipCount clips • ${template.aspectRatio}",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(cardColor.copy(alpha = 0.15f))
                        .border(1.dp, cardColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        template.aspectRatio,
                        color = cardColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Badges / Tags row
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                template.effects.take(3).forEach { eff ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Surface3)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            eff.replace("_", " ").uppercase(),
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                template.transitions.take(2).forEach { trans ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Surface3)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            trans.replace("_", " ").uppercase(),
                            color = AccentSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 1-Tap Use Template Action Button
            Button(
                onClick = onUse,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cardColor,
                    contentColor = Color.Black
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Black
                    )
                    Text(
                        "Use This Template",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

