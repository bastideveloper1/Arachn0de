package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.access.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

/** Only existing facts are rendered. Empty fields never reserve a row. */
internal fun searchDetailLines(result:ElementResult,detail:SearchDetail,locale:Locale,zone:TimeZone,now:Long):List<String> {
    fun date(day:LocalDate):String {
        val today=java.time.Instant.ofEpochMilli(now).atZone(zone.toZoneId()).toLocalDate()
        return day.format(DateTimeFormatter.ofPattern(if(day.year==today.year)"d MMM" else "d MMM yyyy",locale))
    }
    val financial=buildList {
        if(detail.amount!=null || detail.recurrence!=null) {
            val title=ElementSearch.normalize(result.title)
            val people=detail.people.filter {name->!Regex("(?<![\\p{L}\\p{N}])"+Regex.escape(ElementSearch.normalize(name))+"(?![\\p{L}\\p{N}])").containsMatchIn(title)}
            if(people.isNotEmpty())add(people.joinToString(", "))
        }
        detail.amount?.let {amount->
            val money=Money.format(amount,locale)
            if(detail.partial || ElementSearch.normalize(money) !in ElementSearch.normalize(result.title))add((if(detail.partial)"Pendiente: " else "")+money)
        }
        if(detail.amount!=null)detail.dueAt?.let {add("Vence: "+date(java.time.Instant.ofEpochMilli(it).atZone(zone.toZoneId()).toLocalDate()))}
        when(detail.payment) {
            SearchPaymentState.PAID->add("Pagado")
            SearchPaymentState.COMPLETED->add("Completada")
            SearchPaymentState.VERIFY->add("Por verificar")
            else->Unit
        }
    }
    val signals=buildList {
        detail.recurrence?.let {r->
            if(r.nextDay!=null)add("Próxima: "+date(LocalDate.ofEpochDay(r.nextDay)))
            else add(when(r.status) {"PAUSED"->"Recurrencia pausada";"FINISHED"->"Recurrencia finalizada";"DELETED"->"Recurrencia eliminada";else->"Recurrencia"})
        }
        // One useful reason, following the same priority as AttentionPolicy.
        detail.attention.firstOrNull()?.let {add(when(it) {
            AttentionReason.OVERDUE->"Vencida"
            AttentionReason.DUE_TODAY->"Vence hoy"
            AttentionReason.UPCOMING->"Próxima a vencer"
            AttentionReason.HIGH_PRIORITY->"Prioridad alta"
            AttentionReason.MEDIUM_PRIORITY->"Prioridad media"
        })}
    }
    return listOf(financial.joinToString(" · "),signals.joinToString(" · ")).filter {it.isNotBlank()}
}

@Composable internal fun SearchResultRow(result:ElementResult,detail:SearchDetail?,enabled:Boolean,now:Long,onOpen:()->Unit) {
    val locale=LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("access-result:"+result.key)
        .clickable(enabled=enabled,onClickLabel="Abrir "+result.type,onClick=onOpen),verticalAlignment=Alignment.CenterVertically) {
        LucideIcon(when {result.nodeId==null->Icons.Default.AccountTree
            result.type=="Capa"->Icons.Default.Folder
            result.type=="Nota"->Icons.Default.Description
            else->Icons.Default.ListAlt},null,Modifier.padding(end=8.dp).size(20.dp),tint=Arachn0deColors.TextSecondary)
        Column(Modifier.weight(1f).padding(vertical=8.dp)) {
            Text(result.title,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(if(result.nodeId==null)"Núcleo" else listOf(result.type,result.path).filter {it.isNotBlank()}.joinToString(" · "),
                style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary,maxLines=2,overflow=TextOverflow.Ellipsis)
            detail?.let {searchDetailLines(result,it,locale,TimeZone.getDefault(),now).forEach {line->
                Text(line,style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary,maxLines=2,overflow=TextOverflow.Ellipsis)
            }}
        }
        FavoriteToggle(result.key,enabled=enabled)
    }
}
