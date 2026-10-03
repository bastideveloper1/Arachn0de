package com.r0ybt.arachn0de.ui.state

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import com.r0ybt.arachn0de.domain.model.*
import java.util.UUID

/** Save only finite generation inputs, destination and retry identity, never database objects. */
internal class NodeBatchDraft(val parentId: String?, val batchId: String = UUID.randomUUID().toString()) {
    var baseName by mutableStateOf("")
    var quantity by mutableStateOf("1")
    var numberingMode by mutableStateOf(NumberingMode.NONE)
    var startNumber by mutableStateOf("1")
    var purpose by mutableStateOf(NodePurpose.ACTION)
    var description by mutableStateOf("")
    var responsibleIds by mutableStateOf(emptyList<String>())
    var temporalRule by mutableStateOf(BatchTemporalRule.NONE)
    var showResponsible by mutableStateOf(false)
    val dates = EditorDraft(null, parentId, "", "", creationId = batchId)

    fun parameters(): NodeBatchParameters = NodeBatchParameters(
        baseName, quantity.toIntOrNull() ?: throw IllegalArgumentException("La cantidad debe ser un entero entre 1 y ${NodeBatchGenerator.MAX_BATCH_SIZE}."),
        numberingMode,
        if (numberingMode == NumberingMode.NONE) 1 else startNumber.toIntOrNull()
            ?: throw IllegalArgumentException("El número inicial debe ser un entero de 0 o mayor."),
        purpose, description.trim(), temporalRule, dates.dueAt,
    )

    companion object {
        val Saver = listSaver<NodeBatchDraft?, String>(
            save = { draft -> if (draft == null) emptyList() else listOf(draft.parentId.orEmpty(), draft.batchId,
                draft.baseName, draft.quantity, draft.numberingMode.name, draft.startNumber, draft.purpose.name,
                draft.description, draft.temporalRule.name, draft.dates.dueAt?.toString().orEmpty(), draft.showResponsible.toString()) + draft.responsibleIds },
            restore = { values -> if (values.isEmpty()) null else NodeBatchDraft(values[0].ifEmpty { null }, values[1]).apply {
                baseName = values[2]; quantity = values[3]; numberingMode = NumberingMode.valueOf(values[4])
                startNumber = values[5]; purpose = NodePurpose.valueOf(values[6]); description = values[7]
                temporalRule = BatchTemporalRule.valueOf(values[8]); dates.dueAt = values[9].toLongOrNull()
                showResponsible = values[10].toBoolean(); responsibleIds = values.drop(11)
            } },
        )
    }
}
