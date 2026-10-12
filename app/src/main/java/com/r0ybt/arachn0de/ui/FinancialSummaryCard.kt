package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

internal fun FinancialState.label(): String = when (this) {
    FinancialState.NO_OBLIGATIONS -> "Sin obligaciones"
    FinancialState.PENDING -> "Pendiente"
    FinancialState.PARTIAL -> "Parcialmente completado"
    FinancialState.COMPLETED -> "Completado"
}

/** The overview already respects person/project/layer and keeps all pending periods. */
@Composable
internal fun FinancialSummaryCard(summary: FinancialSummary?, modifier: Modifier = Modifier, title: String = "Pagos pendientes", overview: FinancialSummary? = summary) {
    if (summary == null || overview == null || overview.count == 0) return
    val locale = LocalConfiguration.current.locales[0]
    val density=LocalDensity.current
    val measurer=rememberTextMeasurer()
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Arachn0deColors.ControlSurface)) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(title,style=MaterialTheme.typography.titleSmall)
            overview.byCurrency.toSortedMap().forEach { (code,totals) ->
                val values=listOf("Este mes" to totals.thisMonthPendingMinor,"Próximo mes" to totals.nextMonthPendingMinor,"Total pendiente" to totals.pendingMinor)
                    .map { (label,amount)->label to Money.format(amount,code,locale) }
                BoxWithConstraints(Modifier.fillMaxWidth().testTag("financial-currency:$code")) {
                    val style=MaterialTheme.typography.titleMedium
                    val cellWidth=with(density) {((maxWidth-16.dp)/3).toPx()}
                    val horizontal=values.all { (label,value)->
                        measurer.measure(value,style,softWrap=false).size.width<=cellWidth
                    }
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(code,style=MaterialTheme.typography.labelSmall,color=Arachn0deColors.TextSecondary)
                        if(horizontal) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            values.forEachIndexed {index,(label,value)->Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                Text(label,style=MaterialTheme.typography.labelSmall,minLines=2)
                                Text(value,style=style,color=if(index==0) Arachn0deColors.PathHighlight else Arachn0deColors.TextPrimary,
                                    modifier=Modifier.testTag("financial-amount:$code:$index"))
                            }}
                        } else Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            values.forEachIndexed {index,(label,value)->Text("$label · $value",style=if(index==0) style else MaterialTheme.typography.bodyMedium,
                                color=if(index==0) Arachn0deColors.PathHighlight else Arachn0deColors.TextPrimary,
                                modifier=Modifier.testTag("financial-amount:$code:$index"))}
                        }
                    }
                }
            }
        }
    }
}
