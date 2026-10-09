package com.r0ybt.arachn0de.metro

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Temporary scenario input, deliberately excluded from confirmed journey/backup data. */
@Composable internal fun MetroDepartureField(departure:Long,onChange:(Long)->Unit) {
    val context=LocalContext.current
    val zone=TimeZone.getTimeZone("America/Santiago")
    val calendar=Calendar.getInstance(zone).apply {timeInMillis=departure}
    val label=SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.forLanguageTag("es-CL")).apply {timeZone=zone}.format(calendar.time)
    OutlinedButton(onClick={
        DatePickerDialog(context,{_,year,month,day->
            val chosen=Calendar.getInstance(zone).apply {timeInMillis=departure;set(year,month,day);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
            TimePickerDialog(context,{_,hour,minute->chosen.set(Calendar.HOUR_OF_DAY,hour);chosen.set(Calendar.MINUTE,minute);onChange(chosen.timeInMillis)},calendar.get(Calendar.HOUR_OF_DAY),calendar.get(Calendar.MINUTE),true).show()
        },calendar.get(Calendar.YEAR),calendar.get(Calendar.MONTH),calendar.get(Calendar.DAY_OF_MONTH)).apply {datePicker.minDate=0}.show()
    }) {Text("Fecha y hora en Santiago: $label")}
}
