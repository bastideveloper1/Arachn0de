package com.r0ybt.arachn0de.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=android.app.Application::class,sdk=[28],qualifiers="w480dp-h800dp")
class QuickCreateBarTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private fun pump() { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); compose.mainClock.advanceTimeByFrame() }
    private fun await(text:String) = compose.waitUntil(15000) {pump();compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()}
    private fun mount(draft:EditorDraft,busy:State<Boolean> = mutableStateOf(false),save:()->Unit={},cancel:()->Unit={},full:()->Unit={}) {
        compose.runOnUiThread { compose.activity.setContent { Arachn0deTheme { AppSafeArea { Column { QuickCreateBar(draft,busy.value,save,cancel,full) } } } } }
    }
    @Test fun emptyTitleRejectedAndBusyPreventsRepeatedSaveInBothDestinations() {
        val draft=EditorDraft(null,"layer-id","","")
        val busy=mutableStateOf(false);var saves=0
        mount(draft,busy,save={saves++;busy.value=true})
        compose.onNodeWithTag("quick-create-save").assertIsNotEnabled()
        compose.onNodeWithTag("quick-create-title").performTextInput("Desde Capa")
        compose.onNodeWithTag("quick-create-save").performClick()
        compose.onNodeWithTag("quick-create-save").assertIsNotEnabled().performClick()
        assertEquals(1,saves);assertEquals("layer-id",draft.parentId)
    }
    @Test fun noteKeepsTitleAndDisablesTaskOptions() {
        val draft=EditorDraft(null,null,"","")
        mount(draft)
        compose.onNodeWithTag("quick-create-title").performTextInput("Mi nota")
        compose.onNodeWithText("Nota").performClick()
        assertEquals(NodePurpose.NOTE,draft.purpose);assertEquals("Mi nota",draft.title)
        compose.onNodeWithText("Vence").assertIsNotEnabled()
        compose.onNodeWithText("Repetir").assertIsNotEnabled()
        compose.onNodeWithText("Nota ✓").performClick()
        assertEquals(NodePurpose.ACTION,draft.purpose);assertEquals("Mi nota",draft.title)
    }
    @Test fun fullFormReceivesOriginalDraftAndCancelNeverSaves() {
        val draft=EditorDraft(null,null,"Título","",dueAt=123456789L).apply {defaultDueMinute=18*60+30}
        var transferred:EditorDraft?=null;var saves=0;var cancels=0
        mount(draft,save={saves++},cancel={cancels++},full={transferred=draft})
        compose.onNodeWithContentDescription("Formulario completo").performClick()
        assertSame(draft,transferred);assertEquals(123456789L,transferred!!.activeDue)
        assertEquals(18*60+30,transferred!!.defaultDueMinute)
        compose.onNodeWithContentDescription("Cancelar creación").performClick()
        assertEquals(1,cancels);assertEquals(0,saves)
    }
    @Test fun dueCanBeRemovedUsingExistingSelector() {
        val draft=EditorDraft(null,null,"Con fecha","",dueAt=123456789L)
        mount(draft)
        compose.onNodeWithText("Vence ✓").performClick();await("Listo")
        compose.onNodeWithText("Quitar Vencimiento").performClick()
        compose.onNodeWithText("Listo").performClick()
        compose.runOnIdle {assertNull(draft.activeDue)}
    }
    @Test fun recurrenceRequiresExplicitAnchorAndCanBeDisabled() {
        val draft=EditorDraft(null,null,"Recurrente","")
        mount(draft)
        compose.onNodeWithText("Repetir").performClick();await("Listo")
        compose.onNodeWithText("Listo").assertIsNotEnabled()
        compose.runOnIdle {assertEquals("",draft.recurrenceStart)}
        compose.onNodeWithText("Recurrencia: Diaria").performClick()
        compose.onNodeWithText("Ninguna").performClick()
        compose.onNodeWithText("Listo").performClick()
        assertEquals("NONE",draft.recurrenceFrequency)
    }
    @Test fun monthlyConfigurationRetainsInclusiveEndOrNoEnd() {
        val start=RecurrenceSchedule.parse("2027-08-15")
        val draft=EditorDraft(null,"layer-id","Mensual","",dueAt=RecurrenceSchedule.timestamp(start,java.util.TimeZone.getDefault().id,18*60+30)).apply {
            recurrenceFrequency="MONTHLY";recurrenceStart="2027-08-15";recurrenceEnd="2027-12-15"
        }
        mount(draft)
        compose.onNodeWithText("Repetir ✓").performClick();await("Listo")
        compose.onNodeWithText("Listo").assertIsEnabled().performClick()
        val rule=draft.recurrenceRule("project-id")!!
        assertEquals("layer-id",rule.parentId);assertEquals(start,rule.startDay)
        assertEquals(RecurrenceSchedule.parse("2027-12-15"),rule.endDay)
        assertEquals(18*60+30,rule.dueMinute)
        compose.onNodeWithText("Repetir ✓").performClick();await("Listo")
        compose.onNodeWithText("Sin fecha final").performClick()
        compose.onNodeWithText("Listo").performClick()
        assertNull(draft.recurrenceRule("project-id")!!.endDay)
    }
}
