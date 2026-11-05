package com.r0ybt.arachn0de.export

import android.content.Context
import com.r0ybt.arachn0de.backup.syncBackupDirectory
import com.r0ybt.arachn0de.security.VaultCrypto
import com.r0ybt.arachn0de.security.VaultInputStream
import com.r0ybt.arachn0de.security.VaultOutputStream
import java.io.*
import java.util.UUID

/** Regenerable public derivatives only. Call under AttachmentRepository.fileOperations.
 * Durable leases identify owned files; keys live only in RAM. A restart loses the key and
 * cancels these derivatives, never confirmed source data. Live leases never expire by age. */
internal class PublicExportStaging(context:Context,private val delete:(File)->Boolean={it.delete()},private val sync:(File)->Unit=::syncBackupDirectory):AutoCloseable {
    companion object {
        private const val DIRECTORY="public-exports-v1"
        private const val MAGIC="public-export-v1\n"
        private val process=UUID.randomUUID().toString()
        private val active=hashSetOf<String>()
        private val operations=Any()
        private fun uuid(s:String)=runCatching {UUID.fromString(s).toString()==s}.getOrDefault(false)
        private fun root(context:Context):File {
            val cache=context.cacheDir.canonicalFile;val directory=File(cache,DIRECTORY)
            require(directory.canonicalFile==directory.absoluteFile) {"Staging fuera del directorio controlado."}
            return directory
        }
        fun recover(context:Context,sync:(File)->Unit=::syncBackupDirectory)=synchronized(operations) {recoverRoot(root(context),sync=sync)}
        private fun recoverRoot(root:File,delete:(File)->Boolean={it.delete()},sync:(File)->Unit=::syncBackupDirectory) {
            if(!root.exists())return
            root.listFiles().orEmpty().filter {it.name.endsWith(".lease") && uuid(it.name.removeSuffix(".lease"))}.forEach {lease->
                require(lease.canonicalFile.parentFile==root.canonicalFile && lease.length()<=128)
                val owner=lease.readText();require(owner.startsWith(MAGIC) && uuid(owner.removePrefix(MAGIC)))
                if(lease.canonicalPath in active)return@forEach
                val directory=File(root,lease.name.removeSuffix(".lease"))
                require(directory.canonicalFile.parentFile==root.canonicalFile)
                if(directory.exists()) {
                    val files=requireNotNull(directory.listFiles());var clean=true
                    files.forEach {file->
                        if(file.isFile && file.canonicalFile.parentFile==directory.canonicalFile && file.name.endsWith(".enc") && uuid(file.name.removeSuffix(".enc"))) {
                            if(!delete(file))clean=false
                        } else clean=false
                    }
                    if(!clean || !delete(directory))return@forEach
                }
                if(delete(lease))sync(root)
            }
        }
    }
    private val root=root(context)
    private val id=UUID.randomUUID().toString()
    private val lease=File(root,"$id.lease")
    private val directory=File(root,id)
    private val key=VaultCrypto.randomKey()
    private var closed=false
    init {synchronized(operations) {
        try {
            val created=!root.exists();check(root.isDirectory || root.mkdirs());if(created)sync(root.parentFile!!);recoverRoot(root,sync=sync)
            check(lease.createNewFile())
            FileOutputStream(lease).use {it.write((MAGIC+process).toByteArray());it.fd.sync()};sync(root)
            check(directory.mkdir());sync(root);active.add(lease.canonicalPath)
        } catch(t:Throwable) {key.fill(0);throw t}
    }}
    fun put(bytes:ByteArray):String {
        require(bytes.size<=20*1024*1024)
        return put(bytes.size.toLong()) {it.write(bytes)}
    }
    /** Stream one rendered PNG without retaining a second encoded image in RAM. */
    fun put(maxBytes:Long,write:(OutputStream)->Unit):String {
        check(!closed);require(maxBytes in 0..64L*1024*1024)
        val existing=root.listFiles().orEmpty().filter {it.isDirectory && uuid(it.name) && it.canonicalFile.parentFile==root.canonicalFile}.sumOf {dir->dir.listFiles().orEmpty().filter {it.isFile && it.canonicalFile.parentFile==dir.canonicalFile}.sumOf {it.length()}}
        val name="${UUID.randomUUID()}.enc";val target=File(directory,name);check(target.createNewFile())
        FileOutputStream(target).use {raw->
            val borrowed=object:FilterOutputStream(raw) {override fun write(b:ByteArray,o:Int,n:Int) {out.write(b,o,n)};override fun close() {flush()}}
            VaultOutputStream(borrowed,key,"public-export-staging-v1").use {encrypted->
                var size=0L
                fun reserve(n:Int) {
                    require(n>=0 && size+n<=maxBytes) {"La imagen supera la capacidad temporal disponible."}
                    val next=size+n
                    require(existing+next+next/65536L*20+80<=512L*1024*1024) {"La caché cifrada de exportación requiere limpieza antes de continuar."}
                    size=next
                }
                val bounded=object:OutputStream() {
                    override fun write(value:Int) {reserve(1);encrypted.write(value)}
                    override fun write(bytes:ByteArray,offset:Int,count:Int) {reserve(count);encrypted.write(bytes,offset,count)}
                    override fun flush() {encrypted.flush()}
                }
                write(bounded)
            }
            raw.fd.sync()
        };sync(directory);return name
    }
    fun input(name:String):InputStream {
        check(!closed);require(name.endsWith(".enc") && uuid(name.removeSuffix(".enc")))
        return VaultInputStream(File(directory,name).inputStream(),key,"public-export-staging-v1") {check(!closed)}
    }
    override fun close() {synchronized(operations) {
        if(closed)return
        closed=true;key.fill(0);active.remove(lease.canonicalPath)
        // A cleanup failure leaves its durable lease for retry, without failing a completed export.
        runCatching {recoverRoot(root,delete,sync)}
    }}
}
