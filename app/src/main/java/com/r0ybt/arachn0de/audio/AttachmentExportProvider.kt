package com.r0ybt.arachn0de.audio

import android.content.*
import android.database.*
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.security.*
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

/** Grants read-only original bytes for the active vault; no decrypted sharing cache. */
class AttachmentExportProvider:ContentProvider() {
    override fun onCreate()=true
    private fun session(uri:Uri):VaultSession {
        val session=(context!!.applicationContext as Arachn0deApplication).security.current.value ?: error("Almacén bloqueado.")
        require(uri.pathSegments.size==2 && uri.pathSegments[0]==session.access.id.toString())
        return session
    }
    private fun row(uri:Uri)=runBlocking(Dispatchers.IO) {val s=session(uri);requireNotNull(s.database.attachmentDao().get(uri.pathSegments[1])).also {require(it.lifecycleState=="READY" && s.database.attachmentDao().references(it.id)>0)}}
    override fun getType(uri:Uri)=row(uri).mimeType
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
        val row=row(uri);val columns=(projection ?: arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)).filter {it==OpenableColumns.DISPLAY_NAME || it==OpenableColumns.SIZE}
        return MatrixCursor(columns.toTypedArray()).apply {addRow(columns.map<String,Any> {if(it==OpenableColumns.DISPLAY_NAME) row.originalName else row.byteSize}.toTypedArray())}
    }
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        require(mode=="r");val owner=session(uri);val row=row(uri)
        SecureFiles.requireActive(owner.attachments.localFile(row))
        val bytes=runBlocking(Dispatchers.IO) {originalAttachmentBytes(owner.attachments,row)}
        val pipe=ParcelFileDescriptor.createReliablePipe()
        val reservation=AutoCloseable {bytes.fill(0);runCatching {pipe[1].closeWithError("Almacén bloqueado.")}}
        try {owner.access.retain(reservation)} catch(e:Exception) {bytes.fill(0);pipe.forEach {runCatching {it.close()}};throw e}
        Thread({try {ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use {out->var at=0;while(at<bytes.size) {owner.access.checkOpen();check(SecureFiles.activeProfile==owner.access.id);val n=minOf(8192,bytes.size-at);out.write(bytes,at,n);at+=n}}} catch(e:Exception) {runCatching {pipe[1].closeWithError("Adjunto no disponible.")}} finally {bytes.fill(0);owner.access.release(reservation)}},"private-attachment-share").apply {isDaemon=true;start()}
        return pipe[0]
    }
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException()
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
}
