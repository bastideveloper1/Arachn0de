package com.r0ybt.arachn0de.metro

import java.util.Calendar
import java.util.TimeZone

/** General hours only: independent of express timetables, routing and tracking.
 * Sources checked 2026-10-10:
 * https://www.red.cl/mapas-y-horarios/metro/?width=wide
 * https://www.gob.cl/noticias/feriados-2026-revisa-cuantos-habra-y-cuales-son-irrenunciables/
 * No station-specific closures or exceptional operation are inferred.
 */
internal object MetroOperatingHours {
    const val closingMarginMinutes=30 // Preventive UX margin, not an official last-train deadline.
    private val holidays2026=setOf("01-01","04-03","04-04","05-01","05-21","06-21","06-29",
        "07-16","08-15","09-18","09-19","10-12","10-31","11-01","12-08","12-25")
    enum class Warning(val label:String) {
        OUTSIDE("Fuera del horario habitual"),
        NEAR_CLOSING("Cerca del horario de cierre"),
        MAY_FINISH_LATE("El viaje podría finalizar después del cierre"),
        UNVERIFIED_HOLIDAY("El inicio podría estar fuera del horario de festivo; festivos de este año sin verificar")
    }
    fun warnings(start:Long,durationMinutes:Int,hours:MetroHoursConfig=MetroHoursConfig()):List<Warning> {
        val local=Calendar.getInstance(TimeZone.getTimeZone("America/Santiago")).apply {timeInMillis=start}
        val date="${(local.get(Calendar.MONTH)+1).toString().padStart(2,'0')}-${local.get(Calendar.DAY_OF_MONTH).toString().padStart(2,'0')}"
        val holiday=local.get(Calendar.YEAR)==2026 && date in holidays2026
        val day=local.get(Calendar.DAY_OF_WEEK)
        val period=when {holiday || day==Calendar.SUNDAY->hours.sunday;day==Calendar.SATURDAY->hours.saturday;else->hours.weekday}
        val opening=period.open
        val closing=(local.clone() as Calendar).apply {set(Calendar.HOUR_OF_DAY,period.close/60);set(Calendar.MINUTE,period.close%60);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
        val minute=local.get(Calendar.HOUR_OF_DAY)*60+local.get(Calendar.MINUTE)
        // The official source permits entering at 23:00; do not declare that instant closed.
        if(minute<opening || start>closing) return listOf(Warning.OUTSIDE)
        return buildList {
            if(local.get(Calendar.YEAR)!=2026 && day!=Calendar.SUNDAY && minute<hours.sunday.open) add(Warning.UNVERIFIED_HOLIDAY)
            if(start>=closing-closingMarginMinutes*60_000L) add(Warning.NEAR_CLOSING)
            if(durationMinutes.coerceAtLeast(0).toLong()*60_000L>closing-start) add(Warning.MAY_FINISH_LATE)
        }
    }
}
