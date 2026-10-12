package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.R
import com.r0ybt.arachn0de.access.*
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w360dp-h800dp")
class OfficialIconsUiTest {
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
    @Test fun drawerDestinationsKeepTheirOriginalActionsWithoutDuplicates() {
        var destination=""
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {
            AppIdentityDrawer({},onProjects={destination="projects"},onPeople={destination="people"},onMetro={destination="metro"})
        }}}
        compose.onNodeWithText("Recursos").performScrollTo().performClick()
        for((label,key) in listOf("Proyectos" to "projects","Personas" to "people","Metro" to "metro")) {
            compose.onAllNodesWithText(label).assertCountEquals(1)
            val item=compose.onNodeWithText(label)
            if(runCatching {item.assertIsDisplayed()}.isFailure) item.performScrollTo()
            item.performClick()
            assertEquals(key,destination)
        }
    }
    @Test fun bottomLockReusesCallbackAndQuickCaptureUsesExistingDialog() {
        var locks=0
        val project=runBlocking {app.projectRepository.createProject("Destino")}
        val repository=AccessRepository(app.database)
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalProjectLock provides ProjectLockAction(true) {locks++},
                LocalElementAccess provides repository,
                LocalQuickCaptureIndex provides ElementSearch.index(listOf(project),emptyList())) {
                Row { ProjectLockButton(); ProjectQuickCaptureButton() }
            }
        }}}
        compose.onNodeWithContentDescription("Bloquear").assertWidthIsAtLeast(48.dp).performClick()
        assertEquals(1,locks)
        compose.onNodeWithContentDescription("Captura rápida").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Crear tarea").assertIsNotEnabled()
        compose.onNodeWithText("Título").assertExists()
    }
    @Test fun projectBarKeepsOrderNewEditorAndOnionNavigator() {
        runBlocking {app.projectRepository.createProject("Iconos")}
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalProjectLock provides ProjectLockAction(true) {}) {
                AppRoot(app.projectRepository,app.nodeRepository)
            }
        }}}
        fun await(text:String) {compose.waitUntil(15000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }}
        await("Iconos");compose.onNodeWithText("Iconos").performClick();await("Nuevo elemento")
        val ordered=listOf(compose.onNodeWithContentDescription("Bloquear"),compose.onNodeWithText("Nuevo elemento"),
            compose.onNodeWithContentDescription("Captura rápida"),compose.onNodeWithContentDescription("Capas de cebolla"))
        val rectangles=ordered.map {it.assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot}
        rectangles.forEach {assertEquals(it.width,it.height,1f)}
        val positions=rectangles.map {it.center.x}
        assertEquals(positions.sorted(),positions)
        val gaps=rectangles.zipWithNext {left,right->right.left-left.right}
        gaps.forEach {assertTrue(it>0);assertEquals(gaps.first(),it,1f)}
        compose.onNodeWithText("+ Tarea").assertDoesNotExist()
        compose.onNodeWithContentDescription("Capas de cebolla").performClick()
        compose.onNodeWithText("Capas de cebolla").assertExists()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("Nuevo elemento").performClick();await("Título")
        compose.onNodeWithText("Título").assertExists()
    }
    @Test fun bottomActionsRemainSquareAndVisibleWithLargeFont() {
        runBlocking {app.projectRepository.createProject("Fuente grande")}
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.8f),
                LocalProjectLock provides ProjectLockAction(true) {}) {
                AppRoot(app.projectRepository,app.nodeRepository)
            }
        }}}
        fun await(text:String) {compose.waitUntil(15000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }}
        await("Fuente grande");compose.onNodeWithText("Fuente grande").performClick();await("Nuevo elemento")
        val controls=listOf(compose.onNodeWithContentDescription("Bloquear"),compose.onNodeWithText("Nuevo elemento"),
            compose.onNodeWithContentDescription("Captura rápida"),compose.onNodeWithContentDescription("Capas de cebolla"))
        controls.forEach {val bounds=it.assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
            assertEquals(bounds.width,bounds.height,1f)}
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Nuevo elemento",useUnmergedTree=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertFalse(layouts.single().hasVisualOverflow)
    }
    @Test fun allSevenUntintedImagesHaveBoundedGeometryOnSmallScreen() {
        val icons=listOf(R.drawable.official_personas,R.drawable.official_proyecto,R.drawable.official_metro,
            R.drawable.official_nuevo,R.drawable.official_bloquear,R.drawable.official_rapido,R.drawable.official_gestion_proyecto)
        val appearance=com.r0ybt.arachn0de.ui.theme.AppearancePreferences(compose.activity.getSharedPreferences("icons-test",0))
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme(preferences=appearance) {
            Column(Modifier.width(320.dp)) {icons.forEachIndexed {i,res-> OfficialIcon(res,"oficial:$i")}}
        }}}
        for(theme in com.r0ybt.arachn0de.ui.theme.AppearanceTheme.entries) {
            compose.runOnIdle {appearance.setTheme(theme)}
            icons.indices.forEach {compose.onNodeWithContentDescription("oficial:$it").assertIsDisplayed().assertWidthIsEqualTo(36.dp).assertHeightIsEqualTo(36.dp)}
        }
    }
}
