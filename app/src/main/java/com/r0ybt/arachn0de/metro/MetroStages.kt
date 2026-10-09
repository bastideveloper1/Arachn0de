package com.r0ybt.arachn0de.metro

internal enum class MetroPhase { RIDING, ARRIVED, TRANSFERRING }
internal data class MetroStageRecord(val firstStep:Int,val boundary:Int,val railMillis:Long,val transferMillis:Long=0)
internal data class MetroArrivalUndo(val previous:MetroSession,val time:MetroTime,val reversible:Boolean=true)
internal data class MetroControl(val firstStep:Int,val since:MetroTime,val pauseBaseline:Long,
    val phase:MetroPhase=MetroPhase.RIDING,val heldMillis:Long=0,val transferStarted:MetroTime?=null,
    val records:List<MetroStageRecord> = emptyList(),val undo:MetroArrivalUndo?=null,val clockTrusted:Boolean=true)

internal object MetroStages {
    fun portable(s:MetroSession):MetroSession=s.copy(uncertain=s.uncertain || s.ended==null,
        historyElapsedTrusted=if(s.ended==null) false else s.historyElapsedTrusted,
        control=s.control?.let {c->c.copy(clockTrusted=false,undo=c.undo?.let {it.copy(previous=portable(it.previous))})})
    fun duration(start:MetroTime,now:MetroTime,trusted:Boolean=true):Long=(if(trusted && start.boot==now.boot && now.elapsed>=start.elapsed) now.elapsed-start.elapsed else now.wall-start.wall).coerceAtLeast(0)
    fun offset(route:MetroRoute,index:Int)=route.steps.take(index).sumOf {it.minutes*60_000L}
    fun control(s:MetroSession):MetroControl=s.control ?: atOffset(s,s.offset,MetroTime(s.anchorWall,s.anchorElapsed,s.boot))
    fun atOffset(s:MetroSession,offset:Long,time:MetroTime):MetroControl {
        var consumed=0L;var first=0
        s.route.steps.forEachIndexed {i,step->consumed+=step.minutes*60_000L;if(step.kind!=MetroStepKind.RIDE && consumed<=offset) first=i+1}
        val existing=s.control
        if(existing!=null && existing.firstStep==first && existing.phase==MetroPhase.RIDING)
            return existing.copy(undo=existing.undo?.copy(reversible=false))
        return MetroControl(first,time,s.pausedMillis,records=s.control?.records.orEmpty(),undo=s.control?.undo?.copy(reversible=false))
    }
    fun boundary(s:MetroSession)=s.route.steps.indices.firstOrNull {it>=control(s).firstStep && s.route.steps[it].kind!=MetroStepKind.RIDE} ?: s.route.steps.size
    fun nextRideIndex(s:MetroSession)=s.route.steps.indices.firstOrNull {it>boundary(s) && s.route.steps[it].kind==MetroStepKind.RIDE} ?: s.route.steps.size
    fun nextRide(s:MetroSession)=s.route.steps.getOrNull(nextRideIndex(s))
    fun gateKind(s:MetroSession):MetroStepKind? {
        val kinds=s.route.steps.subList(boundary(s),nextRideIndex(s)).map {it.kind}
        return when {MetroStepKind.TRANSFER in kinds->MetroStepKind.TRANSFER;MetroStepKind.CHANGE in kinds->MetroStepKind.CHANGE;else->kinds.firstOrNull()}
    }
    fun railMillis(s:MetroSession,now:MetroTime):Long {
        val c=control(s)
        return (duration(c.since,now,c.clockTrusted)-(s.pausedMillis-c.pauseBaseline)-MetroTracking.currentPauseMillis(s,now)-c.heldMillis).coerceAtLeast(0)
    }
    fun arrive(s:MetroSession,now:MetroTime):MetroSession {
        if(s.ended!=null || control(s).phase!=MetroPhase.RIDING) return s
        val c=control(s);val boundary=boundary(s);val p=MetroTracking.position(s,now)
        val previous=s.copy(offset=p.offset,anchorWall=now.wall,anchorElapsed=now.elapsed,boot=now.boot,control=c.copy(undo=null))
        val undo=MetroArrivalUndo(previous,now)
        val target=offset(s.route,boundary);val station=s.route.steps.getOrNull(boundary)?.from ?: s.route.stops.last()
        val record=MetroStageRecord(c.firstStep,boundary,railMillis(s,now))
        val arrived=s.copy(offset=target,anchorWall=now.wall,anchorElapsed=now.elapsed,boot=now.boot,confirmed=station,uncertain=false,
            control=c.copy(phase=MetroPhase.ARRIVED,records=c.records+record,undo=undo),
            events=s.events+MetroEvent(if(boundary==s.route.steps.size) "ARRIVED" else "STAGE_ARRIVED",now.wall,target,station))
        return if(boundary<s.route.steps.size) arrived else arrived.copy(ended=now.wall,realMillis=MetroTracking.totalMillis(s,now),
            pausedMillis=s.pausedMillis+MetroTracking.currentPauseMillis(s,now),pausedAt=null,pausedElapsed=null,pausedBoot=null)
    }
    fun beginTransfer(s:MetroSession,now:MetroTime):MetroSession {
        val c=control(s)
        if(s.ended!=null || c.phase!=MetroPhase.ARRIVED || s.pausedAt!=null || boundary(s)>=s.route.steps.size || gateKind(s)==MetroStepKind.WAYPOINT) return s
        return s.copy(control=c.copy(phase=MetroPhase.TRANSFERRING,transferStarted=now,clockTrusted=true),events=s.events+MetroEvent("BEGIN_TRANSFER",now.wall,s.offset,s.confirmed))
    }
    fun nextLine(s:MetroSession,now:MetroTime):MetroSession {
        val c=control(s);val b=boundary(s)
        if(s.ended!=null || c.phase==MetroPhase.RIDING || s.pausedAt!=null || b>=s.route.steps.size) return s
        val next=nextRideIndex(s);val target=offset(s.route,next)
        val records=c.records.toMutableList().apply {if(isNotEmpty()) {val last=last();this[lastIndex]=last.copy(transferMillis=c.transferStarted?.let {duration(it,now,c.clockTrusted)} ?: 0)}}
        return s.copy(offset=target,anchorWall=now.wall,anchorElapsed=now.elapsed,boot=now.boot,uncertain=false,confirmed=s.route.steps.getOrNull(next-1)?.to ?: s.route.stops.last(),
            control=MetroControl(next,now,s.pausedMillis,records=records,undo=c.undo?.copy(reversible=false)),
            events=s.events+MetroEvent("NEXT_LINE",now.wall,target,s.route.steps[b].to))
    }
    fun undoArrival(s:MetroSession,now:MetroTime):MetroSession {
        val undo=s.control?.undo ?: return s
        if(!undo.reversible) return s
        val old=undo.previous;val c=control(old)
        return old.copy(personId=s.personId,anchorWall=now.wall,anchorElapsed=now.elapsed,boot=now.boot,uncertain=old.uncertain || undo.time.boot!=now.boot,
            control=c.copy(heldMillis=c.heldMillis+duration(undo.time,now,c.clockTrusted),undo=null),
            events=old.events+MetroEvent("UNDO_ARRIVAL",now.wall,old.offset,old.confirmed))
    }
}
