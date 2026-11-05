package com.r0ybt.arachn0de.security

import java.io.*
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class SecureFilesTest {
    @Test fun independentStoresAndRevokedReferencesCannotReadFilesOrReuseStreams() {
        val root=Files.createTempDirectory("vault-isolation").toFile()
        val a=File(root,"a").apply { mkdir() };val b=File(root,"b").apply { mkdir() }
        val main=SecureFiles.Session(UUID.randomUUID(),a,VaultCrypto.randomKey())
        val decoy=SecureFiles.Session(UUID.randomUUID(),b,VaultCrypto.randomKey())
        SecureFiles.register(main);SecureFiles.register(decoy)
        try {
            val private=File(a,"record");SecureFiles.write(private,"main secret".toByteArray())
            val other=File(b,"record");SecureFiles.write(other,"decoy".toByteArray())
            assertEquals("main secret",SecureFiles.text(private));assertEquals("decoy",SecureFiles.text(other))
            assertFalse(private.readBytes().toString(Charsets.ISO_8859_1).contains("main secret"))
            assertEquals(11L,SecureFiles.size(private))
            private.copyTo(File(b,"transplanted"))
            try { SecureFiles.read(File(b,"transplanted"));fail("Key/context isolation required") } catch(_:VaultAuthenticationException) {}
            val input=SecureFiles.input(private);assertEquals('m'.code,input.read());SecureFiles.revoke(main)
            try { input.read();fail("Existing handles must be revoked") } catch(_:IllegalStateException) {}
            try { input.close() } catch(_:IllegalStateException) {}
            try { SecureFiles.read(private);fail("No plaintext fallback when locked") } catch(_:IllegalStateException) {}
            assertEquals("decoy",SecureFiles.text(other))
        } finally { SecureFiles.revoke(main);SecureFiles.revoke(decoy);root.deleteRecursively() }
    }
    @Test fun symlinkCannotEscapeOrCrossAStoreBoundary() {
        val root=Files.createTempDirectory("vault-links").toFile();val store=File(root,"a").apply { mkdir() }
        val outside=File(root,"outside").apply { writeText("plaintext") }
        val session=SecureFiles.Session(UUID.randomUUID(),store,VaultCrypto.randomKey());SecureFiles.register(session)
        try {
            val link=File(store,"link");Files.createSymbolicLink(link.toPath(),outside.toPath())
            try { SecureFiles.read(link);fail("Symlink must not bypass session") } catch(_:IllegalArgumentException) {}
            assertEquals("plaintext",outside.readText())
        } finally { SecureFiles.revoke(session);root.deleteRecursively() }
    }
    @Test fun unknownOrdinaryPathsRetainLegacyReadingForMigrationOnly() {
        val file=File.createTempFile("legacy-migration", ".txt")
        try { file.writeText("existing data");assertEquals("existing data",SecureFiles.text(file)) }
        finally { file.delete() }
    }
    @Test fun closingRejectsNewAcquisitionsBeforeResourceCleanupAndClosesOnlyOnce() {
        val root=Files.createTempDirectory("closing-admission").toFile()
        val session=SecureFiles.Session(UUID.randomUUID(),root,VaultCrypto.randomKey());SecureFiles.register(session)
        val closes=java.util.concurrent.atomic.AtomicInteger()
        try {
            val file=File(root,"private");SecureFiles.write(file,"synthetic".toByteArray());val input=SecureFiles.input(file)
            session.retain(AutoCloseable {closes.incrementAndGet()})
            session.beginClosing()
            assertTrue(runCatching {session.withKey {it.size}}.isFailure)
            assertTrue(runCatching {session.retain(AutoCloseable {})}.isFailure)
            assertTrue(runCatching {SecureFiles.input(file)}.isFailure)
            assertTrue(runCatching {SecureFiles.output(file)}.isFailure)
            assertTrue(runCatching {input.read()}.isFailure)
            assertEquals(0,closes.get())
            session.close();session.close();assertEquals(1,closes.get())
        } finally {SecureFiles.revoke(session);root.deleteRecursively()}
    }
    @Test(timeout=10000) fun registrationRacingCloseCannotLeaveAnUnrevokedHandle() {
        val root=Files.createTempDirectory("retain-close-race").toFile()
        try {repeat(100) {
            val session=SecureFiles.Session(UUID.randomUUID(),root,VaultCrypto.randomKey())
            val start=java.util.concurrent.CountDownLatch(1);val closed=java.util.concurrent.atomic.AtomicInteger();var retained=false
            val thread=Thread {start.await();retained=runCatching {session.retain(AutoCloseable {closed.incrementAndGet()})}.isSuccess}
            thread.start();start.countDown();session.close();thread.join(1000);assertFalse(thread.isAlive)
            session.close();assertEquals(if(retained)1 else 0,closed.get())
            assertTrue(runCatching {session.withKey {it.size}}.isFailure)
        }} finally {root.deleteRecursively()}
    }

}
