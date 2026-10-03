package com.r0ybt.arachn0de.ui.state

import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver

/** Only user input and destination identity belong in saved state, never Room entities. */
internal class EditorDraft(
    val id: String?,
    val parentId: String?,
    title: String,
    description: String,
    val creationId: String = id ?: UUID.randomUUID().toString(),
    startAt: Long? = null,
    dueAt: Long? = null,
    purpose: com.r0ybt.arachn0de.domain.model.NodePurpose = com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION,
    obligation: com.r0ybt.arachn0de.domain.model.Obligation? = null,
    val moneyLocaleTag: String = java.util.Locale.getDefault().toLanguageTag(),
    val hadObligation: Boolean = obligation != null,
    priority: com.r0ybt.arachn0de.domain.model.Priority = com.r0ybt.arachn0de.domain.model.Priority.NONE,
) {
    var creationGroupId: String? = null
    private var sharedBaseline: List<String>? = null
    private fun sharedValues(): List<String> {
        val money = obligation()
        return listOf(description.trim(), money?.amountMinor?.toString().orEmpty(), money?.currencyCode.orEmpty(), priority.name,
            tagIds.sorted().joinToString("\u001f"), responsibleIds.sorted().joinToString("\u001f"))
    }
    fun captureSharedBaseline() { sharedBaseline = sharedValues() }
    fun sharedPatch(): com.r0ybt.arachn0de.domain.model.SharedNodePatch {
        val before = sharedBaseline ?: return com.r0ybt.arachn0de.domain.model.SharedNodePatch()
        val after = sharedValues()
        return com.r0ybt.arachn0de.domain.model.SharedNodePatch(
            description = after[0].takeIf { it != before[0] },
            amount = if(after[1] != before[1]) com.r0ybt.arachn0de.domain.model.FieldChange(after[1].toLongOrNull()) else null,
            currency = if(after[2] != before[2]) com.r0ybt.arachn0de.domain.model.FieldChange(after[2].ifEmpty { null }) else null,
            priority = priority.takeIf { after[3] != before[3] },
            tags = tagIds.toSet().takeIf { after[4] != before[4] },
            responsibleIds = responsibleIds.toSet().takeIf { after[5] != before[5] })
    }
    var batchEnabled by mutableStateOf(false)
    var batchQuantity by mutableStateOf("1")
    var batchNumbering by mutableStateOf(com.r0ybt.arachn0de.domain.model.NumberingMode.NONE)
    var batchStartNumber by mutableStateOf("1")
    var batchTemporal by mutableStateOf(com.r0ybt.arachn0de.domain.model.BatchTemporalRule.NONE)
    var latentFrequency by mutableStateOf("DAILY")
    var startEnabled by mutableStateOf(startAt != null)
    var dueEnabled by mutableStateOf(dueAt != null)
    val activeStart: Long? get() = startAt.takeIf { startEnabled }
    val activeDue: Long? get() = dueAt.takeIf { dueEnabled }
    fun toggleRecurrence(enabled: Boolean) {
        if (!enabled) { if (recurrenceFrequency != "NONE") latentFrequency = recurrenceFrequency; recurrenceFrequency = "NONE" }
        else {
            recurrenceFrequency = latentFrequency
            if (recurrenceStart.isBlank()) recurrenceStart = com.r0ybt.arachn0de.domain.model.RecurrenceSchedule.format(
                com.r0ybt.arachn0de.domain.model.RecurrenceSchedule.localDay(activeDue ?: System.currentTimeMillis(), java.util.TimeZone.getDefault().id))
        }
    }
    fun batchDraft() = NodeBatchDraft(parentId, creationId, moneyLocaleTag).also {
        it.baseName = title; it.description = description; it.quantity = batchQuantity
        it.numberingMode = batchNumbering; it.startNumber = batchStartNumber; it.purpose = purpose
        it.temporalRule = if (purpose == com.r0ybt.arachn0de.domain.model.NodePurpose.NOTE) com.r0ybt.arachn0de.domain.model.BatchTemporalRule.NONE else batchTemporal
        it.responsibleIds = responsibleIds; it.dates.tagIds = tagIds; it.dates.priority = priority
        it.dates.financialEnabled = financialEnabled; it.dates.amountText = amountText; it.dates.currencyCode = currencyCode
        it.dates.dueAt = activeDue
    }
    val hasWork: Boolean get() = title.isNotBlank() || description.isNotBlank() || financialEnabled || amountText.isNotBlank() ||
        startAt != null || dueAt != null || recurrenceFrequency != "NONE" || recurrenceStart.isNotBlank() || batchEnabled ||
        batchQuantity != "1" || batchStartNumber != "1" || batchNumbering != com.r0ybt.arachn0de.domain.model.NumberingMode.NONE ||
        batchTemporal != com.r0ybt.arachn0de.domain.model.BatchTemporalRule.NONE || recurrenceInterval != "1" || recurrenceEnd.isNotBlank() || startEnabled || dueEnabled || priority != com.r0ybt.arachn0de.domain.model.Priority.NONE || tagIds.isNotEmpty() || responsibleIds.isNotEmpty()
    var priority by mutableStateOf(priority)
    var tagIds by mutableStateOf(emptyList<String>())
    var responsibleIds by mutableStateOf(emptyList<String>())
    var showResponsible by mutableStateOf(false)
    var financialEnabled by mutableStateOf(obligation != null)
    var amountText by mutableStateOf(obligation?.let { com.r0ybt.arachn0de.domain.model.Money.input(it.amountMinor, it.currencyCode, java.util.Locale.forLanguageTag(moneyLocaleTag)) }.orEmpty())
    var currencyCode by mutableStateOf(obligation?.currencyCode ?: "CLP")
    var financialRemovalConfirmed by mutableStateOf(false)
    fun obligation(): com.r0ybt.arachn0de.domain.model.Obligation? = if (!financialEnabled || purpose != com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION) null else
        com.r0ybt.arachn0de.domain.model.Obligation(com.r0ybt.arachn0de.domain.model.Money.parse(amountText, currencyCode, java.util.Locale.forLanguageTag(moneyLocaleTag)), currencyCode)

    var recurrenceFrequency by mutableStateOf("NONE")
    var recurrenceInterval by mutableStateOf("1")
    var recurrenceStart by mutableStateOf("")
    var recurrenceEnd by mutableStateOf("")
    fun recurrenceRule(projectId: String): com.r0ybt.arachn0de.data.local.RecurrenceRuleEntity? {
        if (recurrenceFrequency == "NONE" || purpose != com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION) return null
        require(activeStart == null || activeDue != null) { "Configura Vence para repetir la fecha de inicio." }
        val schedule = com.r0ybt.arachn0de.domain.model.RecurrenceSchedule
        val zone = java.util.TimeZone.getDefault().id
        val start = schedule.parse(recurrenceStart)
        val end = recurrenceEnd.takeIf { it.isNotBlank() }?.let(schedule::parse)
        val interval = requireNotNull(recurrenceInterval.toIntOrNull()) { "Intervalo inválido." }
        val time = java.util.Calendar.getInstance().apply { timeInMillis = activeDue ?: schedule.timestamp(start, zone, 0) }
        val money = obligation()
        return com.r0ybt.arachn0de.data.local.RecurrenceRuleEntity(creationId, projectId, parentId, title.trim(), description.trim(),
            money?.amountMinor, money?.currencyCode, start, recurrenceFrequency, interval, end, 0, "ACTIVE", zone,
            time.get(java.util.Calendar.HOUR_OF_DAY) * 60 + time.get(java.util.Calendar.MINUTE),
            if (activeStart != null && activeDue != null) Math.subtractExact(activeDue!!, activeStart!!) else null, priority = priority.name).also {
                com.r0ybt.arachn0de.data.repository.RecurrenceRepository.validate(it)
            }
    }

    var purpose by mutableStateOf(purpose)
    var title by mutableStateOf(title)
    var description by mutableStateOf(description)
    private var startValue by mutableStateOf(startAt)
    private var dueValue by mutableStateOf(dueAt)
    var startAt: Long?
        get() = startValue
        set(value) { startValue = value; if (value != null) startEnabled = true }
    var dueAt: Long?
        get() = dueValue
        set(value) { dueValue = value; if (value != null) dueEnabled = true }

    companion object {
        val Saver = listSaver<EditorDraft?, String>(
            save = {
                if (it == null) emptyList()
                else listOf("__shared_v1", it.creationGroupId.orEmpty(), (it.sharedBaseline?.size ?: 0).toString()) + it.sharedBaseline.orEmpty() + listOf("__ux_v1", it.batchEnabled.toString(), it.batchQuantity, it.batchNumbering.name, it.batchStartNumber, it.batchTemporal.name, it.latentFrequency, it.startEnabled.toString(), it.dueEnabled.toString()) + listOf(it.id.orEmpty(), it.parentId.orEmpty(), it.title, it.description, it.creationId, it.startAt?.toString().orEmpty(), it.dueAt?.toString().orEmpty(), it.purpose.name, it.financialEnabled.toString(), it.amountText, it.currencyCode, it.moneyLocaleTag, it.hadObligation.toString(), it.financialRemovalConfirmed.toString(), it.showResponsible.toString()) + listOf("__recurrence_v1", it.recurrenceFrequency, it.recurrenceInterval, it.recurrenceStart, it.recurrenceEnd) + listOf("__priority_v1", it.priority.name) + listOf("__tags_v1", it.tagIds.size.toString()) + it.tagIds + it.responsibleIds
            },
            restore = { encoded ->
                val groupMeta = encoded.firstOrNull() == "__shared_v1"
                val baselineSize = if(groupMeta) encoded[2].toInt() else 0
                val saved = if(groupMeta) encoded.drop(3 + baselineSize) else encoded
                val ux = saved.takeIf { it.firstOrNull() == "__ux_v1" }?.take(9)
                val stored = if (ux != null) saved.drop(9) else saved
                val priority = if (stored.getOrNull(20) == "__priority_v1") com.r0ybt.arachn0de.domain.model.Priority.valueOf(stored[21]) else com.r0ybt.arachn0de.domain.model.Priority.NONE
                val it = if (stored.getOrNull(20) == "__priority_v1") stored.take(20) + stored.drop(22) else stored
                if (it.isEmpty()) null
                else EditorDraft(it[0].ifEmpty { null }, it[1].ifEmpty { null }, it[2], it[3], it[4], it.getOrNull(5)?.toLongOrNull(), it.getOrNull(6)?.toLongOrNull(), it.getOrNull(7)?.let(com.r0ybt.arachn0de.domain.model.NodePurpose::valueOf) ?: com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION,
                    moneyLocaleTag = it.getOrNull(11) ?: java.util.Locale.getDefault().toLanguageTag(), hadObligation = it.getOrNull(12)?.toBoolean() ?: false).apply {
                    creationGroupId = if(groupMeta) encoded[1].ifEmpty { null } else null
                    sharedBaseline = if(baselineSize > 0) encoded.drop(3).take(baselineSize) else null
                    this.priority = priority
                    if (ux != null) {
                        batchEnabled = ux[1].toBoolean(); batchQuantity = ux[2]; batchNumbering = com.r0ybt.arachn0de.domain.model.NumberingMode.valueOf(ux[3])
                        batchStartNumber = ux[4]; batchTemporal = com.r0ybt.arachn0de.domain.model.BatchTemporalRule.valueOf(ux[5])
                        latentFrequency = ux[6]; startEnabled = ux[7].toBoolean(); dueEnabled = ux[8].toBoolean()
                    }
                    financialEnabled = it.getOrNull(8)?.toBoolean() ?: false
                    amountText = it.getOrNull(9).orEmpty(); currencyCode = it.getOrNull(10) ?: "CLP"
                    financialRemovalConfirmed = it.getOrNull(13)?.toBoolean() ?: false
                    showResponsible = it.getOrNull(14)?.toBoolean() ?: false
                    if (it.getOrNull(15) == "__recurrence_v1") {
                        recurrenceFrequency = it[16]; if (ux == null && recurrenceFrequency != "NONE") latentFrequency = recurrenceFrequency; recurrenceInterval = it[17]; recurrenceStart = it[18]; recurrenceEnd = it[19]
                        if (it.getOrNull(20) == "__tags_v1") { val count = it[21].toInt(); tagIds = it.drop(22).take(count); responsibleIds = it.drop(22 + count) } else responsibleIds = it.drop(20)
                    } else responsibleIds = it.drop(15)
                }
            },
        )
    }
}
