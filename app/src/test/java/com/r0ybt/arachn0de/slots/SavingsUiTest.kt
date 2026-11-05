package com.r0ybt.arachn0de.slots

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.bank.BankRepository
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[33],qualifiers="w411dp-h900dp")
class SavingsUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:SavingsRepository
    private lateinit var initial:SavingsView
    @Before fun setup()=runBlocking<Unit> {
        val app=ApplicationProvider.getApplicationContext<Application>();app.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(app)
        db.personDao().insert(PersonEntity("yo","Yo",null));val bank=BankRepository(db)
        bank.createRoot("Caja física","","bank");bank.createAccount("bank","Personal","yo")
        repo=SavingsRepository(db)
        repo.configure(SavingsConfig(UUID.randomUUID().toString(),"bank",bank.state().accounts.single().id))
        initial=SavingsView(repo.read(),repo.bankState())
    }
    @After fun close() {
        compose.runOnUiThread {val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content);for(i in 0 until root.childCount)(root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition();root.removeAllViews()}
        db.close()
    }
    private fun show(page:String,onDismiss:()->Unit={}) {
        compose.setContent {Arachn0deTheme {
            val view by repo.observe().collectAsState(initial)
            var open by remember {mutableStateOf(true)}
            if(open)SavingsPanel(view,repo,db,page,{open=false;onDismiss()},{_,_->},{})
        }}
    }
    @Test fun purchasePreviewAndClosingDoNotWrite() {
        var cancelled=false;show("buy") {cancelled=true}
        compose.onNodeWithText("Recibirás 5 tiradas").assertExists()
        assertTrue(runBlocking {repo.bankState().operations.isEmpty()});assertEquals(0L,runBlocking {repo.read().book().credits})
        compose.onNodeWithText("Cancelar").assertDoesNotExist();compose.onNodeWithText("Cerrar").assertDoesNotExist()
        compose.onNodeWithContentDescription("Cerrar").performClick();assertTrue(cancelled)
        assertTrue(runBlocking {repo.bankState().operations.isEmpty()})
    }
    @Test fun confirmedMonetaryDepositCreatesOneIncomeAndFiveSpins() {
        show("buy");compose.onNodeWithText("DEPOSITAR").performClick()
        compose.waitUntil(15000) {shadowOf(Looper.getMainLooper()).idle();runBlocking {repo.read().book().credits==5L}}
        assertEquals(1,runBlocking {repo.bankState().operations.size});assertEquals(1000L,runBlocking {repo.bankState().operations.single().amount})
    }
    @Test fun depositClpExplainsHundredSpinsForTwentyThousandAndClosesAfterDeposit() {
        var closed=false;show("buy") {closed=true}
        compose.onNodeWithTag("slot-deposit-clp").performTextReplacement("20.000")
        compose.onNodeWithText("Recibirás 100 tiradas").assertExists()
        compose.onNodeWithTag("slot-deposit-clp").performTextReplacement("40.000")
        compose.onNodeWithText("Recibirás 200 tiradas").assertExists()
        compose.onNodeWithTag("slot-deposit-clp").performTextReplacement("20.000")
        compose.onNodeWithText("DEPOSITAR").performClick()
        compose.waitUntil(15000) {shadowOf(Looper.getMainLooper()).idle();runBlocking {repo.read().book().credits==100L}}
        compose.waitForIdle();assertTrue(closed)
        assertEquals(20000L,runBlocking {repo.bankState().operations.single().amount})
    }
    private fun pendingPrize()=runBlocking {
        repo=SavingsRepository(db,draw={listOf(1,1,1)})
        val c=repo.read().active!!;repo.depositAndBuy(UUID.randomUUID().toString(),1000,c.root,c.account);repo.topup(UUID.randomUUID().toString(),19,c.id)
        val e=repo.spin(UUID.randomUUID().toString());repo.acknowledge(e.id);initial=SavingsView(repo.read(),repo.bankState())
    }
    @Test fun prizeHasOnlyWithdrawalAndClosingPreservesMoneyAndReservation() {
        pendingPrize();var closed=false;show("win") {closed=true}
        compose.onNodeWithText("RETIRAR").assertExists()
        listOf("Cancelar","Cerrar","Ya retiré","Conservar").forEach {compose.onNodeWithText(it).assertDoesNotExist()}
        compose.onNodeWithContentDescription("Cerrar").assertDoesNotExist()
        compose.onNode(isDialog()).performTouchInput {click(androidx.compose.ui.geometry.Offset(2f,2f))}
        compose.waitForIdle();assertTrue(closed)
        assertEquals(2L,runBlocking {repo.read().book().reserved});assertEquals(20000L,runBlocking {repo.bankState().balance(initial.state.active!!.account)})
        assertTrue(runBlocking {repo.bankState().operations.none {it.kind=="EXPENSE"}})
    }
    @Test fun withdrawButtonRecordsSingleExpenseAndClosesPrize() {
        pendingPrize();var closed=false;show("prizes") {closed=true}
        compose.onNodeWithText("RETIRAR").performClick()
        compose.waitUntil(15000) {shadowOf(Looper.getMainLooper()).idle();runBlocking {repo.read().book().pending.isEmpty()}}
        compose.waitForIdle();assertTrue(closed)
        assertEquals(18000L,runBlocking {repo.bankState().balance(initial.state.active!!.account)})
        assertEquals(1,runBlocking {repo.bankState().operations.count {it.kind=="EXPENSE"}})
    }
    @Test fun configurationShowsExactRtpAndRejectsInconsistentTargets() {
        show("config")
        compose.onNodeWithText("Guardar").assertIsEnabled()
        compose.onNodeWithText("RTP objetivo %").performTextReplacement("40")
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(3000,runBlocking {repo.read().active!!.targetBps})
        assertTrue(runBlocking {repo.bankState().operations.isEmpty()})
    }
    @Test fun accountPickerKeepsSearchBelowListAndDoesNotChangeAssociationOnCancel() {
        show("config");compose.onNodeWithText("Personal").performClick()
        val search=compose.onNodeWithTag("savings-account-search").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val list=compose.onNodeWithTag("savings-account-list").fetchSemanticsNode().boundsInRoot
        assertTrue(search.top>=list.bottom)
        compose.onNodeWithTag("savings-account-search").performTextInput("Caja")
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(initial.state,runBlocking {repo.read()})
    }
}
