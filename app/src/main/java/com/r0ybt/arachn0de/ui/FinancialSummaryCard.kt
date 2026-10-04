package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

internal fun FinancialState.label(): String = when (this) {
    FinancialState.NO_OBLIGATIONS -> "Sin obligaciones"
    FinancialState.PENDING -> "Pendiente"
    FinancialState.PARTIAL -> "Parcialmente completado"
    FinancialState.COMPLETED -> "Completado"
}

@Composable
internal fun FinancialSummaryCard(summary: FinancialSummary?, modifier: Modifier = Modifier, title: String = "Obligaciones", overview: FinancialSummary? = summary) {
    if (summary == null || overview == null || overview.count == 0) return
    val locale = LocalConfiguration.current.locales[0]
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Arachn0deColors.ControlSurface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("$title · ${summary.state.label()}", fontSize = 14.sp)
            Text("${summary.count} obligaciones · ${summary.pendingCount} pendientes · ${summary.completedCount} completadas", fontSize = 12.sp)
            (summary.byCurrency.keys + overview.byCurrency.keys).sorted().forEach { code ->
                val totals = summary.byCurrency[code] ?: CurrencyTotals()
                val pending = overview.byCurrency[code] ?: CurrencyTotals()
                Column(Modifier.testTag("financial-currency:$code"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(code, color = Arachn0deColors.PathHighlight, fontSize = 12.sp)
                    Text("Total · ${Money.format(totals.totalMinor, code, locale)}", fontSize = 12.sp)
                    Text("Pendiente total · ${Money.format(pending.pendingMinor, code, locale)}", fontSize = 12.sp)
                    Text("Este mes · ${Money.format(pending.thisMonthPendingMinor, code, locale)}", fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Arachn0deColors.PathHighlight)
                    Text("Próximo mes · ${Money.format(pending.nextMonthPendingMinor, code, locale)}", fontSize = 12.sp)
                    Text("Completado · ${Money.format(totals.completedMinor, code, locale)}", fontSize = 12.sp, color = Arachn0deColors.TextSecondary)
                }
            }
        }
    }
}
