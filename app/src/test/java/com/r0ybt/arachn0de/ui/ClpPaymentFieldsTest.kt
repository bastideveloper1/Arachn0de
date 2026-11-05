package com.r0ybt.arachn0de.ui

import android.app.Application
import androidx.activity.ComponentActivity
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
@Config(application=Application::class,sdk=[33])
class ClpPaymentFieldsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun newClpPaymentUsesExistingAmountFieldWithoutCurrencySelector() {
        val draft=EditorDraft(null,null,"Nueva","",obligation=Obligation(15000,"CLP"))
        compose.setContent {Arachn0deTheme {ObligationFields(draft,true)}}
        compose.onNodeWithText("Monto").performTextReplacement("16000")
        compose.onNodeWithText("USD").assertDoesNotExist();compose.onNodeWithText("EUR").assertDoesNotExist()
        compose.runOnIdle {assertEquals(Obligation(16000,"CLP"),draft.obligation())}
    }
    @Test fun historicalAmountIsVisibleDisabledAndExplained() {
        val draft=EditorDraft("old",null,"Histórica","",obligation=Obligation(1050,"USD"))
        compose.setContent {Arachn0deTheme {ObligationFields(draft,true)}}
        compose.onNodeWithText("Monto").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("obligation-enabled").assertIsNotEnabled()
        compose.onNodeWithText(PaymentCurrencyPolicy.MESSAGE).assertIsDisplayed()
        compose.runOnIdle {assertEquals(Obligation(1050,"USD"),draft.obligation())}
    }
}
