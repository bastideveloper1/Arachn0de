package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.NodeBatchDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import java.util.TimeZone

@Composable
internal fun NodeBatchDialog(
    draft: NodeBatchDraft,
    people: List<Person>,
    peopleLoaded: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (List<GeneratedNodeSpec>, Set<String>) -> Unit,
    tagRepository: com.r0ybt.arachn0de.data.repository.TagRepository? = null,
) {
    val zone = TimeZone.getDefault()
    val generated = remember(draft.baseName, draft.quantity, draft.numberingMode, draft.startNumber,
        draft.purpose, draft.description, draft.dates.financialEnabled, draft.dates.amountText, draft.dates.currencyCode, draft.dates.priority, draft.temporalRule, draft.dates.dueAt, zone.id) {
        runCatching { NodeBatchGenerator.generate(draft.parameters(), zone) }
    }
    val specs = generated.getOrNull()
    val picker = rememberSaveable(draft.batchId, saver = TaskDatePickerDraft.Saver) { TaskDatePickerDraft() }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() }, containerColor = Arachn0deColors.Surface,
        title = { Text("Crear varios") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (draft.purpose == NodePurpose.ACTION) PrioritySelector(draft.dates.priority, { draft.dates.priority = checkNotNull(it) }, enabled = !busy)
                tagRepository?.let { repo -> val state by remember(repo) { repo.observe() }.collectAsState(initial = com.r0ybt.arachn0de.domain.model.TagState())
                    TagSelector(state.tags, draft.dates.tagIds.toSet(), { draft.dates.tagIds = it.toList() }, repository = repo) }
                OutlinedTextField(draft.baseName, { draft.baseName = it }, label = { Text("Nombre base") }, singleLine = true, enabled = !busy)
                OutlinedTextField(draft.quantity, { draft.quantity = it }, label = { Text("Cantidad") },
                    supportingText = { Text("De 1 a ${NodeBatchGenerator.MAX_BATCH_SIZE}") }, singleLine = true, enabled = !busy)
                Text("Numeración")
                BatchChoices(NumberingMode.entries.map { it to when (it) {
                    NumberingMode.NONE -> "Sin numeración"; NumberingMode.PREFIX -> "Al inicio"; NumberingMode.SUFFIX -> "Al final"
                } }, draft.numberingMode, !busy) { draft.numberingMode = it }
                if (draft.numberingMode != NumberingMode.NONE) OutlinedTextField(draft.startNumber, { draft.startNumber = it },
                    label = { Text("Número inicial") }, singleLine = true, enabled = !busy)
                BatchChoices(listOf(NodePurpose.ACTION to "Tarea", NodePurpose.NOTE to "Nota"), draft.purpose, !busy) {
                    draft.purpose = it
                    if (it == NodePurpose.NOTE) { draft.temporalRule = BatchTemporalRule.NONE; draft.dates.financialEnabled = false; picker.field = null }
                }
                OutlinedTextField(draft.description, { draft.description = it }, label = { Text("Descripción común") }, enabled = !busy)
                TextButton(enabled = !busy && peopleLoaded, onClick = { draft.showResponsible = true }) {
                    Text("Responsables comunes (${draft.responsibleIds.size})")
                }
                if (draft.purpose == NodePurpose.ACTION) {
                    ObligationFields(draft.dates, !busy)
                    Text("Fechas de vencimiento")
                    BatchChoices(BatchTemporalRule.entries.map { it to when (it) {
                        BatchTemporalRule.NONE -> "Sin fechas"; BatchTemporalRule.DAILY -> "Diaria"
                        BatchTemporalRule.WEEKLY -> "Semanal"; BatchTemporalRule.MONTHLY -> "Mensual"; BatchTemporalRule.YEARLY -> "Anual"
                    } }, draft.temporalRule, !busy) { draft.temporalRule = it }
                    if (draft.temporalRule != BatchTemporalRule.NONE) {
                        Text("Primera fecha/hora · ${zone.id}")
                        TaskDatesEditor(draft.dates, !busy, picker, includeStart = false)
                    }
                }
                if (specs == null) Text(generated.exceptionOrNull()?.message ?: "Revisa los parámetros.", color = Arachn0deColors.Destructive)
                else {
                    Text("Vista previa · ${specs.size} elementos")
                    val indices = if (specs.size <= 4) specs.indices.toList() else listOf(0, 1, 2, specs.lastIndex)
                    indices.forEachIndexed { previewIndex, index ->
                        if (previewIndex == 3 && specs.size > 4) Text("…")
                        val spec = specs[index]
                        Text(spec.title + (spec.obligation?.let { " — ${Money.format(it, androidx.compose.ui.platform.LocalConfiguration.current.locales[0])}" } ?: "") + (spec.dueAt?.let { " — ${formatTaskDate(it, compact = true)}" } ?: ""), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy && peopleLoaded && specs != null, onClick = {
            if (specs != null) onCreate(specs, draft.responsibleIds.toSet())
        }) { Text(if (busy) "Creando…" else "Crear lote") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } },
    )
    if (draft.showResponsible && peopleLoaded) ResponsibleDialog(
        nodeId = draft.batchId, people = people, assigned = people.filter { it.id in draft.responsibleIds }, busy = busy,
        onDismiss = { draft.showResponsible = false },
        onSave = { draft.responsibleIds = it.toList(); draft.showResponsible = false },
    )
}

/** Small finite choice sets wrap on narrow displays instead of forcing horizontal overflow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> BatchChoices(choices: List<Pair<T, String>>, selected: T, enabled: Boolean, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { (value, label) -> FilterChip(selected == value, onClick = { onSelect(value) }, enabled = enabled, label = { Text(label) }) }
    }
}
