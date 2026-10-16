package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.testTag
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.MainActivity
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import com.r0ybt.arachn0de.update.ReleaseSource
import com.r0ybt.arachn0de.update.UpdateRepository
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28])
class VisualActionsNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @After fun close() {
        compose.runOnUiThread {
            val content=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until content.childCount) (content.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            content.removeAllViews()
        }
        val app=compose.activity.application as Arachn0deApplication
        kotlinx.coroutines.runBlocking {
            app.security.current.value!!.scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            app.security.current.value!!.scope.coroutineContext[kotlinx.coroutines.Job]?.join()
            kotlinx.coroutines.withTimeout(15000) {app.database.getCoroutineScope().coroutineContext[kotlinx.coroutines.Job]!!.children.toList().forEach {it.join()}}
        }
        app.database.close()
    }
    private fun mount(content:@Composable () -> Unit) { compose.runOnUiThread { compose.activity.setContent { Arachn0deTheme { AppSafeArea(content=content) } } } }
    private fun roleButton()=SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Button)
    @Test fun actionMenuHasExplicitRolesAndDistinctDestructiveSemantics() {
        var selected=""
        mount { ActionMenu("Elemento",{}) {
            ActionMenuItem("Convertir en nota",Icons.Default.SwapHoriz,{ selected="convert" })
            ActionMenuItem("Copiar este elemento",Icons.Default.ContentCopy,{ selected="copy" })
            ActionMenuItem("Eliminar",Icons.Default.Delete,{ selected="delete" },destructive=true)
        } }
        compose.onNodeWithText("Convertir en nota").assert(roleButton()).performClick();assertEquals("convert",selected)
        compose.onNodeWithText("Copiar este elemento").assert(roleButton()).performClick();assertEquals("copy",selected)
        compose.onNodeWithText("Eliminar").assert(roleButton()).assert(hasStateDescription("Acción destructiva"))
    }
    @Test fun drawerGroupsCollapseIndependentlyAndKeepPrimaryDestinationsOrdered() {
        var destination="";var dismissed=0
        mount {AppIdentityDrawer({dismissed++},onSearch={destination="Buscar"},onProjects={destination="Proyectos"},onMetro={destination="Metro"},
            onFavorites={destination="Favoritos"},onAttention={destination="Avisos"},onObligations={destination="Obligaciones"},onCalendar={destination="Calendario"},
            onPeople={destination="Personas"},onTechnologies={destination="Tecnologías"},onTags={destination="Etiquetas"},
            onSecurity={destination="Seguridad"},onBackups={destination="Respaldo"},onAppearance={destination="Diseño"},
            onQuickCapture={destination="Captura rápida"},onAbout={destination="Acerca de"},onGame={destination="Juego"})}
        val primary=listOf("Buscar","Proyectos","Metro").map {compose.onNodeWithText(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top}
        assertEquals(primary.sorted(),primary)
        val groups=listOf("Seguimiento" to listOf("Favoritos","Avisos","Obligaciones","Calendario"),
            "Recursos" to listOf("Personas","Tecnologías","Etiquetas"),"Ajustes" to listOf("Seguridad","Respaldo","Diseño"))
        for((section,children) in groups) {
            compose.onNodeWithTag("drawer-section:$section").assert(hasStateDescription("Contraída"))
            children.forEach {compose.onNodeWithText(it).assertDoesNotExist()}
            compose.onNodeWithText(section).performScrollTo().performClick()
            compose.onNodeWithTag("drawer-section:$section").assert(hasStateDescription("Expandida"))
            children.forEach {label->compose.onNodeWithText(label).performScrollTo().performClick();assertEquals(label,destination)}
            compose.onNodeWithText(section).performScrollTo().performClick()
            children.forEach {compose.onNodeWithText(it).assertDoesNotExist()}
        }
        for(label in listOf("Buscar","Proyectos","Metro","Captura rápida","Acerca de","Juego")) {
            val item=compose.onNodeWithText(label)
            if(runCatching {item.assertIsDisplayed()}.isFailure) item.performScrollTo()
            item.performClick();assertEquals(label,destination)
        }
        assertEquals(16,dismissed)
    }
    @Test fun compactDrawerHasIdentitySelectedDestinationScrollAndBackWithLargeFont() {
        mount {
            var open by remember { mutableStateOf(true) }
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                if(open) AppIdentityDrawer({ open=false },onProjects={},projectsSelected=true,onPeople={},onAttention={},onCalendar={},onObligations={},onAbout={})
            }
        }
        compose.onNodeWithTag("navigation-drawer").assert(hasStateDescription("Menú a ancho completo"))
        compose.onNodeWithText("Arachn0de").assertExists();compose.onNodeWithContentDescription("Logo de Arachn0de").assertExists()
        compose.onNodeWithText("Proyectos").assertIsSelected()
        compose.onNodeWithText("Acerca de").performScrollTo().assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("navigation-drawer").assertDoesNotExist()
    }
    @Test fun normalPhoneDestinationClosesDrawerAndNavigatesOnce() {
        var calendarCalls=0
        mount { var open by remember { mutableStateOf(true) }
            CompositionLocalProvider(LocalDensity provides Density(0.9f,1f)) {
                if(open) AppIdentityDrawer({ open=false },onProjects={},onCalendar={ calendarCalls++ },onAbout={})
            }
        }
        compose.onNodeWithTag("navigation-drawer").assert(hasStateDescription("Menú a ancho completo"))
        compose.onNodeWithText("Seguimiento").performScrollTo().performClick()
        compose.onNodeWithText("Calendario").performScrollTo().performClick()
        compose.onNodeWithTag("navigation-drawer").assertDoesNotExist();assertEquals(1,calendarCalls)
    }
    @Test fun widerWindowKeepsLateralSheetAndExplicitClose() {
        mount { var open by remember { mutableStateOf(true) }
            CompositionLocalProvider(LocalDensity provides Density(0.5f,1f)) {
                if(open) AppIdentityDrawer({ open=false },onProjects={},onAbout={})
            }
        }
        compose.onNodeWithTag("navigation-drawer").assert(hasStateDescription("Menú lateral"))
        compose.onNodeWithContentDescription("Cerrar menú").assert(roleButton()).performClick()
        compose.onNodeWithTag("navigation-drawer").assertDoesNotExist()
        assertEquals(320.dp,DrawerWidthPolicy.width(320.dp));assertEquals(393.dp,DrawerWidthPolicy.width(393.dp))
        assertEquals(599.dp,DrawerWidthPolicy.width(599.dp));assertEquals(360.dp,DrawerWidthPolicy.width(600.dp));assertEquals(360.dp,DrawerWidthPolicy.width(840.dp))
    }
    @Test fun socialRowsKeepIdentifiersDistinctSourceAndAccessibleExternalAffordance() {
        mount { AboutScreen({},UpdateRepository(ReleaseSource { emptyList() })) }
        for(link in AuthorLinks.profiles) {
            compose.onNodeWithText(link.label).performScrollTo().assert(roleButton())
            compose.onNodeWithContentDescription(link.label.replace(" · ",", ")+", abrir enlace externo").assertExists()
        }
        compose.onNodeWithText("Código fuente").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("Código fuente, Arachn0de, abrir enlace externo").performScrollTo().assertExists()
        assertNotEquals(AuthorLinks.profiles[1].url,AuthorLinks.source.url)
    }
    @Test fun externalViewFailureKeepsVisibleErrorWithLargeFont() {
        val unavailable=object:android.content.ContextWrapper(compose.activity) {
            override fun startActivity(intent:android.content.Intent) { throw SecurityException("Test") }
        }
        mount { val density=LocalDensity.current
            CompositionLocalProvider(LocalContext provides unavailable,LocalDensity provides Density(density.density,1.6f)) {
                AboutScreen({},UpdateRepository(ReleaseSource { emptyList() }))
            }
        }
        compose.onNodeWithText(AuthorLinks.profiles[2].label).performScrollTo().performClick()
        compose.onNodeWithText("No se pudo abrir el enlace. No hay una aplicación disponible o Android impidió abrirlo.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(AuthorLinks.profiles[2].label).assertExists()
    }
    @Test fun phoneDrawerFillsAvailableAreaInsideAsymmetricSafeInsets() {
        compose.runOnUiThread { compose.activity.setContent { Arachn0deTheme {
            Box(Modifier.fillMaxSize().testTag("window")) {
                AppSafeArea(WindowInsets(left=12.dp,top=24.dp,right=20.dp,bottom=32.dp)) {
                    Box(Modifier.fillMaxSize().testTag("safe-area")) {
                        AppIdentityDrawer({},onProjects={},onAbout={})
                    }
                }
            }
        } } }
        val window=compose.onNodeWithTag("window").fetchSemanticsNode().boundsInRoot
        val safe=compose.onNodeWithTag("safe-area").fetchSemanticsNode().boundsInRoot
        val drawer=compose.onNodeWithTag("navigation-drawer").fetchSemanticsNode().boundsInRoot
        assertTrue(safe.left>window.left && safe.top>window.top)
        assertTrue(safe.right<window.right && safe.bottom<window.bottom)
        assertEquals(safe,drawer)
    }

}
