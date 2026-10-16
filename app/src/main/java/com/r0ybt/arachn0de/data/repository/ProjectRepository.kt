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
    internal var beforeDelete:suspend (String)->Unit = {}
    constructor(projectDao: ProjectDao, currentTimeMillis: () -> Long) :
        this(projectDao, null, currentTimeMillis)

    fun observeProjects(): Flow<List<Project>> = projectDao.observeWithPhotos().map { rows -> rows.map { it.toProject() } }

    suspend fun setPinned(id:String,pinned:Boolean):Boolean = inTransaction {projectDao.setPinned(id,pinned)==1}

    suspend fun normalizeProjectOrder() = inTransaction {
        val ordered = sortProjectsForDisplay(projectDao.getAll())
        writeOrder(ordered)
    }

    suspend fun reorderProject(id:String,moveUp:Boolean):Boolean = inTransaction {
        val current=projectDao.getById(id) ?: return@inTransaction false
        val group=sortProjectsForDisplay(projectDao.getAll()).filter {it.isPinned==current.isPinned}
        val from=group.indexOfFirst {it.id==id};val to=from+if(moveUp) -1 else 1
        if(to in group.indices) writePinnedGroupOrder(group,group.toMutableList().apply {java.util.Collections.swap(this,from,to)})
        true
    }
    suspend fun reorderProjectTo(id:String,targetId:String):Boolean = inTransaction {
        val current=projectDao.getById(id) ?: return@inTransaction false
        val target=projectDao.getById(targetId) ?: return@inTransaction false
        if(current.isPinned!=target.isPinned) return@inTransaction false
        val group=sortProjectsForDisplay(projectDao.getAll()).filter {it.isPinned==current.isPinned}
        val from=group.indexOfFirst {it.id==id};val to=group.indexOfFirst {it.id==targetId}
        val after=group.toMutableList();after.add(to,after.removeAt(from));writePinnedGroupOrder(group,after)
        true
    }
    private suspend fun writePinnedGroupOrder(before:List<ProjectEntity>,after:List<ProjectEntity>) {
        val positions=before.map {it.position}.let {if(it.distinct().size==it.size) it else before.indices.toList()}
        after.forEachIndexed {i,row->if(row.position!=positions[i]) check(projectDao.updateOrder(row.id,positions[i],currentTimeMillis())==1)}
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

    /** Merge one property with the latest row; never overwrite a concurrent edit silently. */
    suspend fun updateField(id:String,field:String,original:String,value:String):Boolean = inTransaction {
        require(field=="title" || field=="description")
        val current=projectDao.getById(id) ?: return@inTransaction false
        check((if(field=="title") current.name else current.description)==original) {"El campo cambió durante la edición."}
        updateProject(id,if(field=="title") value else current.name,
            if(field=="description") value.trim() else current.description)
    }

    /** Save editor content and owner-specific order atomically; missing technologies roll back. */
    suspend fun saveWithTechnologies(id:String?,creationId:String,name:String,description:String,technologyIds:List<String>?,tagIds:Set<String>?=null,peopleIds:Set<String>?=null,priority:com.r0ybt.arachn0de.domain.model.Priority?=null):Boolean = inTransaction {
        val db=requireNotNull(database)
        if(technologyIds!=null) require(technologyIds.distinct().size==technologyIds.size && technologyIds.all {db.technologyDao().get(it)!=null})
        val owner=id ?: creationId
        val saved=if(id==null) {createProject(name,description,creationId);true} else updateProject(id,name,description)
        if(saved && technologyIds!=null) {
            db.technologyDao().clearProject(owner)
            db.technologyDao().assignProjects(technologyIds.mapIndexed {position,technology->com.r0ybt.arachn0de.data.local.ProjectTechnologyEntity(owner,technology,position)})
        }
        if(saved && (tagIds!=null || peopleIds!=null || priority!=null)) {
            val conversion=db.conversionDao()
            var root=conversion.project(owner)
            val oldTags=root?.let {r->conversion.tags().filter {it.rootId==r.id}.mapTo(hashSetOf()) {it.tagId}}.orEmpty()
            tagIds?.let {ids->
                require(ids.size<=1 || ids==oldTags) {"Selecciona una etiqueta o ninguna."}
                TagRepository(db).validate(ids)
            }
            peopleIds?.forEach {requireNotNull(db.personDao().get(it)) {"Persona inexistente."}}
            if(root==null && (!tagIds.isNullOrEmpty() || !peopleIds.isNullOrEmpty() || priority!=null && priority!=com.r0ybt.arachn0de.domain.model.Priority.NONE)) {
                val project=requireNotNull(db.projectDao().getById(owner))
                root=com.r0ybt.arachn0de.data.local.ConversionRootEntity(UUID.randomUUID().toString(),owner,null,owner,owner,project.position,0,null,null,"NONE",null,false)
                conversion.save(root)
            }
            root?.let {r->
                if(tagIds!=null) {conversion.clearTags(r.id);conversion.tags(tagIds.map {com.r0ybt.arachn0de.data.local.ConversionTagEntity(r.id,it)})}
                if(peopleIds!=null) {conversion.clearPeople(r.id);conversion.people(peopleIds.map {com.r0ybt.arachn0de.data.local.ConversionPersonEntity(r.id,it)})}
                if(priority!=null) conversion.save(r.copy(priority=priority.name))
            }
        }
        saved
    }

    /** Deleting an already absent project returns false. */
    suspend fun deleteProject(id: String): Boolean {
        beforeDelete(id)
        return inTransaction {
        database?.let {db->
            val ids=db.nodeDao().getProjectNodes(id).mapTo(hashSetOf()) {it.id}
            BacklogRepository(db).rejectProjectDeletion(id,ids)
            SprintLinkRepository(db).rejectDeletion(ids)
            // Remove owned histories before nodes with RESTRICT foreign keys, in this transaction.
            db.conversionDao().roots().filter {it.projectId==id || it.nodeId in ids}.forEach {db.managementContextDao().delete(it.id)}
        }
        val deleted = projectDao.delete(id) == 1
        database?.let {com.r0ybt.arachn0de.access.AccessRepository(it).cleanReferences()}
        if (deleted) {
            val ordered = sortProjectsForDisplay(projectDao.getAll())
            writeOrder(ordered)
        }
        deleted
    }
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
