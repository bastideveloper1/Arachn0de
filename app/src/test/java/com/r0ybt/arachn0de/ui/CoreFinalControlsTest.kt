package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.NodeCopyActions
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class CoreFinalControlsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun completionRemainsOnLeftWithComfortableTargetAndCanBeUndoneAcrossThemes() {
        var completed by mutableStateOf(false)
        val preferences=AppearancePreferences(compose.activity.getSharedPreferences("core-themes",0))
        compose.setContent {Arachn0deTheme(preferences) {CompositionLocalProvider(LocalDensity provides Density(1f,1.5f)) {Box(Modifier.width(280.dp)) {
                val node=Node("task","p",null,"Título largo de la Tarea","",completed,0,0,0,false)
                NodeCard(node,null,false,true,{},{},{},false,false,{_,done->done()},{completed=!completed})
            }}
        }}
        listOf(AppearanceTheme.Arachn0de,AppearanceTheme.NightBlue,AppearanceTheme.DayGrey).forEach {theme->
            compose.runOnIdle {preferences.setTheme(theme)}
            val check=compose.onNodeWithContentDescription("Completar: Título largo de la Tarea")
            val bounds=check.fetchSemanticsNode().boundsInRoot
            val title=compose.onNodeWithText("Título largo de la Tarea",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            assertTrue(title.left>=bounds.right);assertTrue(bounds.width>=48f && bounds.height>=48f)
            check.performClick();assertTrue(completed)
            compose.onNodeWithContentDescription("Marcar pendiente: Título largo de la Tarea").performClick();assertFalse(completed)
        }
    }
    @Test @Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class)
    fun corePriorityIsNamedAndDiscardStillDependsOnDraftChanges() {
        val app=compose.activity.application as com.r0ybt.arachn0de.Arachn0deApplication
        val draft=com.r0ybt.arachn0de.ui.state.EditorDraft(null,null,"","")
        var discarded=false
        compose.setContent {Arachn0deTheme {ProjectDialog(draft,false,{}, {_,_->}, {discarded=true})}}
        compose.waitUntil(15000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            runCatching {compose.onNodeWithText("Común").assertIsEnabled()}.isSuccess
        }
        compose.onNodeWithText("Descartar").assertDoesNotExist()
        compose.onNodeWithText("Común").performScrollTo().performClick()
        assertEquals(Priority.HIGH,draft.priority)
        compose.onNodeWithText("Descartar").performScrollTo().assertExists()
        compose.onNodeWithText("Prioritario").performScrollTo().performClick()
        assertEquals(Priority.NONE,draft.priority)
        compose.onNodeWithText("Descartar").assertDoesNotExist()
        compose.onNodeWithText("Común").performScrollTo().performClick()
        compose.onNodeWithText("Descartar").performScrollTo().performClick()
        compose.onNodeWithText("¿Descartar borrador?").assertExists()
        compose.onNodeWithText("Descartar borrador").performClick();assertTrue(discarded)
        compose.runOnUiThread {
            val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()
        }
        kotlinx.coroutines.runBlocking {app.security.current.value!!.scope.coroutineContext[kotlinx.coroutines.Job]?.cancel();app.database.close()}
    }
    @Test fun navigatorShowsOfficialCoreIconAndKeepsNavigation() {
        var opened=false
        compose.setContent {Arachn0deTheme {LayerNavigator(emptyList(),emptyList(),{},null,{},"Núcleo de prueba",{}, {opened=true})}}
        compose.onNodeWithContentDescription("Núcleo",useUnmergedTree=true).assertIsDisplayed()
        compose.onNodeWithTag("navigator-project").performClick();assertTrue(opened)
    }
    @Test fun descendantCopyStartsPendingAndResetsAfterChoosingAllAndCancelling() {
        lateinit var actions:NodeCopyActions
        val root=Node("root","p",null,"Capa","",false,0,0,0,true,purpose=NodePurpose.LAYER)
        val tree=NodeTreeSnapshot(listOf(root))
        compose.setContent {val scope=rememberCoroutineScope();actions=remember {NodeCopyActions(compose.activity,scope)};Arachn0deTheme {CopyTextChoiceDialog(actions)}}
        fun selected(label:String)=compose.onNode(isSelectable() and hasAnySibling(hasText(label)),useUnmergedTree=true)
        compose.runOnIdle {actions.copy(tree,"root",true)}
        selected("Solo pendientes").assertIsSelected();selected("Todas").assertIsNotSelected()
        selected("Todas").performClick();selected("Todas").assertIsSelected()
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle {actions.copy(tree,"root",true)}
        selected("Solo pendientes").assertIsSelected();selected("Todas").assertIsNotSelected()
    }
}
