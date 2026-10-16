package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.security.EncryptedPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class PersonFavoritesTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var people:PersonRepository
    private fun open() {db=Arachn0deDatabase.create(context);people=PersonRepository(db,AvatarStore(context,syncDirectory=BackupFixture::syncDirectory))}
    @Before fun setup()=runBlocking<Unit> {context.deleteDatabase("arachn0de.db");open();people.save("a","Ana",null);people.save("b","Bruno",null);people.identifySelf("a")}
    @After fun close() {db.close()}
    @Test fun persistenceBackupHistoricalDefaultAndDeletionKeepIdentityAndOtherPreferences()=runBlocking<Unit> {
        assertTrue(people.favoritePeople().isEmpty())
        val other=PrivatePreferenceEntity("other",EncryptedPreferences.encode(mapOf("value" to "conservar")))
        db.privatePreferenceDao().save(other)
        people.toggleFavorite("b")
        db.close();open()
        assertEquals(setOf("b"),people.favoritePeople());assertEquals("a",people.observeSelf().first()!!.personId)
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val before=backups.snapshot()
        val historical=before.copy(privatePreferences=before.privatePreferences.filter {it.name!=PersonFavorites.NAME})
        val historicalInspection=backups.inspect(BackupFixture.archive(historical).inputStream())
        try {backups.restore(historicalInspection)} finally {backups.discard(historicalInspection)}
        assertTrue(people.favoritePeople().isEmpty())
        val restored=backups.inspect(BackupFixture.archive(before).inputStream())
        try {backups.restore(restored)} finally {backups.discard(restored)}
        assertEquals(setOf("b"),people.favoritePeople());assertEquals(other,db.privatePreferenceDao().get("other"))
        people.delete("b");assertTrue(people.favoritePeople().isEmpty());backups.snapshot().validate()
        assertEquals("a",people.observeSelf().first()!!.personId)
    }
    @Test fun malformedOrDanglingFavoriteBackupsFailBeforeChangingCurrentData()=runBlocking<Unit> {
        people.toggleFavorite("b")
        val backups=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val before=backups.snapshot()
        val payloads=listOf(PersonFavorites.encode(setOf("absent")),EncryptedPreferences.encode(mapOf("version" to 2,"people" to setOf("b"))),EncryptedPreferences.encode(mapOf("version" to 1,"people" to "b")))
        for(payload in payloads) {
            val invalid=before.copy(privatePreferences=before.privatePreferences.filter {it.name!=PersonFavorites.NAME}+PrivatePreferenceEntity(PersonFavorites.NAME,payload))
            assertTrue(runCatching {invalid.validate()}.isFailure)
            val json=org.json.JSONObject(BackupJson.encode(before).toString(Charsets.UTF_8))
            val rows=json.getJSONArray("privatePreferences")
            for(i in 0 until rows.length()) if(rows.getJSONObject(i).getString("name")==PersonFavorites.NAME) rows.getJSONObject(i).put("payload",payload)
            val archive=java.io.ByteArrayOutputStream().also {BackupContainer.write(json.toString().toByteArray(),it)}.toByteArray()
            assertTrue(runCatching {backups.inspect(archive.inputStream())}.isFailure)
            val after=backups.snapshot()
            assertEquals(before.persons,after.persons);assertEquals(before.privatePreferences,after.privatePreferences);assertEquals(before.localIdentities,after.localIdentities)
        }
        assertTrue(runCatching {people.toggleFavorite("absent")}.isFailure)
        assertEquals(setOf("b"),people.favoritePeople())
    }
}
