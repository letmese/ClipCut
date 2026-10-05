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
            val list = dao.getProjectById("")?.let { listOf(it.toDomain()) } ?: emptyList()
            // The Flow-based getAllProjectsFlow returns immediately; refresh manually
            val helper = ClipCutDatabase.getInstance(getApplication())
            val db = helper.readableDatabase
            val cursor = db.query("projects", null, null, null, null, null, "lastModifiedTime DESC")
            val out = mutableListOf<Project>()
            cursor.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(c.getColumnIndexOrThrow("id"))
                    dao.getProjectById(id)?.let { out.add(it.toDomain()) }
                }
            }
            _projects.value = out
        }
    }

    fun createProject(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val p = Project(name = name.ifBlank { "Untitled Video" })
            dao.insertProject(ProjectEntity.fromDomain(p))
            AppLog.i(TAG, "createProject ${p.id} name=${p.name}")
            refresh()
            onCreated(p.id)
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
