package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

internal enum class LayerRole(val label:String) { EPIC("Épica"), STORY("Historia") }
internal data class BacklogRecords(
    val roles:List<ManagementRoleEntity> = emptyList(),val objectiveEpics:List<ObjectiveEpicEntity> = emptyList(),
    val epicStories:List<EpicStoryEntity> = emptyList(),val storyTasks:List<StoryTaskEntity> = emptyList(),val entries:List<BacklogEntryEntity> = emptyList()
) {
    fun validate(nodes:List<NodeEntity>,roots:List<ConversionRootEntity>,contexts:List<ManagementContextEntity>,objectives:List<ManagementObjectiveEntity>) {
        val byId=nodes.associateBy {it.id};val owners=contextOwners(nodes,roots,contexts);val contextsById=contexts.associateBy {it.id}
        val byRole=roles.associateBy {it.nodeId};val byObjective=objectives.associateBy {it.id}
        fun valid(ok:Boolean) {require(ok) {"La operación invalidaría relaciones de gestión. Retira los vínculos, roles y entradas afectados antes de continuar."}}
        valid(byRole.size==roles.size)
        roles.forEach {valid(it.role in listOf("EPIC","STORY") && byId[it.nodeId]?.purpose=="LAYER" && owners[it.nodeId]==it.contextId && it.contextId in contextsById)}
        valid(objectiveEpics.distinct().size==objectiveEpics.size && epicStories.distinct().size==epicStories.size && storyTasks.distinct().size==storyTasks.size)
        objectiveEpics.forEach {valid(byRole[it.epicId]?.role=="EPIC" && byObjective[it.objectiveId]?.contextId==byRole[it.epicId]?.contextId && it.objectiveId in byObjective)}
        epicStories.forEach {valid(byRole[it.epicId]?.role=="EPIC" && byRole[it.storyId]?.role=="STORY" && byRole[it.epicId]?.contextId==byRole[it.storyId]?.contextId)}
        storyTasks.forEach {valid(byRole[it.storyId]?.role=="STORY" && byId[it.taskId]?.purpose=="ACTION" && owners[it.taskId]==byRole[it.storyId]?.contextId)}
        valid(entries.map {it.nodeId}.distinct().size==entries.size && entries.map {it.contextId to it.position}.distinct().size==entries.size)
        entries.forEach {valid(it.position>=0 && it.contextId in contextsById && owners[it.nodeId]==it.contextId && (byId[it.nodeId]?.purpose=="ACTION" || byRole[it.nodeId]?.role=="STORY"))}
        // Typed edges Objective→EPIC→STORY→ACTION cannot form cycles, regardless of physical ancestry.
    }
}

