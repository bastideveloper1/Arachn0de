package com.r0ybt.arachn0de.metro

import java.util.Calendar
import java.util.TimeZone

/** Offline reference, NOT a verified current timetable or a real-time train feed.
 * Public authority announcement: transporteinforma.cl, 18 April 2022.
 * Unknown holidays, exceptional operation and boundary train transitions stay uncertain.
 */
internal class MetroSchedule(private val holidays:Set<String> = emptySet(),
    private val availableLines:Set<String> = setOf("L2","L4","L5")) {
    fun express(line:String,direction:Int,wall:Long):Boolean? {
        require(direction in setOf(-1,1))
        if(line !in setOf("L2","L4","L5")) return false
        if(line !in availableLines) return null
        val local=Calendar.getInstance(TimeZone.getTimeZone("America/Santiago")).apply {timeInMillis=wall}
        val date="${local.get(Calendar.YEAR)}-${(local.get(Calendar.MONTH)+1).toString().padStart(2,'0')}-${local.get(Calendar.DAY_OF_MONTH).toString().padStart(2,'0')}"
        if(local.get(Calendar.DAY_OF_WEEK) in setOf(Calendar.SATURDAY,Calendar.SUNDAY) || date in holidays) return false
        val minute=local.get(Calendar.HOUR_OF_DAY)*60+local.get(Calendar.MINUTE)
        return minute in 360 until 540 || minute in 1080 until 1260
    }
    fun permits(step:MetroStep,departure:Long):Boolean {
        if(step.kind!=MetroStepKind.RIDE) return true
        val start=express(step.line,step.direction,departure) ?: return false
        val end=express(step.line,step.direction,departure+step.minutes*60_000L-1) ?: return false
        // Do not invent what happens to a concrete train straddling a timetable boundary.
        return start==end && (step.service!=MetroService.NORMAL)==start
    }
    fun warning(route:MetroRoute,departure:Long):String {
        var wall=departure
        val crosses=route.steps.any {s->val invalid=s.kind==MetroStepKind.RIDE && !permits(s,wall);wall+=s.minutes*60_000L;invalid}
        return "Horario de referencia, no operación en tiempo real. Vigencia, festivos y excepciones sin confirmar; verifica el color del tren en el andén."+
            if(crosses) " El recorrido cruza una franja o no coincide con el horario de referencia; recalcula antes de viajar." else ""
    }
}
