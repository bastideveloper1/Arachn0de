package com.r0ybt.arachn0de.backup

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.Obligation
import com.r0ybt.arachn0de.domain.model.TaskTemporal

/** Logical record set, not a database file. PNG keys are portable names, never device paths. */
internal data class BackupData(
    val appVersion: String,
    val createdAt: Long,
    val projects: List<ProjectEntity>,
    val nodes: List<NodeEntity>,
    val persons: List<PersonEntity>,
    val assignments: List<NodePersonEntity>,
    val avatars: Map<String, ByteArray>,
    val recurrenceRules: List<RecurrenceRuleEntity> = emptyList(),
    val recurrenceOccurrences: List<RecurrenceOccurrenceEntity> = emptyList(),
    val recurrenceAssignments: List<RecurrencePersonEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val nodeTags: List<NodeTagEntity> = emptyList(),
    val recurrenceTags: List<RecurrenceTagEntity> = emptyList(),
    val nodeEvents: List<NodeEventEntity> = emptyList(),
    val creationDefaults: List<CreationDefaultsEntity> = emptyList(),
    val defaultsTags: List<CreationDefaultsTagEntity> = emptyList(),
    val defaultsPeople: List<CreationDefaultsPersonEntity> = emptyList(),
)

internal object BackupLimits {
    const val PAYLOAD_BYTES = 16 * 1024 * 1024
    const val AVATAR_BYTES = 1024 * 1024
    const val TOTAL_AVATAR_BYTES = 8 * 1024 * 1024
    const val RECORDS = 100_000
    val avatarName = Regex("[a-f0-9-]{36}\\.png")
}

