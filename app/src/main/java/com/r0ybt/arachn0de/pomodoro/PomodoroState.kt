package com.r0ybt.arachn0de.pomodoro

import org.json.JSONObject

internal enum class PomodoroPhase(val label:String) { FOCUS("Concentración"), SHORT_BREAK("Descanso corto"), LONG_BREAK("Descanso largo") }
internal enum class PomodoroStatus(val label:String) { READY("Lista para iniciar"), RUNNING("En marcha"), PAUSED("Pausado"), FINISHED("Finalizada") }
internal enum class PomodoroAppearance(val label:String) {
    SOLAR("Resplandor solar"), NIGHT_BLUE("Azul nocturno"), SOFT_GREEN("Verde tenue"), MINIMAL("Minimalista")
}
internal data class PomodoroConfig(val focus:Int=25,val shortBreak:Int=5,val longBreak:Int=15,val sessions:Int=4,val ticking:Boolean=false,val sound:Boolean=true,val appearance:PomodoroAppearance=PomodoroAppearance.SOLAR) {
    init { require(focus in 1..180 && shortBreak in 1..180 && longBreak in 1..180 && sessions in 1..12) }
    fun duration(phase:PomodoroPhase)=60_000L*when(phase) {PomodoroPhase.FOCUS->focus;PomodoroPhase.SHORT_BREAK->shortBreak;PomodoroPhase.LONG_BREAK->longBreak}
}
internal data class PomodoroTime(val wall:Long,val elapsed:Long,val boot:Int)
internal data class PomodoroSession(val phase:PomodoroPhase,val status:PomodoroStatus,val number:Int,val cycle:Int,
    val total:Long,val remaining:Long,val wall:Long=0,val elapsed:Long=0,val boot:Int=0,val manualEnd:Boolean=false) {
    init {
        require(cycle in 1..12 && number in 1..cycle && total in 60_000L..10_800_000L && remaining in 0..total)
        require(!manualEnd || (remaining==0L && status in setOf(PomodoroStatus.RUNNING,PomodoroStatus.PAUSED)))
        require(wall>=0 && elapsed>=0 && boot>=0)
        require(status!=PomodoroStatus.RUNNING || remaining>0 || manualEnd)
        require(status!=PomodoroStatus.FINISHED || remaining==0L)
    }
    fun remainingAt(now:PomodoroTime):Long {
        if(status!=PomodoroStatus.RUNNING) return remaining
        val spent=if(boot==now.boot && now.elapsed>=elapsed) now.elapsed-elapsed else (now.wall-wall).coerceAtLeast(0)
        return (remaining-spent).coerceIn(0,remaining)
    }
}
internal data class PomodoroState(val config:PomodoroConfig=PomodoroConfig(),val session:PomodoroSession?=null,val revision:Long=0) {
    init {require(revision in 0 until Long.MAX_VALUE)}
    val visible get()=session!=null && session.status!=PomodoroStatus.FINISHED
    fun start(now:PomodoroTime):PomodoroState {
        val old=session
        if(old?.status==PomodoroStatus.RUNNING) return this
        val s=if(old==null || old.status==PomodoroStatus.FINISHED) PomodoroSession(PomodoroPhase.FOCUS,PomodoroStatus.READY,1,config.sessions,config.duration(PomodoroPhase.FOCUS),config.duration(PomodoroPhase.FOCUS)) else old
        val duration=if(s.status==PomodoroStatus.READY) config.duration(s.phase) else s.total
        val rest=if(s.status==PomodoroStatus.READY) duration else s.remaining
        return copy(session=s.copy(status=PomodoroStatus.RUNNING,total=duration,remaining=rest,wall=now.wall,elapsed=now.elapsed,boot=now.boot),revision=revision+1)
    }
    fun pause(now:PomodoroTime):PomodoroState {
        val settled=advance(now)
        val s=settled.session ?: return settled
        return if(s.status!=PomodoroStatus.RUNNING) settled else settled.copy(session=s.copy(status=PomodoroStatus.PAUSED,remaining=s.remainingAt(now)),revision=settled.revision+1)
    }
    /** Positive minutes advance elapsed progress; reaching the end holds this phase for a decision. */
    fun adjust(minutes:Int,now:PomodoroTime):PomodoroState {
        require(minutes in setOf(-5,-1,1,5))
        val s=session ?: return this
        if(s.status !in setOf(PomodoroStatus.RUNNING,PomodoroStatus.PAUSED)) return this
        val rest=(s.remainingAt(now)-minutes*60_000L).coerceIn(0,s.total)
        return copy(session=s.copy(remaining=rest,wall=now.wall,elapsed=now.elapsed,boot=now.boot,manualEnd=rest==0L),revision=revision+1)
    }
    fun finish():PomodoroState=copy(session=session?.copy(status=PomodoroStatus.FINISHED,remaining=0,manualEnd=false),revision=revision+1)
    fun advance(now:PomodoroTime):PomodoroState {
        val s=session ?: return this
        if(s.status!=PomodoroStatus.RUNNING || s.manualEnd || s.remainingAt(now)>0) return this
        val phase=when(s.phase) {
            PomodoroPhase.FOCUS->if(s.number==s.cycle) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK
            else->PomodoroPhase.FOCUS
        }
        val number=when(s.phase) {PomodoroPhase.FOCUS->s.number;PomodoroPhase.SHORT_BREAK->s.number+1;PomodoroPhase.LONG_BREAK->1}
        val cycle=if(s.phase==PomodoroPhase.LONG_BREAK) config.sessions else s.cycle
        val duration=config.duration(phase)
        return copy(session=PomodoroSession(phase,PomodoroStatus.READY,number,cycle,duration,duration),revision=revision+1)
    }
}

