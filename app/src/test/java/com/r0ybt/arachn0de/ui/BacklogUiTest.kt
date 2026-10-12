package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.room.withTransaction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.NodePurpose
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class BacklogUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {
        compose.runOnUiThread {
            val content=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until content.childCount) (content.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
        }
        compose.waitForIdle()
        runBlocking {app.database.withTransaction {}}
    }
    private fun pump() {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
    }
    private fun await(text:String)=compose.waitUntil(15000) {pump();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun click(text:String) {
        await(text);val node=compose.onNodeWithText(text)
        if(runCatching {node.assertIsDisplayed()}.isFailure) node.performScrollTo()
        node.performClick()
        if(text in listOf("Seleccionar","Confirmar rol","Confirmar")) compose.waitUntil(15000) {pump();compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()}
    }
    private fun scroll(tag:String,matcher:SemanticsMatcher)=compose.waitUntil(15000) {pump();runCatching {compose.onNodeWithTag(tag).performScrollToNode(matcher)}.isSuccess}
    private fun select(title:String) {
        compose.onNodeWithText("Buscar por título").performTextReplacement(title)
        scroll("backlog-selector",hasContentDescription("Seleccionar existente: $title"));compose.onNodeWithContentDescription("Seleccionar existente: $title").performClick();click("Seleccionar")
    }
    @Test fun originalOpensStoredNodeAndClosesRelationsEvenForCurrentStory() {
        val fixture=runBlocking {
            val p=app.projectRepository.createProject("Backlog project")
            val repo=app.nodeRepository.management
            val context=repo.configure(p.id,null,true,ManagementMethodology.AGILE)!!.id
            val backlog=app.database.managementLayerDao().all().first {it.kind=="BACKLOG"}.nodeId
            val epic=repo.cascade.create(context,backlog,"Primary epic",LayerRole.EPIC)
            val story=repo.cascade.create(context,epic,"Searchable story",LayerRole.STORY)
            val origin=app.nodeRepository.createNode(p.id,null,"Original layer",purpose=NodePurpose.LAYER)
            val task=app.nodeRepository.createNode(p.id,origin.id,"Searchable task",description="Original task content")
            repo.backlog.linkTask(context,story,task.id)
            Triple(story,task,epic)
        }
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        fun layer(text:String) {scroll("nodes-list",hasText(text));click(text)}
        click("Backlog project");layer("Backlog");layer("Primary epic");layer("Searchable story")
        click("Gestionar vínculos adicionales");click("Abrir original")
        compose.waitUntil(15000) {pump();compose.onAllNodesWithText("Cerrar Backlog").fetchSemanticsNodes().isEmpty()}
        await("Gestionar vínculos adicionales")
        click("Gestionar vínculos adicionales");scroll("backlog-list",hasText("Tarea · Searchable task"));click("Tarea · Searchable task")
        click("Abrir original");await("Original task content")
        compose.onNodeWithText("Cerrar Backlog").assertDoesNotExist()
        runBlocking {
            assertEquals(fixture.second.parentId,app.nodeRepository.getNode(fixture.second.id)!!.parentId)
            assertEquals(fixture.third,app.nodeRepository.getNode(fixture.first)!!.parentId)
            assertEquals(1,app.database.backlogDao().storyTasks().size)
        }
    }
    @Test fun principalEpicAndAdditionalLinksRemainDistinctAndMissingOriginalIsExplained() {
        val data=runBlocking {
            val p=app.projectRepository.createProject("Hierarchy context")
            val repo=app.nodeRepository.management
            val context=repo.configure(p.id,null,true,ManagementMethodology.AGILE)!!.id
            val backlog=app.database.managementLayerDao().all().first {it.kind=="BACKLOG"}.nodeId
            val principal=repo.cascade.create(context,backlog,"Principal epic",LayerRole.EPIC)
            val extra=repo.cascade.create(context,backlog,"Additional epic",LayerRole.EPIC)
            val story=repo.cascade.create(context,principal,"Nested story",LayerRole.STORY)
            repo.backlog.linkEpic(context,extra,story)
            Triple(context,story,principal)
        }
        var selected by androidx.compose.runtime.mutableStateOf<String?>(data.second)
        var opened:String?=null
        compose.setContent {Arachn0deTheme {androidx.compose.runtime.key(selected) {
            BacklogDialog(data.first,app.nodeRepository.management.backlog,{opened=it},false,selected) {}
        }}}
        await("Épica principal");compose.onNodeWithText("Principal epic").assertExists()
        scroll("backlog-list",hasText("Épica · Additional epic"))
        compose.onNodeWithText("Épica · Additional epic").assertHasClickAction()
        runBlocking {
            assertEquals(data.third,app.nodeRepository.getNode(data.second)!!.parentId)
            assertEquals(2,app.database.backlogDao().epicStories().size)
        }
        compose.runOnIdle {selected="missing-original"}
        await("El elemento original ya no está disponible.")
        compose.onNodeWithText("Abrir original").assertDoesNotExist();assertNull(opened)
    }
    @Test fun orderUnlinkConfirmationAndRoleRemovalWorkWithLargeFontsAndLongTitles() {
        val title="Historia extensa ".repeat(15).trim()
        val data=runBlocking {
            val p=app.projectRepository.createProject("Large backlog")
            val id=app.nodeRepository.management.configure(p.id,null,true,ManagementMethodology.AGILE)!!.id
            val story=app.nodeRepository.createNode(p.id,null,"Long story",purpose=NodePurpose.LAYER)
            // Legacy persisted titles may exceed the current editor limit.
            app.database.nodeDao().updateContent(story.id,title,"",1)
            val task=app.nodeRepository.createNode(p.id,null,"Existing task")
            app.nodeRepository.management.backlog.setRole(id,story.id,LayerRole.STORY)
            app.nodeRepository.management.backlog.linkTask(id,story.id,task.id)
            Triple(id,story.id,task.id)
        }
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.8f)) {
            BacklogDialog(data.first,app.nodeRepository.management.backlog,{}, {})
        }}}
        scroll("backlog-list",hasContentDescription("Opciones Backlog: Existing task"))
        compose.onNodeWithContentDescription("Opciones Backlog: Existing task").performClick()
        compose.onNodeWithContentDescription("Subir Backlog: Existing task").performClick()
        compose.waitUntil(15000) {runBlocking {app.database.backlogDao().entries().firstOrNull()?.nodeId==data.third}}
        scroll("backlog-list",hasContentDescription("Opciones Backlog: Existing task"))
        compose.onNodeWithContentDescription("Opciones Backlog: Existing task").performClick()
        compose.onNodeWithContentDescription("Relaciones: Existing task").performClick()
        scroll("backlog-list",hasContentDescription("Opciones vínculo: $title"))
        compose.onNodeWithContentDescription("Opciones vínculo: $title").performClick()
        compose.onNodeWithContentDescription("Desvincular: $title").performScrollTo().performClick();click("Cancelar")
        runBlocking {assertEquals(1,app.database.backlogDao().storyTasks().size)}
        compose.onNodeWithContentDescription("Opciones vínculo: $title").performClick()
        compose.onNodeWithContentDescription("Desvincular: $title").performClick();click("Confirmar")
        runBlocking {assertTrue(app.database.backlogDao().storyTasks().isEmpty());assertEquals(2,app.database.backlogDao().entries().size)}
        click("Volver al Backlog");scroll("backlog-list",hasText("Relaciones y roles"));click("Relaciones y roles")
        scroll("backlog-list",hasContentDescription("Opciones de rol: $title"))
        compose.onNodeWithContentDescription("Opciones de rol: $title").performClick()
        compose.onNodeWithContentDescription("Cambiar rol: $title").performScrollTo().performClick()
        click("Capa normal");click("Cancelar");runBlocking {assertEquals(1,app.database.backlogDao().roles().size)}
        compose.onNodeWithContentDescription("Opciones de rol: $title").performClick()
        compose.onNodeWithContentDescription("Cambiar rol: $title").performClick();click("Capa normal");click("Confirmar rol")
        compose.waitUntil(15000) {runBlocking {app.database.backlogDao().roles().isEmpty()}}
        runBlocking {assertEquals(listOf(data.third),app.database.backlogDao().entries().map {it.nodeId});assertNotNull(app.database.nodeDao().getById(data.second))}
        compose.onNodeWithText("Cerrar Backlog").assertIsDisplayed()
    }
}
