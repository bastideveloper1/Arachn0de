package com.r0ybt.arachn0de.security

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.backup.BackupRepository
import kotlinx.coroutines.*

/** All private dependencies belong to one authenticated session, never process-wide lazy values. */
internal class VaultSession(val access:SecureFiles.Session,val context:VaultContext,val database:Arachn0deDatabase,val primary:Boolean) {
    var initialProjects:List<com.r0ybt.arachn0de.domain.model.Project>?=null
        internal set
    val appearance by lazy {
        val started=com.r0ybt.arachn0de.ui.ResponseTiming.start("theme_preferences_create")
        com.r0ybt.arachn0de.ui.theme.AppearancePreferences(context.getSharedPreferences(com.r0ybt.arachn0de.ui.theme.AppearancePreferences.FILE,0),access)
            .also {com.r0ybt.arachn0de.ui.ResponseTiming.end("theme_preferences_ready",started)}
    }
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val attachments=AttachmentRepository(database,AttachmentStore(context))
    val calendarEvents=CalendarEventRepository(database)
    val sharedCaptures=com.r0ybt.arachn0de.sharing.SharedCaptureQueue(database,attachments)
    val photos=ProjectPhotoRepository(database,ProjectPhotoStore(context))
    val technologies=TechnologyRepository(database,TechnologyIconStore(context),scope)
    val people=PersonRepository(database,AvatarStore(context))
    val projects=ProjectRepository(database.projectDao(),database).apply {beforeDelete={audio.beforeProjectDeleted(it)}}
    val nodes=NodeRepository(database).apply {beforeDelete={audio.beforeOwnersDeleted(it)}}
    val metro=com.r0ybt.arachn0de.metro.MetroRepository(database,context)
    val templates=com.r0ybt.arachn0de.templates.SavedTemplateRepository(database)
    val backups=BackupRepository(database,context)
    val audio=com.r0ybt.arachn0de.audio.AudioController(context.baseContext,this)
    val completionSounds=com.r0ybt.arachn0de.completion.CompletionSounds(context,scope)
    val cleanup=com.r0ybt.arachn0de.cleanup.CompletedCleanup(database,nodes,attachments)
    init {
        nodes.onCompletionCandidate={nodeId,eventId->scope.launch {
            try {
                val committed=nodes.completionStillCommitted(nodeId,eventId)
                if(committed)completionSounds.play(eventId)
            } catch(c:CancellationException) {throw c}
            catch(_:Exception) {android.util.Log.w("CompletionSound","No se pudo reproducir el sonido de completado.")}
        };Unit}

        scope.launch {
            cleanup.observeDays().collect {days->
                try {com.r0ybt.arachn0de.cleanup.CleanupSchedule.update(context.baseContext,days)}
                catch(c:CancellationException) {throw c} catch(_:Exception) {android.util.Log.w("CompletedCleanup","Scheduling failed")}
            }
        }
    }
    fun close() {
        access.caches.close()
        completionSounds.close()
        audio.dispose()
        initialProjects=null
        scope.cancel();technologies.close();database.close();context.clearMemory()
        SecureFiles.revoke(access)
    }
}
