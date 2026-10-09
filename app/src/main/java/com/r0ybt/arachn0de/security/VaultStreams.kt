package com.r0ybt.arachn0de.security

import java.io.*

/** Same records as VaultCrypto, bounded memory and revocable session access. */
internal class VaultInputStream(input: InputStream, master: ByteArray, private val purpose: String,
    private val authorized: () -> Unit = {}) : InputStream() {
    private val source = DataInputStream(input)
    private val header: ByteArray
    private val key: ByteArray
    private var index = 0L
    private var buffer = ByteArray(0)
    private var offset = 0
    private var ended = false
    private var closed = false
    init {
        try {
            authorized()
            header=ByteArray(40).also(source::readFully)
            require(header.copyOfRange(0,8).contentEquals(VaultCrypto.streamMagic))
            key=VaultCrypto.subkey(master,header.copyOfRange(8,40),"stream-v1:$purpose")
        } catch(failure:Exception) { source.close();throw VaultAuthenticationException(failure) }
    }
    private fun next():Boolean {
        authorized();check(!closed)
        if(offset<buffer.size) return true
        buffer.fill(0)
        if(ended) return false
        try {
            require(index<Long.MAX_VALUE)
            val size=source.readInt();require(size in 0..VaultCrypto.CHUNK_BYTES)
            val cipher=ByteArray(size+16).also(source::readFully)
            buffer=VaultCrypto.gcm(javax.crypto.Cipher.DECRYPT_MODE,key,VaultCrypto.nonce(index),VaultCrypto.aad(header,purpose,index,size),cipher)
            offset=0;index++
            if(size==0) { require(source.read()==-1);ended=true;return false }
            return true
        } catch(failure:Exception) { throw VaultAuthenticationException(failure) }
    }
    @Synchronized override fun read():Int = if(next()) buffer[offset++].toInt() and 255 else -1
    @Synchronized override fun read(bytes:ByteArray,off:Int,len:Int):Int {
        require(off>=0 && len>=0 && off<=bytes.size-len)
        if(len==0) return 0
        if(!next()) return -1
        val count=minOf(len,buffer.size-offset)
        buffer.copyInto(bytes,off,offset,offset+count);offset+=count;return count
    }
    @Synchronized fun abort() {
        if(closed) return
        closed=true;buffer.fill(0);key.fill(0);source.close()
    }
    @Synchronized override fun close() {
        if(closed) return
        try { while(next()) offset=buffer.size } // Verify skipped tail; BitmapFactory may stop before EOF.
        finally { closed=true;buffer.fill(0);key.fill(0);source.close() }
    }
}

internal class VaultOutputStream(output:OutputStream,master:ByteArray,private val purpose:String,
    private val authorized:()->Unit = {}) : OutputStream() {
    private val target=DataOutputStream(output)
    private val salt=VaultCrypto.randomKey()
    private val header=VaultCrypto.streamMagic+salt
    private val key=VaultCrypto.subkey(master,salt,"stream-v1:$purpose")
    private val buffer=ByteArray(VaultCrypto.CHUNK_BYTES)
    private var size=0
    private var index=0L
    private var closed=false
    init { try { authorized();target.write(header) } catch(failure:Throwable) { key.fill(0);target.close();throw failure } }
    private fun record() {
        authorized();check(!closed);require(index<Long.MAX_VALUE)
        val plain=buffer.copyOf(size)
        val cipher=try { VaultCrypto.gcm(javax.crypto.Cipher.ENCRYPT_MODE,key,VaultCrypto.nonce(index),VaultCrypto.aad(header,purpose,index,size),plain) }
            finally { plain.fill(0) }
        target.writeInt(size);target.write(cipher);buffer.fill(0);size=0;index++
    }
    @Synchronized override fun write(b:Int) { authorized();check(!closed);buffer[size++]=b.toByte();if(size==buffer.size) record() }
    @Synchronized override fun write(bytes:ByteArray,off:Int,len:Int) {
        require(off>=0 && len>=0 && off<=bytes.size-len)
        authorized();check(!closed)
        var position=off;var remaining=len
        while(remaining>0) {
            val count=minOf(remaining,buffer.size-size)
            bytes.copyInto(buffer,size,position,position+count);size+=count;position+=count;remaining-=count
            if(size==buffer.size) record()
        }
    }
    @Synchronized override fun flush() { authorized();check(!closed);target.flush() } // Don't emit partial records.
    @Synchronized fun abort() {
        if(closed) return
        closed=true;buffer.fill(0);key.fill(0);target.close()
    }
    @Synchronized override fun close() {
        if(closed) return
        try { if(size>0) record();record();target.flush() }
        finally { closed=true;buffer.fill(0);key.fill(0);target.close() }
    }
}
