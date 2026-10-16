package com.r0ybt.arachn0de.cleanup

import android.app.job.*
import android.content.ComponentName
import android.content.Context
import com.r0ybt.arachn0de.Arachn0deApplication
import kotlinx.coroutines.*

internal object CleanupSchedule {
    const val ID=0x434C4541
    fun update(context:Context,days:Int) {
        val scheduler=context.getSystemService(JobScheduler::class.java)
        if(days==0) scheduler.cancel(ID)
        else check(scheduler.schedule(JobInfo.Builder(ID,ComponentName(context,CleanupJobService::class.java))
            .setPeriodic(6*60*60*1000L).build())==JobScheduler.RESULT_SUCCESS) {"Android no pudo programar la limpieza."}
    }
}

/** No keys or task data leave the authenticated session. Locked executions wait for a later run. */
class CleanupJobService:JobService() {
    private var running:Job?=null
    private var parameters:JobParameters?=null
    override fun onStartJob(params:JobParameters):Boolean {
        val session=(application as Arachn0deApplication).security.current.value ?: return false
        if(session.audio.state.value.let {it.recording || it.fileId!=null || it.loading}) return false
        parameters=params
        running=session.scope.launch(start=CoroutineStart.UNDISPATCHED) {
            var retry=false
            try {session.cleanup.automatic()}
            catch(c:CancellationException) {throw c}
            catch(_:Exception) {retry=true;android.util.Log.w("CompletedCleanup","Deferred cleanup failed")}
            finally {withContext(NonCancellable+Dispatchers.Main) {if(parameters===params) {parameters=null;jobFinished(params,retry)}}}
        }
        return true
    }
    override fun onStopJob(params:JobParameters):Boolean {parameters=null;running?.cancel();running=null;return true}
    override fun onDestroy() {parameters=null;running?.cancel();super.onDestroy()}
}
