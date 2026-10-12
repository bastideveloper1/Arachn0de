package com.r0ybt.arachn0de.ui


import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.access.AccessRepository
import com.r0ybt.arachn0de.domain.model.NodePurpose
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w360dp-h800dp")
class GlobalNavigationUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {
        compose.runOnUiThread {
            val content=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until content.childCount) (content.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            content.removeAllViews()
        }
        pump()
        val application=app;val db=application.database
        runBlocking {
            application.security.current.value!!.scope.coroutineContext[Job]?.cancelAndJoin()
            withTimeout(15000) {db.getCoroutineScope().coroutineContext[Job]!!.children.toList().joinAll()}
        }
        db.close()
    }
    private fun pump() {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()}
    private fun await(text:String)=compose.waitUntil(15000) {pump();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun click(text:String) {
        val section=when(text) {"Favoritos","Avisos","Calendario","Obligaciones","Recurrencias"->"Seguimiento";"Personas","Tecnologías","Etiquetas"->"Recursos";"Seguridad","Respaldo","Apariencia"->"Ajustes";else->null}
        if(section!=null && compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()) compose.onNodeWithText(section).performScrollTo().performClick()
        await(text);val node=compose.onNodeWithText(text);if(runCatching {node.assertIsDisplayed()}.isFailure) node.performScrollTo();node.performClick()}
    private fun menu() {compose.onNodeWithContentDescription("Abrir menú").performClick()}
    private fun mount() {compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}}}
    @Test fun recurrencesAreOnlyInTrackingAndKeepTheirExistingDialog() {
        runBlocking {app.projectRepository.createProject("Raíz")}
        mount();await("Raíz")
        compose.onNodeWithText("Recurrencias").assertDoesNotExist()
        menu();click("Recurrencias");await("Recurrencias")
        compose.onNodeWithTag("navigation-drawer").assertDoesNotExist()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithText("Recurrencias").assertDoesNotExist()
        compose.onNodeWithText("Raíz").assertIsDisplayed()
    }
    @Test fun projectCardPlanningPreservesHistoricalMethodAndSprintIndependence() {
        val project=runBlocking {app.projectRepository.createProject("Planificar")}
        val original=runBlocking {app.nodeRepository.management.configure(project.id,null,true,com.r0ybt.arachn0de.data.repository.ManagementMethodology.HYBRID)!!}
        val modes=runBlocking {app.nodeRepository.getProjectNodes(project.id)}.map {it.id to it.sprintMode}
        mount();await("Planificar")
        compose.onNodeWithContentDescription("Opciones del proyecto").performClick()
        click("Planificación");await("Guardar")
        for(label in listOf("Ágil","Híbrida","Tradicional")) compose.onNodeWithText(label).assertDoesNotExist()
        click("Guardar");await("Planificar")
        assertEquals(original,runBlocking {app.database.managementContextDao().all().single()})
        assertEquals(modes,runBlocking {app.nodeRepository.getProjectNodes(project.id)}.map {it.id to it.sprintMode})
    }
    @Test fun drawerExpansionSurvivesRecreationWithoutOpeningOtherGroups() {
        val restoration=androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {Arachn0deTheme {AppSafeArea {AppIdentityDrawer({},onPeople={},onAttention={},onAppearance={})}}}
        compose.onNodeWithText("Recursos").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Personas").assertExists()
        compose.onNodeWithTag("drawer-section:Seguimiento").assert(hasStateDescription("Contraída"))
        compose.onNodeWithTag("drawer-section:Ajustes").assert(hasStateDescription("Contraída"))
        compose.onNodeWithText("Recursos").performScrollTo().performClick()
        compose.onNodeWithText("Personas").assertDoesNotExist()
    }
    @Test fun searchRestoresQueryFilterAndReturnsToOriginalLayer() {
        val project=runBlocking {app.projectRepository.createProject("Origen")}
        val layer=runBlocking {app.nodeRepository.createNode(project.id,null,"Área",purpose=NodePurpose.LAYER)}
        val task=runBlocking {app.nodeRepository.createNode(project.id,layer.id,"Destino local")}
        runBlocking {AccessRepository(app.database).toggle("node:${task.id}")}
        val restoration=StateRestorationTester(compose)
        restoration.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        click("Origen");await("Área");compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Área"));click("Área");await("CAPA 1")
        menu();click("Favoritos");compose.onNodeWithTag("search-screen").assertIsDisplayed()
        compose.onNodeWithText("Título o descripción").performTextInput("destino")
        await("Destino local");compose.onNodeWithText("Favoritos").assertIsSelected()
        compose.onNodeWithTag("access-result:node:${task.id}").performClick();await("Destino local")
        compose.onNodeWithContentDescription("Volver a búsqueda").performClick();await("Título o descripción")
        compose.onNodeWithText("Título o descripción").assertTextContains("destino")
        compose.onNodeWithText("Favoritos").assertIsSelected()
        restoration.emulateSavedInstanceStateRestore();await("Destino local")
        compose.onNodeWithText("Título o descripción").assertTextContains("destino")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await("CAPA 1");compose.onNodeWithTag("app-bar-title").assertTextEquals("Origen")
        compose.onNodeWithTag("search-screen").assertDoesNotExist()
    }
    @Test fun drawerPreservesEveryDestinationAndSecurityUsesExistingCallback() {
        var settings=0
        runBlocking {app.projectRepository.createProject("Raíz")}
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalOpenSecurity provides {settings++}) {AppRoot(app.projectRepository,app.nodeRepository)}
        }}}
        await("Raíz");menu()
        for(section in listOf("Seguimiento","Recursos","Ajustes")) compose.onNodeWithText(section).performScrollTo().performClick()
        for(label in listOf("Buscar","Favoritos","Proyectos","Avisos","Calendario","Obligaciones","Personas","Tecnologías","Etiquetas","Metro","Seguridad","Respaldo","Captura rápida","Apariencia","Acerca de","Juego")) compose.onAllNodesWithText(label).assertCountEquals(1)
        click("Seguridad");assertEquals(1,settings);compose.onNodeWithTag("navigation-drawer").assertDoesNotExist()
        menu();click("Respaldo");await("Backup y restauración")
        compose.onNodeWithText("Respaldos").assertExists()
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};await("Raíz")
    }
    @Test fun searchFromPeopleReturnsToPeopleAndKeepsItsQuery() {
        runBlocking {app.projectRepository.createProject("Raíz");app.personRepository.save("person-local","Persona local",null)}
        mount();await("Raíz");menu();click("Personas");await("Persona local")
        menu();click("Buscar");compose.onNodeWithText("Título o descripción").performTextInput("raiz")
        await("Raíz");compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        await("Persona local");menu();click("Buscar")
        compose.onNodeWithText("Título o descripción").assertTextContains("raiz")
    }
    @Test fun projectHasOneHeaderAndKeepsThreeBottomActions() {
        runBlocking {
            val project=app.projectRepository.createProject("Raíz")
            app.nodeRepository.createNode(project.id,null,"Capa de referencia",purpose=com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        }
        mount();click("Raíz");await("Crear")
        compose.onAllNodesWithTag("app-bar-title").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Abrir menú").assertCountEquals(1)
        compose.onAllNodesWithText("Bloquear").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Captura rápida").assertCountEquals(0)
        compose.onNodeWithText("Crear").assertIsDisplayed()
        compose.onNodeWithText("Capas").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Capas").assertCountEquals(1)
        val bottomBounds=listOf("Bloquear","Crear","Capas").map {compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot}
        bottomBounds.forEach {bounds ->
            assertTrue("Comfortable touch height",bounds.height >= 64f * compose.density.density)
            assertEquals(bottomBounds.first().top,bounds.top,1f)
            assertEquals(bottomBounds.first().height,bounds.height,1f)
            assertEquals(bottomBounds.first().width,bounds.width,1f)
        }
        assertEquals(bottomBounds[1].left-bottomBounds[0].right,bottomBounds[2].left-bottomBounds[1].right,1f)
        // Software rendering gives a review artifact without relying on Robolectric PixelCopy.
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val output=java.io.File("build/reports/block1a-bottom-actions.png")
            output.parentFile?.mkdirs()
            output.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
        val listBounds=compose.onNodeWithTag("nodes-list").fetchSemanticsNode().boundsInRoot
        assertTrue("List ends before the buttons",listBounds.bottom <= bottomBounds.first().top)
        compose.onNodeWithTag("nodes-list").performTouchInput {swipeUp()}
        listOf("Bloquear","Crear","Capas").forEachIndexed {index,label ->
            assertEquals(bottomBounds[index],compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot)
        }
        compose.onNodeWithText("Buscar").assertDoesNotExist()
        compose.onNodeWithText("Seguridad").assertDoesNotExist()
        click("Capas");await("Capas de cebolla")
        compose.onNodeWithText("Cerrar").performClick();await("Crear")
        click("Crear");await("Nuevo elemento")
        compose.onNodeWithText("Cerrar").performClick()
        await("Crear")
        compose.onNodeWithContentDescription("Ordenar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Filtros").assertIsDisplayed()
        menu();click("Buscar");await("Título o descripción");compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};await("Crear")
    }
    @Test fun securityDialogIsReachableWithoutAPermanentSecurityToolbar() {
        runBlocking {app.projectRepository.createProject("Raíz")}
        val manager=app.security;val session=manager.current.value!!
        compose.runOnUiThread {compose.activity.setContent {Arachn0deTheme {
            var settings by androidx.compose.runtime.remember {androidx.compose.runtime.mutableStateOf<(() -> Unit)?>(null)}
            var lock by androidx.compose.runtime.remember {androidx.compose.runtime.mutableStateOf<ProjectLockAction?>(null)}
            var page by androidx.compose.runtime.remember {androidx.compose.runtime.mutableStateOf<(@androidx.compose.runtime.Composable (() -> Unit) -> Unit)?>(null)}
            CompositionLocalProvider(LocalOpenSecurity provides settings,LocalProjectLock provides lock,LocalSecurityPage provides page) {
                androidx.compose.foundation.layout.Column {
                    com.r0ybt.arachn0de.security.SecurityControls(manager,session,showChrome=false,onLockReady={lock=it},onSettingsReady={settings=it},onPageReady={page=it})
                    AppRoot(app.projectRepository,app.nodeRepository)
                }
            }
        }}}
        await("Raíz");compose.onNodeWithText("Seguridad").assertDoesNotExist()
        menu();click("Seguridad");await("Bloqueo al pasar a segundo plano")
        compose.onNodeWithTag("security-screen").assertIsDisplayed()
        compose.onNodeWithText("Permitir capturas de pantalla").assertExists()
        click("Cerrar");compose.onNodeWithText("Seguridad").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Bloquear").assertCountEquals(1)
    }

    @Test fun searchOpenedFromBackupsReturnsToThatScreen() {
        runBlocking {app.projectRepository.createProject("Raíz")}
        mount();await("Raíz");menu();click("Respaldo");await("Backup y restauración")
        menu();click("Buscar");await("Título o descripción")
        compose.onAllNodesWithContentDescription("Abrir menú").assertCountEquals(1)
        compose.onNodeWithContentDescription("Volver").performClick();await("Backup y restauración")
    }

    @Test fun searchScrollSurvivesOpeningAResultAndRestoration() {
        val project=runBlocking {app.projectRepository.createProject("Raíz")}
        runBlocking {repeat(36) {app.nodeRepository.createNode(project.id,null,"Trabajo "+it.toString().padStart(3,'0'))}}
        val restoration=StateRestorationTester(compose)
        restoration.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        await("Raíz");menu();click("Buscar")
        compose.onNodeWithText("Título o descripción").performTextInput("Trabajo")
        await("Trabajo 000")
        compose.onNodeWithTag("search-results").performScrollToNode(hasText("Trabajo 035"))
        compose.onNodeWithText("Trabajo 035").performClick();await("Trabajo 035")
        compose.onNodeWithContentDescription("Volver a búsqueda").performClick();await("Trabajo 035")
        compose.onNodeWithText("Trabajo 035").assertIsDisplayed()
        compose.onNodeWithText("Trabajo 000").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore();await("Trabajo 035")
        compose.onNodeWithText("Trabajo 035").assertIsDisplayed()
        compose.onNodeWithText("Trabajo 000").assertDoesNotExist()
    }

    @Test fun experimentalDestinationUsesItsOwnHeaderWithoutAnExtraGlobalBar() {
        runBlocking {app.projectRepository.createProject("Raíz")}
        mount();await("Raíz");menu();click("Juego");await("Carrera táctica")
        compose.onAllNodesWithContentDescription("Abrir menú").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Bloquear").assertCountEquals(1)
        compose.onNodeWithText("Seguridad").assertDoesNotExist()
        compose.onNodeWithText("Reglas").assertExists()
        menu();click("Proyectos");await("Raíz")
    }

    @Test fun expandedMetroMapKeepsMenuAndLockAccessible() {
        runBlocking {app.projectRepository.createProject("Raíz")}
        mount();await("Raíz");menu();click("Metro");await("Líneas");click("Líneas")
        compose.waitUntil(15000) {pump();compose.onAllNodesWithTag("metro-expand").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("metro-expand").performClick()
        compose.waitUntil(15000) {pump();compose.onAllNodesWithContentDescription("Contraer mapa").fetchSemanticsNodes().isNotEmpty()}
        compose.onAllNodesWithContentDescription("Abrir menú").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Bloquear").assertCountEquals(1)
        compose.onNodeWithContentDescription("Abrir menú").assertIsDisplayed()
        menu();click("Proyectos");await("Raíz")
    }

}
