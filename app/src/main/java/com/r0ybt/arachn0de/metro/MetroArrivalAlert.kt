package com.r0ybt.arachn0de.metro

/** One optional reminder per estimated approach, never proof of passenger location. */
internal object MetroArrivalAlert {
    fun pending(session: MetroSession, now: MetroTime): Int? {
        if(session.ended!=null || session.pausedAt!=null || MetroStages.control(session).phase!=MetroPhase.RIDING) return null
        val position=MetroTracking.position(session,now)
        val boundary=MetroStages.boundary(session)
        if(position.uncertain || boundary<=0 || position.step!=boundary-1) return null
        if(boundary<session.route.steps.size && MetroStages.gateKind(session) !in setOf(MetroStepKind.TRANSFER,MetroStepKind.CHANGE)) return null
        val marker="PASS_${boundary-1}"
        return (boundary-1).takeIf {session.events.none {it.kind==marker}}
    }
    fun mark(session:MetroSession, now:MetroTime):MetroSession {
        val step=pending(session,now) ?: return session
        return session.copy(events=session.events+MetroEvent("PASS_$step",now.wall,MetroTracking.position(session,now).offset,session.route.steps[step].from))
    }
}
