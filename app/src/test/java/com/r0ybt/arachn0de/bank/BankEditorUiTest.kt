package com.r0ybt.arachn0de.bank

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.repository.TagRepository
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.ui.*
import com.r0ybt.arachn0de.ui.state.EditorDraft
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[33],qualifiers="w360dp-h891dp")
class BankEditorUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun coreTypeAndPriorityAreIndependentExclusiveChoices() {
        val draft=EditorDraft(null,null,"","")
        compose.setContent {Arachn0deTheme {CoreCreationChoices(draft,true)}}
        compose.onNodeWithText("Núcleo").assertIsSelected();compose.onNodeWithText("Común").assertIsSelected()
        compose.onNodeWithText("Banca").performClick();compose.onNodeWithText("Prioritario").performClick()
        assertTrue(draft.bankType);assertEquals(Priority.HIGH,draft.priority)
        compose.onNodeWithText("Núcleo").assertIsNotSelected()
        compose.onNodeWithText("Núcleo").performClick();assertFalse(draft.bankType);assertEquals(Priority.HIGH,draft.priority)
    }
    @Test fun paymentOpensFieldsDirectlyAndCancelDoesNotConfirmDraft() {
        val app=ApplicationProvider.getApplicationContext<Application>();app.deleteDatabase("arachn0de.db")
        val db=Arachn0deDatabase.create(app)
        val draft=EditorDraft("task",null,"Tarea","",moneyLocaleTag="es-CL")
        var cancelled=0;var saved:Obligation?=null
        compose.setContent {Arachn0deTheme {NodePropertyEditor(draft,NodeProperty.PAYMENT,true,600.dp,emptyList(),TagState(),TagRepository(db),emptyList(),{cancelled++},{saved=draft.obligation()})}}
        compose.onNodeWithTag("obligation-enabled").assertDoesNotExist()
        compose.onNodeWithText("Monto").performTextInput("1200")
        compose.onNodeWithText("Cancelar").performClick();assertEquals(1,cancelled);assertNull(saved)
        compose.onNodeWithText("Confirmar").performClick();assertEquals(1200L,saved!!.amountMinor)
        compose.runOnUiThread {
            val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount)(root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()
        }
        db.close()
    }
}
