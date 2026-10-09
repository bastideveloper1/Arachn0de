package com.r0ybt.arachn0de.security

import java.io.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Sessions own controlled roots. Known protected paths fail closed even after revocation. */
internal object SecureFiles {
    internal class Session(val id:UUID,root:File,key:ByteArray) : AutoCloseable {
        val root:File=root.canonicalFile
        private val key=key.copyOf()
        private val handles=ConcurrentHashMap.newKeySet<AutoCloseable>()
        @Volatile private var open=true
        init { require(key.size==32) }
        @Synchronized fun <T> withKey(work:(ByteArray)->T):T { checkOpen();return work(key) }
        fun checkOpen() { check(open) { "Almacén bloqueado." } }
        fun retain(handle:AutoCloseable) {checkOpen();handles.add(handle)}
        fun release(handle:AutoCloseable) {handles.remove(handle)}
        override fun close() {
            synchronized(this) {open=false;key.fill(0)}
            handles.toList().forEach {runCatching {it.close()}}
            handles.clear()
        }
    }
    @Volatile var activeProfile:UUID?=null
    private val roots=ConcurrentHashMap<String,Session>()
    private val knownRoots=ConcurrentHashMap.newKeySet<String>()
    @Synchronized fun register(session:Session) {
        require(roots.keys.none { it.startsWith(session.root.path+File.separator) || session.root.path.startsWith(it+File.separator) })
        knownRoots.add(session.root.path)
        check(roots.putIfAbsent(session.root.path,session)==null) { "La raíz ya tiene una sesión." }
    }
    fun revoke(session:Session) { session.close(); roots.remove(session.root.path,session) }
    private fun session(file:File):Session? {
        val path=file.canonicalPath
        val absolute=file.absoluteFile.normalize().path
        val owner=roots.entries.firstOrNull { path.startsWith(it.key+File.separator) }?.value
        val lexical=roots.entries.firstOrNull { absolute.startsWith(it.key+File.separator) }?.value
        require(owner===lexical) { "Archivo fuera de su almacén controlado." }
        if(owner==null && (path.contains("/security-v1/stores/") || knownRoots.any { path.startsWith(it+File.separator) || absolute.startsWith(it+File.separator) })) error("Almacén bloqueado.")
        return owner
    }
    fun protected(file:File)=session(file)?.let {it.checkOpen();true} ?: false
    fun ownerId(file:File)=session(file)?.id
    fun requireActive(file:File) { val owner=requireNotNull(session(file));check(owner.id==activeProfile);owner.checkOpen() }
    fun input(file:File):InputStream {
        val session=session(file) ?: return file.inputStream()
        return session.withKey { key ->
            val stream=VaultInputStream(file.inputStream(),key,"private-v1:${session.id}",session::checkOpen)
            val reservation=AutoCloseable {stream.abort()};session.retain(reservation)
            object:FilterInputStream(stream) {
                override fun close() {try {stream.close()} finally {session.release(reservation)}}
            }
        }
    }
    /** fsync after the authenticated final record, including when used with Kotlin .use. */
    private fun durableOutput(file:File):OutputStream {
        val raw=FileOutputStream(file)
        return object:FilterOutputStream(raw) {
            override fun write(bytes:ByteArray,off:Int,len:Int) {out.write(bytes,off,len)}
            override fun close() {try {flush();raw.fd.sync()} finally {super.close()}}
        }
    }
    fun output(file:File):OutputStream {
        val session=session(file) ?: return durableOutput(file)
        // Opening/truncating the destination is authorized under the same lock as key access.
        return session.withKey { key ->
            val raw=durableOutput(file)
            val stream=try {VaultOutputStream(raw,key,"private-v1:${session.id}",session::checkOpen)}
                catch(failure:Throwable) {raw.close();throw failure}
            val reservation=AutoCloseable {stream.abort()};session.retain(reservation)
            object:FilterOutputStream(stream) {
                override fun write(bytes:ByteArray,off:Int,len:Int) {stream.write(bytes,off,len)}
                override fun close() {try {stream.close()} finally {session.release(reservation)}}
            }
        }
    }
    fun read(file:File):ByteArray=input(file).use { it.readBytes() }
    fun text(file:File):String=input(file).bufferedReader(Charsets.UTF_8).use { it.readText() }
    fun write(file:File,bytes:ByteArray) { output(file).use { it.write(bytes) } }
    fun size(file:File):Long {
        if(!protected(file)) return file.length()
        var size=0L
        input(file).use { stream -> val buffer=ByteArray(32768);while(true) { val n=stream.read(buffer);if(n<0) break;size+=n };buffer.fill(0) }
        return size
    }
    fun decoded(file:File,options:android.graphics.BitmapFactory.Options?=null):android.graphics.Bitmap? =
        input(file).use { android.graphics.BitmapFactory.decodeStream(it,null,options) }
    fun orientation(file:File):Int = input(file).use {
        android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)
    }
}
