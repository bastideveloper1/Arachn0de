package com.r0ybt.arachn0de.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
@Config(application=NavigationWorkApplication::class,sdk=[28],qualifiers="w320dp-h480dp")
class FormatToolsPlacementTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as NavigationWorkApplication
    @After fun close() {
        compose.runOnUiThread {
            val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount)(root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()
        }
        stopAppearanceTestApplication(app)
    }
    private fun awaitReady(draft:EditorDraft)=compose.waitUntil(15000) {
        shadowOf(Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        draft.attachmentsLoaded
    }
    private fun draft()=EditorDraft("node",null,"Existing note",(1..120).joinToString("\n") {"Long description $it"},purpose=NodePurpose.NOTE)
        .apply {descriptionSelectionStart=0;descriptionSelectionEnd=4}
    @Test fun propertyToolsStayVisibleWithReducedHeight() {
        val draft=draft()
        var height by mutableStateOf(340.dp)
        var width by mutableStateOf(320.dp)
        var scale by mutableStateOf(1f)
        compose.setContent {Arachn0deTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                Box(Modifier.width(width).height(height)) {
                    NodePropertyEditor(draft,NodeProperty.DESCRIPTION,true,height,emptyList(),TagState(),app.nodeRepository.tags,emptyList(),{}, {})
                }
            }
        }}
        awaitReady(draft)
        val source=draft.description
        for(available in listOf(340.dp,220.dp)) for(font in listOf(1f,1.5f)) {
            compose.runOnIdle {height=available;width=280.dp;scale=font}
            val panel=compose.onNodeWithTag("node-property-editor:DESCRIPTION").fetchSemanticsNode().boundsInRoot
            val tools=compose.onNodeWithTag("description-tools").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(tools.top>=panel.top);assertTrue(tools.bottom<=panel.bottom+.5f)
            for(label in listOf("Negrita","Cursiva","Subrayado","Título","Código","Insertar imagen")) {
                compose.onNodeWithContentDescription(label).performScrollTo().assertIsDisplayed()
            }
            val after=compose.onNodeWithTag("description-tools").fetchSemanticsNode().boundsInRoot
            assertEquals(tools.top,after.top,.5f)
            compose.onNodeWithText("Confirmar").assertIsDisplayed()
            compose.onNodeWithText("Cancelar").assertIsDisplayed()
            compose.runOnIdle {assertEquals(source,draft.description);assertEquals(0,draft.descriptionSelectionStart);assertEquals(4,draft.descriptionSelectionEnd)}
        }
    }
    @Test fun projectToolsRemainOutsideLongBody() {
        val draft=draft()
        compose.setContent {Arachn0deTheme {ProjectDialog(draft,false,{}, {_,_->})}}
        awaitReady(draft)
        val tools=compose.onNodeWithTag("description-tools").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Código").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Guardar").assertIsDisplayed()
        compose.onNodeWithText("Cerrar").assertIsDisplayed()
        val after=compose.onNodeWithTag("description-tools").fetchSemanticsNode().boundsInRoot
        assertEquals(tools.top,after.top,.5f)
        assertEquals((1..120).joinToString("\n") {"Long description $it"},draft.description)
    }
    @Config(sdk=[33])
    @Test fun propertyToolsStayAboveImeInsets() {
        val draft=draft()
        compose.setContent {Arachn0deTheme {
            Box(Modifier.fillMaxSize().imePadding(),contentAlignment=androidx.compose.ui.Alignment.BottomCenter) {
                NodePropertyEditor(draft,NodeProperty.DESCRIPTION,true,340.dp,emptyList(),TagState(),app.nodeRepository.tags,emptyList(),{}, {})
            }
        }}
        awaitReady(draft)
        fun view(v:android.view.View):android.view.View? {
            if(v.javaClass.simpleName=="AndroidComposeView")return v
            if(v is android.view.ViewGroup)for(i in 0 until v.childCount)view(v.getChildAt(i))?.let {return it}
            return null
        }
        val root=checkNotNull(view(compose.activity.window.decorView))
        compose.runOnIdle {
            val insets=androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(),androidx.core.graphics.Insets.of(0,0,0,160))
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(),true).build()
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(root,insets)
        }
        val tools=compose.onNodeWithTag("description-tools").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Tools must remain above the simulated keyboard",tools.bottom<=root.height-160+.5f)
        compose.onNodeWithContentDescription("Insertar imagen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirmar").assertIsDisplayed()
        assertEquals((1..120).joinToString("\n") {"Long description $it"},draft.description)
    }
}
