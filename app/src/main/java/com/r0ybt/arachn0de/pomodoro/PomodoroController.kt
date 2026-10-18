package com.r0ybt.arachn0de.pomodoro

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Global, hierarchy-free timer. No coroutine or per-second disk writes in the controller. */
internal class PomodoroController(private val context:Context,private val clock:()->PomodoroTime={pomodoroClock(context)}) {
    val sounds=PomodoroSounds(context)
    private val cache=context.getSharedPreferences(CACHE,0)
    private var source:SharedPreferences?=null
    private var writing=false
    private val initial=runCatching {cache.getString(PomodoroCodec.KEY,null)?.let(PomodoroCodec::decode)}.getOrNull()
    private val mutable=MutableStateFlow(initial ?: PomodoroState())
    val state=mutable.asStateFlow()
    val error=MutableStateFlow<String?>(null)
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener { preferences,key ->
        synchronized(this) {
            if(!writing && preferences===source && (key==null || key==PomodoroCodec.KEY)) {
                runCatching {read(preferences)}.onSuccess {replaceFromRestore(it)}.onFailure {error.value="No se pudo recuperar Pomodoro. Se conserva la sesión actual."}
            }
        }
    }
    private fun read(preferences:SharedPreferences)=preferences.getString(PomodoroCodec.KEY,null)?.let(PomodoroCodec::decode) ?: PomodoroState()
    @Synchronized fun attach(preferences:SharedPreferences?,soundPreferences:SharedPreferences?=null) {
        sounds.attach(soundPreferences)
        if(source===preferences) return
        source?.unregisterOnSharedPreferenceChangeListener(listener)
        source=preferences
        if(preferences!=null) {
            val privateState=runCatching {read(preferences)}.getOrElse {error.value="No se pudo recuperar la configuración de Pomodoro.";return}
            // The encrypted checkpoint is authoritative; a stale device cache must never undo a restore.
            save(privateState.advance(clock()))
            preferences.registerOnSharedPreferenceChangeListener(listener)
        }
        refresh()
        schedule()
    }
    private fun replaceFromRestore(value:PomodoroState) {
        // Imported monotonic times may come from another device; recover once by wall time.
        val now=clock()
        val s=value.session
        val recovered=if(s?.status==PomodoroStatus.RUNNING) {
            val rest=(s.remaining-(now.wall-s.wall).coerceAtLeast(0)).coerceIn(0,s.remaining)
            if(rest>0) value.copy(session=s.copy(remaining=rest,wall=now.wall,elapsed=now.elapsed,boot=now.boot),revision=value.revision+1)
            else value.advance(now.copy(elapsed=s.elapsed+s.remaining,boot=s.boot))
        } else value
        mutable.value=recovered
        save(recovered)
        schedule()
    }
    private fun save(value:PomodoroState):Boolean {
        val text=PomodoroCodec.encode(value)
        writing=true
        try {
            val privateStore=source
            if(privateStore!=null) {
                // Revocation can precede the application's detach callback during locking.
                if(runCatching {privateStore.all}.isFailure) {privateStore.unregisterOnSharedPreferenceChangeListener(listener);source=null}
                else if(!privateStore.edit().putString(PomodoroCodec.KEY,text).commit()) {error.value="No se pudo guardar Pomodoro. Reintenta la acción.";return false}
            }
            if(!cache.edit().putString(PomodoroCodec.KEY,text).commit()) {
                error.value="No se pudo conservar la recuperación del temporizador. Reintenta la acción.";return false
            }
            mutable.value=value;sounds.update(value);return true
        } finally {writing=false}
    }
    fun remaining():Long=state.value.session?.remainingAt(clock()) ?: state.value.config.duration(PomodoroPhase.FOCUS)
    @Synchronized fun start() {refresh();if(save(mutable.value.start(clock()))) schedule()}
    @Synchronized fun adjust(minutes:Int) {if(save(mutable.value.adjust(minutes,clock()))) schedule()}
    @Synchronized fun pause() {refresh();if(save(mutable.value.pause(clock()))) schedule()}
    @Synchronized fun finish() {if(save(mutable.value.finish())) {schedule();PomodoroAlarms.clearNotice(context)}}
    @Synchronized fun configure(config:PomodoroConfig) {
        val old=mutable.value
        val session=old.session?.let {s->if(s.status==PomodoroStatus.READY) s.copy(total=config.duration(s.phase),remaining=config.duration(s.phase)) else s}
        if(save(old.copy(config=config,session=session,revision=old.revision+1))) schedule()
    }
    @Synchronized fun refresh() {complete(mutable.value,clock())}
    private fun complete(before:PomodoroState,now:PomodoroTime) {
        val next=before.advance(now)
        if(next!=before && save(next)) {
            schedule()
            sounds.completed()
            PomodoroAlarms.notify(context,before.session!!.phase,next.session!!.phase)
        }
    }
    private fun schedule() {
        runCatching {PomodoroAlarms.schedule(context,mutable.value,clock())}.onFailure {error.value="Android no permitió programar el aviso. El tiempo se recuperará al volver."}
    }
    companion object {const val CACHE="pomodoro_clock_cache"}
}
