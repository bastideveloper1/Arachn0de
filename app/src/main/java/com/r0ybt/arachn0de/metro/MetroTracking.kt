package com.r0ybt.arachn0de.metro

internal data class MetroTime(val wall: Long, val elapsed: Long, val boot: Int)
internal data class MetroEvent(val kind: String, val at: Long, val offset: Long, val station: String? = null)
internal data class MetroSession(val id: String, val route: MetroRoute, val start: Long, val anchorWall: Long, val anchorElapsed: Long, val boot: Int,
    val offset: Long = 0, val pausedAt: Long? = null, val pausedMillis: Long = 0, val ended: Long? = null,
    val confirmed: String? = null, val events: List<MetroEvent> = emptyList(), val uncertain: Boolean = false, val originalRoute: MetroRoute = route, val personId: String? = null,
    val startElapsed: Long = anchorElapsed, val startBoot: Int = boot, val pausedElapsed: Long? = null, val pausedBoot: Int? = null,
    val realMillis: Long? = null, val routeHistory: List<MetroRoute> = emptyList(), val historyElapsedTrusted: Boolean = true, val automatic: Boolean = false, val control:MetroControl?=null)
internal data class MetroPosition(val offset: Long, val step: Int, val fraction: Float, val station: String, val waiting: MetroStepKind? = null, val uncertain: Boolean = false)
internal object MetroTracking {
    fun start(route: MetroRoute, time: MetroTime) = MetroSession(java.util.UUID.randomUUID().toString(), route, time.wall, time.wall, time.elapsed, time.boot,
        automatic = true, control=MetroControl(0,time,0), confirmed = route.stops.first(), events = listOf(MetroEvent("BOARD", time.wall, 0, route.stops.first())))
    fun position(s: MetroSession, now: MetroTime): MetroPosition {
        val uncertain = s.uncertain || s.boot != now.boot || now.elapsed < s.anchorElapsed || kotlin.math.abs((now.wall-now.elapsed)-(s.anchorWall-s.anchorElapsed)) > 300_000
        var offset = s.offset + if (s.pausedAt == null && s.ended == null && !uncertain) (now.elapsed - s.anchorElapsed).coerceAtLeast(0) else 0
        val boundary=if(s.ended==null) MetroStages.boundary(s) else s.route.steps.size
        if(s.ended==null) {
            val control=MetroStages.control(s)
            offset=if(control.phase!=MetroPhase.RIDING) s.offset else minOf(offset,MetroStages.offset(s.route,boundary))
        }
        var consumed = 0L
        s.route.steps.forEachIndexed { i, step ->
            val duration = step.minutes * 60_000L
            if (s.ended==null && step.kind != MetroStepKind.RIDE && i==boundary && offset>=consumed) {
                offset = minOf(offset, consumed)
                if (offset >= consumed) return MetroPosition(consumed, i, 0f, step.from, step.kind, uncertain)
            }
            if (offset < consumed + duration) return MetroPosition(offset, i, ((offset-consumed).toFloat()/duration).coerceIn(0f,1f), step.from, uncertain = uncertain)
            consumed += duration
        }
        return MetroPosition(consumed, s.route.steps.size, 0f, s.route.stops.last(), uncertain = uncertain)
    }
    private fun anchor(s: MetroSession, position: Long, now: MetroTime, event: MetroEvent) = s.copy(offset = position, anchorWall = now.wall, anchorElapsed = now.elapsed, boot = now.boot, events = s.events + event)
    fun pause(s: MetroSession, now: MetroTime): MetroSession {
        require(s.ended == null && s.pausedAt == null)
        val p = position(s,now)
        return anchor(s,p.offset,now,MetroEvent("PAUSE",now.wall,p.offset)).copy(pausedAt = now.wall, pausedElapsed=now.elapsed, pausedBoot=now.boot, uncertain = p.uncertain)
    }
    fun resume(s: MetroSession, now: MetroTime): MetroSession {
        require(s.ended == null && s.pausedAt != null)
        val p=position(s,now)
        return anchor(s,s.offset,now,MetroEvent("RESUME",now.wall,s.offset)).copy(pausedMillis = s.pausedMillis + currentPauseMillis(s,now), pausedAt = null, pausedElapsed=null, pausedBoot=null, uncertain=p.uncertain)
    }
    fun confirm(s: MetroSession, station: String, now: MetroTime, between: Boolean = false): MetroSession {
        require(s.ended == null)
        val current = position(s,now)
        var offset = 0L
        val candidates = mutableListOf<Long>()
        if (!between && s.route.stops.first() == station) candidates.add(0)
        s.route.steps.forEach { step ->
            if (step.from == station && between && step.kind == MetroStepKind.RIDE) candidates.add(offset + 60_000)
            offset += step.minutes * 60_000L
            if (step.to == station && !between) candidates.add(offset)
        }
        require(candidates.isNotEmpty()) { "Estación fuera de la ruta." }
        val target = candidates.minBy { kotlin.math.abs(it-current.offset) }
        // Backward corrections make future transfers pending again; history remains intact.
        val anchored = anchor(s,target,now,MetroEvent(if(between) "BETWEEN" else "CONFIRM",now.wall,target,station))
        return anchored.copy(confirmed = if(between) null else station, uncertain = false,control=MetroStages.atOffset(s,target,now))
    }
    fun enableAutomatic(s:MetroSession, now:MetroTime):MetroSession {
        require(s.ended==null)
        val p=position(s,now)
        // Preserve the last estimated/confirmed point, pause and recovery warning.
        return s.copy(automatic=true,offset=p.offset,anchorWall=now.wall,anchorElapsed=now.elapsed,boot=now.boot,uncertain=p.uncertain)
    }
    fun pass(s:MetroSession,now:MetroTime):MetroSession {
        require(s.ended==null && s.pausedAt==null)
        require(position(s,now).waiting!=null)
        return MetroStages.nextLine(MetroStages.arrive(s,now),now)
    }
    fun finish(s:MetroSession,now:MetroTime):MetroSession {
        if(s.ended!=null) return s
        val atDestination=s.copy(control=MetroStages.control(s).copy(firstStep=s.route.steps.size))
        val finished=MetroStages.arrive(atDestination,now)
        return finished.copy(control=finished.control?.copy(undo=MetroArrivalUndo(s.copy(offset=position(s,now).offset,anchorWall=now.wall,anchorElapsed=now.elapsed,boot=now.boot,control=MetroStages.control(s).copy(undo=null)),now)))
    }
    fun remainingStops(s: MetroSession, now: MetroTime): List<String> {
        val p = position(s,now)
        val completed = s.route.steps.take(p.step).count { it.kind == MetroStepKind.WAYPOINT }
        return s.route.stops.drop(completed+1)
    }
    fun replan(s: MetroSession, route: MetroRoute, now: MetroTime): MetroSession {
        require(s.ended == null)
        return anchor(s,0,now,MetroEvent("REPLAN",now.wall,0,route.stops.first())).copy(route=route,routeHistory=s.routeHistory+s.route,confirmed=route.stops.first(),uncertain=false,control=MetroControl(0,now,s.pausedMillis,records=MetroStages.control(s).records,undo=s.control?.undo?.copy(reversible=false)))
    }
    fun currentPauseMillis(s: MetroSession, now: MetroTime): Long {
        if(s.pausedAt==null) return 0
        return (if(s.historyElapsedTrusted && s.pausedBoot==now.boot && s.pausedElapsed!=null && now.elapsed>=s.pausedElapsed) now.elapsed-s.pausedElapsed else now.wall-s.pausedAt).coerceAtLeast(0)
    }
    fun totalMillis(s: MetroSession, now: MetroTime) = s.realMillis ?: (if(s.historyElapsedTrusted && s.ended==null && s.startBoot==now.boot && now.elapsed>=s.startElapsed) now.elapsed-s.startElapsed else (s.ended ?: now.wall)-s.start).coerceAtLeast(0)
}
