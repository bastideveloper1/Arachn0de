package com.r0ybt.arachn0de.metro

import org.json.JSONArray
import org.json.JSONObject

internal object MetroControlCodec {
    private fun obj(vararg pairs:Pair<String,Any?>)=JSONObject().apply {pairs.forEach {(k,v)->put(k,v ?: JSONObject.NULL)}}
    private fun time(t:MetroTime)=obj("wall" to t.wall,"elapsed" to t.elapsed,"boot" to t.boot)
    private fun fields(o:JSONObject,vararg keys:String) {require(o.keys().asSequence().toSet()==keys.toSet())}
    private fun number(o:JSONObject,k:String):Long {val n=o.get(k);require(n is Int || n is Long);return (n as Number).toLong().also {require(it>=0)}}
    private fun index(o:JSONObject,k:String)=number(o,k).also {require(it<=Int.MAX_VALUE)}.toInt()
    private fun time(o:JSONObject):MetroTime {fields(o,"wall","elapsed","boot");return MetroTime(number(o,"wall"),number(o,"elapsed"),index(o,"boot"))}
    fun encode(c:MetroControl?):Any = c?.let {obj("firstStep" to it.firstStep,"since" to time(it.since),"pauseBaseline" to it.pauseBaseline,"phase" to it.phase.name,
        "clockTrusted" to it.clockTrusted,"heldMillis" to it.heldMillis,"transferStarted" to it.transferStarted?.let(::time),
        "records" to JSONArray(it.records.map {r->obj("firstStep" to r.firstStep,"boundary" to r.boundary,"railMillis" to r.railMillis,"transferMillis" to r.transferMillis)}),
        "undo" to it.undo?.let {u->require(u.previous.control?.undo==null);obj("previous" to JSONObject(MetroCodec.journey(MetroJourneyData(u.previous.route,listOf(u.previous)))),"time" to time(u.time),"reversible" to u.reversible)})} ?: JSONObject.NULL
    fun decode(o:JSONObject,s:MetroSession,net:MetroNetwork,allowUndo:Boolean):MetroControl {
        fields(o,"firstStep","since","pauseBaseline","phase","heldMillis","transferStarted","records","undo","clockTrusted")
        val records=o.getJSONArray("records").let {a->(0 until a.length()).map {i->val r=a.getJSONObject(i);fields(r,"firstStep","boundary","railMillis","transferMillis");MetroStageRecord(index(r,"firstStep"),index(r,"boundary"),number(r,"railMillis"),number(r,"transferMillis"))}}
        val undo=if(o.isNull("undo")) null else {
            require(allowUndo)
            val u=o.getJSONObject("undo");fields(u,"previous","time","reversible");require(u.get("reversible") is Boolean)
            val previous=MetroCodec.journey(u.getJSONObject("previous").toString(),net,allowUndo=false).sessions.single()
            require(previous.id==s.id && previous.start==s.start && previous.startElapsed==s.startElapsed && previous.startBoot==s.startBoot && previous.originalRoute==s.originalRoute && previous.ended==null && previous.control?.undo==null)
            val reversible=u.getBoolean("reversible");if(reversible) require(previous.route==s.route)
            MetroArrivalUndo(previous,time(u.getJSONObject("time")),reversible)
        }
        require(o.get("clockTrusted") is Boolean)
        val c=MetroControl(index(o,"firstStep"),time(o.getJSONObject("since")),number(o,"pauseBaseline"),MetroPhase.valueOf(o.getString("phase")),number(o,"heldMillis"),if(o.isNull("transferStarted")) null else time(o.getJSONObject("transferStarted")),records,undo,o.getBoolean("clockTrusted"))
        require(c.firstStep in 0..s.route.steps.size && c.pauseBaseline<=s.pausedMillis)
        require((c.transferStarted!=null)==(c.phase==MetroPhase.TRANSFERRING))
        require(records.all {it.firstStep<=it.boundary && it.boundary<=4096})
        val boundary=s.route.steps.indices.firstOrNull {it>=c.firstStep && s.route.steps[it].kind!=MetroStepKind.RIDE} ?: s.route.steps.size
        val lower=MetroStages.offset(s.route,c.firstStep);val upper=MetroStages.offset(s.route,boundary)
        require(s.offset in lower..upper)
        if(c.phase!=MetroPhase.RIDING) {
            require(c.records.isNotEmpty() && s.offset==upper && c.records.last().boundary==boundary)
            require(s.confirmed==(s.route.steps.getOrNull(boundary)?.from ?: s.route.stops.last()))
        }
        return c
    }
}
