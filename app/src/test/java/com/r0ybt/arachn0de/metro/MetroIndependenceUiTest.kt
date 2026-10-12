package com.r0ybt.arachn0de.metro

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
class MetroIndependenceUiTest {
    @Before fun resetNavigation() {MetroNavigation.requests.value=null}
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {
        MetroNavigation.requests.value=null
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
    @Test fun exactJourneyOpensDuplicatesAndDeletionRequiresConfirmation() {
        val repository=app.metroRepository
        val id=runBlocking {
            val route=MetroPlanner.plan(repository.snapshot().preferences.network,listOf("los-heroes","republica"),false,MetroRestrictions())!!
            repository.savePlan(route)
        }
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),journeyId=id,onBack={})}}
        awaitText("Comenzar viaje");compose.onNodeWithTag("metro-actions:$id").performClick();compose.onNodeWithText("Duplicar").performClick()
        compose.waitUntil(15000) {runBlocking {repository.snapshot().journeys.size==2}}
        val copy=runBlocking {repository.snapshot().journeys.single {it.row.id!=id}.row.id}
        compose.onNodeWithTag("metro-actions:$copy").performClick()
        compose.onNodeWithText("Eliminar viaje").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(2,runBlocking {repository.snapshot().journeys.size})
        compose.onNodeWithTag("metro-actions:$copy").performClick();compose.onNodeWithText("Eliminar viaje").performClick();compose.onNodeWithText("Confirmar").performClick()
        compose.waitUntil(15000) {runBlocking {repository.snapshot().journeys.size==1}}
        assertEquals(id,runBlocking {repository.snapshot().journeys.single().row.id})
        assertNull(runBlocking {app.database.metroDao().journey(copy)})
    }
    @Test fun taskSelectorLinksExistingActiveJourneyAndUnlinksOnlyAfterConfirmation() {
        val repository=app.metroRepository;val nodes=app.nodeRepository;val projects=app.projectRepository
        val (task,id)=runBlocking {
            val p=projects.createProject("P");val t=nodes.createNode(p.id,null,"T")
            val route=MetroPlanner.plan(repository.snapshot().preferences.network,listOf("los-heroes","republica"),false,MetroRestrictions())!!
            val trip=repository.savePlan(route);assertTrue(repository.begin(trip,repository.snapshot().journeys.single().row.revision,metroTime(app)));t to trip
        }
        val payload=runBlocking {app.database.metroDao().journey(id)!!.payload}
        compose.setContent {Arachn0deTheme {TaskMetroAccess(task.id,repository)}}
        awaitText("Vincular viaje")
        compose.waitUntil(15000) {runCatching {compose.onNodeWithText("Vincular viaje").assertIsEnabled()}.isSuccess}
        compose.onNodeWithText("Vincular viaje").performClick();awaitText("Buscar viaje")
        compose.onNodeWithText("Buscar viaje").performTextInput("República")
        compose.onNodeWithTag("link-trip:$id").performClick();awaitText("Vínculo Metro")
        assertEquals(payload,runBlocking {app.database.metroDao().journey(id)!!.payload})
        compose.onNodeWithText("Vínculo Metro").performClick();compose.onNodeWithText("Abrir viaje").performClick()
        assertEquals(id,MetroNavigation.requests.value!!.journeyId)
        compose.onNodeWithText("Vínculo Metro").performClick();compose.onNodeWithText("Desvincular").performClick()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(task.id,runBlocking {app.database.metroDao().journey(id)!!.nodeId})
        compose.onNodeWithText("Desvincular").performClick()
        compose.onAllNodesWithText("Desvincular").onLast().performClick();awaitText("Vincular viaje")
        val row=runBlocking {app.database.metroDao().journey(id)!!};assertNull(row.nodeId);assertEquals(payload,row.payload)
        assertNotNull(runBlocking {app.database.nodeDao().getById(task.id)})
    }
    @Test fun activeTripMenuCannotDeleteAndTrainFullscreenPreservesSession() {
        val repository=app.metroRepository
        val id=runBlocking {
            val route=MetroPlanner.plan(repository.snapshot().preferences.network,listOf("los-heroes","tobalaba"),false,MetroRestrictions())!!
            val trip=repository.savePlan(route);repository.begin(trip,repository.snapshot().journeys.single().row.revision,metroTime(app));trip
        }
        val session=runBlocking {repository.snapshot().journeys.single().data.active!!.id}
        compose.setContent {Arachn0deTheme {MetroScreen(emptyList(),emptyList(),journeyId=id,onBack={})}}
        awaitText("Pausar");compose.onNodeWithTag("metro-train").assertIsDisplayed()
        compose.onNodeWithTag("metro-expand").performClick();compose.onNodeWithTag("metro-train").assertIsDisplayed()
        compose.onNodeWithTag("metro-expand").performClick();awaitText("Posición estimada")
        assertEquals(session,runBlocking {repository.snapshot().journeys.single().data.active!!.id})
        compose.onNodeWithText("Viajes").performClick();compose.onNodeWithTag("metro-actions:$id").performClick()
        compose.onNodeWithText("Editar viaje").assertExists();compose.onNodeWithText("Duplicar").assertExists()
        compose.onNodeWithText("Eliminar viaje").assertDoesNotExist()
    }

}
