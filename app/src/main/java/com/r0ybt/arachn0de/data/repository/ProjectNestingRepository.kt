package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

/** Identity-preserving transfer: immutable projectId requires physical reinsertion, never new tasks. */
internal class ProjectNestingRepository(private val database:Arachn0deDatabase,
    private val beforeCommit:suspend ()->Unit = {}) {
    suspend fun move(sourceId:String,targetId:String,parentId:String?):String = withContext(Dispatchers.IO) {
        database.withTransaction {
            require(sourceId!=targetId) { "El proyecto no puede moverse dentro de sí mismo." }
            val projects=database.projectDao();val nodes=database.nodeDao()
            val source=requireNotNull(projects.getById(sourceId)) { "El proyecto ya no existe." }
            requireNotNull(projects.getById(targetId)) { "El destino ya no existe." }
            val targetNodes=nodes.getProjectNodes(targetId).associateBy { it.id }
            val visited=hashSetOf<String>();var ancestor=parentId
            while(ancestor!=null) {
                require(visited.add(ancestor)) { "Destino inválido." }
                val row=requireNotNull(targetNodes[ancestor]) { "La capa de destino ya no existe." }
                require(row.purpose=="LAYER" && row.amountMinor==null) { "El destino debe ser una capa." }
                ancestor=row.parentId
            }
            val original=nodes.getProjectNodes(sourceId)
            val originalParents=original.mapNotNullTo(hashSetOf()) { it.parentId }
            NodeTreeSnapshot(original.map { it.toNode(it.id in originalParents) }) // Validate before destructive writes.
            val children=original.groupBy { it.parentId }
            val ordered=ArrayList<NodeEntity>(original.size);val queue=ArrayDeque<NodeEntity>()
            queue.addAll(children[null].orEmpty())
            while(queue.isNotEmpty()) { currentCoroutineContext().ensureActive();val row=queue.removeFirst();ordered.add(row);queue.addAll(children[row.id].orEmpty()) }
            check(ordered.size==original.size)
            val batches=original.map { it.id }.chunked(500)
            val people=batches.flatMap { database.personDao().assignmentsForNodes(it) }
            val tags=batches.flatMap { database.tagDao().tagsForNodes(it) }
            // Per-node tie order is preserved by restoring chronological/rowid ascending entries.
            val events=batches.flatMap { database.nodeEventDao().eventsForNodes(it).asReversed() }
            val defaultsDao=database.creationDefaultsDao()
            val defaults=defaultsDao.forProject(sourceId).filter { it.projectId==sourceId }
            val defaultsIds=defaults.mapTo(hashSetOf()) { it.id }
            val defaultsTags=defaultsDao.tagsForProject(sourceId).filter { it.defaultsId in defaultsIds }
            val defaultsPeople=defaultsDao.peopleForProject(sourceId).filter { it.defaultsId in defaultsIds }
            val rules=database.recurrenceDao().rules().filter { it.projectId==sourceId }
            var layerId:String
            do { layerId=UUID.randomUUID().toString() } while(nodes.getById(layerId)!=null || layerId==sourceId)
            var maximum=nodes.maxPosition(targetId,parentId) ?: -1
            if(maximum==Int.MAX_VALUE) {
                nodes.getSiblings(targetId,parentId).forEachIndexed { index,row -> check(nodes.updateOrder(row.id,index,row.updatedAt)==1) }
                maximum=nodes.maxPosition(targetId,parentId) ?: -1
            }
            check(maximum<Int.MAX_VALUE)
            nodes.insert(NodeEntity(layerId,targetId,parentId,source.name,source.description,false,maximum+1,source.createdAt,source.updatedAt,purpose="LAYER"))
            batches.forEach { nodes.detachNodes(it) }
            batches.forEach { nodes.deleteDetachedNodes(it) }
            ordered.forEach { row ->
                currentCoroutineContext().ensureActive()
                nodes.insert(row.copy(projectId=targetId,parentId=row.parentId ?: layerId))
            }
            database.personDao().assign(people);database.tagDao().assignNodes(tags);database.nodeEventDao().insertAll(events)
            val remap=defaults.associate { row -> row.id to if(row.nodeId==null) "N:$layerId" else row.id }
            defaults.forEach { row -> defaultsDao.save(row.copy(id=remap.getValue(row.id),projectId=targetId,nodeId=row.nodeId ?: layerId)) }
            defaultsDao.insertTags(defaultsTags.map { it.copy(defaultsId=remap.getValue(it.defaultsId)) })
            defaultsDao.insertPeople(defaultsPeople.map { it.copy(defaultsId=remap.getValue(it.defaultsId)) })
            rules.forEach { database.recurrenceDao().update(it.copy(projectId=targetId,parentId=it.parentId ?: layerId)) }
            check(projects.deleteProjectRow(sourceId)==1)
            beforeCommit()
            currentCoroutineContext().ensureActive()
            layerId
        }
    }
}
