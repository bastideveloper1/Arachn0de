package com.r0ybt.arachn0de.ui

import kotlinx.coroutines.launch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.R
import com.r0ybt.arachn0de.calendar.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deColors

@Composable internal fun rememberCalendarSpecialPreferences(provided:CalendarSpecialPreferences?):CalendarSpecialPreferences {
    val context=LocalContext.current
    val app=context.applicationContext as? Arachn0deApplication
    val privateContext=if(app==null) context else checkNotNull(app.security.current.value).context
    val preferences=provided ?: remember(privateContext) {CalendarSpecialPreferences(privateContext.getSharedPreferences(CalendarSpecialPreferences.NAME,0))}
    DisposableEffect(preferences) {preferences.observe();onDispose {preferences.unobserve()}}
    return preferences
}

/** Corner marker: adds no row or space to existing task/event indicators. */
internal fun Modifier.calendarSpecialMarker(count:Int,color:Color):Modifier=if(count==0) this else drawWithContent {
    drawContent()
    drawCircle(color,radius=1.5.dp.toPx(),center=Offset(size.width-4.dp.toPx(),4.dp.toPx()))
}

@Composable internal fun CalendarSpecialRow(entry:SpecialDateEntry,onInspect:()->Unit) {
    Card(Modifier.fillMaxWidth().testTag("calendar-special:${entry.id}").clickable(onClick=onInspect),
        colors=CardDefaults.cardColors(containerColor=Arachn0deColors.Surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(when(entry.kind) {SpecialDateKind.HOLIDAY->R.drawable.lucide_flag;SpecialDateKind.SAINT->R.drawable.lucide_bookmark;SpecialDateKind.OBSERVANCE->R.drawable.lucide_calendar_days}),null,Modifier.size(16.dp),tint=Arachn0deColors.TextSecondary)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(entry.kind.label,style=MaterialTheme.typography.labelSmall,color=Arachn0deColors.TextSecondary)
                Text(entry.name,style=MaterialTheme.typography.bodyMedium,color=Arachn0deColors.TextPrimary)
            }
            Icon(painterResource(R.drawable.lucide_info),"Consultar fuente y detalles",Modifier.size(16.dp),tint=Arachn0deColors.TextSecondary)
        }
    }
}

@Composable internal fun CalendarSpecialDetail(entry:SpecialDateEntry,onDismiss:()->Unit) {
    ModularPanelDialog(onDismissRequest=onDismiss,title={Text(entry.name)},compact=true,
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(entry.kind.label)
            if(entry.description.isNotEmpty()) Text(entry.description)
            if(entry.kind==SpecialDateKind.SAINT) Text("Fecha nominal del calendario propio católico de Chile; consulta cultural. No aplica precedencia ni traslados litúrgicos y no es un santoral completo.")
            Text("Fuente: ${entry.source.name}",style=MaterialTheme.typography.bodySmall)
            Text(entry.source.url,style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(onClick=onDismiss) {Text("Cerrar")}})
}

@Composable internal fun CalendarSpecialSettings(preferences:CalendarSpecialPreferences,catalog:CalendarSpecialDates) {
    val settings=preferences.settings
    var failed by remember {mutableStateOf(false)}
    var saving by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    Text("Fechas informativas",style=MaterialTheme.typography.titleSmall)
    listOf(Triple("Feriados de Chile",settings.holidays,{v:Boolean->settings.copy(holidays=v)}),
        Triple("Santoral",settings.saints,{v:Boolean->settings.copy(saints=v)}),
        Triple("Fechas conmemorativas",settings.commemorations,{v:Boolean->settings.copy(commemorations=v)}),
        Triple("Cumpleaños de Personas",settings.birthdays,{v:Boolean->settings.copy(birthdays=v)})).forEach {(name,checked,changed)->
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(name,Modifier.weight(1f))
            Switch(checked,enabled=!saving,onCheckedChange={value->if(!saving) {saving=true;scope.launch {
                try {failed=!kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO+kotlinx.coroutines.NonCancellable) {preferences.set(changed(value))}}
                catch(c:kotlinx.coroutines.CancellationException) {throw c}
                catch(_:Exception) {failed=true}
                finally {saving=false}
            }}},modifier=Modifier.testTag("calendar-special-toggle:$name"))
        }
    }
    if(failed) Text("No se pudo conservar la preferencia. Reintenta el cambio.",color=Arachn0deColors.TextSecondary)
    Text("Feriados nacionales: ${catalog.holidayYears.sorted().joinToString()}. Fuera de esos años el catálogo no está cubierto. No incluye feriados regionales, comunales ni nuevos decretos posteriores a ${catalog.verifiedOn}.",style=MaterialTheme.typography.bodySmall)
    Text("Santoral parcial: ${catalog.saintDays}/366 fechas nominales del calendario propio de Chile, CECh · Ordo 2022, p. 13. Sin registro para el 29 de febrero. No es una lista universal ni el calendario litúrgico anual completo.",style=MaterialTheme.typography.bodySmall)
    Text("Conmemoraciones: selección de ${catalog.commemorations} días documentados por Naciones Unidas. Sin recordatorios automáticos. Las fuentes se consultan al tocar cada celebración.",style=MaterialTheme.typography.bodySmall)
}

/** Second corner marker, independent of the existing special-date, task and event marks. */
internal fun Modifier.calendarBirthdayMarker(count:Int,color:Color):Modifier=if(count==0) this else drawWithContent {
    drawContent()
    drawCircle(color,radius=1.5.dp.toPx(),center=Offset(4.dp.toPx(),4.dp.toPx()),style=androidx.compose.ui.graphics.drawscope.Stroke(width=1.dp.toPx()))
}

@Composable internal fun CalendarBirthdayRow(birthday:CalendarBirthday,compact:Boolean=false,onOpen:()->Unit) {
    Card(Modifier.fillMaxWidth().testTag("calendar-birthday:${birthday.person.id}").clickable(onClick=onOpen),
        colors=CardDefaults.cardColors(containerColor=Arachn0deColors.Surface)) {
        Row(Modifier.fillMaxWidth().then(if(compact) Modifier.heightIn(min=48.dp) else Modifier).padding(horizontal=12.dp,vertical=if(compact) 4.dp else 12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Cake,null,Modifier.size(16.dp),tint=cardIndicatorColor("birthday",true))
            Text(birthday.person.name,Modifier.weight(1f),color=Arachn0deColors.TextPrimary)
            birthday.age?.let {Text("Cumple $it ${if(it==1) "año" else "años"}",style=MaterialTheme.typography.bodySmall,color=Arachn0deColors.TextSecondary)}
        }
    }
}
