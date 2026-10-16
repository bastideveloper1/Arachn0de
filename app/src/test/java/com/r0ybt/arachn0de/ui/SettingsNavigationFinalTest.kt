package com.r0ybt.arachn0de.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.backup.BackupFixture
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class SettingsNavigationFinalTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        pump()
        runBlocking {app.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin();withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}}
        app.database.close()
    }
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {pump();block()}
    private fun text(value:String) {await {compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()}}
    @Test fun antisocialAndUnifiedSettingsUseExistingRoutesWithoutDuplicates() {
        var destination by androidx.compose.runtime.mutableStateOf("")
        compose.setContent {Arachn0deTheme {
            if(destination=="people") PeopleScreen(app.personRepository) {destination=""}
            else AppIdentityDrawer({},onProjects={destination="projects"},onMetro={destination="metro"},onPeople={destination="people"},peopleSelected=true,
                onTechnologies={},onTags={},onSecurity={destination="security"},onBackups={destination="backups"},onAppearance={})
        }}
        val primary=listOf("Núcleos","Metro","Antisocial").map {compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top}
        assertEquals(primary.sorted(),primary);compose.onNodeWithText("Antisocial").assertIsSelected()
        compose.onNodeWithText("Recursos").performScrollTo().performClick();compose.onNodeWithText("Personas").assertDoesNotExist()
        compose.onNodeWithText("Ajustes").performScrollTo().performClick()
        compose.onNodeWithText("Respaldo").assertDoesNotExist();compose.onNodeWithText("Seguridad").assertDoesNotExist()
        compose.onNodeWithText("Seguridad y respaldos").performScrollTo().performClick();assertEquals("security",destination)
        compose.onNodeWithText("Antisocial").performClick();text("Buscar Personas");compose.onNodeWithText("Personas").assertExists()
    }
    @Test fun standardLayersIconKeepsExistingBottomAction() {
        runBlocking {app.projectRepository.createProject("Núcleo de prueba")}
        var locks=0
        compose.setContent {Arachn0deTheme {
            androidx.compose.runtime.CompositionLocalProvider(LocalProjectLock provides ProjectLockAction(true) {locks++}) {
                AppRoot(app.projectRepository,app.nodeRepository)
            }
        }}
        text("Núcleo de prueba");compose.onNodeWithText("Núcleo de prueba").performClick();text("Capas")
        val row=compose.onNodeWithTag("project-bottom-actions")
        row.assertExists()
        compose.onNodeWithText("Bloquear").assertDoesNotExist()
        val lock=compose.onNodeWithContentDescription("Bloquear").assertIsDisplayed()
        val create=compose.onNode(hasClickAction() and hasText("Crear")).fetchSemanticsNode().boundsInRoot
        val layers=compose.onNode(hasClickAction() and hasText("Capas")).fetchSemanticsNode().boundsInRoot
        val header=lock.fetchSemanticsNode().boundsInRoot
        assertEquals(create.width,layers.width,1f);assertEquals(create.height,layers.height,1f)
        assertEquals(create.top,layers.top,1f);assertTrue(create.right<layers.left)
        assertTrue(header.bottom<create.top);assertTrue(header.center.x>layers.center.x)
        compose.onAllNodesWithContentDescription("Bloquear").assertCountEquals(1)
        lock.performClick();assertEquals(1,locks)
        compose.onNodeWithContentDescription("Capas").performClick();text("Capas de cebolla")
        compose.onNodeWithText("Crear").assertExists()
    }
}
