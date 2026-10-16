package com.r0ybt.arachn0de.metro

import org.json.JSONArray
import org.json.JSONObject

/** Evidence at prediction time, kept even when later observations or undo change the training set. */
internal data class MetroPredictionPart(
    val first: Int, val end: Int, val millis: Long,
    val mean: Long?, val accepted: Int,
    val discarded: Map<String, Int>,
    val lastMillis: Long?, val lastAt: Long?,
) {
    // Stored strategy v1 keeps its constants even if future training algorithms change.
    val weight: Double get() = if(accepted < 5) 0.0 else minOf(1.0,accepted/30.0)
}
internal data class MetroPrediction(val parts: List<MetroPredictionPart>, val window: Int = MetroLearning.DEFAULT_WINDOW)

/** Does not change route choice/service/timetables. Parts never overlap or duplicate observations. */
internal object MetroEstimates {
    fun adapt(route: MetroRoute, observations: List<MetroObservation>): MetroRoute {
        if(route.prediction!=null || route.steps.isEmpty()) return route
        val byContext=observations.groupBy {it.context}
        val parts=mutableListOf<MetroPredictionPart>()
        var first=0
        while(first < route.steps.size) {
            val step=route.steps[first]
            var end=first+1
            var estimate=MetroLearning.estimate(route.networkVersion,listOf(step),byContext[MetroLearning.context(route.networkVersion,listOf(step),route.steps.getOrNull(first+1))].orEmpty(),next=route.steps.getOrNull(first+1))
            if(step.kind==MetroStepKind.RIDE) {
                var boundary=first+1
                while(boundary<route.steps.size && route.steps[boundary].let {it.kind==MetroStepKind.RIDE && it.line==step.line && it.direction==step.direction && it.service==step.service}) boundary++
                // Prefer the longest directly measured path with enough evidence; interpolate only its estimates.
                for(candidate in boundary downTo first+2) {
                    val path=route.steps.subList(first,candidate)
                    val samples=byContext[MetroLearning.context(route.networkVersion,path)] ?: continue
                    val result=MetroLearning.estimate(route.networkVersion,path,samples)
                    if(result.weight>0) {end=candidate;estimate=result;break}
                }
            }
            val latest=(estimate.accepted+estimate.discarded).maxByOrNull {it.recordedOrder}
            parts+=MetroPredictionPart(first,end,estimate.finalMillis,estimate.observedMean,estimate.accepted.size,
                estimate.discarded.groupingBy {requireNotNull(it.exclusion)}.eachCount(),latest?.millis,latest?.at)
            first=end
        }
        // Historical/base-only routes retain their exact representation and standard timing.
        return if(parts.none {it.weight>0}) route else route.copy(prediction=MetroPrediction(parts))
    }

    fun durations(route: MetroRoute): List<Long> {
        val base=route.steps.map {it.minutes*60_000L}
        val prediction=route.prediction ?: return base
        val result=base.toMutableList()
        prediction.parts.forEach {part ->
            val total=base.subList(part.first,part.end).sum()
            var allocated=0L
            for(i in part.first until part.end) {
                val duration=if(i==part.end-1) part.millis-allocated else if(total==0L) 0 else part.millis*base[i]/total
                result[i]=duration;allocated+=duration
            }
        }
        return result
    }
    fun remaining(s: MetroSession, now: MetroTime, stageOnly: Boolean = false): Long {
        val p=MetroTracking.position(s,now)
        val boundary=if(stageOnly) MetroStages.boundary(s) else s.route.steps.size
        return (MetroStages.offset(s.route,boundary)-p.offset).coerceAtLeast(0)
    }
    fun remainingText(s: MetroSession, now: MetroTime, net:MetroNetwork?=null): String {
        val station=s.route.steps.getOrNull(MetroStages.boundary(s))?.from ?: s.route.stops.last()
        return "≈ ${(remaining(s,now,true)+59_999)/60_000} min hasta ${net?.stations?.get(station)?.name ?: station}"
    }
    fun mapEta(route:MetroRoute,index:Int,offset:Long=0):String = "≈ ${((MetroStages.offset(route,index)-offset).coerceAtLeast(0)+59_999)/60_000} min"

