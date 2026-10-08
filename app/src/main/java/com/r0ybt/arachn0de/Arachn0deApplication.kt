package com.r0ybt.arachn0de

import android.app.Application
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository

/** Process-scoped dependencies. An Activity never closes the shared database. */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class Arachn0deApplication : Application() {
    private val attachmentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    val attachmentRepository: com.r0ybt.arachn0de.data.repository.AttachmentRepository by lazy {
        com.r0ybt.arachn0de.data.repository.AttachmentRepository(database, com.r0ybt.arachn0de.data.local.AttachmentStore(this))
    }
    internal val technologyRepository by lazy {
        com.r0ybt.arachn0de.data.repository.TechnologyRepository(database, com.r0ybt.arachn0de.data.local.TechnologyIconStore(this))
    }
    override fun onCreate() {
        super.onCreate()
        if (listOf("avatars", "technology-icons", "attachments", "image-lifecycle", "backup-restore-journal.json", "technology-icon-journal.json", "backup-attachment-restore-journal.json").any { java.io.File(filesDir, it).exists() }) attachmentScope.launch { database }
    }
    val database: Arachn0deDatabase by lazy {
        Arachn0deDatabase.create(this).also { db ->
            attachmentScope.launch {
                db.invalidationTracker.createFlow("persons", "technologies", "attachment_files", "node_attachments", "project_attachments")
                    .debounce(500).collect { maintainStorage() }
            }
            attachmentScope.launch {
                // Coalesce startup with editor state restoration; unfinished leased drafts survive.
                kotlinx.coroutines.delay(5000)
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    maintainStorage()
                    kotlinx.coroutines.delay(60L * 60 * 1000)
                }
            }
        }
    }
    private suspend fun maintainStorage() {
        try {
            backupRepository.recover()
            personRepository.cleanup()
            technologyRepository.cleanup()
            attachmentRepository.cleanup()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { android.util.Log.e("Storage", "Cleanup deferred until next change or periodic recovery", failure) }
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) com.r0ybt.arachn0de.data.local.PrivateImageCache.clear()
    }
    override fun onLowMemory() { super.onLowMemory(); com.r0ybt.arachn0de.data.local.PrivateImageCache.clear() }
    val projectRepository by lazy { ProjectRepository(database.projectDao(),database) }
    val personRepository by lazy { com.r0ybt.arachn0de.data.repository.PersonRepository(database, com.r0ybt.arachn0de.data.local.AvatarStore(this)) }
    internal val backupRepository by lazy { com.r0ybt.arachn0de.backup.BackupRepository(database, this) }
    val nodeRepository by lazy { NodeRepository(database) }
}
