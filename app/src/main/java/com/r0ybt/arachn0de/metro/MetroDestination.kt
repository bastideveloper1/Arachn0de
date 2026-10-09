package com.r0ybt.arachn0de.metro

/** Candidate selection has no side effects. Only a confirmed revision enters the repository transaction. */
internal object MetroDestination {
    fun location(s:MetroSession,now:MetroTime):String? {
        val p=MetroTracking.position(s,now)
        return s.confirmed?.takeIf { !p.uncertain && p.offset==s.offset && p.fraction==0f && p.station==it && s.events.lastOrNull()?.kind!="BETWEEN" }
    }
    fun change(s:MetroSession,route:MetroRoute,station:String,now:MetroTime,event:String="DESTINATION"):MetroSession {
        require(s.ended==null && route.stops.first()==station)
        val c=MetroStages.control(s)
        val records=c.records.toMutableList()
        if(c.phase==MetroPhase.RIDING) records.add(MetroStageRecord(c.firstStep,MetroStages.boundary(s),MetroStages.railMillis(s,now)))
        else c.transferStarted?.let {start->
            val duration=MetroStages.duration(start,now,c.clockTrusted)
            if(records.isNotEmpty()) records[records.lastIndex]=records.last().copy(transferMillis=duration)
            else records.add(MetroStageRecord(0,0,0,duration))
        }
        val archived=s.copy(offset=MetroTracking.position(s,now).offset,anchorWall=now.wall,anchorElapsed=now.elapsed,boot=now.boot,
            control=c.copy(records=records,undo=null),routeArchives=emptyList())
        val phase=if(route.steps.isEmpty()) MetroPhase.RIDING else MetroPhase.READY
        return s.copy(route=route,routeHistory=s.routeHistory+s.route,routeArchives=s.routeArchives+archived,
            offset=0,anchorWall=now.wall,anchorElapsed=now.elapsed,boot=now.boot,confirmed=station,uncertain=false,
            control=MetroControl(0,now,s.pausedMillis,phase=phase,transferStarted=if(phase==MetroPhase.READY && c.transferStarted!=null) now else null,undo=c.undo?.copy(reversible=false)),
            events=s.events+MetroEvent(event,now.wall,0,station))
    }
}
