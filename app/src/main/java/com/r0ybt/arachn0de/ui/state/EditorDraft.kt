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
        require(startAt == null || dueAt != null) { "Configura Vence para repetir la fecha de inicio." }
        val schedule = com.r0ybt.arachn0de.domain.model.RecurrenceSchedule
        val zone = java.util.TimeZone.getDefault().id
        val start = schedule.parse(recurrenceStart)
        val end = recurrenceEnd.takeIf { it.isNotBlank() }?.let(schedule::parse)
        val interval = requireNotNull(recurrenceInterval.toIntOrNull()) { "Intervalo inválido." }
        val time = java.util.Calendar.getInstance().apply { timeInMillis = dueAt ?: schedule.timestamp(start, zone, 0) }
        val money = obligation()
        return com.r0ybt.arachn0de.data.local.RecurrenceRuleEntity(creationId, projectId, parentId, title.trim(), description.trim(),
            money?.amountMinor, money?.currencyCode, start, recurrenceFrequency, interval, end, 0, "ACTIVE", zone,
            time.get(java.util.Calendar.HOUR_OF_DAY) * 60 + time.get(java.util.Calendar.MINUTE),
            if (startAt != null && dueAt != null) Math.subtractExact(dueAt!!, startAt!!) else null, priority = priority.name).also {
                com.r0ybt.arachn0de.data.repository.RecurrenceRepository.validate(it)
            }
    }

    var purpose by mutableStateOf(purpose)
    var title by mutableStateOf(title)
    var description by mutableStateOf(description)
    var startAt by mutableStateOf(startAt)
    var dueAt by mutableStateOf(dueAt)

    companion object {
        val Saver = listSaver<EditorDraft?, String>(
            save = {
                if (it == null) emptyList()
                else listOf(it.id.orEmpty(), it.parentId.orEmpty(), it.title, it.description, it.creationId, it.startAt?.toString().orEmpty(), it.dueAt?.toString().orEmpty(), it.purpose.name, it.financialEnabled.toString(), it.amountText, it.currencyCode, it.moneyLocaleTag, it.hadObligation.toString(), it.financialRemovalConfirmed.toString(), it.showResponsible.toString()) + listOf("__recurrence_v1", it.recurrenceFrequency, it.recurrenceInterval, it.recurrenceStart, it.recurrenceEnd) + listOf("__priority_v1", it.priority.name) + listOf("__tags_v1", it.tagIds.size.toString()) + it.tagIds + it.responsibleIds
            },
            restore = { stored ->
                val priority = if (stored.getOrNull(20) == "__priority_v1") com.r0ybt.arachn0de.domain.model.Priority.valueOf(stored[21]) else com.r0ybt.arachn0de.domain.model.Priority.NONE
                val it = if (stored.getOrNull(20) == "__priority_v1") stored.take(20) + stored.drop(22) else stored
                if (it.isEmpty()) null
                else EditorDraft(it[0].ifEmpty { null }, it[1].ifEmpty { null }, it[2], it[3], it[4], it.getOrNull(5)?.toLongOrNull(), it.getOrNull(6)?.toLongOrNull(), it.getOrNull(7)?.let(com.r0ybt.arachn0de.domain.model.NodePurpose::valueOf) ?: com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION,
                    moneyLocaleTag = it.getOrNull(11) ?: java.util.Locale.getDefault().toLanguageTag(), hadObligation = it.getOrNull(12)?.toBoolean() ?: false).apply {
                    this.priority = priority
                    financialEnabled = it.getOrNull(8)?.toBoolean() ?: false
                    amountText = it.getOrNull(9).orEmpty(); currencyCode = it.getOrNull(10) ?: "CLP"
                    financialRemovalConfirmed = it.getOrNull(13)?.toBoolean() ?: false
                    showResponsible = it.getOrNull(14)?.toBoolean() ?: false
                    if (it.getOrNull(15) == "__recurrence_v1") {
                        recurrenceFrequency = it[16]; recurrenceInterval = it[17]; recurrenceStart = it[18]; recurrenceEnd = it[19]
                        if (it.getOrNull(20) == "__tags_v1") { val count = it[21].toInt(); tagIds = it.drop(22).take(count); responsibleIds = it.drop(22 + count) } else responsibleIds = it.drop(20)
                    } else responsibleIds = it.drop(15)
                }
            },
        )
    }
}
