package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.defaults.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CreationDefaultsCodec {
    fun key(scope:DefaultsScope):String = when(scope) { DefaultsScope.Global -> "G";is DefaultsScope.Project -> "P:${scope.projectId}";is DefaultsScope.Layer -> "N:${scope.nodeId}" }
    fun scope(row:CreationDefaultsEntity):DefaultsScope {
        val scope=when { row.projectId==null -> { require(row.nodeId==null);DefaultsScope.Global }
            row.nodeId==null -> DefaultsScope.Project(row.projectId)
            else -> DefaultsScope.Layer(row.projectId,row.nodeId) }
        require(row.id==key(scope)) { "Identidad de defaults inválida." };return scope
    }
    private fun <T> own(value:T?):DefaultValue<T> = if(value==null) DefaultValue.Inherit else DefaultValue.Own(value)
    private fun <T> value(field:DefaultValue<T>):T? = (field as? DefaultValue.Own)?.value
    private fun date(rule:String?,number:Int?):DefaultValue<DefaultDate> {
        if(rule==null) { require(number==null);return DefaultValue.Inherit }
        return DefaultValue.Own(DefaultDate(DefaultDateKind.valueOf(rule),requireNotNull(number)))
    }
    private fun time(minute:Int?):DefaultValue<DefaultTime> = when(minute) { null -> DefaultValue.Inherit;-1 -> DefaultValue.Own(DefaultTime.Unspecified);else -> DefaultValue.Own(DefaultTime.Minute(minute)) }
    private fun minute(field:DefaultValue<DefaultTime>):Int? = when(val time=value(field)) { null -> null;DefaultTime.Unspecified -> -1;is DefaultTime.Minute -> time.value }
    fun decode(row:CreationDefaultsEntity,tags:Set<String> = emptySet(),people:Set<String> = emptySet()):CreationDefaults {
        scope(row);require(row.tagsOverride || tags.isEmpty());require(row.peopleOverride || people.isEmpty())
        return CreationDefaults(own(row.purpose?.let(NodePurpose::valueOf)),own(row.obligation),own(row.currency),own(row.priority?.let(Priority::valueOf)),
            if(row.tagsOverride) DefaultValue.Own(tags) else DefaultValue.Inherit,
            if(row.peopleOverride) DefaultValue.Own(people) else DefaultValue.Inherit,
            date(row.startRule,row.startNumber),time(row.startMinute),date(row.dueRule,row.dueNumber),time(row.dueMinute))
    }
    fun encode(scope:DefaultsScope,defaults:CreationDefaults):CreationDefaultsEntity = CreationDefaultsEntity(key(scope),
        when(scope) { DefaultsScope.Global -> null;is DefaultsScope.Project -> scope.projectId;is DefaultsScope.Layer -> scope.projectId },
        (scope as? DefaultsScope.Layer)?.nodeId,value(defaults.purpose)?.name,value(defaults.obligation),value(defaults.currency),value(defaults.priority)?.name,
        defaults.tags is DefaultValue.Own,defaults.people is DefaultValue.Own,
        value(defaults.start)?.kind?.name,value(defaults.start)?.number,minute(defaults.startTime),
        value(defaults.due)?.kind?.name,value(defaults.due)?.number,minute(defaults.dueTime))
}
data class CreationDefaultsConfiguration(val own:CreationDefaults,val inherited:EffectiveCreationDefaults) {
    val effective get()=own.applyTo(inherited)
}
class CreationDefaultsRepository(private val database:Arachn0deDatabase) {
    suspend fun save(scope:DefaultsScope,defaults:CreationDefaults) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val row=CreationDefaultsCodec.encode(scope,defaults)
            val tags=(defaults.tags as? DefaultValue.Own)?.value.orEmpty()
            val people=(defaults.people as? DefaultValue.Own)?.value.orEmpty()
            CreationDefaultsCodec.decode(row,tags,people)
            val dao=database.creationDefaultsDao();dao.save(row);dao.clearTags(row.id);dao.clearPeople(row.id)
            dao.insertTags(tags.sorted().map { CreationDefaultsTagEntity(row.id,it) })
            dao.insertPeople(people.sorted().map { CreationDefaultsPersonEntity(row.id,it) })
        }
    }
    suspend fun reset(scope:DefaultsScope) = withContext(Dispatchers.IO) { database.creationDefaultsDao().delete(CreationDefaultsCodec.key(scope)) }
    suspend fun configuration(scope:DefaultsScope):CreationDefaultsConfiguration = withContext(Dispatchers.Default) {
        database.withTransaction {
            val projectId=when(scope) { DefaultsScope.Global -> "";is DefaultsScope.Project -> scope.projectId;is DefaultsScope.Layer -> scope.projectId }
            if(scope!=DefaultsScope.Global) require(database.projectDao().getById(projectId)!=null) { "El proyecto ya no existe." }
            val dao=database.creationDefaultsDao()
            val rows=dao.forProject(projectId)
            val tags=dao.tagsForProject(projectId).groupBy { it.defaultsId };val people=dao.peopleForProject(projectId).groupBy { it.defaultsId }
            val configs=rows.associate { row -> row.id to CreationDefaultsCodec.decode(row,tags[row.id].orEmpty().mapTo(hashSetOf()) { it.tagId },people[row.id].orEmpty().mapTo(hashSetOf()) { it.personId }) }
            val empty=CreationDefaults();val own=configs[CreationDefaultsCodec.key(scope)] ?: empty
            val global=configs["G"] ?: empty
            val inherited=when(scope) {
                DefaultsScope.Global -> EffectiveCreationDefaults()
                is DefaultsScope.Project -> global.applyTo(EffectiveCreationDefaults())
                is DefaultsScope.Layer -> {
                    val entities=database.nodeDao().getProjectNodes(projectId);val parents=entities.mapNotNullTo(hashSetOf()) { it.parentId }
                    val nodes=entities.associate { it.id to it.toNode(it.id in parents) }
                    val current=requireNotNull(nodes[scope.nodeId]) { "La capa ya no existe." }
                    CreationDefaultsResolver.resolve(projectId,current.parentId,nodes,global,configs["P:$projectId"] ?: empty,
                        rows.filter { it.nodeId!=null }.associate { it.nodeId!! to configs.getValue(it.id) })
                }
            }
            CreationDefaultsConfiguration(own,inherited)
        }
    }
    suspend fun resolve(projectId:String,parentId:String?):EffectiveCreationDefaults = configuration(
        if(parentId==null) DefaultsScope.Project(projectId) else DefaultsScope.Layer(projectId,parentId)).effective
}
