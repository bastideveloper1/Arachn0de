package com.r0ybt.arachn0de.metro

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable internal fun MetroHoursEditor(current:MetroHoursConfig,onDismiss:()->Unit,onSave:(MetroHoursConfig)->Boolean) {
    var values by remember {mutableStateOf(listOf(current.weekday.open,current.weekday.close,current.saturday.open,current.saturday.close,current.sunday.open,current.sunday.close))}
    var error by remember {mutableStateOf<String?>(null)}
    val context=LocalContext.current
    fun candidate()=MetroHoursConfig.decode(values.joinToString(","))
    AlertDialog(onDismissRequest=onDismiss,title={Text("Horarios del Metro")},text={Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("America/Santiago · Solo avisos informativos; no restringe viajes.")
        Text(if(runCatching {candidate()==MetroHoursConfig()}.getOrDefault(false)) "Valores oficiales de referencia" else "Valores personalizados")
        listOf("Lunes a viernes","Sábados","Domingos y festivos").forEachIndexed {day,label->
            Text(label)
            Row {
                for(part in 0..1) {
                    val index=day*2+part;val minute=values[index]
                    TextButton(onClick={TimePickerDialog(context,{_,h,m->values=values.toMutableList().apply {set(index,h*60+m)};error=null},minute/60,minute%60,true).show()}) {
                        Text("${if(part==0) "Apertura" else "Cierre"}: ${"%02d:%02d".format(minute/60,minute%60)}")
                    }
                }
            }
        }
        TextButton(onClick={val d=MetroHoursConfig();values=listOf(d.weekday.open,d.weekday.close,d.saturday.open,d.saturday.close,d.sunday.open,d.sunday.close);error=null}) {Text("Restablecer horarios predeterminados")}
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={
        val value=runCatching {candidate()}.getOrElse {error="La apertura debe ser anterior al cierre en el mismo día.";return@TextButton}
        if(!onSave(value)) error="No se pudieron guardar los horarios. Reintenta."
    }) {Text("Guardar")}},dismissButton={TextButton(onClick=onDismiss) {Text("Cancelar")}})
}
