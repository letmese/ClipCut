package com.clipcut.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.Project
import com.clipcut.data.db.ClipCutDatabase
import com.clipcut.data.db.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProjectListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ClipCutDatabase.getInstance(application).projectDao()

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val entities = dao.getAllProjects()
            _projects.value = entities.map { it.toDomain() }
        }
    }

    fun createProject(
        name: String,
        aspectRatio: com.clipcut.core.model.AspectRatio = com.clipcut.core.model.AspectRatio.R_9_16,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val p = Project(
                name = name.ifBlank { "Untitled Video" },
                aspectRatio = aspectRatio
            )
            dao.insertProject(ProjectEntity.fromDomain(p))
            AppLog.i(TAG, "createProject ${p.id} name=${p.name} ratio=${p.aspectRatio.label}")
            refresh()
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onCreated(p.id)
            }
        }
    }

    fun createProjectFromTemplate(template: com.clipcut.core.templates.Template, onCreated: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val registry = com.clipcut.core.templates.TemplateRegistry(getApplication())
            val base = Project(name = template.name)
            val configured = registry.applyToProject(template, base, emptyList())
            dao.insertProject(ProjectEntity.fromDomain(configured))
            AppLog.i(TAG, "createProjectFromTemplate ${configured.id} name=${configured.name}")
            refresh()
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onCreated(configured.id)
            }
        }
    }

    fun duplicateProject(project: Project) {
        viewModelScope.launch(Dispatchers.IO) {
            val copy = project.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = "${project.name} (Copy)",
                lastModifiedTime = System.currentTimeMillis()
            )
            dao.insertProject(ProjectEntity.fromDomain(copy))
            refresh()
        }
    }

    fun renameProject(id: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = dao.getProjectById(id) ?: return@launch
            val updated = entity.copy(
                name = newName.ifBlank { entity.name },
                lastModifiedTime = System.currentTimeMillis()
            )
            dao.updateProject(updated)
            refresh()
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteProjectById(id)
            refresh()
        }
    }

    companion object { private const val TAG = "ProjectListVM" }
}
