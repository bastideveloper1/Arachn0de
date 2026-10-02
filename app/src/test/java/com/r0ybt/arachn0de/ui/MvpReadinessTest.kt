package com.r0ybt.arachn0de.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MvpReadinessTest {
    @get:Rule val compose = createComposeRule()

    @Test fun emptyNestedLayerKeepsCreateButtonInsideSafeAreaWithLargeText() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), Arachn0deDatabase::class.java).build()
        val projects = ProjectRepository(db.projectDao())
        val nodes = NodeRepository(db)
        val project = runBlocking { projects.createProject("Proyecto de prueba") }
        val parent = runBlocking { nodes.createNode(project.id, null, "Contenedor") }
        val mounted = mutableStateOf(true)
        var pixelsPerDp = 1f
        try {
            compose.setContent {
                pixelsPerDp = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(pixelsPerDp, 1.6f)) {
                    Arachn0deTheme {
                        Box(Modifier.requiredSize(320.dp, 480.dp).testTag("viewport")) {
                            AppSafeArea(WindowInsets(left = 16.dp, top = 52.dp, right = 12.dp, bottom = 24.dp)) {
                                if (mounted.value) ProjectNodeScreen(project, nodes, {})
                            }
                        }
                    }
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("nodes-list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nodes-list").performScrollToKey("node:${parent.id}")
            compose.onNodeWithText("Contenedor").performClick()
            val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
            val menu = compose.onNodeWithContentDescription("Abrir menú").fetchSemanticsNode().boundsInRoot
            val create = compose.onNodeWithText("Nuevo elemento")
            create.assertIsDisplayed().assertIsEnabled()
            val button = create.fetchSemanticsNode().boundsInRoot
            assertTrue(menu.top >= viewport.top + 52 * pixelsPerDp)
            assertTrue(menu.left >= viewport.left + 16 * pixelsPerDp)
            assertTrue(button.right <= viewport.right - 12 * pixelsPerDp)
            assertTrue(button.bottom <= viewport.bottom - 24 * pixelsPerDp)
            create.performClick()
            compose.onNodeWithText("Título").performTextInput("Tarea accesible")
            compose.onNodeWithText("Guardar").assertIsDisplayed().assertIsEnabled()
        } finally {
            compose.runOnIdle { mounted.value = false }
            compose.waitForIdle()
            db.close()
        }
    }

    @Test fun narrowDashboardAndDrawerExposeNoNonfunctionalFeatures() {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), Arachn0deDatabase::class.java).build()
        val repository = ProjectRepository(db.projectDao())
        val mounted = mutableStateOf(true)
        try {
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                    Arachn0deTheme {
                        Box(Modifier.requiredSize(320.dp, 480.dp)) {
                            AppSafeArea(WindowInsets(top = 52.dp, bottom = 24.dp)) {
                                if (mounted.value) ProjectDashboardScreen(repository, emptyList())
                            }
                        }
                    }
                }
            }
            compose.onNodeWithText("Activos").assertDoesNotExist()
            compose.onNodeWithText("Hoy").assertDoesNotExist()
            compose.onNodeWithContentDescription("Buscar").assertDoesNotExist()
            compose.onNodeWithText("Nuevo proyecto").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("Nombre").assertExists()
            compose.onNodeWithText("Cancelar").performClick()
            compose.onNodeWithContentDescription("Abrir menú").performClick()
            compose.onNodeWithText("Personas").assertDoesNotExist()
            compose.onNodeWithText("Configuración").assertDoesNotExist()
            compose.onNodeWithContentDescription("Cerrar menú").assertIsDisplayed().performClick()
        } finally {
            compose.runOnIdle { mounted.value = false }
            compose.waitForIdle()
            db.close()
        }
    }
    @Test fun completionControlAnnouncesActionAndUpdatedState() {
        val completed = mutableStateOf(false)
        compose.setContent {
            Arachn0deTheme {
                val node = com.r0ybt.arachn0de.domain.model.Node(
                    "task", "project", null, "Revisar", "", completed.value, 0, 0, 0, false,
                )
                NodeCard(node, null, false, true, {}, {}, {}, false, false, { _, _ -> }, { completed.value = !completed.value })
            }
        }
        compose.onNodeWithContentDescription("Completar: Revisar").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Marcar pendiente: Revisar")
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Completada"))
    }

}
