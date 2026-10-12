package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun BirthdayField(value: String, onChange: (String) -> Unit, enabled: Boolean = true) {
    var calendar by rememberSaveable {mutableStateOf(false)}
    Column {
        OutlinedTextField(value,{onChange(it.take(10))},label={Text("AAAA-MM-DD")},modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=enabled)
        TextButton(enabled=enabled,onClick={calendar=true}) {Text("Elegir fecha de nacimiento")}
        Text("Opcional. Puedes dejarla vacía. En el calendario toca el año para elegirlo rápidamente.",style=MaterialTheme.typography.bodySmall)
    }
    if(calendar) {
        val today=LocalDate.now()
        val initial=runCatching {LocalDate.parse(value)}.getOrDefault(today).let {if(it.year<1 || it.isAfter(today)) today else it}
        val state=rememberDatePickerState(initialSelectedDateMillis=initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange=1..today.year,selectableDates=object:SelectableDates {
                override fun isSelectableDate(utcTimeMillis:Long)=utcTimeMillis<=today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                override fun isSelectableYear(year:Int)=year<=today.year
            })
        DatePickerDialog(onDismissRequest={calendar=false},confirmButton={TextButton(enabled=state.selectedDateMillis!=null,onClick={
            state.selectedDateMillis?.let {onChange(java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())};calendar=false
        }) {Text("Seleccionar")}},dismissButton={Row {
            TextButton(onClick={onChange("");calendar=false}) {Text("Quitar fecha")}
            TextButton(onClick={calendar=false}) {Text("Cancelar")}
        }}) {DatePicker(state,title={Text("Fecha de nacimiento")},showModeToggle=true)}
    }
}
