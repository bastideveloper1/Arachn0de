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
class NodeMoveUiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: Arachn0deApplication
    private lateinit var project: String
    private lateinit var source: String
    private lateinit var child: String
    private lateinit var target: String
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            app = ApplicationProvider.getApplicationContext()
            runBlocking {
                project = app.projectRepository.createProject("Move project").id
                source = app.nodeRepository.createNode(project, null, "Source", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER).id
                child = app.nodeRepository.createNode(project, source, "Child").id
                target = app.nodeRepository.createNode(project, null, "Target layer", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER).id
            }
        }
        override fun after() { app.database.close() }
    }).around(compose)

    private fun openSource() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Move project").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Move project").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("nodes-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Source"))
        compose.onNodeWithText("Source").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("nodes-list").performScrollToIndex(0)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("CAPA 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasContentDescription("Opciones del elemento"))
        compose.onNodeWithContentDescription("Opciones del elemento").performClick()
        compose.onNodeWithText("Mover a…").performClick()
    }

    @Test fun movingOpenLayerExcludesSubtreeAndKeepsCurrentNodeWithNewAncestors() {
        openSource()
        compose.onNodeWithTag("navigator-node:$source").assertDoesNotExist()
        compose.onNodeWithTag("navigator-node:$child").assertDoesNotExist()
        compose.onNodeWithTag("navigator-home").assertDoesNotExist()
        compose.onNodeWithTag("navigator-node:$target").performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getNode(source)?.parentId == target } }
        compose.waitForIdle()
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Child"))
        compose.onNodeWithText("Child").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Source"))
        compose.onNodeWithText("Source").assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Target layer").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun contextualMoveOfLeafToRootPreservesItsIdentity() {
        openSource()
        compose.onNodeWithText("Cancelar").performClick()
        compose.onNodeWithTag("nodes-list").performScrollToNode(hasText("Child"))
        compose.onNodeWithContentDescription("Más opciones").performClick()
        compose.onNode(hasText("Mover a…") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithTag("navigator-project").performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getNode(child)?.parentId == null } }
        check(runBlocking { app.nodeRepository.getNode(source)!!.isStructural && !app.nodeRepository.getNode(source)!!.hasChildren })
        check(runBlocking { app.nodeRepository.getNode(child)!!.title == "Child" })
    }

    @Test fun failedMoveKeepsDestinationPickerAndCanRetry() {
        openSource()
        app.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_move BEFORE UPDATE OF parentId ON nodes BEGIN SELECT RAISE(ABORT,'injected'); END")
        compose.onNodeWithTag("navigator-node:$target").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Entendido").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Entendido").performClick()
        compose.onNodeWithTag("navigator-node:$target").assertExists()
        check(runBlocking { app.nodeRepository.getNode(source)!!.parentId == null })
        app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_move")
        compose.onNodeWithTag("navigator-node:$target").performClick()
        compose.waitUntil(10_000) { runBlocking { app.nodeRepository.getNode(source)?.parentId == target } }
    }
}
