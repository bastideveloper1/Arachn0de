package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
class SprintManagedConversionTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    private lateinit var sprint:String
    private lateinit var tasks:List<String>
    @Before fun setup()=runBlocking {
        context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context);nodes=NodeRepository(db)
        for(p in listOf("p","q","r")) db.projectDao().insert(ProjectEntity(p,p,"",0,1,2))
        nodes.management.configure("p",null,true,ManagementMethodology.AGILE)
        sprint=nodes.createNode("p",null,"Sprint",purpose=NodePurpose.LAYER).id;nodes.setSprintMode(sprint,true)
        tasks=WorkState.entries.map {phase->val t=nodes.createNode("p",null,phase.label);nodes.sprintLinks.link(sprint,setOf(t.id));nodes.setWorkState(t.id,phase);t.id}
        val direct=nodes.createNode("p",sprint,"Direct");nodes.setWorkState(direct.id,WorkState.VALIDATED);tasks=tasks+direct.id
    }
    @After fun close() {db.close()}
    private suspend fun snapshot()=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory)).snapshot().copy(createdAt=0)
    @Test fun roundTripPreservesAllFivePhasesCompletionIdsLinksAndNestedContext()=runBlocking {
        nodes.management.configure("q",null,true,ManagementMethodology.HYBRID)
        val phases=tasks.associateWith {db.nodeDao().getById(it)!!.workState};val links=db.sprintLinkDao().all();val contexts=db.managementContextDao().all()
        val nesting=ProjectNestingRepository(db);val layer=nesting.move("p","q",null)
        assertEquals(links,db.sprintLinkDao().all());tasks.forEach {assertEquals(phases[it],db.nodeDao().getById(it)!!.workState);assertFalse(db.nodeDao().getById(it)!!.isCompleted)}
        assertEquals("p",nesting.promote(layer));assertEquals(contexts,db.managementContextDao().all());assertEquals(links,db.sprintLinkDao().all());tasks.forEach {assertEquals(phases[it],db.nodeDao().getById(it)!!.workState)}
    }
    @Test fun partialAndTraditionalConversionsAreRejectedBeforeChangingData()=runBlocking {
        val before=snapshot();assertTrue(runCatching {ProjectNestingRepository(db).promote(sprint)}.isFailure);assertEquals(before,snapshot())
        nodes.management.configure("p",null,false,ManagementMethodology.AGILE)
        // A disabled persistent managed context still preserves its identity.
        val layer=ProjectNestingRepository(db).move("p","q",null);ProjectNestingRepository(db).promote(layer)
        val n=nodes.createNode("r",null,"Traditional",purpose=NodePurpose.LAYER);nodes.setSprintMode(n.id,true)
        val task=nodes.createNode("r",null,"Traditional task");nodes.sprintLinks.link(n.id,setOf(task.id))
        val original=snapshot();assertTrue(runCatching {ProjectNestingRepository(db).move("r","q",null)}.isFailure);assertEquals(original,snapshot())
    }
    @Test fun physicalIdentityCollisionsAreRejectedWithoutFallbackIdentity()=runBlocking {
        nodes.createNode("q",null,"Collision",creationId="p")
        val before=snapshot();assertTrue(runCatching {ProjectNestingRepository(db).move("p","q",null)}.isFailure);assertEquals(before,snapshot())
    }
    @Test fun promotionCollisionAndFailureBeforeCommitRollbackLinksAndPhases()=runBlocking {
        val layer=ProjectNestingRepository(db).move("p","q",null)
        db.projectDao().insert(ProjectEntity("p","Collision","",2,1,2))
        val before=snapshot();assertTrue(runCatching {ProjectNestingRepository(db).promote(layer)}.isFailure);assertEquals(before,snapshot())
        db.projectDao().deleteProjectRow("p")
        val saved=snapshot();assertTrue(runCatching {ProjectNestingRepository(db) {error("Fixture rollback")}.promote(layer)}.isFailure);assertEquals(saved,snapshot())
    }
    @Test fun outerConversionPreservesAnIndependentNestedManagedSprintContext()=runBlocking {
        val nesting=ProjectNestingRepository(db);nesting.move("p","q",null)
        nodes.management.configure("q",null,true,ManagementMethodology.TRADITIONAL)
        val beforeLinks=db.sprintLinkDao().all();val contexts=db.managementContextDao().all()
        val outer=nesting.move("q","r",null);nesting.promote(outer)
        assertEquals(beforeLinks,db.sprintLinkDao().all());assertEquals(contexts,db.managementContextDao().all())
    }
}
