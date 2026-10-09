package com.r0ybt.arachn0de.security
import java.io.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DurableVaultFileTest {
    @Test fun failureAtEveryPrecommitSyncRecoversOriginalAndAllowsRetry() {
        for(failAt in 1..5) {
            val root=Files.createTempDirectory("vault-journal").toFile();val file=File(root,"config").apply { writeText("original ciphertext") }
            var syncs=0
            try {
                try { DurableVaultFile.replace(file,"next ciphertext".toByteArray()) { if(++syncs==failAt) throw IOException("injected fsync") };fail("Must fail before commit") } catch(_:IOException) {}
                DurableVaultFile.recover(file) {}
                assertEquals("original ciphertext",file.readText())
                DurableVaultFile.replace(file,"next ciphertext".toByteArray()) {}
                assertEquals("next ciphertext",file.readText());assertEquals(listOf("config"),root.list()!!.toList())
            } finally { root.deleteRecursively() }
        }
    }
    @Test fun processDeathBeforeActivationRollsBackAndAfterCommitRetainsNewData() {
        val root=Files.createTempDirectory("vault-interruption").toFile();val file=File(root,"config")
        try {
            File(root,"config.pending").writeBytes(byteArrayOf(1));File(root,"config.previous").writeText("old");file.writeText("new")
            DurableVaultFile.recover(file) {};assertEquals("old",file.readText())
            File(root,"config.pending").writeBytes(byteArrayOf(1));File(root,"config.previous").writeText("old");file.writeText("new")
            File(root,"config.commit").writeBytes(java.security.MessageDigest.getInstance("SHA-256").digest("new".toByteArray()))
            DurableVaultFile.recover(file) {};assertEquals("new",file.readText())
        } finally { root.deleteRecursively() }
    }
}
