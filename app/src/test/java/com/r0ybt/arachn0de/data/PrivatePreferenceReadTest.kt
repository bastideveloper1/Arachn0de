package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrivatePreferenceReadTest {
    @Test fun smallReadsAreBoundedDirectAndUpdatesRemainAtomic()=runBlocking<Unit> {
        val queries=java.util.Collections.synchronizedList(mutableListOf<String>())
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),Arachn0deDatabase::class.java)
            .setQueryCallback({sql,_->queries.add(sql)},java.util.concurrent.Executor {it.run()}).build()
        try {
            val dao=db.privatePreferenceDao()
            val boundary="é".repeat(65536)
            dao.save(PrivatePreferenceEntity("small",boundary))
            queries.clear()
            assertEquals(boundary,dao.get("small")!!.payload)
            val reads=synchronized(queries) {queries.filter {it.startsWith("SELECT",ignoreCase=true)}}
            println("Small preference SQL: ${reads.joinToString("\n")}")
            // Room refreshes invalidation after save asynchronously; this SELECT is not a DAO read.
            val invalidationSql="SELECT * FROM room_table_modification_log WHERE invalidated = 1"
            val directSql="SELECT name,payload FROM private_preferences WHERE name=? AND length(CAST(payload AS BLOB))<=131072"
            assertEquals(listOf(directSql),reads.filterNot {it==invalidationSql})
            val larger=boundary+"😀"
            dao.save(PrivatePreferenceEntity("small",larger))
            assertNull(dao.small("small"))
            assertEquals(larger,dao.get("small")!!.payload)
            try {db.withTransaction {
                dao.save(PrivatePreferenceEntity("small","replacement"))
                error("rollback")
            }} catch(_:IllegalStateException) {}
            assertEquals(larger,dao.get("small")!!.payload)
            dao.save(PrivatePreferenceEntity("small",""))
            assertEquals("",dao.get("small")!!.payload)
        } finally {db.close()}
    }



    @Test fun largeHistoricalRowsAndUnicodeBoundariesReadExactlyWithoutCursorWindowOverflow()=runBlocking<Unit> {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),Arachn0deDatabase::class.java).build()
        try {
            val dao=db.privatePreferenceDao()
            val large=("a".repeat(32767)+"😀\uD834\uDD1Eñ").repeat(80)
            assertTrue(large.toByteArray().size>2*1024*1024)
            dao.save(PrivatePreferenceEntity("appearance_preferences",large))
            dao.save(PrivatePreferenceEntity("empty",""))
            val direct=runCatching {
                db.openHelper.readableDatabase.query("SELECT payload FROM private_preferences WHERE name='appearance_preferences'").use {it.moveToFirst();it.getString(0)}
            }.exceptionOrNull()
            assertTrue(direct is android.database.sqlite.SQLiteBlobTooBigException)
            println("REPRODUCED CURSOR WINDOW FAILURE (synthetic data):\n${direct!!.stackTraceToString()}")
            assertEquals(large,dao.get("appearance_preferences")!!.payload)
            assertEquals("",dao.get("empty")!!.payload);assertNull(dao.get("absent"))
            assertEquals(listOf(PrivatePreferenceEntity("appearance_preferences",large),PrivatePreferenceEntity("empty","")),dao.all())
            assertEquals(50,db.openHelper.readableDatabase.version)
        } finally {db.close()}
    }
}
