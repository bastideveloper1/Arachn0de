package com.r0ybt.arachn0de.report

import com.r0ybt.arachn0de.security.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.nio.file.Files
import java.util.UUID

class CardShareDeliveryTest {
    @Test fun authenticatedSnapshotsReleaseSourceAndBoundConcurrentReaders() {
        val root=Files.createTempDirectory("card-delivery").toFile();val owner=SecureFiles.Session(UUID.randomUUID(),root,VaultCrypto.randomKey());SecureFiles.register(owner)
        val prepared=mutableListOf<CardShareDelivery>()
        try {
            val file=File(root,"card");SecureFiles.write(file,ByteArray(32768) {it.toByte()});val cipher=file.readBytes()
            repeat(4) {prepared+=CardShareDelivery.prepare(file,owner,authorized=owner::checkOpen)}
            assertTrue(runCatching {CardShareDelivery.prepare(file,owner,authorized=owner::checkOpen)}.isFailure)
            assertArrayEquals(cipher,file.readBytes())
            prepared.removeAt(0).close();prepared+=CardShareDelivery.prepare(file,owner,authorized=owner::checkOpen)
            owner.close();prepared.forEach {it.close()}
            assertTrue(runCatching {CardShareDelivery.prepare(file,owner,authorized=owner::checkOpen)}.isFailure)
        } finally {prepared.forEach {it.close()};SecureFiles.revoke(owner);root.deleteRecursively()}
    }
    @Test fun failedAuthenticationBetweenPassesDoesNotLeakBudgetOrChangeOriginal() {
        val root=Files.createTempDirectory("card-auth").toFile();val owner=SecureFiles.Session(UUID.randomUUID(),root,VaultCrypto.randomKey());SecureFiles.register(owner)
        val prepared=mutableListOf<CardShareDelivery>()
        try {
            val file=File(root,"card");SecureFiles.write(file,ByteArray(16384) {it.toByte()});val original=file.readBytes();var checks=0
            assertTrue(runCatching {CardShareDelivery.prepare(file,owner) {owner.checkOpen();if(checks++==1)file.writeBytes(original.copyOf().apply {this[lastIndex]=(last().toInt() xor 1).toByte()})}}.isFailure)
            file.writeBytes(original)
            repeat(4) {prepared+=CardShareDelivery.prepare(file,owner,authorized=owner::checkOpen)}
            assertArrayEquals(original,file.readBytes())
        } finally {prepared.forEach {it.close()};SecureFiles.revoke(owner);root.deleteRecursively()}
    }
}
