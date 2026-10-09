package com.r0ybt.arachn0de.security
import java.io.*
import org.junit.Assert.*
import org.junit.Test

class VaultStreamsTest {
    @Test fun interoperableWithCoreFormatIncludingRepeatedFlushAndPartialReads() {
        val key=VaultCrypto.randomKey();val source=ByteArray(200001){it.toByte()};val encoded=ByteArrayOutputStream()
        VaultOutputStream(encoded,key,"test").use { output -> source.asList().chunked(700).forEach { chunk -> output.write(chunk.toByteArray());output.flush() } }
        val restored=ByteArrayOutputStream();VaultCrypto.decrypt(ByteArrayInputStream(encoded.toByteArray()),restored,key,"test")
        assertArrayEquals(source,restored.toByteArray())
        val fromCore=ByteArrayOutputStream();VaultCrypto.encrypt(ByteArrayInputStream(source),fromCore,key,"test")
        assertArrayEquals(source,VaultInputStream(ByteArrayInputStream(fromCore.toByteArray()),key,"test").use { it.readBytes() })
    }
    @Test fun closeVerifiesTailEvenIfConsumerReadsOnlyFirstByte() {
        val key=VaultCrypto.randomKey();val out=ByteArrayOutputStream();VaultCrypto.encrypt(ByteArrayInputStream(ByteArray(100000){3}),out,key,"test")
        val corrupt=out.toByteArray().dropLast(1).toByteArray()
        val stream=VaultInputStream(ByteArrayInputStream(corrupt),key,"test");assertEquals(3,stream.read())
        try { stream.close();fail("Must validate skipped tail") } catch(_:VaultAuthenticationException) {}
    }
    @Test fun existingStreamsRejectAccessAfterSessionIsRevoked() {
        val key=VaultCrypto.randomKey();val out=ByteArrayOutputStream();VaultCrypto.encrypt(ByteArrayInputStream(byteArrayOf(1,2)),out,key,"test")
        var allowed=true
        val stream=VaultInputStream(ByteArrayInputStream(out.toByteArray()),key,"test") { check(allowed) }
        assertEquals(1,stream.read());allowed=false
        try { stream.read();fail("Session must be checked for buffered reads") } catch(_:IllegalStateException) {}
        try { stream.close() } catch(_:IllegalStateException) {}
    }
}
