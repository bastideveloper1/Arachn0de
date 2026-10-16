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
class HierarchyPinMigrationTest {
    @Test fun room40PreservesRecordsAndOrderWithUnpinnedDefaults()=runBlocking<Unit> {
        val context=ApplicationProvider.getApplicationContext<Application>();context.deleteDatabase("arachn0de.db")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:SupportSQLiteOpenHelper.Callback(40) {
            override fun onCreate(db:SupportSQLiteDatabase) {}
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
        }).build())
        val sql=helper.writableDatabase
        val schema=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/40.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database").getJSONArray("entities")
        for(i in 0 until schema.length()) {
            val entity=schema.getJSONObject(i);val table=entity.getString("tableName")
            sql.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
            val indexes=entity.optJSONArray("indices") ?: org.json.JSONArray()
            for(j in 0 until indexes.length()) sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
        }
        sql.execSQL("INSERT INTO projects(id,name,description,position,createdAt,updatedAt) VALUES ('project','Original','Contenido',7,1,2)")
        sql.execSQL("INSERT INTO nodes(id,projectId,parentId,title,description,isCompleted,position,createdAt,updatedAt,purpose) VALUES ('layer','project',NULL,'Capa','Descripción',0,3,4,5,'LAYER')")
        helper.close()
        val db=Arachn0deDatabase.create(context)
        try {
            val project=db.projectDao().getById("project")!!;val layer=db.nodeDao().getById("layer")!!
            assertFalse(project.isPinned);assertFalse(layer.isPinned)
            assertEquals(7,project.position);assertEquals(3,layer.position)
            assertEquals("Contenido",project.description);assertEquals("Descripción",layer.description)
            assertEquals(2L,project.updatedAt);assertEquals(5L,layer.updatedAt)
        } finally {db.close()}
    }
}
