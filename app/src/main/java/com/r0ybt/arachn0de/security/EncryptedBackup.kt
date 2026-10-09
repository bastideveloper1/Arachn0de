package com.r0ybt.arachn0de.security

import java.io.*
import java.util.UUID

/** Portable authenticated envelope; the password wraps a fresh key, never the vault's key. */
internal object EncryptedBackup {
    private val magic = "ANBACK01".toByteArray(Charsets.US_ASCII)
    fun output(output:OutputStream,password:CharArray):VaultOutputStream {
        val id=UUID.randomUUID();val key=VaultCrypto.randomKey()
        try {
            val stream=DataOutputStream(output)
            stream.write(magic);stream.writeLong(id.mostSignificantBits);stream.writeLong(id.leastSignificantBits)
            stream.write(VaultCrypto.wrap(id,password,key))
            return VaultOutputStream(stream,key,"backup-v1:$id")
        } finally { key.fill(0) }
    }
    fun input(input:InputStream,password:CharArray):InputStream {
        var key:ByteArray?=null
        try {
            val stream=DataInputStream(input)
            require(ByteArray(8).also(stream::readFully).contentEquals(magic))
            val id=UUID(stream.readLong(),stream.readLong())
            key=VaultCrypto.unwrap(id,password,ByteArray(128).also(stream::readFully))
            return VaultInputStream(stream,key,"backup-v1:$id")
        } catch(failure:Exception) { throw VaultAuthenticationException(failure) }
        finally { key?.fill(0) }
    }
    fun write(input:InputStream,output:OutputStream,password:CharArray) {
        val stream=this.output(output,password)
        try { input.copyTo(stream) } catch(failure:Throwable) { stream.abort();throw failure } finally { stream.close() }
    }
    fun read(input:InputStream,output:OutputStream,password:CharArray) { this.input(input,password).use { it.copyTo(output) } }
    fun isEncrypted(prefix:ByteArray)=prefix.contentEquals(magic)
}
