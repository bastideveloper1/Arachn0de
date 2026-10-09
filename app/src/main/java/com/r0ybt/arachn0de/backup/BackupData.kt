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
    val attachmentFiles: List<AttachmentFileEntity> = emptyList(),
    val nodeAttachments: List<NodeAttachmentEntity> = emptyList(),
    val projectAttachments: List<ProjectAttachmentEntity> = emptyList(),
    // Private operation-local files, never serialized as paths.
    val attachmentContents: Map<String, java.io.File> = emptyMap(),
    val inspectionDirectory: java.io.File? = null,
    val technologies: List<TechnologyEntity> = emptyList(),
    val nodeTechnologies: List<NodeTechnologyEntity> = emptyList(),
    val projectTechnologies: List<ProjectTechnologyEntity> = emptyList(),
    val technologyIcons: Map<String, ByteArray> = emptyMap(),
    val projectPhotos: List<ProjectPhotoEntity> = emptyList(),
    val projectPhotoImages: Map<String, ByteArray> = emptyMap(),
    val conversionRoots: List<ConversionRootEntity> = emptyList(),
    val conversionPeople: List<ConversionPersonEntity> = emptyList(),
    val conversionTags: List<ConversionTagEntity> = emptyList(),
    val conversionEvents: List<ConversionEventEntity> = emptyList(),
    val conversionWorkStates: List<ConversionWorkStateEntity> = emptyList(),
    val nodeSortPreferences: List<NodeSortPreferenceEntity> = emptyList(),
    val imageFiles: List<BackupImageFile> = emptyList(),
    val imageContents: Map<String, java.io.File> = emptyMap(),
    val gameSession: String? = null,
    val storeId:String?=null,
    val storeKind:String="primary",
    val privatePreferences: List<PrivatePreferenceEntity> = emptyList(),
    val savedTemplates: List<SavedTemplateEntity> = emptyList(),
    val metroPreferences: String? = null,
    val metroJourneys: List<MetroJourneyEntity> = emptyList(),
)

internal object BackupLimits {
    const val PAYLOAD_BYTES = 16 * 1024 * 1024
    const val TOTAL_STREAMED_IMAGE_BYTES = 1024L * 1024 * 1024
    const val PROJECT_PHOTO_BYTES = 20 * 1024 * 1024
    const val AVATAR_BYTES = 1024 * 1024
    const val TOTAL_AVATAR_BYTES = 8 * 1024 * 1024
    const val ATTACHMENT_BYTES = 20L * 1024 * 1024
    const val TOTAL_ATTACHMENT_BYTES = 2L * 1024 * 1024 * 1024
    val attachmentName = Regex("[a-f0-9-]{36}\\.(png|jpg)")
    const val RECORDS = 100_000
    val avatarName = Regex("[a-f0-9-]{36}\\.png")
}

