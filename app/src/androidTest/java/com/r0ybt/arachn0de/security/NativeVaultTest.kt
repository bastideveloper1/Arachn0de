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
            val target=context.getDatabasePath("protected.db");val key=VaultCrypto.randomKey()
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
}