/** Validate before staging or deleting anything; return parent-first order without recursion. */
internal fun BackupData.validate(): List<NodeEntity> {
    require(appVersion.isNotBlank() && appVersion.length <= 128 && createdAt >= 0) { "Metadatos de backup inválidos." }
    require(projects.size.toLong() + nodes.size + persons.size + assignments.size + recurrenceRules.size + recurrenceOccurrences.size + recurrenceAssignments.size + tags.size + nodeTags.size + recurrenceTags.size + nodeEvents.size + creationDefaults.size + defaultsTags.size + defaultsPeople.size <= BackupLimits.RECORDS) { "Demasiados registros en el backup." }
    fun unique(ids: List<String>): Set<String> {
        require(ids.all { it.isNotBlank() && it.length <= 256 }) { "Identidad inválida." }
        return ids.toSet().also { require(it.size == ids.size) { "Identidades duplicadas." } }
    }
    val projectIds = unique(projects.map { it.id })
    val nodeIds = unique(nodes.map { it.id })
    val personIds = unique(persons.map { it.id })
    // Historical long titles and non-contiguous positions are valid persisted data, not editor input.
    require(projects.all { it.name.isNotBlank() } && nodes.all { it.title.isNotBlank() } && persons.all { it.name.isNotBlank() }) { "Nombre vacío." }
    val byId = nodes.associateBy { it.id }
    val children = nodes.groupBy { it.parentId }
    nodes.forEach { node ->
        require(node.projectId in projectIds) { "Proyecto ausente." }
        node.parentId?.let { parentId ->
            val parent = requireNotNull(byId[parentId]) { "Padre ausente." }
            require(parent.projectId == node.projectId && parent.id != node.id && parent.purpose == "LAYER" && parent.amountMinor == null) { "Jerarquía inválida." }
        }
        require(node.creationGroupId == null || (node.creationGroupId.isNotBlank() && node.creationGroupId.length <= 200)) { "Grupo inválido." }
        com.r0ybt.arachn0de.domain.model.Priority.valueOf(node.priority)
        require(node.purpose in listOf("ACTION","NOTE","LAYER")) { "Propósito desconocido." }
        require(!node.isCompleted || (node.purpose == "ACTION" && children[node.id].isNullOrEmpty())) { "Completado inválido." }
        require(node.purpose == "LAYER" || children[node.id].isNullOrEmpty()) { "Nota con hijos." }
        TaskTemporal.validateDates(node.startAt, node.dueAt)
        require((node.amountMinor == null) == (node.currencyCode == null)) { "Obligación incompleta." }
        node.amountMinor?.let {
            require(node.purpose == "ACTION" && children[node.id].isNullOrEmpty()) { "Obligación con hijos." }
            Obligation(it, requireNotNull(node.currencyCode))
        }
    }
    require(nodes.filter { it.creationGroupId != null }.groupBy { it.creationGroupId }.values.all { group -> group.map { it.projectId }.distinct().size == 1 }) { "Grupo compartido entre proyectos." }
    val queue = ArrayDeque<NodeEntity>()
    queue.addAll(children[null].orEmpty())
    val ordered = ArrayList<NodeEntity>(nodes.size)
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        ordered.add(node)
        queue.addAll(children[node.id].orEmpty())
    }
    require(ordered.size == nodes.size) { "Ciclo en la jerarquía." }
    require(assignments.toSet().size == assignments.size && assignments.all { it.nodeId in nodeIds && it.personId in personIds }) { "Relación de responsables inválida." }
    val ruleIds = unique(recurrenceRules.map { it.id })
    recurrenceRules.forEach { com.r0ybt.arachn0de.data.repository.RecurrenceRepository.validate(it) }
    require(recurrenceOccurrences.map { it.ruleId to it.day }.toSet().size == recurrenceOccurrences.size)
    unique(recurrenceOccurrences.map { it.nodeId })
    val rulesById = recurrenceRules.associateBy { it.id }
    recurrenceOccurrences.forEach { receipt ->
        val rule = requireNotNull(rulesById[receipt.ruleId]) { "Regla ausente." }
        val frequency = com.r0ybt.arachn0de.domain.model.RecurrenceFrequency.valueOf(rule.frequency)
        val index = com.r0ybt.arachn0de.domain.model.RecurrenceSchedule.indexOnOrAfter(rule.startDay, frequency, rule.interval, receipt.day)
        require(index < rule.nextIndex && com.r0ybt.arachn0de.domain.model.RecurrenceSchedule.date(rule.startDay, frequency, rule.interval, index) == receipt.day)
        require(rule.endDay == null || receipt.day <= rule.endDay)
        require(receipt.nodeId == java.util.UUID.nameUUIDFromBytes("recurrence:${rule.id}:${receipt.day}".toByteArray(Charsets.UTF_8)).toString())
        // Missing Nodes are intentional deletion receipts, not broken backup references.
    }
    require(recurrenceAssignments.toSet().size == recurrenceAssignments.size && recurrenceAssignments.all { it.ruleId in ruleIds && it.personId in personIds })
    val tagIds = unique(tags.map { it.id })
    require(tags.map { it.normalizedName }.toSet().size == tags.size) { "Etiquetas duplicadas." }
    tags.forEach {
        require(it.name == com.r0ybt.arachn0de.domain.model.TagNames.display(it.name) &&
            it.normalizedName == com.r0ybt.arachn0de.domain.model.TagNames.normalize(it.name)) { "Etiqueta inválida." }
    }
    require(nodeTags.toSet().size == nodeTags.size && nodeTags.all { it.nodeId in nodeIds && it.tagId in tagIds })
    require(recurrenceTags.toSet().size == recurrenceTags.size && recurrenceTags.all { it.ruleId in ruleIds && it.tagId in tagIds })
    unique(nodeEvents.map { it.id })
    nodeEvents.forEach {
        require(it.nodeId in nodeIds) { "Evento de un nodo ausente." }
        com.r0ybt.arachn0de.domain.model.NodeEventType.valueOf(it.type)
    }
    val defaultsIds=creationDefaults.map { it.id }.toSet()
    require(defaultsIds.size==creationDefaults.size) { "Defaults duplicados." }
    val defaultTagsById=defaultsTags.groupBy { it.defaultsId };val defaultPeopleById=defaultsPeople.groupBy { it.defaultsId }
    require(defaultsTags.toSet().size==defaultsTags.size && defaultsTags.all { it.defaultsId in defaultsIds && it.tagId in tagIds }) { "Tags de defaults inválidos." }
    require(defaultsPeople.toSet().size==defaultsPeople.size && defaultsPeople.all { it.defaultsId in defaultsIds && it.personId in personIds }) { "Responsables de defaults inválidos." }
    creationDefaults.forEach { row ->
        row.projectId?.let { require(it in projectIds) { "Proyecto de defaults ausente." } }
        row.nodeId?.let { require(byId[it]?.projectId==row.projectId && row.projectId!=null) { "Capa de defaults inválida." } }
        com.r0ybt.arachn0de.data.repository.CreationDefaultsCodec.decode(row,
            defaultTagsById[row.id].orEmpty().mapTo(hashSetOf()) { it.tagId },defaultPeopleById[row.id].orEmpty().mapTo(hashSetOf()) { it.personId })
    }
    val names = persons.mapNotNull { it.avatarFile }.toSet()
    require(names == avatars.keys && names.all { BackupLimits.avatarName.matches(it) }) { "Referencias de avatar inválidas." }
    require(avatars.values.sumOf { it.size.toLong() } <= BackupLimits.TOTAL_AVATAR_BYTES) { "Avatares demasiado grandes." }
    avatars.values.forEach { validateAvatar(it) }
    return ordered
}

internal fun validateAvatar(bytes: ByteArray) {
    require(bytes.size in 1..BackupLimits.AVATAR_BYTES) { "Avatar demasiado grande o vacío." }
    val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    require(bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(signature)) { "Avatar no PNG." }
    // Check every PNG chunk including its CRC; decoders can otherwise accept a truncated image.
    val input = java.io.DataInputStream(java.io.ByteArrayInputStream(bytes, 8, bytes.size - 8))
    var first = true
    var ended = false
    while (input.available() > 0) {
        require(input.available() >= 12) { "PNG incompleto." }
        val size = input.readInt()
        require(size >= 0 && size <= input.available() - 8) { "PNG incompleto." }
        val type = ByteArray(4).also(input::readFully)
        val content = ByteArray(size).also(input::readFully)
        val crc = java.util.zip.CRC32().apply { update(type); update(content) }.value
        require(input.readInt().toLong() and 0xffffffffL == crc) { "PNG dañado." }
        val name = type.toString(Charsets.US_ASCII)
        if (first) require(name == "IHDR" && size == 13) { "Cabecera PNG inválida." }
        first = false
        if (name == "IEND") {
            require(size == 0 && input.available() == 0) { "Fin PNG inválido." }
            ended = true
        }
    }
    require(ended) { "PNG incompleto." }
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth in 1..512 && bounds.outHeight in 1..512) { "Dimensiones de avatar inválidas." }
    val image = requireNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Avatar dañado." }
    image.recycle()
}
