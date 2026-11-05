package com.r0ybt.arachn0de.security

import android.content.*
import android.content.pm.ApplicationInfo
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.photos.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PhaseCSecurityTest {
    private val app get()=ApplicationProvider.getApplicationContext<Context>()
    private fun isolated():Pair<Context,File> {
        val root=File(app.filesDir,"phase-c-${UUID.randomUUID()}").apply {mkdirs()}
        return object:ContextWrapper(app) {
            override fun getApplicationContext():Context=this
            override fun getApplicationInfo()=ApplicationInfo(app.applicationInfo).apply {dataDir=root.path}
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
            override fun getDatabasePath(name:String)=File(root,"databases/$name").apply {parentFile!!.mkdirs()}
            override fun openOrCreateDatabase(name:String,mode:Int,factory:android.database.sqlite.SQLiteDatabase.CursorFactory?)=android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path,factory)
            override fun openOrCreateDatabase(name:String,mode:Int,factory:android.database.sqlite.SQLiteDatabase.CursorFactory?,handler:android.database.DatabaseErrorHandler?)=android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path,factory,handler)
        } to root
    }
    @Test(timeout=90000) fun externalUidCaptureImportsEncryptedAndTransfersToEditablePrivatePhoto()=runBlocking<Unit> {
        val (context,root)=isolated();val manager=VaultManager(context);var capture:String?=null
        val files=CameraCaptureFiles(app)
        try {
            manager.setup("phase-c-synthetic-only-2026".toCharArray());val s=manager.current.value!!
            val id=files.prepare(s.access.id);capture=id;val uri=files.uri(id)
            val latch=CountDownLatch(1);val token=UUID.randomUUID().toString();var writer=-1;var success=false
            val receiver=object:BroadcastReceiver() {override fun onReceive(c:Context,i:Intent) {if(i.getStringExtra("token")==token) {writer=i.getIntExtra("uid",-1);success=i.getBooleanExtra("ok",false);latch.countDown()}}}
            ContextCompat.registerReceiver(app,receiver,IntentFilter("com.r0ybt.arachn0de.PHASE_C_CAMERA_DONE"),ContextCompat.RECEIVER_EXPORTED)
            try {
                val test=InstrumentationRegistry.getInstrumentation().context
                val intent=Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).setClassName(test.packageName,SyntheticCameraActivity::class.java.name)
                    .putExtra(android.provider.MediaStore.EXTRA_OUTPUT,uri).putExtra("returnPackage",app.packageName).putExtra("token",token)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    .apply {clipData=ClipData.newRawUri("synthetic capture",uri)}
                app.startActivity(intent);assertTrue(latch.await(10,TimeUnit.SECONDS));assertTrue(success);assertTrue(writer!=android.os.Process.myUid())
            } finally {app.unregisterReceiver(receiver)}
            val raw=files.file(id).readBytes();val expected=PrivatePhoto.clean(raw).bytes
            val importer=CameraPhotoImport(files,s.attachments,s.access.id);val photo=importer.import(id)
            assertFalse(files.file(id).exists());assertArrayEquals(expected,SecureFiles.read(photo.file));assertFalse(photo.file.readBytes().contentEquals(expected))
            assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,app.checkUriPermission(uri,-1,writer,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            val avatar=s.people.importAvatar(android.net.Uri.EMPTY,photo.file)
            s.people.save("phase-c-person","Synthetic",avatar)
            s.attachments.retainGallery("phase-c-editor",setOf(photo.attachment.id))
            val memories=PersonMemoryRepository(s.database,s.context,s.attachments,s.people)
            val memory=memories.create("phase-c-person",listOf(photo.attachment.id),"phase-c-editor")
            importer.discard(id);assertTrue(photo.file.exists())
            val bytes=ByteArrayOutputStream();s.backups.writeSnapshot(bytes)
            val inspected=BackupContainer.readBackup(bytes.toByteArray().inputStream(),File(s.context.cacheDir,"phase-c-inspect").apply {mkdirs()})
            assertTrue(inspected.attachmentFiles.any {it.id==photo.attachment.id})
            s.backups.restore(inspected);s.backups.discard(inspected)
            memories.deleteEntry(memory)
        } finally {capture?.let {runCatching {val s=manager.current.value;if(s!=null)CameraPhotoImport(files,s.attachments,s.access.id).discard(it)}};manager.lock();root.deleteRecursively()}
    }
    @Test(timeout=90000) fun corruptCurrentDatabaseKeepsRecoveryManualUnknownAndMultipleArchives()=runBlocking<Unit> {
        val (context,root)=isolated();val manager=VaultManager(context)
        try {
            val original=Arachn0deDatabase.create(context);val project=ProjectRepository(original.projectDao(),original).createProject("Synthetic recovery canary");original.close()
            val names=listOf("manual.arachnode","automatic-fixture-one.arachnode","automatic-fixture-two.arachnode","unknown-provenance.bin")
            names.forEach {File(context.filesDir,it).writeText("synthetic-$it")}
            val rawDirectory=File(context.cacheDir,"antisocial-camera").apply {mkdirs()};File(rawDirectory,"unknown-pending.jpg").writeText("synthetic unconfirmed capture")
            manager.setup("phase-c-recovery-only-2026".toCharArray());val s=manager.current.value!!
            val archive=File(s.access.root,"migration-original/files")
            val before=names.associateWith {File(archive,it).readBytes()};val recovery=File(s.access.root,"recovery.arachnode.enc");val recoveryBytes=recovery.readBytes()
            assertFalse(File(s.context.cacheDir,"antisocial-camera").exists());assertFalse(File(s.access.root,"migration-original/cache/antisocial-camera").exists());assertTrue(File(rawDirectory,"unknown-pending.jpg").exists())
            names.forEach {assertEquals("synthetic-$it",SecureFiles.text(File(s.context.filesDir,it)))}
            val database=s.context.getDatabasePath("arachn0de.db");manager.lock()
            database.writeBytes(ByteArray(4096) {0x5a.toByte()});assertTrue(runCatching {manager.unlock("phase-c-recovery-only-2026".toCharArray())}.isFailure)
            val exported=ByteArrayOutputStream();manager.exportInitialRecovery("phase-c-recovery-only-2026".toCharArray(),"phase-c-portable-2026".toCharArray(),exported)
            val plain=ByteArrayOutputStream();EncryptedBackup.read(exported.toByteArray().inputStream(),plain,"phase-c-portable-2026".toCharArray())
            val restored=BackupContainer.readBackup(plain.toByteArray().inputStream());assertTrue(restored.projects.any {it.id==project.id})
            assertArrayEquals(recoveryBytes,recovery.readBytes());names.forEach {assertArrayEquals(before.getValue(it),File(archive,it).readBytes())}
        } finally {manager.lock();root.deleteRecursively()}
    }
}
