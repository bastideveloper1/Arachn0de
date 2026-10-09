package com.r0ybt.arachn0de.security

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=android.app.Application::class)
class VaultManagerTest {
    @Test fun metroConsultationUsesOwnStoreAndLockDiscardsQueuedRequest()=runBlocking<Unit> {
        val original=ApplicationProvider.getApplicationContext<Context>()
        val root=File(original.filesDir,"metro-isolation-${UUID.randomUUID()}").apply {mkdirs()}
        val base=object:ContextWrapper(original) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
        }
        seed(base,"main-metro-fixture");val vault=manager(base)
        try {
            vault.unlock("main-metro-fixture".toCharArray());val main=vault.current.value!!
            val p=main.projects.createProject("Principal Metro")
            val task=main.nodes.createNode(p.id,null,"Principal",creationId="same-metro-node")
            val net=main.metro.snapshot().preferences.network
            val referenceWall=java.time.LocalDateTime.parse("2026-10-09T12:00").atZone(java.time.ZoneId.of("America/Santiago")).toInstant().toEpochMilli()
            val plan=com.r0ybt.arachn0de.metro.MetroPlanner.planAt(net,listOf("las-parcelas","los-dominicos"),referenceWall,com.r0ybt.arachn0de.metro.MetroRestrictions())!!
            val id=main.metro.savePlan(plan,task.id)
            val time=com.r0ybt.arachn0de.metro.MetroTime(referenceWall,100_000,1)
            main.metro.begin(id,main.metro.snapshot().journeys.single().row.revision,time)
            main.metro.tracking(id,main.metro.snapshot().journeys.single().row.revision) {com.r0ybt.arachn0de.metro.MetroStages.arrive(it,time)}
            main.metro.tracking(id,main.metro.snapshot().journeys.single().row.revision) {com.r0ybt.arachn0de.metro.MetroStages.beginTransfer(it,time)}
            val current=main.metro.snapshot().journeys.single()
            main.metro.changeDestination(id,current.row.revision,current.data.active!!.confirmed!!,"republica",time)
            val backups=com.r0ybt.arachn0de.backup.BackupRepository(main.database,main.context,
                com.r0ybt.arachn0de.backup.BackupAvatarFiles(main.context,BackupFixture::syncDirectory))
            val protected=backups.create("metro-backup-fixture".toCharArray())
            val inspection=backups.inspect(SecureFiles.input(protected),"metro-backup-fixture".toCharArray())
            backups.restore(inspection);backups.discard(inspection);backups.discardPending(protected.name)
            assertEquals(com.r0ybt.arachn0de.metro.MetroPhase.READY,main.metro.snapshot().journeys.single().data.active!!.control!!.phase)
            assertNotNull(main.metro.snapshot().journeys.single().data.active!!.control!!.undo)
            assertEquals(1,main.metro.snapshot().journeys.single().data.active!!.routeArchives.size)
            val before=main.metro.snapshot()
            assertEquals(before,main.metro.snapshot())
            vault.createSecondary("main-metro-fixture".toCharArray(),"secondary-metro-fixture".toCharArray())
            com.r0ybt.arachn0de.metro.MetroNavigation.open(task.id)
            vault.lock();assertNull(com.r0ybt.arachn0de.metro.MetroNavigation.requests.value)
            vault.unlock("secondary-metro-fixture".toCharArray());val secondary=vault.current.value!!
            assertTrue(secondary.metro.snapshot().journeys.isEmpty())
            assertTrue(runCatching {main.metro.snapshot()}.isFailure)
            val sp=secondary.projects.createProject("Señuelo Metro")
            val st=secondary.nodes.createNode(sp.id,null,"Señuelo",creationId="same-metro-node")
            secondary.metro.savePlan(plan,st.id)
            assertNotEquals(id,secondary.metro.snapshot().journeys.single().row.id)
            vault.lock();vault.unlock("main-metro-fixture".toCharArray())
            assertEquals(before,vault.current.value!!.metro.snapshot())
        } finally {vault.lock();root.deleteRecursively()}
    }
    /** Native SQLCipher is replaced ONLY in this test. Auth, envelopes, files and controller are real. */
    private fun manager(base:Context)=VaultManager(base,BackupFixture::syncDirectory) { ctx,_ ->
        Room.databaseBuilder(base,Arachn0deDatabase::class.java,ctx.getDatabasePath("arachn0de.db").absolutePath).build()
    }
    private suspend fun seed(base:Context,password:String):UUID {
        val id=UUID.randomUUID();val root=File(base.filesDir,"security-v1/stores/$id").apply {mkdirs()}
        val key=VaultCrypto.randomKey();val access=SecureFiles.Session(id,root,key);SecureFiles.register(access)
        try {
            File(root,"key.envelope").writeBytes(VaultCrypto.wrap(id,password.toCharArray(),key))
            SecureFiles.write(File(root,"identity.enc"),"primary".toByteArray())
            SecureFiles.output(File(root,"recovery.arachnode.enc")).use {com.r0ybt.arachn0de.backup.BackupContainer.writeBackup(BackupFixture.empty(),it)}
            File(base.filesDir,"security-v1/index").writeText(JSONArray(listOf(id.toString())).toString())
        } finally {SecureFiles.revoke(access);key.fill(0)}
        return id
    }
    @Test fun passwordChangeIndependentStoresRestartAndRevokedReferences()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>();val root=File(app.filesDir,"manager-${UUID.randomUUID()}").apply {mkdir()}
        val base=object:ContextWrapper(app) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
        }
        val primary=seed(base,"principal-123");val manager=manager(base)
        try {
            assertNull(manager.current.value);assertTrue(manager.configured)
            val wrong="incorrecta".toCharArray();assertTrue(runCatching {manager.unlock(wrong)}.isFailure);assertTrue(wrong.all {it=='\u0000'})
            manager.unlock("principal-123".toCharArray());val main=manager.current.value!!
            assertTrue(main.primary);assertEquals(primary,main.access.id)
            val project=main.projects.createProject("Privado principal")
            main.database.technologyDao().insert(com.r0ybt.arachn0de.data.local.TechnologyEntity("main-tech","Tecnología principal",null))
            kotlinx.coroutines.withTimeout(10000) {main.technologies.state.first {it.loaded && it.catalog.any {row->row.id=="main-tech"}}}
            val photo=File(main.context.filesDir,"photo.bin");SecureFiles.write(photo,byteArrayOf(7,8,9))
            val mainRepo=com.r0ybt.arachn0de.backup.BackupRepository(main.database,main.context,
                com.r0ybt.arachn0de.backup.BackupAvatarFiles(main.context,BackupFixture::syncDirectory))
            val backupPassword="portable-backup-123".toCharArray()
            val mainBackup=mainRepo.create(backupPassword)
            val mainBytes=SecureFiles.read(mainBackup);mainRepo.discardPending(mainBackup.name)
            assertEquals("primary",mainRepo.snapshot().storeKind)
            manager.savedUi=mapOf("secret" to listOf("editor privado"))
            val cipherBefore=photo.readBytes()
            manager.changePassword("principal-123".toCharArray(),"principal-nueva".toCharArray())
            assertArrayEquals(cipherBefore,photo.readBytes())
            assertTrue(runCatching {manager.createSecondary("principal-nueva".toCharArray(),"principal-nueva".toCharArray())}.isFailure)
            manager.createSecondary("principal-nueva".toCharArray(),"secundaria-123".toCharArray())
            manager.lock();assertNull(manager.current.value);assertNull(manager.savedUi);assertFalse(main.database.isOpen)
            assertTrue(main.technologies.state.value.catalog.isEmpty())
            assertTrue(runCatching {SecureFiles.read(photo)}.isFailure)
            manager.unlock("secundaria-123".toCharArray());val secondary=manager.current.value!!
            assertFalse(secondary.primary);assertNotEquals(primary,secondary.access.id)
            assertTrue(secondary.database.backupDao().projects().isEmpty())
            assertTrue(secondary.database.technologyDao().catalog().isEmpty())
            assertTrue(runCatching {manager.createSecondary("secundaria-123".toCharArray(),"third-password".toCharArray())}.isFailure)
            val secondaryRepo=com.r0ybt.arachn0de.backup.BackupRepository(secondary.database,secondary.context,
                com.r0ybt.arachn0de.backup.BackupAvatarFiles(secondary.context,BackupFixture::syncDirectory))
            assertTrue(runCatching {secondaryRepo.inspect(java.io.ByteArrayInputStream(mainBytes),backupPassword)}.isFailure)
            assertTrue(runCatching {secondaryRepo.restore(BackupFixture.empty())}.isFailure)
            secondary.projects.createProject("Contenido independiente")
            assertEquals("secondary",secondaryRepo.snapshot().storeKind)
            val secondaryBackup=secondaryRepo.create(backupPassword)
            val own=secondaryRepo.inspect(SecureFiles.input(secondaryBackup),backupPassword)
            secondaryRepo.restore(own);secondaryRepo.discard(own);secondaryRepo.discardPending(secondaryBackup.name)
            assertEquals("Contenido independiente",secondary.database.backupDao().projects().single().name)
            backupPassword.fill('\u0000')
            assertTrue(runCatching {manager.changePassword("secundaria-123".toCharArray(),"principal-nueva".toCharArray())}.isFailure)
            manager.lock()
            val recovered=java.io.ByteArrayOutputStream()
            manager.exportInitialRecovery("principal-nueva".toCharArray(),"recovery-password".toCharArray(),recovered)
            val plain=java.io.ByteArrayOutputStream()
            EncryptedBackup.read(java.io.ByteArrayInputStream(recovered.toByteArray()),plain,"recovery-password".toCharArray())
            val initial=com.r0ybt.arachn0de.backup.BackupContainer.readBackup(java.io.ByteArrayInputStream(plain.toByteArray()))
            assertTrue(initial.projects.isEmpty()) // Explicit older snapshot, never silently replaces live rows.
            assertTrue(runCatching {manager.exportInitialRecovery("secundaria-123".toCharArray(),"recovery-password".toCharArray(),java.io.ByteArrayOutputStream())}.isFailure)
            val restarted=manager(base);assertNull(restarted.current.value)
            assertTrue(runCatching {restarted.unlock("principal-123".toCharArray())}.isFailure)
            restarted.unlock("principal-nueva".toCharArray())
            assertEquals(listOf(project.id),restarted.current.value!!.database.backupDao().projects().map {it.id})
            assertArrayEquals(byteArrayOf(7,8,9),SecureFiles.read(photo))
            restarted.lock()
        } finally {manager.lock();root.deleteRecursively();SecureFiles.activeProfile=null}
    }
    @Test fun manualLockWorksEvenWhenCallerIsCancelled()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>();val root=File(app.filesDir,"lock-${UUID.randomUUID()}").apply {mkdir()}
        val base=object:ContextWrapper(app) {override fun getApplicationContext():Context=this;override fun getFilesDir()=root}
        seed(base,"principal-123");val manager=manager(base)
        try {
            manager.unlock("principal-123".toCharArray());val session=manager.current.value!!
            val entered=kotlinx.coroutines.CompletableDeferred<Unit>()
            val release=kotlinx.coroutines.CompletableDeferred<Unit>()
            val holder=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.r0ybt.arachn0de.data.repository.AttachmentRepository.fileOperations.withLock {entered.complete(Unit);release.await()}
            }
            entered.await()
            val locking=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {manager.lock()}
            manager.current.first {it==null}
            locking.cancel();release.complete(Unit);locking.join();holder.join()
            assertNull(manager.current.value);assertFalse(session.database.isOpen)
            assertTrue(runCatching {session.access.checkOpen()}.isFailure)
        } finally {manager.lock();root.deleteRecursively();SecureFiles.activeProfile=null}
    }
    @Test fun backgroundGraceAndExpiredForegroundCloseTheStoreWithoutClearingData()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val root=File(app.filesDir,"background-${UUID.randomUUID()}").apply {mkdir()}
        val base=object:ContextWrapper(app) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
        }
        seed(base,"background-password");val manager=manager(base)
        try {
            manager.unlock("background-password".toCharArray())
            val session=manager.current.value!!;val project=session.projects.createProject("Keep after background")
            manager.background();assertTrue(manager.backgrounded.value)
            manager.foreground();assertSame(session,manager.current.value);assertFalse(manager.backgrounded.value)
            manager.background()
            org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(31))
            manager.foreground();assertNull(manager.current.value);assertFalse(session.database.isOpen)
            manager.unlock("background-password".toCharArray())
            assertEquals(project.id,manager.current.value!!.database.backupDao().projects().single().id)
            assertTrue(manager.current.value!!.context.getSharedPreferences("security_preferences",0).edit().putLong("background_timeout",0).commit())
            manager.background();manager.foreground();assertNull(manager.current.value)
        } finally {manager.lock();root.deleteRecursively()}
    }

}
