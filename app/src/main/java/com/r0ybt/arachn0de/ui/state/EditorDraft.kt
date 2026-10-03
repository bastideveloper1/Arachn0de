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
) {
    var responsibleIds by mutableStateOf(emptyList<String>())
    var showResponsible by mutableStateOf(false)
    var financialEnabled by mutableStateOf(obligation != null)
    var amountText by mutableStateOf(obligation?.let { com.r0ybt.arachn0de.domain.model.Money.input(it.amountMinor, it.currencyCode, java.util.Locale.forLanguageTag(moneyLocaleTag)) }.orEmpty())
    var currencyCode by mutableStateOf(obligation?.currencyCode ?: "CLP")
    var financialRemovalConfirmed by mutableStateOf(false)
    fun obligation(): com.r0ybt.arachn0de.domain.model.Obligation? = if (!financialEnabled || purpose != com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION) null else
        com.r0ybt.arachn0de.domain.model.Obligation(com.r0ybt.arachn0de.domain.model.Money.parse(amountText, currencyCode, java.util.Locale.forLanguageTag(moneyLocaleTag)), currencyCode)

    var purpose by mutableStateOf(purpose)
    var title by mutableStateOf(title)
    var description by mutableStateOf(description)
    var startAt by mutableStateOf(startAt)
    var dueAt by mutableStateOf(dueAt)

    companion object {
        val Saver = listSaver<EditorDraft?, String>(
            save = {
                if (it == null) emptyList()
                else listOf(it.id.orEmpty(), it.parentId.orEmpty(), it.title, it.description, it.creationId, it.startAt?.toString().orEmpty(), it.dueAt?.toString().orEmpty(), it.purpose.name, it.financialEnabled.toString(), it.amountText, it.currencyCode, it.moneyLocaleTag, it.hadObligation.toString(), it.financialRemovalConfirmed.toString(), it.showResponsible.toString()) + it.responsibleIds
            },
            restore = {
                if (it.isEmpty()) null
                else EditorDraft(it[0].ifEmpty { null }, it[1].ifEmpty { null }, it[2], it[3], it[4], it.getOrNull(5)?.toLongOrNull(), it.getOrNull(6)?.toLongOrNull(), it.getOrNull(7)?.let(com.r0ybt.arachn0de.domain.model.NodePurpose::valueOf) ?: com.r0ybt.arachn0de.domain.model.NodePurpose.ACTION,
                    moneyLocaleTag = it.getOrNull(11) ?: java.util.Locale.getDefault().toLanguageTag(), hadObligation = it.getOrNull(12)?.toBoolean() ?: false).apply {
                    financialEnabled = it.getOrNull(8)?.toBoolean() ?: false
                    amountText = it.getOrNull(9).orEmpty(); currencyCode = it.getOrNull(10) ?: "CLP"
                    financialRemovalConfirmed = it.getOrNull(13)?.toBoolean() ?: false
                    showResponsible = it.getOrNull(14)?.toBoolean() ?: false
                    responsibleIds = it.drop(15)
                }
            },
        )
    }
}
