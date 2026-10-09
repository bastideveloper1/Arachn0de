package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.ProjectDao
import com.r0ybt.arachn0de.data.local.ProjectEntity
import com.r0ybt.arachn0de.data.local.toProject
import com.r0ybt.arachn0de.domain.model.Project
import java.util.UUID
import com.r0ybt.arachn0de.domain.model.TitleLimits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val database: Arachn0deDatabase? = null,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    constructor(projectDao: ProjectDao, currentTimeMillis: () -> Long) :
        this(projectDao, null, currentTimeMillis)

    fun observeProjects(): Flow<List<Project>> = projectDao.observeWithPhotos().map { rows -> rows.map { it.toProject() } }

    suspend fun normalizeProjectOrder() = inTransaction {
        val ordered = sortProjectsForDisplay(projectDao.getAll())
        writeOrder(ordered)
    }

    suspend fun reorderProject(id: String, moveUp: Boolean): Boolean = inTransaction {
        val current = projectDao.getById(id) ?: return@inTransaction false
        val ordered = sortProjectsForDisplay(projectDao.getAll())
        val from = ordered.indexOfFirst { it.id == id }
        val to = from + if (moveUp) -1 else 1
        if (to !in ordered.indices) return@inTransaction true
        val target = ordered[to]
        val modified = ordered.toMutableList()
        modified[from] = target
        modified[to] = current
        writeOrder(modified, setOf(id, target.id))
        true
    }

    suspend fun reorderProjectTo(id: String, targetId: String): Boolean = inTransaction {
        val ordered = sortProjectsForDisplay(projectDao.getAll())
        val from = ordered.indexOfFirst { it.id == id }
        val to = ordered.indexOfFirst { it.id == targetId }
        if (from < 0 || to < 0 || from == to) return@inTransaction from >= 0 && to >= 0 && from == to
        val moved = ordered.toMutableList()
        val item = moved.removeAt(from)
        moved.add(to, item)
        writeOrder(moved, setOf(id, targetId))
        true
    }

    suspend fun moveInside(sourceId:String,targetId:String,parentId:String?):String =
        ProjectNestingRepository(requireNotNull(database)).move(sourceId,targetId,parentId)

    suspend fun convertLayerToProject(layerId: String): String =
        ProjectNestingRepository(requireNotNull(database)).promote(layerId)

    suspend fun destinationLayers(projectId:String):List<com.r0ybt.arachn0de.domain.model.Node> =
        NodeRepository(requireNotNull(database),currentTimeMillis).getProjectNodes(projectId).filter { it.isStructural }

    suspend fun getProject(id: String): Project? = projectDao.getWithPhoto(id)?.toProject()

    suspend fun createProject(
        name: String,
        description: String = "",
        creationId: String = UUID.randomUUID().toString(),
    ): Project = inTransaction {
        require(database != null || com.r0ybt.arachn0de.domain.model.AttachmentReferences.ids(description).isEmpty()) { "Los adjuntos requieren una transacción de base de datos." }
        val normalizedName = validateName(name)
        require(creationId.isNotBlank())
        val now = currentTimeMillis()
        val entity = ProjectEntity(
            id = creationId,
            name = normalizedName,
            description = description,
            position = nextPosition(),
            createdAt = now,
            updatedAt = now,
        )
        val persisted = projectDao.insertOrGet(entity)
        if (com.r0ybt.arachn0de.domain.model.AttachmentReferences.ids(description).isNotEmpty()) ensureProjectReferences(requireNotNull(database), persisted.id, description)
        check(persisted.name == normalizedName && persisted.description == description) {
            "Creation already committed with different content"
        }
        persisted.toProject()
    }

    /** Returns false if the project no longer exists; never inserts a missing project. */
    suspend fun updateProject(id: String, name: String, description: String): Boolean =
        inTransaction {
            if (projectDao.getById(id) == null) return@inTransaction false
            if (com.r0ybt.arachn0de.domain.model.AttachmentReferences.ids(description).isNotEmpty()) ensureProjectReferences(requireNotNull(database), id, description)
            projectDao.update(id, validateName(name), description, currentTimeMillis()) == 1
        }

    /** Deleting an already absent project returns false. */
    suspend fun deleteProject(id: String): Boolean = inTransaction {
        val deleted = projectDao.delete(id) == 1
        if (deleted) {
            val ordered = sortProjectsForDisplay(projectDao.getAll())
            writeOrder(ordered)
        }
        deleted
    }

    private suspend fun writeOrder(projects: List<ProjectEntity>, changedIds: Set<String> = emptySet()) {
        val now = if (changedIds.isEmpty()) null else currentTimeMillis()
        projects.forEachIndexed { position, project ->
            if (project.position != position || project.id in changedIds) {
                check(projectDao.updateOrder(project.id, position, if (project.id in changedIds) checkNotNull(now) else project.updatedAt) == 1)
            }
        }
    }

    private fun sortProjectsForDisplay(projects: List<ProjectEntity>): List<ProjectEntity> =
        projects.sortedWith(compareBy<ProjectEntity> { it.position }.thenBy { it.createdAt }.thenBy { it.id })

    private suspend fun nextPosition(): Int {
        var maximum = projectDao.maxPosition() ?: -1
        if (maximum == Int.MAX_VALUE) {
            normalizeProjectOrder()
            maximum = projectDao.maxPosition() ?: -1
        }
        check(maximum < Int.MAX_VALUE) { "Project position exhausted" }
        return maximum + 1
    }

    private suspend fun <T> inTransaction(block: suspend () -> T): T = if (database != null) {
        database.withTransaction(block)
    } else {
        block()
    }

    private fun validateName(name: String): String {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Project name must not be blank" }
        require(TitleLimits.count(normalizedName) <= TitleLimits.PROJECT) { "Project name exceeds 60 characters" }
        return normalizedName
    }
}
