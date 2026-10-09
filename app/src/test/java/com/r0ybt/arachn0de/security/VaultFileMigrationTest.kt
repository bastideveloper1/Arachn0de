package com.r0ybt.arachn0de.security

import java.io.*
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class VaultFileMigrationTest {
    @Test fun lowSpaceReadErrorsInterruptionAndRetryNeverChangeSource() {
        val root=Files.createTempDirectory("vault-migration").toFile();val source=File(root,"source").apply { mkdir() }
        val first=File(source,"avatars/a.png").apply { parentFile!!.mkdir();writeBytes(ByteArray(90000){33}) }
        val second=File(source,"avatars/b.png").apply { writeBytes(ByteArray(500){42}) }
        val inventory=VaultFileMigration.inventory(source)
        val original=inventory.associate { it.path to VaultFileMigration.checked(source,it.path).readBytes() }
        fun target(label:String,work:(File)->Unit) {
            val directory=File(root,label).apply { mkdir() };val session=SecureFiles.Session(UUID.randomUUID(),directory,VaultCrypto.randomKey())
            SecureFiles.register(session)
            try { work(directory) } finally { SecureFiles.revoke(session) }
        }
        try {
            target("low-space") { dest ->
                try { VaultFileMigration.copy(source,dest,inventory,availableBytes={1},syncDirectory={});fail("Must refuse low space") } catch(_:IllegalArgumentException) {}
                assertEquals(0,dest.list()!!.size)
            }
            target("missing-source") { dest ->
                assertTrue(second.delete())
                try { VaultFileMigration.copy(source,dest,inventory,syncDirectory={});fail("Must reject missing source") } catch(_:IOException) {}
                second.writeBytes(original.getValue("avatars/b.png"))
            }
            target("interrupted") { dest ->
                var calls=0
                try { VaultFileMigration.copy(source,dest,inventory,checkCancelled={ if(++calls==3) throw InterruptedIOException("injected cancellation") },syncDirectory={});fail("Must stop") } catch(_:InterruptedIOException) {}
            }
            original.forEach { (path,bytes)->assertArrayEquals(bytes,VaultFileMigration.checked(source,path).readBytes()) }
            target("retry") { dest ->
                var synced=0
                assertTrue(runCatching {VaultFileMigration.copy(source,dest,inventory,syncDirectory={throw IOException("directory fsync")})}.isFailure)
                assertTrue(first.exists())
                VaultFileMigration.copy(source,dest,inventory,resumeUnactivated=true,syncDirectory={synced++})
                assertTrue(synced>=2)
                VaultFileMigration.verify(source,dest,inventory)
                assertArrayEquals(first.readBytes(),SecureFiles.read(File(dest,"avatars/a.png")))
                VaultFileMigration.removeVerifiedSources(source,dest,inventory)
                assertFalse(first.exists());assertFalse(second.exists())
                VaultFileMigration.removeVerifiedSources(source,dest,inventory)
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun cleanupRefusesChangedSourceAndTamperedDestination() {
        val root=Files.createTempDirectory("vault-cleanup").toFile();val source=File(root,"source").apply { mkdir() }
        val original=File(source,"photo").apply { writeText("original") };val entries=VaultFileMigration.inventory(source)
        val target=File(root,"target").apply { mkdir() };val session=SecureFiles.Session(UUID.randomUUID(),target,VaultCrypto.randomKey());SecureFiles.register(session)
        try {
            VaultFileMigration.copy(source,target,entries,syncDirectory={});original.writeText("changed")
            try { VaultFileMigration.removeVerifiedSources(source,target,entries);fail("Must preserve changed originals") } catch(_:IllegalStateException) {}
            assertEquals("changed",original.readText());original.writeText("original")
            val copy=File(target,"photo");val bytes=copy.readBytes();bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();copy.writeBytes(bytes)
            try { VaultFileMigration.removeVerifiedSources(source,target,entries);fail("Must preserve originals if target corrupt") } catch(_:VaultAuthenticationException) {}
            assertEquals("original",original.readText())
        } finally { SecureFiles.revoke(session);root.deleteRecursively() }
    }
}
