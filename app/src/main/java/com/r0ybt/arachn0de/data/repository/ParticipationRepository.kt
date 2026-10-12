package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

internal enum class ResponsibilityRole(val label:String) {
    PROJECT_LEAD("Lead"), EXECUTOR("Ejecutor"), SUPERVISOR("Supervisor"), VALIDATOR("Validador")
}
internal data class ParticipationRecords(
    val participants:List<ManagementParticipantEntity> = emptyList(),
    val roles:List<ParticipantRoleEntity> = emptyList(),
    val objectives:List<ObjectiveResponsibilityEntity> = emptyList(),
    val nodes:List<NodeResponsibilityEntity> = emptyList()
) {
    fun validate(people:List<PersonEntity>,contexts:List<ManagementContextEntity>,goals:List<ManagementObjectiveEntity>,tree:List<NodeEntity>,roots:List<ConversionRootEntity>,layers:List<ManagementRoleEntity>) {
        fun valid(ok:Boolean) {require(ok) {"La operación invalidaría responsabilidades de gestión. Retira primero las responsabilidades afectadas; los elementos se conservarán."}}
        valid(participants.distinct().size==participants.size && roles.distinct().size==roles.size && objectives.distinct().size==objectives.size && nodes.distinct().size==nodes.size)
        val persons=people.mapTo(hashSetOf()) {it.id};val contextsIds=contexts.mapTo(hashSetOf()) {it.id}
        val members=participants.mapTo(hashSetOf()) {it.contextId to it.personId};val owners=contextOwners(tree,roots,contexts)
        val goalOwners=goals.associate {it.id to it.contextId};val byNode=tree.associateBy {it.id};val byLayer=layers.associateBy {it.nodeId}
        val roleNames=ResponsibilityRole.entries.mapTo(hashSetOf()) {it.name}
        participants.forEach {valid(it.contextId in contextsIds && it.personId in persons)}
        roles.forEach {valid(it.contextId to it.personId in members && it.role in roleNames)}
        objectives.forEach {valid(it.contextId to it.personId in members && it.role in roleNames && goalOwners[it.objectiveId]==it.contextId)}
        nodes.forEach {valid(it.contextId to it.personId in members && it.role in roleNames && owners[it.nodeId]==it.contextId && (byNode[it.nodeId]?.purpose=="ACTION" || (byNode[it.nodeId]?.purpose=="LAYER" && byLayer[it.nodeId]?.contextId==it.contextId && byLayer[it.nodeId]?.role in listOf("EPIC","STORY"))))}
    }
}
internal data class ParticipationState(val people:List<PersonEntity> = emptyList(),val records:ParticipationRecords=ParticipationRecords(),val work:BacklogState=BacklogState())
internal class ParticipationRepository(private val db:Arachn0deDatabase) {
    private val dao get()=db.participationDao()
    suspend fun records()=ParticipationRecords(dao.participants(),dao.roles(),dao.objectives(),dao.nodes())
    fun observe(context:String)=combine(db.personDao().observePeople(),combine(dao.observeParticipants(),dao.observeRoles(),dao.observeObjectives(),dao.observeNodes(),::ParticipationRecords),BacklogRepository(db).observe(context),::ParticipationState).flowOn(Dispatchers.Default)
    suspend fun validate(rows:ParticipationRecords?=null,tree:List<NodeEntity>?=null) {(rows ?: records()).validate(db.backupDao().persons(),db.managementContextDao().all(),db.planningDao().objectives(),tree ?: db.backupDao().nodes(),db.conversionDao().roots(),db.backlogDao().roles())}
    private suspend fun active(context:String) {require(db.managementContextDao().all().any {it.id==context && it.enabled}) {"La gestión está desactivada."}}
    suspend fun add(context:String,person:String)=db.withTransaction {active(context);dao.insert(ManagementParticipantEntity(context,person));validate()}
    suspend fun remove(context:String,person:String,confirmed:Boolean=false)=db.withTransaction {
        active(context);val rows=records()
        val count=rows.roles.count {it.contextId==context && it.personId==person}+rows.objectives.count {it.contextId==context && it.personId==person}+rows.nodes.count {it.contextId==context && it.personId==person}
        require(count==0 || confirmed) {"Confirma la retirada de la participación: se perderán $count roles y responsabilidades. La Persona y todos los elementos se conservarán."}
        dao.remove(context,person)
    }
    suspend fun role(context:String,person:String,role:ResponsibilityRole,assigned:Boolean)=db.withTransaction {
        active(context);if(assigned) dao.insert(ParticipantRoleEntity(context,person,role.name)) else dao.removeRole(context,person,role.name);validate()
    }
    suspend fun assign(context:String,person:String,target:String,objective:Boolean,role:ResponsibilityRole,assigned:Boolean=true)=db.withTransaction {
        active(context)
        if(objective) {if(assigned) dao.insert(ObjectiveResponsibilityEntity(context,person,target,role.name)) else dao.removeObjective(context,person,target,role.name)}
        else {if(assigned) dao.insert(NodeResponsibilityEntity(context,person,target,role.name)) else dao.removeNode(context,person,target,role.name)}
        validate()
    }
    suspend fun rejectDeletion(ids:Set<String>) {require(dao.nodes().none {it.nodeId in ids}) {"Retira primero las responsabilidades de gestión de los elementos afectados. Reactiva la gestión si está desactivada."}}
    suspend fun restore(rows:ParticipationRecords) {rows.participants.forEach {dao.insert(it)};rows.roles.forEach {dao.insert(it)};rows.objectives.forEach {dao.insert(it)};rows.nodes.forEach {dao.insert(it)}}
}
