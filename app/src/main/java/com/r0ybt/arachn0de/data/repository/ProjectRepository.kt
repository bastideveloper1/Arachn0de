package com.r0ybt.arachn0de.data.repository

import com.r0ybt.arachn0de.data.local.ProjectDao
import com.r0ybt.arachn0de.data.local.ProjectEntity
import com.r0ybt.arachn0de.data.local.toProject
import com.r0ybt.arachn0de.domain.model.Project
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    fun observeProjects(): Flow<List<Project>> = projectDao.observeAll().map { projects ->
        projects.map { it.toProject() }
    }

    suspend fun getProject(id: String): Project? = projectDao.getById(id)?.toProject()

    suspend fun createProject(
        name: String,
        description: String = "",
        creationId: String = UUID.randomUUID().toString(),
    ): Project {
        val normalizedName = validateName(name)
        val now = currentTimeMillis()
        val entity = ProjectEntity(
            id = creationId,
            name = normalizedName,
            description = description,
            createdAt = now,
            updatedAt = now,
        )
        require(creationId.isNotBlank())
        val persisted = projectDao.insertOrGet(entity)
        check(persisted.name == normalizedName && persisted.description == description) {
            "Creation already committed with different content"
        }
        return persisted.toProject()
    }

    /** Returns false if the project no longer exists; never inserts a missing project. */
    suspend fun updateProject(id: String, name: String, description: String): Boolean =
        projectDao.update(id, validateName(name), description, currentTimeMillis()) == 1

    /** Deleting an already absent project returns false. */
    suspend fun deleteProject(id: String): Boolean = projectDao.delete(id) == 1

    private fun validateName(name: String): String {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Project name must not be blank" }
        return normalizedName
    }
}
