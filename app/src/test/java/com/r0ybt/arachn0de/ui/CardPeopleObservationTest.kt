package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.local.RecurrenceRuleEntity
import com.r0ybt.arachn0de.data.repository.RecurrenceRepository
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class CardPeopleObservationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun pump() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
    }
    private fun indicator(key:String,active:Boolean) {
        val expected=SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,if(active)"Configurado" else "Sin datos")
        compose.waitUntil(15000) {pump();compose.onAllNodes(hasTestTag("card-signal:$key:people") and expected,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
    }
    private fun title(id:String,label:String) {
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(label))
        compose.onNodeWithTag("node-title:$id",useUnmergedTree=true).performClick()
    }
    @After fun close() {
        compose.runOnUiThread {
            val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount)(root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()
        }
        pump()
        val db=app.database
        runBlocking {
            app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin()
            withTimeout(15000) {db.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}
        }
        db.close()
    }
    @Test fun overdueMonthlyTaskAndAncestorsObservePeopleWithoutOpeningOtherScreens() {
        val application=app
        val fixture=runBlocking {
            val project=application.projectRepository.createProject("Servicios")
            val layer=application.nodeRepository.createNode(project.id,null,"Telefonía",purpose=NodePurpose.LAYER)
            application.personRepository.save("person","Persona",null)
            val day=LocalDate.now().minusDays(3).toEpochDay()
            val recurrence=RecurrenceRepository(application.database)
            recurrence.create(RecurrenceRuleEntity("monthly",project.id,layer.id,"Plan Virgin","",10000,"CLP",day,"MONTHLY",1,day,0,"ACTIVE","UTC",0,null),setOf("person"))
            recurrence.materializeDue()
            val occurrence=application.database.recurrenceDao().occurrences().single {it.ruleId=="monthly"}
            Triple(project.id,layer.id,occurrence.nodeId)
        }
        val (project,layer,task)=fixture
        val before=runBlocking {application.database.nodeDao().getById(task)!!}
        assertTrue(before.dueAt!!<System.currentTimeMillis())
        assertEquals(setOf("person"),runBlocking {application.personRepository.observeAssignments(project).first().getValue(task).map {it.id}.toSet()})
        compose.setContent {Arachn0deTheme {AppSafeArea {AppRoot(application.projectRepository,application.nodeRepository)}}}
        // Cold entry: no Attention, Calendar or Payments navigation has occurred.
        indicator("project:$project",true)
        compose.onNodeWithTag("project-card:$project").performClick()
        indicator("node:$layer",true)
        title(layer,"Telefonía");indicator("node:$task",true)
        // Removing a task assignment updates its preview without modifying the recurrence template.
        runBlocking {application.personRepository.setResponsiblePeople(task,emptySet())}
        indicator("node:$task",false)
        assertEquals(setOf("person"),runBlocking {RecurrenceRepository(application.database).people("monthly")})
        // A direct layer assignment must not appear as an assignment of the child task.
        runBlocking {application.personRepository.setResponsiblePeople(layer,setOf("person"))}
        indicator("node:$task",false)
        assertEquals(before,runBlocking {application.database.nodeDao().getById(task)!!})
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        indicator("node:$layer",true)
        runBlocking {application.personRepository.setResponsiblePeople(layer,emptySet())}
        indicator("node:$layer",false)
    }
}
