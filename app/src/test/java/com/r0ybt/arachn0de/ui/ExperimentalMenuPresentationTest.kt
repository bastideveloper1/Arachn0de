package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w320dp-h700dp")
class ExperimentalMenuPresentationTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun everyExistingActionKeepsItsCallbackWithStableBottomCategoriesAndLargeText() {
        val routes=mutableListOf<String>()
        val groups=linkedMapOf(
            "Inicio" to listOf("Núcleos","Borradores","Metro","Buscar","Antisocial","Calendario","Tragaperras"),
            "Herramientas" to listOf("Diagramas","Wireframe","Pomodoro","Captura rápida"),
            "Organizar" to listOf("Tecnologías","Etiquetas","Pagos","Recurrencias","Estadísticas de Metro"),
            "Ajustes" to listOf("Apariencia","Seguridad y respaldos","Acerca de"))
        compose.setContent {Arachn0deTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {
                fun route(label:String):()->Unit={routes+=label}
                AppIdentityDrawer({routes+="dismiss"},experimentalSheet=true,onProjects=route("Núcleos"),onDrafts=route("Borradores"),
                    onMetro=route("Metro"),onSearch=route("Buscar"),onPeople=route("Antisocial"),onCalendar=route("Calendario"),onSlots=route("Tragaperras"),
                    onDiagrams=route("Diagramas"),onWireframe=route("Wireframe"),onPomodoro=route("Pomodoro"),onQuickCapture=route("Captura rápida"),
                    onTechnologies=route("Tecnologías"),onTags=route("Etiquetas"),onObligations=route("Pagos"),onRecurrences=route("Recurrencias"),onMetroStatistics=route("Estadísticas de Metro"),
                    onAppearance=route("Apariencia"),onSecurity=route("Seguridad y respaldos"),onAbout=route("Acerca de"))
            }
        }}
        var bottom:Float?=null
        for((category,entries) in groups) {
            compose.onNodeWithTag("menu-category:$category").assertIsDisplayed().performClick().assertIsSelected()
            val categories=compose.onNodeWithTag("menu-categories").fetchSemanticsNode().boundsInRoot
            bottom?.let {assertEquals(it,categories.bottom,1f)} ?: run {bottom=categories.bottom}
            compose.onNodeWithTag("drawer-close-bottom").assertIsDisplayed().assertIsOn()
            for(entry in entries) {
                val item=compose.onNode(hasText(entry) and hasClickAction()).performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
                val bounds=item.fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.bottom<=categories.top+1)
                val before=routes.size
                item.performClick()
                assertEquals(listOf("dismiss",entry),routes.drop(before))
            }
        }
        assertEquals(20,routes.count {it!="dismiss"})
    }

    @Test fun movingAccessKeepsExistingBusyGuardsAndDoesNotDuplicateTheHeaderButton() {
        var busy by mutableStateOf(true)
        var clicks=0
        var modal by mutableStateOf(false)
        val gate=GlobalMenuGate()
        val navigation=GlobalNavigation({clicks++},false,bottomAccess=true,gate=gate)
        compose.setContent {Arachn0deTheme {CompositionLocalProvider(LocalGlobalNavigation provides navigation) {
            BottomModulesFrame {
                Row {GlobalChromeActions(enabled=!busy,leading=true);GlobalMenuAccess(navigation)}
                if(modal) BottomModulesDialog(onDismissRequest={modal=false}) {
                    Row {GlobalChromeActions(leading=true);androidx.compose.material3.Text("Editor modal");GlobalMenuAccess()}
                }
            }
        }}}
        compose.onAllNodesWithContentDescription("Abrir menú").assertCountEquals(1)
        compose.onNodeWithTag("global-menu-access").assertIsNotEnabled()
        assertEquals(0,clicks)
        compose.runOnIdle {busy=false}
        compose.onNodeWithTag("global-menu-access").assertIsEnabled().performClick()
        assertEquals(1,clicks)
        compose.runOnIdle {modal=true}
        compose.onNodeWithText("Editor modal").assertIsDisplayed()
        compose.onAllNodes(hasContentDescription("Abrir menú") and hasAnyAncestor(isDialog())).assertCountEquals(0)
    }

    @Config(qualifiers="w411dp-h891dp")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun fourContextActionsKeepTheirHeightAndTargetsWhenAddingMenuAtDifferentWidthsAndFonts() {
        var width by mutableStateOf(411.dp)
        var scale by mutableStateOf(1f)
        var integrated by mutableStateOf(false)
        val controls=TaskViewControls(com.r0ybt.arachn0de.domain.model.TaskStatus.VERIFY,emptyMap(),true,false,{}, {}, {})
        compose.setContent {Arachn0deTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale),
                LocalGlobalNavigation provides GlobalNavigation({},false,bottomAccess=integrated)) {
                Box(androidx.compose.ui.Modifier.width(width)) {CoreBottomModule(null,{},300.dp,{}, {},viewControls=controls)}
            }
        }}
        for(w in listOf(280.dp,320.dp,411.dp)) for(font in listOf(1f,1.5f,2f)) {
            compose.runOnIdle {width=w;scale=font;integrated=false}
            val before=compose.onNodeWithTag("core-navigation-actions").fetchSemanticsNode().boundsInRoot.height
            compose.runOnIdle {integrated=true}
            val after=compose.onNodeWithTag("core-navigation-actions").fetchSemanticsNode().boundsInRoot.height
            assertEquals("Bar height at $w and font $font",before,after,1f)
            for(context in listOf("Content","Creation","View","Options")) {
                compose.onNodeWithTag("core-context:$context").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            }
            if(w==411.dp && font==1f) compose.onNodeWithTag("global-menu-access").assertIsDisplayed()
            val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithText("Por verificar",useUnmergedTree=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {it(layouts)}
            assertFalse("Text overflow at $w font $font: ${layouts.single().size}, lines=${layouts.single().lineCount}, width=${layouts.single().didOverflowWidth}, height=${layouts.single().didOverflowHeight}",layouts.single().hasVisualOverflow)
        }
    }

    @Test fun sheetClosesWithBackOutsideAndDownwardGesture() {
        var open by mutableStateOf(true)
        compose.setContent {Arachn0deTheme {if(open) AppIdentityDrawer({open=false},onProjects={},experimentalSheet=true)}}
        compose.onNodeWithTag("navigation-drawer").assertIsDisplayed()
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("navigation-drawer").assertDoesNotExist()
        compose.runOnIdle {open=true}
        compose.onNode(isDialog()).performTouchInput {click(androidx.compose.ui.geometry.Offset(3f,3f))}
        compose.waitUntil(5000) {!open}
        compose.onNodeWithTag("navigation-drawer").assertDoesNotExist()
        compose.runOnIdle {open=true}
        compose.onNodeWithTag("navigation-drawer").performTouchInput {swipeDown(startY=2f,endY=height.toFloat()-2f,durationMillis=300)}
        compose.waitUntil(5000) {!open}
    }
}
