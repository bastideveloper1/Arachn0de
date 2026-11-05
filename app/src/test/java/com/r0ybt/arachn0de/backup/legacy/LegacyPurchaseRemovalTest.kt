package com.r0ybt.arachn0de.backup.legacy

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[33])
class LegacyPurchaseRemovalTest {
    private val vault=UUID.randomUUID().toString()
    private val entry=PurchaseSuggestion(UUID.randomUUID().toString(),"a".repeat(64),Purchase("SODIMAC",45809,100))
    private fun state()=PurchaseState(true,vault,listOf(entry),mapOf(entry.fingerprint to 100L))

    @Test fun historicalBackupsRoundTripWithoutCreatingTasksOrFinancialOperations()=runBlocking<Unit> {
        val app=ApplicationProvider.getApplicationContext<Application>();app.deleteDatabase("arachn0de.db")
        val db=Arachn0deDatabase.create(app)
        try {
            val original=PrivatePreferenceEntity(PurchaseCodec.NAME,PurchaseCodec.encode(state()))
            db.privatePreferenceDao().save(original)
            val backup=BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory))
            val snapshot=backup.snapshot();val encoded=BackupJson.encode(snapshot)
            val restored=BackupJson.decode(encoded)
            assertEquals(original,restored.privatePreferences.single {it.name==PurchaseCodec.NAME})
            backup.restore(restored)
            assertEquals(original,db.privatePreferenceDao().get(PurchaseCodec.NAME))
            assertTrue(db.nodeDao().getById(entry.id)==null)
            assertTrue(db.bankDao().operations().isEmpty());assertTrue(db.bankDao().allocations().isEmpty())
            assertTrue(BackupJson.decode(BackupJson.encode(BackupFixture.empty())).privatePreferences.isEmpty())
            val corrupt=restored.copy(privatePreferences=listOf(original.copy(payload="corrupt")))
            assertTrue(runCatching {backup.restore(corrupt)}.isFailure)
            assertEquals(original,db.privatePreferenceDao().get(PurchaseCodec.NAME))
        } finally {db.close()}
    }
    @Test fun archivedInboxMergesOnlyMatchingVaultWithoutMutatingSources() {
        val input=state();val rows=emptyList<PrivatePreferenceEntity>()
        val archived=LegacyPurchaseArchive.merge(rows,input,vault,emptySet())
        assertEquals(input.copy(enabled=false),PurchaseCodec.decode(archived.single().payload))
        assertEquals(archived,LegacyPurchaseArchive.merge(archived,input,vault,emptySet()))
        assertEquals(rows,LegacyPurchaseArchive.merge(rows,input,UUID.randomUUID().toString(),emptySet()))
        val confirmed=LegacyPurchaseArchive.merge(rows,input,vault,setOf(entry.id))
        assertTrue(PurchaseCodec.decode(confirmed.single().payload).pending.isEmpty())
        assertEquals(1,input.pending.size);assertTrue(rows.isEmpty())
        val invalid=input.copy(pending=listOf(entry.copy(purchase=entry.purchase.copy(amount=-1))))
        assertTrue(runCatching {PurchaseCodec.decode(PurchaseCodec.encode(invalid))}.isFailure)
    }
    @Test fun oldEncryptedInboxRemainsReadableAndCorruptionDoesNotDeleteFiles() {
        val root=kotlin.io.path.createTempDirectory("legacy-purchase-test").toFile()
        try {
            val key=KeyGenerator.getInstance("AES").apply {init(256)}.generateKey()
            val file=java.io.File(root,"inbox")
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key);updateAAD("arachnode-purchase-input-v1".toByteArray())}
            val bytes=cipher.iv+cipher.doFinal(PurchaseCodec.encode(state()).toByteArray());file.writeBytes(bytes)
            val inbox=LegacyPurchaseInbox(file,{key},{})
            assertEquals(state(),inbox.read());assertArrayEquals(bytes,file.readBytes())
            val bad=bytes.copyOf().apply {this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()};file.writeBytes(bad)
            assertTrue(runCatching {inbox.read()}.isFailure);assertArrayEquals(bad,file.readBytes())
        } finally {root.deleteRecursively()}
    }
    @Test fun mergedManifestHasNoNotificationAccessAndKeepsOtherPermissions() {
        val app=ApplicationProvider.getApplicationContext<Application>();val pm=app.packageManager
        val info=pm.getPackageInfo(app.packageName,PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PERMISSIONS)
        assertTrue(info.services.orEmpty().none {it.permission=="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" || it.name.contains("Purchase")})
        assertTrue(info.receivers.orEmpty().none {it.name.contains("Purchase")})
        val permissions=info.requestedPermissions.orEmpty().toSet()
        listOf("CAMERA","RECORD_AUDIO","POST_NOTIFICATIONS","REQUEST_INSTALL_PACKAGES","INTERNET","FOREGROUND_SERVICE").forEach {assertTrue(it,"android.permission.$it" in permissions)}
        val manifest=java.io.File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("NotificationListenerService"));assertFalse(manifest.contains("BIND_NOTIFICATION_LISTENER_SERVICE"));assertFalse(manifest.contains("cl.android"))
    }
}
