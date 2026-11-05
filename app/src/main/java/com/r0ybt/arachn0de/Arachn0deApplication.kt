package com.r0ybt.arachn0de

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter

/** Private dependencies exist only inside an authenticated, revocable store session. */
@OptIn(FlowPreview::class)
open class Arachn0deApplication:Application() {
    internal val security by lazy { com.r0ybt.arachn0de.security.VaultManager(this) }
    internal val pomodoro by lazy { com.r0ybt.arachn0de.pomodoro.PomodoroController(this) }
    private val processScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val session get()=requireNotNull(security.current.value) { "Almacén bloqueado." }
    internal val privateContext get()=session.context
    val database get()=session.database
    val attachmentRepository get()=session.attachments
    internal val projectPhotoRepository get()=session.photos
    internal val technologyRepository get()=session.technologies
    internal val savedTemplateRepository get()=session.templates
    internal val metroRepository get()=session.metro
    val projectRepository get()=session.projects
    val personRepository get()=session.people
    internal val backupRepository get()=session.backups
    val nodeRepository get()=session.nodes
    override fun onCreate() {
        super.onCreate()
        runCatching {com.r0ybt.arachn0de.photos.CameraCaptureFiles(this).recover()}
        // No database, media or private preference is opened before authentication.
        processScope.launch {
            security.current.collectLatest { active ->
                pomodoro.attach(active?.context?.getSharedPreferences(com.r0ybt.arachn0de.pomodoro.PomodoroCodec.NAME,0),
                    active?.context?.getSharedPreferences(com.r0ybt.arachn0de.pomodoro.PomodoroSoundCodec.NAME,0))
                if(active==null) return@collectLatest
                coroutineScope {
                    active.scope.launch {
                        try {com.r0ybt.arachn0de.diagrams.DiagramExportJournal(this@Arachn0deApplication,active.context,theme=true).recover()}
                        catch(c:CancellationException) {throw c}
                        catch(_:Exception) {android.util.Log.w("Storage","Quedó una exportación histórica parcial pendiente.")}
                    }
                    active.scope.launch {com.r0ybt.arachn0de.photos.CameraPhotoImport(com.r0ybt.arachn0de.photos.CameraCaptureFiles(this@Arachn0deApplication),active.attachments,active.access.id).recover()}
                    suspend fun maintain() {
                        try {
                            com.r0ybt.arachn0de.data.repository.AttachmentRepository.fileOperations.withLock {com.r0ybt.arachn0de.export.PublicExportStaging.recover(active.context)}
                            active.backups.recover();active.people.cleanup();active.technologies.cleanup()
                            com.r0ybt.arachn0de.templates.TemplateInsertionRecovery.recover(active.context,active.attachments)
                            active.photos.cleanup();active.attachments.cleanup();com.r0ybt.arachn0de.report.CardShareFiles(active.context).cleanup()
                        } catch(cancelled:CancellationException) { throw cancelled }
                        catch(_:Exception) { android.util.Log.w("Storage","Limpieza pendiente; se reintentará.") }
                    }
                    active.scope.launch {
                        active.database.invalidationTracker.createFlow("persons","person_banners","person_photos","person_memories","person_memory_images","technologies","project_photos","attachment_files","node_attachments","project_attachments")
                            .debounce(500).catch { failure ->
                                if(failure is CancellationException) throw failure
                                android.util.Log.w("Storage","Observación interrumpida; recuperación pendiente.")
                            }.collect { maintain() }
                    }
                    active.scope.launch { delay(5000);while(isActive) { maintain();delay(60L*60*1000) } }
                    awaitCancellation()
                }
            }
        }
    }
    override fun onTrimMemory(level:Int) {
        super.onTrimMemory(level)
        if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) com.r0ybt.arachn0de.data.local.PrivateImageCache.clear()
    }
    override fun onLowMemory() { super.onLowMemory();com.r0ybt.arachn0de.data.local.PrivateImageCache.clear() }
    override fun onTerminate() { processScope.cancel();super.onTerminate() }
}
