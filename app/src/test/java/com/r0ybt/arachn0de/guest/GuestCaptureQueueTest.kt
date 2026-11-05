package com.r0ybt.arachn0de.guest

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.domain.model.AvatarFraming
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class GuestCaptureQueueTest {
    @get:Rule val temporary=org.junit.rules.TemporaryFolder()
    @Test fun encryptedAcceptedCaptureSurvivesRestartRejectsTamperingAndRetainsOnFailure() {
        val root=temporary.newFolder("queue")
        val key=SecretKeySpec(ByteArray(32) {it.toByte()},"AES")
        val queue=GuestCaptureQueue(root,{key},BackupFixture::syncDirectory)
        val id=queue.prepare("primary-vault","private-person")
        assertFalse(queue.read(id).accepted)
        val original=ByteArray(4096) {42}
        queue.accept(id,original,AvatarFraming(2f,.1f,-.2f))
        val raw=File(root,"$id.capture").readBytes()
        assertFalse(raw.toString(Charsets.ISO_8859_1).contains("private-person"))
        assertFalse(raw.toList().windowed(original.size).any {it==original.toList()})
        val restored=GuestCaptureQueue(root,{key},BackupFixture::syncDirectory).read(id)
        assertTrue(restored.accepted);assertArrayEquals(original,restored.bytes);assertEquals(2f,restored.framing.zoom)
        assertThrows(IllegalStateException::class.java) {queue.accept(id,byteArrayOf(1),AvatarFraming())}
        val file=File(root,"$id.capture");file.writeBytes(raw.copyOf().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()})
        assertThrows(Exception::class.java) {queue.read(id)}
        assertTrue(file.exists())
        file.writeBytes(raw);queue.remove(id);assertTrue(queue.ids().isEmpty())
    }
    @Test fun failedAcceptanceRollsBackWithoutAcknowledgingAndCanRetry() {
        val root=temporary.newFolder("failure")
        val key=SecretKeySpec(ByteArray(32) {7},"AES")
        val queue=GuestCaptureQueue(root,{key},BackupFixture::syncDirectory)
        val id=queue.prepare("primary","person")
        var calls=0
        val failing=GuestCaptureQueue(root,{key}) {dir->
            calls++
            if(calls==3) throw java.io.IOException("injected before commit")
            BackupFixture.syncDirectory(dir)
        }
        assertThrows(java.io.IOException::class.java) {failing.accept(id,byteArrayOf(1,2,3),AvatarFraming())}
        assertFalse(queue.read(id).accepted)
        queue.accept(id,byteArrayOf(1,2,3),AvatarFraming())
        assertTrue(queue.read(id).accepted)
    }

}
