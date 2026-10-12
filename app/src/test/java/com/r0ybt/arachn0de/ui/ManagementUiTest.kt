package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class ManagementUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {
        compose.runOnUiThread {
            val content=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until content.childCount) (content.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            // A still-attached ComposeView can recreate its composition during measurement.
            content.removeAllViews()
        }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
        val application=app;val db=application.database
        // Let in-flight Room reads/transactions finish before closing the Robolectric pool.
        runBlocking {
            application.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin()
            withTimeout(15000) {db.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}
        }
        db.close()
    }
    private fun awaitText(text:String)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    @Test fun normalProjectCanActivateConfigureDisableAndReactivateWithoutLosingContext() {
        runBlocking {app.projectRepository.createProject("Managed candidate")}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        awaitText("Managed candidate");compose.onNodeWithText("Managed candidate").performClick()
        awaitText("Gestión del proyecto · Normal")
        compose.onNodeWithText("Gestión del proyecto · Normal").performScrollTo().performClick()
        compose.onNodeWithText("Híbrida").performClick();compose.onNodeWithText("Activar gestión").performClick()
        awaitText("Gestión del proyecto · Gestionado")
        val initial=runBlocking {app.database.managementContextDao().all().single()}
        assertTrue(initial.enabled);assertEquals("HYBRID",initial.methodology)
        compose.onNodeWithText("Gestión del proyecto · Gestionado").performClick()
        compose.onNodeWithText("Configuración").performClick()
        compose.onNodeWithText("Tradicional").performClick();compose.onNodeWithText("Guardar configuración").performClick()
        compose.waitUntil(15000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText("Guardar configuración").fetchSemanticsNodes().isEmpty()
        }
        awaitText("Gestión del proyecto · Gestionado")
        compose.onNodeWithText("Gestión del proyecto · Gestionado").performClick()
        compose.onNodeWithText("Configuración").performClick()
        compose.onNodeWithText("Desactivar gestión").performClick();awaitText("Gestión del proyecto · Normal")
        compose.onNodeWithText("Gestión del proyecto · Normal").performClick()
        compose.onNodeWithText("✓ Tradicional").assertExists();compose.onNodeWithText("Cancelar").performClick()
        runBlocking {assertFalse(app.database.managementContextDao().all().single().enabled)}
        compose.onNodeWithText("Gestión del proyecto · Normal").performClick();compose.onNodeWithText("Activar gestión").performClick()
        awaitText("Gestión del proyecto · Gestionado")
        runBlocking {
            val actual=app.database.managementContextDao().all().single()
            assertEquals(initial.id,actual.id);assertEquals("TRADITIONAL",actual.methodology);assertTrue(actual.enabled)
            assertTrue(app.database.backupDao().nodes().isEmpty())
        }
    }
    @Test fun contextualHubReturnsFromEveryToolWithoutShowingConfigurationByDefault() {
        val project=runBlocking {app.projectRepository.createProject("Navigation candidate")}
        runBlocking {app.nodeRepository.management.configure(project.id,null,true,com.r0ybt.arachn0de.data.repository.ManagementMethodology.AGILE)}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        awaitText("Navigation candidate");compose.onNodeWithText("Navigation candidate").performClick()
        awaitText("Gestión del proyecto · Gestionado")
        compose.onNodeWithText("Gestión del proyecto · Gestionado").performScrollTo().performClick()
        compose.onNodeWithText("Guardar configuración").assertDoesNotExist()
        compose.onNodeWithText("Asociaciones · 0").assertDoesNotExist()
        for((tool,close) in listOf("Objetivos" to "Cerrar planificación","Backlog" to "Cerrar Backlog","Historias" to "Cerrar Backlog","Sprint" to "Cerrar Iteraciones","Kanban" to "Cerrar","Equipo" to "Cerrar")) {
            compose.onNodeWithText(tool).performClick()
            awaitText(close)
            if(tool=="Backlog") compose.onNodeWithText("Solo pendientes").performClick()
            compose.onNodeWithText(close).performClick()
            awaitText("Gestión del proyecto")
            compose.onNodeWithText("Configuración").assertExists()
        }
        compose.onNodeWithText("Backlog").performClick()
        awaitText("✓ Solo pendientes")
        compose.onNodeWithText("Cerrar Backlog").performClick()
        compose.onNodeWithText("Cerrar").performClick()
        awaitText("Navigation candidate")
    }

    @Test fun optionalAssociationReviewIsConsultableAndDoesNotChangeTheStory() {
        val project=runBlocking {app.projectRepository.createProject("Review candidate")}
        val context=runBlocking {requireNotNull(app.nodeRepository.management.configure(project.id,null,true,com.r0ybt.arachn0de.data.repository.ManagementMethodology.AGILE))}
        val db=app.database
        runBlocking {
            db.nodeDao().insert(com.r0ybt.arachn0de.data.local.NodeEntity("review-story",project.id,null,"Optional story","Original description",false,0,0,0,purpose="LAYER"))
            app.nodeRepository.management.backlog.setRole(context.id,"review-story",com.r0ybt.arachn0de.data.repository.LayerRole.STORY)
        }
        val before=runBlocking {db.nodeDao().getById("review-story")}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        awaitText("Review candidate");compose.onNodeWithText("Review candidate").performClick()
        awaitText("Gestión del proyecto · Gestionado")
        compose.onNodeWithText("Gestión del proyecto · Gestionado").performScrollTo().performClick()
        awaitText("Asociaciones · 1");compose.onNodeWithText("Asociaciones · 1").performClick()
        compose.onNodeWithText("Sin objetivo · Sin Sprint").assertExists()
        compose.onNodeWithText("Historias").performClick()
        awaitText("Historias y épicas")
        compose.onNode(hasText("Optional story") and hasAnyAncestor(hasTestTag("backlog-list"))).assertExists()
        assertEquals(before,runBlocking {db.nodeDao().getById("review-story")})
    }

}
