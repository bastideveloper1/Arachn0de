package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
class PersonMemoryPeopleMigrationTest {
    @Test fun migration47PreservesPublicationsPhotosPinningAndAddsSafeAssociations()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>();context.deleteDatabase("arachn0de.db")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:SupportSQLiteOpenHelper.Callback(47) {
            override fun onCreate(db:SupportSQLiteDatabase) {}
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
        }).build())
        val sql=helper.writableDatabase
        val entities=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/47.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database").getJSONArray("entities")
        for(i in 0 until entities.length()) {
            val entity=entities.getJSONObject(i);val name=entity.getString("tableName")
            sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",name))
            val indexes=entity.optJSONArray("indices") ?: org.json.JSONArray()
            for(j in 0 until indexes.length())sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",name))
        }
        sql.execSQL("INSERT INTO persons VALUES ('owner','Yo',NULL,1,0,0)")
        sql.execSQL("INSERT INTO persons VALUES ('friend','Ana',NULL,1,0,0)")
        sql.execSQL("INSERT INTO person_photos VALUES ('photo','owner','11111111-1111-1111-1111-111111111111.png',3,1,0,0)")
        sql.execSQL("INSERT INTO person_memories VALUES ('post','owner','2020-01-02','Texto',7,1)")
        sql.execSQL("INSERT INTO person_memory_images VALUES ('image','post',NULL,'photo',0,NULL,'Histórico')")
        helper.close()
        val db=Arachn0deDatabase.create(context)
        try {
            assertEquals(50, db.openHelper.writableDatabase.version)
            val post=db.personMemoryDao().memories().single()
            assertEquals("post",post.id);assertEquals("Texto",post.description);assertEquals(7,post.position);assertTrue(post.isPinned);assertTrue(post.peopleVisible)
            assertEquals("Histórico",db.personMemoryDao().images().single().description)
            assertTrue(db.personMemoryDao().people().isEmpty())
            db.personMemoryDao().people(listOf(PersonMemoryPersonEntity("association","post","friend","Ana","Ana")))
            db.personDao().update("friend","Anita",null);db.personDao().delete("friend")
            val detached=db.personMemoryDao().people().single()
            assertNull(detached.personId);assertEquals("Anita",detached.profileName)
            db.personMemoryDao().remove("post");assertTrue(db.personMemoryDao().people().isEmpty())
        } finally {db.close();context.deleteDatabase("arachn0de.db")}
    }
}
