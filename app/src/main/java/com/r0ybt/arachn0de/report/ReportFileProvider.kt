package com.r0ybt.arachn0de.report

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.r0ybt.arachn0de.security.SecureFiles
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlinx.coroutines.sync.withLock

/** Decrypt explicit shares through a pipe, never into a persistent plaintext cache. */
class ReportFileProvider:FileProvider() {
    private fun cardUri(uri:Uri)=uri.pathSegments.firstOrNull()=="cards" || uri.pathSegments.lastOrNull()?.startsWith("arachn0de-card-")==true
    private fun cardFile(uri:Uri):File {
        val ctx=requireNotNull(context)
        require(uri.scheme=="content" && uri.authority=="${ctx.packageName}.reports")
        val parts=uri.pathSegments
        val directory=if(parts.firstOrNull()=="v1") {
            require(parts.size==3);val id=UUID.fromString(parts[1]);require(id.toString()==parts[1])
            File(ctx.filesDir.canonicalFile,"security-v1/stores/$id/cache/${CardShareFiles.DIRECTORY}")
        } else {requireLegacyAllowed();require(parts.size==2 && parts[0]=="cards");File(ctx.cacheDir,CardShareFiles.DIRECTORY)}
        val name=parts.last();require(CardShareFiles.validName(name))
        val target=File(directory,name);require(target.canonicalFile==File(directory.canonicalFile,name))
        if(protectedUri(uri)) SecureFiles.requireActive(target)
        require(target.isFile && target.length()>0);return target
    }
    private fun cardQuery(uri:Uri,projection:Array<out String>?):Cursor {
        val target=cardFile(uri)
        val columns=(projection?:arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)).filter {it==OpenableColumns.DISPLAY_NAME || it==OpenableColumns.SIZE}
        return MatrixCursor(columns.toTypedArray(),1).apply {addRow(columns.map {if(it==OpenableColumns.DISPLAY_NAME) target.name else SecureFiles.size(target)}.toTypedArray())}
    }
    private fun cardPipe(uri:Uri,mode:String):ParcelFileDescriptor {
        if(mode!="r") throw FileNotFoundException("Solo lectura.")
        try {cardFile(uri)} catch(_:Exception) {throw FileNotFoundException("Imagen no disponible.")}
        val pipe=ParcelFileDescriptor.createReliablePipe()
        Thread({
            try {kotlinx.coroutines.runBlocking {
                com.r0ybt.arachn0de.data.repository.AttachmentRepository.fileOperations.withLock {
                    val target=cardFile(uri)
                    SecureFiles.input(target).use {input->val buffer=ByteArray(32768);while(input.read(buffer)>=0) {}}
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use {output->SecureFiles.input(target).use {it.copyTo(output)}}
                }
            }} catch(_:Exception) {runCatching {pipe[1].closeWithError("Imagen no disponible.")}}
        },"private-card-share").apply {isDaemon=true;start()}
        return pipe[0]
    }
    private fun protectedUri(uri:Uri)=uri.pathSegments.firstOrNull()=="v1"
    private fun file(uri:Uri):File {
        val parts=uri.pathSegments;require(parts.size==3 && parts[0]=="v1")
        val id=UUID.fromString(parts[1]);require(id.toString()==parts[1])
        val name=parts[2];require(name==File(name).name && name.startsWith("arachn0de-obligations-") && name.endsWith(".png"))
        val file=File(requireNotNull(context).filesDir.canonicalFile,"security-v1/stores/$id/cache/obligation-reports/$name")
        SecureFiles.requireActive(file);require(file.isFile);return file
    }
    private fun requireLegacyAllowed() {
        val app=requireNotNull(context).applicationContext as? com.r0ybt.arachn0de.Arachn0deApplication
        if(app!=null && app.security.current.value==null) throw FileNotFoundException("Informe no disponible en esta sesión.")
        if(File(requireNotNull(context).filesDir.canonicalFile,"security-v1/index").exists()) throw FileNotFoundException("Informe no disponible en esta sesión.")
    }
    override fun getType(uri:Uri):String?=if(cardUri(uri)) {cardFile(uri);"image/png"} else if(protectedUri(uri)) { file(uri);ObligationReportFiles.MIME } else {requireLegacyAllowed();super.getType(uri)}
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
        if(cardUri(uri)) return cardQuery(uri,projection)
        if(!protectedUri(uri)) {requireLegacyAllowed();return requireNotNull(super.query(uri,projection,selection,selectionArgs,sortOrder))}
        val target=file(uri)
        val columns=(projection?:arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)).filter { it==OpenableColumns.DISPLAY_NAME || it==OpenableColumns.SIZE }
        return MatrixCursor(columns.toTypedArray(),1).apply { addRow(columns.map { if(it==OpenableColumns.DISPLAY_NAME) target.name else SecureFiles.size(target) }.toTypedArray()) }
    }
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        if(cardUri(uri)) return cardPipe(uri,mode)
        if(!protectedUri(uri)) {
            // Once a vault exists, prior private cache URIs must never bypass authentication.
            requireLegacyAllowed()
            return requireNotNull(super.openFile(uri,mode))
        }
        if(mode!="r") throw FileNotFoundException("Solo lectura.")
        val target=try { file(uri) } catch(_:Exception) { throw FileNotFoundException("Informe no disponible en esta sesión.") }
        val pipe=ParcelFileDescriptor.createReliablePipe()
        Thread({
            try {
                // Authenticate the complete file before returning decrypted bytes to a recipient.
                SecureFiles.input(target).use { input -> val buffer=ByteArray(32768);while(input.read(buffer)>=0) {} }
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output -> SecureFiles.input(target).use { it.copyTo(output) } }
            } catch(_:Exception) { runCatching { pipe[1].closeWithError("Informe no disponible.") } }
        },"private-report-share").apply { isDaemon=true;start() }
        return pipe[0]
    }
}
