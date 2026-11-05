package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.Arachn0deApplication
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=com.r0ybt.arachn0de.security.LegacyUiTestApplication::class,sdk=[28],qualifiers="w400dp-h850dp")
class TaskEffortUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun await(block:()->Boolean)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame();block()
    }
    @Test fun miniOpensTaskAndEditingPanelStillPersistsAllLevels() {
        val app=compose.activity.application as Arachn0deApplication
        val project=runBlocking {app.projectRepository.createProject("Esfuerzo")}
        val original=runBlocking {app.nodeRepository.createNode(project.id,null,"Tarea")}
        var editing by mutableStateOf(false);var opened=0;var showSort by mutableStateOf(false);var sort by mutableStateOf(SortConfiguration());var applied=0
        compose.setContent {Arachn0deTheme {
            if(showSort)NodeSortMenu(sort,{sort=it},{},onApply={done->applied++;done(null)})
            else {
                val snapshot by remember {app.nodeRepository.observeProjectState(project.id)}.collectAsState(NodeTreeSnapshot(listOf(original)))
                val task=snapshot.nodesById[original.id] ?: original
                Column {NodeCard(task,null,false,true,{opened++},{},{},false,false,{_,done->done()},{})}
                if(editing)TaskEffortPanel(task.id,task.effort,app.nodeRepository) {editing=false}
            }
        }}
        val mini=compose.onNodeWithTag("task-effort:${original.id}",useUnmergedTree=true)
        mini.assertContentDescriptionEquals("Esfuerzo: Sin definir")
        mini.performTouchInput {click(center)}
        compose.onNodeWithTag("effort-panel").assertDoesNotExist()
        assertEquals(1,opened)
        compose.runOnIdle {editing=true}
        compose.onNodeWithTag("effort-panel").assertIsDisplayed()
        for(level in 1..5) {
            if(level>1)compose.runOnIdle {editing=true}
            compose.onNodeWithTag("effort-level:$level").performClick()
            await {runBlocking {app.nodeRepository.getNode(original.id)!!.effort==level} && compose.onAllNodesWithTag("effort-panel").fetchSemanticsNodes().isEmpty()}
            mini.assertContentDescriptionEquals("Esfuerzo: ${effortLabel(level)}")
            compose.onAllNodes(hasText(level.toString()) and hasAnyAncestor(hasTestTag("task-effort:${original.id}")),useUnmergedTree=true).assertCountEquals(1)
            mini.assertHasNoClickAction()
        }
        compose.runOnIdle {editing=true}
        compose.onNodeWithTag("effort-level:5").performClick()
        await {runBlocking {app.nodeRepository.getNode(original.id)!!.effort==0} && compose.onAllNodesWithTag("effort-panel").fetchSemanticsNodes().isEmpty()}
        compose.runOnIdle {editing=true}
        compose.onNodeWithTag("effort-level:3").performClick()
        await {runBlocking {app.nodeRepository.getNode(original.id)!!.effort==3} && compose.onAllNodesWithTag("effort-panel").fetchSemanticsNodes().isEmpty()}
        compose.runOnIdle {editing=true}
        compose.onNodeWithText("3/5 · Media").assertExists()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss) {it()}
        await {compose.onAllNodesWithTag("effort-panel").fetchSemanticsNodes().isEmpty()}
        compose.runOnIdle {showSort=true}
        compose.onNodeWithTag("sort-criteria").performScrollToIndex(sort.rules.indexOfFirst {it.criterion==SortCriterion.EFFORT})
        compose.onNodeWithTag("sort-check:EFFORT").performClick()
        compose.runOnIdle {assertTrue(sort.rules.single {it.criterion==SortCriterion.EFFORT}.enabled);assertEquals(0,applied)}
        compose.onNodeWithTag("sort-rule:EFFORT").performClick()
        compose.runOnIdle {assertTrue(sort.rules.single {it.criterion==SortCriterion.EFFORT}.reverse);assertEquals(0,applied)}
        compose.onNodeWithTag("sort-apply").performClick()
        compose.runOnIdle {assertEquals(1,applied)}
    }
}
