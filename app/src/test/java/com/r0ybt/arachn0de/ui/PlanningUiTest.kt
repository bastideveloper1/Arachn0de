package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.data.repository.ManagementMethodology
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w411dp-h891dp")
class PlanningUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=compose.activity.application as Arachn0deApplication
    @After fun close() {app.database.close()}
    private fun awaitText(text:String)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun click(text:String) {
        awaitText(text);val node=compose.onNodeWithText(text)
        if(runCatching {node.assertIsDisplayed()}.isFailure) node.performScrollTo()
        node.performClick()
        if(text=="Guardar" || text=="Confirmar eliminación") compose.waitUntil(15000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }
    private fun scrollTo(matcher:SemanticsMatcher) {
        compose.waitUntil(15000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
            runCatching {compose.onNodeWithTag("planning-list").performScrollToNode(matcher)}.isSuccess
        }
    }
    private fun input(label:String,value:String) {compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)}
    @Test fun appNavigationPartialCharterAndExplicitValidationDeletionAndReactivation() {
        runBlocking {app.projectRepository.createProject("Plan candidate")}
        compose.setContent {Arachn0deTheme {AppRoot(app.projectRepository,app.nodeRepository)}}
        click("Plan candidate");click("Gestión del proyecto · Normal")
        compose.onNodeWithText("Objetivos").assertDoesNotExist()
        click("Activar gestión");click("Gestión del proyecto · Gestionado");click("Objetivos")
        click("Editar acta");input("Propósito","Partial purpose");click("Guardar")
        awaitText("Partial purpose");click("Crear objetivo");input("Título","Objective A");input("Descripción","Objective description");click("Guardar")
        click("Ver detalle");awaitText("Primero deben definirse criterios de aceptación.")
        click("Crear criterio");input("Criterio verificable","Verified output");click("Guardar")
        awaitText("Con criterios pendientes")
        compose.onNodeWithContentDescription("Validar criterio: Verified output").performScrollTo().performClick()
        awaitText("No quedan criterios pendientes.")
        compose.onNodeWithContentDescription("Revocar criterio: Verified output").performScrollTo().performClick()
        awaitText("Con criterios pendientes")
        compose.onNodeWithContentDescription("Eliminar criterio: Verified output").performScrollTo().performClick()
        click("Cancelar");runBlocking {assertEquals(1,app.database.planningDao().criteria().size)}
        compose.onNodeWithContentDescription("Eliminar criterio: Verified output").performScrollTo().performClick()
        click("Confirmar eliminación");awaitText("Primero deben definirse criterios de aceptación.")
        click("Volver a objetivos")
        compose.onNodeWithContentDescription("Eliminar objetivo: Objective A").performScrollTo().performClick()
        click("Cancelar");runBlocking {assertEquals(1,app.database.planningDao().objectives().size)}
        click("Cerrar planificación");click("Configuración");click("Desactivar gestión")
        click("Gestión del proyecto · Normal");compose.onNodeWithText("Objetivos").assertDoesNotExist()
        click("Activar gestión");click("Gestión del proyecto · Gestionado");click("Objetivos")
        awaitText("Partial purpose");awaitText("Objective A")
        compose.onNodeWithContentDescription("Eliminar objetivo: Objective A").performScrollTo().performClick()
        click("Confirmar eliminación");awaitText("No hay objetivos registrados.")
        runBlocking {assertTrue(app.database.planningDao().criteria().isEmpty());assertEquals("Partial purpose",app.database.planningDao().charters().single().purpose)}
    }
    @Test fun orderingAndLongTextRemainScrollableAtLargeFont() {
        val id=runBlocking {
            val project=app.projectRepository.createProject("Long text project")
            val id=app.nodeRepository.management.configure(project.id,null,true,ManagementMethodology.AGILE)!!.id
            val repo=app.nodeRepository.management.planning
            repo.createObjective(id,"First objective", "A long description ".repeat(40))
            repo.createObjective(id,"Second objective")
            id
        }
        compose.setContent {Arachn0deTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.8f)) {
                PlanningDialog(id,"Live project title","Live project description ".repeat(25),app.nodeRepository.management.planning,{})
            }
        }}
        awaitText("Live project title")
        scrollTo(hasContentDescription("Subir objetivo: Second objective"))
        compose.onNodeWithContentDescription("Subir objetivo: Second objective").performScrollTo().performClick()
        compose.waitUntil(15000) {runBlocking {app.database.planningDao().objectives().firstOrNull()?.title=="Second objective"}}
        scrollTo(hasText("First objective"))
        compose.onNodeWithText("First objective",useUnmergedTree=true).performScrollTo().performClick()
        click("Crear criterio");input("Criterio verificable","A long criterion ".repeat(50));click("Guardar")
        awaitText("Con criterios pendientes")
        scrollTo(hasContentDescription("Validar criterio: ${"A long criterion ".repeat(50).trim()}"))
        compose.onNodeWithContentDescription("Validar criterio: ${"A long criterion ".repeat(50).trim()}").performScrollTo().assertIsDisplayed().performClick()
        scrollTo(hasText("Todos los criterios validados"));awaitText("Todos los criterios validados")
        compose.onNodeWithText("Cerrar planificación").assertIsDisplayed()
        runBlocking {assertTrue(app.database.planningDao().criteria().single().validated)}
    }
}
