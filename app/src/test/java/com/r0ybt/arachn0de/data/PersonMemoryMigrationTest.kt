package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.r0ybt.arachn0de.data.local.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class PersonMemoryMigrationTest {
    @Test fun room39AddsSingleImageEntriesWithoutInventingDatesOrChangingAvatar()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>();context.deleteDatabase("arachn0de.db")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:SupportSQLiteOpenHelper.Callback(39) {
            override fun onCreate(db:SupportSQLiteDatabase) {}
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
        }).build())
        val sql=helper.writableDatabase
        val schema=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/39.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database").getJSONArray("entities")
        for(i in 0 until schema.length()) {
            val entity=schema.getJSONObject(i);val table=entity.getString("tableName")
            sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
            val indexes=entity.optJSONArray("indices") ?: org.json.JSONArray()
            for(j in 0 until indexes.length()) sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
        }
        val file="11111111-1111-1111-1111-111111111111.png"
        sql.execSQL("INSERT INTO persons VALUES ('person','Ana',?,2,.1,.2)",arrayOf(file))
        sql.execSQL("INSERT INTO person_photos VALUES ('photo','person',?,3,3,.3,.4)",arrayOf(file))
        helper.close()
        val db=Arachn0deDatabase.create(context)
        try {
            val entry=db.personMemoryDao().memories().single()
            assertEquals("photo",entry.id);assertNull(entry.date);assertEquals("",entry.description);assertEquals(3,entry.position)
            assertEquals("photo",db.personMemoryDao().images().single().legacyPhotoId)
            assertEquals(file,db.personDao().get("person")!!.avatarFile)
            assertEquals(2f,db.personDao().get("person")!!.avatarZoom,0f)
            assertEquals(3f,db.personGalleryDao().photos().single().zoom,0f)
        } finally {db.close()}
    }
}
