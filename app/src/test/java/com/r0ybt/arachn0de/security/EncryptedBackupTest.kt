package com.r0ybt.arachn0de.security

import java.io.*
import org.junit.Assert.*
import org.junit.Test

class EncryptedBackupTest {
    private fun write(value: ByteArray,password: CharArray)=ByteArrayOutputStream().also {
        EncryptedBackup.write(ByteArrayInputStream(value),it,password)
    }.toByteArray()
    private fun read(value: ByteArray,password: CharArray)=ByteArrayOutputStream().also {
        EncryptedBackup.read(ByteArrayInputStream(value),it,password)
    }.toByteArray()
    private fun rejected(work:()->Unit) { try { work();fail("Must reject archive") } catch (_:VaultAuthenticationException) {} }
    @Test fun portableArchiveRetainsBytesAndHasIndependentSaltsKeysAndNoPlainData() {
        val secret="Proyecto confidencial ñ €".toByteArray(); val password="recuperación segura".toCharArray()
        val first=write(secret,password);val second=write(secret,password)
        assertFalse(first.contentEquals(second));assertFalse(String(first,Charsets.ISO_8859_1).contains("Proyecto confidencial"))
        assertArrayEquals(secret,read(first,password));assertArrayEquals(secret,read(second,password))
    }
    @Test fun wrongPasswordCorruptionAndIncompleteArchiveAreRejected() {
        val password="offline".toCharArray();val first=write(ByteArray(150000){71},password)
        rejected { read(first,"wrong".toCharArray()) }
        for(offset in listOf(0,8,24,48,110,152,192,first.lastIndex)) {
            val corrupt=first.copyOf();corrupt[offset]=(corrupt[offset].toInt() xor 1).toByte()
            rejected { read(corrupt,password) }
        }
        rejected { read(first.copyOf(first.size-1),password) }
        rejected { read(first+byteArrayOf(0),password) }
    }
    @Test fun writeFailuresDoNotBecomeApparentlyValidBackups() {
        val input=object:InputStream() { var count=0;override fun read():Int { if(count++>70000) throw IOException("Injected input failure"); return 1 } }
        val partial=ByteArrayOutputStream()
        try { EncryptedBackup.write(input,partial,"test".toCharArray()); fail("Must propagate read failure") } catch (_:IOException) {}
        rejected { read(partial.toByteArray(),"test".toCharArray()) }
    }
}
