package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle

/** Changes only the enclosing editor's draft; its existing Save commits the ISO date. */
@Composable internal fun BirthdayField(value: String, onChange: (String) -> Unit, enabled: Boolean = true) {
    var yearOpen by rememberSaveable {mutableStateOf(false)}
    var monthOpen by remember {mutableStateOf(false)}
    var dayOpen by remember {mutableStateOf(false)}
    val today=LocalDate.now()
    val date=runCatching {LocalDate.parse(value)}.getOrNull()
    val active=date?.takeUnless {it.isAfter(today)} ?: today
    val locale=LocalConfiguration.current.locales[0]
    fun choose(year:Int=active.year,month:Int=active.monthValue,day:Int=active.dayOfMonth) {
        val safeMonth=month.coerceAtMost(if(year==today.year) today.monthValue else 12)
        val first=LocalDate.of(year,safeMonth,1)
        val last=if(year==today.year && safeMonth==today.monthValue) today.dayOfMonth else first.lengthOfMonth()
        onChange(first.withDayOfMonth(day.coerceAtMost(last)).toString())
    }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedButton(enabled=enabled,onClick={yearOpen=true},modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("birthday-year"),contentPadding=PaddingValues(6.dp)) {Text(date?.year?.toString() ?: "Año",maxLines=1)}
            Box(Modifier.weight(1f)) {
                OutlinedButton(enabled=enabled,onClick={monthOpen=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("birthday-month"),contentPadding=PaddingValues(6.dp)) {Text(date?.month?.getDisplayName(TextStyle.FULL,locale) ?: "Mes",maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
                DropdownMenu(monthOpen,{monthOpen=false}) {
                    (1..if(active.year==today.year) today.monthValue else 12).forEach {month->DropdownMenuItem(text={Text(Month.of(month).getDisplayName(TextStyle.FULL,locale))},onClick={choose(month=month);monthOpen=false},modifier=Modifier.testTag("birthday-month:$month"))}
                }
            }
            Box(Modifier.weight(1f)) {
                OutlinedButton(enabled=enabled,onClick={dayOpen=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("birthday-day"),contentPadding=PaddingValues(6.dp)) {Text(date?.dayOfMonth?.toString() ?: "Día",maxLines=1)}
                DropdownMenu(dayOpen,{dayOpen=false}) {
                    val last=if(active.year==today.year && active.month==today.month) today.dayOfMonth else active.lengthOfMonth()
                    (1..last).forEach {day->DropdownMenuItem(text={Text(day.toString())},onClick={choose(day=day);dayOpen=false},modifier=Modifier.testTag("birthday-day:$day"))}
                }
            }
        }
        if(value.isNotEmpty()) TextButton(enabled=enabled,onClick={onChange("")}) {Text("Quitar fecha")}
        else Text("Fecha de nacimiento opcional",style=MaterialTheme.typography.bodySmall)
    }
    if(yearOpen) AlertDialog(onDismissRequest={yearOpen=false},title={Text("Año de nacimiento")},text={
        val state=rememberLazyListState(initialFirstVisibleItemIndex=today.year-active.year)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=320.dp).testTag("birthday-years"),state=state) {
            items((today.year downTo 1).toList(),key={it}) {year->TextButton(onClick={choose(year=year);yearOpen=false},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("birthday-year:$year")) {Text(year.toString())}}
        }
    },confirmButton={TextButton(onClick={yearOpen=false}) {Text("Cancelar")}})
}
