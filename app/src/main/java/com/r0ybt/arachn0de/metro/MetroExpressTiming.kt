package com.r0ybt.arachn0de.metro

/** Provisional model v1: preserve 105 s of travel, reserve 15 s for a compatible stop.
 * This is a conservative decomposition of the existing 120 s reference, not measured speed.
 * All physical edges remain; no two-minute segment is removed. */
internal object MetroExpressTiming {
    fun base(net:MetroNetwork,step:MetroStep):Long =
        if(step.kind==MetroStepKind.RIDE && step.service!=MetroService.NORMAL && !net.stops(step.to,step.line,step.service)) 105_000L
        else step.minutes*60_000L
    fun apply(net:MetroNetwork,route:MetroRoute):MetroRoute = if(!route.usedExpress) route else
        route.copy(provisional=route.steps.map {base(net,it)})
    fun passes(route:MetroRoute,index:Int)=route.provisional!=null && route.steps.getOrNull(index)?.let {
        it.kind==MetroStepKind.RIDE && it.service!=MetroService.NORMAL && route.baseDurations[index]==105_000L
    }==true
    /** A new express prediction starts/ends at effective stops, never at a pass-through station. */
    fun intervalEnd(route:MetroRoute,first:Int):Int {
        var end=first+1
        while(end<route.steps.size && passes(route,end-1) && route.steps[end].let {
            it.kind==MetroStepKind.RIDE && it.line==route.steps[first].line && it.direction==route.steps[first].direction && it.service==route.steps[first].service
        }) end++
        return end
    }
    fun description(route:MetroRoute)=if(route.provisional==null) "Estándar histórico" else
        "Provisional expreso: 105 s de avance por segmento + 15 s si hay detención; supuesto sin velocidades medidas"
}
