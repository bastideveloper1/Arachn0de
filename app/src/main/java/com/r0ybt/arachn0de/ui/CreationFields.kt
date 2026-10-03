package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.EditorDraft

@Composable internal fun FormSection(label: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(label, style = MaterialTheme.typography.titleSmall)
}
@Composable internal fun FormToggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.testTag("option:$label").semantics { contentDescription = label })
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun <T> FormChoices(choices: List<Pair<T, String>>, selected: T, enabled: Boolean, onChange: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { (value, label) -> FilterChip(selected == value, { onChange(value) }, enabled = enabled, label = { Text(label) }) }
    }
}
@Composable internal fun CreationBatchFields(draft: EditorDraft, enabled: Boolean) {
    OutlinedTextField(draft.batchQuantity, { draft.batchQuantity = it }, label = { Text("Cantidad") }, supportingText = { Text("De 1 a ${NodeBatchGenerator.MAX_BATCH_SIZE}") }, enabled = enabled, singleLine = true)
    Text("Numeración")
    FormChoices(listOf(NumberingMode.NONE to "Sin numeración", NumberingMode.PREFIX to "Al inicio", NumberingMode.SUFFIX to "Al final"), draft.batchNumbering, enabled) { draft.batchNumbering = it }
    if (draft.batchNumbering != NumberingMode.NONE) OutlinedTextField(draft.batchStartNumber, { draft.batchStartNumber = it }, label = { Text("Número inicial") }, enabled = enabled, singleLine = true)
    if (draft.purpose == NodePurpose.ACTION) {
        Text("Fechas de vencimiento del lote")
        FormChoices(BatchTemporalRule.entries.map { it to when(it) {
            BatchTemporalRule.NONE -> "Sin fechas"; BatchTemporalRule.DAILY -> "Diaria"; BatchTemporalRule.WEEKLY -> "Semanal"; BatchTemporalRule.MONTHLY -> "Mensual"; BatchTemporalRule.YEARLY -> "Anual"
        } }, draft.batchTemporal, enabled) { draft.batchTemporal = it }
        Text("El lote genera solo vencimientos. Configura la primera fecha en Planificación; no asigna fecha de inicio.", style = MaterialTheme.typography.bodySmall)
    }
    val result = runCatching { NodeBatchGenerator.generate(draft.batchDraft().parameters(), java.util.TimeZone.getDefault()) }
    result.fold(onSuccess = { specs ->
        Text("Vista previa · ${specs.size} elementos")
        val indices = if (specs.size <= 4) specs.indices.toList() else listOf(0, 1, 2, specs.lastIndex)
        indices.forEachIndexed { preview, index ->
            if (preview == 3 && specs.size > 4) Text("…")
            Text(specs[index].title + (specs[index].dueAt?.let { " — ${formatTaskDate(it)}" } ?: ""))
        }
    }, onFailure = { Text(it.message ?: "Revisa los parámetros.", color = MaterialTheme.colorScheme.error) })
}
