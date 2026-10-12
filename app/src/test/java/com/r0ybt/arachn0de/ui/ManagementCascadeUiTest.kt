package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.room.withTransaction
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class ManagementCascadeUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private var fontScale=1f
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {
        compose.runOnUiThread {val content=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until content.childCount) (content.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()}
        compose.waitForIdle();runBlocking {app.database.withTransaction {}}
        // The application owns the database; its termination closes the authenticated session.
        // Closing it here races collectors disposed by the activity rule.
    }
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(text:String) {try {compose.waitUntil(20000) {pump();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}} catch(e:Exception) {println(compose.onAllNodes(isRoot(),useUnmergedTree=true).fetchSemanticsNodes().joinToString {it.config.toString()});throw e}}
    private fun click(text:String) {
        await(text)
        val dialog=hasText(text) and hasAnyAncestor(isDialog())
        val node=if(compose.onAllNodes(dialog).fetchSemanticsNodes().isNotEmpty()) compose.onNode(dialog) else compose.onNodeWithText(text)
        if(runCatching {node.assertIsDisplayed()}.isFailure) node.performScrollTo()
        node.performClick()
    }
    private fun openLayer(text:String) {
        compose.waitUntil(20000) {pump();runCatching {compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(text))}.isSuccess}
        click(text)
    }
    @Test fun realHierarchyKanbanCreatesSharedNoteAndBackRestoresBoard() {
        val fixture=runBlocking {
            val project=app.projectRepository.createProject("Cascade project")
            val repo=app.nodeRepository.management
            val context=repo.configure(project.id,null,true,ManagementMethodology.AGILE)!!.id
            val layers=app.database.managementLayerDao().all().associateBy {it.kind}
            val epic=repo.cascade.create(context,layers.getValue("BACKLOG").nodeId,"Access epic",LayerRole.EPIC)
            val story=repo.cascade.create(context,epic,"Login story",LayerRole.STORY)
            val sprint=repo.cascade.create(context,layers.getValue("SPRINTS").nodeId,"Sprint one",sprint=true)
            repo.relations.addStory(sprint,story);repo.iterations.start(app.database.iterationDao().plans().single().id)
            Triple(project.id,story,layers.getValue("KANBAN").nodeId)
        }
        compose.setContent {Arachn0deTheme {val density=androidx.compose.ui.platform.LocalDensity.current;androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,fontScale)) {AppSafeArea {AppRoot(app.projectRepository,app.nodeRepository)}}}}
        click("Cascade project");openLayer("Kanban");await("Login story");click("Login story")
        await("Nuevo elemento");click("Nuevo elemento");await("Título")
        compose.onNodeWithText("Nota").performClick()
        compose.onNodeWithText("Título").performTextReplacement("Shared note")
        click("Crear")
        compose.waitUntil(20000) {pump();runBlocking {app.nodeRepository.getProjectNodes(fixture.first).any {it.parentId==fixture.second && it.title=="Shared note"}}}
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await("Buscar tareas u origen")
        compose.onAllNodesWithText("Kanban").fetchSemanticsNodes().let {assertTrue(it.isNotEmpty())}
        // Back restores the board; closing returns directly to the origin, without an intermediate layer.
        click("Cerrar")
        openLayer("Backlog");openLayer("Access epic");openLayer("Login story");openLayer("Shared note")
        runBlocking {
            val notes=app.nodeRepository.getProjectNodes(fixture.first).filter {it.title=="Shared note"}
            assertEquals(1,notes.size);assertEquals(fixture.second,notes.single().parentId);assertEquals(NodePurpose.NOTE,notes.single().purpose)
            assertEquals(1,app.database.cascadeRelationDao().stories().size)
        }
    }
    @Test fun largeFontsPreserveCanonicalNavigationAndSharedNote() {fontScale=1.6f;realHierarchyKanbanCreatesSharedNoteAndBackRestoresBoard()}

    private fun creationStaysInList(kind:String) {
        val fixture=runBlocking {
            val project=app.projectRepository.createProject("Creation UX")
            val repo=app.nodeRepository.management
            val context=repo.configure(project.id,null,true,ManagementMethodology.AGILE)!!.id
            val layers=app.database.managementLayerDao().all().associateBy {it.kind}
            val parent=if(kind=="STORY") repo.cascade.create(context,layers.getValue("BACKLOG").nodeId,"Epic parent",LayerRole.EPIC) else layers.getValue(kind).nodeId
            Triple(project.id,context,parent)
        }
        compose.setContent {Arachn0deTheme {AppSafeArea {AppRoot(app.projectRepository,app.nodeRepository)}}}
        click("Creation UX")
        when(kind) {
            "OBJECTIVES"->{openLayer("Acta de constitución");openLayer("Objetivos")}
            "STORY"->{openLayer("Backlog");openLayer("Epic parent")}
            "SPRINTS"->openLayer("Sprints")
            else->openLayer("Backlog")
        }
        val label=when(kind) {"OBJECTIVES"->"Nuevo objetivo";"STORY"->"Nueva historia";"SPRINTS"->"Nuevo Sprint";else->"Nueva épica"}
        compose.onNodeWithText("Nuevo elemento").assertIsDisplayed()
        compose.runOnIdle {
            val primary=compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            val traditional=compose.onNodeWithText("Nuevo elemento").fetchSemanticsNode().boundsInRoot
            assertEquals(primary.center.y,traditional.center.y,1f)
        }
        click(label)
        await("Título")
        compose.onNodeWithText("Título").performTextReplacement("Created here")
        click("Crear")
        compose.waitUntil(20000) {pump();runBlocking {app.nodeRepository.getProjectNodes(fixture.first).any {it.title=="Created here"}}}
        await("Created here")
        compose.onNodeWithText(label).assertIsDisplayed()
        if(kind=="BACKLOG" || kind=="STORY" || kind=="SPRINTS") {
            click(label);await("Título")
            compose.onNodeWithText("Título").performTextReplacement("Created next")
            click("Crear")
            compose.waitUntil(20000) {pump();runBlocking {app.nodeRepository.getProjectNodes(fixture.first).any {it.title=="Created next"}}}
            compose.onNodeWithText(label).assertIsDisplayed()
            assertEquals(2,runBlocking {app.nodeRepository.getProjectNodes(fixture.first).count {it.parentId==fixture.third && it.title.startsWith("Created ")}})
        }
        val saved=runBlocking {app.nodeRepository.getProjectNodes(fixture.first).single {it.title=="Created here"}}
        assertEquals(fixture.third,saved.parentId)
        openLayer("Created here")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await(label);openLayer("Created here")
        assertEquals(saved.id,runBlocking {app.nodeRepository.getProjectNodes(fixture.first).single {it.title=="Created here"}.id})
    }

    @Test fun objectiveCreationStaysInList() {creationStaysInList("OBJECTIVES")}
    @Test fun epicCreationStaysInList() {creationStaysInList("BACKLOG")}
    @Test fun storyCreationStaysInList() {creationStaysInList("STORY")}
    @Test fun sprintCreationStaysInList() {creationStaysInList("SPRINTS")}

    @Test fun fiveToolCardsExposeFullSurfaceNavigation() {
        val kinds=listOf("ACTA","BACKLOG","SPRINTS","KANBAN","GANTT")
        var opened:String?=null
        compose.setContent {Arachn0deTheme {androidx.compose.foundation.layout.Column {
            kinds.forEach {kind->ManagementNavigationCard(requireNotNull(managementToolVisual(kind))) {opened=kind}}
        }}}
        kinds.forEach {kind->
            val tool=requireNotNull(managementToolVisual(kind))
            compose.onNodeWithText(tool.title).assertHasClickAction().performTouchInput {click(androidx.compose.ui.geometry.Offset(width-4f,height/2f))}
            compose.runOnIdle {assertEquals(kind,opened)}
        }
    }


    @Test fun teamIntegratesParticipantsAndNestedBackReturnsToItsOrigin() {
        val context=runBlocking {
            val p=app.projectRepository.createProject("Team navigation")
            val id=app.nodeRepository.management.configure(p.id,null,true,ManagementMethodology.AGILE)!!.id
            repeat(4) {app.database.personDao().insert(com.r0ybt.arachn0de.data.local.PersonEntity("team$it","Member $it",null))}
            repeat(3) {app.nodeRepository.management.participation.add(id,"team$it")}
            id
        }
        compose.setContent {Arachn0deTheme {AppSafeArea {AppRoot(app.projectRepository,app.nodeRepository)}}}
        click("Team navigation");openLayer("Acta de constitución");openLayer("Equipo")
        await("Participantes")
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithText("Esta capa está vacía").assertDoesNotExist()
        compose.waitUntil(15000) {pump();compose.onAllNodesWithTag("person-photo:team2",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val photos=(0..2).map {compose.onNodeWithTag("person-photo:team$it",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot}
        assertEquals(photos[0].top,photos[1].top,1f);assertEquals(photos[1].top,photos[2].top,1f)
        photos.forEach {assertEquals(it.width,it.height,1f)}
        click("Incorporar Personas");click("Member 3")
        compose.waitUntil(15000) {pump();runBlocking {app.database.participationDao().participants().size==4}}
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await("Participantes");compose.onAllNodes(isDialog()).assertCountEquals(0)
        click("Member 0");await("Roles generales del proyecto")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await("Equipo");compose.onNode(hasText("Member 0") and hasAnyAncestor(isDialog())).assertExists()
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onAllNodes(isDialog()).assertCountEquals(0);await("Participantes")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await("Añadir sección")
        runBlocking {assertEquals(4,app.database.participationDao().participants().count {it.contextId==context});assertEquals(4,app.database.backupDao().persons().size)}
    }

}
