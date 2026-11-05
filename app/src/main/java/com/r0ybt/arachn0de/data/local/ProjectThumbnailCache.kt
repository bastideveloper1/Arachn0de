package com.r0ybt.arachn0de.data.local

import com.r0ybt.arachn0de.backup.syncBackupDirectory
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.r0ybt.arachn0de.security.SecureFiles
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.r0ybt.arachn0de.data.repository.AttachmentRepository

/** Regenerable encrypted disk derivatives, never a source or backup input. */
internal object ProjectThumbnailCache {
    const val MAX_DISK_BYTES=64L*1024*1024
    private val names=Regex("[a-f0-9-]{36}-[a-f0-9]{64}\\.png(?:\\.part)?")
    // One reader owns this durable lease namespace; after a crash the next reader
    // can release its predecessors without expiring any editor/backup reservation.
    private const val READER_OWNER="project-thumbnail-reader"
    private val readers=Mutex()
    private var lastPruneAt=0L
    fun read(context:Context,source:File,requested:Int,syncDirectory:(File)->Unit=::syncBackupDirectory):Bitmap? = runBlocking {
        readers.withLock reading@ {
            require(source.canonicalFile.parentFile==File(context.filesDir.canonicalFile,"project-photos"))
            require(com.r0ybt.arachn0de.backup.imageNameValid("project-photos",source.name))
            var reserved=false
            val lifecycle=ImageFileLifecycle(context,"project-photos",syncDirectory)
            try {
                val identity=AttachmentRepository.fileOperations.withLock acquiring@ {
                    if(SecureFiles.protected(source)) SecureFiles.requireActive(source)
                    lifecycle.reservations(READER_OWNER).forEach {lifecycle.release(it,READER_OWNER)}
                    if(!source.isFile) return@acquiring null
                    reserved=true
                    lifecycle.reserve(source.name,READER_OWNER)
                    "${source.canonicalPath}:${source.length()}:${source.lastModified()}:${requested.coerceIn(96,2048)}"
                } ?: return@reading null
                if(SecureFiles.protected(source)) SecureFiles.requireActive(source)
                require(source.canonicalFile.parentFile==source.parentFile?.canonicalFile)
                val root=File(context.cacheDir,"project-thumbnails")
                require(root.canonicalFile==File(context.cacheDir.canonicalFile,"project-thumbnails"))
                check(root.isDirectory || root.mkdirs())
                prune(root)
                val dimension=requested.coerceIn(96,2048)
                val hash=MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") {"%02x".format(it)}
                val target=File(root,"${source.nameWithoutExtension}-$hash.png")
                require(target.canonicalFile.parentFile==root.canonicalFile)
                if(target.isFile) {
                    val cached=runCatching {PrivateImageCache.read(target,dimension,immutable=true)}.getOrNull()
                    if(cached!=null) {
                        if(SecureFiles.protected(source)) SecureFiles.requireActive(source)
                        target.setLastModified(System.currentTimeMillis());return@reading cached
                    }
                    target.delete();PrivateImageCache.invalidate(target)
                }
                val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                SecureFiles.decoded(source,bounds)
                if(bounds.outWidth<=0 || bounds.outHeight<=0) return@reading null
                var sample=1
                while(maxOf(bounds.outWidth,bounds.outHeight)/(sample*2)>=dimension) sample*=2
                var bitmap=SecureFiles.decoded(source,BitmapFactory.Options().apply {inSampleSize=sample}) ?: return@reading null
                if(maxOf(bitmap.width,bitmap.height)>dimension) {
                    val factor=dimension.toFloat()/maxOf(bitmap.width,bitmap.height)
                    val resized=Bitmap.createScaledBitmap(bitmap,(bitmap.width*factor).toInt().coerceAtLeast(1),(bitmap.height*factor).toInt().coerceAtLeast(1),true)
                    if(resized!==bitmap) bitmap.recycle()
                    bitmap=resized
                }
                val part=File(root,target.name+".part")
                try {
                    SecureFiles.output(part).use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
                    check(part.renameTo(target));prune(root,force=true)
                    if(SecureFiles.protected(source)) SecureFiles.requireActive(source)
                    bitmap
                } finally {part.delete()}
            } finally {
                if(reserved)withContext(NonCancellable) {
                    AttachmentRepository.fileOperations.withLock {lifecycle.release(source.name,READER_OWNER)}
                }
            }
        }
    }
    private fun prune(root:File,force:Boolean=false) {
        val now=System.nanoTime()
        if(!force && lastPruneAt!=0L && now-lastPruneAt<60_000_000_000L) return

        val files=root.listFiles().orEmpty().filter {it.isFile && names.matches(it.name) && it.canonicalFile.parentFile==root.canonicalFile}
        files.filter {it.name.endsWith(".part")}.forEach {it.delete()}
        val cached=files.filter {it.name.endsWith(".png")}.sortedBy {it.lastModified()}
        var size=cached.sumOf {it.length()}
        for(file in cached) {if(size<=MAX_DISK_BYTES) break;val length=file.length();if(file.delete()) {size-=length;PrivateImageCache.invalidate(file)}}
        lastPruneAt=now
    }
}
