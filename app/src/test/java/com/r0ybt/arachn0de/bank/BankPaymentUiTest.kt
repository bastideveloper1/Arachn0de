package com.r0ybt.arachn0de.bank

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.Obligation
import com.r0ybt.arachn0de.ui.BankWorkspace
import com.r0ybt.arachn0de.ui.theme.Arachn0deTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[33],qualifiers="w360dp-h891dp")
class BankPaymentUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:BankRepository
    private lateinit var cash:String
    private fun await(text:String)=compose.waitUntil(15000) {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications();compose.mainClock.advanceTimeByFrame()
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    @Before fun setup()=runBlocking<Unit> {
        val app=ApplicationProvider.getApplicationContext<Application>();app.deleteDatabase("arachn0de.db")
        db=Arachn0deDatabase.create(app);repo=BankRepository(db)
        db.personDao().insert(PersonEntity("yo","Yo",null));db.personDao().insert(PersonEntity("otra","Otra",null))
        repo.createRoot("Banca","","bank");repo.createAccount("bank","Personal","yo");cash=repo.state().accounts.single().id
        repo.confirm(repo.preview(BankRequest(rootId="bank",contextAccountId=cash,kind="INCOME",amount=10000,day=20000,concept="",destinationAccountId=cash)))
        ProjectRepository(db.projectDao(),db).createProject("Núcleo de pagos",creationId="tasks")
        val nodes=NodeRepository(db)
        nodes.createNode("tasks",null,"A",creationId="a",obligation=Obligation(5000,"CLP"),responsibleIds=setOf("yo"))
        nodes.createNode("tasks",null,"B",creationId="b",obligation=Obligation(4000,"CLP"),responsibleIds=setOf("yo"))
        nodes.createNode("tasks",null,"Compartida",creationId="shared",obligation=Obligation(3000,"CLP"),responsibleIds=setOf("yo","otra"))
        nodes.createNode("tasks",null,"Pago de Otra",creationId="other",obligation=Obligation(2000,"CLP"),responsibleIds=setOf("otra"))
        val core=ProjectRepository(db.projectDao(),db).getProject("bank")!!
        compose.setContent {Arachn0deTheme {BankWorkspace(core,db,{}) {_,_->}}}
    }
    @After fun close() {
        compose.runOnUiThread {
            val root=compose.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            for(i in 0 until root.childCount)(root.getChildAt(i) as? androidx.compose.ui.platform.AbstractComposeView)?.disposeComposition()
            root.removeAllViews()
        }
        db.close()
    }
    @Test fun accountOptionsOfferDeletionWithCancelAndExplicitConfirmation() {
        await("Personal (Yo)");compose.onNodeWithText("Personal (Yo)").performClick();await("$10.000")
        compose.onNodeWithText("Opciones").performClick();compose.onNodeWithText("Eliminar cuenta").performClick();await("¿Eliminar cuenta?")
        compose.onNodeWithText("Cancelar").performClick();compose.waitForIdle()
        runBlocking {assertTrue(repo.state().accounts.any {it.id==cash});assertEquals(1,repo.state().operations.size)}
        compose.onNodeWithText("Opciones").performClick();compose.onNodeWithText("Eliminar cuenta").performClick();await("¿Eliminar cuenta?")
        compose.onNodeWithText("Eliminar cuenta").performClick()
        compose.waitUntil(15000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();runBlocking {repo.state().accounts.none {it.id==cash}}}
        runBlocking {assertTrue(repo.state().roots.any {it.id=="bank"});assertNotNull(repo.state().nodes.find {it.id=="a"})}
    }
    private fun openPayment() {
        compose.onNodeWithText("Operar").performClick()
        compose.onNodeWithText("Transferir dinero").assertDoesNotExist()
        compose.onNodeWithText("Pagar").performClick();await("Pagos pendientes")
    }
    private fun select(id:String) {
        compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("bank-debt:$id"))).performScrollTo().performClick()
    }
    @Test fun originalCardsSingleSelectionCancelAndSaldoPayment() {
        await("Personal (Yo)");compose.onNodeWithText("Personal (Yo)").performClick();await("$10.000");openPayment()
        compose.onNodeWithText("Desde saldo").assertDoesNotExist();compose.onNodeWithText("Directamente").assertDoesNotExist()
        compose.onNodeWithText("Confirmar pago").assertIsNotEnabled()
        compose.onNodeWithTag("bank-debt:shared").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Cambiar estado: A").assertDoesNotExist()
        select("a")
        compose.onNodeWithTag("bank-payment-amount").performScrollTo().performTextInput("6.000")
        compose.onNodeWithText("Confirmar pago").assertIsNotEnabled()
        compose.onNodeWithTag("bank-payment-amount").performScrollTo().performTextReplacement("3.000")
        select("b")
        compose.onNodeWithTag("bank-debt:a").assertIsNotSelected();compose.onNodeWithTag("bank-debt:b").assertIsSelected()
        compose.onNodeWithText("Confirmar pago").assertIsEnabled()
        compose.onNodeWithText("Cancelar").performClick();assertEquals(1,runBlocking {repo.state().operations.size})
        openPayment();select("a")
        compose.onNodeWithTag("bank-payment-amount").performScrollTo().performTextInput("3.000")
        compose.onNodeWithText("Confirmar pago").performClick();await("¿Confirmar pago de $3.000?")
        compose.onNodeWithText("Confirmar").performClick();await("$7.000")
        val state=runBlocking {repo.state()};assertEquals(7000L,state.balance(cash));assertEquals(mapOf("a" to 3000L),state.allocations.associate {it.nodeId to it.amount})
        assertFalse(state.nodes.single {it.id=="a"}.isCompleted)
        openPayment();compose.onNodeWithText("Cancelar").performClick()
    }
    @Test fun mainDirectPaymentWithoutAccountsAndFinalCancellationWritesNothing() {
        runBlocking {repo.deleteAccount(repo.previewAccountDeletion(cash))}
        compose.waitUntil(15000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithText("Personal (Yo)").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("core-context:Options").assertTextContains("Pagar").performClick();await("Pagos pendientes")
        compose.onNodeWithText("Saldo disponible:",substring=true).assertDoesNotExist()
        select("other")
        compose.onNodeWithTag("bank-payment-amount").performScrollTo().performTextInput("2.000")
        compose.onNodeWithText("Confirmar pago").performClick();await("¿Confirmar pago de $2.000?")
        compose.onAllNodesWithText("Cancelar").onLast().performClick();assertTrue(runBlocking {repo.state().operations.isEmpty()})
        compose.onNodeWithText("Confirmar pago").performClick();await("¿Confirmar pago de $2.000?")
        compose.onNodeWithText("Confirmar").performClick();await("Pago")
        val state=runBlocking {repo.state()};assertTrue(state.accounts.isEmpty());assertNull(state.operations.single().contextAccountId)
        assertNull(state.operations.single().sourcePersonId);assertEquals(2000L,state.paid("other"));assertTrue(state.nodes.single {it.id=="other"}.isCompleted)
        compose.onNodeWithTag("core-context:Options").performClick();await("Pagos pendientes")
        compose.onNodeWithText("Cancelar").performClick()
    }
}
