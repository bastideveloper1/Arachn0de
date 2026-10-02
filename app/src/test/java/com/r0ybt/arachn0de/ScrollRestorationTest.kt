package com.r0ybt.arachn0de

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScrollRestorationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: Arachn0deApplication
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            app = ApplicationProvider.getApplicationContext()
            runBlocking {
                val project = ProjectRepository(app.database.projectDao()) { 1L }.createProject("Proyecto scroll")
                repeat(35) { i ->
                    ProjectRepository(app.database.projectDao()) { 100L + i }.createProject("Proyecto $i")
                    val node = app.nodeRepository.createNode(project.id, null, "Raíz $i")
                    if (i == 20) repeat(35) { j -> app.nodeRepository.createNode(project.id, node.id, "Hijo $j") }
                }
            }
        }
        override fun after() { app.database.close() }
    }).around(compose)

    private fun awaitTag(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun openProject() {
        awaitTag("projects-list")
        compose.onNodeWithText("Proyecto scroll").performClick()
        awaitTag("nodes-list")
    }
    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun eachLayerKeepsItsScrollAfterRecreationAndBack() {
        openProject()
        compose.onNodeWithTag("nodes-list").performScrollToIndex(21)
        compose.onNodeWithText("Raíz 20").performClick()
        compose.onNodeWithTag("nodes-list").performScrollToIndex(21)
        compose.onNodeWithText("Hijo 20").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        awaitTag("nodes-list")
        compose.onNodeWithText("Hijo 20").assertIsDisplayed()
        back()
        compose.onNodeWithText("Raíz 20").assertIsDisplayed()
    }

    @Test fun dashboardRetainsScrollAcrossProjectVisitAndRecreation() {
        awaitTag("projects-list")
        compose.onNodeWithTag("projects-list").performScrollToIndex(21)
        compose.onNodeWithText("Proyecto 20").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Volver a proyectos").fetchSemanticsNodes().isNotEmpty() }
        back()
        awaitTag("projects-list")
        compose.onNodeWithText("Proyecto 20").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        awaitTag("projects-list")
        compose.onNodeWithText("Proyecto 20").assertIsDisplayed()
    }

    @Test fun mapRetainsScrollWhileClosedAndAcrossRecreation() {
        openProject()
        compose.onNodeWithText("Mapa de capas").performClick()
        compose.onNodeWithTag("layer-map").performScrollToIndex(20)
        compose.onNodeWithText("Raíz 20").assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performClick()
        compose.activityRule.scenario.recreate()
        awaitTag("nodes-list")
        compose.onNodeWithText("Mapa de capas").performClick()
        compose.onNodeWithText("Raíz 20").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        awaitTag("layer-map")
        compose.onNodeWithText("Raíz 20").assertIsDisplayed()
    }
}
