package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.*
import com.r0ybt.arachn0de.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w640dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawerOrganizationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun compactDrawerKeepsBottomNavigationAndReachableClose() {
        var closed=0
        compose.setContent {Arachn0deTheme {Box(Modifier.width(320.dp).height(520.dp)) {
            AppIdentityDrawer({closed++},onProjects={},onTechnologies={},onSearch={},onCalendar={})
        }}}
        val panel=compose.onNodeWithTag("navigation-drawer").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.height<520*compose.density.density)
        compose.onNodeWithTag("menu-category:Organizar").assertIsDisplayed().performClick()
        compose.onNodeWithTag("menu-item:Tecnologías").assertIsDisplayed()
        val close=compose.onNodeWithTag("drawer-close-bottom").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(close.height>=48*compose.density.density)
        compose.onNodeWithTag("drawer-close-bottom").performClick()
        assertEquals(1,closed)
    }
    private val events=mutableListOf<String>()
    private val groups=linkedMapOf(
        "Inicio" to listOf("Núcleos","Borradores","Metro","Buscar","Antisocial","Calendario","Tragaperras"),
        "Herramientas" to listOf("Diagramas","Wireframe","Pomodoro","Captura rápida"),
        "Organizar" to listOf("Etiquetas","Pagos","Recurrencias","Estadísticas de Metro"),
        "Ajustes" to listOf("Apariencia","Seguridad y respaldos","Acerca de"))
    @Composable private fun Panel() {
        fun route(name:String):()->Unit={events+="go:$name"}
        AppIdentityDrawer({events+="dismiss"},onProjects=route("Núcleos"),projectsSelected=true,onDrafts=route("Borradores"),onSearch=route("Buscar"),onPeople=route("Antisocial"),onCalendar=route("Calendario"),onSlots=route("Tragaperras"),onQuickCapture=route("Captura rápida"),
            onFavorites=route("Favoritos"),onRecurrences=route("Recurrencias"),onAttention=route("Atención"),onObligations=route("Pagos"),onDiagrams=route("Diagramas"),onWireframe=route("Wireframe"),onPomodoro=route("Pomodoro"),onMetro=route("Metro"),
            onTechnologies=route("Tecnologías"),onTags=route("Etiquetas"),onAppearance=route("Apariencia"),onSecurity=route("Seguridad y respaldos"),onBackups=route("fallback"),onAbout=route("Acerca de"),onMetroStatistics=route("Estadísticas de Metro"))
    }
    private fun click(label:String) {compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()}
    @Test fun everyDestinationUsesOriginalCallbackAndOnlyOneCategoryIsVisible() {
        compose.setContent {Arachn0deTheme {Panel()}}
        groups.forEach {(category,entries)->
            compose.onNodeWithTag("menu-category:$category").performClick().assertIsSelected()
            compose.onNodeWithText("Juego").assertDoesNotExist()
            groups.keys.filter {it!=category}.forEach {compose.onNodeWithTag("menu-category:$it").assertIsNotSelected()}
            entries.forEach {entry->
                val before=events.size;click(entry)
                assertEquals(listOf("dismiss","go:$entry"),events.drop(before))
            }
            groups.values.flatten().filter {it !in entries}.forEach {compose.onNodeWithText(it).assertDoesNotExist()}
        }
        assertEquals(18,events.count {it.startsWith("go:")});assertFalse("go:fallback" in events)
    }
    @Test fun shortNarrowPanelKeepsCategoriesReachableAtLargeFontInBothThemes() {
        val prefs=AppearancePreferences(compose.activity.getSharedPreferences("menu",0))
        compose.setContent {val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {Arachn0deTheme(prefs) {Box(Modifier.requiredSize(320.dp,400.dp)) {Panel()}}}}
        for(theme in listOf(AppearanceTheme.Dark,AppearanceTheme.DayGrey)) {
            compose.runOnIdle {prefs.setTheme(theme)}
            groups.forEach {(category,entries)->
                val tab=compose.onNodeWithTag("menu-category:$category").assertIsDisplayed();tab.performClick()
                val original=tab.fetchSemanticsNode().boundsInRoot
                entries.forEach {entry->
                    val node=compose.onNode(hasText(entry) and hasClickAction()).performScrollTo().assertIsDisplayed()
                    val bounds=node.fetchSemanticsNode().boundsInRoot
                    val panel=compose.onNodeWithTag("navigation-drawer").fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.left>=panel.left && bounds.right<=panel.right+1f)
                }
                assertEquals(original,tab.assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
            }
        }
    }
    @Test fun categoryRestoresAndBackAndOutsideDismissWithoutNavigation() {
        val restore=StateRestorationTester(compose)
        restore.setContent {Arachn0deTheme {Panel()}}
        compose.onNodeWithTag("menu-category:Organizar").performClick();restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("menu-category:Organizar").assertIsSelected()
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        assertEquals(listOf("dismiss"),events)
        compose.onRoot().performTouchInput {click(androidx.compose.ui.geometry.Offset(4f,4f))}
        assertEquals(listOf("dismiss","dismiss"),events)
    }
    @Test fun backupAndOfficialLogoRemainAvailableAcrossCategoryChanges() {
        val theme=AppearanceTheme.NightBlue
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalAppearancePalette provides theme.palette) {AppIdentityDrawer({events+="dismiss"},onBackups={events+="backup"},onPomodoro=null)}}}
        val header=compose.onNodeWithTag("drawer-logo").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        groups.keys.forEach {category->
            compose.onNodeWithTag("menu-category:$category").performClick()
            val current=compose.onNodeWithTag("drawer-logo").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val panel=compose.onNodeWithTag("navigation-drawer").fetchSemanticsNode().boundsInRoot
            // The compact panel changes height with its category; the official logo keeps its size and stays inside it.
            assertEquals(header.size,current.size)
            assertTrue(current.top>=panel.top && current.bottom<=panel.bottom)
        }
        click("Seguridad y respaldos");assertEquals(listOf("dismiss","backup"),events)
        compose.onNodeWithText("Metro").assertDoesNotExist()
    }
}
