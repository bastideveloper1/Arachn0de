package com.r0ybt.arachn0de.data

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class PersonMemoryOrderTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var people:PersonRepository
    private lateinit var gallery:PersonMemoryRepository
    private lateinit var original:String
    @Before fun open()=runBlocking<Unit> {
        context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context)
        people=PersonRepository(db,AvatarStore(context,syncDirectory=BackupFixture::syncDirectory))
        gallery=PersonMemoryRepository(db,context,AttachmentRepository(db,AttachmentStore(context,BackupFixture::syncDirectory)),people)
        people.save("person","Ana",null)
        val source=java.io.File(context.cacheDir,"memory-order.png").apply {writeBytes(BackupFixture.png())}
        original=people.importAvatar(Uri.fromFile(source));people.savePhoto(PersonPhotoEntity("legacy","person",original,0),"seed")
        people.save("person","Ana",original,isNew=false);db.personMemoryDao().clear()
    }
    @After fun close() {db.close()}
    private suspend fun create(id:String) {
        gallery.save(PersonMemoryEntity(id,"person","2000-01-01","Compartida $id",999),listOf(PersonMemoryImageEntity("image-$id",id,null,"legacy",0,"1999-01-01","Histórica $id")),"draft-$id")
    }
    private suspend fun order()=db.personMemoryDao().memories().map {it.id}
    @Test fun newEntriesPinGroupsDragAndDeleteKeepIndependentStableOrders()=runBlocking<Unit> {
        create("a");create("b");create("c");assertEquals(listOf("c","b","a"),order())
        gallery.pin("b",true);gallery.pin("c",true);assertEquals(listOf("b","c","a"),order())
        gallery.reorder("person","c","b");assertEquals(listOf("c","b","a"),order())
        val before=db.personMemoryDao().memories()
        assertTrue(runCatching {gallery.reorder("person","a","b")}.isFailure);assertEquals(before,db.personMemoryDao().memories())
        gallery.pin("c",false);assertEquals(listOf("b","a","c"),order())
        gallery.deleteEntry("b");create("d");assertEquals(listOf("d","a","c"),order())
        assertEquals(listOf(0,1,2),db.personMemoryDao().memories().map {it.position})
        assertTrue(java.io.File(context.filesDir,"avatars/$original").exists())
    }
    @Test fun descriptionEditsKeepHistoricalTextDatesPinAndLatestOrderAndRollbackOnInvalidCreate()=runBlocking<Unit> {
        create("a");val stale=db.personMemoryDao().memories().single();create("b");gallery.pin("a",true)
        val images=db.personMemoryDao().images().filter {it.memoryId=="a"}
        gallery.save(stale.copy(description="Nueva compartida",date="2024-01-01"),images,"edit")
        val a=db.personMemoryDao().memories().first {it.id=="a"};assertTrue(a.isPinned);assertEquals(0,a.position)
        assertEquals(images,db.personMemoryDao().images().filter {it.memoryId=="a"})
        val before=db.personMemoryDao().memories()
        assertTrue(runCatching {gallery.save(PersonMemoryEntity("bad","person",null,"",0),emptyList(),"bad")}.isFailure)
        assertEquals(before,db.personMemoryDao().memories())
    }
    @Test fun backupRoundTripKeepsPinOrderHistoryAndRejectsInvalidFlagsBeforeRestore()=runBlocking<Unit> {
        create("a");create("b");gallery.pin("a",true)
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val before=backup.snapshot();val encoded=BackupJson.encode(before)
        assertEquals(35,JSONObject(String(encoded)).getInt("dataVersion"))
        val candidate=backup.inspect(backup.create().readBytes().inputStream())
        gallery.pin("a",false);gallery.deleteEntry("b");backup.restore(candidate);backup.discard(candidate)
        assertEquals(before.personMemories,backup.snapshot().personMemories);assertEquals(before.personMemoryImages,backup.snapshot().personMemoryImages)
        val damaged=JSONObject(String(encoded));damaged.getJSONArray("personMemories").getJSONObject(0).put("isPinned","true")
        assertTrue(runCatching {BackupJson.decode(damaged.toString().toByteArray())}.isFailure)
        assertEquals(before.personMemories,backup.snapshot().personMemories)
        val old=JSONObject(String(encoded)).put("dataVersion",32)
        val rows=old.getJSONArray("personMemories");repeat(rows.length()) {rows.getJSONObject(it).remove("isPinned")}
        val historic=BackupJson.decode(old.toString().toByteArray());assertTrue(historic.personMemories.none {it.isPinned})
        backup.restore(historic);assertTrue(db.personMemoryDao().memories().none {it.isPinned})
        assertEquals(before.personMemoryImages,db.personMemoryDao().images())
    }
    @Test fun positionsSurviveDatabaseReopenAndRemovingLastImageNormalizesRemainingEntries()=runBlocking<Unit> {
        create("a");create("b");create("c");gallery.reorder("person","a","c");gallery.pin("b",true)
        val before=db.personMemoryDao().memories();db.close();db=Arachn0deDatabase.create(context)
        assertEquals(before,db.personMemoryDao().memories())
        people=PersonRepository(db,AvatarStore(context,syncDirectory=BackupFixture::syncDirectory))
        gallery=PersonMemoryRepository(db,context,AttachmentRepository(db,AttachmentStore(context,BackupFixture::syncDirectory)),people)
        gallery.removeImage("image-a");assertEquals(listOf("b","c"),order())
        assertEquals(listOf(0,0),db.personMemoryDao().memories().map {it.position})
        assertTrue(java.io.File(context.filesDir,"avatars/$original").exists())
    }
}
