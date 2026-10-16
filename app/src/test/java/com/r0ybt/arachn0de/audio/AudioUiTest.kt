package com.r0ybt.arachn0de.audio

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.*
import com.r0ybt.arachn0de.ui.state.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w320dp-h891dp")
class AudioUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {pump();block()}
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        pump();app.security.current.value!!.audio.dispose()
        runBlocking {app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}}
        app.database.close()
    }
    @Test fun quickCreateKeepsSixAccessibleIconsWith48DpTargetsAndDisablesMicForRecurrence() {
        val draft=EditorDraft(null,null,"","",purpose=NodePurpose.ACTION)
        compose.setContent {Arachn0deTheme {QuickCreateBar(draft,false,{}, {}, {},onAudio={})}}
        val names=listOf("Nota","Vence","Repetir","Grabar audio","Formulario completo","Cancelar creación")
        val bounds=names.map {compose.onNodeWithContentDescription(it).assertExists().fetchSemanticsNode().boundsInRoot}
        bounds.forEach {assertTrue(it.width>=48*compose.density.density-1);assertTrue(it.height>=48*compose.density.density-1)}
        bounds.zipWithNext().forEach {(a,b)->assertTrue(a.right<=b.left+1)}
        assertTrue(bounds.last().right<=compose.onNodeWithTag("quick-create").fetchSemanticsNode().boundsInRoot.right)
        compose.onNodeWithText("Nota").assertDoesNotExist();compose.onNodeWithText("Vence").assertDoesNotExist();compose.onNodeWithText("Repetir").assertDoesNotExist()
        compose.runOnUiThread {draft.recurrenceFrequency="DAILY"}
        compose.onNodeWithContentDescription("Grabar audio").assertIsNotEnabled()
    }
    @Test fun editorOnlyToolsScrollWhileExitAndSaveStayVisibleWithLargeFontAndKeepSelection() {
        val draft=EditorDraft("task",null,"Título","abc").apply {attachmentsLoaded=true;descriptionSelectionStart=0;descriptionSelectionEnd=3}
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,1.3f)) {Arachn0deTheme {Column(Modifier.fillMaxWidth()) {
            AttachmentDescriptionTools(draft,true,compact=true,trailingActions={IconButton(onClick={},modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Close,"Cancelar")};Button(onClick={},modifier=Modifier.heightIn(min=48.dp)) {Text("Guardar")}})
        }}}}
        compose.onNodeWithContentDescription("Imagen").assertExists();compose.onNodeWithText("Imagen").assertDoesNotExist()
        val initialSave=compose.onNodeWithText("Guardar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val initialCancel=compose.onNodeWithContentDescription("Cancelar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("B").performScrollTo().performClick()
        compose.onNodeWithText("I").performScrollTo().performClick()
        compose.onNodeWithText("U").performScrollTo().performClick()
        val parsed=DescriptionParser.parse(draft.description).single {it.text=="abc"}
        assertTrue(parsed.formats.containsAll(DescriptionParser.Format.entries))
        assertTrue(draft.descriptionSelectionEnd>draft.descriptionSelectionStart)
        compose.onNodeWithContentDescription("Grabar audio").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Guardar").assertIsDisplayed()
        val save=compose.onNodeWithText("Guardar").fetchSemanticsNode().boundsInRoot
        val cancel=compose.onNodeWithContentDescription("Cancelar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(initialSave,save);assertEquals(initialCancel,cancel)
        assertTrue(save.right<=compose.onNodeWithTag("description-toolbar").fetchSemanticsNode().boundsInRoot.right)
        assertTrue(cancel.width>=48);assertTrue(cancel.height>=48);assertTrue(save.height>=48)
        assertEquals(save.center.y,cancel.center.y,1f)
    }
    @Config(sdk=[33])
    @Test fun realEditorKeepsExitAndSaveAboveKeyboardOnNarrowScreenWithLargeFont() {
        val draft=EditorDraft("task",null,"Título","abc").apply {attachmentsLoaded=true}
        var saved=false;var cancelled=false
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,1.5f)) {Arachn0deTheme {
            NodeDialog(draft,false,{cancelled=true},{_,_->saved=true},initialField="description",hasUnsavedChanges=false)
        }}}
        await {runCatching {compose.onNodeWithText("Guardar").assertIsEnabled()}.isSuccess}
        val window=org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        compose.runOnIdle {
            val insets=androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),androidx.core.graphics.Insets.of(0,24,0,24))
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(),androidx.core.graphics.Insets.of(0,0,0,160))
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(),true).build()
            fun composeView(view:android.view.View):android.view.View? {
                if(view.javaClass.simpleName=="AndroidComposeView") return view
                if(view is android.view.ViewGroup) for(i in 0 until view.childCount) composeView(view.getChildAt(i))?.let {return it}
                return null
            }
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(checkNotNull(composeView(window)),insets)
        }
        listOf(NodePurpose.ACTION,NodePurpose.LAYER,NodePurpose.NOTE).forEach {purpose->
            compose.runOnIdle {draft.purpose=purpose}
            val toolbar=compose.onNodeWithTag("description-toolbar").fetchSemanticsNode().boundsInRoot
            val save=compose.onNodeWithText("Guardar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val cancel=compose.onNodeWithContentDescription("Cancelar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            compose.onNodeWithText("Cancelar").assertDoesNotExist()
            assertTrue(save.right<=toolbar.right);assertTrue(cancel.right<=save.left)
            assertTrue(cancel.width>=48);assertTrue(cancel.height>=48);assertTrue(save.height>=48)
            val editor=compose.onNodeWithTag("node-editor").fetchSemanticsNode().boundsInRoot
            assertTrue(save.bottom<=editor.bottom-160)
            compose.onNodeWithContentDescription("Imagen").performScrollTo().assertIsDisplayed()
            assertEquals(save,compose.onNodeWithText("Guardar").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithText("Guardar").performClick();assertTrue(saved);saved=false
        }
        compose.onNodeWithContentDescription("Cancelar").performClick();assertTrue(cancelled)
    }
    @Test fun compactRecordingBarPersistsAcrossNavigationAndContentRecreationWithoutDuplicateBars() {
        runBlocking {app.projectRepository.createProject("Núcleo de audio")}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        await {compose.onAllNodesWithText("Núcleo de audio").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnUiThread {app.security.current.value!!.audio.state.value=AudioState(recording=true,durationMs=2345)}
        compose.onNodeWithTag("audio-bar").assertExists()
        compose.onNodeWithText("Núcleo de audio").performClick()
        await {compose.onAllNodesWithTag("project-bottom-actions").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("audio-bar").assertExists()
        assertEquals(1,compose.onAllNodesWithTag("audio-bar").fetchSemanticsNodes().size)
        val audio=compose.onNodeWithTag("audio-bar").fetchSemanticsNode().boundsInRoot
        val footer=compose.onNodeWithTag("project-bottom-actions").fetchSemanticsNode().boundsInRoot
        assertTrue(audio.bottom<=footer.top+1)
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await {compose.onAllNodesWithText("Núcleo de audio").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(2345L,app.security.current.value!!.audio.state.value.durationMs)
        compose.onNodeWithTag("audio-bar").assertExists()
    }
    private fun openQuickAudio(title:String?) {
        runBlocking {app.projectRepository.createProject("Contexto rápido")}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        await {compose.onAllNodesWithText("Contexto rápido").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Contexto rápido").performClick()
        await {app.security.current.value!!.audio.canStartRecording && compose.onAllNodesWithText("Crear").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Crear").performClick()
        await {compose.onAllNodesWithTag("quick-create").fetchSemanticsNodes().isNotEmpty()}
        if(title!=null) compose.onNodeWithTag("quick-create-title").performTextInput(title)
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.RECORD_AUDIO)
        compose.onNodeWithContentDescription("Grabar audio").performClick()
        await {runBlocking {app.database.backupDao().nodes().any {it.purpose=="ACTION"}}}
    }
    @Test fun quickMicWithoutTitlePersistsOneTaskInCurrentContextAndRejectsSecondActiveRequest() {
        openQuickAudio(null)
        val first=runBlocking {app.database.backupDao().nodes().single()}
        assertTrue(first.title.startsWith("Audio — "));assertNull(first.parentId)
        val project=runBlocking {app.database.projectDao().getAll().single()}
        assertEquals(project.id,first.projectId)
        await {compose.onAllNodesWithTag("project-bottom-actions").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Crear").performClick()
        await {compose.onAllNodesWithTag("quick-create").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Grabar audio").performClick()
        await {app.security.current.value!!.audio.state.value.error!=null}
        assertEquals(listOf(first),runBlocking {app.database.backupDao().nodes()})
    }
    @Test fun quickMicKeepsWrittenTitleAndOriginalCreationAction() {
        openQuickAudio("Título escrito por mí")
        assertEquals("Título escrito por mí",runBlocking {app.database.backupDao().nodes().single().title})
        compose.onNodeWithTag("audio-bar").assertExists()
    }
    @Test fun fullDescriptionEditorHostsOnlyOneBarAboveItsTools() {
        val draft=EditorDraft("task",null,"Tarea","Descripción").apply {attachmentsLoaded=true}
        compose.runOnUiThread {app.security.current.value!!.audio.state.value=AudioState(recording=true,durationMs=1000)}
        compose.setContent {Arachn0deTheme {Column {
            AudioBar()
            NodeDialog(draft,false,{}, {_,_->},initialField="description",hasUnsavedChanges=false)
        }}}
        await {compose.onAllNodesWithTag("audio-bar").fetchSemanticsNodes().size==1}
        compose.onNodeWithTag("node-editor").assertExists()
        val bar=compose.onNodeWithTag("audio-bar").fetchSemanticsNode().boundsInRoot
        val tools=compose.onNodeWithContentDescription("Negrita").fetchSemanticsNode().boundsInRoot
        assertTrue(bar.bottom<=tools.top+1)
    }

}
