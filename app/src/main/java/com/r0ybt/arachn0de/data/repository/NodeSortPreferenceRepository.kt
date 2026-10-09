package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NodeSortPreferenceRepository(private val database: Arachn0deDatabase) {
    fun observe() = database.nodeSortPreferenceDao().observe()
    suspend fun set(context: String, mode: String?) = withContext(Dispatchers.IO) { database.withTransaction {
        val projects = database.projectDao().getAll()
        val valid = projects.any { context == "${it.id}:project-root" } || projects.any { project ->
            context.startsWith("${project.id}:") && database.nodeDao().getById(context.removePrefix("${project.id}:"))?.projectId == project.id }
        if (valid) {
            val dao = database.nodeSortPreferenceDao()
            if (mode == null) dao.delete(context) else dao.save(NodeSortPreferenceEntity(context, mode))
        }
    } }
}
