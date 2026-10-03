package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import com.r0ybt.arachn0de.domain.model.*
import java.util.UUID

/** Save only finite generation inputs, destination and retry identity, never database objects. */
internal class NodeBatchDraft(val parentId: String?, val batchId: String = UUID.randomUUID().toString(), moneyLocaleTag: String = java.util.Locale.getDefault().toLanguageTag()) {
    var baseName by mutableStateOf("")
    var quantity by mutableStateOf("1")
    var numberingMode by mutableStateOf(NumberingMode.NONE)
    var startNumber by mutableStateOf("1")
    var purpose by mutableStateOf(NodePurpose.ACTION)
    var description by mutableStateOf("")
    var responsibleIds by mutableStateOf(emptyList<String>())
    var temporalRule by mutableStateOf(BatchTemporalRule.NONE)
    var showResponsible by mutableStateOf(false)
    val dates = EditorDraft(null, parentId, "", "", creationId = batchId, moneyLocaleTag = moneyLocaleTag)

    fun parameters(): NodeBatchParameters = NodeBatchParameters(
        baseName, quantity.toIntOrNull() ?: throw IllegalArgumentException("La cantidad debe ser un entero entre 1 y ${NodeBatchGenerator.MAX_BATCH_SIZE}."),
        numberingMode,
        if (numberingMode == NumberingMode.NONE) 1 else startNumber.toIntOrNull()
            ?: throw IllegalArgumentException("El número inicial debe ser un entero de 0 o mayor."),
        purpose, description.trim(), temporalRule, dates.dueAt, if (purpose == NodePurpose.ACTION) dates.obligation() else null,
    )

    companion object {
        val Saver = listSaver<NodeBatchDraft?, String>(
            save = { draft -> if (draft == null) emptyList() else listOf(draft.parentId.orEmpty(), draft.batchId,
                draft.baseName, draft.quantity, draft.numberingMode.name, draft.startNumber, draft.purpose.name,
                draft.description, draft.temporalRule.name, draft.dates.dueAt?.toString().orEmpty(), draft.showResponsible.toString(), "financial-v1", draft.dates.financialEnabled.toString(), draft.dates.amountText, draft.dates.currencyCode, draft.dates.moneyLocaleTag) + listOf("tags-v1", draft.dates.tagIds.size.toString()) + draft.dates.tagIds + draft.responsibleIds },
            restore = { values ->
                if (values.isEmpty()) null else {
                    val financial = values.getOrNull(11) == "financial-v1"
                    NodeBatchDraft(values[0].ifEmpty { null }, values[1],
                        if (financial) values[15] else java.util.Locale.getDefault().toLanguageTag()).apply {
                        baseName = values[2]; quantity = values[3]; numberingMode = NumberingMode.valueOf(values[4])
                        startNumber = values[5]; purpose = NodePurpose.valueOf(values[6]); description = values[7]
                        temporalRule = BatchTemporalRule.valueOf(values[8]); dates.dueAt = values[9].toLongOrNull()
                        showResponsible = values[10].toBoolean()
                        if (financial) {
                            dates.financialEnabled = values[12].toBoolean(); dates.amountText = values[13]; dates.currencyCode = values[14]
                        }
                        if (financial && values.getOrNull(16) == "tags-v1") { val count = values[17].toInt(); dates.tagIds = values.drop(18).take(count); responsibleIds = values.drop(18 + count) } else responsibleIds = values.drop(if (financial) 16 else 11)
                    }
                }
            },
        )
    }
}
