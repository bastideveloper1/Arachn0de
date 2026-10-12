package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import java.util.UUID

internal enum class ManagementMethodology(val label:String) { AGILE("Ágil"), TRADITIONAL("Tradicional"), HYBRID("Híbrida") }

internal class ManagementRepository(private val db:Arachn0deDatabase) {
    val gantt=GanttRepository(db)
    val relations=CascadeRelationsRepository(db)
    val cascade=ManagementCascadeRepository(db)
    val kanban=KanbanRepository(db)
    val iterations=IterationRepository(db)
    val participation=ParticipationRepository(db)
    val backlog=BacklogRepository(db)
    val planning=PlanningRepository(db)
    val roots get()=db.conversionDao().observeRoots()
    val contexts get()=db.managementContextDao().observe()
    suspend fun configure(projectId:String?,nodeId:String?,enabled:Boolean,methodology:ManagementMethodology):ManagementContextEntity? = db.withTransaction {
        require((projectId==null)!=(nodeId==null))
        val conversion=db.conversionDao()
        var root=if(projectId!=null) conversion.project(projectId) else conversion.node(requireNotNull(nodeId))
        if(root==null) {
            require(projectId!=null) {"Esta capa no tiene un contexto de proyecto."}
            val project=requireNotNull(db.projectDao().getById(projectId)) {"El proyecto ya no existe."}
            if(!enabled) return@withTransaction null
            root=ConversionRootEntity(UUID.randomUUID().toString(),projectId,null,projectId,projectId,
                project.position,0,null,null,"NONE",null,false)
            conversion.save(root)
        }
        ManagementContextEntity(root.id,enabled,methodology.name).also {db.managementContextDao().save(it);if(enabled) cascade.ensure(it.id);backlog.validateStructure()}
    }
}
