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
        if (text == "Capas de cebolla") {
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty() }
            return
        }
        if (text == "Nivel dos" || text == "Tarea final") {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("nodes-list").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nodes-list").performScrollToNode(hasText(text))
        }
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
        compose.onNodeWithText("Nivel uno").performTouchInput { click() }
        awaitText("Nivel dos")
        compose.onNodeWithText("Nivel dos").performTouchInput { click() }
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
    @Test fun drawerProjectsFromNestedLayerReturnsToDashboardAndCloses() {
        openProject()
        compose.onNodeWithText("Nivel uno").performTouchInput { click() }
        awaitText("Nivel dos")
        compose.onNodeWithContentDescription("Abrir menú").performClick()
        compose.onNodeWithText("Proyectos").assertIsSelected().performScrollTo().performClick()
        awaitText("Proyectos")
        compose.onNodeWithTag("navigation-drawer").assertDoesNotExist()
        compose.onNodeWithText("Proyecto navegación").assertExists()
        compose.onNodeWithTag("nodes-list").assertDoesNotExist()
    }

    @Test fun deletedLayerRestoresToNearestExistingAncestor() {
        openProject()
        compose.onNodeWithText("Nivel uno").performTouchInput { click() }
        awaitText("Nivel dos")
        runBlocking { NodeRepository(database).deleteNode(rootId) }
        compose.activityRule.scenario.recreate()
        awaitText("Capas de cebolla")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Volver a la capa anterior").assertDoesNotExist()
    }

    @Test fun deletedProjectRestoresDashboard() {
        openProject()
        runBlocking { ProjectRepository(database.projectDao()).deleteProject(projectId) }
        compose.activityRule.scenario.recreate()
        awaitText("Proyectos")
        compose.onNodeWithContentDescription("Capas de cebolla").assertDoesNotExist()
    }

    @Test fun navigatorExpansionSurvivesClosingAndActivityRecreation() {
        openProject()
        compose.onNodeWithContentDescription("Capas de cebolla").performClick()
        compose.onNodeWithText("Nivel dos").assertDoesNotExist()
        compose.onNodeWithTag("layer-navigator").performScrollToNode(hasTestTag("expand:$rootId"))
        compose.onNodeWithTag("expand:$rootId").performClick()
        compose.onNodeWithTag("layer-navigator").performScrollToNode(hasText("Nivel dos"))
        compose.onNodeWithText("Nivel dos").assertExists()
        compose.onNodeWithText("Cerrar").performClick()
        compose.activityRule.scenario.recreate()
        awaitText("Nivel uno")
        compose.onNodeWithContentDescription("Capas de cebolla").performClick()
        compose.onNodeWithTag("layer-navigator").performScrollToNode(hasText("Nivel dos"))
        compose.onNodeWithText("Nivel dos").assertExists()
        compose.onNodeWithTag("layer-navigator").performScrollToNode(hasTestTag("expand:$rootId"))
        compose.onNodeWithTag("expand:$rootId").performClick()
        compose.onNodeWithText("Nivel dos").assertDoesNotExist()
    }

    @Test fun cancelledPredictiveBackKeepsRestoredLayerAndCommittedBackPopsOnce() {
        openProject()
        compose.onNodeWithText("Nivel uno").performTouchInput { click() }
        awaitText("Nivel dos")
        compose.activityRule.scenario.recreate()
        awaitText("Nivel dos")
        compose.runOnUiThread {
            val dispatcher = compose.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f, 0f, 0f, 0))
            dispatcher.dispatchOnBackProgressed(androidx.activity.BackEventCompat(30f, 0f, 0.5f, 0))
            dispatcher.dispatchOnBackCancelled()
        }
        compose.onNodeWithText("Nivel dos").assertExists()
        compose.runOnUiThread {
            val dispatcher = compose.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f, 0f, 0f, 0))
            dispatcher.dispatchOnBackProgressed(androidx.activity.BackEventCompat(80f, 0f, 1f, 0))
            dispatcher.onBackPressed()
        }
        awaitText("Nivel uno")
        compose.onNodeWithText("Nivel dos").assertDoesNotExist()
    }

    @Test fun vanishedLayerRestoresNavigatorAtProjectRoot() {
        openProject()
        compose.onNodeWithText("Nivel uno").performTouchInput { click() }
        awaitText("Nivel dos")
        compose.onNodeWithContentDescription("Capas de cebolla").performClick()
        runBlocking { NodeRepository(database).deleteNode(rootId) }
        compose.activityRule.scenario.recreate()
        awaitText("Cerrar")
        compose.onNodeWithTag("navigator-project").assertIsSelected()
        compose.onNodeWithText("ACTUAL").assertExists()
        compose.onNodeWithTag("navigator-home").performClick()
        awaitText("Proyectos")
    }

    @Test fun navigatorMarksOnlyCurrentLayerAndJumpsToRootAndHome() {
        openProject()
        compose.onNodeWithText("Nivel uno").performTouchInput { click() }
        awaitText("Nivel dos")
        compose.onNodeWithContentDescription("Capas de cebolla").performClick()
        compose.onNodeWithTag("navigator-node:$rootId").assertIsSelected()
        compose.onAllNodesWithText("ACTUAL").assertCountEquals(1)
        compose.onNodeWithTag("navigator-project").assertIsNotSelected().performClick()
        compose.onNodeWithContentDescription("Volver a la capa anterior").assertDoesNotExist()
        compose.onNodeWithContentDescription("Capas de cebolla").performClick()
        compose.onNodeWithTag("navigator-home").performClick()
        awaitText("Proyectos")
    }

}
