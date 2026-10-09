package com.r0ybt.arachn0de.data.repository

import androidx.room.withTransaction
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.domain.defaults.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Converts the live tree, retaining only properties with no counterpart as permanent root data. */
internal class ProjectNestingRepository(private val database: Arachn0deDatabase,
    private val beforeCommit: suspend () -> Unit = {}) {
    private val projects get() = database.projectDao()
    private val nodes get() = database.nodeDao()
    private val conversion get() = database.conversionDao()
    private suspend fun <T> atomic(work: suspend () -> T): T = withContext(Dispatchers.IO) {
        AttachmentRepository.fileOperations.withLock { database.withTransaction {
            val result = work(); beforeCommit(); currentCoroutineContext().ensureActive(); result
        } }
    }
    suspend fun move(sourceId: String, targetId: String, parentId: String?): String = atomic {
        require(sourceId != targetId) { "El proyecto no puede convertirse dentro de sí mismo." }
        val source = requireNotNull(projects.getById(sourceId)) { "El proyecto ya no existe." }
        requireNotNull(projects.getById(targetId)) { "El proyecto de destino ya no existe." }
        val target = nodes.getProjectNodes(targetId).associateBy { it.id }
        var cursor = parentId; val ancestors = hashSetOf<String>()
        while (cursor != null) {
            require(ancestors.add(cursor)) { "El destino contiene un ciclo." }
            val row = requireNotNull(target[cursor]) { "La capa de destino ya no existe o pertenece a otro proyecto." }
            require(row.purpose == "LAYER" && row.amountMinor == null) { "El destino debe ser una capa." }
            cursor = row.parentId
        }
        val original = nodes.getProjectNodes(sourceId)
        validate(original)
        val saved = conversion.project(sourceId)
        val layerId = available(saved?.nodeIdentity ?: sourceId) { nodes.getById(it) != null }
        val root = saved ?: ConversionRootEntity(UUID.randomUUID().toString(), sourceId, null, sourceId, layerId,
            source.position, 0, null, null, "NONE", null, false)
        val group = root.creationGroupId?.let { validGroup(it, targetId, original.mapTo(hashSetOf()) { row -> row.id }) }
        val layer = NodeEntity(layerId, targetId, parentId, source.name, source.description, false,
            nextNodePosition(targetId, parentId), source.createdAt, source.updatedAt, root.startAt, root.dueAt,
            purpose = "LAYER", priority = root.priority, creationGroupId = group, sprintMode = root.sprintMode)
        val effective = CreationDefaultsRepository(database).resolve(sourceId, null)
        val sort = database.nodeSortPreferenceDao().all()
        val bundle = capture(original, sourceId, wholeProject = true)
        val hiddenPeople = conversion.people().filter { it.rootId == root.id }
        val hiddenTags = conversion.tags().filter { it.rootId == root.id }
        val hiddenEvents = conversion.events().filter { it.rootId == root.id }
        val hiddenWork = conversion.workStates().filter { it.rootId == root.id }.associateBy { it.nodeId }
        val photo = database.projectPhotoDao().forProject(sourceId)
        val attachments = database.attachmentDao().forProject(sourceId)
        val technologies = database.technologyDao().projects().filter { it.projectId == sourceId }
        nodes.insert(layer)
        transfer(bundle, targetId, layerId, root.id, hiddenWork, layer)
        conversion.save(root.copy(projectId = null, nodeId = layerId, projectIdentity = sourceId,
            nodeIdentity = layerId, projectPosition = source.position, nodePosition = layer.position))
        database.personDao().assign(hiddenPeople.map { NodePersonEntity(layerId, it.personId) })
        database.tagDao().assignNodes(hiddenTags.map { NodeTagEntity(layerId, it.tagId) })
        database.nodeEventDao().insertAll(hiddenEvents.map { NodeEventEntity(it.id, layerId, it.type, it.occurredAt) })
        conversion.clearPeople(root.id); conversion.clearTags(root.id); conversion.clearEvents(root.id); conversion.clearWorkStates(root.id)
        database.technologyDao().assignNodes(technologies.map { NodeTechnologyEntity(layerId, it.technologyId, it.position) })
        database.attachmentDao().attachNode(attachments.map { NodeAttachmentEntity(layerId, it.attachmentId) })
        photo?.let { database.projectPhotoDao().save(it.copy(projectId = null, nodeId = layerId)) }
        preserveEffectiveDefaults(DefaultsScope.Layer(targetId, layerId), effective)
        remapSort(sourceId, targetId, original, null, layerId, sort)
        check(projects.deleteProjectRow(sourceId) == 1)
        layerId
    }
    suspend fun promote(layerId: String): String = atomic {
        val layer = requireNotNull(nodes.getById(layerId)) { "La capa ya no existe." }
        require(layer.purpose == "LAYER" && layer.amountMinor == null) { "Solo una capa puede convertirse en proyecto." }
        val entire = nodes.getProjectNodes(layer.projectId)
        validate(entire)
        val ids = nodes.getSubtreeIds(layerId).toHashSet()
        val subtree = entire.filter { it.id in ids }
        val saved = conversion.node(layerId)
        val projectId = available(saved?.projectIdentity ?: layerId) { projects.getById(it) != null }
        val position = saved?.projectPosition ?: nextProjectPosition()
        val root = (saved ?: ConversionRootEntity(UUID.randomUUID().toString(), null, layerId, projectId,
            layerId, position, layer.position, layer.startAt, layer.dueAt, layer.priority, layer.creationGroupId, layer.sprintMode))
            .copy(projectId = projectId, nodeId = null, projectIdentity = projectId, nodeIdentity = layerId,
                nodePosition = layer.position, startAt = layer.startAt, dueAt = layer.dueAt,
                priority = layer.priority, creationGroupId = layer.creationGroupId, sprintMode = layer.sprintMode)
        val effective = CreationDefaultsRepository(database).resolve(layer.projectId, layerId)
        val sort = database.nodeSortPreferenceDao().all()
        val bundle = capture(subtree, layer.projectId, wholeProject = false)
        val photo = database.projectPhotoDao().forNodes(listOf(layerId)).singleOrNull()
        val attachments = database.attachmentDao().forNode(layerId)
        val technologies = database.technologyDao().forNodes(listOf(layerId))
        val rootPeople = database.personDao().assignmentsForNodes(listOf(layerId))
        val rootTags = database.tagDao().tagsForNodes(listOf(layerId))
        val rootEvents = database.nodeEventDao().forNode(layerId).asReversed()
        val directWork = subtree.filter { it.parentId == layerId && it.workState != null }
        projects.insert(ProjectEntity(projectId, layer.title, layer.description, position, layer.createdAt, layer.updatedAt))
        val groups = transfer(bundle, projectId, null, root.id, emptyMap(), excluded = layerId)
        conversion.save(root.copy(creationGroupId = layer.creationGroupId?.let(groups::getValue)))
        conversion.people(rootPeople.map { ConversionPersonEntity(root.id, it.personId) })
        conversion.tags(rootTags.map { ConversionTagEntity(root.id, it.tagId) })
        conversion.events(rootEvents.map { ConversionEventEntity(it.id, root.id, it.type, it.occurredAt) })
        conversion.workStates(directWork.map { ConversionWorkStateEntity(it.id, root.id, checkNotNull(it.workState)) })
        database.technologyDao().assignProjects(technologies.map { ProjectTechnologyEntity(projectId, it.technologyId, it.position) })
        database.attachmentDao().attachProject(attachments.map { ProjectAttachmentEntity(projectId, it.attachmentId) })
        photo?.let { database.projectPhotoDao().save(it.copy(projectId = projectId, nodeId = null)) }
        preserveEffectiveDefaults(DefaultsScope.Project(projectId), effective)
        remapSort(layer.projectId, projectId, subtree, layerId, null, sort)
        projectId
    }
    private data class Bundle(val nodes: List<NodeEntity>, val people: List<NodePersonEntity>, val tags: List<NodeTagEntity>,
        val events: List<NodeEventEntity>, val technologies: List<NodeTechnologyEntity>, val attachments: List<NodeAttachmentEntity>,
        val photos: List<ProjectPhotoEntity>, val roots: List<ConversionRootEntity>, val work: List<ConversionWorkStateEntity>,
        val defaults: List<CreationDefaultsEntity>, val defaultTags: List<CreationDefaultsTagEntity>,
        val defaultPeople: List<CreationDefaultsPersonEntity>, val rules: List<RecurrenceRuleEntity>)
    private suspend fun capture(rows: List<NodeEntity>, projectId: String, wholeProject: Boolean): Bundle {
        val ids = rows.mapTo(hashSetOf()) { it.id }; val batches = ids.chunked(500)
        val defaults = database.creationDefaultsDao().forProject(projectId).filter {
            it.projectId == projectId && (it.nodeId in ids || (wholeProject && it.nodeId == null)) }
        val defaultsIds = defaults.mapTo(hashSetOf()) { it.id }
        return Bundle(rows, batches.flatMap { database.personDao().assignmentsForNodes(it) },
            batches.flatMap { database.tagDao().tagsForNodes(it) }, batches.flatMap { database.nodeEventDao().eventsForNodes(it).asReversed() },
            batches.flatMap { database.technologyDao().forNodes(it) }, rows.flatMap { database.attachmentDao().forNode(it.id) },
            batches.flatMap { database.projectPhotoDao().forNodes(it) }, conversion.roots().filter { it.nodeId in ids },
            conversion.workStates().filter { it.nodeId in ids }, defaults,
            database.creationDefaultsDao().tags().filter { it.defaultsId in defaultsIds },
            database.creationDefaultsDao().people().filter { it.defaultsId in defaultsIds },
            database.recurrenceDao().rules().filter { it.projectId == projectId && (wholeProject || it.parentId in ids) })
    }
    private suspend fun transfer(bundle: Bundle, targetId: String, rootId: String?, mainIdentity: String,
        hiddenWork: Map<String, ConversionWorkStateEntity>, insertedRoot: NodeEntity? = null, excluded: String? = null): Map<String, String> {
        val ids = bundle.nodes.map { it.id }; val batches = ids.chunked(500)
        val rows = bundle.nodes.filter { it.id != excluded }
        val groups = bundle.nodes.mapNotNull { it.creationGroupId }.distinct().associateWith { validGroup(it, targetId, ids.toSet()) }
        batches.forEach { nodes.detachNodes(it) }; batches.forEach { nodes.deleteDetachedNodes(it) }
        val children = rows.groupBy { if (it.parentId == excluded) null else it.parentId }
        val queue = ArrayDeque<NodeEntity>(); queue.addAll(children[null].orEmpty())
        val inserted = database.nodeDao().getProjectNodes(targetId).associateBy { it.id }.toMutableMap()
        insertedRoot?.let { inserted[it.id] = it }
        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val row = queue.removeFirst()
            val parent = if (row.parentId == null || row.parentId == excluded) rootId else row.parentId
            val sprint = row.purpose == "ACTION" && inserted[parent]?.sprintMode == true
            val remembered = hiddenWork[row.id]?.workState ?: row.workState
            val state = if (!sprint) null else when {
                row.isCompleted -> remembered?.takeIf { it == "DONE" || it == "VALIDATED" } ?: "DONE"
                remembered == "DONE" || remembered == "VALIDATED" || remembered == null -> "UNPLANNED"
                else -> remembered
            }
            val next = row.copy(projectId = targetId, parentId = parent, workState = state,
                creationGroupId = row.creationGroupId?.let(groups::getValue))
            nodes.insert(next); inserted[next.id] = next; queue.addAll(children[row.id].orEmpty())
        }
        check(inserted.keys.containsAll(rows.map { it.id })) { "No se pudo conservar toda la jerarquía." }
        database.personDao().assign(bundle.people.filter { it.nodeId != excluded })
        database.tagDao().assignNodes(bundle.tags.filter { it.nodeId != excluded })
        database.nodeEventDao().insertAll(bundle.events.filter { it.nodeId != excluded })
        database.technologyDao().assignNodes(bundle.technologies.filter { it.nodeId != excluded })
        database.attachmentDao().attachNode(bundle.attachments.filter { it.nodeId != excluded })
        bundle.photos.filter { it.nodeId != excluded }.forEach { database.projectPhotoDao().save(it) }
        bundle.roots.filter { it.id != mainIdentity }.forEach { conversion.save(it) }
        val roots = conversion.roots().associateBy { it.id }
        conversion.workStates(bundle.work.filter { state -> state.rootId != mainIdentity &&
            roots[state.rootId]?.projectId == targetId && inserted[state.nodeId]?.parentId == null })
        val defaultIds = bundle.defaults.associate { it.id to if (it.nodeId == null || it.nodeId == excluded)
            (rootId?.let { root -> "N:$root" } ?: "P:$targetId") else it.id }
        bundle.defaults.forEach { row -> database.creationDefaultsDao().save(row.copy(id = defaultIds.getValue(row.id),
            projectId = targetId, nodeId = if (row.nodeId == null || row.nodeId == excluded) rootId else row.nodeId)) }
        database.creationDefaultsDao().insertTags(bundle.defaultTags.map { it.copy(defaultsId = defaultIds.getValue(it.defaultsId)) })
        database.creationDefaultsDao().insertPeople(bundle.defaultPeople.map { it.copy(defaultsId = defaultIds.getValue(it.defaultsId)) })
        bundle.rules.forEach { row -> database.recurrenceDao().update(row.copy(projectId = targetId,
            parentId = if (row.parentId == null || row.parentId == excluded) rootId else row.parentId)) }
        return groups
    }
    private fun validate(rows: List<NodeEntity>) {
        val parents = rows.mapNotNullTo(hashSetOf()) { it.parentId }
        NodeTreeSnapshot(rows.map { it.toNode(it.id in parents) })
    }
    private suspend fun available(preferred: String, taken: suspend (String) -> Boolean): String {
        if (!taken(preferred)) return preferred
        var next: String; do { next = UUID.randomUUID().toString() } while (taken(next)); return next
    }
    private suspend fun validGroup(id: String, target: String, moving: Set<String> = emptySet()): String =
        if (nodes.groupMembers(id).any { it.id !in moving && it.projectId != target }) UUID.randomUUID().toString() else id
    private suspend fun nextProjectPosition(): Int {
        val max = projects.maxPosition() ?: -1
        if (max < Int.MAX_VALUE) return max + 1
        projects.getAll().sortedWith(compareBy<ProjectEntity> { it.position }.thenBy { it.createdAt }.thenBy { it.id })
            .forEachIndexed { index, row -> check(projects.updateOrder(row.id, index, row.updatedAt) == 1) }
        return (projects.maxPosition() ?: -1) + 1
    }
    private suspend fun nextNodePosition(projectId: String, parentId: String?): Int {
        var max = nodes.maxPosition(projectId, parentId) ?: -1
        if (max == Int.MAX_VALUE) {
            nodes.getSiblings(projectId, parentId).forEachIndexed { index, row -> check(nodes.updateOrder(row.id, index, row.updatedAt) == 1) }
            max = nodes.maxPosition(projectId, parentId) ?: -1
        }
        check(max < Int.MAX_VALUE); return max + 1
    }
    private suspend fun preserveEffectiveDefaults(scope: DefaultsScope, effective: EffectiveCreationDefaults) {
        CreationDefaultsRepository(database).save(scope, CreationDefaults(
            DefaultValue.Own(effective.purpose), DefaultValue.Own(effective.obligation), DefaultValue.Own(effective.currency),
            DefaultValue.Own(effective.priority), DefaultValue.Own(effective.tags), DefaultValue.Own(effective.people),
            DefaultValue.Own(effective.start), DefaultValue.Own(effective.startTime), DefaultValue.Own(effective.due), DefaultValue.Own(effective.dueTime)))
    }
    private suspend fun remapSort(source: String, target: String, rows: List<NodeEntity>, oldRoot: String?, newRoot: String?, preferences: List<NodeSortPreferenceEntity>) {
        val dao = database.nodeSortPreferenceDao()
        val ids = rows.mapTo(hashSetOf()) { it.id }
        val sourceRoot = "$source:${oldRoot ?: "project-root"}"
        val inherited = preferences.firstOrNull { it.context == sourceRoot }
            ?: preferences.firstOrNull { it.context == "$source:project-root" }
        preferences.filter { it.context == sourceRoot ||
            (it.context.startsWith("$source:") && it.context.removePrefix("$source:") in ids) }.forEach { row ->
            dao.delete(row.context)
            val suffix = row.context.removePrefix("$source:")
            dao.save(row.copy(context = "$target:${if (row.context == sourceRoot) newRoot ?: "project-root" else suffix}"))
        }
        dao.save(NodeSortPreferenceEntity("$target:${newRoot ?: "project-root"}", inherited?.mode ?: "MANUAL"))
        val baseline = preferences.firstOrNull { it.context == "$source:project-root" }?.mode ?: "MANUAL"
        rows.filter { it.purpose == "LAYER" && it.id != oldRoot }.forEach { row ->
            val explicit = preferences.firstOrNull { it.context == "$source:${row.id}" }?.mode
            dao.save(NodeSortPreferenceEntity("$target:${row.id}", explicit ?: baseline))
        }
    }
}
