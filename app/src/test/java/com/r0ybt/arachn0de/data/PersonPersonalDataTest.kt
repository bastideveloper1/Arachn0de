package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.PersonRepository
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class PersonPersonalDataTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()

    @Test fun room46PreservesNamesProfilesAndAssignmentsWhilePromotingNicknames()=runBlocking<Unit> {
        context.deleteDatabase("arachn0de.db")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db")
            .callback(object:SupportSQLiteOpenHelper.Callback(46) {
                override fun onCreate(db:SupportSQLiteDatabase) {}
                override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
            }).build())
        val sql=helper.writableDatabase
        val schema=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/46.json")!!.bufferedReader().use {it.readText()})
            .getJSONObject("database").getJSONArray("entities")
        repeat(schema.length()) {i->
            val entity=schema.getJSONObject(i);val table=entity.getString("tableName")
            sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
            val indices=entity.optJSONArray("indices") ?: JSONArray()
            repeat(indices.length()) {j->sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))}
        }
        sql.execSQL("INSERT INTO persons VALUES ('a','Nombre completo anterior',NULL,1,0,0),('b','Nombre sin apodo',NULL,1,0,0),('c','Sin ficha',NULL,1,0,0)")
        sql.execSQL("INSERT INTO person_profiles SELECT id,CASE WHEN id='a' THEN 'Luna' ELSE '' END,'Biografía','Notas','123','','','','','','','' FROM persons WHERE id!='c'")
        sql.execSQL("INSERT INTO projects(id,name,description,position,createdAt,updatedAt) VALUES('p','Núcleo','',0,1,1)")
        sql.execSQL("INSERT INTO nodes(id,projectId,title,description,isCompleted,position,createdAt,updatedAt,purpose) VALUES('t','p','Tarea','',0,0,1,1,'ACTION')")
        sql.execSQL("INSERT INTO node_person VALUES ('t','a')");helper.close()
        val db=Arachn0deDatabase.create(context)
        try {
            assertEquals(47,db.openHelper.writableDatabase.version)
            assertEquals("Luna",db.personDao().get("a")!!.name)
            assertEquals("Nombre sin apodo",db.personDao().get("b")!!.name)
            assertEquals("Sin ficha",db.personDao().get("c")!!.name)
            val profiles=db.personProfileDao().profiles().associateBy {it.personId}
            assertEquals("Nombre completo anterior",profiles.getValue("a").givenName)
            assertEquals("Nombre sin apodo",profiles.getValue("b").givenName)
            assertEquals("Sin ficha",profiles.getValue("c").givenName)
            assertEquals("Notas",profiles.getValue("a").notes)
            assertTrue(profiles.values.all {it.familyName.isEmpty() && it.documentId.isEmpty()})
            assertEquals(listOf("a"),db.personDao().assignmentIds("t"))
        } finally {db.close()}
    }

    @Test fun nicknameOnlyCreationAndPersonalFieldsReopenAndRestoreWithoutLeakingIntoPublicName()=runBlocking<Unit> {
        context.deleteDatabase("arachn0de.db")
        var db=Arachn0deDatabase.create(context)
        try {
            val people=PersonRepository(db,AvatarStore(context,syncDirectory=BackupFixture::syncDirectory))
            people.save("a","Luna",null,profile=PersonProfileEntity("a",alias="Luna"))
            assertTrue(db.personProfileDao().profiles().single().givenName.isEmpty())
            val profile=PersonProfileEntity("a",alias="Luna",notes="Intactas",givenName="Ana María",familyName="Pérez",documentId="12.345.678-K")
            people.save("a","Luna",null,isNew=false,profile=profile)
            val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
            val bytes=BackupJson.encode(backup.snapshot())
            assertEquals(39,JSONObject(String(bytes)).getInt("dataVersion"))
            val restored=BackupJson.decode(bytes)
            people.saveProfile(profile.copy(documentId="Otro"));backup.restore(restored)
            assertEquals(profile,db.personProfileDao().profiles().single())
            assertEquals("Luna",db.personDao().get("a")!!.name)
            db.close();db=Arachn0deDatabase.create(context)
            assertEquals(profile,db.personProfileDao().profiles().single())
            val invalid=JSONObject(String(bytes));invalid.getJSONArray("personProfiles").getJSONObject(0).put("documentId",123)
            assertTrue(runCatching {BackupJson.decode(invalid.toString().toByteArray())}.isFailure)
            val current=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
            assertTrue(runCatching {current.restore(restored.copy(personProfiles=listOf(profile.copy(personId="missing"))))}.isFailure)
            assertEquals(profile,db.personProfileDao().profiles().single())
        } finally {db.close()}
    }

    @Test fun historicalBackupsPreserveLegalNamesAndUseTheApprovedNicknameFallback() {
        val source=BackupFixture.empty().copy(persons=listOf(PersonEntity("a","Nombre anterior",null),PersonEntity("b","Sin apodo",null)),
            personProfiles=listOf(PersonProfileEntity("a",alias="Luna",notes="Conservar")))
        val legacy=JSONObject(String(BackupJson.encode(source))).put("dataVersion",38)
        val rows=legacy.getJSONArray("personProfiles")
        repeat(rows.length()) {i->listOf("givenName","familyName","documentId").forEach {rows.getJSONObject(i).remove(it)}}
        val decoded=BackupJson.decode(legacy.toString().toByteArray())
        assertEquals(listOf("Luna","Sin apodo"),decoded.persons.map {it.name})
        assertEquals("Nombre anterior",decoded.personProfiles.first {it.personId=="a"}.givenName)
        assertEquals("Conservar",decoded.personProfiles.first {it.personId=="a"}.notes)
        assertEquals("Sin apodo",decoded.personProfiles.first {it.personId=="b"}.givenName)
        val oldest=JSONObject().put("dataVersion",1).put("appVersion","old").put("createdAt",1)
            .put("projects",JSONArray()).put("nodes",JSONArray()).put("assignments",JSONArray()).put("avatars",JSONArray())
            .put("persons",JSONArray().put(JSONObject().put("id","a").put("name","Largo".repeat(4000)).put("avatarFile",JSONObject.NULL)))
        val old=BackupJson.decode(oldest.toString().toByteArray())
        assertEquals("Largo".repeat(4000),old.personProfiles.single().givenName)
        assertEquals(old,BackupJson.decode(BackupJson.encode(old)))
        rows.put(rows.getJSONObject(0))
        assertTrue(runCatching {BackupJson.decode(legacy.toString().toByteArray())}.isFailure)
    }

    @Test fun invalidPersonalDataRollsBackPublicNicknameAndProfileTogether()=runBlocking<Unit> {
        context.deleteDatabase("arachn0de.db");val db=Arachn0deDatabase.create(context)
        try {
            val people=PersonRepository(db,AvatarStore(context,syncDirectory=BackupFixture::syncDirectory))
            val original=PersonProfileEntity("a",alias="Luna",givenName="Nombre privado",documentId="Original")
            people.save("a","Luna",null,profile=original)
            assertTrue(runCatching {people.save("a","Cambio",null,isNew=false,profile=original.copy(documentId="x".repeat(16001)))}.isFailure)
            assertEquals("Luna",db.personDao().get("a")!!.name)
            assertEquals(original,db.personProfileDao().profiles().single())
        } finally {db.close()}
    }
}
