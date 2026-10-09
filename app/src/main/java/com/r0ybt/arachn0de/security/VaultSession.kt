package com.r0ybt.arachn0de.security

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.backup.BackupRepository
import kotlinx.coroutines.*

/** All private dependencies belong to one authenticated session, never process-wide lazy values. */
internal class VaultSession(val access:SecureFiles.Session,val context:VaultContext,val database:Arachn0deDatabase,val primary:Boolean) {
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val attachments=AttachmentRepository(database,AttachmentStore(context))
    val photos=ProjectPhotoRepository(database,ProjectPhotoStore(context))
    val technologies=TechnologyRepository(database,TechnologyIconStore(context),scope)
    val people=PersonRepository(database,AvatarStore(context))
    val projects=ProjectRepository(database.projectDao(),database)
    val nodes=NodeRepository(database)
    val metro=com.r0ybt.arachn0de.metro.MetroRepository(database,context)
    val templates=com.r0ybt.arachn0de.templates.SavedTemplateRepository(database)
    val backups=BackupRepository(database,context)
    fun close() {
        scope.cancel();technologies.close();database.close();context.clearMemory()
        PrivateImageCache.clear();SecureFiles.revoke(access)
    }
}
