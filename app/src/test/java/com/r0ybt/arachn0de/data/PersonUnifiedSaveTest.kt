package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.PersonRepository
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class PersonUnifiedSaveTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var people:PersonRepository
    @Before fun open() {context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context);people=PersonRepository(db,AvatarStore(context,syncDirectory=BackupFixture::syncDirectory))}
    @After fun close() {db.close()}
    @Test fun combinedSaveRoundTripsExistingFormatWithoutTruncatingHistoricalFields()=runBlocking<Unit> {
        val old=PersonProfileEntity("person",description="b".repeat(400),notes="Notas",qualities="Cualidades",skills="Habilidades",interests="Intereses",availability="Disponible")
        people.save("person","Ana",null);people.saveProfile(old);people.saveBirthday("person","1980-04-12")
        val updated=old.copy(alias="Ani",phones="123\n456",emails="a@example.org",addresses="Dirección",links="www.google.com\nhttps://example.org")
        assertTrue(people.save("person","Ana nueva",null,isNew=false,profile=updated,identity=true))
        assertEquals("1980-04-12",db.personGalleryDao().birthdays().single().date)
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val snapshot=backup.snapshot();val decoded=BackupJson.decode(BackupJson.encode(snapshot))
        assertEquals(updated,decoded.personProfiles.single())
        assertEquals("person",decoded.localIdentities.single().personId)
        people.saveProfile(updated.copy(notes="Cambio"));backup.restore(decoded)
        assertEquals(updated,db.personProfileDao().profiles().single())
        assertEquals(snapshot.personBirthdays,db.personGalleryDao().birthdays())
    }
    @Test fun invalidBirthdayRollsBackNameProfileAndIdentityTogether()=runBlocking<Unit> {
        people.save("person","Ana",null);val original=PersonProfileEntity("person",notes="Intactas");people.saveProfile(original)
        people.saveBirthday("person","1980-04-12")
        assertTrue(runCatching {people.save("person","Cambio",null,isNew=false,profile=original.copy(alias="Cambio"),birthday="1980-02-31",identity=true)}.isFailure)
        assertEquals("Ana",db.personDao().get("person")!!.name)
        assertEquals(original,db.personProfileDao().profiles().single())
        assertEquals("1980-04-12",db.personGalleryDao().birthdays().single().date)
        assertTrue(db.cascadeRelationDao().identities().isEmpty())
    }
}
