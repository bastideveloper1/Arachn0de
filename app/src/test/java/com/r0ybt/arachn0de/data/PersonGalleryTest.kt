package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonGalleryTest {
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:PersonRepository
    private val context get()=RuntimeEnvironment.getApplication()
    private val store get()=AvatarStore(context,syncDirectory=BackupFixture::syncDirectory)
    @Before fun setup()=runBlocking<Unit> {context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context);repo=PersonRepository(db,store);repo.save("a","Ana",null);repo.save("b","Bruno",null)}
    @After fun close() {db.close();context.deleteDatabase("arachn0de.db")}
    private suspend fun import():String {val f=java.io.File(context.cacheDir,"gallery.png").apply {writeBytes(BackupFixture.png())};return repo.importAvatar(android.net.Uri.fromFile(f))}
    @Test fun explicitPromotionRetainsSharedBytesAndReferencesThroughDeletionAndBackup()=runBlocking {
        val old=import();repo.save("a","Ana",old,false)
        val file=import();repo.savePhoto(PersonPhotoEntity("one","a",file,0,2f,.2f,.3f),"draft")
        val bytes=BackupAvatarFiles(context,BackupFixture::syncDirectory).read(file)
        assertTrue(repo.setPhotoAsAvatar("a","one"))
        assertEquals(file,db.personDao().get("a")!!.avatarFile);assertEquals(2f,db.personDao().get("a")!!.avatarZoom,0f)
        repo.cleanup();assertTrue(store.exists(old));assertTrue(store.exists(file));assertEquals(old,db.personGalleryDao().photos().single().file)
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val before=backups.snapshot();val inspected=backups.inspect(BackupFixture.archive(before).inputStream())
        try {backups.restore(inspected)} finally {backups.discard(inspected)}
        val restored=db.personDao().get("a")!!.avatarFile!!
        val restoredReference=db.personGalleryDao().photos().single().file
        assertNotEquals(restored,restoredReference);assertTrue(store.exists(restoredReference))
        assertArrayEquals(bytes,BackupAvatarFiles(context,BackupFixture::syncDirectory).read(restored))
        repo.removePhoto("one");assertFalse(store.exists(restoredReference));assertTrue(store.exists(restored));assertEquals(restored,db.personDao().get("a")!!.avatarFile)
        repo.save("a","Ana",null,false);repo.cleanup();assertFalse(store.exists(restored))
    }
    @Test fun invalidPromotionAndBirthdayFailureKeepPreviousDataAndImages()=runBlocking {
        val main=import();repo.save("a","Ana",main,false,birthday="1980-01-02")
        val photo=import();repo.savePhoto(PersonPhotoEntity("foreign","b",photo,0),"draft")
        assertTrue(runCatching {repo.setPhotoAsAvatar("a","foreign")}.isFailure)
        val own=import();repo.savePhoto(PersonPhotoEntity("own","a",own,0),"draft")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_promotion BEFORE UPDATE ON persons BEGIN SELECT RAISE(ABORT,'Injected'); END")
        assertTrue(runCatching {repo.setPhotoAsAvatar("a","own")}.isFailure)
        assertEquals(main,db.personDao().get("a")!!.avatarFile);assertEquals(own,db.personGalleryDao().photos().first {it.id=="own"}.file)
        assertTrue(store.exists(main));assertTrue(store.exists(own))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_promotion")
        assertTrue(runCatching {repo.save("a","Changed",photo,false,birthday="2000-02-30")}.isFailure)
        assertEquals("Ana",db.personDao().get("a")!!.name);assertEquals(main,db.personDao().get("a")!!.avatarFile)
        assertEquals("1980-01-02",db.personGalleryDao().birthdays().single().date);assertTrue(store.exists(main));assertTrue(store.exists(photo))
        assertTrue(runCatching {repo.save("new","New",null,birthday="2000-02-30")}.isFailure);assertNull(db.personDao().get("new"))
        assertTrue(repo.save("new","New",null,birthday="1930-02-28"));assertEquals("1930-02-28",db.personGalleryDao().birthdays().first {it.personId=="new"}.date)
    }
    @Test fun birthdayValidationAndAgeNeverMutateProfile()=runBlocking {
        repo.saveProfile(PersonProfileEntity("a",alias="Alias",skills="Skills"))
        repo.saveBirthday("a","2000-02-29")
        assertEquals(24,personAge("2000-02-29",LocalDate.of(2024,2,29)))
        assertEquals(23,personAge("2000-02-29",LocalDate.of(2024,2,28)))
        assertTrue(runCatching {repo.saveBirthday("a","2001-02-29")}.isFailure)
        assertTrue(runCatching {repo.saveBirthday("a","9999-01-01")}.isFailure)
        assertEquals("2000-02-29",db.personGalleryDao().birthdays().single().date)
        repo.saveBirthday("a","");assertTrue(db.personGalleryDao().birthdays().isEmpty());assertEquals("Alias",db.personProfileDao().profiles().single().alias)
    }
    @Test fun fourPhotosReorderPersistenceAndPrincipalFramingRemainIndependent()=runBlocking {
        val main=import();repo.save("a","Ana",main,false,zoom=4f,x=.2f,y=-.3f)
        repeat(4) {val file=import();repo.retainAvatar(file,"draft");repo.savePhoto(PersonPhotoEntity("photo$it","a",file,it,3f,.1f,.2f),"draft")}
        val extra=import();assertTrue(runCatching {repo.savePhoto(PersonPhotoEntity("fifth","a",extra,3),"draft")}.isFailure);repo.discardAvatar(extra,"draft");assertFalse(store.exists(extra))
        repo.reorderPhotos("a",listOf("photo3","photo2","photo1","photo0"));val expected=db.personGalleryDao().photos()
        db.close();db=Arachn0deDatabase.create(context);repo=PersonRepository(db,store)
        assertEquals(expected,db.personGalleryDao().photos());assertEquals(4f,db.personDao().get("a")!!.avatarZoom,0f);assertEquals(main,db.personDao().get("a")!!.avatarFile)
        repo.delete("a");assertTrue(db.personGalleryDao().photos().isEmpty());assertFalse(store.exists(main));expected.forEach {assertFalse(store.exists(it.file))}
    }
    @Test fun sharedReferencesReplacementCancellationAndFailedSaveReleaseSafely()=runBlocking {
        val shared=import();repo.save("a","Ana",shared,false);repo.savePhoto(PersonPhotoEntity("one","b",shared,0),"draft")
        repo.save("a","Ana",null,false);assertTrue(store.exists(shared))
        val replacement=import();repo.savePhoto(PersonPhotoEntity("one","b",replacement,0),"draft");assertFalse(store.exists(shared))
        val pending=import();repo.retainAvatar(pending,"editor");repo.discardAvatar(pending);assertTrue(store.exists(pending));repo.discardAvatar(pending,"editor");assertFalse(store.exists(pending))
        val failed=import();repo.retainAvatar(failed,"editor");db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_photo BEFORE INSERT ON person_photos BEGIN SELECT RAISE(ABORT,'Injected'); END")
        assertTrue(runCatching {repo.savePhoto(PersonPhotoEntity("two","b",failed,1),"editor")}.isFailure)
        repo.cleanup();assertTrue(store.exists(failed));assertEquals(replacement,db.personGalleryDao().photos().single().file)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_photo");repo.discardAvatar(failed,"editor");assertFalse(store.exists(failed));assertTrue(store.exists(replacement))
    }
    @Test fun newAndHistoricalBackupsRoundTripBytesAndRollback()=runBlocking {
        val shared=import();repo.save("a","Ana",shared,false,zoom=3f,x=.1f,y=.2f)
        repo.savePhoto(PersonPhotoEntity("one","a",shared,0,2f,.2f,.3f),"draft");repo.savePhoto(PersonPhotoEntity("two","b",shared,0),"draft");repo.saveBirthday("a","1980-05-10")
        repo.saveProfile(PersonProfileEntity("a",notes="Preserved"))
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory));val before=backups.snapshot()
        assertEquals(1,before.avatars.size);val json=BackupJson.encode(before);assertEquals(28,org.json.JSONObject(String(json)).getInt("dataVersion"));assertEquals(before.personPhotos,BackupJson.decode(json).personPhotos)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_photo BEFORE INSERT ON person_photos BEGIN SELECT RAISE(ABORT,'Injected'); END")
        assertTrue(runCatching {backups.restore(before)}.isFailure);assertEquals(before.personPhotos,db.personGalleryDao().photos());assertTrue(store.exists(shared))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_photo")
        val inspected=backups.inspect(BackupFixture.archive(before).inputStream());try {backups.restore(inspected)} finally {backups.discard(inspected)}
        val photos=db.personGalleryDao().photos();assertEquals(1,photos.map {it.file}.toSet().size);assertEquals(db.personDao().get("a")!!.avatarFile,photos.first().file)
        assertArrayEquals(before.avatars.getValue(shared),BackupAvatarFiles(context,BackupFixture::syncDirectory).read(photos.first().file))
        assertEquals(before.personBirthdays,db.personGalleryDao().birthdays());assertEquals(2f,photos.first {it.id=="one"}.zoom,0f)
        val historical=before.copy(personPhotos=emptyList(),personBirthdays=emptyList());val old=BackupJson.decode(BackupJson.encode(historical));assertTrue(old.personPhotos.isEmpty());backups.restore(old)
        assertTrue(db.personGalleryDao().photos().isEmpty());assertTrue(db.personGalleryDao().birthdays().isEmpty());assertEquals(before.personProfiles,db.personProfileDao().profiles())
    }
    @Test fun invalidBackupReferencesAndDatesFailBeforeReplacingData()=runBlocking {
        val file=import();repo.savePhoto(PersonPhotoEntity("one","a",file,0),"draft")
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory));val before=backups.snapshot()
        listOf(before.copy(personPhotos=listOf(before.personPhotos.single().copy(personId="missing"))),before.copy(personPhotos=before.personPhotos+before.personPhotos),before.copy(avatars=emptyMap()),before.copy(personBirthdays=listOf(PersonBirthdayEntity("a","2020-02-30")))).forEach {assertTrue(runCatching {backups.restore(it)}.isFailure);assertEquals(before.personPhotos,db.personGalleryDao().photos());assertTrue(store.exists(file))}
    }
    @Test fun durableJournalRecoversInterruptedPostCommitCleanup()=runBlocking {
        val old=import();repo.savePhoto(PersonPhotoEntity("one","a",old,0),"editor")
        val replacement=import();repo.retainAvatar(replacement,"editor")
        // Simulate interruption after the real transaction commits, before its cleanup.
        store.durable.record(setOf(old,replacement))
        db.personGalleryDao().save(PersonPhotoEntity("one","a",replacement,0,4f,.2f,.3f))
        db.close();db=Arachn0deDatabase.create(context);repo=PersonRepository(db,store)
        repo.cleanup();assertFalse(store.exists(old));assertTrue(store.exists(replacement));assertFalse(store.durable.hasJournal())
        repo.discardAvatar(replacement,"editor");assertTrue(store.exists(replacement));assertEquals(4f,db.personGalleryDao().photos().single().zoom,0f)
    }

}
