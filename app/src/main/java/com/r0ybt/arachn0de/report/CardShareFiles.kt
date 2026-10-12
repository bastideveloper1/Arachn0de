package com.r0ybt.arachn0de.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.r0ybt.arachn0de.security.SecureFiles
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import com.r0ybt.arachn0de.backup.syncBackupDirectory

/** Regenerable, session-private cache. No confirmed user data is owned by this class. */
internal class CardShareFiles(private val context:Context,private val clock:()->Long=System::currentTimeMillis,
    private val sync:(File)->Unit=::syncBackupDirectory) {
    companion object {
        const val DIRECTORY="card-shares"
        const val RETENTION=24L*60*60*1000
        const val MAX_FILES=24
        const val MAX_BYTES=64L*1024*1024
        fun validName(name:String)=Regex("arachn0de-card-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.png").matches(name)
    }
    private val directory get()=File(context.cacheDir.canonicalFile,DIRECTORY).also {
        require(it.canonicalFile==it.absoluteFile) {"Directorio temporal no válido."}
    }
    private fun checked(file:File):File {
        require(validName(file.name) && file.canonicalFile.parentFile==directory.canonicalFile)
        require(file.isFile && file.length()>0) {"La imagen no está disponible."};return file
    }
    private fun contentUri(file:File):Uri {
        require(validName(file.name) && file.canonicalFile==File(directory.canonicalFile,file.name))
        val builder=Uri.Builder().scheme("content").authority("${context.packageName}.reports")
        if(SecureFiles.protected(file)) {
            SecureFiles.requireActive(file)
            builder.appendPath("v1").appendPath(SecureFiles.ownerId(file).toString())
        } else builder.appendPath("cards")
        return builder.appendPath(file.name).build()
    }
    fun uri(file:File):Uri {checked(file);return contentUri(file)}
    fun shareIntent(file:File)=Intent(Intent.ACTION_SEND).apply {
        val content=uri(file);type="image/png";putExtra(Intent.EXTRA_STREAM,content)
        clipData=ClipData.newRawUri("Tarjeta de Arachn0de",content);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    /** Copy the same generated PNG to a newly selected SAF document, without storage permissions. */
    suspend fun save(file:File,destination:Uri)=withContext(Dispatchers.IO) {
        require(destination.scheme=="content")
        AttachmentRepository.fileOperations.withLock {
            try {
                SecureFiles.input(checked(file)).use {input->
                    val output=context.contentResolver.openOutputStream(destination,"wt")
                        ?: throw java.io.IOException("No se pudo abrir el destino.")
                    output.use {stream->
                        val buffer=ByteArray(32*1024)
                        while(true) {
                            currentCoroutineContext().ensureActive()
                            val count=input.read(buffer);if(count<0) break
                            stream.write(buffer,0,count)
                        }
                        stream.flush()
                    }
                }
            } catch(failure:Throwable) {
                runCatching {android.provider.DocumentsContract.deleteDocument(context.contentResolver,destination)}
                throw failure
            }
        }
    }
    suspend fun cleanup()=withContext(Dispatchers.IO) {AttachmentRepository.fileOperations.withLock {cleanupLocked()}}
    private fun cleanupLocked() {
        if(!directory.isDirectory) return
        // Under the common mutex no writer or sharing pipe can still own a staging file.
        directory.listFiles().orEmpty().filter {it.name.endsWith(".part") && validName(it.name.removeSuffix(".part"))}.forEach {it.delete()}
        directory.listFiles().orEmpty().filter {validName(it.name)}.forEach {file->
            val pending=File(directory,file.name+".delete")
            if(pending.exists() || clock()-file.lastModified()>=RETENTION) {
                if(!pending.exists()) {SecureFiles.write(pending,byteArrayOf(1));sync(directory)}
                // Revoke delivery grants first; the mutex also excludes live read pipes.
                context.revokeUriPermission(contentUri(file),Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if(file.delete()) {pending.delete();sync(directory)}
            }
        }
        directory.listFiles().orEmpty().filter {it.name.endsWith(".delete") && validName(it.name.removeSuffix(".delete"))}
            .filter { !File(directory,it.name.removeSuffix(".delete")).exists() }.forEach {it.delete()}
    }
    suspend fun generate(source:suspend ()->CardShareData,colors:CardShareColors):File=withContext(Dispatchers.IO) {
        AttachmentRepository.fileOperations.withLock {
            cleanupLocked()
            check(directory.isDirectory || directory.mkdirs()) {"No se pudo preparar la imagen."}
            val existing=directory.listFiles().orEmpty().filter {validName(it.name)}
            check(existing.size<MAX_FILES && existing.sumOf {it.length()}<MAX_BYTES) {"La caché temporal de imágenes está llena. Los envíos recientes se conservan."}
            val target=File(directory,"arachn0de-card-${UUID.randomUUID()}.png")
            val part=File(directory,target.name+".part")
            val job=currentCoroutineContext()
            try {
                val data=source();job.ensureActive()
                val bitmap=CardShareRenderer.render(data,colors) {job.ensureActive()}
                try {SecureFiles.output(part).use {output->ObligationReportFiles.writePng(bitmap,output);job.ensureActive()}}
                finally {bitmap.recycle()}
                check(existing.sumOf {it.length()}+part.length()<=MAX_BYTES) {"La imagen supera la capacidad temporal disponible."}
                check(part.length()>0 && part.renameTo(target)) {"No se pudo completar la imagen."}
                sync(directory);job.ensureActive();target
            } catch(failure:Throwable) {
                part.delete()
                if(target.exists() && !target.delete()) runCatching {
                    SecureFiles.write(File(directory,target.name+".delete"),byteArrayOf(1));sync(directory)
                }
                throw failure
            }
        }
    }
    suspend fun discard(file:File)=withContext(Dispatchers.IO) {AttachmentRepository.fileOperations.withLock {
        checked(file);SecureFiles.write(File(directory,file.name+".delete"),byteArrayOf(1));sync(directory);cleanupLocked()
    }}
}
