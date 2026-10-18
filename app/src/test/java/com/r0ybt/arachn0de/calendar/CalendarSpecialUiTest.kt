package com.r0ybt.arachn0de.calendar

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.CalendarScreen
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*

@RunWith(RobolectricTestRunner::class) @Config(sdk=[28],application=Application::class)
class CalendarSpecialUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun scroll(tag:String) {compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag(tag))}
    @Test fun togglesAndDetailsLeaveRealTasksEventsAndTheirMonthIndicatorsIntact()=runBlocking<Unit> {
        val context=compose.activity
        val db=Room.inMemoryDatabaseBuilder(context,Arachn0deDatabase::class.java).build()
        val nodes=NodeRepository(db);val projects=ProjectRepository(db.projectDao(),db);val events=CalendarEventRepository(db)
        val day=CalendarDay(2026,7,16);val now=CalendarDates.labelInstant(day)
        val project=projects.createProject("Personal")
        val task=nodes.createNode(project.id,null,"Tarea personal",dueAt=now)
        val event=CalendarEventEntity(UUID.randomUUID().toString(),"Evento personal","2026-07-16",null,"Texto original")
        events.save(event,emptySet(),true)
        val original=nodes.getProjectNodes(project.id)
        val tree=NodeTreeSnapshot(original)
        val storage=context.getSharedPreferences("calendar-special-ui",0).apply {edit().clear().commit()}
        val preferences=CalendarSpecialPreferences(storage)
        val catalog=CalendarSpecialDates.load(context)
        val rows=catalog.on(day,CalendarSpecialVisibility(true,true,true))
        val holiday=rows.single {it.kind==SpecialDateKind.HOLIDAY};val saint=rows.single {it.kind==SpecialDateKind.SAINT}
        try {
            compose.setContent {Arachn0deTheme {CalendarScreen(CalendarSnapshot(tree,TimeZone.getTimeZone("UTC")),listOf(project),emptyMap(),true,now,"UTC",{},{},eventRepository=events,specialPreferences=preferences,specialCatalog=catalog)}}
            compose.waitUntil(10000) {compose.onAllNodesWithTag("calendar-task-dot",true).fetchSemanticsNodes().isNotEmpty()}
            compose.onAllNodesWithTag("calendar-task-dot",true).assertCountEquals(1)
            compose.onAllNodesWithTag("calendar-event-dash",true).assertCountEquals(1)
            scroll("calendar-special:${holiday.id}")
            compose.onNodeWithTag("calendar-filters").performClick()
            compose.onNodeWithTag("calendar-special-toggle:Santoral").performScrollTo().performClick()
            compose.onNodeWithTag("calendar-special-toggle:Fechas conmemorativas").performScrollTo().performClick()
            compose.onNodeWithText("Listo").performClick()
            scroll("calendar-special:${saint.id}")
            compose.onNodeWithTag("calendar-special:${saint.id}").performClick()
            compose.onNodeWithText("Fuente: ${saint.source.name}").assertExists()
            compose.onNodeWithText("Cerrar").performClick()
            scroll("calendar-task:${task.id}");compose.onNodeWithTag("calendar-task:${task.id}").assertExists()
            scroll("calendar-event:${event.id}");compose.onNodeWithTag("calendar-event:${event.id}").assertExists()
            compose.onNodeWithTag("calendar-filters").performClick()
            compose.onNodeWithTag("calendar-special-toggle:Feriados de Chile").performScrollTo().performClick()
            compose.onNodeWithTag("calendar-special-toggle:Santoral").performScrollTo().performClick()
            compose.onNodeWithTag("calendar-special-toggle:Fechas conmemorativas").performScrollTo().performClick()
            compose.onNodeWithText("Listo").performClick()
            compose.runOnIdle {assertEquals(CalendarSpecialVisibility(false,false,false),preferences.settings)}
            scroll("calendar-task:${task.id}");scroll("calendar-event:${event.id}")
            assertEquals(original,nodes.getProjectNodes(project.id));assertEquals(listOf(event),db.calendarEventDao().all())
            assertEquals(preferences.settings,CalendarSpecialPreferences(storage).settings)
        } finally {
            compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
            db.close()
        }
    }
}
