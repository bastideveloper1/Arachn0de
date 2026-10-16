package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.DateFormat
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class, sdk=[28], qualifiers="en-rUS")
class CalendarWeekStripTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val zone=TimeZone.getTimeZone("UTC")
    private val today=CalendarDay(2026,12,31)
    private fun task(id:String,day:CalendarDay,completed:Boolean=false)=Node(id,"p",null,id,"",completed,0,0,0,false,
        dueAt=CalendarDates.labelInstant(day))
    private val nodes=listOf(task("jueves",today),task("viernes",CalendarDay(2027,1,1),true))
    private val snapshot=CalendarSnapshot(NodeTreeSnapshot(nodes),zone)
    private fun awaitTag(tag:String)=compose.waitUntil(10000) {compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun week() {
        compose.setContent {Arachn0deTheme {CalendarScreen(snapshot,listOf(Project("p","Núcleo","",0L,0L)),emptyMap(),true,
            CalendarDates.labelInstant(today),zone.id,{}, {})}}
        compose.onNodeWithTag("calendar-view:WEEK").performClick()
        awaitTag("calendar-week-day:2026-12-28")
    }
    @Test fun mondayToSundaySelectionCrossesYearAndNavigationKeepsDayWhileMonthAndTodayRemainAvailable() {
        week()
        compose.onNodeWithTag("calendar-week-day:2026-12-31").assertIsSelected()
        compose.onNodeWithTag("calendar-week-day:2027-1-3").assertExists()
        awaitTag("calendar-task:jueves")
        compose.onNodeWithTag("calendar-task:viernes").assertDoesNotExist()
        compose.onNodeWithTag("calendar-week-day:2027-1-1").performClick()
        awaitTag("calendar-task:viernes")
        compose.onNodeWithTag("calendar-task:jueves").assertDoesNotExist()
        compose.onNodeWithTag("calendar-week-day:2027-1-2").performClick()
        compose.onNodeWithText("Sin tareas para este día").assertExists()
        compose.onNodeWithContentDescription("Semana siguiente").performClick()
        compose.onNodeWithTag("calendar-week-day:2027-1-9").assertIsSelected()
        compose.onNodeWithContentDescription("Semana anterior").performClick()
        compose.onNodeWithTag("calendar-week-day:2027-1-2").assertIsSelected()
        compose.onNodeWithTag("calendar-current-period").performClick()
        compose.onNodeWithTag("calendar-week-day:2026-12-31").assertIsSelected()
        compose.onNodeWithTag("calendar-view:MONTH").performClick()
        awaitTag("calendar-month")
        compose.onNodeWithTag("calendar-day:2026-12-31").assertIsSelected()
        compose.onNodeWithTag("calendar-week-strip").assertDoesNotExist()
        compose.onNodeWithTag("calendar-view:DAY").performClick()
        awaitTag("calendar-task:jueves")
        compose.onNodeWithTag("calendar-week-strip").assertDoesNotExist()
    }
    @Test fun completionFilterRemovesDotButKeepsOtherDaysAndSelection() {
        week()
        compose.onAllNodesWithTag("calendar-task-dot",useUnmergedTree=true).assertCountEquals(2)
        compose.onNodeWithTag("calendar-filters").performClick()
        compose.onNodeWithTag("calendar-status-filter").performScrollTo().performClick()
        compose.onNodeWithText("Pendientes",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Listo").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("calendar-task-dot",useUnmergedTree=true).fetchSemanticsNodes().size==1}
        compose.onNodeWithTag("calendar-week-day:2026-12-31").assertIsSelected()
        compose.onNodeWithTag("calendar-week-day:2027-1-1").performClick()
        compose.onNodeWithTag("calendar-task:viernes").assertDoesNotExist()
        compose.onNodeWithText("No hay elementos que coincidan con los filtros.").assertExists()
    }
    @Test fun narrowStripWithDoubleFontKeepsAllDaysReachableAndUsesFilteredCounts() {
        val days=List(7) {TemporalRanges.shift(CalendarDay(2026,12,28),it)}
        val filtered=FilteredCalendar(snapshot,NodeFilter(personId="roy"),mapOf("viernes" to setOf("roy")))
        var selected=today
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,2f)) {Arachn0deTheme {
            androidx.compose.foundation.layout.Box(Modifier.width(280.dp)) {
                CalendarWeekStrip(days,today,today,Locale.US,filtered,DateFormat.getDateInstance(),{selected=it})
            }
        }}}
        compose.onAllNodesWithTag("calendar-task-dot",useUnmergedTree=true).assertCountEquals(1)
        compose.onNodeWithTag("calendar-week-day:2027-1-3").performScrollTo().performClick()
        assertEquals(CalendarDay(2027,1,3),selected)
        compose.onNodeWithTag("calendar-week-day:2026-12-28").performScrollTo().performClick()
        assertEquals(CalendarDay(2026,12,28),selected)
    }
}
