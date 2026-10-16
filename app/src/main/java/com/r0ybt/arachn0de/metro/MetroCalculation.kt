package com.r0ybt.arachn0de.metro

internal data class MetroCaptureStatus(val capture:MetroCapture,val invalidReason:String?)

/** Presentation data only. All timing/statistical decisions stay outside Compose. */
internal object MetroCalculation {
    fun time(millis:Long):String = "${millis/60_000} min ${(millis%60_000)/1000} s"
    fun clock(millis:Long):String = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date(millis))
    fun delta(millis:Long):String = (if(millis>=0) "+" else "−")+time(kotlin.math.abs(millis))
    fun reason(reason:String):String = when(reason) {
        "UNDONE"->"Confirmación deshecha"
        "CORRECTION"->"Corrección de posición sin apertura de puertas"
        "PAUSED","PAUSE"->"Tiempo con una pausa: guardado, sin usar para aprender tiempos habituales"
        "NOT_RIDING"->"Etapa sin recorrido ferroviario activo"
        "NO_STOP"->"Estación sin detención en el servicio seleccionado"
        "NO_RAIL"->"Recorrido sin tramo ferroviario"
        "RESTORED_CLOCK"->"Relojes separados por una restauración"
        "REBOOT"->"Reinicio del dispositivo"
        "NON_MONOTONIC"->"Tiempo monotónico no válido"
        "ROUTE_OR_POSITION"->"Cambio de recorrido, retroceso o estación repetida"
        "CONTEXT_CHANGE"->"Cambio de línea, sentido, servicio o transbordo"
        "OUTSIDE_WINDOW"->"Fuera de la ventana reciente"
        "IMPLAUSIBLE_DURATION"->"Duración fuera de los límites plausibles"
        "OUTLIER_MAD"->"Valor atípico según mediana y MAD"
        else->"Captura no válida"
    }
    fun lines(route:MetroRoute,observations:List<MetroObservation>,net:MetroNetwork,session:MetroSession?=null,latest:MetroCaptureStatus?=null):List<String> = buildList {
        add("Tiempo estándar: ${time(route.baseMillis)}")
        add("Estimación adaptada: ${time(route.totalMillis)}")
        add("Ajuste aplicado: ${delta(route.totalMillis-route.baseMillis)}")
        session?.let {add("Predicción original del viaje: ${time(it.originalRoute.totalMillis)}")}
        add("Método estadístico: promedio filtrado por mediana/MAD, con inercia hacia el estándar. Ventana: ${route.prediction?.window ?: MetroLearning.DEFAULT_WINDOW} observaciones por contexto.")
        if(route.prediction==null) add("Modelo estándar fijo. No se modifica una sesión histórica al incorporar nuevas mediciones.")
        val parts=route.prediction?.parts ?: route.steps.indices.map {i->
            val estimate=MetroLearning.estimate(route.networkVersion,listOf(route.steps[i]),observations,next=route.steps.getOrNull(i+1))
            val last=(estimate.accepted+estimate.discarded).maxByOrNull {it.recordedOrder}
            MetroPredictionPart(i,i+1,route.duration(i),estimate.observedMean,estimate.accepted.size,
                estimate.discarded.groupingBy {requireNotNull(it.exclusion)}.eachCount(),last?.millis,last?.at)
        }
        val contexts=parts.distinctBy {part->MetroLearning.context(route.networkVersion,route.steps.subList(part.first,part.end),route.steps.getOrNull(part.end))}
        add("Mediciones aceptadas: ${contexts.sumOf {it.accepted}} · descartadas: ${contexts.sumOf {it.discarded.values.sum()}} (evidencia de los contextos al fijar esta predicción).")
        parts.forEach {part->
            val steps=route.steps.subList(part.first,part.end)
            val base=steps.sumOf {it.minutes*60_000L}
            val from=net.stations.getValue(steps.first().from).name
            val to=net.stations.getValue(steps.last().to).name
            add("$from → $to · ${steps.first().line} · ${steps.first().service.name} · sentido ${steps.first().direction}")
            add("Base ${time(base)} · adaptada ${time(part.millis)} · ajuste ${delta(part.millis-base)}")
            add("Promedio observado: ${part.mean?.let(::time) ?: "sin mediciones"} · peso aplicado: ${if(route.prediction==null) 0 else (part.weight*100).toInt()} %")
            add(if(part.end-part.first>1) "Medición acumulada del recorrido. Tiempos intermedios inferidos por proporción del estándar; no son mediciones individuales." else if(part.accepted>0 && steps.first().kind in setOf(MetroStepKind.TRANSFER,MetroStepKind.CHANGE)) "Combinación medida entre aperturas confirmadas; incluye espera y embarque." else if(part.accepted>0) "Medición directa del tramo." else "Estimación inicial; aún no hay mediciones aceptadas de este tramo.")
            part.discarded.forEach {(r,n)->add("${reason(r)}: $n")}
        }
        val last=observations.filter {session==null || it.sessionId==session.id}.maxByOrNull {it.recordedOrder}
        if(last==null) add("Duración real observada: todavía no existe un intervalo entre dos confirmaciones fiables.")
        else {
            add("Última observación registrada: ${time(last.millis)} · ${clock(last.at)}")
            add("${if(last.exclusion in setOf("PAUSE","PAUSED")) "Tiempo registrado con pausa" else "Duración real observada"}: ${time(last.millis)} · desfase respecto del estándar: ${delta(last.millis-last.baseMillis)}")
            val index=route.steps.indices.firstOrNull {i->i+last.steps.size<=route.steps.size && route.steps.subList(i,i+last.steps.size)==last.steps}
            val filtered=if(index==null || last.steps.isEmpty()) null else MetroLearning.estimate(route.networkVersion,last.steps,observations,next=route.steps.getOrNull(index+last.steps.size)).discarded.firstOrNull {it.id==last.id}?.exclusion
            (last.exclusion ?: filtered)?.let {add("Excluida: ${reason(it)}")}
        }
        latest?.let {status->
            val e=status.capture
            add("Último evento: ${net.stations.getValue(e.station).name} · ${when(e.kind) {MetroCaptureKind.DOORS->"puertas abiertas";MetroCaptureKind.ARRIVAL->"llegada";MetroCaptureKind.CORRECTION->"corrección";MetroCaptureKind.PAUSE->"pausa · estación estimada";MetroCaptureKind.RESUME->"reanudación · estación estimada";MetroCaptureKind.PAUSED_LOCATION->"estación confirmada durante la pausa, sin confirmar apertura"}} · ${clock(e.time.wall)}")
            (status.invalidReason ?: e.exclusion)?.let {add(reason(it))}
            add("Tiempo restante mostrado al pulsar: ${time(e.etaMillis)}")
        }
        add(if(parts.any {it.accepted>=MetroLearning.MIN_SAMPLES}) "Aprendizaje: hay evidencia para ajustar nuevos recorridos medidos. El modelo de esta sesión permanece fijo." else "Aprendizaje: se necesitan al menos cinco mediciones fiables por contexto; con menos de cinco se conserva el estándar.")
        add("Todavía desconoce tiempos individuales sin confirmaciones, espera del próximo tren y operación en tiempo real.")
    }
    val help=listOf(
        "Pulsa «Estoy aquí» cuando se abran las puertas en la estación seleccionada.",
        "Pulsa «Llegué» cuando se abran las puertas en el destino o la llegada de tu etapa.",
        "Las confirmaciones intermedias mejoran la precisión. No es obligatorio confirmar todas las estaciones.",
        "Si notas un desfase, espera a la apertura de puertas en la siguiente estación y pulsa «Estoy aquí».",
        "La primera confirmación fiable inicia un intervalo medible. «Empezar viaje» inicia el seguimiento, pero no mide retrospectivamente una apertura.",
        "Pausa cuando el tren esté detenido y reanuda cuando vuelva a salir. El tiempo afectado se conserva, pero no enseña cuánto tarda normalmente el Metro.",
        "«Estoy aquí» durante una pausa corrige la estación y mantiene el seguimiento pausado. No confirma que las puertas se acaben de abrir.",
        "No necesitas confirmar cada estación. Después de reanudar, una nueva confirmación de puertas permite volver a medir desde esa estación.",
        "Cancelar seguimiento lo apaga sin registrar llegada. Eliminar viaje borra el plan y su historial, pero conserva las mediciones ya registradas.",
        "Todo el aprendizaje se guarda en este dispositivo y funciona sin conexión.",
    )
}
