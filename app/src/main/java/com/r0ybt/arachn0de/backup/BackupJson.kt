package com.r0ybt.arachn0de.backup

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import com.r0ybt.arachn0de.data.local.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Explicit field mapping is versioned independently of Room and of the outer container. */
internal object BackupJson {
    fun encode(data: BackupData): ByteArray {
        data.validate()
        // Bound user text before constructing JSON, which otherwise could exhaust the heap.
        val textBytes = data.technologies.sumOf { it.name.toByteArray().size.toLong() + it.id.toByteArray().size + (it.iconFile?.toByteArray()?.size ?: 0) } + data.attachmentFiles.sumOf { it.originalName.toByteArray().size.toLong() + it.storageName.toByteArray().size + it.id.toByteArray().size + it.mimeType.toByteArray().size + it.sha256.toByteArray().size } + data.tags.sumOf { it.name.toByteArray().size.toLong() + it.normalizedName.toByteArray().size } + data.projects.sumOf { it.name.toByteArray().size.toLong() + it.description.toByteArray().size } +
            data.nodes.sumOf { it.title.toByteArray().size.toLong() + it.description.toByteArray().size } +
            data.persons.sumOf { it.name.toByteArray().size.toLong() } + data.recurrenceRules.sumOf { it.title.toByteArray().size.toLong() + it.description.toByteArray().size } +
            data.creationDefaults.sumOf { it.id.toByteArray().size.toLong() + (it.projectId?.toByteArray()?.size ?: 0) + (it.nodeId?.toByteArray()?.size ?: 0) } +
            data.defaultsTags.sumOf { it.defaultsId.toByteArray().size.toLong() + it.tagId.toByteArray().size } +
            data.defaultsPeople.sumOf { it.defaultsId.toByteArray().size.toLong() + it.personId.toByteArray().size }
        require(textBytes <= BackupLimits.PAYLOAD_BYTES / 2) { "El contenido supera el límite de metadatos del backup." }
        fun obj(vararg values: Pair<String, Any?>) = JSONObject().apply { values.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) } }
        val json = obj(
            "dataVersion" to 12, "appVersion" to data.appVersion, "createdAt" to data.createdAt,
            "projects" to JSONArray(data.projects.map { obj("id" to it.id, "name" to it.name, "description" to it.description, "position" to it.position, "createdAt" to it.createdAt, "updatedAt" to it.updatedAt) }),
            "nodes" to JSONArray(data.nodes.map { obj("id" to it.id, "projectId" to it.projectId, "parentId" to it.parentId, "title" to it.title, "description" to it.description, "isCompleted" to it.isCompleted, "position" to it.position, "createdAt" to it.createdAt, "updatedAt" to it.updatedAt, "startAt" to it.startAt, "dueAt" to it.dueAt, "purpose" to it.purpose, "amountMinor" to it.amountMinor, "currencyCode" to it.currencyCode, "priority" to it.priority, "creationGroupId" to it.creationGroupId, "sprintMode" to it.sprintMode, "workState" to it.workState) }),
            "persons" to JSONArray(data.persons.map { obj("id" to it.id, "name" to it.name, "avatarFile" to it.avatarFile, "avatarZoom" to it.avatarZoom, "avatarX" to it.avatarX, "avatarY" to it.avatarY) }),
            "assignments" to JSONArray(data.assignments.map { obj("nodeId" to it.nodeId, "personId" to it.personId) }),
            "recurrenceRules" to JSONArray(data.recurrenceRules.map { obj("id" to it.id, "projectId" to it.projectId, "parentId" to it.parentId, "title" to it.title, "description" to it.description, "amountMinor" to it.amountMinor, "currencyCode" to it.currencyCode, "startDay" to it.startDay, "frequency" to it.frequency, "interval" to it.interval, "endDay" to it.endDay, "nextIndex" to it.nextIndex, "status" to it.status, "zoneId" to it.zoneId, "dueMinute" to it.dueMinute, "startOffsetMillis" to it.startOffsetMillis, "priority" to it.priority) }),
            "recurrenceOccurrences" to JSONArray(data.recurrenceOccurrences.map { obj("ruleId" to it.ruleId, "day" to it.day, "nodeId" to it.nodeId) }),
            "recurrenceAssignments" to JSONArray(data.recurrenceAssignments.map { obj("ruleId" to it.ruleId, "personId" to it.personId) }),
            "tags" to JSONArray(data.tags.map { obj("id" to it.id, "name" to it.name, "normalizedName" to it.normalizedName) }),
            "nodeTags" to JSONArray(data.nodeTags.map { obj("nodeId" to it.nodeId, "tagId" to it.tagId) }),
            "recurrenceTags" to JSONArray(data.recurrenceTags.map { obj("ruleId" to it.ruleId, "tagId" to it.tagId) }),
            "nodeEvents" to JSONArray(data.nodeEvents.map { obj("id" to it.id, "nodeId" to it.nodeId, "type" to it.type, "occurredAt" to it.occurredAt) }),
            "creationDefaults" to JSONArray(data.creationDefaults.map { obj("id" to it.id,"projectId" to it.projectId,"nodeId" to it.nodeId,
                "purpose" to it.purpose,"obligation" to it.obligation,"currency" to it.currency,"priority" to it.priority,
                "tagsOverride" to it.tagsOverride,"peopleOverride" to it.peopleOverride,"startRule" to it.startRule,"startNumber" to it.startNumber,
                "startMinute" to it.startMinute,"dueRule" to it.dueRule,"dueNumber" to it.dueNumber,"dueMinute" to it.dueMinute) }),
            "defaultsTags" to JSONArray(data.defaultsTags.map { obj("defaultsId" to it.defaultsId,"tagId" to it.tagId) }),
            "defaultsPeople" to JSONArray(data.defaultsPeople.map { obj("defaultsId" to it.defaultsId,"personId" to it.personId) }),
            "attachmentFiles" to JSONArray(data.attachmentFiles.map { obj("id" to it.id, "storageName" to it.storageName, "originalName" to it.originalName, "mimeType" to it.mimeType, "byteSize" to it.byteSize, "width" to it.width, "height" to it.height, "sha256" to it.sha256, "createdAt" to it.createdAt, "lifecycleState" to it.lifecycleState) }),
            "nodeAttachments" to JSONArray(data.nodeAttachments.map { obj("nodeId" to it.nodeId, "attachmentId" to it.attachmentId) }),
            "projectAttachments" to JSONArray(data.projectAttachments.map { obj("projectId" to it.projectId, "attachmentId" to it.attachmentId) }),
            "technologies" to JSONArray(data.technologies.map { obj("id" to it.id, "name" to it.name, "iconFile" to it.iconFile) }),
            "nodeTechnologies" to JSONArray(data.nodeTechnologies.map { obj("nodeId" to it.nodeId, "technologyId" to it.technologyId) }),
            "projectTechnologies" to JSONArray(data.projectTechnologies.map { obj("projectId" to it.projectId, "technologyId" to it.technologyId) }),
            "technologyIcons" to JSONArray(data.technologyIcons.toSortedMap().map { (name, bytes) -> obj("name" to name, "png" to Base64.encodeToString(bytes, Base64.NO_WRAP)) }),
            "avatars" to JSONArray(data.avatars.toSortedMap().map { (name, bytes) -> obj("name" to name, "png" to Base64.encodeToString(bytes, Base64.NO_WRAP)) }),
        )
        return json.toString().toByteArray(Charsets.UTF_8).also {
            require(it.size <= BackupLimits.PAYLOAD_BYTES) { "El backup supera el límite de 16 MiB de metadatos." }
        }
    }

    fun decode(bytes: ByteArray): BackupData {
        require(bytes.size <= BackupLimits.PAYLOAD_BYTES)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val root = JsonReader(StringReader(text)).use { reader ->
            val value = readValue(reader, 0)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Contenido adicional." }
            value as? JSONObject ?: error("Backup no es un objeto.")
        }
        val version = root.integer("dataVersion")
        require(version in 1L..12L) { "Versión de datos no compatible." }
        val baseFields = arrayOf("dataVersion", "appVersion", "createdAt", "projects", "nodes", "persons", "assignments", "avatars")
        root.fields(*(baseFields + (if (version >= 2L) arrayOf("recurrenceRules", "recurrenceOccurrences", "recurrenceAssignments") else emptyArray()) + (if (version >= 3L) arrayOf("tags", "nodeTags", "recurrenceTags") else emptyArray()) + (if (version >= 4L) arrayOf("nodeEvents") else emptyArray()) + (if (version >= 7L) arrayOf("creationDefaults","defaultsTags","defaultsPeople") else emptyArray()) + (if (version >= 10L) arrayOf("attachmentFiles", "nodeAttachments", "projectAttachments") else emptyArray()) + (if (version >= 11L) arrayOf("technologies", "nodeTechnologies", "projectTechnologies", "technologyIcons") else emptyArray())))
        val projects = root.records("projects").map { row ->
            row.fields("id", "name", "description", "position", "createdAt", "updatedAt")
            ProjectEntity(row.string("id"), row.string("name"), row.string("description"), row.position(), row.integer("createdAt"), row.integer("updatedAt"))
        }
        val rawNodes = root.records("nodes").map { row ->
            row.fields("id", "projectId", "parentId", "title", "description", "isCompleted", "position", "createdAt", "updatedAt", "startAt", "dueAt", "purpose", "amountMinor", "currencyCode", *(if (version >= 5) arrayOf("priority") else emptyArray()), *(if (version >= 6) arrayOf("creationGroupId") else emptyArray()), *(if (version >= 9) arrayOf("sprintMode","workState") else emptyArray()))
            NodeEntity(row.string("id"), row.string("projectId"), row.nullableString("parentId"), row.string("title"), row.string("description"), row.get("isCompleted") as? Boolean ?: error("Completado inválido."), row.position(), row.integer("createdAt"), row.integer("updatedAt"), row.nullableLong("startAt"), row.nullableLong("dueAt"), row.string("purpose"), row.nullableLong("amountMinor"), row.nullableString("currencyCode"), if (version >= 5) row.string("priority") else "NONE", if (version >= 6) row.nullableString("creationGroupId") else null, if (version >= 9) row.boolean("sprintMode") else false, if (version >= 9) row.nullableString("workState") else null)
        }
        val legacyParents=rawNodes.mapNotNullTo(hashSetOf()) { it.parentId }
        if(version<8) require(rawNodes.all { it.purpose in listOf("ACTION","NOTE") }) { "Propósito desconocido en backup antiguo." }
        val nodes=if(version<8) rawNodes.map { if(it.id in legacyParents) {
            require(it.purpose=="ACTION" && !it.isCompleted && it.amountMinor==null) { "Capa antigua inválida." }
            it.copy(purpose="LAYER")
        } else it } else rawNodes
        val persons = root.records("persons").map { row ->
            row.fields(*(arrayOf("id", "name", "avatarFile") + if (version >= 12) arrayOf("avatarZoom", "avatarX", "avatarY") else emptyArray()))
            PersonEntity(row.string("id"), row.string("name"), row.nullableString("avatarFile"),
                if (version >= 12) row.finiteFloat("avatarZoom") else 1f,
                if (version >= 12) row.finiteFloat("avatarX") else 0f,
                if (version >= 12) row.finiteFloat("avatarY") else 0f)
        }
        val assignments = root.records("assignments").map { row ->
            row.fields("nodeId", "personId")
            NodePersonEntity(row.string("nodeId"), row.string("personId"))
        }
        val rules = if (version == 1L) emptyList() else root.records("recurrenceRules").map { row ->
            row.fields("id", "projectId", "parentId", "title", "description", "amountMinor", "currencyCode", "startDay", "frequency", "interval", "endDay", "nextIndex", "status", "zoneId", "dueMinute", "startOffsetMillis", *(if (version >= 5) arrayOf("priority") else emptyArray()))
            RecurrenceRuleEntity(row.string("id"), row.string("projectId"), row.nullableString("parentId"), row.string("title"), row.string("description"), row.nullableLong("amountMinor"), row.nullableString("currencyCode"), row.integer("startDay"), row.string("frequency"), row.intValue("interval"), row.nullableLong("endDay"), row.integer("nextIndex"), row.string("status"), row.string("zoneId"), row.intValue("dueMinute"), row.nullableLong("startOffsetMillis"), if (version >= 5) row.string("priority") else "NONE")
        }
        val occurrences = if (version == 1L) emptyList() else root.records("recurrenceOccurrences").map { row ->
            row.fields("ruleId", "day", "nodeId")
            RecurrenceOccurrenceEntity(row.string("ruleId"), row.integer("day"), row.string("nodeId"))
        }
        val recurrenceAssignments = if (version == 1L) emptyList() else root.records("recurrenceAssignments").map { row ->
            row.fields("ruleId", "personId")
            RecurrencePersonEntity(row.string("ruleId"), row.string("personId"))
        }
        val tags = if (version < 3L) emptyList() else root.records("tags").map {
            it.fields("id", "name", "normalizedName")
            TagEntity(it.string("id"), it.string("name"), it.string("normalizedName"))
        }
        val nodeTags = if (version < 3L) emptyList() else root.records("nodeTags").map {
            it.fields("nodeId", "tagId"); NodeTagEntity(it.string("nodeId"), it.string("tagId"))
        }
        val recurrenceTags = if (version < 3L) emptyList() else root.records("recurrenceTags").map {
            it.fields("ruleId", "tagId"); RecurrenceTagEntity(it.string("ruleId"), it.string("tagId"))
        }
        val nodeEvents = if (version < 4L) emptyList() else root.records("nodeEvents").map {
            it.fields("id", "nodeId", "type", "occurredAt")
            NodeEventEntity(it.string("id"), it.string("nodeId"), it.string("type"), it.integer("occurredAt"))
        }
        val defaults=if(version<7) emptyList() else root.records("creationDefaults").map { row ->
            row.fields("id","projectId","nodeId","purpose","obligation","currency","priority","tagsOverride","peopleOverride","startRule","startNumber","startMinute","dueRule","dueNumber","dueMinute")
            CreationDefaultsEntity(row.string("id"),row.nullableString("projectId"),row.nullableString("nodeId"),row.nullableString("purpose"),
                if(row.get("obligation")==JSONObject.NULL) null else row.boolean("obligation"),row.nullableString("currency"),row.nullableString("priority"),
                row.boolean("tagsOverride"),row.boolean("peopleOverride"),row.nullableString("startRule"),row.nullableInt("startNumber"),row.nullableInt("startMinute"),
                row.nullableString("dueRule"),row.nullableInt("dueNumber"),row.nullableInt("dueMinute"))
        }
        val defaultsTags=if(version<7) emptyList() else root.records("defaultsTags").map { it.fields("defaultsId","tagId");CreationDefaultsTagEntity(it.string("defaultsId"),it.string("tagId")) }
        val defaultsPeople=if(version<7) emptyList() else root.records("defaultsPeople").map { it.fields("defaultsId","personId");CreationDefaultsPersonEntity(it.string("defaultsId"),it.string("personId")) }
        val attachmentFiles = if (version < 10) emptyList() else root.records("attachmentFiles").map {
            it.fields("id", "storageName", "originalName", "mimeType", "byteSize", "width", "height", "sha256", "createdAt", "lifecycleState")
            AttachmentFileEntity(it.string("id"), it.string("storageName"), it.string("originalName"), it.string("mimeType"), it.integer("byteSize"), it.intValue("width"), it.intValue("height"), it.string("sha256"), it.integer("createdAt"), it.string("lifecycleState"))
        }
        val nodeAttachments = if (version < 10) emptyList() else root.records("nodeAttachments").map { it.fields("nodeId", "attachmentId"); NodeAttachmentEntity(it.string("nodeId"), it.string("attachmentId")) }
        val projectAttachments = if (version < 10) emptyList() else root.records("projectAttachments").map { it.fields("projectId", "attachmentId"); ProjectAttachmentEntity(it.string("projectId"), it.string("attachmentId")) }
        val technologies = if (version < 11) emptyList() else root.records("technologies").map {
            it.fields("id", "name", "iconFile"); TechnologyEntity(it.string("id"), it.string("name"), it.nullableString("iconFile"))
        }
        val nodeTechnologies = if (version < 11) emptyList() else root.records("nodeTechnologies").map {
            it.fields("nodeId", "technologyId"); NodeTechnologyEntity(it.string("nodeId"), it.string("technologyId"))
        }
        val projectTechnologies = if (version < 11) emptyList() else root.records("projectTechnologies").map {
            it.fields("projectId", "technologyId"); ProjectTechnologyEntity(it.string("projectId"), it.string("technologyId"))
        }
        var imageBytes = 0L
        fun pngs(key: String): Map<String, ByteArray> {
            val images = linkedMapOf<String, ByteArray>()
            root.records(key).forEach { row ->
                row.fields("name", "png")
                val name = row.string("name")
                require(name !in images && BackupLimits.avatarName.matches(name)) { "Nombre de imagen inválido o repetido." }
                val png = row.string("png")
                require(png.length <= (BackupLimits.AVATAR_BYTES + 2) / 3 * 4) { "Imagen demasiado grande." }
                val decoded = Base64.decode(png, Base64.NO_WRAP)
                require(Base64.encodeToString(decoded, Base64.NO_WRAP) == png) { "Codificación de imagen inválida." }
                imageBytes += decoded.size
                require(imageBytes <= BackupLimits.TOTAL_AVATAR_BYTES) { "Avatares e iconos demasiado grandes." }
                images[name] = decoded
            }
            return images
        }
        val avatars = pngs("avatars")
        val technologyIcons = if (version < 11) emptyMap() else pngs("technologyIcons")
        return BackupData(root.string("appVersion"), root.integer("createdAt"), projects, nodes, persons, assignments, avatars, rules, occurrences, recurrenceAssignments, tags, nodeTags, recurrenceTags, nodeEvents, defaults, defaultsTags, defaultsPeople, attachmentFiles, nodeAttachments, projectAttachments, technologies = technologies, nodeTechnologies = nodeTechnologies, projectTechnologies = projectTechnologies, technologyIcons = technologyIcons).also { it.validate() }
    }

    private fun readValue(reader: JsonReader, depth: Int, framingNumber: Boolean = false): Any {
        require(depth <= 8) { "Estructura demasiado profunda." }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JSONObject().apply {
                reader.beginObject()
                while (reader.hasNext()) {
                    require(length() < 32) { "Demasiados campos." }
                    val key = reader.nextName()
                    require(!has(key)) { "Campo duplicado." }
                    put(key, readValue(reader, depth + 1, key in setOf("avatarZoom", "avatarX", "avatarY")))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JSONArray().apply {
                reader.beginArray()
                while (reader.hasNext()) {
                    require(length() < BackupLimits.RECORDS) { "Demasiados registros." }
                    put(readValue(reader, depth + 1))
                }
                reader.endArray()
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> {
                val token = reader.nextString()
                if (framingNumber) token.toDoubleOrNull()?.also { require(it.isFinite()) } ?: error("Encuadre numérico inválido.")
                else token.toLongOrNull() ?: error("Número entero inválido o fuera de rango.")
            }
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); JSONObject.NULL }
            else -> error("Estructura JSON inválida.")
        }
    }

    private fun JSONObject.fields(vararg names: String) { require(keys().asSequence().toSet() == names.toSet()) { "Campos ausentes o desconocidos." } }
    private fun JSONObject.string(key: String) = get(key) as? String ?: error("Texto inválido: $key")
    private fun JSONObject.integer(key: String) = get(key) as? Long ?: error("Entero inválido: $key")
    private fun JSONObject.nullableLong(key: String) = if (get(key) == JSONObject.NULL) null else integer(key)
    private fun JSONObject.nullableString(key: String) = if (get(key) == JSONObject.NULL) null else string(key)
    private fun JSONObject.intValue(key: String): Int = integer(key).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
    private fun JSONObject.finiteFloat(key: String): Float {
        val value = get(key) as? Number ?: error("Número inválido: $key")
        return value.toFloat().also { require(it.isFinite()) }
    }
    private fun JSONObject.boolean(key:String):Boolean = get(key) as? Boolean ?: error("Boolean inválido: $key")
    private fun JSONObject.nullableInt(key:String):Int? = if(get(key)==JSONObject.NULL) null else intValue(key)
    private fun JSONObject.position(): Int = intValue("position")
    private fun JSONObject.records(key: String): List<JSONObject> {
        val array = get(key) as? JSONArray ?: error("Lista inválida: $key")
        return (0 until array.length()).map { array.get(it) as? JSONObject ?: error("Registro inválido: $key") }
    }
}
