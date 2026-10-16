package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.*
import com.r0ybt.arachn0de.backup.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class HierarchyPinTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var projects:ProjectRepository
    private lateinit var nodes:NodeRepository
    @Before fun open() {context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context);repositories()}
    private fun repositories() {projects=ProjectRepository(db.projectDao(),db);nodes=NodeRepository(db)}
    @After fun close() {db.close()}
    @Test fun rootsPinStablyWithoutWritingOrderAndPersistAfterReopen()=runBlocking<Unit> {
        val rows=listOf("A","B","C","D").map {projects.createProject(it,creationId=it)}
        val before=db.projectDao().getAll()
        projects.setPinned("C",true);projects.setPinned("A",true)
        assertEquals(listOf("A","C","B","D"),projects.observeProjects().first().map {it.id})
        assertEquals(before,db.projectDao().getAll().map {it.copy(isPinned=false)})
        assertFalse(projects.reorderProjectTo("A","B"))
        assertTrue(projects.reorderProjectTo("C","A"))
        assertEquals(listOf("C","A","B","D"),projects.observeProjects().first().map {it.id})
        assertEquals(before.filter {it.id in listOf("B","D")},db.projectDao().getAll().filter {it.id in listOf("B","D")})
        db.close();db=Arachn0deDatabase.create(context);repositories()
        assertTrue(db.projectDao().getById("C")!!.isPinned)
        projects.setPinned("C",false);projects.setPinned("A",false)
        assertEquals(listOf("C","B","A","D"),projects.observeProjects().first().map {it.id})
        assertEquals(rows.size,db.projectDao().getAll().size)
    }
    @Test fun layersPinPerContainerAndTasksAndTheirAttributesRemainUntouched()=runBlocking<Unit> {
        val project=projects.createProject("Núcleo")
        val a=nodes.createNode(project.id,null,"A",purpose=NodePurpose.LAYER)
        val task=nodes.createNode(project.id,null,"Tarea",priority=Priority.HIGH)
        val b=nodes.createNode(project.id,null,"B",purpose=NodePurpose.LAYER)
        val c=nodes.createNode(project.id,null,"C",purpose=NodePurpose.LAYER)
        val child=nodes.createNode(project.id,b.id,"Hija",purpose=NodePurpose.LAYER)
        val before=db.backupDao().nodes()
        assertTrue(nodes.setPinned(c.id,true));assertTrue(nodes.setPinned(a.id,true));assertFalse(nodes.setPinned(task.id,true))
        val current=nodes.getProjectNodes(project.id)
        val root=current.filter {it.parentId==null}
        assertEquals(listOf(a.id,c.id,task.id,b.id),NodePresentationSort.children(root,NodeSortMode.MANUAL).map {it.id})
        assertEquals(before,db.backupDao().nodes().map {it.copy(isPinned=false)})
        db.close();db=Arachn0deDatabase.create(context);repositories()
        assertTrue(db.nodeDao().getById(c.id)!!.isPinned)
        assertFalse(db.nodeDao().getById(child.id)!!.isPinned)
        assertFalse(nodes.reorderNodeTo(c.id,null,b.id,layersOnly=true))
        assertTrue(nodes.reorderNodeTo(c.id,null,a.id,layersOnly=true))
        assertEquals(before.first {it.id==task.id},db.nodeDao().getById(task.id))
        val filtered=listOf(FilteredNodeRow(current.first {it.id==b.id},0,true),FilteredNodeRow(current.first {it.id==c.id},0,true))
        assertEquals(listOf(c.id,b.id),NodePresentationSort.filtered(filtered,NodeSortMode.MANUAL).map {it.node.id})
        assertTrue(nodes.moveNode(c.id,b.id));assertTrue(db.nodeDao().getById(c.id)!!.isPinned)
        assertTrue(nodes.setPinned(c.id,false));assertTrue(nodes.setPinned(a.id,false))
        assertFalse(nodes.setPinned("missing",true))
    }
    @Test fun navigatorPinsOnlySiblingsAndRetainsHierarchy()=runBlocking<Unit> {
        val p=projects.createProject("Núcleo")
        val a=nodes.createNode(p.id,null,"A",purpose=NodePurpose.LAYER)
        val b=nodes.createNode(p.id,null,"B",purpose=NodePurpose.LAYER)
        val child=nodes.createNode(p.id,a.id,"Hija",purpose=NodePurpose.LAYER)
        nodes.setPinned(b.id,true);nodes.setPinned(child.id,true)
        val rows=LayerHierarchyIndex(nodes.getProjectNodes(p.id)).visibleRows(setOf(a.id))
        assertEquals(listOf(b.id,a.id,child.id),rows.map {it.node.id})
        assertEquals(listOf(0,0,1),rows.map {it.depth})
    }
    @Test fun rootLayerConversionsRetainPinAndLeafTaskConversionClearsIt()=runBlocking<Unit> {
        val source=projects.createProject("Origen");val target=projects.createProject("Destino")
        val child=nodes.createNode(source.id,null,"Descendiente")
        projects.setPinned(source.id,true)
        val layer=projects.moveInside(source.id,target.id,null)
        assertTrue(db.nodeDao().getById(layer)!!.isPinned);assertFalse(db.nodeDao().getById(child.id)!!.isPinned)
        val promoted=projects.convertLayerToProject(layer)
        assertTrue(db.projectDao().getById(promoted)!!.isPinned)
        assertFalse(db.nodeDao().getById(child.id)!!.isPinned)
        val leaf=nodes.createNode(target.id,null,"Hoja",purpose=NodePurpose.LAYER)
        nodes.setPinned(leaf.id,true);assertTrue(nodes.convertPurpose(leaf.id,NodePurpose.ACTION))
        assertFalse(db.nodeDao().getById(leaf.id)!!.isPinned);assertFalse(nodes.setPinned(leaf.id,true))
    }
    @Test fun realBackupRestoresPinsLegacyDefaultsAndRejectsTaskPinsWithoutReplacingData()=runBlocking<Unit> {
        val p=projects.createProject("Principal");val layer=nodes.createNode(p.id,null,"Capa",purpose=NodePurpose.LAYER)
        val task=nodes.createNode(p.id,null,"Tarea")
        projects.setPinned(p.id,true);nodes.setPinned(layer.id,true)
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val before=backup.snapshot()
        assertEquals(33,JSONObject(String(BackupJson.encode(before))).getInt("dataVersion"))
        val inspected=backup.inspect(backup.create().readBytes().inputStream())
        projects.setPinned(p.id,false);nodes.setPinned(layer.id,false);backup.restore(inspected);backup.discard(inspected)
        assertTrue(db.projectDao().getById(p.id)!!.isPinned);assertTrue(db.nodeDao().getById(layer.id)!!.isPinned)
        val bad=before.copy(nodes=before.nodes.map {if(it.id==task.id) it.copy(isPinned=true) else it})
        assertTrue(runCatching {backup.restore(bad)}.isFailure)
        assertEquals(before.projects,db.backupDao().projects());assertEquals(before.nodes,db.backupDao().nodes())
        val historical=JSONObject(String(BackupJson.encode(before))).apply {
            put("dataVersion",32)
            for(name in listOf("projects","nodes")) {val rows=getJSONArray(name);for(i in 0 until rows.length()) rows.getJSONObject(i).remove("isPinned")}
        }
        backup.restore(BackupJson.decode(historical.toString().toByteArray()))
        assertTrue(db.backupDao().projects().none {it.isPinned});assertTrue(db.backupDao().nodes().none {it.isPinned})
        assertEquals(before.nodes.map {it.copy(isPinned=false)},db.backupDao().nodes())
    }
}
