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

/** Changes only the enclosing editor's draft; its existing Save commits a validated date. */
@Composable internal fun BirthdayField(value: String, onChange: (String) -> Unit, enabled: Boolean = true) {
    var yearOpen by rememberSaveable {mutableStateOf(false)}
    var monthOpen by remember {mutableStateOf(false)}
    var dayOpen by remember {mutableStateOf(false)}
    val today=LocalDate.now()
    // Incomplete selections live only in the enclosing editor draft; validators prevent saving them.
    val parts=if(value.startsWith("--")) listOf("")+value.drop(2).split("-") else value.split("-")
    val year=parts.getOrNull(0)?.toIntOrNull()?.takeIf {it>0}
    val month=parts.getOrNull(1)?.toIntOrNull()?.takeIf {it in 1..12}
    val day=parts.getOrNull(2)?.toIntOrNull()?.takeIf {it in 1..31}
    val locale=LocalConfiguration.current.locales[0]
    fun choose(y:Int?=year,m:Int?=month,d:Int?=day) {
        val prefix=y?.toString()?.padStart(4,'0') ?: "-"
        onChange("$prefix-${(m ?: 0).toString().padStart(2,'0')}-${(d ?: 0).toString().padStart(2,'0')}")
    }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedButton(enabled=enabled,onClick={yearOpen=true},modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("birthday-year"),contentPadding=PaddingValues(6.dp)) {Text(year?.toString() ?: "Año",maxLines=1)}
            Box(Modifier.weight(1f)) {
                OutlinedButton(enabled=enabled,onClick={monthOpen=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("birthday-month"),contentPadding=PaddingValues(6.dp)) {Text(month?.let {Month.of(it).getDisplayName(TextStyle.FULL,locale)} ?: "Mes",maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
                DropdownMenu(monthOpen,{monthOpen=false}) {
                    (1..12).forEach {month->DropdownMenuItem(text={Text(Month.of(month).getDisplayName(TextStyle.FULL,locale))},onClick={choose(m=month);monthOpen=false},modifier=Modifier.testTag("birthday-month:$month"))}
                }
            }
            Box(Modifier.weight(1f)) {
                OutlinedButton(enabled=enabled,onClick={dayOpen=true},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("birthday-day"),contentPadding=PaddingValues(6.dp)) {Text(day?.toString() ?: "Día",maxLines=1)}
                DropdownMenu(dayOpen,{dayOpen=false}) {
                    val last=month?.let {if(year!=null) java.time.YearMonth.of(year,it).lengthOfMonth() else Month.of(it).maxLength()} ?: 31
                    (1..last).forEach {day->DropdownMenuItem(text={Text(day.toString())},onClick={choose(d=day);dayOpen=false},modifier=Modifier.testTag("birthday-day:$day"))}
                }
            }
        }
        if(!com.r0ybt.arachn0de.data.local.PersonBirthDate.valid(value)) Text("Selecciona día y mes válidos. El año es opcional.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
        if(value.isNotEmpty()) TextButton(enabled=enabled,onClick={onChange("")}) {Text("Quitar fecha")}
        else Text("Fecha de nacimiento opcional",style=MaterialTheme.typography.bodySmall)
    }
    if(yearOpen) AlertDialog(onDismissRequest={yearOpen=false},title={Text("Año de nacimiento")},text={
        val state=rememberLazyListState(initialFirstVisibleItemIndex=(today.year-(year ?: today.year)).coerceAtLeast(0))
        Column {
        TextButton(onClick={if(value.isNotEmpty()) choose(y=null);yearOpen=false},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("birthday-no-year")) {Text("Sin año")}
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=320.dp).testTag("birthday-years"),state=state) {
            items((today.year downTo 1).toList(),key={it}) {year->TextButton(onClick={choose(y=year);yearOpen=false},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("birthday-year:$year")) {Text(year.toString())}}
        }
        }
    },confirmButton={TextButton(onClick={yearOpen=false}) {Text("Cancelar")}})
}
