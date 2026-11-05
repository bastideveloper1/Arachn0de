package com.r0ybt.arachn0de.data

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class PersonBannerTest {
    @Test fun bannerRoundTripOriginalSharedReferenceRemovalAndCancellation()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("arachn0de.db")
        val db=Arachn0deDatabase.create(context)
        try {
            val avatars=AvatarStore(context,syncDirectory=BackupFixture::syncDirectory)
            val store=AttachmentStore(context,BackupFixture::syncDirectory)
            val attachments=AttachmentRepository(db,store)
            val people=PersonRepository(db,avatars)
            people.save("person","Ana",null)
            val source=File(context.cacheDir,"banner.png").apply {writeBytes(BackupFixture.png())}
            val original=source.readBytes()
            val file=attachments.importGallery(Uri.fromFile(source),"banner-draft")
            val banners=PersonBannerRepository(db,attachments)
            val row=PersonBannerEntity("person",file.id,2f,.2f,-.3f)
            banners.save(row,"banner-draft")
            assertEquals(1,db.attachmentDao().references(file.id))
            db.personMemoryDao().save(PersonMemoryEntity("crop","person",null,"Original intacto",0))
            db.personMemoryDao().images(listOf(PersonMemoryImageEntity("crop-image","crop",file.id,null,0,zoom=2f,x=.2f,y=-.3f)))
            val backups=BackupRepository(db,context,BackupAvatarFiles(context,syncDirectory=BackupFixture::syncDirectory))
            val data=backups.snapshot()
            val decoded=BackupJson.decode(BackupJson.encode(data))
            assertEquals(listOf(row),decoded.personBanners)
            assertEquals(2f,decoded.personMemoryImages.single().zoom)
            assertEquals(.2f,decoded.personMemoryImages.single().x)
            assertTrue(BackupJson.decode(BackupJson.encode(BackupFixture.empty())).personBanners.isEmpty())
            assertThrows(IllegalArgumentException::class.java) {data.copy(personBanners=listOf(row.copy(attachmentId="missing"))).validate()}
            val gallery=PersonMemoryRepository(db,context,attachments,people)
            gallery.create("person",listOf(file.id),"gallery")
            banners.remove("person")
            assertTrue(db.personProfileDao().banners().isEmpty())
            assertArrayEquals(original,store.file(file.storageName).readBytes())
            val cancelled=attachments.importGallery(Uri.fromFile(source),"cancel")
            attachments.discardDraft("cancel")
            assertTrue(store.file(file.storageName).exists())
            assertEquals(file.id,cancelled.id)
            // Restore the committed banner and verify metadata and exact bytes after staging/renaming.
            backups.restore(data)
            val restored=db.personProfileDao().banners().single()
            val restoredFile=requireNotNull(db.attachmentDao().get(restored.attachmentId))
            assertArrayEquals(original,store.file(restoredFile.storageName).readBytes())
            assertEquals(row,restored)
            assertEquals(2f,db.personMemoryDao().images().single().zoom)
            val avatar=people.importAvatar(Uri.fromFile(source))
            val later=people.importAvatar(Uri.fromFile(source))
            try {
                assertTrue(people.setGuestAvatarIfAbsent("person",avatar,com.r0ybt.arachn0de.domain.model.AvatarFraming()))
                assertFalse(people.setGuestAvatarIfAbsent("person",later,com.r0ybt.arachn0de.domain.model.AvatarFraming()))
                assertEquals(avatar,db.personDao().get("person")?.avatarFile)
            } finally {people.discardAvatar(avatar);people.discardAvatar(later)}
        } finally {db.close()}
    }
    @Test fun migrationPreservesExistingProfile()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("arachn0de.db")
        val old=Arachn0deDatabase.create(context)
        old.personDao().insert(PersonEntity("old","Existing profile",null))
        old.close()
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("arachn0de.db").path,null,0).use {db->
            db.execSQL("DROP TABLE person_banners")
            db.execSQL("DROP TABLE person_memory_images")
            val schema=javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/51.json")!!.bufferedReader().use {org.json.JSONObject(it.readText())}
            val entities=schema.getJSONObject("database").getJSONArray("entities")
            val image=(0 until entities.length()).map {entities.getJSONObject(it)}.single {it.getString("tableName")=="person_memory_images"}
            db.execSQL(image.getString("createSql").replace("\${TABLE_NAME}","person_memory_images"))
            val indices=image.getJSONArray("indices")
            repeat(indices.length()) {db.execSQL(indices.getJSONObject(it).getString("createSql").replace("\${TABLE_NAME}","person_memory_images"))}
            db.version=51
        }
        val upgraded=Arachn0deDatabase.create(context)
        try {assertEquals("Existing profile",upgraded.personDao().get("old")?.name);assertTrue(upgraded.personProfileDao().banners().isEmpty())}
        finally {upgraded.close()}
    }

}
