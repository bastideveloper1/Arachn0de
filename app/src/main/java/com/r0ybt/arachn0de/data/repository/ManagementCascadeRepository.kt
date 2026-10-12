package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import java.util.UUID

internal enum class ManagementLayerKind(val title:String,val parent: String?=null,val purpose:String="LAYER") {
    ACTA("Acta de constitución"), BACKLOG("Backlog"), SPRINTS("Sprints"), KANBAN("Kanban"),
    OBJECTIVES("Objetivos","ACTA"), TEAM("Equipo","ACTA"),
    PURPOSE("Propósito y justificación","ACTA","NOTE"), SCOPE("Alcance","ACTA","NOTE"),
    EXCLUSIONS("Restricciones","ACTA","NOTE"), DELIVERABLES("Entregables","ACTA","NOTE"),
    SUCCESS("Criterios de éxito","ACTA","NOTE"), RISKS("Riesgos","ACTA","NOTE")
}
internal val essentialManagementKinds=setOf("ACTA","BACKLOG","SPRINTS","KANBAN","OBJECTIVES","TEAM")

internal fun validateManagementLayers(rows:List<ManagementLayerEntity>,tree:List<NodeEntity>,roots:List<ConversionRootEntity>,contexts:List<ManagementContextEntity>) {
    val nodes=tree.associateBy {it.id};val owners=contextOwners(tree,roots,contexts)
    require(rows.map {it.nodeId}.distinct().size==rows.size && rows.map {it.contextId to it.kind}.distinct().size==rows.size) {"Capas de gestión duplicadas."}
    rows.groupBy {it.contextId}.values.forEach {group->require(group.map {it.kind}.containsAll(essentialManagementKinds)) {"Estructura de gestión incompleta."}}
    val byKind=rows.associateBy {it.contextId to it.kind}
    rows.forEach {row->
        val kind=if(row.kind.startsWith("OBJECTIVE:")) ManagementLayerKind.OBJECTIVES else ManagementLayerKind.entries.firstOrNull {it.name==row.kind}
        require(kind!=null && nodes[row.nodeId]?.purpose==kind.purpose && owners[row.nodeId]==row.contextId) {"Capa de gestión fuera de su contexto o de tipo inválido."}
        val root=roots.firstOrNull {it.id==row.contextId}
        val expected=if(row.kind.startsWith("OBJECTIVE:")) requireNotNull(byKind[row.contextId to "OBJECTIVES"]).nodeId else kind.parent?.let {requireNotNull(byKind[row.contextId to it]) {"Falta la capa contenedora."}.nodeId} ?: root?.nodeId
        require(root!=null && nodes[row.nodeId]?.parentId==expected) {"La capa de gestión debe conservar su contenedor."}
    }
}

