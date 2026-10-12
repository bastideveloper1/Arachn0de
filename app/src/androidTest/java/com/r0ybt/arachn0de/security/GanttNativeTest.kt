package com.r0ybt.arachn0de.security

import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.gantt.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.*
import java.util.UUID

class GanttNativeTest {
    @Test fun demoNativePersistenceRestoreAndRealPng()=runBlocking {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        require(target.packageName=="com.r0ybt.arachn0de.securityvalidation") {"Solo paquete de validación aislado."}
        val root=File(target.cacheDir,"gantt-${UUID.randomUUID()}").apply {mkdirs()}
        val context=object:ContextWrapper(target) {
            override fun getApplicationContext():Context=this
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getCacheDir()=File(root,"cache").apply {mkdirs()}
            override fun getDatabasePath(name:String)=File(root,name)
        }
        val key=ByteArray(32) {(it+1).toByte()};var db=Arachn0deDatabase.create(context,key)
        try {
            val result=GanttDemo.create(db,PersonRepository(db,AvatarStore(context)))
            val before=BackupRepository(db,context).snapshot()
            assertEquals(30,org.json.JSONObject(String(BackupJson.encode(before))).getInt("dataVersion"))
            BackupRepository(db,context).restore(BackupJson.decode(BackupJson.encode(before)))
            assertEquals(before.nodes,db.backupDao().nodes());assertEquals(before.ganttAnnotations,db.ganttAnnotationDao().all())
            db.close();db=Arachn0deDatabase.create(context,key)
            assertEquals(before.ganttAnnotations,db.ganttAnnotationDao().all())
            val state=ManagementRepository(db).iterations.observe(result.context).first()
            val responsible=workResponsibilities(state.people.records.nodes,db.backupDao().assignments().map {it.nodeId to it.personId})
            val output=File(target.getExternalFilesDir(null),"gantt-demo-room38").apply {mkdirs()}
            val cases=listOf("semana-tareas-vertical" to Pair(GanttSelection(GanttScope.WEEK,sprint=result.sprints[1],anchor=result.base),true),"sprint-integrantes-vertical" to Pair(GanttSelection(sprint=result.sprints[1],people=true),true),"todos-sprints-horizontal" to Pair(GanttSelection(scope=GanttScope.ALL,scale=GanttScale.WEEK),false))
            cases.forEach {(name,config)->
                val doc=ganttDocument(state,result.context,config.first,responsible,db.ganttAnnotationDao().all(),"DEMO — Biblioteca digital")
                GanttRenderer.pages(doc,config.second).forEach {page->
                    val file=File(output,"$name-${page.index}.png")
                    file.outputStream().use {GanttRenderer.png(doc,page,true,it)}
                    val bitmap=BitmapFactory.decodeFile(file.path);assertNotNull(bitmap);assertEquals(page.width,bitmap.width);assertEquals(page.height,bitmap.height);bitmap.recycle()
                    println("GANTT_PNG ${file.path} ${page.width}x${page.height}")
                }
            }
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {assertFalse(it.moveToFirst())}
        } finally {db.close();root.deleteRecursively()}
    }
}
