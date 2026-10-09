package com.r0ybt.arachn0de.metro

/** An editor proposal only: never reverses steps, transfers or task associations. */
internal data class MetroReturnDraft(val origin:String,val destination:String,val express:Boolean,val personId:String?)
internal object MetroReturn {
    fun draft(journey:MetroJourney):MetroReturnDraft {
        val session=journey.data.sessions.lastOrNull()
        val outbound=session?.route ?: journey.data.plan
        val original=session?.originalRoute ?: journey.data.plan
        return MetroReturnDraft(outbound.stops.last(),original.stops.first(),outbound.express,journey.row.personId)
    }
    fun plan(journey:MetroJourney,p:MetroPreferences,departure:Long=System.currentTimeMillis()):MetroRoute? {
        val d=draft(journey)
        return if((journey.data.sessions.lastOrNull()?.route ?: journey.data.plan).departure!=null) MetroPlanner.planAt(p.planningNetwork,listOf(d.origin,d.destination),departure,p.restrictions) else MetroPlanner.plan(p.planningNetwork,listOf(d.origin,d.destination),d.express,p.restrictions)
    }
}
