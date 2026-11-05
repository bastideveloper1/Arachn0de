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
internal fun ObligationFields(draft: EditorDraft, enabled: Boolean, showToggle: Boolean = true) {
    val historical=draft.currencyCode!="CLP"
    if (showToggle) Row {
        Checkbox(draft.financialEnabled, onCheckedChange = {
            draft.financialEnabled = it; draft.financialRemovalConfirmed = false
        }, enabled = enabled && !(historical && draft.id!=null), modifier = Modifier.testTag("obligation-enabled").semantics { contentDescription = "Pago" })
        Text("Pago", Modifier.padding(top = 12.dp))
    }
    if (draft.financialEnabled) {
        val result = remember(draft.amountText, draft.currencyCode, draft.moneyLocaleTag) { runCatching { draft.obligation() } }
        OutlinedTextField(draft.amountText, { draft.amountText = it }, label = { Text("Monto") },
            supportingText = { Text("${draft.currencyCode} · ${Money.fractionDigits(draft.currencyCode)} decimales · ${Locale.forLanguageTag(draft.moneyLocaleTag).displayName}") },
            isError = result.isFailure, singleLine = true, enabled = enabled && !historical,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        if(historical)Text(PaymentCurrencyPolicy.MESSAGE,color=Arachn0deColors.TextSecondary)
        if (result.isFailure) Text(result.exceptionOrNull()?.message ?: "Revisa el monto.", color = Arachn0deColors.Destructive, fontSize = 12.sp)
    }
}

@Composable
internal fun ObligationIndicator(node: Node,compactSummary:Boolean=true) {
    if (!node.isCompletable) return
    val obligation = node.obligation ?: return
    val locale = LocalConfiguration.current.locales[0]
    val paid=LocalBankPaid.current[node.id] ?: 0L
    val remainder=(obligation.amountMinor-paid).coerceAtLeast(0)
    val extra=if(paid>0) {
        " · Abonado Banca: "+Money.format(java.math.BigInteger.valueOf(paid),obligation.currencyCode,locale)+
            (if(node.isCompleted && remainder>0) " · Resto completado manualmente, sin movimiento Banca" else " · Pendiente: "+Money.format(java.math.BigInteger.valueOf(remainder),obligation.currencyCode,locale))
    } else if(node.isCompleted) " · Completada sin movimiento Banca" else ""
    val summary=if(compactSummary && paid>0) {
        when {
            !node.isCompleted && remainder>0 -> "Pendiente: "+Money.format(java.math.BigInteger.valueOf(remainder),obligation.currencyCode,locale)
            remainder==0L -> Money.format(obligation,locale)+" · Pagada"
            else -> Money.format(obligation,locale)+" · Completada"
        }
    } else Money.format(obligation, locale)+extra
    Text(summary, color = Arachn0deColors.PathHighlight, fontSize = 12.sp)
}

@Composable
internal fun RemoveObligationDialog(busy: Boolean, toNote: Boolean, resultLabel: String? = null, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, containerColor = Arachn0deColors.Surface,
        title = { Text("Eliminar datos financieros") },
        text = { Text("Se eliminarán el monto y la moneda. El elemento se conservará como ${resultLabel ?: if (toNote) "nota" else "tarea normal"}. Esta acción no se puede deshacer.") },
        confirmButton = { TextButton(enabled = !busy, onClick = onConfirm) { Text("Eliminar datos y continuar") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss, modifier = Modifier.testTag("cancel-remove-obligation")) { Text("Cancelar") } })
}
