package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class EffortPersistenceTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    @Before fun open() {context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context)}
    @After fun close() {db.close()}
    private fun configuration(reverse:Boolean=false)=SortConfiguration(false,listOf(SortRule(SortCriterion.EFFORT,true,reverse))+SortCriterion.entries.filter {it!=SortCriterion.EFFORT}.map {SortRule(it)})
    @Test fun migration53KeepsRowsInitializesZeroAndRejectsInvalidValues() {
        db.close();context.deleteDatabase("arachn0de.db")
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db").callback(object:SupportSQLiteOpenHelper.Callback(53) {
            override fun onCreate(db:SupportSQLiteDatabase) {}
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected")
        }).build())
        val sql=helper.writableDatabase
        val schema=JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/53.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database").getJSONArray("entities")
        for(i in 0 until schema.length()) {
            val row=schema.getJSONObject(i);val table=row.getString("tableName")
            sql.execSQL(row.getString("createSql").replace("\${TABLE_NAME}",table))
            val indices=row.optJSONArray("indices") ?: org.json.JSONArray()
            for(j in 0 until indices.length())sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
        }
        sql.execSQL("INSERT INTO projects(id,name,description,position,createdAt,updatedAt) VALUES ('p','Original','Texto',2,1,2)")
        sql.execSQL("INSERT INTO nodes(id,projectId,title,description,isCompleted,position,createdAt,updatedAt) VALUES ('task','p','Tarea','Contenido',1,3,4,5)")
        helper.close();db=Arachn0deDatabase.create(context)
        assertEquals(54,db.openHelper.writableDatabase.version)
        db.openHelper.writableDatabase.query("SELECT title,description,isCompleted,position,effort FROM nodes WHERE id='task'").use {
            assertTrue(it.moveToFirst());assertEquals("Tarea",it.getString(0));assertEquals("Contenido",it.getString(1));assertEquals(1,it.getInt(2));assertEquals(3,it.getInt(3));assertEquals(0,it.getInt(4))
        }
        assertTrue(runCatching {db.openHelper.writableDatabase.execSQL("UPDATE nodes SET effort=6 WHERE id='task'")}.isFailure)
    }
    @Test fun levelsSurviveMoveRestartBackupRestoreAndBadRestoreKeepsPreviousData()=runBlocking<Unit> {
        val repository=NodeRepository(db)
        db.projectDao().insert(ProjectEntity("p","Núcleo","",0,1,1))
        db.projectDao().insert(ProjectEntity("q","Otro","",1,1,1))
        val task=repository.createNode("p",null,"Tarea")
        for(level in 0..5) {assertTrue(repository.setEffort(task.id,level));assertEquals(level,repository.getNode(task.id)!!.effort)}
        assertTrue(runCatching {repository.setEffort(task.id,-1)}.isFailure)
        val layer=repository.createNode("p",null,"Capa",purpose=NodePurpose.LAYER)
        assertFalse(repository.setEffort(layer.id,4))
        assertTrue(repository.moveNode(task.id,layer.id))
        assertEquals(5,repository.getNode(task.id)!!.effort)
        assertTrue(repository.moveToContainer(setOf(task.id),"q",null))
        db.close();db=Arachn0deDatabase.create(context)
        assertEquals(5,db.nodeDao().getById(task.id)!!.effort)
        val prefs=NodeSortPreferenceRepository(db);prefs.configure("q:project-root",configuration())
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val rule=RecurrenceRuleEntity("rule","q",null,"Repetida","Original",null,null,0,"DAILY",1,null,0,"ACTIVE","UTC",0,null)
        val snapshot=backup.snapshot().copy(recurrenceRules=listOf(rule));val bytes=BackupJson.encode(snapshot)
        assertEquals(57,JSONObject(String(bytes)).getInt("dataVersion"))
        val decoded=BackupJson.decode(bytes)
        db.close();context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context)
        val recovered=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        recovered.restore(decoded)
        assertEquals(listOf(rule),recovered.snapshot().recurrenceRules)
        assertEquals(5,db.nodeDao().getById(task.id)!!.effort)
        assertEquals(snapshot.nodeSortPreferences,db.nodeSortPreferenceDao().all())
        val bad=decoded.copy(nodes=decoded.nodes.map {if(it.id==task.id)it.copy(effort=6) else it})
        assertTrue(runCatching {recovered.restore(bad)}.isFailure)
        assertEquals(5,db.nodeDao().getById(task.id)!!.effort)
        val corrupted=JSONObject(String(bytes));corrupted.getJSONArray("nodes").getJSONObject(0).put("effort",1.5)
        assertTrue(runCatching {BackupJson.decode(corrupted.toString().toByteArray())}.isFailure)
    }
    @Test fun dormantEffortSurvivesTypeConversionAndBackup()=runBlocking<Unit> {
        db.projectDao().insert(ProjectEntity("p","Núcleo","",0,1,1))
        val repository=NodeRepository(db)
        val task=repository.createNode("p",null,"Tarea")
        repository.setEffort(task.id,4)
        assertTrue(repository.convertPurpose(task.id,NodePurpose.LAYER))
        val project=ProjectNestingRepository(db).promote(task.id)
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val data=BackupJson.decode(BackupJson.encode(backup.snapshot()))
        assertEquals(4,data.conversionRoots.single().effort)
        backup.restore(data)
        val layer=ProjectNestingRepository(db).move(project,"p",null)
        assertEquals(NodePurpose.LAYER,repository.getNode(layer)!!.purpose)
        assertEquals(4,repository.getNode(layer)!!.effort)
    }
    @Test fun effortConfigurationOnlyReordersOnApplyAndInheritedLayerUsesIt()=runBlocking<Unit> {
        db.projectDao().insert(ProjectEntity("p","Núcleo","",0,1,1))
        val rows=listOf(NodeEntity("layer","p",null,"Capa","",false,0,1,1,purpose="LAYER"),
            NodeEntity("low","p","layer","Baja","",false,0,1,1,effort=1),NodeEntity("high","p","layer","Alta","",false,1,1,1,effort=5),
            NodeEntity("undefined","p","layer","Sin definir","",false,2,1,1),NodeEntity("done","p","layer","Hecha","",true,3,1,1,effort=5))
        rows.forEach {db.nodeDao().insert(it)}
        val prefs=NodeSortPreferenceRepository(db)
        prefs.configure("p:project-root",configuration())
        prefs.configure("p:layer",configuration().copy(inherited=true))
        assertEquals(rows,db.nodeDao().getProjectNodes("p").sortedBy {rows.indexOfFirst {r->r.id==it.id}})
        NodeRepository(db).applyConfiguredOrder("p:layer",configuration().copy(inherited=true))
        fun ordered()=runBlocking {db.nodeDao().getProjectNodes("p").filter {it.parentId=="layer"}.sortedBy {it.position}.map {it.id}}
        assertEquals(listOf("high","low","undefined","done"),ordered())
        prefs.configure("p:layer",configuration(true))
        assertEquals(listOf("high","low","undefined","done"),ordered())
        NodeRepository(db).applyConfiguredOrder("p:layer",configuration(true))
        assertEquals(listOf("low","high","undefined","done"),ordered())
    }
}
