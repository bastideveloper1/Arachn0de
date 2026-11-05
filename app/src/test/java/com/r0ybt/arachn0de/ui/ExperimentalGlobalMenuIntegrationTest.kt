package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.sharing.SharingTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class ExperimentalGlobalMenuIntegrationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    private fun awaitTag(tag:String)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()}
        val process=Arachn0deApplication::class.java.getDeclaredField("processScope").apply {isAccessible=true}.get(app) as CoroutineScope
        runBlocking {process.coroutineContext[Job]?.cancelAndJoin();app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin()}
        app.database.close()
    }
    @Test fun oneBottomAccessReservesSpaceAndSurvivesNavigationWithoutChangingContextualActions() {
        val project=runBlocking {app.projectRepository.createProject("Origen experimental")}
        val layer=runBlocking {app.nodeRepository.createNode(project.id,null,"Capa conservada",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)}
        var keyboard by mutableStateOf(0.dp)
        compose.setContent {Arachn0deTheme {AppSafeArea(WindowInsets(bottom=keyboard+24.dp)) {AppRoot(app.projectRepository,app.nodeRepository)}}}
        awaitTag("dashboard-bottom-actions")
        fun assertBottomAccess(bar:String) {
            compose.onAllNodesWithContentDescription("Abrir menú").assertCountEquals(1)
            val button=compose.onNodeWithTag("global-menu-access").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val footer=compose.onNodeWithTag(bar).fetchSemanticsNode().boundsInRoot
            assertTrue(button.right<=footer.right+1)
            assertTrue(button.top>=footer.top-1 && button.bottom<=footer.bottom+1)
            compose.onNodeWithTag("global-navigation-bottom").assertDoesNotExist()
            compose.onNodeWithTag("global-menu-access").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        assertBottomAccess("dashboard-bottom-actions")
        val card=compose.onNodeWithTag("project-card:${project.id}").fetchSemanticsNode().boundsInRoot
        val dashboardBar=compose.onNodeWithTag("dashboard-bottom-actions").fetchSemanticsNode().boundsInRoot
        assertEquals(card.left,dashboardBar.left,1f);assertEquals(card.right,dashboardBar.right,1f)
        compose.onNodeWithText(project.name).performClick()
        awaitTag("nodes-list");assertBottomAccess("core-navigation-actions")
        val calendar=compose.onNodeWithTag("context-calendar").fetchSemanticsNode().boundsInRoot
        val interiorBar=compose.onNodeWithTag("core-navigation-actions").fetchSemanticsNode().boundsInRoot
        assertEquals(calendar.left,interiorBar.left,1f);assertEquals(calendar.right,interiorBar.right,1f)
        compose.onNodeWithText(layer.title).performClick()
        awaitTag("nodes-list")
        val nodes=compose.onNodeWithTag("nodes-list").fetchSemanticsNode().boundsInRoot
        val projectFooter=compose.onNodeWithTag("core-navigation-actions").fetchSemanticsNode().boundsInRoot
        assertTrue(nodes.bottom<=projectFooter.top)
        compose.onNodeWithTag("global-menu-access").performClick()
        compose.onNodeWithTag("menu-category:Herramientas").performClick()
        compose.onNode(hasText("Wireframe") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithTag("global-menu-access").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Abrir menú").assertCountEquals(0)
        compose.onNodeWithText("Volver").performClick()
        awaitTag("nodes-list");assertBottomAccess("core-navigation-actions")
        compose.onNodeWithText(layer.title,useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("global-menu-access").performClick()
        compose.onAllNodesWithTag("navigation-drawer").assertCountEquals(1)
        compose.onNodeWithTag("menu-category:Inicio").performClick()
        compose.onNode(hasText("Buscar") and hasClickAction()).performScrollTo().performClick()
        awaitTag("search-screen");assertBottomAccess("search-navigation-actions")
        compose.onNodeWithText("Título o descripción").performTextInput("borrador conservado")
        val beforeIme=compose.onNodeWithTag("search-navigation-actions").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {keyboard=240.dp}
        val withIme=compose.onNodeWithTag("search-navigation-actions").fetchSemanticsNode().boundsInRoot
        assertTrue(withIme.bottom<=beforeIme.bottom-240*compose.density.density+1)
        compose.onNodeWithTag("global-menu-access").assertIsDisplayed().performClick()
        compose.onNodeWithTag("drawer-close-bottom").performClick()
        compose.onNodeWithText("Título o descripción").assertTextContains("borrador conservado")
        assertBottomAccess("search-navigation-actions")
        compose.onNodeWithTag("global-menu-access").performClick()
        compose.onNodeWithTag("menu-category:Inicio").performClick()
        compose.onNode(hasText("Antisocial") and hasClickAction()).performScrollTo().performClick()
        awaitTag("people-navigation-actions");assertBottomAccess("people-navigation-actions")
        compose.onNodeWithTag("global-menu-access").performClick()
        compose.onNodeWithTag("menu-category:Inicio").performClick()
        compose.onNode(hasText("Borradores") and hasClickAction()).performScrollTo().performClick()
        awaitTag("new-draft");assertBottomAccess("drafts-navigation-actions")
        compose.onNodeWithTag("new-draft").performClick()
        compose.waitUntil(15000) {compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodes(hasContentDescription("Abrir menú") and hasAnyAncestor(isDialog())).assertCountEquals(0)
    }
}
