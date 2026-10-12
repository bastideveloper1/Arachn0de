package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.gantt.GanttDemo
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class GanttUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun dispose() {
        compose.runOnUiThread {val views=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until views.childCount) (views.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()};compose.waitForIdle()
    }
    private fun await(text:String) {compose.waitUntil(20000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}}
    private fun exercise(font:Float) {
        val d=runBlocking {GanttDemo.create(app.database,app.personRepository)}
        var dismissed=false;var opened:String?=null
        compose.setContent {Arachn0deTheme {val current=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(current.density,font)) {ProjectGanttDialog(d.context,app.nodeRepository.management,{opened=it},"DEMO",{dismissed=true})}}}
        await("PNG");compose.onNodeWithText("PNG").assertIsDisplayed()
        compose.onNodeWithText("Opciones").performClick()
        compose.onNodeWithText("Sprint:",substring=true).performScrollTo().performClick()
        compose.onNode(hasText("Sprint 2") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Opciones").performClick()
        compose.onNodeWithText("Anotar").performClick();await("Anotación")
        compose.onNodeWithText("Texto").performTextReplacement("✅ Persistente")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(20000) {runBlocking {app.database.ganttAnnotationDao().all().any {it.text=="✅ Persistente"}}}
        val story=runBlocking {app.database.backupDao().nodes().single {it.title=="HU4 — Registrar"}}
        compose.onNodeWithTag("gantt-item:${story.id}").performScrollTo().performClick();assertEquals(story.id,opened)
        compose.onNodeWithText("Volver").performClick();assertTrue(dismissed)
    }
    @Test fun controlsAndAnnotationReachable()=exercise(1f)
    @Test fun largeFontsKeepActionsReachable()=exercise(1.6f)
    @Test fun projectEntryOpensTimelineAndReturnsFromRealStory() {
        val d=runBlocking {GanttDemo.create(app.database,app.personRepository)}
        val project=runBlocking {app.projectRepository.getProject(d.project)!!}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        await(project.name);compose.onNodeWithText(project.name).performClick()
        compose.waitUntil(15000) {runCatching {compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Gantt"))}.isSuccess}
        compose.onNodeWithText("Gantt").performClick();await("PNG")
        compose.onNodeWithTag("gantt-timeline").assertExists()
        compose.onNodeWithText("Opciones").performClick()
        compose.onNodeWithText("Sprint:",substring=true).performScrollTo().performClick()
        compose.onNode(hasText("Sprint 2") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithText("Opciones").performClick()
        val story=runBlocking {app.database.backupDao().nodes().single {it.title=="HU4 — Registrar"}}
        compose.onNodeWithTag("gantt-item:${story.id}").performScrollTo().performClick()
        await("Criterio: conservar la identidad del registro.")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await("PNG");compose.onNodeWithTag("gantt-timeline").assertExists()
        compose.onNodeWithText("Volver").performClick()
        compose.onNodeWithText("PNG").assertDoesNotExist()
    }
    @Test fun calendarCreatesMovesReadsAndDeletesSamePersistedAnnotation() {
        val first=java.time.LocalDate.of(2026,10,10)
        val fixture=runBlocking {
            val project=app.projectRepository.createProject("Notes")
            val context=app.nodeRepository.management.configure(project.id,null,true,ManagementMethodology.AGILE)!!.id
            val sprint=app.nodeRepository.createNode(project.id,null,"Sprint note test",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
            app.nodeRepository.setSprintMode(sprint.id,true)
            val story=app.nodeRepository.createNode(project.id,null,"Historia original",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
            app.nodeRepository.management.backlog.setRole(context,story.id,LayerRole.STORY)
            fun millis(day:java.time.LocalDate)=day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            app.nodeRepository.management.iterations.configure(context,sprint.id,startAt=millis(first),endAt=millis(first.plusDays(5)))
            app.nodeRepository.management.relations.addStory(sprint.id,story.id)
            context to story.id
        }
        compose.setContent {Arachn0deTheme {ProjectGanttDialog(fixture.first,app.nodeRepository.management,{},"Notes") {}}}
        await("Sprint note test")
        val density=compose.activity.resources.displayMetrics.density
        compose.onNodeWithTag("gantt-calendar").performTouchInput {click(androidx.compose.ui.geometry.Offset(9*density,33*density))}
        await("Anotación");compose.onNodeWithText("Texto").performTextReplacement("Nota táctil completa")
        compose.onNodeWithText("Guardar").performClick()
        compose.waitUntil(15000) {runBlocking {app.database.ganttAnnotationDao().all().size==1}}
        val original=runBlocking {app.database.ganttAnnotationDao().all().single()}
        assertEquals(first.toEpochDay(),original.epochDay)
        compose.onNodeWithTag("gantt-calendar").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(9*density,33*density))
            moveTo(androidx.compose.ui.geometry.Offset(27*density,40*density),300);up()
        }
        compose.waitUntil(15000) {runBlocking {app.database.ganttAnnotationDao().all().single().epochDay==first.plusDays(1).toEpochDay()}}
        val moved=runBlocking {app.database.ganttAnnotationDao().all().single()}
        assertEquals(original.id,moved.id);assertEquals(original.text,moved.text);assertNotEquals(original.lane,moved.lane)
        compose.onNodeWithTag("gantt-note:${moved.id}").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        await("Anotación");compose.onNodeWithText("Texto").assertTextContains(original.text)
        compose.onNodeWithText("Eliminar").performClick()
        compose.waitUntil(15000) {runBlocking {app.database.ganttAnnotationDao().all().isEmpty()}}
        assertNotNull(runBlocking {app.nodeRepository.getNode(fixture.second)})
    }

    @Test fun demoActionRequiresConfirmationAndAllowsAnotherInstance() {
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        await("Opciones");compose.onNodeWithText("Opciones").performClick()
        compose.onNodeWithText("Crear proyecto de demostración").performClick();await("Cancelar")
        assertTrue(runBlocking {app.database.projectDao().getAll().isEmpty()})
        compose.onNodeWithText("Cancelar").performClick()
        repeat(2) {
            compose.onNodeWithText("Opciones").performClick()
            compose.onNodeWithText("Crear proyecto de demostración").performClick()
            compose.onNodeWithText("Crear demostración").performClick()
            compose.waitUntil(20000) {runBlocking {app.database.projectDao().getAll().size==it+1}}
            compose.waitUntil(20000) {compose.onAllNodesWithText("Crear demostración").fetchSemanticsNodes().isEmpty()}
        }
        assertEquals(12,runBlocking {app.database.backupDao().persons().size})
    }

    @Test fun demoTraversesActaBacklogSprintKanbanAndGantt() {
        val d=runBlocking {GanttDemo.create(app.database,app.personRepository)}
        val project=runBlocking {app.projectRepository.getProject(d.project)!!}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        fun open(title:String) {
            compose.waitUntil(15000) {runCatching {compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(title))}.isSuccess}
            compose.onNodeWithText(title).performClick()
        }
        fun back() {compose.onNodeWithContentDescription("Volver a la capa anterior").performClick();compose.waitForIdle()}
        fun scroll(title:String,tag:String="nodes-list") {
            compose.waitUntil(15000) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                compose.mainClock.advanceTimeByFrame()
                runCatching {compose.onNodeWithTag(tag).performScrollToNode(hasText(title))}.isSuccess
            }
        }
        await(project.name);compose.onNodeWithText(project.name).performClick()
        open("Acta de constitución");open("Objetivos")
        scroll("Administrar catálogo")
        compose.onNodeWithText("Administrar catálogo").assertExists()
        back();open("Equipo")
        scroll("DEMO Ana")
        compose.onNodeWithText("DEMO Ana").assertExists()
        back();back();open("Backlog");open("Libros");open("HU1 — Registrar")
        await("Criterio: conservar la identidad del registro.")
        back();back();back();open("Sprints");open("Sprint 2")
        await("Planificar");compose.onNodeWithText("Planificar").assertExists()
        back();back();open("Kanban")
        await("Buscar tareas u origen")
        scroll("Sprint seleccionado: Sprint 2","kanban-list")
        compose.onNodeWithText("Sprint seleccionado: Sprint 2").assertExists()
        back();open("Gantt");await("PNG")
        compose.onNodeWithTag("gantt-timeline").assertExists()
    }

}
