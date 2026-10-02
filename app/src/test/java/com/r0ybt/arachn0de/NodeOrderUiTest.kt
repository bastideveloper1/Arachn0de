package com.r0ybt.arachn0de

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
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
class NodeOrderUiTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private lateinit var app: Arachn0deApplication
    private lateinit var projectId: String
    @get:Rule val rules: RuleChain=RuleChain.outerRule(object: ExternalResource() {
        override fun before() {
            app=ApplicationProvider.getApplicationContext()
            runBlocking {
                projectId=app.projectRepository.createProject("Order project").id
                app.nodeRepository.createNode(projectId,null,"Alpha")
                app.nodeRepository.createNode(projectId,null,"Beta")
            }
        }
        override fun after() { app.database.close() }
    }).around(compose)
    private fun open() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Order project").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Order project").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Alpha").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("Más opciones")[0].performClick()
    }
    @Test fun menuMovesNodeAndPersistsAcrossRecreation() {
        open()
        compose.onNodeWithText("Mover arriba").assertIsNotEnabled()
        compose.onNodeWithText("Mover abajo").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Mover abajo").fetchSemanticsNodes().isEmpty() }
        runBlocking { check(app.nodeRepository.getProjectNodes(projectId).map { it.title } == listOf("Beta","Alpha")) }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Beta").fetchSemanticsNodes().isNotEmpty() }
        check(compose.onNodeWithText("Beta").fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.top)
    }
    @Test fun failedReorderKeepsMenuForRetry() {
        open()
        app.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_order BEFORE UPDATE OF position ON nodes BEGIN SELECT RAISE(ABORT,'injected'); END")
        compose.onNodeWithText("Mover abajo").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Entendido").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Entendido").performClick()
        compose.onNodeWithText("Mover abajo").assertExists()
        runBlocking { check(app.nodeRepository.getProjectNodes(projectId).map { it.title } == listOf("Alpha","Beta")) }
        app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_order")
        compose.onNodeWithText("Mover abajo").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Mover abajo").fetchSemanticsNodes().isEmpty() }
    }
}
