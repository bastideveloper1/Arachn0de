package com.r0ybt.arachn0de.photos

import com.r0ybt.arachn0de.data.local.AttachmentFileEntity
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.security.SecureFiles
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

/** A reserved, encrypted attachment; consumers never receive the external-camera URI. */
internal data class CameraPhoto(val attachment:AttachmentFileEntity,val file:File)
internal class CameraPhotoImport(private val files:CameraCaptureFiles,private val attachments:AttachmentRepository,private val owner:UUID) {
    companion object {private val operations=kotlinx.coroutines.sync.Mutex()}
    private fun draft(id:String)="camera-$id"
    suspend fun import(id:String):CameraPhoto=operations.withLock {withContext(Dispatchers.IO) {
        files.returned(id,owner)
        val existing=attachments.pendingImports(draft(id))
        val row=if(existing.isNotEmpty())existing.single() else {
            val raw=files.file(id).inputStream().use(PrivatePhoto::read)
            val clean=try {PrivatePhoto.clean(raw)} finally {raw.fill(0)}
            try {attachments.deduplicatePhoto(attachments.importGuestBytes(clean.bytes,draft(id),if(clean.mime=="image/jpeg")"fotografia.jpg" else "fotografia.png"),draft(id))}
            finally {clean.bytes.fill(0)}
        }
        val target=attachments.localFile(row);require(SecureFiles.ownerId(target)==owner)
        AttachmentRepository.fileOperations.withLock {com.r0ybt.arachn0de.backup.verifyBackupAttachment(row,target)}
        // Only a committed row, durable reservation and authenticated full read permit removal.
        files.imported(id,owner)
        CameraPhoto(row,target)
    }}
    suspend fun discard(id:String)=withContext(Dispatchers.IO+NonCancellable) {operations.withLock {
        files.discard(id,owner)
        attachments.discardDraft(draft(id))
        files.finish(id)
    }}
    suspend fun recover() {
        for((id,r) in files.pending(owner)) {
            try {when(r.state) {"RETURNED","IMPORTED"->import(id);"DISCARD"->discard(id)}}
            catch(c:CancellationException) {throw c}
            catch(_:Exception) {android.util.Log.w("CameraCapture","Recuperación pendiente; se conservan los datos.")}
        }
    }
}
