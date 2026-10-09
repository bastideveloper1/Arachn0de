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
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk = [28])
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

    }

    private fun dragAlphaBelowBeta() {
        val from = compose.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.center
        val to = compose.onNodeWithText("Beta").fetchSemanticsNode().boundsInRoot.center
        // A held drag intentionally requests frames forever, even with a stationary finger.
        // Drive that clock explicitly instead of asking Espresso to reach animation idle.
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput {
            down(from)
            advanceEventTime(700)
            moveTo(from)
        }
        compose.mainClock.advanceTimeBy(32)
        repeat(6) { step ->
            compose.onRoot().performTouchInput {
                moveTo(from + (to - from) * ((step + 1) / 6f), delayMillis = 40)
            }
            compose.mainClock.advanceTimeBy(48)
        }
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }
    @Test fun dragPersistsAcrossRecreation() {
        open()
        dragAlphaBelowBeta()
        compose.waitUntil(10_000) {
            runBlocking { app.nodeRepository.getProjectNodes(projectId).map { it.title } == listOf("Beta","Alpha") }
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Beta").fetchSemanticsNodes().isNotEmpty() }
        check(compose.onNodeWithText("Beta").fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.top)
    }
    @Test fun failedDropRollsBackAndCanBeRetried() {
        open()
        app.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_order BEFORE UPDATE OF position ON nodes BEGIN SELECT RAISE(ABORT,'injected'); END")
        dragAlphaBelowBeta()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Entendido").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Entendido").performClick()
        runBlocking { check(app.nodeRepository.getProjectNodes(projectId).map { it.title } == listOf("Alpha","Beta")) }
        app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_order")
        dragAlphaBelowBeta()
        compose.waitUntil(10_000) {
            runBlocking { app.nodeRepository.getProjectNodes(projectId).map { it.title } == listOf("Beta","Alpha") }
        }
    }
    @Test fun completedGroupHasItsOwnDragOrderAndReopenedTaskReturnsToAvailable() {
        open()
        compose.onNodeWithText("Disponibles").assertExists()
        compose.onNodeWithText("Completadas").assertDoesNotExist()
        runBlocking {
            app.nodeRepository.getProjectNodes(projectId).forEach { app.nodeRepository.setCompleted(it.id, true) }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Completadas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Disponibles").assertDoesNotExist()
        dragAlphaBelowBeta()
        compose.waitUntil(10_000) {
            runBlocking { app.nodeRepository.getProjectNodes(projectId).map { it.title } == listOf("Beta", "Alpha") }
        }
        runBlocking {
            val alpha = app.nodeRepository.getProjectNodes(projectId).first { it.title == "Alpha" }
            app.nodeRepository.setCompleted(alpha.id, false)
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Completar: Alpha").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nodes-list").performScrollToIndex(0)
        compose.onNodeWithText("Disponibles").assertExists()
        compose.onNodeWithText("Completadas").assertExists()
        compose.onNodeWithTag("nodes-list").performScrollToIndex(1)
        val before = runBlocking { app.nodeRepository.getProjectNodes(projectId) }
        dragAlphaBelowBeta()
        check(before == runBlocking { app.nodeRepository.getProjectNodes(projectId) })
    }

}
