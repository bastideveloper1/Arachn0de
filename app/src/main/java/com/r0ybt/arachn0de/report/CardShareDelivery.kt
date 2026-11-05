package com.r0ybt.arachn0de.report

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import com.r0ybt.arachn0de.security.SecureFiles
import java.io.File
import java.io.IOException

/** Authenticated snapshot in RAM. No file/key is needed once the shared mutex is released.
 * A nonblocking pipe keeps cancellation independent of the recipient's read rate. */
internal class CardShareDelivery private constructor(
    private var bytes:ByteArray,
    private val owner:SecureFiles.Session?,
    private val authorized:()->Unit,
    private val clock:()->Long,
):AutoCloseable {
    companion object {
        private val budget=Any()
        private var count=0
        private var retained=0L
        private fun reserve(size:Int)=synchronized(budget) {
            require(count<4 && retained+size<=CardShareFiles.MAX_BYTES) {"Hay demasiadas tarjetas en proceso de envío."}
            count++;retained+=size
        }
        private fun release(size:Int)=synchronized(budget) {count--;retained-=size}

        /** Called under AttachmentRepository.fileOperations; no external writes here. */
        fun prepare(file:File,owner:SecureFiles.Session?,clock:()->Long=android.os.SystemClock::elapsedRealtime,authorized:()->Unit):CardShareDelivery {
            authorized()
            // Authenticate before exposing any byte and allocate one exact-size buffer.
            val size=SecureFiles.size(file);require(size in 1..CardShareFiles.MAX_BYTES)
            reserve(size.toInt())
            var data:ByteArray?=null
            try {
                val contents=ByteArray(size.toInt());data=contents;authorized()
                SecureFiles.input(file).use {input->
                    var at=0
                    while(at<contents.size) {authorized();val n=input.read(contents,at,contents.size-at);require(n>0);at+=n}
                    require(input.read()==-1) {"La tarjeta cambió durante la preparación."}
                }
                authorized()
                return CardShareDelivery(contents,owner,authorized,clock).also {owner?.retain(it)}
            } catch(failure:Throwable) {
                data?.fill(0);release(size.toInt())
                if(failure is OutOfMemoryError)throw IOException("No hay memoria suficiente para compartir la tarjeta.",failure)
                throw failure
            }
        }
    }
    private val size=bytes.size
    @Volatile private var closed=false
    private var started=false
    override fun close() {
        synchronized(this) {
            if(closed)return
            closed=true;bytes.fill(0);bytes=ByteArray(0);release(size)
        }
        owner?.release(this)
    }
    fun start():ParcelFileDescriptor {
        val pipe=try {ParcelFileDescriptor.createReliablePipe()} catch(t:Throwable) {close();throw t}
        try {
            synchronized(this) {check(!closed && !started);authorized();started=true}
            val fd=pipe[1].fileDescriptor
            Os.fcntlInt(fd,OsConstants.F_SETFL,Os.fcntlInt(fd,OsConstants.F_GETFL,0) or OsConstants.O_NONBLOCK)
            Thread({
                var complete=false
                try {
                    val ready=StructPollfd().apply {this.fd=fd;events=OsConstants.POLLOUT.toShort()}
                    var at=0;var progressed=clock()
                    while(at<size) {
                        if(closed)throw IOException("Envío cancelado.")
                        authorized()
                        if(clock()-progressed>=60_000)throw IOException("El destinatario dejó de consumir la tarjeta.")
                        try {Os.poll(arrayOf(ready),100)} catch(e:ErrnoException) {if(e.errno==OsConstants.EINTR)continue;throw e}
                        if(ready.revents.toInt() and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL)!=0)throw IOException("Destinatario cerrado.")
                        if(ready.revents.toInt() and OsConstants.POLLOUT==0)continue
                        try {
                            // write is nonblocking: neither wiping nor revocation waits for a reader.
                            synchronized(this) {check(!closed);authorized();val n=Os.write(fd,bytes,at,minOf(4096,size-at));at+=n;if(n>0)progressed=clock()}
                        } catch(e:ErrnoException) {if(e.errno!=OsConstants.EAGAIN && e.errno!=OsConstants.EINTR)throw e}
                    }
                    complete=true
                } catch(_:Exception) {
                    // Reliable-pipe error is sent by the writer itself; no cross-thread fd reuse.
                } finally {
                    close()
                    if(complete)runCatching {pipe[1].close()}
                    else runCatching {pipe[1].closeWithError("Imagen no disponible en esta sesión.")}
                }
            },"private-card-share").apply {isDaemon=true;start()}
            return pipe[0]
        } catch(t:Throwable) {close();pipe.forEach {runCatching {it.close()}};throw t}
    }
}
