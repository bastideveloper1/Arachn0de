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
        val textBytes = data.tags.sumOf { it.name.toByteArray().size.toLong() + it.normalizedName.toByteArray().size } + data.projects.sumOf { it.name.toByteArray().size.toLong() + it.description.toByteArray().size } +
            data.nodes.sumOf { it.title.toByteArray().size.toLong() + it.description.toByteArray().size } +
            data.persons.sumOf { it.name.toByteArray().size.toLong() } + data.recurrenceRules.sumOf { it.title.toByteArray().size.toLong() + it.description.toByteArray().size }
        require(textBytes <= BackupLimits.PAYLOAD_BYTES / 2) { "El contenido supera el límite de backup v1." }
        fun obj(vararg values: Pair<String, Any?>) = JSONObject().apply { values.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) } }
        val json = obj(
            "dataVersion" to 4, "appVersion" to data.appVersion, "createdAt" to data.createdAt,
            "projects" to JSONArray(data.projects.map { obj("id" to it.id, "name" to it.name, "description" to it.description, "position" to it.position, "createdAt" to it.createdAt, "updatedAt" to it.updatedAt) }),
            "nodes" to JSONArray(data.nodes.map { obj("id" to it.id, "projectId" to it.projectId, "parentId" to it.parentId, "title" to it.title, "description" to it.description, "isCompleted" to it.isCompleted, "position" to it.position, "createdAt" to it.createdAt, "updatedAt" to it.updatedAt, "startAt" to it.startAt, "dueAt" to it.dueAt, "purpose" to it.purpose, "amountMinor" to it.amountMinor, "currencyCode" to it.currencyCode) }),
            "persons" to JSONArray(data.persons.map { obj("id" to it.id, "name" to it.name, "avatarFile" to it.avatarFile) }),
            "assignments" to JSONArray(data.assignments.map { obj("nodeId" to it.nodeId, "personId" to it.personId) }),
            "recurrenceRules" to JSONArray(data.recurrenceRules.map { obj("id" to it.id, "projectId" to it.projectId, "parentId" to it.parentId, "title" to it.title, "description" to it.description, "amountMinor" to it.amountMinor, "currencyCode" to it.currencyCode, "startDay" to it.startDay, "frequency" to it.frequency, "interval" to it.interval, "endDay" to it.endDay, "nextIndex" to it.nextIndex, "status" to it.status, "zoneId" to it.zoneId, "dueMinute" to it.dueMinute, "startOffsetMillis" to it.startOffsetMillis) }),
            "recurrenceOccurrences" to JSONArray(data.recurrenceOccurrences.map { obj("ruleId" to it.ruleId, "day" to it.day, "nodeId" to it.nodeId) }),
            "recurrenceAssignments" to JSONArray(data.recurrenceAssignments.map { obj("ruleId" to it.ruleId, "personId" to it.personId) }),
            "tags" to JSONArray(data.tags.map { obj("id" to it.id, "name" to it.name, "normalizedName" to it.normalizedName) }),
            "nodeTags" to JSONArray(data.nodeTags.map { obj("nodeId" to it.nodeId, "tagId" to it.tagId) }),
            "recurrenceTags" to JSONArray(data.recurrenceTags.map { obj("ruleId" to it.ruleId, "tagId" to it.tagId) }),
            "nodeEvents" to JSONArray(data.nodeEvents.map { obj("id" to it.id, "nodeId" to it.nodeId, "type" to it.type, "occurredAt" to it.occurredAt) }),
            "avatars" to JSONArray(data.avatars.toSortedMap().map { (name, bytes) -> obj("name" to name, "png" to Base64.encodeToString(bytes, Base64.NO_WRAP)) }),
        )
        return json.toString().toByteArray(Charsets.UTF_8).also {
            require(it.size <= BackupLimits.PAYLOAD_BYTES) { "El backup supera el límite de 16 MiB de v1." }
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
        require(version in 1L..4L) { "Versión de datos no compatible." }
        val baseFields = arrayOf("dataVersion", "appVersion", "createdAt", "projects", "nodes", "persons", "assignments", "avatars")
        root.fields(*(baseFields + (if (version >= 2L) arrayOf("recurrenceRules", "recurrenceOccurrences", "recurrenceAssignments") else emptyArray()) + (if (version >= 3L) arrayOf("tags", "nodeTags", "recurrenceTags") else emptyArray()) + (if (version >= 4L) arrayOf("nodeEvents") else emptyArray())))
        require(version in 1L..4L) { "Versión de datos no compatible." }
        val projects = root.records("projects").map { row ->
            row.fields("id", "name", "description", "position", "createdAt", "updatedAt")
            ProjectEntity(row.string("id"), row.string("name"), row.string("description"), row.position(), row.integer("createdAt"), row.integer("updatedAt"))
        }
        val nodes = root.records("nodes").map { row ->
            row.fields("id", "projectId", "parentId", "title", "description", "isCompleted", "position", "createdAt", "updatedAt", "startAt", "dueAt", "purpose", "amountMinor", "currencyCode")
            NodeEntity(row.string("id"), row.string("projectId"), row.nullableString("parentId"), row.string("title"), row.string("description"), row.get("isCompleted") as? Boolean ?: error("Completado inválido."), row.position(), row.integer("createdAt"), row.integer("updatedAt"), row.nullableLong("startAt"), row.nullableLong("dueAt"), row.string("purpose"), row.nullableLong("amountMinor"), row.nullableString("currencyCode"))
        }
        val persons = root.records("persons").map { row ->
            row.fields("id", "name", "avatarFile")
            PersonEntity(row.string("id"), row.string("name"), row.nullableString("avatarFile"))
        }
        val assignments = root.records("assignments").map { row ->
            row.fields("nodeId", "personId")
            NodePersonEntity(row.string("nodeId"), row.string("personId"))
        }
        val rules = if (version == 1L) emptyList() else root.records("recurrenceRules").map { row ->
            row.fields("id", "projectId", "parentId", "title", "description", "amountMinor", "currencyCode", "startDay", "frequency", "interval", "endDay", "nextIndex", "status", "zoneId", "dueMinute", "startOffsetMillis")
            RecurrenceRuleEntity(row.string("id"), row.string("projectId"), row.nullableString("parentId"), row.string("title"), row.string("description"), row.nullableLong("amountMinor"), row.nullableString("currencyCode"), row.integer("startDay"), row.string("frequency"), row.intValue("interval"), row.nullableLong("endDay"), row.integer("nextIndex"), row.string("status"), row.string("zoneId"), row.intValue("dueMinute"), row.nullableLong("startOffsetMillis"))
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
        var avatarBytes = 0L
        val avatars = linkedMapOf<String, ByteArray>()
        root.records("avatars").forEach { row ->
            row.fields("name", "png")
            val name = row.string("name")
            require(name !in avatars && BackupLimits.avatarName.matches(name)) { "Nombre de avatar inválido o repetido." }
            val png = row.string("png")
            require(png.length <= (BackupLimits.AVATAR_BYTES + 2) / 3 * 4) { "Avatar demasiado grande." }
            val decoded = Base64.decode(png, Base64.NO_WRAP)
            require(Base64.encodeToString(decoded, Base64.NO_WRAP) == png) { "Codificación de avatar inválida." }
            avatarBytes += decoded.size
            require(avatarBytes <= BackupLimits.TOTAL_AVATAR_BYTES) { "Avatares demasiado grandes." }
            avatars[name] = decoded
        }
        return BackupData(root.string("appVersion"), root.integer("createdAt"), projects, nodes, persons, assignments, avatars, rules, occurrences, recurrenceAssignments, tags, nodeTags, recurrenceTags, nodeEvents).also { it.validate() }
    }

    private fun readValue(reader: JsonReader, depth: Int): Any {
        require(depth <= 8) { "Estructura demasiado profunda." }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JSONObject().apply {
                reader.beginObject()
                while (reader.hasNext()) {
                    require(length() < 32) { "Demasiados campos." }
                    val key = reader.nextName()
                    require(!has(key)) { "Campo duplicado." }
                    put(key, readValue(reader, depth + 1))
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
            JsonToken.NUMBER -> reader.nextString().toLongOrNull() ?: error("Número entero inválido o fuera de rango.")
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
    private fun JSONObject.position(): Int = intValue("position")
    private fun JSONObject.records(key: String): List<JSONObject> {
        val array = get(key) as? JSONArray ?: error("Lista inválida: $key")
        return (0 until array.length()).map { array.get(it) as? JSONObject ?: error("Registro inválido: $key") }
    }
}
