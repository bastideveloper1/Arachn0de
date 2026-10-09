package com.r0ybt.arachn0de.security

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Device-only: no plaintext Room replacement or simulated SQLCipher engine. */
@RunWith(AndroidJUnit4::class)
class NativeVaultTest {
    private fun isolated():Pair<Context,File> {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val root=File(app.filesDir,"instrumented-security-${UUID.randomUUID()}").apply {mkdirs()}
        return object:ContextWrapper(app) {
            override fun getApplicationContext():Context=this
            override fun getApplicationInfo()=ApplicationInfo(app.applicationInfo).apply {dataDir=root.path}
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
            override fun getDatabasePath(name:String)=File(root,"databases/$name").apply {parentFile!!.mkdirs()}
            override fun openOrCreateDatabase(name:String,mode:Int,factory:android.database.sqlite.SQLiteDatabase.CursorFactory?)=
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path,factory)
            override fun openOrCreateDatabase(name:String,mode:Int,factory:android.database.sqlite.SQLiteDatabase.CursorFactory?,errorHandler:android.database.DatabaseErrorHandler?)=
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path,factory,errorHandler)
            override fun deleteDatabase(name:String)=android.database.sqlite.SQLiteDatabase.deleteDatabase(getDatabasePath(name))
        } to root
    }
    @Test fun nativePagesExportMainMigrationAndIndependentSecondary()=runBlocking {
        val (context,root)=isolated();val manager=VaultManager(context)
        try {
            val original=Arachn0deDatabase.create(context)
            val project=ProjectRepository(original.projectDao(),original).createProject("NATIVE_PRIVATE_SENTINEL")
            val originalFile=File(context.filesDir,"test-private-image.bin").apply {writeBytes(byteArrayOf(1,2,3,4,5))}
            original.close()
            manager.setup("principal-native-123".toCharArray())
            val main=manager.current.value!!
            assertTrue(main.primary)
            assertEquals(listOf(project.id),main.database.backupDao().projects().map {it.id})
            assertFalse(originalFile.exists())
            assertArrayEquals(byteArrayOf(1,2,3,4,5),SecureFiles.read(File(main.context.filesDir,originalFile.name)))
            val cipher=main.context.getDatabasePath("arachn0de.db")
            assertFalse(String(cipher.readBytes(),Charsets.ISO_8859_1).contains("NATIVE_PRIVATE_SENTINEL"))
            assertFalse(cipher.inputStream().use {String(ByteArray(16).also {header->java.io.DataInputStream(it).readFully(header)},Charsets.US_ASCII)}.startsWith("SQLite format 3"))
            manager.createSecondary("principal-native-123".toCharArray(),"secondary-native-123".toCharArray())
            manager.lock()
            assertTrue(runCatching {android.database.sqlite.SQLiteDatabase.openDatabase(cipher.path,null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use {it.rawQuery("SELECT * FROM projects",null).use { c->c.moveToFirst() }}}.isFailure)
            manager.unlock("secondary-native-123".toCharArray())
            assertTrue(manager.current.value!!.database.backupDao().projects().isEmpty())
            manager.current.value!!.projects.createProject("Only secondary")
            manager.lock();manager.unlock("principal-native-123".toCharArray())
            assertEquals(listOf(project.id),manager.current.value!!.database.backupDao().projects().map {it.id})
            manager.lock()
            val recovery=java.io.ByteArrayOutputStream()
            manager.exportInitialRecovery("principal-native-123".toCharArray(),"portable-native-123".toCharArray(),recovery)
            manager.unlock("principal-native-123".toCharArray())
            val data=manager.current.value!!.backups.inspect(java.io.ByteArrayInputStream(recovery.toByteArray()),"portable-native-123".toCharArray())
            assertEquals(listOf(project.id),data.projects.map {it.id})
            manager.current.value!!.backups.restore(data)
            manager.current.value!!.backups.discard(data)
            assertEquals(listOf(project.id),manager.current.value!!.database.backupDao().projects().map {it.id})
        } finally {manager.lock();root.deleteRecursively()}
    }
    @Test fun encryptedVersion26UpgradePreservesBothStoreIdentitiesAndNewPhotosBackups()=runBlocking {
        val (context,root)=isolated();val manager=VaultManager(context)
        fun version26(db:Arachn0deDatabase) {
            val sql=db.openHelper.writableDatabase
            sql.execSQL("ALTER TABLE node_sort_preferences DROP COLUMN layersFirst")
            sql.execSQL("UPDATE room_master_table SET identity_hash='38afa7183ac982125a17210e879c2dc5' WHERE id=42")
            sql.version=26
        }
        try {
            manager.setup("main-upgrade-fixture".toCharArray())
            val primary=manager.current.value!!;val primaryId=primary.access.id
            val mainProject=primary.projects.createProject("MAIN_UPGRADE_SENTINEL")
            primary.database.nodeSortPreferenceDao().save(com.r0ybt.arachn0de.data.local.NodeSortPreferenceEntity("${mainProject.id}:project-root","DUE_PRIORITY"))
            manager.createSecondary("main-upgrade-fixture".toCharArray(),"decoy-upgrade-fixture".toCharArray())
            version26(primary.database);manager.lock()
            manager.unlock("decoy-upgrade-fixture".toCharArray())
            val secondary=manager.current.value!!;val secondaryId=secondary.access.id
            val decoyProject=secondary.projects.createProject("DECOY_UPGRADE_SENTINEL")
            secondary.database.nodeSortPreferenceDao().save(com.r0ybt.arachn0de.data.local.NodeSortPreferenceEntity("${decoyProject.id}:project-root","PRIORITY"))
            version26(secondary.database);manager.lock()
            manager.unlock("main-upgrade-fixture".toCharArray())
            val reopened=manager.current.value!!
            assertEquals(primaryId,reopened.access.id);assertEquals(27,reopened.database.openHelper.readableDatabase.version)
            assertEquals(listOf(mainProject.id),reopened.database.backupDao().projects().map {it.id})
            val preference=reopened.database.nodeSortPreferenceDao().all().single();assertFalse(preference.layersFirst);assertEquals("DUE_PRIORITY",preference.mode)
            reopened.database.nodeSortPreferenceDao().save(preference.copy(layersFirst=true))
            val bitmap=android.graphics.Bitmap.createBitmap(1800,900,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.GREEN)}
            val source=File(context.cacheDir,"upgrade-source.png");source.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
            val photo=reopened.photos.import(android.net.Uri.fromFile(source),"upgrade-editor")
            val framing=com.r0ybt.arachn0de.domain.model.AvatarFraming(3f,.4f,-.2f)
            reopened.photos.save(mainProject.id,photo,framing,"upgrade-editor")
            val bytes=SecureFiles.read(File(reopened.context.filesDir,"project-photos/$photo"))
            val thumbnail=com.r0ybt.arachn0de.data.local.AvatarStore(reopened.context,"project-photos").readThumbnail(photo,240)!!
            assertEquals(240,thumbnail.width)
            val cached=File(reopened.context.cacheDir,"project-thumbnails").listFiles()!!.first {it.name.endsWith(".png")}
            assertFalse(cached.readBytes().take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10)))
            val exported=reopened.backups.create("portable-upgrade-fixture".toCharArray())
            val inspection=reopened.backups.inspect(SecureFiles.input(exported),"portable-upgrade-fixture".toCharArray())
            reopened.backups.restore(inspection);reopened.backups.discard(inspection)
            val restored=reopened.projects.getProject(mainProject.id)!!.photo!!
            assertEquals(framing,restored.framing);assertArrayEquals(bytes,SecureFiles.read(File(reopened.context.filesDir,"project-photos/${restored.file}")))
            assertTrue(reopened.database.nodeSortPreferenceDao().all().single().layersFirst)
            manager.lock();manager.unlock("decoy-upgrade-fixture".toCharArray())
            val decoy=manager.current.value!!;assertEquals(secondaryId,decoy.access.id);assertEquals(27,decoy.database.openHelper.readableDatabase.version)
            assertEquals(listOf(decoyProject.id),decoy.database.backupDao().projects().map {it.id});assertFalse(decoy.database.nodeSortPreferenceDao().all().single().layersFirst)
            assertNull(decoy.projects.getProject(decoyProject.id)!!.photo)
        } finally {manager.lock();root.deleteRecursively()}
    }
    @Test fun nativeSqlcipherPreservesTypedRowsAndRejectsWrongKey() {
        val (context,root)=isolated()
        try {
            val source=context.getDatabasePath("source.db")
            android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(source,null).use { db ->
                db.execSQL("CREATE TABLE typed(id INTEGER PRIMARY KEY, t TEXT, n REAL, b BLOB, z TEXT)")
                db.execSQL("INSERT INTO typed VALUES(?,?,?,?,?)",arrayOf(7,"ñ UTF-8",2.75,byteArrayOf(0,1,127),null))
                db.execSQL("PRAGMA user_version=25")
            }
            val target=context.getDatabasePath("protected.db")
            val entropy=VaultCrypto.randomKey()
            val key=try {entropy.joinToString("") {"%02x".format(it)}.toByteArray(Charsets.US_ASCII)} finally {entropy.fill(0)}
            try {
                val before=source.readBytes();SqlCipherMigration.copy(source,target,key)
                assertArrayEquals(before,source.readBytes())
                assertTrue(runCatching {net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(target.path,VaultCrypto.randomKey(),null,net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,null).use {db->db.rawQuery("SELECT * FROM typed",emptyArray<String>()).use {it.moveToFirst()}}}.isFailure)
                net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(target.path,key,null,net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,null).use {db ->
                    db.rawQuery("SELECT * FROM typed",emptyArray<String>()).use {c->assertTrue(c.moveToFirst());assertEquals("ñ UTF-8",c.getString(1));assertEquals(2.75,c.getDouble(2),0.0);assertArrayEquals(byteArrayOf(0,1,127),c.getBlob(3));assertTrue(c.isNull(4))}
                }
            } finally {key.fill(0)}
        } finally {root.deleteRecursively()}
    }
    @Test fun interruptedNativeMigrationRetainsSourceAndRetriesWithSamePassword()=runBlocking {
        val (context,root)=isolated()
        var interrupt=true
        val manager=VaultManager(context,sync={directory ->
            if(interrupt && directory.name=="migration-original") {
                interrupt=false;throw java.io.IOException("Injected pre-activation interruption")
            }
            com.r0ybt.arachn0de.backup.syncBackupDirectory(directory)
        })
        try {
            val original=Arachn0deDatabase.create(context)
            val project=ProjectRepository(original.projectDao(),original).createProject("Preserve interrupted migration")
            original.close()
            val source=File(context.filesDir,"original.bin").apply {writeBytes(byteArrayOf(7,8,9))}
            assertTrue(runCatching {manager.setup("retry-native-123".toCharArray())}.isFailure)
            assertNull(manager.current.value);assertFalse(manager.configured)
            assertArrayEquals(byteArrayOf(7,8,9),source.readBytes())
            val reopened=Arachn0deDatabase.create(context)
            try {assertEquals(listOf(project.id),reopened.backupDao().projects().map {it.id})} finally {reopened.close()}
            manager.setup("retry-native-123".toCharArray())
            assertEquals(listOf(project.id),manager.current.value!!.database.backupDao().projects().map {it.id})
            assertArrayEquals(byteArrayOf(7,8,9),SecureFiles.read(File(manager.current.value!!.context.filesDir,source.name)))
        } finally {manager.lock();root.deleteRecursively()}
    }

}