/** Nearest persistent management boundary, including disabled contexts. Iterative and memoized. */
internal fun contextOwners(nodes:List<NodeEntity>,roots:List<ConversionRootEntity>,contexts:List<ManagementContextEntity>):Map<String,String?> {
    val ids=contexts.mapTo(hashSetOf()) {it.id};val managed=roots.filter {it.id in ids}
    val projectContexts=managed.filter {it.projectId!=null}.associate {it.projectId!! to it.id}
    val layerContexts=managed.filter {it.nodeId!=null}.associate {it.nodeId!! to it.id}
    val byId=nodes.associateBy {it.id};val owners=hashMapOf<String,String?>()
    for(node in nodes) {
        if(owners.containsKey(node.id)) continue
        val chain=mutableListOf<String>();val visited=hashSetOf<String>();var cursor:String?=node.id;var owner:String?=null
        while(cursor!=null) {
            require(visited.add(cursor)) {"Ciclo en la jerarquía."}
            if(owners.containsKey(cursor)) {owner=owners[cursor];break}
            val row=requireNotNull(byId[cursor]) {"Nodo de origen ausente."}
            chain.add(cursor)
            if(cursor in layerContexts) {owner=layerContexts[cursor];break}
            if(row.parentId==null) {owner=projectContexts[row.projectId];break}
            cursor=row.parentId
        }
        chain.forEach {owners[it]=owner}
    }
    return owners
}
internal data class BacklogState(val nodes:List<NodeEntity> = emptyList(),val roots:List<ConversionRootEntity> = emptyList(),val contexts:List<ManagementContextEntity> = emptyList(),val objectives:List<ManagementObjectiveEntity> = emptyList(),val records:BacklogRecords=BacklogRecords()) {
    val owners by lazy {contextOwners(nodes,roots,contexts)}
}
internal class BacklogRepository(private val db:Arachn0deDatabase) {
    val participation=ParticipationRepository(db)
    private val dao get()=db.backlogDao()
    suspend fun records()=BacklogRecords(dao.roles(),dao.objectiveEpics(),dao.epicStories(),dao.storyTasks(),dao.entries())
    suspend fun validateStructure(nodes:List<NodeEntity>? = null) {val tree=nodes ?: db.backupDao().nodes();records().validate(tree,db.conversionDao().roots(),db.managementContextDao().all(),db.planningDao().objectives());ParticipationRepository(db).validate(tree=tree);IterationRepository(db).validate(tree);CascadeRelationsRepository(db).validate(tree);validateManagementLayers(db.managementLayerDao().all(),tree,db.conversionDao().roots(),db.managementContextDao().all())}
    fun observe(contextId:String)=combine(
        db.nodeDao().observeAllNodes(),db.conversionDao().observeRoots(),db.managementContextDao().observe(),db.planningDao().observeObjectives(contextId),
        combine(dao.observeRoles(),dao.observeObjectiveEpics(),dao.observeEpicStories(),dao.observeStoryTasks(),dao.observeEntries(),::BacklogRecords)
    ,::BacklogState).map {it.apply {owners}}.flowOn(Dispatchers.Default)
    private suspend fun active(id:String) {require(db.managementContextDao().all().any {it.id==id && it.enabled}) {"La gestión está desactivada o el contexto ya no existe."}}
    private suspend fun role(id:String,context:String,type:String):ManagementRoleEntity= requireNotNull(dao.roles().firstOrNull {it.nodeId==id && it.contextId==context && it.role==type}) {"Selecciona una ${if(type=="EPIC") "épica" else "historia"} de este contexto."}
    private suspend fun append(context:String,node:String) {
        val rows=dao.entries().filter {it.contextId==context};if(rows.any {it.nodeId==node}) return
        rows.forEachIndexed {i,row->if(row.position!=i) dao.save(row.copy(position=i))}
        dao.save(BacklogEntryEntity(node,context,rows.size))
    }
    suspend fun setRole(context:String,node:String,role:LayerRole?,confirmRelationLoss:Boolean=false)=db.withTransaction {
        active(context);val before=records();val previous=before.roles.firstOrNull {it.nodeId==node}
        require(previous==null || previous.contextId==context) {"La capa pertenece a otro contexto."}
        if(previous?.role==role?.name) return@withTransaction
        val affected=before.objectiveEpics.any {it.epicId==node} || before.epicStories.any {it.epicId==node || it.storyId==node} || before.storyTasks.any {it.storyId==node} || before.entries.any {it.nodeId==node}
        require(!affected || confirmRelationLoss) {"Confirma la retirada de los vínculos y la entrada del Backlog de esta capa. El nodo y sus tareas se conservarán."}
        if(previous!=null) {dao.deleteRole(node);dao.deleteEntry(node)}
        if(role!=null) {
            dao.save(ManagementRoleEntity(node,context,role.name))
            if(role==LayerRole.STORY) append(context,node)
        }
        validateStructure()
    }
    suspend fun addTask(context:String,node:String)=db.withTransaction {
        active(context);require(db.nodeDao().getById(node)?.purpose=="ACTION") {"Selecciona una tarea existente."};append(context,node);validateStructure()
    }
    suspend fun addStory(context:String,node:String)=db.withTransaction {active(context);role(node,context,"STORY");append(context,node);validateStructure()}
    suspend fun linkObjective(context:String,objective:String,epic:String)=db.withTransaction {
        active(context);role(epic,context,"EPIC");require(db.planningDao().objective(objective)?.contextId==context) {"El objetivo pertenece a otro contexto."}
        dao.insert(ObjectiveEpicEntity(objective,epic));validateStructure()
    }
    suspend fun linkEpic(context:String,epic:String,story:String)=db.withTransaction {
        active(context);role(epic,context,"EPIC");role(story,context,"STORY");dao.insert(EpicStoryEntity(epic,story));validateStructure()
    }
    suspend fun linkTask(context:String,story:String,task:String)=db.withTransaction {
        active(context);role(story,context,"STORY");dao.insert(StoryTaskEntity(story,task));append(context,task);validateStructure();IterationRepository(db).recordScope()
    }
    suspend fun unlinkObjective(context:String,objective:String,epic:String)=db.withTransaction {active(context);role(epic,context,"EPIC");dao.unlinkObjective(objective,epic)}
    suspend fun unlinkEpic(context:String,epic:String,story:String)=db.withTransaction {active(context);role(epic,context,"EPIC");role(story,context,"STORY");dao.unlinkEpic(epic,story)}
    suspend fun unlinkTask(context:String,story:String,task:String)=db.withTransaction {active(context);role(story,context,"STORY");dao.unlinkTask(story,task)}
    suspend fun removeEntry(context:String,node:String)=db.withTransaction {active(context);require(dao.entries().any {it.contextId==context && it.nodeId==node});dao.deleteEntry(node)}
    suspend fun reorder(context:String,ids:List<String>)=db.withTransaction {
        active(context);val rows=dao.entries().filter {it.contextId==context}.associateBy {it.nodeId}
        require(ids.size==rows.size && ids.toSet()==rows.keys) {"El Backlog cambió. Revisa el orden."}
        ids.forEachIndexed {i,id->dao.save(rows.getValue(id).copy(position=i))}
    }
    /** Whole-project deletion owns its contexts and histories. Partial node deletion stays protected. */
    suspend fun rejectProjectDeletion(project:String,ids:Set<String>) {
        val roots=db.conversionDao().roots()
        val contexts=db.managementContextDao().all()
        val tree=db.backupDao().nodes()
        val owners=contextOwners(tree,roots,contexts)
        val owned=roots.filter {it.projectId==project || it.nodeId in ids}.mapTo(hashSetOf()) {it.id}
        require(tree.none {owners[it.id] in owned && it.id !in ids}) {"El contexto contiene trabajo ajeno al proyecto."}
        fun boundary(a:String,b:String)=(a in ids)!=(b in ids)
        val goals=db.planningDao().objectives().filter {it.contextId in owned}.mapTo(hashSetOf()) {it.id}
        val r=records()
        require(r.epicStories.none {boundary(it.epicId,it.storyId)} && r.storyTasks.none {boundary(it.storyId,it.taskId)} && r.objectiveEpics.none {(it.objectiveId in goals)!=(it.epicId in ids)}) {"Hay vínculos con trabajo fuera del proyecto."}
        require(db.cascadeRelationDao().stories().none {boundary(it.sprintId,it.storyId)} && db.cascadeRelationDao().objectives().none {(it.objectiveId in goals)!=(it.nodeId in ids)}) {"Hay asociaciones fuera del proyecto."}
        val iterations=IterationRepository(db).records()
        val ownedPlans=iterations.plans.filter {it.contextId in owned}.mapTo(hashSetOf()) {it.id}
        val ownedCycles=iterations.cycles.filter {it.planId in ownedPlans}.mapTo(hashSetOf()) {it.id}
        require(iterations.commitments.none {(it.cycleId in ownedCycles)!=(it.taskId in ids)}) {"El historial contiene referencias externas."}
        require(db.participationDao().nodes().none {(it.contextId in owned)!=(it.nodeId in ids)}) {"Hay responsabilidades fuera del proyecto."}
    }
    suspend fun rejectDeletion(ids:Set<String>) {
        val deletionTree=db.backupDao().nodes()
        val deletionOwners=contextOwners(deletionTree,db.conversionDao().roots(),db.managementContextDao().all())
        require(db.managementLayerDao().all().filter {it.nodeId in ids && (it.kind in essentialManagementKinds || it.kind.startsWith("OBJECTIVE:"))}.all {layer->deletionTree.filter {deletionOwners[it.id]==layer.contextId}.all {it.id in ids}}) {"Esta capa organiza la gestión. Desactivar conserva sus datos; no se puede eliminar su estructura accidentalmente."}
        IterationRepository(db).rejectDeletion(ids)
        ParticipationRepository(db).rejectDeletion(ids)
        require(db.cascadeRelationDao().stories().none {it.sprintId in ids || it.storyId in ids} && db.cascadeRelationDao().objectives().none {it.nodeId in ids}) {"Retira explícitamente las asociaciones con objetivos y Sprint antes de eliminar."}
        val rows=records()
        require(rows.roles.none {it.nodeId in ids} && rows.entries.none {it.nodeId in ids} && rows.storyTasks.none {it.taskId in ids}) {"Retira primero los vínculos, roles y entradas del Backlog afectados. La eliminación perdería relaciones de gestión. Reactiva la gestión si está desactivada."}
    }
    suspend fun restore(rows:BacklogRecords) {
        rows.roles.forEach {dao.save(it)};rows.objectiveEpics.forEach {dao.insert(it)};rows.epicStories.forEach {dao.insert(it)};rows.storyTasks.forEach {dao.insert(it)};rows.entries.forEach {dao.save(it)}
    }
}
