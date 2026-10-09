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
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk = [28])
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
                    val node = app.nodeRepository.createNode(project.id, null, "Raíz $i", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
                    if (i != 20) app.nodeRepository.createNode(project.id, node.id, "Tarea")
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
        compose.onNodeWithTag("nodes-list").performScrollToIndex(22)
        compose.onNodeWithText("Raíz 20").performClick()
        compose.onNodeWithTag("nodes-list").performScrollToIndex(22)
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
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Capas de cebolla").fetchSemanticsNodes().isNotEmpty() }
        back()
        awaitTag("projects-list")
        compose.onNodeWithText("Proyecto 20").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        awaitTag("projects-list")
        compose.onNodeWithText("Proyecto 20").assertIsDisplayed()
    }

    @Test fun mapRetainsScrollWhileClosedAndAcrossRecreation() {
        openProject()
        compose.onNodeWithContentDescription("Capas de cebolla").performClick()
        compose.onNodeWithTag("layer-navigator").performScrollToIndex(22)
        compose.onNodeWithText("Raíz 20").assertIsDisplayed()
        compose.onNodeWithText("Cerrar").performClick()
        compose.activityRule.scenario.recreate()
        awaitTag("nodes-list")
        compose.onNodeWithContentDescription("Capas de cebolla").performClick()
        compose.onNodeWithText("Raíz 20").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        awaitTag("layer-navigator")
        compose.onNodeWithText("Raíz 20").assertIsDisplayed()
    }
}
