package com.r0ybt.arachn0de

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.NodeRepository
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import kotlinx.coroutines.runBlocking
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NavigationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var database: Arachn0deDatabase
    private lateinit var projectId: String
    private lateinit var rootId: String

    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() { seed() }
        override fun after() { database.close() }
    }).around(compose)

    private fun seed() = runBlocking {
        database = Arachn0deDatabase.create(ApplicationProvider.getApplicationContext<Context>())
        val projects = ProjectRepository(database.projectDao())
        projectId = projects.createProject("Proyecto navegación").id
        val nodes = NodeRepository(database)
        val root = nodes.createNode(projectId, parentId = null, title = "Nivel uno")
        rootId = root.id
        val child = nodes.createNode(projectId, title = "Nivel dos", parentId = root.id)
        nodes.createNode(projectId, title = "Tarea final", parentId = child.id)
    }

    private fun awaitText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun openProject() {
        awaitText("Proyecto navegación")
        compose.onNodeWithText("Proyecto navegación").performClick()
        awaitText("Nivel uno")
    }
    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun backFromRootReturnsToProjects() {
        openProject()
        back()
        awaitText("Proyectos")
        compose.onNodeWithText("Nivel uno").assertDoesNotExist()
    }

    @Test fun recreationRestoresNestedLayerAndBackTraversesParents() {
        openProject()
        compose.onNodeWithText("Nivel uno").performClick()
        awaitText("Nivel dos")
        compose.onNodeWithText("Nivel dos").performClick()
        awaitText("Tarea final")
        compose.activityRule.scenario.recreate()
        awaitText("Tarea final")
        back()
        awaitText("Nivel dos")
        back()
        awaitText("Nivel uno")
        back()
        awaitText("Proyectos")
    }

    @Test fun recreationAtRootPreservesProject() {
        openProject()
        compose.activityRule.scenario.recreate()
        awaitText("Nivel uno")
    }

    @Test fun drawerConsumesBackBeforeLeavingProject() {
        openProject()
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithContentDescription("Cerrar menú").assertExists()
        back()
        compose.onNodeWithContentDescription("Cerrar menú").assertDoesNotExist()
        compose.onNodeWithText("Nivel uno").assertExists()
    }
    @Test fun deletedLayerRestoresToNearestExistingAncestor() {
        openProject()
        compose.onNodeWithText("Nivel uno").performClick()
        awaitText("Nivel dos")
        runBlocking { NodeRepository(database).deleteNode(rootId) }
        compose.activityRule.scenario.recreate()
        awaitText("Volver a proyectos")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Volver a la capa anterior").assertDoesNotExist()
    }

    @Test fun deletedProjectRestoresDashboard() {
        openProject()
        runBlocking { ProjectRepository(database.projectDao()).deleteProject(projectId) }
        compose.activityRule.scenario.recreate()
        awaitText("Proyectos")
        compose.onNodeWithText("Volver a proyectos").assertDoesNotExist()
    }

    @Test fun mapExpansionSurvivesClosingAndActivityRecreation() {
        openProject()
        compose.onNodeWithText("Mapa de capas").performClick()
        compose.onNodeWithText("Nivel dos").assertDoesNotExist()
        compose.onNodeWithTag("expand:$rootId").performClick()
        compose.onNodeWithText("Nivel dos").assertExists()
        compose.onNodeWithText("Cerrar").performClick()
        compose.activityRule.scenario.recreate()
        awaitText("Nivel uno")
        compose.onNodeWithText("Mapa de capas").performClick()
        compose.onNodeWithText("Nivel dos").assertExists()
        compose.onNodeWithTag("expand:$rootId").performClick()
        compose.onNodeWithText("Nivel dos").assertDoesNotExist()
    }

}