/** Creates real nodes once, in the same transaction; editable titles never identify semantics. */
internal class ManagementCascadeRepository(private val db:Arachn0deDatabase) {
    val layers get()=db.managementLayerDao().observe()
    suspend fun ensure(context:String)=db.withTransaction {
        val cfg=requireNotNull(db.managementContextDao().all().firstOrNull {it.id==context})
        if(!cfg.enabled) return@withTransaction
        val root=requireNotNull(db.conversionDao().roots().firstOrNull {it.id==context})
        val owner=root.nodeId?.let {db.nodeDao().getById(it)}
        val project=root.projectId ?: requireNotNull(owner).projectId
        val existing=db.managementLayerDao().all().filter {it.contextId==context}.associateBy {it.kind}.toMutableMap()
        val initial=existing.isEmpty()
        val charter=db.planningDao().charters().firstOrNull {it.contextId==context}
        val text=mapOf("PURPOSE" to charter?.purpose.orEmpty(),"SCOPE" to charter?.scope.orEmpty(),"EXCLUSIONS" to charter?.exclusions.orEmpty(),"DELIVERABLES" to charter?.deliverables.orEmpty(),"SUCCESS" to charter?.successConditions.orEmpty())
        for(kind in ManagementLayerKind.entries.filter {it.name in essentialManagementKinds || (initial && text[it.name].orEmpty().isNotBlank())}) {
            if(kind.name in existing) continue
            val parent=kind.parent?.let {existing.getValue(it).nodeId} ?: root.nodeId
            val id=UUID.randomUUID().toString();val now=System.currentTimeMillis()
            db.nodeDao().insert(NodeEntity(id,project,parent,kind.title,text[kind.name].orEmpty(),false,(db.nodeDao().maxPosition(project,parent) ?: -1)+1,now,now,purpose=kind.purpose))
            val mapping=ManagementLayerEntity(id,context,kind.name);db.managementLayerDao().insert(mapping);existing[kind.name]=mapping
        }
        for(goal in db.planningDao().objectives().filter {it.contextId==context}) {
            val kind="OBJECTIVE:"+goal.id
            if(kind !in existing) {
                require(db.nodeDao().getById(goal.id)==null) {"Colisión entre identidad de objetivo y nodo; no se modificaron datos."}
                val now=System.currentTimeMillis()
                db.nodeDao().insert(NodeEntity(goal.id,project,existing.getValue("OBJECTIVES").nodeId,goal.title,goal.description,false,(db.nodeDao().maxPosition(project,existing.getValue("OBJECTIVES").nodeId) ?: -1)+1,now,now,purpose="LAYER"))
                val row=ManagementLayerEntity(goal.id,context,kind);db.managementLayerDao().insert(row);existing[kind]=row
            }
        }
        if(!initial) {validateManagementLayers(db.managementLayerDao().all(),db.backupDao().nodes(),db.conversionDao().roots(),db.managementContextDao().all());return@withTransaction}
        // Existing semantic nodes retain identity, descendants, associations and phase. Only their
        // canonical parent changes. A story shared by epics retains all links and chooses one parent.
        val tree=db.backupDao().nodes();val owners=contextOwners(tree,db.conversionDao().roots(),db.managementContextDao().all())
        val roles=db.backlogDao().roles().filter {it.contextId==context}
        val byId=tree.associateBy {it.id}
        suspend fun place(id:String,parent:String) {
            val row=requireNotNull(byId[id]);if(row.parentId==parent) return
            require(owners[id]==context)
            // Moving an ancestor into its descendant would corrupt the tree: retain its existing
            // location and expose the original through its persistent semantic relationship instead.
            require(parent !in db.nodeDao().getSubtreeIds(id)) {"La organización existente contiene una relación jerárquica incompatible; no se modificaron datos."}
            db.nodeDao().move(id,parent,(db.nodeDao().maxPosition(project,parent) ?: -1)+1,System.currentTimeMillis(),row.workState)
        }
        roles.filter {it.role=="EPIC"}.forEach {place(it.nodeId,existing.getValue("BACKLOG").nodeId)}
        val links=db.backlogDao().epicStories()
        roles.filter {it.role=="STORY"}.forEach {story->
            val epics=links.filter {it.storyId==story.nodeId}.map {it.epicId}.sorted()
            val current=byId[story.nodeId]?.parentId
            val parent=epics.firstOrNull {it==current} ?: epics.firstOrNull() ?: existing.getValue("BACKLOG").nodeId
            place(story.nodeId,parent)
        }
        tree.filter {it.sprintMode && owners[it.id]==context && roles.none {r->r.nodeId==it.id}}.forEach {place(it.id,existing.getValue("SPRINTS").nodeId)}
        BacklogRepository(db).validateStructure()
        validateManagementLayers(db.managementLayerDao().all(),db.backupDao().nodes(),db.conversionDao().roots(),db.managementContextDao().all())
    }
    /** Explicit edits update legacy text adapters atomically; the node remains the identity. */
    suspend fun synchronizeText(id:String) {
        val mapping=db.managementLayerDao().all().firstOrNull {it.nodeId==id} ?: return
        val node=db.nodeDao().getById(id) ?: return
        if(mapping.kind.startsWith("OBJECTIVE:")) {
            db.planningDao().objective(id)?.let {db.planningDao().save(it.copy(title=node.title,description=node.description))}
            return
        }
        val old=db.planningDao().charters().firstOrNull {it.contextId==mapping.contextId} ?: ProjectCharterEntity(mapping.contextId)
        val current=when(mapping.kind) {
            "PURPOSE"->old.copy(purpose=node.description)
            "SCOPE"->old.copy(scope=node.description)
            "EXCLUSIONS"->old.copy(exclusions=node.description)
            "DELIVERABLES"->old.copy(deliverables=node.description)
            "SUCCESS"->old.copy(successConditions=node.description)
            else->return
        }
        db.planningDao().save(current)
    }
    suspend fun createSection(context:String,title:String,content:String):String=db.withTransaction {
        require(db.managementContextDao().all().any {it.id==context && it.enabled}) {"La gestión está desactivada."}
        require(title.isNotBlank()) {"Escribe un título para la sección."}
        val acta=requireNotNull(db.managementLayerDao().all().firstOrNull {it.contextId==context && it.kind=="ACTA"})
        val parent=requireNotNull(db.nodeDao().getById(acta.nodeId))
        NodeRepository(db).createNode(parent.projectId,parent.id,title.trim(),content,purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.NOTE).id
    }
    suspend fun create(context:String,parent:String,title:String,role:LayerRole?=null,sprint:Boolean=false):String=db.withTransaction {
        require(db.managementContextDao().all().any {it.id==context && it.enabled}) {"La gestión está desactivada."}
        require(title.isNotBlank()) {"Escribe un título."}
        val root=requireNotNull(db.nodeDao().getById(parent))
        require(contextOwners(db.backupDao().nodes(),db.conversionDao().roots(),db.managementContextDao().all())[parent]==context)
        val nodes=NodeRepository(db)
        val id=nodes.createNode(root.projectId,parent,title.trim(),purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER).id
        if(sprint) nodes.setSprintMode(id,true)
        if(role!=null) BacklogRepository(db).setRole(context,id,role)
        if(role==LayerRole.STORY && db.backlogDao().roles().any {it.nodeId==parent && it.role=="EPIC"}) BacklogRepository(db).linkEpic(context,parent,id)
        if(sprint) IterationRepository(db).configure(context,id)
        id
    }
}
