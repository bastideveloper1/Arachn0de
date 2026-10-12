package com.r0ybt.arachn0de.metro

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class MetroModeConflictTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:MetroRepository
    private fun open() {db=Arachn0deDatabase.create(context);repo=MetroRepository(db,context)}
    @Before fun setup() {context.deleteDatabase("arachn0de.db");open()}
    @After fun close() {db.close()}
    private val now=MetroTime(1000000,100000,1)
    private suspend fun journey(id:String)=repo.snapshot().journeys.single {it.row.id==id}
    private suspend fun seed(linked:Boolean=false):String {
        val node=if(linked) {
            val project=ProjectRepository(db.projectDao(),db).createProject("Metro")
            NodeRepository(db).createNode(project.id,null,"Viaje").id
        } else null
        val net=repo.snapshot().preferences.planningNetwork
        return repo.savePlan(MetroPlanner.plan(net,listOf("los-heroes","republica"),false,MetroRestrictions())!!,node)
    }
    @Test fun finalizedJourneyCanToggleModeWithoutLosingIdentityLinkOrSessionsAfterReopen()=runBlocking<Unit> {
        val id=seed(true)
        assertTrue(repo.begin(id,journey(id).row.revision,now))
        assertTrue(runCatching {repo.enabled(id,false)}.isFailure)
        assertTrue(repo.tracking(id,journey(id).row.revision) {MetroTracking.finish(it,now.copy(wall=1120000,elapsed=220000))})
        val before=journey(id)
        repo.enabled(id,false)
        db.close();open()
        val normal=journey(id)
        assertFalse(normal.row.enabled);assertEquals(before.data,normal.data)
        assertEquals(before.row.nodeId,normal.row.nodeId);assertEquals(id,normal.row.id)
        repo.enabled(id,true)
        db.close();open()
        val restored=journey(id)
        assertTrue(restored.row.enabled);assertEquals(before.data,restored.data)
        assertEquals(before.row.nodeId,restored.row.nodeId)
        assertEquals(1,repo.snapshot().journeys.size)
    }
    @Test fun secondTrackingIsRejectedUntilFirstFinishes()=runBlocking<Unit> {
        val first=seed();val second=seed()
        assertTrue(repo.begin(first,journey(first).row.revision,now))
        val before=repo.snapshot()
        assertTrue(runCatching {repo.begin(second,journey(second).row.revision,now)}.isFailure)
        assertEquals(before,repo.snapshot())
        assertTrue(repo.tracking(first,journey(first).row.revision) {MetroTracking.finish(it,now.copy(wall=1120000,elapsed=220000))})
        assertTrue(repo.begin(second,journey(second).row.revision,now))
        assertEquals(1,repo.snapshot().journeys.count {it.data.active!=null})
    }
}
