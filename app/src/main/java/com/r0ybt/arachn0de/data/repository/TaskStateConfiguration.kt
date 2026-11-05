package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.toNode
import com.r0ybt.arachn0de.data.local.toProject
import com.r0ybt.arachn0de.domain.model.TaskStatePolicy
import com.r0ybt.arachn0de.domain.model.TaskStatus

/** A preview is consent to this exact effective-mode change, never to late arrivals. */
internal class TaskStateConfiguration(private val db:Arachn0deDatabase,private val clock:()->Long=System::currentTimeMillis) {
    data class Request(val projectId:String,val nodeId:String?,val count:Int?)
    data class Preview(val request:Request,val previousSetting:Int?,val changedModes:Map<String,Pair<Int,Int>>,val conversions:Set<String>)
    suspend fun preview(request:Request):Preview=db.withTransaction {inspect(request)}
    private suspend fun inspect(request:Request):Preview {
        require(request.count==null || request.count==2 || request.count==3)
        require(request.nodeId!=null || request.count!=null)
        val project=requireNotNull(db.projectDao().getById(request.projectId)) {"El Núcleo ya no existe."}.toProject()
        val rows=db.nodeDao().getProjectNodes(request.projectId)
        val parents=rows.mapNotNullTo(hashSetOf()) {it.parentId}
        val before=rows.associate {it.id to it.toNode(it.id in parents)}
        val target=request.nodeId?.let {requireNotNull(before[it]) {"El elemento ya no existe."}}
        if(target!=null) require(target.workState==null && (target.isStructural || target.purpose.name=="ACTION"))
        val after=if(target==null) before else before+(target.id to target.copy(taskStateCount=request.count))
        val changedProject=if(target==null) project.copy(verificationEnabled=request.count==3) else project
        val changes=before.values.filter {it.isCompletable && it.workState==null}.mapNotNull {node->
            val old=TaskStatePolicy.count(node,before,project)
            val new=TaskStatePolicy.count(after.getValue(node.id),after,changedProject)
            if(old==new) null else node.id to (old to new)
        }.toMap()
        val conversions=changes.filter {(id,modes)->modes==(3 to 2) && before.getValue(id).awaitingVerification}.keys.toSet()
        return Preview(request,if(target==null) if(project.verificationEnabled)3 else 2 else target.taskStateCount,changes,conversions)
    }
    suspend fun apply(request:Request,confirmed:Preview?=null):Boolean=db.withTransaction {
        val current=inspect(request)
        require(confirmed==null || confirmed==current) {"El alcance cambió. Reabre el menú para revisar la conversión."}
        require(current.conversions.isEmpty() || confirmed!=null) {"Confirma la conversión de ${current.conversions.size} tareas a Pendiente."}
        val nodes=NodeRepository(db,clock)
        current.conversions.forEach {check(nodes.setTaskStatus(it,TaskStatus.PENDING))}
        val updated=if(request.nodeId==null) db.projectDao().setVerificationEnabled(request.projectId,request.count==3,clock())
            else db.nodeDao().setTaskStateCount(request.nodeId,request.count,clock())
        check(updated==1) {"No se pudo guardar la configuración."}
        true
    }
}
