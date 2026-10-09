package com.r0ybt.arachn0de

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch

/** Private dependencies exist only inside an authenticated, revocable store session. */
@OptIn(FlowPreview::class)
open class Arachn0deApplication:Application() {
    internal val security by lazy { com.r0ybt.arachn0de.security.VaultManager(this) }
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
        // No database, media or private preference is opened before authentication.
        processScope.launch {
            security.current.collectLatest { active ->
                if(active==null) return@collectLatest
                coroutineScope {
                    suspend fun maintain() {
                        try {
                            active.backups.recover();active.people.cleanup();active.technologies.cleanup()
                            active.photos.cleanup();active.attachments.cleanup()
                        } catch(cancelled:CancellationException) { throw cancelled }
                        catch(_:Exception) { android.util.Log.w("Storage","Limpieza pendiente; se reintentará.") }
                    }
                    active.scope.launch {
                        active.database.invalidationTracker.createFlow("persons","technologies","project_photos","attachment_files","node_attachments","project_attachments")
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
