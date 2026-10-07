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

    fun createProject(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val p = Project(name = name.ifBlank { "Untitled Video" })
            dao.insertProject(ProjectEntity.fromDomain(p))
            AppLog.i(TAG, "createProject ${p.id} name=${p.name}")
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

    fun deleteProject(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteProjectById(id)
            refresh()
        }
    }

    companion object { private const val TAG = "ProjectListVM" }
}
