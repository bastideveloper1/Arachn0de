package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28],qualifiers="en-rUS")
class CalendarEventsUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:CalendarEventRepository
    private val day=CalendarDay(2026,12,31)
    private val person=Person("roy","Roy",null)
    private val task=Node("task","p",null,"Tarea original","",false,0,0,0,false,dueAt=CalendarDates.labelInstant(day))
    private var opened:String?=null
    @Before fun start()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Application>();context.deleteDatabase("arachn0de.db")
        db=Arachn0deDatabase.create(context);repo=CalendarEventRepository(db);db.personDao().insert(PersonEntity("roy","Roy",null))
    }
    @After fun close() {compose.runOnUiThread {
        val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
        root.removeAllViews()
    };db.close()}
    private fun awaitTag(tag:String)=compose.waitUntil(10000) {compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()}
    private fun scroll(tag:String) {compose.onNodeWithTag("calendar-list").performScrollToNode(hasTestTag(tag));awaitTag(tag)}
    private fun mount(restoration:StateRestorationTester?=null,narrow:Boolean=false,navigation:GlobalNavigation?=null) {
        val content:@Composable ()->Unit={
            CompositionLocalProvider(LocalDensity provides Density(1f,if(narrow) 2f else 1f),LocalGlobalNavigation provides navigation) {Arachn0deTheme {
                Box(if(narrow) Modifier.width(280.dp).height(1100.dp) else Modifier.fillMaxSize()) {
                    CalendarScreen(CalendarSnapshot(NodeTreeSnapshot(listOf(task)),TimeZone.getTimeZone("UTC")),listOf(Project("p","Núcleo","",0L,0L)),emptyMap(),true,
                        CalendarDates.labelInstant(day),"UTC",{opened=it.id},{},people=listOf(person),eventRepository=repo)
                }
            }}
        }
        if(restoration==null) compose.setContent(content) else restoration.setContent(content)
    }
    @Test fun createRestoreDraftCancelEditAndConfirmedDeleteUseExistingCalendar() {
        val restoration=StateRestorationTester(compose);mount(restoration)
        scroll("calendar-new-event");compose.onNodeWithTag("calendar-new-event").performClick()
        compose.onNodeWithTag("event-date").assert(hasText("Fecha"))
        if(compose.onAllNodesWithTag("event-option:title").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("event-option:title").performClick()
        compose.onNodeWithTag("event-title").performTextReplacement("Cita médica")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("event-title").assert(hasText("Cita médica"))
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("event-cancel").assertIsDisplayed().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) {it()}
        compose.onNodeWithTag("event-discard-confirm").performClick()
        assertTrue(runBlocking {db.calendarEventDao().all().isEmpty()})
        scroll("calendar-new-event");compose.onNodeWithTag("calendar-new-event").performClick()
        if(compose.onAllNodesWithTag("event-option:title").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("event-option:title").performClick()
        compose.onNodeWithTag("event-title").performTextReplacement("Cita médica")
        compose.onNodeWithTag("event-title-confirm").performClick()
        compose.onNodeWithTag("event-option:time").performClick()
        compose.onNodeWithTag("event-all-day").assertIsOn()
        compose.onNodeWithTag("event-time-confirm").performClick()
        compose.onNodeWithTag("event-option:people").performScrollTo().performClick()
        compose.onNodeWithTag("person-choice:roy").performClick();compose.onNodeWithTag("event-people-confirm").performClick()
        compose.onNodeWithTag("event-save").performClick()
        compose.waitUntil(10000) {runBlocking {db.calendarEventDao().all().size==1}}
        val event=runBlocking {db.calendarEventDao().all().single()}
        assertNull(event.minute);assertEquals(listOf(CalendarEventPersonEntity(event.id,"roy")),runBlocking {db.calendarEventDao().people()})
        scroll("calendar-event:${event.id}");compose.onNodeWithTag("calendar-event:${event.id}").performClick()
        if(compose.onAllNodesWithTag("event-option:title").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("event-option:title").performClick()
        compose.onNodeWithTag("event-title").performTextReplacement("Reunión")
        compose.onNodeWithTag("event-title-confirm").performClick()
        compose.onNodeWithTag("event-option:time").performClick()
        compose.onNodeWithTag("event-all-day").performClick()
        compose.onNodeWithTag("event-time-confirm").performClick();compose.onNodeWithTag("event-save").performClick()
        compose.waitUntil(10000) {runBlocking {db.calendarEventDao().all().single().title=="Reunión"}}
        assertEquals(720,runBlocking {db.calendarEventDao().all().single().minute})
        scroll("calendar-event:${event.id}");compose.onNodeWithTag("calendar-event:${event.id}").performClick()
        compose.onNodeWithTag("event-delete").performScrollTo().performClick()
        compose.onNodeWithTag("event-confirm-delete").assertExists()
        assertEquals(1,runBlocking {db.calendarEventDao().all().size})
        compose.onNodeWithTag("event-confirm-delete").performClick()
        compose.waitUntil(10000) {runBlocking {db.calendarEventDao().all().isEmpty()}}
        assertTrue(runBlocking {db.calendarEventDao().people().isEmpty()})
    }
    @Test fun realMixedIndicatorsAndTaskOnlyFiltersLeaveEventsVisibleInMonthWeekAndToday() {
        val event=CalendarEventEntity(UUID.randomUUID().toString(),"Celebración","2026-12-31",null,"")
        runBlocking {repo.save(event,setOf("roy"),true)};mount()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("calendar-event-dash",true).fetchSemanticsNodes().size==1}
        compose.onAllNodesWithTag("calendar-task-dot",true).assertCountEquals(1)
        compose.onNodeWithTag("calendar-view:WEEK").performClick();awaitTag("calendar-week-strip")
        compose.onAllNodesWithTag("calendar-event-dash",true).assertCountEquals(1)
        compose.onAllNodesWithTag("calendar-task-dot",true).assertCountEquals(1)
        scroll("calendar-task:task");compose.onNodeWithTag("calendar-task:task").performClick();assertEquals("task",opened)
        compose.onNodeWithTag("calendar-filters").performClick()
        compose.onNodeWithTag("calendar-status-filter").performScrollTo().performClick()
        compose.onNodeWithText("Completados",useUnmergedTree=true).performClick();compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithTag("calendar-list").performScrollToIndex(0)
        compose.waitUntil(10000) {compose.onAllNodesWithTag("calendar-task-dot",true).fetchSemanticsNodes().isEmpty()}
        compose.onAllNodesWithTag("calendar-event-dash",true).assertCountEquals(1)
        scroll("calendar-event:${event.id}")
        compose.onNodeWithTag("calendar-view:DAY").performClick();scroll("calendar-event:${event.id}")
        compose.onNodeWithTag("calendar-filters").performClick()
        compose.onNodeWithTag("calendar-person-filter").performScrollTo().performClick()
        compose.onNodeWithTag("calendar-person:roy").performClick();compose.onNodeWithText("Listo").performClick()
        scroll("calendar-event:${event.id}")
    }
    @Test fun eventDatePickerAcceptsSupportedFutureYearAndTimeInputSavesChosenLocalTime() {
        val draft=CalendarEventDraft(UUID.randomUUID().toString(),true,"Cita futura","2200-12-31",600,"",emptyList())
        compose.setContent {Arachn0deTheme {CalendarEventEditor(draft,repo,listOf(person),true) {}}}
        compose.onNodeWithTag("event-date").performClick()
        compose.onNodeWithTag("event-date-cancel").performClick()
        compose.onNodeWithTag("event-option:time").performClick()
        assertEquals("2200-12-31",draft.date)
        compose.onNodeWithTag("event-time").assertExists()
        compose.onNode(hasSetTextAction() and hasText("10")).performTextReplacement("14")
        compose.onNode(hasSetTextAction() and hasText("00")).performTextReplacement("45")
        compose.onNodeWithTag("event-time-confirm").performClick()
        compose.onNodeWithTag("event-save").performClick()
        compose.waitUntil(10000) {runBlocking {db.calendarEventDao().all().size==1}}
        assertEquals(885,runBlocking {db.calendarEventDao().all().single().minute})
        assertEquals("2200-12-31",runBlocking {db.calendarEventDao().all().single().date})
    }
    @Test fun bottomSheetColorChoiceSurvivesDraftRestorationAndSavesWithoutChangingTasks() {
        val restore=StateRestorationTester(compose);mount(restore)
        scroll("calendar-new-event");compose.onNodeWithTag("calendar-new-event").performClick()
        compose.onNodeWithTag("event-sheet").assertExists()
        val bounds=compose.onNode(hasText("Nuevo evento") and !hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("Encabezado inferior: $bounds",bounds.top>0f)
        if(compose.onAllNodesWithTag("event-option:title").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("event-option:title").performClick()
        compose.onNodeWithTag("event-title").performTextReplacement("Evento violeta")
        compose.onNodeWithTag("event-title-confirm").performClick()
        compose.onNodeWithTag("event-color").performScrollTo().performClick()
        compose.onNodeWithTag("event-color:VIOLET").performScrollTo().performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("event-color:VIOLET").assertIsSelected()
        compose.onNodeWithTag("event-color-confirm").performClick()
        compose.onNodeWithTag("event-color").assert(hasText("Color: Violeta"))
        compose.onNodeWithTag("event-save").performClick()
        compose.waitUntil(10000) {runBlocking {db.calendarEventDao().all().size==1}}
        val event=runBlocking {db.calendarEventDao().all().single()}
        assertEquals("VIOLET",event.color)
        scroll("calendar-event:${event.id}")
        compose.onNodeWithTag("calendar-event-line:${event.id}",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("calendar-list").performScrollToIndex(0)
        compose.onAllNodesWithTag("calendar-event-dash",true).assertCountEquals(1)
        compose.onAllNodesWithTag("calendar-task-dot",true).assertCountEquals(1)
    }
    @Test fun monthAndWeekLimitRealColoredDashesToThreeWithNeutralOverflow() {
        runBlocking {CalendarEventColor.entries.take(5).forEachIndexed {index,color->repo.save(CalendarEventEntity(UUID.randomUUID().toString(),"Evento $index","2026-12-31",null,"",color.name),emptySet(),true)}}
        mount()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("calendar-event-dash",true).fetchSemanticsNodes().size==3}
        compose.onNodeWithTag("calendar-event-overflow",true).assert(hasText("+2"))
        compose.onAllNodesWithTag("calendar-task-dot",true).assertCountEquals(1)
        compose.onNodeWithTag("calendar-view:WEEK").performClick();awaitTag("calendar-week-strip")
        compose.onAllNodesWithTag("calendar-event-dash",true).assertCountEquals(3)
        compose.onNodeWithTag("calendar-event-overflow",true).assert(hasText("+2"))
        compose.onAllNodesWithTag("calendar-task-dot",true).assertCountEquals(1)
    }
    @Test @Config(qualifiers="en-rUS-w320dp-h360dp")
    fun shortViewportWithLargeFontKeepsAlignedToggleFieldsAndActionsReachable() {
        val draft=CalendarEventDraft(UUID.randomUUID().toString(),true,"Cita","2026-12-31",null,"",emptyList())
        compose.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,1.6f)) {Arachn0deTheme {CalendarEventEditor(draft,repo,listOf(person),true) {}}}}
        compose.onNodeWithTag("event-option:title").performClick()
        compose.onNodeWithTag("event-title").performScrollTo().performClick()
        compose.onNodeWithTag("event-title").performTextReplacement("Cita editada")
        compose.onNodeWithTag("event-title").assertIsDisplayed()
        compose.onNodeWithTag("event-title-confirm").performClick()
        compose.onNodeWithTag("event-option:time").performScrollTo().performClick()
        compose.onNodeWithTag("event-all-day").performScrollTo().assertIsOn().performClick()
        compose.onNodeWithTag("event-time").performScrollTo().assertExists()
        compose.onNodeWithTag("event-time-confirm").performClick()
        compose.onNodeWithTag("event-option:description").performScrollTo().performClick()
        compose.onNodeWithTag("event-description").assertIsDisplayed().performClick()
        compose.onNodeWithTag("event-description").performTextReplacement("Descripción conservada")
        compose.onNodeWithTag("event-description").assertIsDisplayed()
        compose.onNodeWithTag("event-description-confirm").performClick()
        compose.onNodeWithTag("event-color").performScrollTo().assertIsDisplayed()
        val save=compose.onNodeWithTag("event-save")
        if(runCatching {save.assertIsDisplayed()}.isFailure) save.performScrollTo()
        save.assertIsDisplayed()
        assertEquals("Cita editada",draft.title);assertEquals("Descripción conservada",draft.description)
    }
    @Test fun smallScreenAndDoubleFontKeepCurrentAndNewEventControlsReachableWithoutOverlap() {
        mount(narrow=true)
        scroll("calendar-new-event")
        val current=compose.onNodeWithTag("calendar-current-period").fetchSemanticsNode().boundsInRoot
        val create=compose.onNodeWithTag("calendar-new-event").fetchSemanticsNode().boundsInRoot
        assertFalse(current.overlaps(create))
        compose.onNodeWithTag("calendar-current-period").assertIsDisplayed()
        compose.onNodeWithTag("calendar-new-event").assertIsDisplayed().performClick()
        if(compose.onAllNodesWithTag("event-option:title").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("event-option:title").performClick()
        compose.onNodeWithTag("event-title").performTextReplacement("Cita")
        compose.onNodeWithTag("event-title-confirm").performClick()
        compose.onNodeWithTag("event-save").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").assertIsDisplayed().performClick()
    }
    @Test fun integratedTitleStaysSmallCancelReturnsCompactAndClosingReleasesCalendar() {
        var menuOpened=false
        mount(navigation=GlobalNavigation({menuOpened=true},false))
        scroll("calendar-new-event");compose.onNodeWithTag("calendar-new-event").performClick()
        compose.onNodeWithTag("event-option:title").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("event-title").assertIsDisplayed()
        compose.onNodeWithContentDescription("Abrir menú").assertIsNotEnabled()
        val panel=compose.onNodeWithTag("event-sheet").fetchSemanticsNode().boundsInRoot
        val root=compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Título debe conservar Calendario visible: $panel / $root",panel.height<root.height*0.6f)
        compose.onNodeWithTag("event-title-cancel").assertIsDisplayed().performClick()
        compose.onNodeWithTag("event-compact").assertIsDisplayed()
        compose.onNodeWithTag("event-title").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close sheet").assertDoesNotExist()
        compose.onNodeWithTag("event-cancel").assertIsDisplayed().performClick()
        compose.onNodeWithTag("event-sheet").assertDoesNotExist()
        compose.onNodeWithContentDescription("Abrir menú").assertIsEnabled().performClick()
        assertTrue(menuOpened)
        compose.onNodeWithTag("calendar-current-period").assertIsDisplayed().performClick()
        scroll("calendar-new-event");compose.onNodeWithTag("calendar-new-event").assertIsEnabled().performClick()
        compose.onNodeWithTag("event-compact").assertIsDisplayed()
    }

}