/** One bounded checkpoint inside the existing privatePreferences backup collection. */
internal object PomodoroCodec {
    const val NAME="pomodoro_preferences"
    const val KEY="checkpoint"
    fun encode(value:PomodoroState):String=JSONObject().apply {
        put("version",if(value.session?.manualEnd==true) 3 else 2);put("revision",value.revision)
        put("config",JSONObject().apply {with(value.config) {put("focus",focus);put("short",shortBreak);put("long",longBreak);put("sessions",sessions);put("ticking",ticking);put("sound",sound);put("appearance",appearance.name)}})
        put("session",value.session?.let {s->JSONObject().apply {
            if(s.manualEnd) put("manualEnd",true)
            put("phase",s.phase.name);put("status",s.status.name);put("number",s.number);put("cycle",s.cycle)
            put("total",s.total);put("remaining",s.remaining);put("wall",s.wall);put("elapsed",s.elapsed);put("boot",s.boot)
        }} ?: JSONObject.NULL)
    }.toString()
    private fun JSONObject.fields(vararg keys:String) {require(keys().asSequence().toSet()==keys.toSet())}
    private fun JSONObject.integer(key:String):Long {val v=get(key);require(v is Int || v is Long);return (v as Number).toLong()}
    private fun JSONObject.intValue(key:String):Int=integer(key).also {require(it in 0..Int.MAX_VALUE.toLong())}.toInt()
    private fun JSONObject.flag(key:String)=get(key).also {require(it is Boolean)} as Boolean
    private fun JSONObject.text(key:String)=get(key).also {require(it is String)} as String
    fun decode(text:String):PomodoroState {
        require(text.length<=4096)
        val j=JSONObject(text);j.fields("version","revision","config","session");val version=j.integer("version");require(version in 1L..3L)
        val c=j.getJSONObject("config")
        val fields=arrayOf("focus","short","long","sessions","ticking","sound")
        c.fields(*(if(version==1L) fields else fields+"appearance"))
        val config=PomodoroConfig(c.intValue("focus"),c.intValue("short"),c.intValue("long"),c.intValue("sessions"),c.flag("ticking"),c.flag("sound"),
            if(version==1L) PomodoroAppearance.SOLAR else PomodoroAppearance.valueOf(c.text("appearance")))
        val s=if(j.isNull("session")) null else j.getJSONObject("session").let {
            it.fields(*(arrayOf("phase","status","number","cycle","total","remaining","wall","elapsed","boot")+if(version==3L) arrayOf("manualEnd") else emptyArray()))
            PomodoroSession(PomodoroPhase.valueOf(it.text("phase")),PomodoroStatus.valueOf(it.text("status")),it.intValue("number"),it.intValue("cycle"),it.integer("total"),it.integer("remaining"),it.integer("wall"),it.integer("elapsed"),it.intValue("boot"),if(version==3L) it.flag("manualEnd") else false)
        }
        return PomodoroState(config,s,j.integer("revision"))
    }
    fun validatePayload(payload:String) {
        val values=com.r0ybt.arachn0de.security.EncryptedPreferences.decode(payload)
        require(values.keys.all {it==KEY})
        values[KEY]?.let {require(it is String);decode(it)}
    }
}
internal fun pomodoroClock(context:android.content.Context)=PomodoroTime(System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime(),
    android.provider.Settings.Global.getInt(context.contentResolver,android.provider.Settings.Global.BOOT_COUNT,0))
internal fun pomodoroTimeText(ms:Long):String {val seconds=(ms.coerceAtLeast(0)+999)/1000;return "%02d:%02d".format(java.util.Locale.ROOT,seconds/60,seconds%60)}
