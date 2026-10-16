package com.r0ybt.arachn0de.audio

import com.r0ybt.arachn0de.data.local.AttachmentFileEntity
import com.r0ybt.arachn0de.data.repository.AttachmentRepository
import com.r0ybt.arachn0de.security.SecureFiles
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** Short file reservation; never hold the common file mutex while an external recipient writes. */
internal suspend fun originalAttachmentBytes(repository:AttachmentRepository,row:AttachmentFileEntity):ByteArray=withContext(Dispatchers.IO) {
    AttachmentRepository.fileOperations.withLock {
        require(row.byteSize in 1..20L*1024*1024)
        val output=WipingByteArrayOutputStream();val buffer=ByteArray(8192);val digest=MessageDigest.getInstance("SHA-256")
        try {
            SecureFiles.input(repository.localFile(row)).use {input->while(true) {
                currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0) break
                require(output.size()+n<=row.byteSize);output.write(buffer,0,n);digest.update(buffer,0,n)
            }}
            require(output.size().toLong()==row.byteSize && digest.digest().joinToString("") {"%02x".format(it)}==row.sha256) {"Adjunto incompleto o modificado."}
            output.toByteArray()
        } finally {buffer.fill(0);output.erase()}
    }
}

internal class WipingByteArrayOutputStream:ByteArrayOutputStream() {
    fun erase() {buf.fill(0);reset()}
}
