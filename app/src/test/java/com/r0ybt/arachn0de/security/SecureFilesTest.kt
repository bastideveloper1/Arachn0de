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
}
