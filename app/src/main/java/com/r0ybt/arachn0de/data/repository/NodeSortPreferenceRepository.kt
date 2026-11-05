package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NodeSortPreferenceRepository(private val database: Arachn0deDatabase) {
    suspend fun configure(context:String,value:com.r0ybt.arachn0de.domain.model.SortConfiguration)=withContext(Dispatchers.IO) {database.withTransaction {
        val project=context.substringBeforeLast(':');val node=context.substringAfterLast(':')
        if(context==com.r0ybt.arachn0de.domain.model.SortConfigurationCodec.PROJECTS_CONTEXT) require(!value.inherited)
        else {
            require(database.projectDao().getById(project)!=null)
            require(node=="project-root" || database.nodeDao().getById(node)?.projectId==project)
        }
        database.nodeSortPreferenceDao().save(NodeSortPreferenceEntity(context,com.r0ybt.arachn0de.domain.model.SortConfigurationCodec.encode(value),value.appliedLayersFirst))
    }}
    fun observe() = database.nodeSortPreferenceDao().observe()
    suspend fun set(context: String, mode: String?,layersFirst:Boolean?=null) = withContext(Dispatchers.IO) { database.withTransaction {
        val projects = database.projectDao().getAll()
        val valid = projects.any { context == "${it.id}:project-root" } || projects.any { project ->
            context.startsWith("${project.id}:") && database.nodeDao().getById(context.removePrefix("${project.id}:"))?.projectId == project.id }
        if (valid) {
            val dao = database.nodeSortPreferenceDao()
            val old=dao.all().firstOrNull {it.context==context}
            if(mode==null) {
                if(old?.layersFirst==true) dao.save(old.copy(mode="INHERIT")) else dao.delete(context)
            } else {
                val effectiveMode=if(layersFirst!=null) old?.mode ?: if(context.endsWith(":project-root")) mode else "INHERIT" else mode
                dao.save(NodeSortPreferenceEntity(context,effectiveMode,layersFirst ?: old?.layersFirst ?: false))
            }
        }
    } }
}