/** Validate before staging or deleting anything; return parent-first order without recursion. */
internal fun BackupData.validate(): List<NodeEntity> {
    require(storeKind in setOf("primary","secondary"))
    storeId?.let { require(java.util.UUID.fromString(it).toString()==it) }
    require(privatePreferences.map { it.name }.toSet().size == privatePreferences.size)
    privatePreferences.forEach { row ->
        require(row.name.matches(Regex("[a-zA-Z0-9_-]+")) && row.name != "vault_meta")
        com.r0ybt.arachn0de.security.EncryptedPreferences.decode(row.payload)
    }
    gameSession?.let { require(com.r0ybt.arachn0de.game.GameSessionCodec.decode(it) != null) { "Partida guardada inválida." } }
    require(appVersion.isNotBlank() && appVersion.length <= 128 && createdAt >= 0) { "Metadatos de backup inválidos." }
    require(privatePreferences.size + savedTemplates.size + projects.size.toLong() + nodes.size + persons.size + assignments.size + recurrenceRules.size + recurrenceOccurrences.size + recurrenceAssignments.size + tags.size + nodeTags.size + recurrenceTags.size + nodeEvents.size + creationDefaults.size + defaultsTags.size + defaultsPeople.size + attachmentFiles.size + nodeAttachments.size + projectAttachments.size + technologies.size + nodeTechnologies.size + projectTechnologies.size + projectPhotos.size + conversionRoots.size + conversionPeople.size + conversionTags.size + conversionEvents.size + conversionWorkStates.size + nodeSortPreferences.size + imageFiles.size + metroJourneys.size + (if (metroPreferences == null) 0 else 1) + (if (gameSession == null) 0 else 1) <= BackupLimits.RECORDS) { "Demasiados registros en el backup." }
    fun unique(ids: List<String>): Set<String> {
        require(ids.all { it.isNotBlank() && it.length <= 256 }) { "Identidad inválida." }
        return ids.toSet().also { require(it.size == ids.size) { "Identidades duplicadas." } }
    }
    unique(savedTemplates.map { it.id }); savedTemplates.forEach { com.r0ybt.arachn0de.templates.SavedTemplateCodec.validate(it) }
    val metroNet = metroPreferences?.let { com.r0ybt.arachn0de.metro.MetroCodec.preferences(it).network }
    require(metroJourneys.isEmpty() || metroNet != null) { "Falta el catálogo Metro del backup." }
    unique(metroJourneys.map { it.id })
    require(metroJourneys.mapNotNull { it.nodeId }.distinct().size == metroJourneys.count { it.nodeId != null })
    metroJourneys.forEach { row ->
        require(row.revision >= 0 && (row.nodeId == null || nodes.any { it.id == row.nodeId }) && (row.personId == null || persons.any { it.id == row.personId })) { "Referencia Metro inválida." }
        com.r0ybt.arachn0de.metro.MetroCodec.journey(row.payload, requireNotNull(metroNet))
    }
    require(metroJourneys.count { com.r0ybt.arachn0de.metro.MetroCodec.journey(it.payload, requireNotNull(metroNet)).active != null } <= 1) { "Más de un seguimiento activo." }
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
        require(!node.sprintMode || node.purpose == "LAYER") { "Modo Sprint incompatible." }
        val sprintAction = node.purpose == "ACTION" && byId[node.parentId]?.sprintMode == true
        require((node.workState != null) == sprintAction) { "Estado Sprint fuera de contexto." }
        node.workState?.let {
            com.r0ybt.arachn0de.domain.model.WorkState.valueOf(it)
        }
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
    persons.forEach { com.r0ybt.arachn0de.domain.model.AvatarFraming(it.avatarZoom, it.avatarX, it.avatarY).validate() }
    val names = persons.mapNotNull { it.avatarFile }.toSet()
    require(names == imageNames("avatars") && names.all { BackupLimits.avatarName.matches(it) }) { "Referencias de avatar inválidas." }
    require(avatars.values.sumOf { it.size.toLong() } <= BackupLimits.TOTAL_AVATAR_BYTES) { "Avatares demasiado grandes." }
    avatars.values.forEach { validateAvatar(it) }
    val attachmentIds = unique(attachmentFiles.map { it.id })
    require(attachmentFiles.map { it.storageName }.toSet().size == attachmentFiles.size) { "Nombres de adjunto duplicados." }
    attachmentFiles.forEach {
        require(BackupLimits.attachmentName.matches(it.storageName) && java.util.UUID.fromString(it.storageName.substringBefore('.')).toString() == it.storageName.substringBefore('.')) { "Nombre de adjunto inválido." }
        require(java.util.UUID.fromString(it.id).toString() == it.id && it.originalName.isNotBlank() && it.originalName.length <= 4096) { "Metadatos de adjunto inválidos." }
        require(it.lifecycleState == "READY" && it.byteSize in 1..BackupLimits.ATTACHMENT_BYTES && it.createdAt >= 0 && it.width in 1..32000 && it.height in 1..32000) { "Metadatos de adjunto inválidos." }
        require(it.mimeType == if (it.storageName.endsWith(".png")) "image/png" else "image/jpeg") { "Tipo de adjunto inválido." }
        require(it.sha256.matches(Regex("[a-f0-9]{64}"))) { "Hash de adjunto inválido." }
    }
    require(attachmentFiles.sumOf { it.byteSize } <= BackupLimits.TOTAL_ATTACHMENT_BYTES) { "Adjuntos demasiado grandes." }
    require(nodeAttachments.toSet().size == nodeAttachments.size && nodeAttachments.all { it.nodeId in nodeIds && it.attachmentId in attachmentIds }) { "Asociación de adjunto inválida." }
    require(projectAttachments.toSet().size == projectAttachments.size && projectAttachments.all { it.projectId in projectIds && it.attachmentId in attachmentIds }) { "Asociación de adjunto inválida." }
    require((nodeAttachments.map { it.attachmentId } + projectAttachments.map { it.attachmentId }).toSet() == attachmentIds) { "Adjunto sin propietario." }
    val nodeLinks = nodeAttachments.groupBy { it.nodeId }.mapValues { (_, rows) -> rows.mapTo(hashSetOf()) { it.attachmentId } }
    val projectLinks = projectAttachments.groupBy { it.projectId }.mapValues { (_, rows) -> rows.mapTo(hashSetOf()) { it.attachmentId } }
    nodes.forEach { node -> require(com.r0ybt.arachn0de.domain.model.AttachmentReferences.ids(node.description).all { id -> id in nodeLinks[node.id].orEmpty() }) { "Referencia de adjunto rota en tarea: ${node.title}" } }
    projects.forEach { project -> require(com.r0ybt.arachn0de.domain.model.AttachmentReferences.ids(project.description).all { id -> id in projectLinks[project.id].orEmpty() }) { "Referencia de adjunto rota en proyecto: ${project.name}" } }
    val technologyIds = unique(technologies.map { it.id })
    require(technologies.all { it.name.isNotBlank() }) { "Nombre de tecnología vacío." }
    val iconNames = technologies.mapNotNull { it.iconFile }.toSet()
    require(iconNames == imageNames("technology-icons") && iconNames.all { BackupLimits.avatarName.matches(it) }) { "Referencias de iconos de tecnología inválidas." }
    require(avatars.values.sumOf { it.size.toLong() } + technologyIcons.values.sumOf { it.size.toLong() } <= BackupLimits.TOTAL_AVATAR_BYTES) { "Avatares e iconos demasiado grandes." }
    technologyIcons.values.forEach { validateAvatar(it) }
    require(nodeTechnologies.map { it.nodeId to it.technologyId }.distinct().size == nodeTechnologies.size && nodeTechnologies.all { it.position >= 0 } && nodeTechnologies.all { it.nodeId in nodeIds && it.technologyId in technologyIds }) { "Asociación de tecnología con nodo inválida." }
    require(projectTechnologies.map { it.projectId to it.technologyId }.distinct().size == projectTechnologies.size && projectTechnologies.all { it.position >= 0 } && projectTechnologies.all { it.projectId in projectIds && it.technologyId in technologyIds }) { "Asociación de tecnología con proyecto inválida." }
    unique(projectPhotos.map { it.id })
    require(projectPhotos.mapNotNull { it.projectId }.toSet().size == projectPhotos.count { it.projectId != null } &&
        projectPhotos.mapNotNull { it.nodeId }.toSet().size == projectPhotos.count { it.nodeId != null }) { "Fotografías duplicadas por propietario." }
    projectPhotos.forEach { photo ->
        require((photo.projectId != null) != (photo.nodeId != null)) { "Fotografía sin propietario o con dos propietarios." }
        photo.projectId?.let { require(it in projectIds) { "Proyecto de fotografía ausente." } }
        photo.nodeId?.let { require(byId[it]?.purpose == "LAYER") { "Capa de fotografía ausente." } }
        com.r0ybt.arachn0de.domain.model.AvatarFraming(photo.photoZoom, photo.photoX, photo.photoY).validate()
    }
    val photoNames = projectPhotos.mapTo(hashSetOf()) { it.file }
    require(photoNames == imageNames("project-photos") && photoNames.all { BackupLimits.avatarName.matches(it) }) { "Referencias de fotografías inválidas." }
    require(avatars.values.sumOf { it.size.toLong() } + technologyIcons.values.sumOf { it.size.toLong() } +
        projectPhotoImages.values.sumOf { it.size.toLong() } <= BackupLimits.TOTAL_AVATAR_BYTES) { "Imágenes demasiado grandes." }
    projectPhotoImages.values.forEach { validateProjectPhoto(it) }
    val rootIds = unique(conversionRoots.map { it.id })
    require(conversionRoots.mapNotNull { it.projectId }.distinct().size == conversionRoots.count { it.projectId != null } &&
        conversionRoots.mapNotNull { it.nodeId }.distinct().size == conversionRoots.count { it.nodeId != null }) { "Raíces de conversión duplicadas." }
    conversionRoots.forEach { row ->
        require((row.projectId != null) != (row.nodeId != null)) { "Conversión sin propietario único." }
        require(row.projectIdentity.isNotBlank() && row.projectIdentity.length <= 256 && row.nodeIdentity.isNotBlank() && row.nodeIdentity.length <= 256)
        require(row.projectId == null || row.projectId in projectIds)
        require(row.nodeId == null || byId[row.nodeId]?.purpose == "LAYER")
        require(row.projectId == null || row.projectIdentity == row.projectId)
        require(row.nodeId == null || row.nodeIdentity == row.nodeId)
        com.r0ybt.arachn0de.domain.model.Priority.valueOf(row.priority)
        TaskTemporal.validateDates(row.startAt, row.dueAt)
        require(row.creationGroupId == null || (row.creationGroupId.isNotBlank() && row.creationGroupId.length <= 200))
    }
    val hiddenRoots = conversionRoots.filter { it.projectId != null }.mapTo(hashSetOf()) { it.id }
    require(conversionPeople.toSet().size == conversionPeople.size && conversionPeople.all { it.rootId in hiddenRoots && it.personId in personIds })
    require(conversionTags.toSet().size == conversionTags.size && conversionTags.all { it.rootId in hiddenRoots && it.tagId in tagIds })
    unique(conversionEvents.map { it.id } + nodeEvents.map { it.id })
    conversionEvents.forEach { require(it.rootId in hiddenRoots); com.r0ybt.arachn0de.domain.model.NodeEventType.valueOf(it.type) }
    unique(conversionWorkStates.map { it.nodeId })
    val conversionById = conversionRoots.associateBy { it.id }
    conversionWorkStates.forEach {
        val root = requireNotNull(conversionById[it.rootId]); val node = requireNotNull(byId[it.nodeId])
        require(root.projectId != null && root.sprintMode && node.projectId == root.projectId && node.parentId == null && node.purpose == "ACTION")
        com.r0ybt.arachn0de.domain.model.WorkState.valueOf(it.workState)
    }
    unique(nodeSortPreferences.map { it.context })
    val sortContexts = projects.mapTo(hashSetOf()) { "${it.id}:project-root" }
    nodes.forEach { sortContexts.add("${it.projectId}:${it.id}") }
    nodeSortPreferences.forEach {
        require(it.mode in listOf("MANUAL", "DUE_ASC", "DUE_DESC", "DUE_PRIORITY", "PRIORITY", "CREATED_NEWEST", "CREATED_OLDEST", "INHERIT"))
        require(it.context in sortContexts) { "Preferencia de orden sin propietario." }
    }
    require(imageFiles.map { it.key }.distinct().size == imageFiles.size) { "Imagen de manifiesto duplicada." }
    imageFiles.forEach {
        require(it.directory in setOf("avatars", "technology-icons", "project-photos") && BackupLimits.avatarName.matches(it.name) && java.util.UUID.fromString(it.name.substringBefore('.')).toString() == it.name.substringBefore('.'))
        require(it.byteSize in 1..imageByteLimit(it.directory).toLong() && it.sha256.matches(Regex("[a-f0-9]{64}")))
        val inline = when (it.directory) { "avatars" -> avatars; "technology-icons" -> technologyIcons; else -> projectPhotoImages }
        require(it.name !in inline) { "Imagen duplicada en JSON y manifiesto." }
    }
    require(imageFiles.sumOf { it.byteSize } + avatars.values.sumOf { it.size.toLong() } + technologyIcons.values.sumOf { it.size.toLong() } + projectPhotoImages.values.sumOf { it.size.toLong() } <= BackupLimits.TOTAL_STREAMED_IMAGE_BYTES) { "Imágenes por streaming demasiado grandes." }
    return ordered
}

internal fun imageByteLimit(directory:String)=if(directory=="project-photos") BackupLimits.PROJECT_PHOTO_BYTES else BackupLimits.AVATAR_BYTES
internal fun validateProjectPhoto(bytes:ByteArray)=validatePng(bytes,BackupLimits.PROJECT_PHOTO_BYTES,2048)
internal fun validateAvatar(bytes:ByteArray)=validatePng(bytes,BackupLimits.AVATAR_BYTES,512)
internal fun validatePrivateImage(directory:String,bytes:ByteArray) { if(directory=="project-photos") validateProjectPhoto(bytes) else validateAvatar(bytes) }
private fun validatePng(bytes: ByteArray,maxBytes:Int,maxDimension:Int) {
    require(bytes.size in 1..maxBytes) { "Avatar demasiado grande o vacío." }
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
    require(bounds.outWidth in 1..maxDimension && bounds.outHeight in 1..maxDimension) { "Dimensiones de avatar inválidas." }
    val image = requireNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Avatar dañado." }
    image.recycle()
}
