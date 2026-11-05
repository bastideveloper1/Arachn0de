package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.r0ybt.arachn0de.access.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=NavigationWorkApplication::class,sdk=[28],qualifiers="w360dp-h1000dp")
class CardFavoritePlacementTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as NavigationWorkApplication
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame();block()
    }
    @After fun close() {
        compose.runOnUiThread {
            val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount)(root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()
        }
        stopAppearanceTestApplication(app)
    }

    @Test fun previewOptionsAndInteriorContentShareExistingFavoriteRecord() {
        val project=runBlocking {app.projectRepository.createProject("Núcleo favorito")}
        val nodes=runBlocking {listOf(NodePurpose.LAYER,NodePurpose.ACTION,NodePurpose.NOTE).map {
            app.nodeRepository.createNode(project.id,null,"Elemento ${it.name}","Contenido conservado",purpose=it)
        }}
        val before=runBlocking {app.database.backupDao().nodes()}
        val access=AccessRepository(app.database)
        var owner by mutableIntStateOf(0)
        var inside by mutableStateOf(false)
        compose.setContent {Arachn0deTheme {
            val state by access.observe().collectAsState(AccessState())
            CompositionLocalProvider(LocalElementAccess provides access,LocalElementAccessState provides state) {
                if(inside) ProjectNodeScreen(project,app.nodeRepository,{inside=false},
                    openNodeId=nodes.getOrNull(owner-1)?.id,entryAction="content")
                else if(owner==0) ProjectCard(project,ProjectCardAlerts(),{inside=true},{},{},onContent={inside=true})
                else NodeCard(nodes[owner-1],null,false,owner==2,{inside=true},{},{},false,false,{_,done->done()},{},onContent={inside=true})
            }
        }}
        for(index in 0..3) {
            compose.runOnIdle {inside=false;owner=index}
            val key=if(index==0)"project:${project.id}" else "node:${nodes[index-1].id}"
            compose.onNodeWithTag("favorite:$key").assertDoesNotExist()
            compose.onNodeWithTag("card-signal:$key:favorite",useUnmergedTree=true).assertDoesNotExist()
            if(index==0) compose.onNodeWithContentDescription("Opciones del proyecto").performClick()
            else compose.onNodeWithTag("node-options:${nodes[index-1].id}").performClick()
            compose.onNodeWithTag("context-action:Contenido").assertDoesNotExist()
            compose.onNodeWithTag("context-action:Marcar como favorito").performScrollTo().performClick()
            await {runBlocking {key in access.snapshot().favorites}}
            await {compose.onAllNodesWithTag("context-action:Quitar de favoritos").fetchSemanticsNodes().isNotEmpty()}
            compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
            compose.onNodeWithTag("card-signal:$key:favorite",useUnmergedTree=true).assertIsDisplayed()
            // Contenido is reached through the card itself, not its exterior menu.
            if(index==0) compose.onNodeWithTag("project-card:${project.id}").performClick()
            else compose.onNode(hasText(nodes[index-1].title) and hasClickAction()).performClick()
            await {runCatching {compose.onNodeWithTag("core-context:Content").assertIsSelected()}.isSuccess}
            compose.onNodeWithTag("context-action:Quitar de Favoritos").performScrollTo().performClick()
            await {runBlocking {key !in access.snapshot().favorites}}
        }
        assertEquals(before,runBlocking {app.database.backupDao().nodes()})
        assertEquals(project,runBlocking {app.projectRepository.getProject(project.id)})
        assertTrue(runBlocking {access.snapshot().favorites.isEmpty()})
    }

    @Test fun exteriorUrgencyUsesExistingPropertyAndKeepsOtherActions() {
        val project=runBlocking {app.projectRepository.createProject("Urgencia")}
        val nodes=runBlocking {listOf(NodePurpose.LAYER,NodePurpose.ACTION,NodePurpose.NOTE).map {
            app.nodeRepository.createNode(project.id,null,"Elemento ${it.name}",purpose=it)
        }}
        val access=AccessRepository(app.database)
        var owner by mutableIntStateOf(0)
        var selections=0
        compose.setContent {Arachn0deTheme {
            val state by access.observe().collectAsState(AccessState())
            val live by app.nodeRepository.observeRootNodes(project.id).collectAsState(nodes)
            val projects by app.projectRepository.observeProjects().collectAsState(listOf(project))
            CompositionLocalProvider(LocalElementAccess provides access,LocalElementAccessState provides state) {
                key(owner) {
                    if(owner==0) ProjectCard(projects.single(),ProjectCardAlerts(),{},{},{},onContent={})
                    else {
                        val node=live.first {it.id==nodes[owner-1].id}
                        NodeCard(node,null,false,owner==2,{},{},{},false,false,{_,done->done()},{},
                            onContent={},onSelect={selections++},onHistory={})
                    }
                }
            }
        }}
        fun otherActions():List<String> = compose.onAllNodes(SemanticsMatcher("acciones del menú") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("context-action:")==true
        }).fetchSemanticsNodes().map {it.config[SemanticsProperties.TestTag]}.filterNot {
            it in setOf("context-action:Marcar como favorito","context-action:Quitar de favoritos",
                "context-action:Marcar como urgente","context-action:Quitar urgencia")
        }
        fun action(label:String)=compose.onNodeWithTag("context-action:$label")
        for(index in 0..3) {
            compose.runOnIdle {owner=index}
            val key=if(index==0)"project:${project.id}" else "node:${nodes[index-1].id}"
            if(index==0) compose.onNodeWithContentDescription("Opciones del proyecto").performClick()
            else compose.onNodeWithTag("node-options:${nodes[index-1].id}").performClick()
            action("Contenido").assertDoesNotExist()
            if(index>0) action("Seleccionar").assertExists()
            val unchanged=otherActions()
            if(index==0 || index==2) {
                await {runCatching {action("Marcar como urgente").assertIsEnabled()}.isSuccess}
                action("Marcar como urgente").performScrollTo().performClick()
                await {runBlocking {
                    if(index==0) app.database.conversionDao().project(project.id)?.priority==Priority.HIGH.name
                    else app.nodeRepository.getNode(nodes[index-1].id)?.priority==Priority.HIGH
                }}
                await {compose.onAllNodesWithTag("context-action:Quitar urgencia").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("card-signal:$key:high",useUnmergedTree=true).assert(hasStateDescription("Configurado"))
                assertEquals(unchanged,otherActions())
                action("Quitar urgencia").performScrollTo().performClick()
                await {runBlocking {
                    if(index==0) app.database.conversionDao().project(project.id)?.priority==Priority.NONE.name
                    else app.nodeRepository.getNode(nodes[index-1].id)?.priority==Priority.NONE
                }}
                await {compose.onAllNodesWithTag("context-action:Marcar como urgente").fetchSemanticsNodes().isNotEmpty()}
                compose.onNodeWithTag("card-signal:$key:high",useUnmergedTree=true).assert(hasStateDescription("Sin datos"))
            } else {
                action("Marcar como urgente").assertDoesNotExist()
                action("Quitar urgencia").assertDoesNotExist()
            }
            action("Marcar como favorito").performScrollTo().performClick()
            await {runBlocking {key in access.snapshot().favorites}}
            await {compose.onAllNodesWithTag("context-action:Quitar de favoritos").fetchSemanticsNodes().isNotEmpty()}
            action("Quitar de favoritos").performScrollTo().performClick()
            await {runBlocking {key !in access.snapshot().favorites}}
            await {compose.onAllNodesWithTag("context-action:Marcar como favorito").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(unchanged,otherActions())
            if(index>0) action("Seleccionar").performScrollTo().performClick()
            else compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        }
        assertEquals(3,selections)
    }
}
