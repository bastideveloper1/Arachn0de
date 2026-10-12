package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import java.util.UUID

internal class GanttRepository(private val db:Arachn0deDatabase) {
    val stories get()=db.cascadeRelationDao().observeStories()
    /** Planned story interval only; containers still have no operational temporal state. */
    suspend fun planStory(context:String,id:String,start:Long?,end:Long?)=db.withTransaction {
        require(db.managementContextDao().all().any {it.id==context && it.enabled}) {"La gestión está desactivada."}
        require(db.backlogDao().roles().any {it.contextId==context && it.nodeId==id && it.role=="STORY"}) {"Selecciona una Historia de este proyecto."}
        val node=requireNotNull(db.nodeDao().getById(id))
        require(node.purpose=="LAYER")
        com.r0ybt.arachn0de.domain.model.TaskTemporal.validateDates(start,end)
        require((start==null || start>=0) && (end==null || end>=0)) {"Fecha inválida."}
        db.nodeDao().updateContentAndDates(id,node.title,node.description,start,end,System.currentTimeMillis())
    }
    fun annotations(context:String)=db.ganttAnnotationDao().observe(context)
    suspend fun save(context:String,text:String,day:Long,lane:Double,id:String=UUID.randomUUID().toString())=db.withTransaction {
        val contexts=db.managementContextDao().all()
        require(contexts.any {it.id==context && it.enabled}) {"La gestión está desactivada."}
        require(db.ganttAnnotationDao().all().none {it.id==id && it.contextId!=context}) {"La anotación pertenece a otro proyecto."}
        val row=GanttAnnotationEntity(id,context,text.trim(),day,lane)
        validateGanttAnnotations(listOf(row),contexts.mapTo(hashSetOf()) {it.id});db.ganttAnnotationDao().save(row)
    }
    suspend fun delete(context:String,id:String)=db.withTransaction {db.ganttAnnotationDao().delete(id,context)}
}
