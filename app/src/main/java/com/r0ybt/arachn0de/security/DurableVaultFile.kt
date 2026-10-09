package com.r0ybt.arachn0de.security

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Durable commit marker separates rollback from cleanup; originals survive pre-commit errors. */
internal object DurableVaultFile {
    private fun sibling(file:File,suffix:String)=File(file.parentFile,"${file.name}.$suffix")
    private fun write(file:File,bytes:ByteArray) { FileOutputStream(file).use { it.write(bytes);it.fd.sync() } }
    private fun hash(file:File):ByteArray=file.inputStream().use { input ->
        val hash=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(32768)
        while(true) { val n=input.read(buffer);if(n<0) break;hash.update(buffer,0,n) };hash.digest()
    }
    fun recover(file:File,sync:(File)->Unit) {
        val pending=sibling(file,"pending");val old=sibling(file,"previous");val commit=sibling(file,"commit")
        if(!pending.exists()) {
            if(commit.exists()) { check(file.exists() && MessageDigest.isEqual(commit.readBytes(),hash(file)));cleanup(file,sync) }
            else check(!old.exists()) { "Diario incompleto." }
            return
        }
        val decision=pending.readBytes();require(decision.size==1 && decision[0].toInt() in 0..1)
        val committed=commit.exists() && file.exists() && MessageDigest.isEqual(commit.readBytes(),hash(file))
        if(!committed) {
            if(old.exists()) { check(old.renameTo(file));sync(file.parentFile!!) }
            else if(decision[0].toInt()==0) { check(!file.exists() || file.delete());sync(file.parentFile!!) }
            // An original still at base means the interruption preceded its rename.
        }
        cleanup(file,sync)
    }
    private fun cleanup(file:File,sync:(File)->Unit) {
        for(suffix in listOf("staged","previous")) {
            val item=sibling(file,suffix);check(!item.exists() || item.delete())
        }
        sync(file.parentFile!!)
        val pending=sibling(file,"pending");check(!pending.exists() || pending.delete());sync(file.parentFile!!)
        val commit=sibling(file,"commit");check(!commit.exists() || commit.delete());sync(file.parentFile!!)
    }
    fun replace(file:File,encryptedBytes:ByteArray,sync:(File)->Unit) {
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs());recover(file,sync)
        val part=sibling(file,"staged");val old=sibling(file,"previous");val pending=sibling(file,"pending");val commit=sibling(file,"commit")
        var committed=false
        try {
            write(pending,byteArrayOf(if(file.exists()) 1 else 0));sync(file.parentFile!!)
            write(part,encryptedBytes);sync(file.parentFile!!)
            if(file.exists()) { check(file.renameTo(old));sync(file.parentFile!!) }
            check(part.renameTo(file));sync(file.parentFile!!)
            write(commit,hash(file));sync(file.parentFile!!);committed=true
        } catch(failure:Throwable) {
            // A commit marker whose fsync failed is not an acknowledged commit.
            runCatching { check(!commit.exists() || commit.delete());recover(file,sync) }
            throw failure
        } finally {
            // Post-commit cleanup failure never turns an acknowledged commit into rollback.
            if(committed) runCatching { cleanup(file,sync) }
        }
    }
}
