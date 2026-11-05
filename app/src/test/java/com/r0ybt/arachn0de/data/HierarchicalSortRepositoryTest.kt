package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class HierarchicalSortRepositoryTest {
    @Test fun favoritePreferenceAppliesWithinSiblingsAndBackupRestoresOrRollsBack()=runBlocking<Unit> {
        val access=com.r0ybt.arachn0de.access.AccessRepository(db)
        access.toggle("node:nested-a",true)
        val value=config(SortCriterion.NAME,true).copy(favorites=FavoriteOrder.FIRST,layersFirst=true)
        preferences.configure("p:project-root",value)
        nodes.applyConfiguredOrder("p:project-root",value,setOf("nested-b","nested-a"))
        assertEquals(listOf("nested-a","nested-b"),order("layer"))
        assertEquals(value.favorites,SortConfigurationCodec.decode(db.nodeSortPreferenceDao().all().first {it.context=="p:layer"}.mode).favorites)
        preferences.configure(SortConfigurationCodec.PROJECTS_CONTEXT,SortConfiguration(favorites=FavoriteOrder.LAST))
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val before=backup.snapshot()
        val encoded=BackupJson.encode(before)
        assertEquals(59,org.json.JSONObject(String(encoded)).getInt("dataVersion"))
        val restored=BackupJson.decode(encoded)
        assertEquals(before.nodeSortPreferences,restored.nodeSortPreferences)
        preferences.configure(SortConfigurationCodec.PROJECTS_CONTEXT,SortConfiguration())
        backup.restore(restored)
        assertEquals(before.nodeSortPreferences,backup.snapshot().nodeSortPreferences)
        assertEquals(before.nodes,backup.snapshot().nodes)
        assertEquals(before.privatePreferences,backup.snapshot().privatePreferences)
        db.clearAllTables()
        backup.restore(restored)
        assertEquals(before.nodeSortPreferences,backup.snapshot().nodeSortPreferences)
        assertEquals(before.nodes,backup.snapshot().nodes)
        val invalid=before.copy(nodeSortPreferences=before.nodeSortPreferences.map {it.copy(mode=it.mode.replace("FIRST","INVALID").replace("LAST","INVALID"))})
        assertTrue(runCatching {backup.restore(invalid)}.isFailure)
        assertEquals(before.nodeSortPreferences,backup.snapshot().nodeSortPreferences)
        assertEquals(before.nodes,backup.snapshot().nodes)
        val downgraded=org.json.JSONObject(String(encoded)).put("dataVersion",58)
        assertTrue(runCatching {BackupJson.decode(downgraded.toString().toByteArray())}.isFailure)
        // Older seven/eight-criterion payloads keep their exact bytes and default to NONE.
        for(effort in listOf(false,true)) {
            val json=org.json.JSONObject(SortConfigurationCodec.encode(SortConfiguration()).removePrefix(SortConfigurationCodec.PREFIX))
            if(!effort) json.put("rules",org.json.JSONArray(SortCriterion.entries.filter {it!=SortCriterion.EFFORT}.map {org.json.JSONObject().put("key",it.name).put("on",false).put("reverse",false)}))
            val mode=SortConfigurationCodec.PREFIX+json
            val old=before.copy(nodeSortPreferences=listOf(NodeSortPreferenceEntity("p:project-root",mode)))
            val read=BackupJson.decode(BackupJson.encode(old))
            assertEquals(mode,read.nodeSortPreferences.single().mode)
            assertEquals(FavoriteOrder.NONE,SortConfigurationCodec.decode(mode).favorites)
        }
    }
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    private lateinit var preferences:NodeSortPreferenceRepository
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    @Before fun setup()=runBlocking<Unit> {
        db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).allowMainThreadQueries().build()
        nodes=NodeRepository(db) {100L};preferences=NodeSortPreferenceRepository(db)
        db.projectDao().insert(ProjectEntity("p","Núcleo","",0,1,1,verificationEnabled=true))
        db.nodeDao().insert(NodeEntity("a","p",null,"A","A original",false,0,1,1,dueAt=30,priority="HIGH"))
        db.nodeDao().insert(NodeEntity("b","p",null,"B","B original",false,1,1,1,dueAt=10,awaitingVerification=true))
        db.nodeDao().insert(NodeEntity("done","p",null,"Terminada","Original",true,2,1,1,dueAt=1))
        db.nodeDao().insert(NodeEntity("layer","p",null,"Capa","Original",false,3,1,1,purpose="LAYER"))
        db.nodeDao().insert(NodeEntity("nested-b","p","layer","Beta","",false,0,1,1))
        db.nodeDao().insert(NodeEntity("nested-a","p","layer","Alfa","",false,1,1,1))
        db.tagDao().insert(listOf(TagEntity("alpha","Álfa","álfa"),TagEntity("beta","Beta","beta"),TagEntity("zeta","Zeta","zeta")))
        db.tagDao().assignNodes(listOf(NodeTagEntity("a","alpha"),NodeTagEntity("a","zeta"),NodeTagEntity("b","beta")))
    }
    @After fun close() {db.close()}
    private fun config(key:SortCriterion,reverse:Boolean=false)=SortConfiguration(false,listOf(SortRule(key,true,reverse))+SortCriterion.entries.filter {it!=key}.map {SortRule(it)})
    private suspend fun order(parent:String?=null)=db.nodeDao().getProjectNodes("p").filter {it.parentId==parent}.sortedBy {it.position}.map {it.id}
    @Test fun configurationDoesNotMoveCardsApplyMovesThemAndManualThenReapplyWorks()=runBlocking<Unit> {
        val before=db.nodeDao().getProjectNodes("p")
        val value=config(SortCriterion.DUE)
        preferences.configure("p:project-root",value)
        assertEquals(before,db.nodeDao().getProjectNodes("p"))
        nodes.applyConfiguredOrder("p:project-root",value)
        assertEquals(listOf("b","a","layer","done"),order())
        assertTrue(nodes.reorderNodeTo("a",null,"b"))
        assertEquals(listOf("a","b","layer","done"),order())
        nodes.applyConfiguredOrder("p:project-root",value)
        assertEquals(listOf("b","a","layer","done"),order())
        assertEquals(before.map {it.copy(position=0,updatedAt=1)}.sortedBy {it.id},db.nodeDao().getProjectNodes("p").map {it.copy(position=0,updatedAt=1)}.sortedBy {it.id})
    }
    @Test fun applyOnlyReordersVisibleSiblingSlotsAndLayersFirstDoesNotSortLayers()=runBlocking<Unit> {
        val value=config(SortCriterion.NAME,true)
        nodes.applyConfiguredOrder("p:project-root",value,setOf("a","b"))
        assertEquals(listOf("b","a","done","layer"),order())
        assertEquals(listOf("nested-b","nested-a"),order("layer"))
        nodes.applyConfiguredOrder("p:project-root",value.copy(layersFirst=true))
        assertEquals("layer",order().first());assertEquals("done",order().last())
        assertTrue(db.nodeSortPreferenceDao().all().first {it.context=="p:project-root"}.layersFirst)
    }
    @Test fun inheritedAndOwnLayerConfigurationsAreAppliedWithoutChangingParentageOrLosingLabels()=runBlocking<Unit> {
        val root=config(SortCriterion.NAME)
        preferences.configure("p:project-root",root)
        nodes.applyConfiguredOrder("p:layer",root.copy(inherited=true))
        assertEquals(listOf("nested-a","nested-b"),order("layer"))
        val own=config(SortCriterion.NAME,true)
        preferences.configure("p:layer",own)
        preferences.configure("p:project-root",config(SortCriterion.TAG))
        nodes.applyConfiguredOrder("p:layer",own)
        assertEquals(listOf("nested-b","nested-a"),order("layer"))
        nodes.applyConfiguredOrder("p:layer",config(SortCriterion.TAG).copy(inherited=true))
        val rows=db.nodeSortPreferenceDao().all().associate {it.context to (it.mode to it.layersFirst)}
        assertTrue(SortConfigurationCodec.effective(rows,"p:layer").inherited)
        assertEquals(SortCriterion.TAG,SortConfigurationCodec.effective(rows,"p:layer").rules.first().criterion)
        assertEquals(3,db.tagDao().nodeTags().size)
        assertEquals(2,db.nodeDao().getProjectNodes("p").count {it.parentId=="layer"})
    }
    @Test fun backupRoundTripsAllRulesPositionsAndInheritanceAndRejectsCorruptionBeforeReplacing()=runBlocking<Unit> {
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val value=SortConfiguration(false,SortCriterion.entries.reversed().map {SortRule(it,true,true)},true)
        nodes.applyConfiguredOrder("p:project-root",value)
        preferences.configure("p:layer",value.copy(inherited=true))
        val before=backup.snapshot()
        val encoded=BackupJson.encode(before)
        assertEquals(57,org.json.JSONObject(String(encoded)).getInt("dataVersion"))
        val restored=BackupJson.decode(encoded)
        assertEquals(before.nodeSortPreferences,restored.nodeSortPreferences)
        assertEquals(before.nodes,restored.nodes)
        preferences.configure("p:project-root",SortConfiguration())
        backup.restore(restored)
        assertEquals(before.nodeSortPreferences,backup.snapshot().nodeSortPreferences)
        assertEquals(before.nodes,backup.snapshot().nodes)
        val invalid=restored.copy(nodeSortPreferences=restored.nodeSortPreferences.map {it.copy(mode="SORT1:{}")})
        assertTrue(runCatching {backup.restore(invalid)}.isFailure)
        assertEquals(before.nodeSortPreferences,backup.snapshot().nodeSortPreferences)
        val old=restored.copy(nodeSortPreferences=listOf(NodeSortPreferenceEntity("p:project-root","DUE_PRIORITY")))
        val historical=BackupJson.decode(BackupJson.encode(old))
        assertEquals(listOf(SortCriterion.DUE,SortCriterion.PRIORITY),SortConfigurationCodec.decode(historical.nodeSortPreferences.single().mode).rules.filter {it.enabled}.map {it.criterion})
    }
}
