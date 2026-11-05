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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

internal fun FinancialState.label(): String = when (this) {
    FinancialState.NO_OBLIGATIONS -> "Sin pagos"
    FinancialState.PENDING -> "Pendiente"
    FinancialState.PARTIAL -> "Parcialmente completado"
    FinancialState.COMPLETED -> "Completado"
}

/** The overview already respects person/project/layer and keeps all pending periods. */
@Composable
internal fun FinancialSummaryCard(summary: FinancialSummary?, modifier: Modifier = Modifier, title: String = "Pagos pendientes", overview: FinancialSummary? = summary) {
    if (summary == null || overview == null || overview.count == 0) return
    val locale = LocalConfiguration.current.locales[0]
    if(LocalBashPresentation.current) {
        Column(modifier.fillMaxWidth().padding(vertical=4.dp)) {
            Text("# $title",style=MaterialTheme.typography.bodySmall)
            overview.byCurrency.toSortedMap().forEach { (code,totals) ->
                Text("payments.$code.this_month=${Money.format(totals.thisMonthPendingMinor,code,locale)}")
                Text("payments.$code.next_month=${Money.format(totals.nextMonthPendingMinor,code,locale)}")
                Text("payments.$code.pending=${Money.format(totals.pendingMinor,code,locale)}")
            }
        }
        return
    }
    val density=LocalDensity.current
    val measurer=rememberTextMeasurer()
    val labelStyle=MaterialTheme.typography.labelSmall.copy(fontSize=10.sp,lineHeight=14.sp)
    GeneralCard(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Arachn0deColors.ControlSurface)) {
        Column(Modifier.padding(horizontal=10.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.labelMedium)
            overview.byCurrency.toSortedMap().forEach { (code,totals) ->
                val values=listOf("Este mes" to totals.thisMonthPendingMinor,"Próximo mes" to totals.nextMonthPendingMinor,"Total pendiente" to totals.pendingMinor)
                    .map { (label,amount)->label to Money.format(amount,code,locale) }
                BoxWithConstraints(Modifier.fillMaxWidth().testTag("financial-currency:$code")) {
                    val style=MaterialTheme.typography.bodySmall
                    val cellWidth=with(density) {((maxWidth-16.dp)/3).toPx()}
                    val horizontal=values.all { (label,value)->
                        measurer.measure(value,style.copy(fontWeight=FontWeight.SemiBold),softWrap=false).size.width<=cellWidth &&
                            measurer.measure(label,labelStyle,softWrap=false).size.width<=cellWidth
                    }
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(code,style=labelStyle,color=Arachn0deColors.TextSecondary)
                        if(horizontal) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            values.forEachIndexed {index,(label,value)->Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                                Text(label,style=labelStyle)
                                Text(value,style=style.copy(fontWeight=if(index==2)FontWeight.SemiBold else FontWeight.Normal),color=if(index==0) Arachn0deColors.PathHighlight else Arachn0deColors.TextPrimary,
                                    modifier=Modifier.testTag("financial-amount:$code:$index"))
                            }}
                        } else Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            values.forEachIndexed {index,(label,value)->Text(androidx.compose.ui.text.buildAnnotatedString {
                                pushStyle(androidx.compose.ui.text.SpanStyle(fontSize=labelStyle.fontSize,fontWeight=FontWeight.Normal))
                                append("$label · ");pop();append(value)
                            },style=style.copy(fontWeight=if(index==2)FontWeight.SemiBold else FontWeight.Normal),
                                color=if(index==0) Arachn0deColors.PathHighlight else Arachn0deColors.TextPrimary,
                                modifier=Modifier.testTag("financial-amount:$code:$index"))}
                        }
                    }
                }
            }
        }
    }
}
