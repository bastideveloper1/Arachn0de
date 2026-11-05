package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.cancelAndJoin
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w411dp-h1000dp")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class CoreCorrectionsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun awaitAttachments(draft:()->EditorDraft)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
        draft().attachmentsLoaded
    }
    @After fun close() {
        val app=compose.activity.application as? com.r0ybt.arachn0de.Arachn0deApplication ?: return
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()}
        val process=com.r0ybt.arachn0de.Arachn0deApplication::class.java.getDeclaredField("processScope").apply {isAccessible=true}.get(app) as kotlinx.coroutines.CoroutineScope
        kotlinx.coroutines.runBlocking {
            process.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
            app.security.current.value!!.scope.coroutineContext[kotlinx.coroutines.Job]?.cancelAndJoin()
        }
        app.database.close()
    }
    @Test fun tagsWrapAboveFullSizeActionsAtSmallWidthsAndLargeFonts() {
        var width by mutableStateOf(320);var scale by mutableStateOf(1f)
        val tags=(1..4).map {Tag("tag$it","Etiqueta larga número $it","tag$it")}
        compose.setContent {Arachn0deTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                Box(Modifier.requiredWidth(width.dp)) {ProjectCard(Project("p","Título extenso en varias líneas","",0L,0L),ProjectCardAlerts(),{},{},{},tags=tags)}
            }
        }}
        for(w in listOf(320,260)) for(s in listOf(1f,1.8f)) {
            compose.runOnIdle {width=w;scale=s}
            val badge=compose.onNodeWithTag("card-corner-tag",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            val options=compose.onNodeWithContentDescription("Opciones del proyecto").fetchSemanticsNode().boundsInRoot
            assertTrue("Tags must clear actions by at least 8dp",options.top-badge.bottom>=8*compose.density.density-1)
            compose.onNodeWithContentDescription("Opciones del proyecto").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            tags.forEach {compose.onNodeWithText(it.name,useUnmergedTree=true).assertExists()}
            val first=compose.onNodeWithText(tags.first().name,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            val last=compose.onNodeWithText(tags.last().name,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            assertTrue(last.top>first.top)
        }
    }
    @Test fun propertyEditorsExplainLimitsPreserveTextAndAllowCorrectingHistoricalLongValues() {
        var limit by mutableStateOf(60)
        var title by mutableStateOf("😀".repeat(60))
        var saves=0
        compose.setContent {Arachn0deTheme {Box(Modifier.requiredWidth(280.dp)) {
            CorePropertyEditor("title",title,{title=it},true,600.dp,{}, {saves++},limit,multilineTitle=true)
        }}}
        compose.onNodeWithTag("modular-text-field").performTextInput("X")
        assertEquals("😀".repeat(60),title)
        compose.onNodeWithText("No se añadió",substring=true).assertExists()
        compose.onNodeWithText("Confirmar").performClick();assertEquals(1,saves)
        compose.runOnIdle {limit=100;title="Texto\n".repeat(12)}
        compose.onNodeWithText("Confirmar").assertIsEnabled().performClick();assertEquals(2,saves)
        compose.runOnIdle {title="x".repeat(110)}
        compose.onNodeWithText("Reduce el título para guardar",substring=true).assertExists()
        compose.onNodeWithText("Confirmar").assertIsNotEnabled()
        compose.onNodeWithTag("modular-text-field").performTextReplacement("Corregido")
        assertEquals("Corregido",title)
        compose.onNodeWithText("Confirmar").assertIsEnabled().performClick();assertEquals(3,saves)
    }
    @Config(application=com.r0ybt.arachn0de.sharing.SharingTestApplication::class)
    @Test fun nodeCreationAndEditingKeepValidMultilineTitlesAndRejectExcessWithoutLosingDraft() {
        var draft by mutableStateOf(EditorDraft(null,null,"Capa\n".repeat(15),"Conservar descripción",purpose=NodePurpose.LAYER))
        val app=compose.activity.application as com.r0ybt.arachn0de.Arachn0deApplication
        val note=kotlinx.coroutines.runBlocking {
            val core=app.projectRepository.createProject("Editar nota")
            app.nodeRepository.createNode(core.id,null,"Título previo","Conservar descripción",purpose=NodePurpose.NOTE)
        }
        var saved=""
        compose.setContent {Arachn0deTheme {key(draft.creationId) {NodeDialog(draft,false,{}, {title,_->saved=title})}}}
        awaitAttachments {draft}
        compose.onNodeWithText("Crear",substring=false).assertIsEnabled()
        compose.onNode(hasSetTextAction() and hasText(draft.title)).performTextReplacement("z".repeat(101))
        assertEquals("Capa\n".repeat(15),draft.title)
        compose.onNodeWithText("No se añadió",substring=true).assertExists()
        compose.onNodeWithText("Crear",substring=false).performClick()
        assertEquals(draft.title.trim(),saved)
        compose.runOnIdle {draft=EditorDraft(note.id,null,note.title,note.description,purpose=NodePurpose.NOTE)}
        awaitAttachments {draft}
        compose.onNode(hasSetTextAction() and hasText("Título previo")).performTextReplacement("Nota\n".repeat(15))
        compose.onNodeWithText("Guardar",substring=false).assertIsEnabled().performClick()
        assertEquals("Nota\n".repeat(15).trim(),saved)
        assertEquals("Conservar descripción",draft.description)
    }
    @Test fun projectCreationAndEditingAcceptMultilineTitlesAndPreserveExistingContent() = kotlinx.coroutines.runBlocking {
        val db=androidx.room.Room.inMemoryDatabaseBuilder(compose.activity.application,com.r0ybt.arachn0de.data.local.Arachn0deDatabase::class.java).build()
        try {
            val projects=com.r0ybt.arachn0de.data.repository.ProjectRepository(db.projectDao(),db)
            val feedback=TitleInputFeedback()
            var title="Núcleo inicial"
            feedback.change(title,"p".repeat(61),TitleLimits.PROJECT) {title=it}
            assertTrue(feedback.blocked);assertEquals("Núcleo inicial",title)
            feedback.change(title,"Núcleo\n".repeat(7),TitleLimits.PROJECT) {title=it}
            val project=projects.createProject(title,"Conservar descripción")
            assertEquals(title.trim(),project.name)
            feedback.change(title,"Otro título\ncon varias líneas",TitleLimits.PROJECT) {title=it}
            assertTrue(projects.updateProject(project.id,title,project.description))
            val edited=projects.getProject(project.id)!!
            assertEquals(title,edited.name);assertEquals(project.description,edited.description)
        } finally {db.close()}
    }
}
