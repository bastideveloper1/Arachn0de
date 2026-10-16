package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.domain.model.CalendarDates
import com.r0ybt.arachn0de.domain.model.CalendarDay
import com.r0ybt.arachn0de.data.repository.CalendarEventRepository
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28],qualifiers="en-rUS")
class CalendarEventPilotTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:CalendarEventRepository
    private lateinit var draft:CalendarEventDraft
    @Before fun start() {
        val context=ApplicationProvider.getApplicationContext<Application>();context.deleteDatabase("arachn0de.db")
        db=Arachn0deDatabase.create(context);repo=CalendarEventRepository(db)
    }
    @After fun close() {compose.runOnUiThread {
        val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        for(i in 0 until root.childCount) (root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
        root.removeAllViews()
    };db.close()}
    private fun mount(restore:StateRestorationTester?=null,people:List<com.r0ybt.arachn0de.domain.model.Person> = emptyList(),initialDescription:String="Conservar",viewport:Boolean=false) {
        val initial=CalendarEventDraft(UUID.randomUUID().toString(),true,"Evento","2026-12-31",null,initialDescription,emptyList(),"BLUE")
        val content:@Composable ()->Unit={
            var saved by rememberSaveable(stateSaver=CalendarEventDraft.Saver) {mutableStateOf<CalendarEventDraft?>(initial)}
            SideEffect {saved?.let {draft=it}}
            Arachn0deTheme {Box(if(viewport) Modifier.fillMaxSize() else Modifier) {saved?.let {CalendarEventEditor(it,repo,people,true) {saved=null}} ?: androidx.compose.material3.Text("Editor cerrado")}}
        }
        if(restore==null) compose.setContent(content) else restore.setContent(content)
    }
    private fun selectDay(day:String) {
        val label=java.text.SimpleDateFormat("EEEE, MMMM d, yyyy",java.util.Locale.US).apply {
            timeZone=java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(CalendarDates.labelInstant(CalendarDay(2026,12,day.toInt()))))
        compose.onNodeWithText(label,useUnmergedTree=true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) {it()}
        compose.onNodeWithText(label,useUnmergedTree=true).assertIsSelected()
    }
    @Test fun newEventHasExactlySixEqualTilesAndFixedLabelsWithCloseAndEqualFooterButtons() {
        mount(viewport=true)
        val tags=listOf("event-option:title","event-date","event-option:time","event-option:description","event-option:people","event-color")
        val labels=listOf("Título","Fecha","Hora","Descripción","Responsables","Color")
        val bounds=tags.mapIndexed {i,tag->
            compose.onNodeWithTag(tag).assertIsDisplayed().assert(hasText(labels[i])).fetchSemanticsNode().boundsInRoot
        }
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("event-compact"))).assertCountEquals(6)
        bounds.forEach {assertEquals(bounds[0].width,it.width,1f);assertEquals(bounds[0].height,it.height,1f)}
        (0..2).forEach {i->assertEquals(bounds[0].top,bounds[i].top,1f);assertEquals(bounds[3].top,bounds[i+3].top,1f);assertEquals(bounds[i].left,bounds[i+3].left,1f)}
        assertTrue(bounds[0].bottom<bounds[3].top)
        tags.forEach {compose.onNodeWithTag("$it:icon",useUnmergedTree=true).assertExists()}
        val cancel=compose.onNodeWithTag("event-cancel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val save=compose.onNodeWithTag("event-save").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(cancel.width,save.width,1f);assertEquals(cancel.height,save.height,1f);assertEquals(cancel.center.y,save.center.y,1f)
        assertTrue(cancel.right<save.left)
        assertTrue(compose.onNodeWithText("Nuevo evento").fetchSemanticsNode().boundsInRoot.right<=compose.onNodeWithTag("event-close").fetchSemanticsNode().boundsInRoot.left)
        compose.onNodeWithText("2026-12-31",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Todo el día",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Conservar",substring=true).assertDoesNotExist()
        action("event-option:title");compose.onNodeWithTag("event-title").performTextReplacement("Oculto en menú");action("event-title-confirm")
        tags.forEachIndexed {i,tag->compose.onNodeWithTag(tag).assert(hasText(labels[i]))}
        compose.onNodeWithText("Oculto en menú",substring=true).assertDoesNotExist()
        assertEquals("Oculto en menú",draft.title)
        action("event-close");compose.onNodeWithTag("event-continue").performClick()
        compose.onNodeWithTag("event-compact").assertExists()
        action("event-cancel");action("event-discard-confirm")
        compose.onNodeWithText("Editor cerrado").assertExists()
        assertTrue(runBlocking {db.calendarEventDao().all().isEmpty()})
    }
    @Test fun dateConfirmationOnlyUpdatesDraftAndFinalSavePersistsCompleteEvent() {
        mount()
        compose.onNodeWithTag("event-compact").assertExists()
        compose.onNodeWithTag("event-title").assertDoesNotExist()
        compose.onNodeWithTag("event-date").performScrollTo().assertIsDisplayed().performClick()
        selectDay("15")
        assertEquals("2026-12-31",draft.date)
        assertTrue(runBlocking {db.calendarEventDao().all().isEmpty()})
        compose.onNodeWithTag("event-date-confirm").performClick()
        compose.onNodeWithTag("event-compact").assertExists()
        assertEquals("2026-12-15",draft.date)
        assertEquals("Conservar",draft.description);assertEquals("BLUE",draft.color)
        assertTrue(runBlocking {db.calendarEventDao().all().isEmpty()})
        compose.onNodeWithTag("event-save").performClick()
        compose.waitUntil(10000) {runBlocking {db.calendarEventDao().all().size==1}}
        val row=runBlocking {db.calendarEventDao().all().single()}
        assertEquals("2026-12-15",row.date);assertEquals("Conservar",row.description);assertEquals("BLUE",row.color)
        compose.onNodeWithText("Editor cerrado").assertExists()
    }
    @Test fun reducingChangingPropertyAndRecreationKeepProvisionalDateAndCancelOnlyDiscardsThatProperty() {
        val restore=StateRestorationTester(compose);mount(restore)
        action("event-date");selectDay("15")
        back()
        assertEquals("2026-12-31",draft.date)
        compose.onNodeWithTag("event-save").assertIsNotEnabled()
        action("event-option:title")
        compose.onNodeWithTag("event-title").performTextReplacement("Título conservado")
        back()
        restore.emulateSavedInstanceStateRestore()
        action("event-date")
        action("event-date-confirm")
        assertEquals("2026-12-15",draft.date);assertEquals("Evento",draft.title)
        action("event-option:title")
        compose.onNodeWithTag("event-title").assert(hasText("Título conservado"))
        action("event-title-confirm")
        action("event-date");selectDay("20")
        action("event-date-cancel")
        compose.onNodeWithTag("event-compact").assertExists()
        assertEquals("2026-12-15",draft.date);assertEquals("Título conservado",draft.title)
        action("event-cancel")
        action("event-continue")
        assertEquals("2026-12-15",draft.date)
        action("event-cancel")
        action("event-discard-confirm")
        compose.onNodeWithText("Editor cerrado").assertExists()
        assertTrue(runBlocking {db.calendarEventDao().all().isEmpty()})
    }
    @Test fun backAndOutsideNeverDiscardProvisionalOrGeneralDraft() {
        mount();compose.onNodeWithTag("event-date").performScrollTo().assertIsDisplayed().performClick();selectDay("15")
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("event-compact").assertExists()
        compose.onNodeWithContentDescription("Close sheet").assertDoesNotExist()
        compose.onNodeWithTag("event-compact").assertExists()
        compose.onNodeWithTag("event-date").performScrollTo().assertIsDisplayed().performClick();compose.onNodeWithTag("event-date-confirm").performClick()
        assertEquals("2026-12-15",draft.date)
        compose.onNodeWithTag("event-save").assertIsEnabled()
    }
    private fun back() {compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()};compose.waitForIdle()}
    private fun action(tag:String) {
        compose.onNodeWithTag(tag).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) {it()}
    }
    @Test fun sixPropertiesAreExclusiveAndUseOneSheet() {
        mount()
        val properties=listOf(
            Triple("event-option:title","event-title","title"),
            Triple("event-date","event-date-editor","date"),
            Triple("event-option:time","event-all-day","time"),
            Triple("event-option:description","event-description","description"),
            Triple("event-option:people","event-people-query","people"),
            Triple("event-color","event-color:BLUE","color"))
        var titleHeight=0f
        for((option,field,property) in properties) {
            action(option)
            compose.onNodeWithTag(field).assertExists()
            compose.onNodeWithTag("event-reduce").assertDoesNotExist()
            compose.onNodeWithTag("event-$property-cancel").assert(hasText("Cancelar"))
            compose.onNodeWithTag("event-$property-confirm").assert(hasText("Confirmar"))
            val height=compose.onNodeWithTag("event-sheet").fetchSemanticsNode().boundsInRoot.height
            if(property=="title") titleHeight=height
            if(property=="description") assertTrue("Descripción debe ocupar más altura que Título",height>titleHeight)
            properties.filter {it.second!=field}.forEach {compose.onNodeWithTag(it.second).assertDoesNotExist()}
            compose.onAllNodesWithTag("event-sheet").assertCountEquals(1)
            action("event-$property-confirm")
            compose.onNodeWithTag("event-compact").assertExists()
        }
        assertTrue(runBlocking {db.calendarEventDao().all().isEmpty()})
    }
    @Test fun independentTextTimeAndColorBuffersSurviveRecreationAndCancelDoesNotTouchConfirmedValues() {
        val restore=StateRestorationTester(compose);mount(restore)
        action("event-option:title");compose.onNodeWithTag("event-title").performTextReplacement("Provisional")
        back()
        action("event-option:description");compose.onNodeWithTag("event-description").performTextReplacement("Texto largo provisional")
        compose.onNodeWithTag("event-description").performTextInputSelection(androidx.compose.ui.text.TextRange(2,8))
        back()
        action("event-option:time");action("event-all-day")
        compose.onNode(hasSetTextAction() and hasText("12")).performTextReplacement("14")
        compose.onNode(hasSetTextAction() and hasText("00")).performTextReplacement("45")
        back()
        action("event-color");action("event-color:VIOLET");back()
        restore.emulateSavedInstanceStateRestore()
        assertEquals("Evento",draft.title);assertEquals("Conservar",draft.description);assertNull(draft.minute);assertEquals("BLUE",draft.color)
        compose.onNodeWithTag("event-save").assertIsNotEnabled()
        action("event-option:description");compose.onNodeWithTag("event-description").assert(hasText("Texto largo provisional"))
        compose.onNodeWithTag("event-description").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange,androidx.compose.ui.text.TextRange(2,8)))
        action("event-description-confirm")
        action("event-option:title");compose.onNodeWithTag("event-title").assert(hasText("Provisional"));action("event-title-cancel")
        action("event-option:title");compose.onNodeWithTag("event-title").assert(hasText("Evento"));action("event-title-confirm")
        action("event-option:time")
        compose.onNode(hasSetTextAction() and hasText("14")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("45")).assertExists()
        action("event-time-confirm")
        action("event-color");compose.onNodeWithTag("event-color:VIOLET").assertIsSelected();action("event-color-cancel")
        assertEquals("BLUE",draft.color);assertEquals(885,draft.minute);assertEquals("Texto largo provisional",draft.description)
        action("event-save")
        compose.waitUntil(10000) {runBlocking {db.calendarEventDao().all().size==1}}
        val saved=runBlocking {db.calendarEventDao().all().single()}
        assertEquals("Evento",saved.title);assertEquals("BLUE",saved.color);assertEquals(885,saved.minute);assertEquals("Texto largo provisional",saved.description)
    }

    @Test fun responsibleSelectionIsProvisionalAndRetainedUntilConfirmedOrExplicitlyCancelled() {
        val restore=StateRestorationTester(compose)
        mount(restore,listOf(com.r0ybt.arachn0de.domain.model.Person("roy","Roy",null)))
        action("event-option:people");action("person-choice:roy");back()
        assertTrue(draft.people.isEmpty())
        restore.emulateSavedInstanceStateRestore()
        action("event-option:people");compose.onNodeWithTag("person-choice:roy").assertIsSelected()
        action("event-people-confirm");assertEquals(listOf("roy"),draft.people)
        action("event-option:people");action("person-choice:roy");action("event-people-cancel")
        assertEquals(listOf("roy"),draft.people)
        action("event-option:people");compose.onNodeWithTag("person-choice:roy").assertIsSelected()
        action("event-people-confirm")
        assertTrue(runBlocking {db.calendarEventDao().all().isEmpty()})
    }

    @Test fun descriptionGrowsThenScrollsAndFullScreenReducesToAdaptiveBeforeCompact() {
        val restore=StateRestorationTester(compose);mount(restore,initialDescription="",viewport=true)
        action("event-option:description")
        fun panelHeight()=compose.onNodeWithTag("event-sheet").fetchSemanticsNode().boundsInRoot.height
        val rootHeight=compose.onRoot().fetchSemanticsNode().boundsInRoot.height
        val small=panelHeight()
        assertTrue("Vacía no debe ocupar pantalla: $small / $rootHeight",small<rootHeight*0.75f)
        compose.onNodeWithTag("event-description").performTextReplacement((1..5).joinToString("\n") {"Línea $it"})
        val medium=panelHeight();assertTrue("Debe crecer con las líneas",medium>small)
        val longText=(1..100).joinToString("\n") {"Línea $it"}
        compose.onNodeWithTag("event-description").performTextReplacement(longText)
        val bounded=panelHeight();assertTrue(bounded>=medium);assertTrue(bounded<=rootHeight*0.75f+1f)
        compose.onNodeWithTag("event-description").performTouchInput {swipeDown()}
        compose.onNodeWithTag("event-description").assert(hasText(longText))
        assertEquals(bounded,panelHeight(),1f)
        compose.onNodeWithTag("event-description-confirm").assertIsDisplayed()
        compose.onNodeWithTag("event-description-cancel").assertIsDisplayed()
        compose.onNodeWithTag("event-description").performTextInputSelection(androidx.compose.ui.text.TextRange(2,8))
        action("event-description-expand");assertTrue(panelHeight()>bounded)
        restore.emulateSavedInstanceStateRestore()
        back()
        compose.onNodeWithTag("event-description").assert(hasText(longText))
        compose.onNodeWithTag("event-compact").assertDoesNotExist()
        assertTrue(panelHeight()<=rootHeight*0.75f+1f)
        compose.onNodeWithTag("event-description").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.TextSelectionRange,androidx.compose.ui.text.TextRange(2,8)))
        back();compose.onNodeWithTag("event-compact").assertExists()
        action("event-option:description");compose.onNodeWithTag("event-description").assert(hasText(longText))
        action("event-description-confirm");assertEquals(longText,draft.description)
        assertTrue(runBlocking {db.calendarEventDao().all().isEmpty()})
        action("event-option:description");compose.onNodeWithTag("event-description").performTextReplacement("")
        assertTrue(panelHeight()<bounded)
        action("event-description-cancel");assertEquals(longText,draft.description)
    }
    @Test fun existingLongDescriptionOpensBoundedWithoutForcingFullScreen() {
        mount(initialDescription=(1..80).joinToString("\n") {"Texto $it"},viewport=true)
        action("event-option:description")
        val rootHeight=compose.onRoot().fetchSemanticsNode().boundsInRoot.height
        assertTrue(compose.onNodeWithTag("event-sheet").fetchSemanticsNode().boundsInRoot.height<=rootHeight*0.75f+1f)
        compose.onNodeWithTag("event-description-expand").assertIsDisplayed()
        compose.onNodeWithTag("event-description-confirm").assertIsDisplayed()
        action("event-description-cancel");compose.onNodeWithTag("event-compact").assertExists()
    }

}
