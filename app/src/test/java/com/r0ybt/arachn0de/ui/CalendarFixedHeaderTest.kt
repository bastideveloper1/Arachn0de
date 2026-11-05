package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28],qualifiers="w360dp-h850dp")
class CalendarFixedHeaderTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun monthWeekAndDayControlsStayVisibleWhileCardsScroll() {
        val day=CalendarDay(2026,10,21);val now=CalendarDates.labelInstant(day)
        val tasks=(0 until 24).map {Node("task$it","p",null,"Tarea $it","",false,it,now,now,false,dueAt=now)}
        val snapshot=CalendarSnapshot(NodeTreeSnapshot(tasks),TimeZone.getTimeZone("UTC"))
        val project=Project("p","Núcleo","",now,now)
        var opened:String?=null
        compose.setContent {Arachn0deTheme {CalendarScreen(snapshot,listOf(project),emptyMap(),true,now,"UTC",{opened=it.id},{})}}
        for(view in listOf("MONTH","WEEK","DAY")) {
            compose.onNodeWithTag("calendar-view:$view").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithTag("calendar-task:task0").fetchSemanticsNodes().isNotEmpty()}
            val before=compose.onNodeWithTag("calendar-fixed-header").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag("calendar-task:task20"))
            assertEquals(before,compose.onNodeWithTag("calendar-fixed-header").assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
            val viewport=compose.onNodeWithTag("calendar-list").fetchSemanticsNode().boundsInRoot
            assertTrue("Cards have a separate viewport below the calendar",viewport.top>=before.bottom)
            compose.onNodeWithTag(if(view=="MONTH") "calendar-month" else "calendar-period").assertIsDisplayed()
            if(view=="WEEK") compose.onNodeWithTag("calendar-week-strip").assertIsDisplayed()
            compose.onNodeWithTag("calendar-filters").assertIsDisplayed()
            compose.onNodeWithTag("calendar-task:task20").performClick()
            assertEquals("task20",opened)
            compose.onNodeWithTag("calendar-list").performScrollToIndex(0)
        }
        assertEquals(tasks,snapshot.tree.nodes)
    }
}
