package com.clipcut.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipcut.core.log.AppLog
import com.clipcut.core.model.Project
import com.clipcut.data.db.ClipCutDatabase
import com.clipcut.data.db.ProjectEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectListViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ClipCutDatabase.getInstance(application)
    private val dao = db.projectDao()

    val projects: StateFlow<List<Project>> = dao.getAllProjectsFlow()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createProject(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val project = Project(name = name.ifBlank { "New Video" })
            dao.insertProject(ProjectEntity.fromDomain(project))
            AppLog.i("ProjectListVM", "Created project: ${project.name} (${project.id})")
            onCreated(project.id)
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            dao.deleteProjectById(id)
            AppLog.i("ProjectListVM", "Deleted project $id")
        }
    }
}
