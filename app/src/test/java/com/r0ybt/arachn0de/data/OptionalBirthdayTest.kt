package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.calendar.CalendarBirthdays
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.PersonRepository
import com.r0ybt.arachn0de.domain.model.CalendarDay
import com.r0ybt.arachn0de.domain.model.Person
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class OptionalBirthdayTest {
    @Test fun partialFullAndLeapDatesValidateAndFormatWithoutInventingYears() {
        val spanish=Locale.forLanguageTag("es-CL")
        assertEquals("23 de octubre",PersonBirthDate.parse("--10-23").format(spanish))
        assertEquals("23 de octubre de 1995",PersonBirthDate.parse("1995-10-23").format(spanish))
        assertNull(personAge("--10-23"))
        assertTrue(PersonBirthDate.valid(""))
        assertTrue(PersonBirthDate.valid("--02-29"))
        assertTrue(PersonBirthDate.valid("2000-02-29"))
        listOf("--02-30","--00-23","--10-00","1995-02-29","0000-10-23","--2-9").forEach {assertFalse(it,PersonBirthDate.valid(it))}
        val calendar=CalendarBirthdays(listOf(Person("a","Ana",null)),listOf(PersonBirthdayEntity("a","--02-29")))
        assertNull(calendar.on(CalendarDay(2028,2,29)).single().age)
        assertTrue(calendar.on(CalendarDay(2027,2,28)).isEmpty())
    }

    @Test fun saveEditReopenBackupRestoreAndInvalidRollback()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("arachn0de.db")
        var db=Arachn0deDatabase.create(context)
        try {
            var repo=PersonRepository(db,AvatarStore(context,syncDirectory=BackupFixture::syncDirectory))
            repo.save("a","Ana",null,birthday="--02-29")
            assertEquals("--02-29",db.personGalleryDao().birthdays().single().date)
            repo.save("a","Ana",null,isNew=false,birthday="2000-02-29")
            repo.save("a","Ana",null,isNew=false,birthday="--02-29")
            db.close();db=Arachn0deDatabase.create(context)
            repo=PersonRepository(db,AvatarStore(context,syncDirectory=BackupFixture::syncDirectory))
            assertEquals("--02-29",db.personGalleryDao().birthdays().single().date)
            val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
            val snapshot=backup.snapshot()
            val bytes=BackupJson.encode(snapshot)
            assertEquals(47,org.json.JSONObject(String(bytes)).getInt("dataVersion"))
            val restored=BackupJson.decode(bytes)
            repo.saveBirthday("a","")
            assertTrue(db.personGalleryDao().birthdays().isEmpty())
            backup.restore(restored)
            assertEquals("--02-29",db.personGalleryDao().birthdays().single().date)
            assertTrue(runCatching {repo.save("a","Changed",null,isNew=false,birthday="1995-02-29")}.isFailure)
            assertEquals("Ana",db.personDao().get("a")!!.name)
            assertTrue(runCatching {backup.restore(restored.copy(personBirthdays=listOf(PersonBirthdayEntity("a","--02-30"))))}.isFailure)
            assertEquals("--02-29",db.personGalleryDao().birthdays().single().date)
            // Historical full-date payload remains version 39 and is restored unchanged.
            val old=BackupJson.encode(snapshot.copy(personBirthdays=listOf(PersonBirthdayEntity("a","1995-10-23"))))
            assertEquals(39,org.json.JSONObject(String(old)).getInt("dataVersion"))
            backup.restore(BackupJson.decode(old))
            assertEquals("1995-10-23",db.personGalleryDao().birthdays().single().date)
            repo.saveBirthday("a","")
            assertTrue(db.personGalleryDao().birthdays().isEmpty())
        } finally {db.close()}
    }
}
