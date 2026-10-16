package com.r0ybt.arachn0de.sharing

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.access.*
import com.r0ybt.arachn0de.ui.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(application=SharingTestApplication::class,sdk=[28])
class SharedCaptureUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();block()}
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        val manager=app.security
        val closing=CoroutineScope(Dispatchers.Default).async {manager.lock()}
        val until=System.nanoTime()+20_000_000_000L
        while(!closing.isCompleted) {
            check(System.nanoTime()<until) {"La limpieza no terminó"}
            shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10)
        }
        runBlocking {closing.await()}
    }
    @Test fun existingCaptureEditsSharedContentAndProcessesNextOnlyAfterCancelOrSave() {
        val session=app.security.current.value!!;val queue=session.sharedCaptures
        val p=runBlocking {app.projectRepository.createProject("Destino")}
        val index=ElementSearch.index(listOf(p),emptyList());val repo=AccessRepository(app.database)
        val a=UUID.randomUUID().toString();val b=UUID.randomUUID().toString()
        runBlocking {queue.receive(a,SharedContent("Primer título","https://example.com",null));queue.receive(b,SharedContent("Segundo título","Segundo texto",null))}
        val restoration=androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {Arachn0deTheme {CompositionLocalProvider(LocalCaptureGate provides remember {CaptureGate()}) {SharedCaptureHost(index,repo,false)}}}
        await {compose.onAllNodesWithText("Primer título").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Segundo título").assertDoesNotExist()
        compose.onNodeWithText("Primer título").performTextReplacement("Título editado")
        restoration.emulateSavedInstanceStateRestore()
        await {compose.onAllNodesWithText("Título editado").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("https://example.com",substring=true).assertExists()
        compose.onNodeWithText("Crear tarea").performClick()
        await {compose.onAllNodesWithText("Segundo título").fetchSemanticsNodes().isNotEmpty()}
        assertEquals("Título editado",runBlocking {app.database.nodeDao().getById(a)!!.title})
        compose.onNodeWithText("Cancelar").performClick()
        await {runBlocking {queue.observe().first().isEmpty()}}
        assertNull(runBlocking {app.database.nodeDao().getById(b)})
    }
    @Test fun temporaryScreenProtectionSurvivesGrantAndReturnsToCurrentPermissionAfterClose()=runBlocking<Unit> {
        val protection=app.security.captures
        compose.runOnIdle {protection.attach(compose.activity.window)}
        compose.runOnIdle {runBlocking {protection.grant(protection.epoch,stillPrimarySession={true})}}
        val first=compose.runOnIdle {protection.holdPrivateView()}
        val second=compose.runOnIdle {protection.holdPrivateView()}
        compose.runOnIdle {runBlocking {protection.grant(protection.epoch,stillPrimarySession={true})}}
        fun secure()=compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE !=0
        assertTrue(secure());compose.runOnIdle {first.close()};assertTrue(secure());compose.runOnIdle {second.close()};assertFalse(secure())
        val hold=compose.runOnIdle {protection.holdPrivateView()};compose.runOnIdle {protection.revoke();hold.close()};assertTrue(secure())
    }
    @Test fun sharedLinkOffersEditableNoteAndKeepsDestinationSelection() {
        val queue=app.security.current.value!!.sharedCaptures
        val p=runBlocking {app.projectRepository.createProject("Destino enlace")}
        val index=ElementSearch.index(listOf(p),emptyList())
        val request=UUID.randomUUID().toString()
        runBlocking {queue.receive(request,SharedContent.read(android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT,"https://example.com/original")))}
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalCaptureGate provides remember {CaptureGate()}) {
            SharedCaptureHost(index,AccessRepository(app.database),false)
        }}}
        await {compose.onAllNodesWithText("Crear nota").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Crear tarea").assertDoesNotExist()
        compose.onNodeWithText("Enlace compartido").performTextReplacement("Título revisado")
        compose.onNodeWithText("https://example.com/original").performTextReplacement("https://example.com/revisado")
        compose.onNodeWithText("Crear nota").performClick()
        await {runBlocking {app.database.nodeDao().getById(request)!=null}}
        val node=runBlocking {app.database.nodeDao().getById(request)!!}
        assertEquals("NOTE",node.purpose);assertEquals("Título revisado",node.title)
        assertEquals("https://example.com/revisado",node.description);assertEquals(p.id,node.projectId)
    }

}
