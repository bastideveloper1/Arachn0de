package com.r0ybt.arachn0de.gantt

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import java.time.*
import java.util.UUID
import androidx.room.withTransaction

/** Explicit local example. All records are new; one Room transaction publishes the whole tree. */
internal object GanttDemo {
    data class Result(val project:String,val context:String,val people:List<String>,val sprints:List<String>,val base:LocalDate)
    suspend fun create(db:Arachn0deDatabase,persons:PersonRepository,today:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault(), beforeCommit: suspend () -> Unit = {}):Result = db.withTransaction {
        val people=(1..6).map {UUID.randomUUID().toString().also {id->db.personDao().insert(PersonEntity(id,listOf("DEMO Ana","DEMO Carlos","DEMO Sofía","DEMO Diego","DEMO Laura","DEMO Omar")[it-1],null))}}
        val project=ProjectRepository(db.projectDao(),db).createProject("Demo — Gestión de proyectos · "+UUID.randomUUID().toString().take(6),"Escenario ficticio aislado. Catálogo, préstamos y consultas.").id
        val nodes=NodeRepository(db);val management=nodes.management
        val context=management.configure(project,null,true,ManagementMethodology.AGILE)!!.id
        val structure=db.managementLayerDao().all().filter {it.contextId==context}.associateBy {it.kind}
        val texts=mapOf("PURPOSE" to "Facilitar el acceso a libros y evitar registros duplicados. Justificación: mejorar préstamos.","EXCLUSIONS" to "No incluye servicios externos ni pagos.","SCOPE" to "Catálogo, préstamos y consultas locales.","DELIVERABLES" to "Catálogo operativo, circulación y manual.","SUCCESS" to "Registrar, localizar y prestar libros sin pérdida.","RISKS" to "Cambios solicitados durante la revisión.")
        texts.forEach {(kind,text)->management.cascade.createSection(context,ManagementLayerKind.valueOf(kind).title,text)}
        people.forEachIndexed {i,p->management.participation.add(context,p);management.participation.role(context,p,if(i==0) ResponsibilityRole.PROJECT_LEAD else ResponsibilityRole.EXECUTOR,true)}
        val base=today.minusDays(today.dayOfWeek.value.toLong()-1)
        fun time(d:LocalDate)=d.atStartOfDay(zone).toInstant().toEpochMilli()
        val sprints=mutableListOf<String>()
        repeat(3) {s->
            val start=base.plusDays((s-1)*14L);val end=start.plusDays(13)
            val sprint=management.cascade.create(context,structure.getValue("SPRINTS").nodeId,"Sprint ${s+1}",sprint=true);sprints+=sprint
            persons.setResponsiblePeople(sprint,setOf(people[s]))
            val goal=management.planning.createObjective(context,listOf("Administrar catálogo","Gestionar préstamos","Consultar información")[s])
            management.planning.createCriterion(goal.id,"Demostrar el objetivo con pruebas y revisión ficticia.")
            management.participation.assign(context,people[s],goal.id,true,ResponsibilityRole.PROJECT_LEAD)
            val epic=management.cascade.create(context,structure.getValue("BACKLOG").nodeId,listOf("Libros","Circulación","Consultas")[s],LayerRole.EPIC)
            management.backlog.linkObjective(context,goal.id,epic)
            val plan=management.iterations.configure(context,sprint,"Entrega ${s+1}",time(start),time(end))
            repeat(3) {h->
                val story=management.cascade.create(context,epic,"HU${s*3+h+1} — "+listOf("Registrar","Editar","Consultar")[h],LayerRole.STORY)
                val a=start.plusDays(h*3L);val b=a.plusDays(3L+h)
                nodes.updateNode(story,db.nodeDao().getById(story)!!.title,"Criterio: conservar la identidad del registro.")
                management.gantt.planStory(context,story,time(a),time(b))
                management.participation.assign(context,people[(s+h)%people.size],story,false,ResponsibilityRole.EXECUTOR)
                repeat(3) {k->
                    val task=nodes.createNode(project,story,"${listOf("Diseño","Implementación","Pruebas")[k]} HU${s*3+h+1}",startAt=if(s==2 && h==2 && k==2)null else time(a.plusDays(k.toLong())),dueAt=if(s==2 && h==2 && k==2)null else time(minOf(b,a.plusDays(k+1L))),responsibleIds=if(h==2 && k==2) emptySet() else setOf(people[(s+h+k)%people.size]),priority=if(k==2) Priority.HIGH else Priority.NONE)
                    if(s==1 && h==0 && k==1) com.r0ybt.arachn0de.access.AccessRepository(db).toggle("node:"+task.id)
                    if(k==1) persons.setResponsiblePeople(task.id,setOf(people[(s+h)%people.size],people[(s+h+1)%people.size]))
                    if(s==0 && h==0 && k==0) {
                        nodes.createNode(project,task.id,"Decisión de diseño","Nota ficticia: revisar campos con el equipo.",purpose=NodePurpose.NOTE)
                        nodes.createNode(project,task.id,"Revisar diseño HU1",startAt=time(a),dueAt=time(a.plusDays(1)),responsibleIds=setOf(people[0]))
                    }
                }
                nodes.createNode(project,story,"Incidencia","Solicitud adicional documentada; no altera aceptación.",purpose=NodePurpose.NOTE)
                management.relations.addStory(sprint,story)
            }
            if(s<2) management.iterations.start(plan.id)
            val current= db.sprintLinkDao().all().filter {it.sprintId==sprint}.map {it.taskId}
            val titles=db.backupDao().nodes().associate {it.id to it.title}
            current.sortedBy {titles.getValue(it)}.forEachIndexed {i,id->nodes.setWorkState(id,if(s==0) WorkState.VALIDATED else if(s==1) listOf(WorkState.PLANNED,WorkState.DOING,WorkState.DONE)[i%3] else WorkState.UNPLANNED);if(s==0 || (s==1 && i%3==2)) nodes.setCompleted(id,true)}
            if(s==0) management.iterations.close(plan.id,current.map {db.nodeDao().getById(it)!!})
        }
        management.gantt.save(context,"☠️ Solicitan extra HU2",base.plusDays(2).toEpochDay(),.25)
        management.gantt.save(context,"✅ Revisión del catálogo",base.minusDays(10).toEpochDay(),.6)
        beforeCommit()
        Result(project,context,people,sprints,base)
    }
}
