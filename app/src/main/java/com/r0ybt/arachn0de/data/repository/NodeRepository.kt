package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.NodeTagEntity
import com.r0ybt.arachn0de.data.local.NodePersonEntity
import com.r0ybt.arachn0de.data.local.NodeEventEntity
import com.r0ybt.arachn0de.data.local.toEvent
import com.r0ybt.arachn0de.domain.model.WorkState
import com.r0ybt.arachn0de.domain.model.Priority
import com.r0ybt.arachn0de.domain.model.NodeEventType
import com.r0ybt.arachn0de.data.local.NodeEntity
import com.r0ybt.arachn0de.data.local.toNode
import com.r0ybt.arachn0de.data.local.toProject
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.Obligation
import com.r0ybt.arachn0de.domain.model.PaymentCurrencyPolicy
import com.r0ybt.arachn0de.domain.model.GeneratedNodeSpec
import com.r0ybt.arachn0de.domain.model.NodeBatchGenerator
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.domain.model.NodeProgress
import com.r0ybt.arachn0de.domain.model.NodeTreeSnapshot
import java.util.UUID
import com.r0ybt.arachn0de.domain.model.TitleLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

internal class DraftLocationConflictException(message:String):IllegalArgumentException(message)

class NodeRepository(
    private val database: Arachn0deDatabase,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    internal val sortWriteMutex=kotlinx.coroutines.sync.Mutex()
    internal var beforeDelete:suspend (Set<String>)->Unit = {}
    internal suspend fun <T> withEditorAssignments(nodeId: String, technologyIds: List<String>?, metroCatalog: String?, metroPlan: String?, traveler: String?, metroJourneyId: String? = null, metroRevision: Long? = null, work: suspend () -> T): T = database.withTransaction {
        require((metroCatalog==null)==(metroPlan==null)) {"Completa el itinerario Metro antes de guardar."}
        require(metroJourneyId==null || metroPlan!=null) {"El viaje seleccionado requiere su configuración."}
        val linked=metroJourneyId?.let {id->requireNotNull(database.metroDao().journey(id)) {"El viaje seleccionado ya no existe."}.also {row->
            require(row.revision==metroRevision) {"El viaje cambió. Reabre el formulario para revisar su configuración."}
            require(row.nodeId==null || row.nodeId==nodeId) {"El viaje ya pertenece a otra tarjeta."}
            val attached=database.metroDao().forNode(nodeId)
            require(attached==null || attached.id==row.id) {"La tarjeta ya tiene otro viaje."}
        }}
        val result=work()
        if(technologyIds!=null) {
            require(technologyIds.distinct().size==technologyIds.size && technologyIds.all { database.technologyDao().get(it)!=null })
            database.technologyDao().clearNode(nodeId);database.technologyDao().assignNodes(technologyIds.mapIndexed { index,id->com.r0ybt.arachn0de.data.local.NodeTechnologyEntity(nodeId,id,index) })
        }
        if(metroPlan!=null) {
            val codec=com.r0ybt.arachn0de.metro.MetroCodec
            val source=com.r0ybt.arachn0de.metro.MetroNetwork.decode(requireNotNull(metroCatalog))
            val configuration=codec.journey(metroPlan,source);require(configuration.sessions.isEmpty())
            val prefs=database.metroDao().preferences()?.let { codec.preferences(it.payload) } ?: com.r0ybt.arachn0de.metro.MetroPreferences(requireNotNull(metroCatalog))
            val previous=linked?.let {codec.journey(it.payload,prefs.network)}
            val route=if(previous?.plan==configuration.plan) configuration.plan else requireNotNull(com.r0ybt.arachn0de.metro.MetroPlanner.plan(prefs.planningNetwork,configuration.plan.stops,configuration.plan.express,prefs.restrictions))
            require(linked!=null || database.metroDao().forNode(nodeId)==null) {"Reabre el formulario antes de editar el viaje existente."}
            com.r0ybt.arachn0de.metro.MetroPlanPersistence.save(database,prefs,route,nodeId,traveler,linked?.id,preserveEnabled=linked!=null,updateActiveTraveler=true)
        }
        result
    }
    val creationDefaults = CreationDefaultsRepository(database)
    val tags = TagRepository(database)
    val recurrence = RecurrenceRepository(database, currentTimeMillis)
    private val nodeDao = database.nodeDao()

    /** Derived from existing history; no schema or persisted presentation order. */
    fun observeCompletionTimes(projectId: String): Flow<Map<String, Long>> =
        database.nodeEventDao().observeCompletionTimes(projectId)
            .map { rows -> rows.associate { it.nodeId to it.occurredAt } }
            .distinctUntilChanged().flowOn(Dispatchers.Default)

    fun observeHistory(nodeId: String) = database.nodeEventDao().observe(nodeId).map { rows -> rows.map { it.toEvent() } }
        .flowOn(Dispatchers.Default)

    internal suspend fun completionStillCommitted(nodeId:String,eventId:String):Boolean=database.withTransaction {
        database.nodeEventDao().committedCompletion(eventId,nodeId) && getNode(nodeId)?.isCompleted==true
    }
    internal var onCompletionCandidate:(String,String)->Unit={_,_->}

    private suspend fun appendEvent(nodeId: String, type: NodeEventType, occurredAt: Long) {
        val id = if (type == NodeEventType.CREATED) UUID.nameUUIDFromBytes("node-created:$nodeId".toByteArray(Charsets.UTF_8)).toString()
            else UUID.randomUUID().toString()
        database.nodeEventDao().insert(NodeEventEntity(id, nodeId, type.name, occurredAt))
    }

    /** Reopen a completed ACTION leaf before it becomes a NOTE/container; triggers remain the defence. */
    private suspend fun reopenBeforeConversion(id: String?, at: Long) {
        if (id == null) return
        val current = nodeDao.getById(id) ?: return
        if (current.isCompleted) check(changeCompletion(current, false, at, updatedAt = current.updatedAt))
    }

    fun observeDrafts(): Flow<List<Node>> = nodeDao.observeDrafts().distinctUntilChanged().map {rows->rows.map {it.toNode(false)}}.flowOn(Dispatchers.IO)

    fun observeAllState(): Flow<NodeTreeSnapshot> =
        nodeDao.observeAllNodes().map {it.filter {row->row.projectId!=null}}.distinctUntilChanged().map(::snapshot).flowOn(Dispatchers.Default)

    fun observeProjectState(projectId: String): Flow<NodeTreeSnapshot> =
        nodeDao.observeProjectNodes(projectId).distinctUntilChanged().map(::snapshot).flowOn(Dispatchers.Default)

    /** Repair legacy order before the screen starts observing; normal observers remain read-only. */
    fun observePreparedProjectState(projectId: String): Flow<NodeTreeSnapshot> = flow {
        normalizeProjectOrder(projectId)
        emitAll(observeProjectState(projectId))
    }.flowOn(Dispatchers.Default)

    internal suspend fun reserveAudioCreation(owner:String,provisionalTitle:String?) {
        require(nodeDao.getById(owner)?.purpose=="ACTION")
        database.attachmentDao().beginRecording(com.r0ybt.arachn0de.data.local.AudioRecordingOperationEntity(owner,"WAITING",owner,provisionalTitle))
    }
    suspend fun setPinned(id:String,pinned:Boolean):Boolean = database.withTransaction {nodeDao.setPinned(id,pinned)==1}

    suspend fun normalizeProjectOrder(projectId: String) = database.withTransaction {
        nodeDao.getProjectNodes(projectId).groupBy { it.parentId }.values.forEach { siblings ->
            writeOrder(sortSiblingsForDisplay(siblings))
        }
    }

    /** A stale parent is rejected. At a boundary this is a successful no-op (plus normalization). */
    suspend fun reorderNode(id: String, expectedParentId: String?, moveUp: Boolean, layersOnly:Boolean=false,contentOnly:Boolean=false): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        if (current.parentId != expectedParentId) return@withTransaction false
        val siblings = sortSiblingsForDisplay(nodeDao.getSiblings(current.projectId, current.parentId))
        if(layersOnly || contentOnly || current.isPinned) {
            if(contentOnly && current.purpose=="LAYER" || layersOnly && current.purpose!="LAYER") return@withTransaction false
            val layers=siblings.filter {(it.purpose=="LAYER")== (current.purpose=="LAYER") && it.isPinned==current.isPinned && it.isCompleted==current.isCompleted && it.workState==current.workState}
            val from=layers.indexOfFirst {it.id==id};val to=from+if(moveUp) -1 else 1
            if(to in layers.indices) writeLayerOrder(layers,layers.toMutableList().apply {val other=this[to];this[to]=this[from];this[from]=other})
            return@withTransaction true
        }
        val sameGroup = siblings.filter { !it.isPinned && it.isCompleted == current.isCompleted && it.workState == current.workState }
        val from = sameGroup.indexOfFirst { it.id == id }
        check(from >= 0)
        val to = from + if (moveUp) -1 else 1
        val changedIds = if (to in sameGroup.indices) {
            val other = sameGroup[to]
            val ordered = siblings.toMutableList()
            val fromIndex = ordered.indexOfFirst { it.id == id }
            val toIndex = ordered.indexOfFirst { it.id == other.id }
            ordered[fromIndex] = other
            ordered[toIndex] = current
            if(siblings.any {it.isPinned}) writeLayerOrder(siblings.filterNot {it.isPinned},ordered.filterNot {it.isPinned}) else writeOrder(ordered, setOf(id, other.id))
            setOf(id, other.id)
        } else emptySet()
        if (changedIds.isEmpty()) return@withTransaction true
        true
    }

    /** Drop onto a sibling's slot; reject stale parents or completion groups atomically. */
    suspend fun reorderNodeTo(id: String, expectedParentId: String?, targetId: String, layersOnly:Boolean=false,contentOnly:Boolean=false): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        val target = nodeDao.getById(targetId) ?: return@withTransaction false
        if (current.parentId != expectedParentId || target.parentId != expectedParentId ||
            current.projectId != target.projectId || current.isPinned!=target.isPinned || current.isCompleted != target.isCompleted || current.workState != target.workState) return@withTransaction false
        if(layersOnly || contentOnly || current.isPinned) {
            if((current.purpose=="LAYER")!=(target.purpose=="LAYER") || current.isPinned!=target.isPinned || (contentOnly && current.purpose=="LAYER") || (layersOnly && current.purpose!="LAYER")) return@withTransaction false
            val layers=sortSiblingsForDisplay(nodeDao.getSiblings(current.projectId,expectedParentId)).filter {(it.purpose=="LAYER")== (current.purpose=="LAYER") && it.isPinned==current.isPinned && it.isCompleted==current.isCompleted && it.workState==current.workState}
            val reordered=layers.toMutableList();val from=reordered.indexOfFirst {it.id==id};val to=reordered.indexOfFirst {it.id==targetId}
            reordered.add(to,reordered.removeAt(from));writeLayerOrder(layers,reordered)
            return@withTransaction true
        }
        val before=sortSiblingsForDisplay(nodeDao.getSiblings(current.projectId,expectedParentId))
        val ordered=before.filterNot {it.isPinned}.toMutableList()
        val from = ordered.indexOfFirst { it.id == id }
        val to = ordered.indexOfFirst { it.id == targetId }
        ordered.add(to, ordered.removeAt(from))
        if(before.any {it.isPinned}) writeLayerOrder(before.filterNot {it.isPinned},ordered) else writeOrder(ordered, if (from == to) emptySet() else setOf(id, targetId))
        true
    }

    /** Apply to the visible sibling slots, atomically, through the same position writer as drag. */
    suspend fun applyConfiguredOrder(context:String,configuration:com.r0ybt.arachn0de.domain.model.SortConfiguration,visibleIds:Set<String>?=null)=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {database.withTransaction {
        val projectId=context.substringBeforeLast(':');val parentId=context.substringAfterLast(':').takeUnless {it=="project-root"}
        val project=requireNotNull(database.projectDao().getById(projectId))
        require(parentId==null || nodeDao.getById(parentId)?.let {it.projectId==projectId && it.purpose=="LAYER"}==true)
        val all=nodeDao.getProjectNodes(projectId)
        val parents=all.mapNotNullTo(hashSetOf()) {it.parentId}
        val nodes=all.map {it.toNode(it.id in parents)}.associateBy {it.id}
        val people=database.personDao().assignmentsForNodes(all.map {it.id}).groupBy {it.nodeId}.mapValues {(_,links)->links.mapNotNull {database.personDao().get(it.personId)?.name}}
        val tags=all.associate {it.id to database.tagDao().nodeIds(it.id).mapNotNull {id->database.tagDao().get(id)?.name}}
        val policies=nodes.mapValues {(_,node)->com.r0ybt.arachn0de.domain.model.TaskStatePolicy.count(node,nodes,project.toProject())}
        val favorites=com.r0ybt.arachn0de.access.AccessRepository(database).snapshot().favorites
        val dao=database.nodeSortPreferenceDao()
        val choices=dao.all().associate {it.context to (it.mode to it.layersFirst)}.toMutableMap()
        choices[context]=com.r0ybt.arachn0de.domain.model.SortConfigurationCodec.encode(configuration) to configuration.appliedLayersFirst
        val visible=all.filter {if(visibleIds==null)it.parentId==parentId else it.id in visibleIds}
        for((parent,rows) in visible.groupBy {it.parentId}) {
            val key="$projectId:${parent ?: "project-root"}"
            val effective=com.r0ybt.arachn0de.domain.model.SortConfigurationCodec.effective(choices,key)
            val comparator=com.r0ybt.arachn0de.domain.model.HierarchicalSort.comparator(effective,people,tags,policies,favorites)
            val before=rows.sortedWith(compareBy<NodeEntity> {it.position}.thenBy {it.createdAt}.thenBy {it.id})
            val after=before.sortedWith(Comparator {a,b->comparator.compare(nodes.getValue(a.id),nodes.getValue(b.id))})
            writeLayerOrder(before,after)
            val updated=effective.copy(appliedLayersFirst=effective.layersFirst)
            dao.save(com.r0ybt.arachn0de.data.local.NodeSortPreferenceEntity(key,com.r0ybt.arachn0de.domain.model.SortConfigurationCodec.encode(updated),updated.appliedLayersFirst))
        }
        // Empty views still retain the configuration, without manufacturing positions.
        if(visible.none {it.parentId==parentId}) {
            val updated=configuration.copy(appliedLayersFirst=configuration.layersFirst)
            dao.save(com.r0ybt.arachn0de.data.local.NodeSortPreferenceEntity(context,com.r0ybt.arachn0de.domain.model.SortConfigurationCodec.encode(updated),updated.appliedLayersFirst))
        }
    }}

    /** Only layer slots change; automatic task order and persisted task positions stay intact. */
    private suspend fun writeLayerOrder(before:List<NodeEntity>,after:List<NodeEntity>) {
        val slots=before.map {it.position}.let {if(it.distinct().size==it.size) it else before.indices.toList()}
        after.forEachIndexed {index,node->if(node.position!=slots[index]) check(nodeDao.updateOrder(node.id,slots[index],currentTimeMillis())==1)}
    }

    private suspend fun writeOrder(siblings: List<NodeEntity>, changedIds: Set<String> = emptySet()) {
        val now = if (changedIds.isEmpty()) null else currentTimeMillis()
        siblings.forEachIndexed { position, node ->
            if (node.position != position || node.id in changedIds) {
                check(nodeDao.updateOrder(node.id, position, if (node.id in changedIds) checkNotNull(now) else node.updatedAt) == 1)
            }
        }
    }

    private fun sortSiblingsForDisplay(siblings: List<NodeEntity>): List<NodeEntity> =
        siblings.sortedWith(
            compareBy<NodeEntity> { if (it.isCompleted) 1 else 0 }
                .thenBy { it.position }
                .thenBy { it.createdAt }
                .thenBy { it.id },
        )

    fun observeRootNodes(projectId: String): Flow<List<Node>> =
        observeProjectState(projectId).map { it.childrenOf(null) }

    fun observeChildren(projectId: String, parentId: String): Flow<List<Node>> =
        observeProjectState(projectId).map { it.childrenOf(parentId) }

    suspend fun getNode(id: String): Node? = database.withTransaction {
        nodeDao.getById(id)?.let { it.toNode(nodeDao.hasChildren(it.projectId, id)) }
    }

    suspend fun createNode(
        projectId: String?,
        parentId: String?,
        title: String,
        description: String = "",
        creationId: String = UUID.randomUUID().toString(),
        startAt: Long? = null,
        dueAt: Long? = null,
        purpose: NodePurpose = NodePurpose.ACTION,
        obligation: Obligation? = null,
        responsibleIds: Set<String> = emptySet(),
        tagIds: Set<String> = emptySet(),
        priority: Priority = Priority.NONE,
    ): Node = database.withTransaction {
        com.r0ybt.arachn0de.domain.model.TaskTemporal.validateDates(startAt, dueAt)
        require(purpose == NodePurpose.ACTION || obligation == null) { "Una nota no puede tener un pago." }
        require(purpose == NodePurpose.ACTION || priority == Priority.NONE) { "Una nota no admite prioridad." }
        val normalizedTitle = validateTitle(title)
        require(creationId.isNotBlank())
        nodeDao.getById(creationId)?.let { existing ->
            check(existing.projectId == projectId && existing.parentId == parentId &&
                existing.title == normalizedTitle && existing.description == description &&
                existing.startAt == startAt && existing.dueAt == dueAt && existing.purpose == purpose.name &&
                existing.amountMinor == obligation?.amountMinor && existing.currencyCode == obligation?.currencyCode && existing.priority == priority.name &&
                database.personDao().assignmentIds(existing.id).toSet() == responsibleIds &&
                database.tagDao().nodeIds(existing.id).toSet() == tagIds) {
                "Creation already committed with different content or destination"
            }
            return@withTransaction existing.toNode(nodeDao.hasChildren(projectId, creationId))
        }
        PaymentCurrencyPolicy.requireNew(obligation)
        projectId?.let { require(database.projectDao().getById(it) != null) { "Project not found" } }
        require(projectId != null || (parentId == null && purpose != NodePurpose.LAYER)) { "Borradores admite notas y tareas sin jerarquía." }
        validateParent(projectId, parentId)
        responsibleIds.forEach { requireNotNull(database.personDao().get(it)) { "Person not found" } }
        tags.validate(tagIds)
        val now = currentTimeMillis()
        val entity = NodeEntity(
            id = creationId, projectId = projectId, parentId = parentId,
            title = normalizedTitle, description = description, isCompleted = false,
            position = nextPosition(projectId, parentId), createdAt = now, updatedAt = now,
            startAt = startAt, dueAt = dueAt, purpose = purpose.name,
            amountMinor = obligation?.amountMinor, currencyCode = obligation?.currencyCode, priority = priority.name,
            workState = initialWorkState(parentId, purpose),
        )
        reopenBeforeConversion(parentId, now)
        nodeDao.insert(entity)
        ensureNodeReferences(database, entity.id, entity.description)
        appendEvent(entity.id, NodeEventType.CREATED, now)
        if (responsibleIds.isNotEmpty()) database.personDao().assign(responsibleIds.map { NodePersonEntity(entity.id, it) })
        tags.assignNode(entity.id, tagIds)
        entity.toNode(hasChildren = false)
    }

    /** The preview's exact specs are committed with assignments in one transaction. Stable IDs make retries safe. */
    suspend fun createBatch(
        projectId: String,
        parentId: String?,
        batchId: String,
        specifications: List<GeneratedNodeSpec>,
        responsibleIds: Set<String> = emptySet(),
        tagIds: Set<String> = emptySet(),
    ): List<Node> {
        // Snapshot caller-owned collections before suspending.
        val specs = specifications.toList()
        val people = responsibleIds.toSet()
        val labels = tagIds.toSet()
        NodeBatchGenerator.validateSpecs(specs)
        require(batchId.isNotBlank() && batchId.length <= 200)
        val ids = specs.indices.map { UUID.nameUUIDFromBytes("$batchId:$it".toByteArray(Charsets.UTF_8)).toString() }
        return database.withTransaction {
            val existing = ids.map { nodeDao.getById(it) }
            if (existing.any { it != null }) {
                check(nodeDao.getById(UUID.nameUUIDFromBytes("$batchId:${specs.size}".toByteArray(Charsets.UTF_8)).toString()) == null) { "El lote ya fue creado con otra cantidad." }
                check(existing.all { it != null }) { "El lote ya existe parcialmente; no se puede repetir." }
                existing.mapIndexed { index, row ->
                    val node = checkNotNull(row)
                    val spec = specs[index]
                    check(node.projectId == projectId && node.parentId == parentId && node.title == spec.title &&
                        node.description == spec.description && node.purpose == spec.purpose.name &&
                        node.startAt == null && node.dueAt == spec.dueAt &&
                        node.amountMinor == spec.obligation?.amountMinor && node.currencyCode == spec.obligation?.currencyCode && node.priority == spec.priority.name && node.creationGroupId == batchId &&
                        database.personDao().assignmentIds(node.id).toSet() == people && database.tagDao().nodeIds(node.id).toSet() == labels) {
                        "El lote ya fue creado con otros datos."
                    }
                    node.toNode(nodeDao.hasChildren(projectId, node.id))
                }
            } else {
                specs.forEach { PaymentCurrencyPolicy.requireNew(it.obligation) }
                require(database.projectDao().getById(projectId) != null) { "Project not found" }
                validateParent(projectId, parentId)
                people.forEach { requireNotNull(database.personDao().get(it)) { "Person not found" } }
                tags.validate(labels)
                var maximum = nodeDao.maxPosition(projectId, parentId) ?: -1
                if (maximum.toLong() + specs.size > Int.MAX_VALUE) {
                    writeOrder(sortSiblingsForDisplay(nodeDao.getSiblings(projectId, parentId)))
                    maximum = nodeDao.maxPosition(projectId, parentId) ?: -1
                }
                check(maximum.toLong() + specs.size <= Int.MAX_VALUE) { "Sibling position exhausted" }
                val now = currentTimeMillis()
                specs.mapIndexed { index, spec ->
                    val entity = NodeEntity(ids[index], projectId, parentId, spec.title, spec.description,
                        false, maximum + 1 + index, now, now, startAt = null, dueAt = spec.dueAt, purpose = spec.purpose.name,
                        amountMinor = spec.obligation?.amountMinor, currencyCode = spec.obligation?.currencyCode, priority = spec.priority.name, creationGroupId = batchId, workState = initialWorkState(parentId, spec.purpose))
                    reopenBeforeConversion(parentId, now)
                    nodeDao.insert(entity)
                    ensureNodeReferences(database, entity.id, entity.description)
                    appendEvent(entity.id, NodeEventType.CREATED, now)
                    if (people.isNotEmpty()) database.personDao().assign(people.map {
                        NodePersonEntity(entity.id, it)
                    })
                    tags.assignNode(entity.id, labels)
                    entity.toNode(hasChildren = false)
                }
            }
        }
    }

    /** Content edits never change structure, completion, identity or sibling order. */
    suspend fun updateNode(id: String, title: String, description: String = ""): Boolean =
        database.withTransaction {
            if (nodeDao.getById(id) == null) return@withTransaction false
            ensureNodeReferences(database, id, description)
            nodeDao.updateContent(id, validateTitle(title), description, currentTimeMillis()) == 1
        }

    /** Apply only the selected property to current rows, retaining the ordinary group rules. */
    suspend fun updateProperty(id:String,field:com.r0ybt.arachn0de.domain.model.NodeProperty,original:List<String>,
        value:List<String>,groupIds:Set<String>?=null,removeObligation:Boolean=false):Boolean = database.withTransaction {
        val source=getNode(id) ?: return@withTransaction false
        val current=com.r0ybt.arachn0de.domain.model.nodePropertyValues(field,source,
            database.tagDao().nodeIds(id),database.personDao().assignmentIds(id),
            database.technologyDao().forNodes(listOf(id)).sortedBy {it.position}.map {it.technologyId})
        // A foreground recording may append audio while the description editor is open.
        // Only those new audio references may differ; concurrent text edits still conflict.
        val recordingOnlyChange=field==com.r0ybt.arachn0de.domain.model.NodeProperty.DESCRIPTION && run {
            val before=original.single()
            val now=current.single()
            val added=com.r0ybt.arachn0de.domain.model.AttachmentReferences.matches(now)
                .filter {com.r0ybt.arachn0de.domain.model.AttachmentReferences.isAudio(it) &&
                    it.groupValues[1] !in com.r0ybt.arachn0de.domain.model.AttachmentReferences.ids(before)}.toList()
            var withoutAdded=now
            added.forEach {withoutAdded=com.r0ybt.arachn0de.domain.model.AttachmentReferences.remove(withoutAdded,it.groupValues[1])}
            added.isNotEmpty() && withoutAdded.trim()==before.trim()
        }
        check(current==original || recordingOnlyChange) {"La propiedad cambió durante la edición. Tu borrador se conserva."}
        val shared=field in setOf(com.r0ybt.arachn0de.domain.model.NodeProperty.DESCRIPTION,com.r0ybt.arachn0de.domain.model.NodeProperty.PAYMENT,
            com.r0ybt.arachn0de.domain.model.NodeProperty.PRIORITY,com.r0ybt.arachn0de.domain.model.NodeProperty.PEOPLE,com.r0ybt.arachn0de.domain.model.NodeProperty.TAGS)
        val targets=if(groupIds!=null) {
            require(shared)
            val group=requireNotNull(source.creationGroupId)
            val members=nodeDao.groupMembers(group)
            check(members.map {it.id}.toSet()==groupIds) {"El grupo cambió. Vuelve a revisar sus miembros."}
            require(members.all {it.projectId==source.projectId})
            groupIds
        } else setOf(id)
        targets.forEach {target->
            val latest=requireNotNull(getNode(target))
            check(when(field) {
                com.r0ybt.arachn0de.domain.model.NodeProperty.TITLE->updateNode(target,value.single().trim(),latest.description)
                com.r0ybt.arachn0de.domain.model.NodeProperty.DESCRIPTION->updateNode(target,latest.title,value.single().trim())
                com.r0ybt.arachn0de.domain.model.NodeProperty.DATES->updateNodeWithDates(target,latest.title,latest.description,value[0].toLongOrNull(),value[1].toLongOrNull())
                com.r0ybt.arachn0de.domain.model.NodeProperty.PRIORITY->setPriority(target,Priority.valueOf(value.single()))
                com.r0ybt.arachn0de.domain.model.NodeProperty.PAYMENT->{
                    val money=value[0].toLongOrNull()?.let {Obligation(it,value[1])}
                    updateLeaf(target,latest.title,latest.description,latest.startAt,latest.dueAt,money,removeObligation)
                }
                com.r0ybt.arachn0de.domain.model.NodeProperty.TAGS->{tags.assignNode(target,value.toSet());true}
                com.r0ybt.arachn0de.domain.model.NodeProperty.PEOPLE->{
                    value.forEach {requireNotNull(database.personDao().get(it))}
                    database.personDao().clearAssignments(target)
                    if(value.isNotEmpty()) database.personDao().assign(value.distinct().map {NodePersonEntity(target,it)})
                    true
                }
                com.r0ybt.arachn0de.domain.model.NodeProperty.TECHNOLOGIES->withEditorAssignments(target,value,null,null,null) {true}
            }) {"El elemento ya no admite esa propiedad."}
        }
        true
    }

    /** Dates are operational only for leaves; content-only edits preserve dormant layer dates. */
    suspend fun updateNodeWithDates(id: String, title: String, description: String, startAt: Long?, dueAt: Long?): Boolean =
        database.withTransaction {
            com.r0ybt.arachn0de.domain.model.TaskTemporal.validateDates(startAt, dueAt)
            val current = nodeDao.getById(id) ?: return@withTransaction false
            if (current.purpose != NodePurpose.ACTION.name || nodeDao.hasChildren(current.projectId, id)) return@withTransaction false
            ensureNodeReferences(database, id, description)
            nodeDao.updateContentAndDates(id, validateTitle(title), description, startAt, dueAt, currentTimeMillis()) == 1
        }

    /** Financial/content/date edits are atomic and preserve completion, identity and assignments. */
    suspend fun updateLeaf(id: String, title: String, description: String, startAt: Long?, dueAt: Long?, obligation: Obligation?, removeObligation: Boolean = false): Boolean = database.withTransaction {
        com.r0ybt.arachn0de.domain.model.TaskTemporal.validateDates(startAt, dueAt)
        val current = nodeDao.getById(id) ?: return@withTransaction false
        if (current.purpose != NodePurpose.ACTION.name || nodeDao.hasChildren(current.projectId, id)) return@withTransaction false
        PaymentCurrencyPolicy.requireEdit(current.amountMinor?.let { Obligation(it, requireNotNull(current.currencyCode)) }, obligation)
        require(current.amountMinor == null || obligation != null || removeObligation) { "Confirma la eliminación de los datos financieros." }
        ensureNodeReferences(database, id, description)
        nodeDao.updateLeaf(id, validateTitle(title), description, startAt, dueAt, obligation?.amountMinor, obligation?.currencyCode, currentTimeMillis()) == 1
    }

    suspend fun updateEditor(id: String, title: String, description: String, startAt: Long?, dueAt: Long?,
        obligation: Obligation?, removeObligation: Boolean, editDates: Boolean, tagIds: Set<String>, priority: Priority? = null, responsibleIds: Set<String>? = null): Boolean = database.withTransaction {
        tags.validate(tagIds)
        val updated = if (editDates) updateLeaf(id, title, description, startAt, dueAt, obligation, removeObligation)
            else updateNode(id, title, description)
        if (updated) {
            priority?.let { check(setPriority(id, it)) { "Este elemento ya no admite prioridad." } }
            tags.assignNode(id, tagIds)
            responsibleIds?.let { ids ->
                ids.forEach { requireNotNull(database.personDao().get(it)) { "Person not found" } }
                database.personDao().clearAssignments(id)
                if (ids.isNotEmpty()) database.personDao().assign(ids.map { NodePersonEntity(id, it) })
            }
        }
        updated
    }

    suspend fun setEffort(id:String,effort:Int):Boolean = database.withTransaction {
        require(effort in 0..5) {"Esfuerzo inválido."}
        val node=getNode(id) ?: return@withTransaction false
        if(!node.isCompletable)return@withTransaction false
        if(node.effort==effort)return@withTransaction true
        nodeDao.setEffort(id,effort,currentTimeMillis())==1
    }

    suspend fun togglePriority(id:String):Boolean = database.withTransaction {
        val node=getNode(id) ?: return@withTransaction false
        setPriority(id,if(node.priority==Priority.HIGH) Priority.NONE else Priority.HIGH)
    }

    suspend fun setPriority(id: String, priority: Priority): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        if (current.purpose != NodePurpose.ACTION.name || nodeDao.hasChildren(current.projectId, id)) return@withTransaction false
        if (current.priority == priority.name) return@withTransaction true
        nodeDao.updatePriority(id, priority.name, currentTimeMillis()) == 1
    }

    /** Conversion retains identity, content, dates, position and assignments; completion is cleared. */
    suspend fun convertPurpose(id: String, purpose: NodePurpose, removeObligation: Boolean = false): Boolean {
        beforeDelete(setOf(id))
        return database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        if (nodeDao.hasChildren(current.projectId, id)) return@withTransaction false
        if (current.purpose == purpose.name) return@withTransaction true
        require(!current.awaitingVerification) {"Resuelve Por verificar antes de cambiar el tipo de la tarea."}
        requireSprintLink(database.sprintLinkDao().task(id)==null) {"Desvincula la tarea del Sprint antes de cambiar su tipo."}
        require(purpose == NodePurpose.LAYER || database.conversionDao().node(id) == null) { "Esta capa conserva propiedades de un proyecto. Conviértela en proyecto antes de cambiar su tipo." }
        require(current.amountMinor == null || removeObligation) { "Confirma la eliminación de los datos financieros." }
        PaymentCurrencyPolicy.requireEdit(current.amountMinor?.let { Obligation(it, requireNotNull(current.currencyCode)) }, null)
        management.backlog.validateStructure(database.backupDao().nodes().map {if(it.id==id) it.copy(purpose=purpose.name) else it})
        val at = currentTimeMillis()
        reopenBeforeConversion(id, at)
        check(nodeDao.updatePurpose(id, purpose.name, at, initialWorkState(current.parentId, purpose)) == 1) { "Purpose change was not written" }
        true
    }
    }

    /** A null parent explicitly moves the node to the project root. */
    suspend fun moveNode(id: String, parentId: String?): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        validateParent(current.projectId, parentId, movingId = id)
        if (current.parentId == parentId) return@withTransaction true
        management.backlog.validateStructure(database.backupDao().nodes().map {if(it.id==id) it.copy(parentId=parentId) else it})
        val at = currentTimeMillis()
        reopenBeforeConversion(parentId, at)
        check(nodeDao.move(id, parentId, nextPosition(current.projectId, parentId), at, movedWorkState(current, parentId)) == 1) { "Move was not written" }
        true
    }

    /** Atomic in-place relocation: node identities, attachments and history are never deleted/recreated. */
    suspend fun moveToContainer(selected:Set<String>,projectId:String,parentId:String?):Boolean = database.withTransaction {
        require(selected.isNotEmpty())
        requireNotNull(database.projectDao().getById(projectId)) {"El Núcleo ya no existe."}
        require(database.bankDao().roots().none {it.id==projectId}) {"Banco solo admite cuentas creadas desde Banco."}
        require(parentId==null || nodeDao.getById(parentId)?.let {it.projectId==projectId && it.purpose=="LAYER"}==true) {"Selecciona un Núcleo o Capa."}
        val all=database.backupDao().nodes();val byId=all.associateBy {it.id}
        selected.forEach {requireNotNull(byId[it]) {"El elemento ya no existe."}}
        val roots=selected.filter {id->
            var p=byId.getValue(id).parentId;val seen=hashSetOf<String>();var selectedAncestor=false
            while(p!=null && seen.add(p)) {if(p in selected)selectedAncestor=true;p=byId[p]?.parentId}
            !selectedAncestor
        }
        val ids=roots.flatMap {nodeDao.getSubtreeIds(it)}.toSet()
        require(parentId !in ids) {"No puedes mover un elemento dentro de sí mismo o de sus descendientes."}
        require(database.bankDao().accounts().none {it.id in ids}) {"Las cuentas deben permanecer en su Núcleo Banco."}
        val moving=all.filter {it.id in ids}
        if(moving.all {it.projectId==projectId}) {
            // Keep the established same-core Sprint/state rules, but never auto-convert a task destination.
            roots.forEach {check(moveNode(it,parentId))}
            for(n in moving.filter {it.awaitingVerification}) require(taskStateCount(n.id)==3) {"Habilita 3 estados en el destino para conservar Por verificar."}
            return@withTransaction true
        }
        require(moving.none {it.workState!=null && it.id in roots}) {"Traslada el Sprint completo para conservar sus estados."}
        val changing=moving.filter {it.projectId!=projectId}.mapTo(hashSetOf()) {it.id}
        val groups=moving.mapNotNull {it.creationGroupId}.toSet()
        require(all.none {it.id !in ids && it.creationGroupId in groups}) {"Traslada el grupo completo para conservar sus relaciones."}
        val rules=database.recurrenceDao().rules().filter {it.parentId in ids}
        val ruleIds=rules.mapTo(hashSetOf()) {it.id}
        require(database.recurrenceDao().occurrences().none {o->
            (o.nodeId in changing && o.ruleId !in ruleIds) || (o.ruleId in ruleIds && byId[o.nodeId]?.let {it.id !in ids}==true)
        }) {"La recurrencia tiene elementos fuera de esta Capa. Traslada el contenedor completo."}
        val defaults=database.creationDefaultsDao().all().filter {it.nodeId in ids}
        // Project-scoped planning/Sprint links cannot silently cross contexts. Identity-only metadata is retained.
        val allowed=setOf("nodes","node_attachments","node_person","node_tag","node_events","node_technologies","project_photos","creation_defaults","bank_allocations")
        val sql=database.openHelper.writableDatabase;val tables=mutableListOf<String>()
        sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use {c->while(c.moveToNext())tables+=c.getString(0)}
        for(table in tables.filter {it !in allowed}) {
            val columns=mutableListOf<String>()
            sql.query("PRAGMA foreign_key_list(`$table`)").use {c->while(c.moveToNext())if(c.getString(2)=="nodes")columns+=c.getString(3)}
            for(column in columns) for(id in changing) sql.query("SELECT 1 FROM `$table` WHERE `$column`=? LIMIT 1",arrayOf(id)).use {c->
                require(!c.moveToFirst()) {"Hay relaciones de planificación o contexto incompatibles con otro Núcleo. El contenido se conserva."}
            }
        }
        val proposed=all.map {n->if(n.id in ids)n.copy(projectId=projectId,parentId=if(n.id in roots)parentId else n.parentId) else n}
        management.backlog.validateStructure(proposed)
        validateParent(projectId,parentId)
        // Composite parent/default FKs stay enforced at commit while both ends change together.
        sql.execSQL("PRAGMA defer_foreign_keys=ON")
        val now=currentTimeMillis()
        for(rootId in roots) {
            val n=byId.getValue(rootId)
            check(nodeDao.relocate(n.id,projectId,parentId,nextPosition(projectId,parentId),movedWorkState(n,parentId),now)==1)
        }
        val children=moving.groupBy {it.parentId}
        val queue=java.util.ArrayDeque<String>();roots.forEach {queue.add(it)}
        while(queue.isNotEmpty()) {
            val parent=queue.removeFirst()
            for(n in children[parent].orEmpty()) {
                check(nodeDao.relocate(n.id,projectId,n.parentId,n.position,n.workState,now)==1)
                queue.add(n.id)
            }
        }
        for(n in moving.filter {it.awaitingVerification}) require(taskStateCount(n.id)==3) {"Habilita 3 estados en el destino para conservar Por verificar."}
        defaults.forEach {database.creationDefaultsDao().save(it.copy(projectId=projectId))}
        rules.forEach {database.recurrenceDao().update(it.copy(projectId=projectId))}
        val sorting=database.nodeSortPreferenceDao();val preferences=sorting.all()
        for(n in moving) {
            val old=n.projectId?.let {"$it:${n.id}"} ?: "draft:${n.id}";val new="$projectId:${n.id}"
            if(old!=new) preferences.firstOrNull {it.context==old}?.let {row->
                require(preferences.none {it.context==new}) {"El destino tiene una configuración de orden incompatible."}
                sorting.delete(old);sorting.save(row.copy(context=new))
            }
        }
        sql.query("PRAGMA foreign_key_check").use {require(!it.moveToFirst()) {"El traslado no conserva todas las referencias."}}
        true
    }

    /** Relocates only independent leaf content. All identity-based links and bytes stay in place. */
    suspend fun relocateNode(id:String, projectId:String?, parentId:String? = null):Boolean = try { database.withTransaction {
        val current=nodeDao.getById(id) ?: return@withTransaction false
        require(projectId!=null || parentId==null) { "Borradores no admite capas internas." }
        if(current.projectId==projectId && current.parentId==parentId) return@withTransaction true
        require(current.purpose!="LAYER" && current.creationGroupId==null && !nodeDao.hasChildren(current.projectId,id)) {
            "Solo pueden trasladarse notas y tareas independientes, sin grupo ni hijos."
        }
        require(current.workState==null && database.sprintLinkDao().task(id)==null) {
            "Retira primero la tarea de su Sprint para conservar sus relaciones."
        }
        if(current.awaitingVerification) {
            require(projectId!=null) {"Por verificar requiere un Núcleo real."}
            var destinationCount=current.taskStateCount
            var ancestor=parentId?.let {nodeDao.getById(it)}
            while(destinationCount==null && ancestor!=null) {destinationCount=ancestor.taskStateCount;ancestor=ancestor.parentId?.let {nodeDao.getById(it)}}
            require((destinationCount ?: if(database.projectDao().getById(projectId)?.verificationEnabled==true) 3 else 2)==3) {"Habilita 3 estados en el destino antes de trasladar la tarea por verificar."}
        }
        if(projectId!=null) requireNotNull(database.projectDao().getById(projectId)) { "El proyecto de destino ya no existe." }
        if(parentId!=null) require(nodeDao.getById(parentId)?.purpose=="LAYER") { "Selecciona un proyecto o una capa existente." }
        validateParent(projectId,parentId,movingId=id)
        // Relations owned by a project or management context cannot be silently reassigned.
        // Keep ordinary node metadata, attachment and history references untouched.
        if(current.projectId!=projectId) {
            require(database.recurrenceDao().occurrences().none {it.nodeId==id}) { "Las ocurrencias recurrentes conservan su proyecto e historial. No pueden trasladarse a Borradores." }
            val allowed=setOf("nodes","node_attachments","node_person","node_tag","node_events","node_technologies")
            val sql=database.openHelper.writableDatabase
            val tables=mutableListOf<String>()
            sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use {c->while(c.moveToNext()) tables+=c.getString(0)}
            for(table in tables.filter {it !in allowed}) {
                val columns=mutableListOf<String>()
                sql.query("PRAGMA foreign_key_list(`$table`)").use {c->while(c.moveToNext()) if(c.getString(2)=="nodes") columns+=c.getString(3)}
                for(column in columns) sql.query("SELECT 1 FROM `$table` WHERE `$column`=? LIMIT 1",arrayOf(id)).use {
                    require(!it.moveToFirst()) { "El elemento tiene relaciones de planificación, recurrencia o contexto. Retíralas antes de cambiar su ubicación." }
                }
            }
        }
        management.backlog.validateStructure(database.backupDao().nodes().map {if(it.id==id) it.copy(projectId=projectId,parentId=parentId) else it})
        val oldSortContext=current.projectId?.let {"$it:$id"} ?: "draft:$id"
        val newSortContext=projectId?.let {"$it:$id"} ?: "draft:$id"
        if(oldSortContext!=newSortContext) {
            val sorting=database.nodeSortPreferenceDao()
            sorting.all().firstOrNull {it.context==oldSortContext}?.let {row->
                require(sorting.all().none {it.context==newSortContext}) {"El destino contiene una preferencia de orden incompatible."}
                sorting.delete(oldSortContext);sorting.save(row.copy(context=newSortContext))
            }
        }
        check(nodeDao.relocate(id,projectId,parentId,nextPosition(projectId,parentId),initialWorkState(parentId,NodePurpose.valueOf(current.purpose)),currentTimeMillis())==1)
        true
    }} catch(e:IllegalArgumentException) {throw DraftLocationConflictException(e.message ?: "La ubicación es incompatible. El contenido se conserva.")}

    internal val management=ManagementRepository(database)
    internal val sprintLinks=SprintLinkRepository(database)

    private suspend fun initialWorkState(parentId: String?, purpose: NodePurpose): String? =
        if (purpose == NodePurpose.ACTION && parentId?.let { nodeDao.getById(it)?.sprintMode } == true) WorkState.UNPLANNED.name else null

    private suspend fun movedWorkState(current: NodeEntity, parentId: String?): String? {
        val link=database.sprintLinkDao().task(current.id)
        val destination=parentId?.let {nodeDao.getById(it)}
        if(link!=null && destination?.sprintMode==true) {
            requireSprintLink(destination.id==link.sprintId) {"La tarea pertenece a otro Sprint. Desvincúlala antes de moverla."}
            database.sprintLinkDao().delete(current.id) // Becomes direct; phase and completion stay intact.
        }
        return if(current.purpose=="ACTION" && (destination?.sprintMode==true || link!=null))
            current.workState ?: WorkState.fromCompletion(current.isCompleted).name else null
    }

    suspend fun setSprintMode(id: String, enabled: Boolean): Boolean = database.withTransaction {
        val layer = nodeDao.getById(id) ?: return@withTransaction false
        if (layer.purpose != "LAYER") return@withTransaction false
        require(database.managementLayerDao().all().none {it.nodeId==id}) {"Esta capa tiene una función de gestión. Crea un Sprint dentro de Sprints."}
        if (layer.sprintMode == enabled) return@withTransaction true
        if(!enabled) management.iterations.rejectDisable(id)
        requireSprintLink(enabled || database.sprintLinkDao().all().none {it.sprintId==id}) {"Desvincula las tareas externas antes de desactivar el Sprint; confirma primero sus fases."}
        if(enabled) requireSprintLink(nodeDao.getSiblings(layer.projectId,id).none {database.sprintLinkDao().task(it.id)!=null}) {"Hay tareas vinculadas a otro Sprint. Desvincúlalas primero."}
        val at = currentTimeMillis()
        nodeDao.getSiblings(layer.projectId, id).filter { it.purpose == "ACTION" }.forEach { child ->
            require(!child.awaitingVerification) {"Resuelve las tareas por verificar antes de activar Modo Sprint."}
            val state = if (enabled) WorkState.fromCompletion(child.isCompleted).name else null
            check(nodeDao.setWorkState(child.id, state, child.isCompleted, at) == 1)
        }
        check(nodeDao.setSprintMode(id, enabled, at) == 1)
        true
    }

    suspend fun setWorkState(id: String, state: WorkState): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        changeWorkState(current, state)
    }

    suspend fun advanceWorkState(id: String): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        val state = current.workState?.let(WorkState::valueOf) ?: return@withTransaction false
        changeWorkState(current, state.next())
    }

    /** Confirm the current Sprint phase and apply its completion boundary atomically. */
    suspend fun confirmWorkState(id: String): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        val state = current.workState?.let(WorkState::valueOf) ?: return@withTransaction false
        val next = state.next()
        if (state == next) return@withTransaction true
        if (!changeWorkState(current, next)) return@withTransaction false
        check(changeCompletion(requireNotNull(nodeDao.getById(id)), next.completed, currentTimeMillis()))
        true
    }

    private suspend fun changeWorkState(current: NodeEntity, state: WorkState): Boolean {
        if (current.purpose != "ACTION" || current.parentId?.let { nodeDao.getById(it)?.sprintMode } != true && database.sprintLinkDao().task(current.id)==null) return false
        if (current.workState == state.name) return true
        val at = currentTimeMillis()
        check(nodeDao.setWorkState(current.id, state.name, current.isCompleted, at) == 1)
        return true
    }

    suspend fun setTaskStateCount(id:String,count:Int?):Boolean=database.withTransaction {
        require(count==null || count==2 || count==3)
        val node=nodeDao.getById(id) ?: return@withTransaction false
        TaskStateConfiguration(database,currentTimeMillis).apply(TaskStateConfiguration.Request(requireNotNull(node.projectId),id,count))
    }
    private suspend fun effectiveTaskStateCount(node:NodeEntity):Int {
        if(node.projectId==null) return 2
        var cursor:NodeEntity?=node;val visited=hashSetOf<String>()
        while(cursor!=null && visited.add(cursor.id)) {
            cursor.taskStateCount?.let {return it}
            cursor=cursor.parentId?.let {nodeDao.getById(it)}
        }
        return if(node.projectId?.let {database.projectDao().getById(it)?.verificationEnabled}==true) 3 else 2
    }
    internal suspend fun taskStateCount(id:String):Int=effectiveTaskStateCount(requireNotNull(nodeDao.getById(id)))
    suspend fun cycleTaskStatus(id:String):Boolean=database.withTransaction {
        val node=nodeDao.getById(id) ?: return@withTransaction false
        val current=if(node.isCompleted) com.r0ybt.arachn0de.domain.model.TaskStatus.COMPLETED else if(node.awaitingVerification) com.r0ybt.arachn0de.domain.model.TaskStatus.VERIFY else com.r0ybt.arachn0de.domain.model.TaskStatus.PENDING
        setTaskStatus(id,com.r0ybt.arachn0de.domain.model.TaskStatePolicy.next(current,effectiveTaskStateCount(node)))
    }

    suspend fun setTaskStatus(id:String,status:com.r0ybt.arachn0de.domain.model.TaskStatus):Boolean = database.withTransaction {
        val current=nodeDao.getById(id) ?: return@withTransaction false
        require(current.purpose=="ACTION" && current.workState==null && !nodeDao.hasChildren(current.projectId,id)) {"Estado disponible solo para tareas normales."}
        if(status!=com.r0ybt.arachn0de.domain.model.TaskStatus.VERIFY) return@withTransaction changeCompletion(current,status==com.r0ybt.arachn0de.domain.model.TaskStatus.COMPLETED,currentTimeMillis())
        if(current.awaitingVerification) return@withTransaction true
        require(current.projectId!=null && effectiveTaskStateCount(current)==3) {"Esta tarea utiliza 2 estados."}
        if(current.isCompleted) check(changeCompletion(current,false,currentTimeMillis()))
        check(nodeDao.markAwaitingVerification(id,currentTimeMillis())==1)
        true
    }

    /** Undo only this exact last state write. Retains append-only history and persisted order. */
    suspend fun restoreTaskStatus(expected:Node,status:com.r0ybt.arachn0de.domain.model.TaskStatus):Boolean=database.withTransaction {
        if(getNode(expected.id)!=expected) return@withTransaction false
        setTaskStatus(expected.id,status)
    }

    suspend fun setCompleted(id: String, completed: Boolean): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        changeCompletion(current, completed, currentTimeMillis())
    }

    suspend fun toggleCompleted(id: String): Boolean = database.withTransaction {
        val current = nodeDao.getById(id) ?: return@withTransaction false
        changeCompletion(current, !current.isCompleted, currentTimeMillis())
    }

    /** Read/validate current persisted state under the same Room transaction as both writes. */
    private suspend fun changeCompletion(current: NodeEntity, completed: Boolean, at: Long, updatedAt: Long = at): Boolean {
        if (current.purpose != NodePurpose.ACTION.name || nodeDao.hasChildren(current.projectId, current.id)) return false
        if (current.isCompleted == completed && !current.awaitingVerification) return true
        val state = current.workState
        if (nodeDao.setWorkState(current.id, state, completed, updatedAt) != 1) return false
        if(completed && !current.isCompleted) {
            val eventId=UUID.randomUUID().toString()
            database.nodeEventDao().insert(NodeEventEntity(eventId,current.id,NodeEventType.COMPLETED.name,at))
            // The session validates this exact event from a separate transaction after commit/rollback.
            // Audio must never turn a successful data operation into a reported failure.
            runCatching {onCompletionCandidate(current.id,eventId)}
        } else appendEvent(current.id, if (completed) NodeEventType.COMPLETED else NodeEventType.REOPENED, at)
        return true
    }

    suspend fun createNodeWithUndo(projectId: String?, parentId: String?, title: String, description: String, creationId: String,
        startAt: Long?, dueAt: Long?, purpose: NodePurpose, obligation: Obligation?, responsibleIds: Set<String>, tagIds: Set<String>, priority: Priority) = database.withTransaction {
        val node = createNode(projectId,parentId,title,description,creationId,startAt,dueAt,purpose,obligation,responsibleIds,tagIds,priority)
        captureCreation(listOf(node.id))
    }
    suspend fun createBatchWithUndo(projectId: String, parentId: String?, batchId: String, specs: List<GeneratedNodeSpec>, responsibleIds: Set<String>, tagIds: Set<String>) = database.withTransaction {
        captureCreation(createBatch(projectId,parentId,batchId,specs,responsibleIds,tagIds).map { it.id })
    }

    suspend fun groupMembers(groupId: String): List<Node> = database.withTransaction {
        val members = nodeDao.groupMembers(groupId)
        val parents = members.map { it.projectId }.distinct().flatMap { nodeDao.getProjectNodes(it) }.mapNotNull { it.parentId }.toSet()
        members.map { it.toNode(it.id in parents) }
    }

    suspend fun captureCreation(ids: List<String>): com.r0ybt.arachn0de.domain.model.CreationUndo = database.withTransaction {
        require(ids.isNotEmpty() && ids.size == ids.toSet().size)
        val rows = ids.chunked(500).flatMap { nodeDao.byIds(it) }.associateBy { it.id }
        require(rows.size == ids.size) { "La creación cambió." }
        val parents = ids.chunked(500).flatMap { nodeDao.parentsWithChildren(it) }.toSet()
        val tags = ids.chunked(500).flatMap { database.tagDao().tagsForNodes(it) }.groupBy { it.nodeId }
        val people = ids.chunked(500).flatMap { database.personDao().assignmentsForNodes(it) }.groupBy { it.nodeId }
        val events = ids.chunked(500).flatMap { database.nodeEventDao().eventsForNodes(it) }.groupBy { it.nodeId }
        val technologies = ids.chunked(500).flatMap { database.technologyDao().forNodes(it) }.groupBy { it.nodeId }
        com.r0ybt.arachn0de.domain.model.CreationUndo(ids.map { rows.getValue(it).toNode(it in parents) },
            ids.associateWith { tags[it].orEmpty().map { row -> row.tagId }.toSet() },
            ids.associateWith { people[it].orEmpty().map { row -> row.personId }.toSet() },
            ids.associateWith { events[it].orEmpty().map { row -> row.toEvent() } },
            ids.associateWith { id -> database.attachmentDao().forNode(id).map { it.attachmentId }.toSet() }.filterValues { it.isNotEmpty() },
            ids.associateWith { id -> technologies[id].orEmpty().map { it.technologyId }.toSet() }.filterValues { it.isNotEmpty() },
            ids.associateWith { id->technologies[id].orEmpty().sortedWith(compareBy({it.position},{it.technologyId})).map {it.technologyId} }.filterValues {it.isNotEmpty()})
    }

    suspend fun deleteSelected(projectId: String, selected: Set<String>): Int {
        beforeDelete(selected)
        return deleteSelectedInTransaction(projectId,selected)
    }

    internal suspend fun deleteSelectedInTransaction(projectId:String,selected:Set<String>):Int = database.withTransaction {
        val nodes = nodeDao.getProjectNodes(projectId).map { it.toNode(false) }
        val roots = com.r0ybt.arachn0de.domain.model.SelectionRoots.normalize(nodes, selected)
        val children = nodes.groupBy { it.parentId }
        val stack = ArrayDeque<String>(); stack.addAll(roots)
        val affected = mutableListOf<String>()
        while (stack.isNotEmpty()) {
            val id = stack.removeLast(); affected.add(id)
            children[id].orEmpty().forEach { stack.addLast(it.id) }
        }
        management.backlog.rejectDeletion(affected.toSet())
        sprintLinks.rejectDeletion(affected.toSet())
        val batches = affected.chunked(500)
        batches.forEach { nodeDao.detachNodes(it) }
        batches.forEach { nodeDao.deleteDetachedNodes(it) }
        com.r0ybt.arachn0de.access.AccessRepository(database).cleanReferences()
        selected.size
    }

    suspend fun moveSelected(projectId: String, selected: Set<String>, parentId: String?): Int = database.withTransaction {
        val nodes = nodeDao.getProjectNodes(projectId).map { it.toNode(false) }
        val roots = com.r0ybt.arachn0de.domain.model.SelectionRoots.normalize(nodes, selected)
        val byId = nodes.associateBy { it.id }
        val rootsSet = roots.toSet()
        val visited = hashSetOf<String>()
        var ancestor = parentId
        while (ancestor != null) {
            require(ancestor !in rootsSet && visited.add(ancestor)) { "Destino dentro de la selección." }
            val parent = requireNotNull(byId[ancestor]) { "Destino ausente." }
            require(parent.obligation == null && (parent.isStructural || (ancestor == parentId && parent.canReceiveChildren))) { "Destino incompatible." }
            ancestor = parent.parentId
        }
        val changing = roots.filter { byId.getValue(it).parentId != parentId }
        if (changing.isNotEmpty()) {
            management.backlog.validateStructure(database.backupDao().nodes().map {if(it.id in changing) it.copy(parentId=parentId) else it})
            val at = currentTimeMillis()
            validateParent(projectId, parentId)
            var maximum = nodes.filter { it.parentId == parentId }.maxOfOrNull { it.position } ?: -1
            if (maximum.toLong() + changing.size > Int.MAX_VALUE) {
                writeOrder(nodeDao.getSiblings(projectId, parentId))
                maximum = nodeDao.maxPosition(projectId, parentId) ?: -1
            }
            check(maximum.toLong() + changing.size <= Int.MAX_VALUE) { "Orden agotado." }
            changing.forEachIndexed { index, id -> check(nodeDao.move(id,parentId,maximum+index+1,at,movedWorkState(checkNotNull(nodeDao.getById(id)),parentId)) == 1) }
        }
        selected.size
    }

    /** Refuse undo if any captured row changed, vanished or acquired children. Never delete new work. */
    suspend fun undoCreation(token: com.r0ybt.arachn0de.domain.model.CreationUndo): Boolean {
        beforeDelete(token.nodes.mapTo(hashSetOf()) {it.id})
        return database.withTransaction {
        val expected = token.nodes
        if (expected.isEmpty() || expected.any {database.metroDao().forNode(it.id)!=null}) return@withTransaction false
        val actual = runCatching { captureCreation(expected.map { it.id }) }.getOrNull() ?: return@withTransaction false
        if (expected.any { it.hasChildren } || actual != token) return@withTransaction false
        // Every captured node is still a leaf; direct chunks cannot cascade into new work.
        if(runCatching {management.backlog.rejectDeletion(expected.mapTo(hashSetOf()) {it.id})}.isFailure) return@withTransaction false
        expected.map { it.id }.chunked(500).forEach { nodeDao.deleteDetachedNodes(it) }
        com.r0ybt.arachn0de.access.AccessRepository(database).cleanReferences()
        true
    }
    }

    suspend fun updateGroup(sourceId: String, patch: com.r0ybt.arachn0de.domain.model.SharedNodePatch,
        expectedIds: Set<String>, title: String, description: String, startAt: Long?, dueAt: Long?,
        editDates: Boolean, obligation: Obligation?, removeObligation: Boolean, tagIds: Set<String>, priority: Priority,
        responsibleIds: Set<String>): Int = database.withTransaction {
        val source = requireNotNull(nodeDao.getById(sourceId)) { "El elemento ya no existe." }
        val group = requireNotNull(source.creationGroupId) { "No pertenece a un grupo." }
        val members = nodeDao.groupMembers(group)
        check(members.map { it.id }.toSet() == expectedIds) { "El grupo cambió. Vuelve a revisar sus miembros." }
        require(!patch.isEmpty)
        require(members.all { it.projectId == source.projectId })
        val parents = nodeDao.getProjectNodes(source.projectId).mapNotNull { it.parentId }.toSet()
        patch.tags?.let { tags.validate(it) }
        patch.responsibleIds?.forEach { requireNotNull(database.personDao().get(it)) { "Persona ausente." } }
        val at = currentTimeMillis()
        members.forEach { current ->
            val actualAmount = if (patch.amount != null) patch.amount.value else current.amountMinor
            val currency = if (patch.currency != null) patch.currency.value else current.currencyCode
            val financial = actualAmount?.let { Obligation(it, requireNotNull(currency)) }
            PaymentCurrencyPolicy.requireEdit(current.amountMinor?.let { Obligation(it, requireNotNull(current.currencyCode)) }, financial)
            require((actualAmount == null) == (currency == null)) { "Monto y moneda deben ser consistentes." }
            val leaf = current.purpose == "ACTION" && current.id !in parents
            require((patch.amount == null && patch.currency == null && patch.priority == null) || leaf) { "El grupo contiene notas o capas incompatibles con estos cambios." }
            patch.tags?.let { ids ->
                database.tagDao().clearNode(current.id)
                if(ids.isNotEmpty()) database.tagDao().assignNodes(ids.map { NodeTagEntity(current.id,it) })
            }
            patch.responsibleIds?.let { ids ->
                database.personDao().clearAssignments(current.id)
                if(ids.isNotEmpty()) database.personDao().assign(ids.map { NodePersonEntity(current.id,it) })
            }
            ensureNodeReferences(database, current.id, patch.description ?: current.description)
            check(nodeDao.patchShared(current.id, patch.description ?: current.description, financial?.amountMinor,
                financial?.currencyCode, patch.priority?.name ?: current.priority, at) == 1)
        }
        check(updateEditor(sourceId,title,description,startAt,dueAt,obligation,removeObligation,editDates,tagIds,priority.takeIf { editDates },responsibleIds))
        members.size
    }

    suspend fun deleteNode(id: String): Boolean {
        beforeDelete(setOf(id))
        return database.withTransaction {
        management.backlog.rejectDeletion(nodeDao.getSubtreeIds(id).toSet())
        sprintLinks.rejectDeletion(nodeDao.getSubtreeIds(id).toSet())
        val deleted=nodeDao.delete(id)==1
        com.r0ybt.arachn0de.access.AccessRepository(database).cleanReferences()
        deleted
    }
    }

    suspend fun calculateProgress(nodeId: String): NodeProgress? = database.withTransaction {
        val current = nodeDao.getById(nodeId) ?: return@withTransaction null
        snapshot(nodeDao.getProjectNodes(current.projectId)).progressById[nodeId]
    }

    suspend fun calculateProjectProgress(projectId: String): NodeProgress? = database.withTransaction {
        val projectNodes = nodeDao.getProjectNodes(projectId)
        if (projectNodes.isEmpty()) {
            return@withTransaction NodeProgress(
                nodeId = projectId,
                completed = 0,
                total = 0,
                percentage = 0,
                state = com.r0ybt.arachn0de.domain.model.NodeProgressState.NO_WORK,
            )
        }
        snapshot(projectNodes).projectProgressById[projectId]
    }

    suspend fun getProjectNodes(projectId: String): List<Node> =
        snapshot(nodeDao.getProjectNodes(projectId)).nodes

    suspend fun hasChildren(nodeId: String): Boolean = getNode(nodeId)?.hasChildren == true

    suspend fun getNodeDepth(nodeId: String): Int = getNodePath(nodeId).size

    suspend fun getNodePath(nodeId: String): List<Node> = database.withTransaction {
        val path = mutableListOf<Node>()
        val visited = mutableSetOf<String>()
        var currentId: String? = nodeId
        var projectId: String? = null
        while (currentId != null) {
            check(visited.add(currentId)) { "Cycle in node hierarchy" }
            val current = nodeDao.getById(currentId)
            if (current == null && path.isEmpty()) break
            checkNotNull(current) { "Missing ancestor" }
            if (projectId == null) projectId = current.projectId
            check(current.projectId == projectId) { "Invalid parent project" }
            path.add(current.toNode(nodeDao.hasChildren(current.projectId, current.id)))
            currentId = current.parentId
        }
        path.asReversed().toList()
    }

    internal suspend fun validateParent(projectId: String?, parentId: String?, movingId: String? = null) {
        require(parentId==null || database.bankDao().roots().none {it.id==projectId}) {"Las cuentas de Banca no admiten subcuentas ni Capas interiores."}
        val visited = mutableSetOf<String>()
        var currentId = parentId
        while (currentId != null) {
            require(currentId != movingId) { "A node cannot be its own ancestor" }
            check(visited.add(currentId)) { "Cycle in existing hierarchy" }
            val parent = nodeDao.getById(currentId)
            requireNotNull(parent) { "Parent node not found" }
            require(parent.amountMinor == null) { "Convierte este pago en una tarea antes de usarla como capa." }
            require(parent.purpose == NodePurpose.LAYER.name || (currentId == parentId && parent.purpose == "ACTION" && !parent.isCompleted)) { "Only layers or pending tasks can receive children" }
            require(parent.projectId == projectId) { "Parent node must belong to the same project" }
            currentId = parent.parentId
        }
        if (parentId != null && nodeDao.getById(parentId)?.purpose == "ACTION") {
            check(convertPurpose(parentId, NodePurpose.LAYER)) { "Destination conversion was not written" }
        }
    }

    private suspend fun nextPosition(projectId: String?, parentId: String?): Int {
        var maximum = nodeDao.maxPosition(projectId, parentId) ?: -1
        if (maximum == Int.MAX_VALUE) {
            writeOrder(nodeDao.getSiblings(projectId, parentId))
            maximum = nodeDao.maxPosition(projectId, parentId) ?: -1
        }
        check(maximum < Int.MAX_VALUE) { "Sibling position exhausted" }
        return maximum + 1
    }

    private fun snapshot(entities: List<NodeEntity>): NodeTreeSnapshot {
        val parents = entities.mapNotNull { it.parentId }.toHashSet()
        return NodeTreeSnapshot(entities.map { it.toNode(it.id in parents) })
    }

    private fun validateTitle(title: String): String = title.trim().also {
        require(it.isNotEmpty()) { "Node title must not be blank" }
        require(TitleLimits.count(it) <= TitleLimits.NODE) { "Node title exceeds 100 characters" }
    }
}
