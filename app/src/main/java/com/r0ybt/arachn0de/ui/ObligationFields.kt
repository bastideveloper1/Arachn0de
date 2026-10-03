package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import java.util.Locale

@Composable
internal fun ObligationFields(draft: EditorDraft, enabled: Boolean) {
    Row {
        Checkbox(draft.financialEnabled, onCheckedChange = {
            draft.financialEnabled = it; draft.financialRemovalConfirmed = false
        }, enabled = enabled, modifier = Modifier.testTag("obligation-enabled").semantics { contentDescription = "Obligación" })
        Text("Obligación", Modifier.padding(top = 12.dp))
    }
    if (draft.financialEnabled) {
        val result = remember(draft.amountText, draft.currencyCode, draft.moneyLocaleTag) { runCatching { draft.obligation() } }
        OutlinedTextField(draft.amountText, { draft.amountText = it }, label = { Text("Monto") },
            supportingText = { Text("${Money.fractionDigits(draft.currencyCode)} decimales · ${Locale.forLanguageTag(draft.moneyLocaleTag).displayName}") },
            isError = result.isFailure, singleLine = true, enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("CLP", "USD", "EUR") + draft.currencyCode).distinct().forEach { code ->
                FilterChip(draft.currencyCode == code, onClick = { draft.currencyCode = code }, enabled = enabled, label = { Text(code) })
            }
        }
        if (result.isFailure) Text(result.exceptionOrNull()?.message ?: "Revisa el monto.", color = Arachn0deColors.Destructive, fontSize = 12.sp)
    }
}

@Composable
internal fun ObligationIndicator(node: Node) {
    if (!node.isCompletable) return
    val obligation = node.obligation ?: return
    val locale = LocalConfiguration.current.locales[0]
    Text(Money.format(obligation, locale), color = Arachn0deColors.PathHighlight, fontSize = 12.sp)
}

@Composable
internal fun RemoveObligationDialog(busy: Boolean, toNote: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, containerColor = Arachn0deColors.Surface,
        title = { Text("Eliminar datos financieros") },
        text = { Text("Se eliminarán el monto y la moneda. El elemento se conservará como ${if (toNote) "nota" else "tarea normal"}. Esta acción no se puede deshacer.") },
        confirmButton = { TextButton(enabled = !busy, onClick = onConfirm) { Text("Eliminar datos y continuar") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.testTag("cancel-remove-obligation")) { Text("Cancelar") } })
}
