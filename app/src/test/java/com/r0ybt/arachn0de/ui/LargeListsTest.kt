package com.r0ybt.arachn0de.ui

import androidx.compose.runtime.*
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.NodeEntity
import com.r0ybt.arachn0de.data.repository.ProjectRepository
import com.r0ybt.arachn0de.data.repository.NodeRepository
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.r0ybt.arachn0de.domain.model.Node
import com.r0ybt.arachn0de.domain.model.Project
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LargeListsTest {
    @get:Rule val compose = createComposeRule()
    private fun node(id: String, parent: String? = null, order: Int = 0) =
        Node(id, "project", parent, id, "", false, order, 0, 0, false)

    @Test fun navigatorOnlyComposesViewportAndNavigatesToDistantNode() {
        val nodes = (0 until 5_000).flatMap { listOf(node("node$it", order = it), node("task$it", "node$it")) }
        var selected: String? = null
        compose.setContent { Arachn0deTheme { LayerNavigator(nodes, emptyList(), {}, null, { selected = it }, "Project", {}, {}) } }
        compose.onNodeWithText("node4999").assertDoesNotExist()
        compose.onNodeWithTag("layer-navigator").performScrollToKey("node:node4999")
        compose.onNodeWithText("node4999").performClick()
        compose.runOnIdle { assertEquals("node4999", selected) }
    }

    @Test fun expansionButtonDoesNotNavigateAndCollapseHidesChildren() {
        val nodes = listOf(node("parent"), node("child", "parent"), node("task", "child"), node("rootTask"))
        var selected: String? = null
        compose.setContent {
            var expanded by remember { mutableStateOf(emptyList<String>()) }
            Arachn0deTheme {
                LayerNavigator(nodes, expanded, { id -> expanded = if (id in expanded) expanded - id else expanded + id }, null, { selected = it }, "Project", {}, {})
            }
        }
        compose.onNodeWithText("child").assertDoesNotExist()
        compose.onNodeWithTag("expand:parent").performClick()
        compose.onNodeWithText("child").assertExists()
        compose.onNodeWithText("rootTask").assertDoesNotExist()
        compose.onNodeWithTag("expand:child").performClick()
        compose.onNodeWithText("task").assertDoesNotExist()
        compose.runOnIdle { assertNull(selected) }
        compose.onNodeWithTag("expand:parent").performClick()
        compose.onNodeWithText("child").assertDoesNotExist()
    }

    @Test fun projectListOnlyComposesViewportAndRetainsPersistentKeys() {
        val projects = (0 until 3_000).map { Project("p$it", "Project $it", "", 0, 0) }
        var selected: String? = null
        compose.setContent { Arachn0deTheme { ProjectList(projects, {}, {}, {}, { selected = it.id }) } }
        compose.onNodeWithText("Project 2999").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2_999)
        compose.onNodeWithText("Project 2999").performClick()
        compose.runOnIdle { assertEquals("p2999", selected) }
    }
    @Test fun deepNavigatorComposesOnlyVisibleAncestorsAndNavigatesById() {
        val nodes = (0 until 5_000).map { node("ancestor$it", if (it == 0) null else "ancestor${it - 1}") } + node("lastTask", "ancestor4999")
        var selected: String? = null
        compose.setContent { Arachn0deTheme { LayerNavigator(nodes, nodes.map { it.id }, {}, null, { selected = it }, "Project", {}, {}) } }
        compose.onNodeWithText("ancestor4999").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToKey("node:ancestor4999")
        compose.onNodeWithText("ancestor4999").performClick()
        compose.runOnIdle { assertEquals("ancestor4999", selected) }
    }

    @Test fun largeTaskLayerScrollsWithoutNavigatingIntoLeaf() {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), Arachn0deDatabase::class.java).build()
        val project = runBlocking {
            val project = ProjectRepository(database.projectDao()).createProject("Large project")
            database.withTransaction {
                repeat(2_000) { i ->
                    database.nodeDao().insert(NodeEntity("task$i", project.id, null, "Task $i", "", false, i, 0, 0))
                }
            }
            project
        }
        val visible = mutableStateOf(true)
        val repository = NodeRepository(database)
        try {
            compose.setContent { if (visible.value) Arachn0deTheme { ProjectNodeScreen(project, repository, {}) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Task 0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Task 1999").assertDoesNotExist()
            compose.onNode(hasScrollToIndexAction()).performScrollToKey("node:task1999")
            compose.onNodeWithText("Task 1999").performTouchInput { click() }
            compose.onNodeWithContentDescription("Volver a la capa anterior").assertDoesNotExist()
        } finally {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            database.close()
        }
    }

}