    fun validate(route: MetroRoute) {
        val prediction=route.prediction ?: return
        require(prediction.window in 1..10_000 && prediction.parts.isNotEmpty() && prediction.parts.size<=route.steps.size)
        var next=0
        prediction.parts.forEach {p ->
            require(p.first==next && p.end in p.first+1..route.steps.size)
            next=p.end
            val path=route.steps.subList(p.first,p.end)
            val base=path.sumOf {it.minutes*60_000L}
            require(p.accepted in 0..prediction.window && (p.mean!=null)==(p.accepted>0))
            require(p.discarded.all {(reason,count)->reason in exclusionReasons && count in 1..100_000})
            require((p.lastAt==null)==(p.lastMillis==null) && (p.lastAt==null || p.lastAt>=0 && p.lastMillis!!>=0))
            require(p.mean==null || p.mean in maxOf(1,base/5)..base*5)
            require(path.size==1 || path.all {it.kind==MetroStepKind.RIDE && it.line==path.first().line && it.direction==path.first().direction && it.service==path.first().service})
            if(path.any {it.kind==MetroStepKind.WAYPOINT}) require(p.accepted==0 && p.mean==null && p.millis==base)
            val expected=(base*(1-p.weight)+(p.mean ?: base).toDouble()*p.weight).toLong()
            require(kotlin.math.abs(p.millis-expected)<=1 && p.millis>=0)
        }
        require(next==route.steps.size)
        val durations=durations(route)
        require(durations.indices.all {i->if(route.steps[i].kind==MetroStepKind.WAYPOINT) durations[i]==0L else durations[i] in 1..1_200_000L})
    }
    private val exclusionReasons=setOf("UNDONE","CORRECTION","PAUSED","NOT_RIDING","INVALID_CAPTURE","RESTORED_CLOCK","REBOOT","NON_MONOTONIC","PAUSE","ROUTE_OR_POSITION","CONTEXT_CHANGE","OUTSIDE_WINDOW","IMPLAUSIBLE_DURATION","OUTLIER_MAD","NO_RAIL","NO_STOP")
}

internal object MetroPredictionCodec {
    fun encode(p: MetroPrediction): JSONObject = JSONObject().apply {
        put("version",1);put("window",p.window)
        put("parts",JSONArray(p.parts.map {part->JSONObject().apply {
            put("first",part.first);put("end",part.end);put("millis",part.millis)
            put("mean",part.mean ?: JSONObject.NULL);put("accepted",part.accepted)
            put("discarded",JSONObject(part.discarded));put("lastMillis",part.lastMillis ?: JSONObject.NULL);put("lastAt",part.lastAt ?: JSONObject.NULL)
        }}))
    }
    fun decode(o: JSONObject): MetroPrediction {
        fun fields(obj:JSONObject,vararg names:String) {require(obj.keys().asSequence().toSet()==names.toSet())}
        fun number(obj:JSONObject,key:String):Long {val n=obj.get(key);require(n is Long || n is Int);return (n as Number).toLong()}
        fun integer(obj:JSONObject,key:String):Int = number(obj,key).also {require(it in 0..Int.MAX_VALUE)}.toInt()
        fun optional(obj:JSONObject,key:String):Long? = if(obj.isNull(key)) null else number(obj,key)
        fields(o,"version","window","parts");require(integer(o,"version")==1)
        val rows=o.getJSONArray("parts")
        return MetroPrediction((0 until rows.length()).map {i->val r=rows.getJSONObject(i)
            fields(r,"first","end","millis","mean","accepted","discarded","lastMillis","lastAt")
            val discarded=r.getJSONObject("discarded")
            MetroPredictionPart(integer(r,"first"),integer(r,"end"),number(r,"millis"),optional(r,"mean"),integer(r,"accepted"),
                discarded.keys().asSequence().associateWith {integer(discarded,it)},optional(r,"lastMillis"),optional(r,"lastAt"))
        },integer(o,"window"))
    }
}
