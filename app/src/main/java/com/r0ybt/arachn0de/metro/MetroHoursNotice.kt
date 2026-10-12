package com.r0ybt.arachn0de.metro

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** One compact informational block, absent when there is nothing to warn about. */
@Composable internal fun MetroHoursNotice(start:Long,durationMinutes:Int,hours:MetroHoursConfig=MetroHoursConfig()) {
    val warnings=MetroOperatingHours.warnings(start,durationMinutes,hours)
    if(warnings.isNotEmpty()) Text(warnings.joinToString(" · ") {it.label}+
        ". ${if(hours==MetroHoursConfig()) "Horario habitual oficial de referencia" else "Horario personalizado"}. Horario orientativo de la red; puede cambiar. No confirma cierres de estaciones ni la hora del último tren.",
        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
