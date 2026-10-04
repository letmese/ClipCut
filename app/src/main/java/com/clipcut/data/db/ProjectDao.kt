package com.clipcut.data.db

import kotlinx.coroutines.flow.Flow

interface ProjectDao {
    fun getAllProjectsFlow(): Flow<List<ProjectEntity>>

    suspend fun getProjectById(id: String): ProjectEntity?

    suspend fun insertProject(project: ProjectEntity)

    suspend fun updateProject(project: ProjectEntity)

    suspend fun deleteProjectById(id: String)
}
