package com.r0ybt.arachn0de

import android.app.Application
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository

/** Process-scoped dependencies. An Activity never closes the shared database. */
class Arachn0deApplication : Application() {
    private val attachmentScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    val attachmentRepository: com.r0ybt.arachn0de.data.repository.AttachmentRepository by lazy {
        com.r0ybt.arachn0de.data.repository.AttachmentRepository(database, com.r0ybt.arachn0de.data.local.AttachmentStore(this))
    }
    override fun onCreate() {
        super.onCreate()
        if (java.io.File(filesDir, "attachments").exists()) attachmentScope.launch { database }
    }
    val database: Arachn0deDatabase by lazy {
        Arachn0deDatabase.create(this).also { db ->
            attachmentScope.launch {
                db.invalidationTracker.createFlow("attachment_files", "node_attachments", "project_attachments").collect {
                    try { attachmentRepository.cleanup() }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (failure: Exception) { android.util.Log.e("Attachments", "Attachment cleanup will retry on the next change or startup", failure) }
                }
            }
        }
    }
    val projectRepository by lazy { ProjectRepository(database.projectDao(),database) }
    val personRepository by lazy { com.r0ybt.arachn0de.data.repository.PersonRepository(database, com.r0ybt.arachn0de.data.local.AvatarStore(this)) }
    internal val backupRepository by lazy { com.r0ybt.arachn0de.backup.BackupRepository(database, this) }
    val nodeRepository by lazy { NodeRepository(database) }
}
