package com.r0ybt.arachn0de.bank

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.Obligation
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class BankPaymentPanelTest {
    private lateinit var db:Arachn0deDatabase
    private lateinit var app:Application
    private lateinit var repo:BankRepository
    private lateinit var cash:String
    @Before fun setup()=runBlocking<Unit> {
        app=ApplicationProvider.getApplicationContext();app.deleteDatabase("arachn0de.db")
        db=Arachn0deDatabase.create(app);repo=BankRepository(db)
        db.personDao().insert(PersonEntity("yo","Yo",null));db.personDao().insert(PersonEntity("otra","Otra",null))
        repo.createRoot("Banca","","bank");repo.createAccount("bank","Personal","yo");cash=repo.state().accounts.single().id
        ProjectRepository(db.projectDao(),db).createProject("Tareas",creationId="tasks")
    }
    @After fun close(){db.close()}
    private fun request(kind:String,n:Long,person:String?=null)=BankRequest(rootId="bank",contextAccountId=cash,kind=kind,amount=n,day=20000,concept="",
        sourceAccountId=cash.takeIf {kind in setOf("EXPENSE","PAYMENT")},destinationAccountId=cash.takeIf {kind=="INCOME"},personId=person,sourcePersonId=person.takeIf {kind=="DIRECT"})
    @Test fun optionalNamesNaturalMoneyHistoricalTransfersAndBackup()=runBlocking<Unit> {
        assertEquals(5000L,bankAmount("5.000"));assertEquals(5000L,bankAmount("$ 5.000"));assertNull(bankAmount("5,50"));assertNull(bankAmount("-5"));assertNull(bankAmount("9223372036854775808"))
        val income=repo.preview(request("INCOME",10000));repo.confirm(income);repo.confirm(income)
        assertEquals("Ingreso",repo.state().operations.single().concept)
        repo.confirm(repo.preview(request("EXPENSE",1000)));assertEquals("Retiro",repo.state().operations.single {it.kind=="EXPENSE"}.concept)
        val before=repo.state();assertTrue(runCatching {repo.preview(request("EXPENSE",10000))}.isFailure);assertEquals(before,repo.state())
        repo.createAccount("bank","Otra caja","yo");val box=repo.state().accounts.single {it.id!=cash}.id
        repo.confirm(repo.preview(request("TRANSFER",2000).copy(sourceAccountId=cash,destinationAccountId=box)))
        val saved=repo.state();assertEquals(7000L,saved.balance(cash));assertEquals(2000L,saved.balance(box))
        val backup=BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory))
        backup.restore(BackupJson.decode(BackupJson.encode(backup.snapshot())))
        assertEquals(saved,repo.state());assertEquals("TRANSFER",repo.state().operations.single {it.kind=="TRANSFER"}.kind)
        assertTrue(runCatching {repo.annul(income.request.id)}.isFailure);assertEquals(saved,repo.state())
    }
    @Test fun exactPaymentValidationSelectionDirectAndIdempotency()=runBlocking<Unit> {
        repo.confirm(repo.preview(request("INCOME",10000)))
        val nodes=NodeRepository(db)
        val a=nodes.createNode("tasks",null,"A",obligation=Obligation(5000,"CLP"),responsibleIds=setOf("yo"))
        val b=nodes.createNode("tasks",null,"B",obligation=Obligation(4000,"CLP"),responsibleIds=setOf("yo"))
        val shared=nodes.createNode("tasks",null,"Compartida",obligation=Obligation(3000,"CLP"),responsibleIds=setOf("yo","otra"))
        val unowned=nodes.createNode("tasks",null,"Sin responsable",obligation=Obligation(3000,"CLP"))
        val s=repo.state();val suggested=s.suggest("yo",6000)
        assertFalse(validBankPayment(s,"bank","yo",cash,false,6000,suggested))
        assertTrue(validBankPayment(s,"bank","yo",cash,false,4000,mapOf(a.id to 4000L)))
        listOf(emptyMap(),mapOf(a.id to 0L),mapOf(a.id to 6000L),mapOf(a.id to 1000L),mapOf(shared.id to 1000L),mapOf(unowned.id to 1000L),mapOf(a.id to Long.MAX_VALUE,b.id to Long.MAX_VALUE)).forEach {assertFalse(validBankPayment(s,"bank","yo",cash,false,6000,it))}
        assertFalse(validBankPayment(s,"bank",null,cash,false,6000,suggested))
        assertFalse(validBankPayment(s,"bank","yo",null,false,6000,suggested))
        assertFalse(validBankPayment(s,"bank","yo",cash,false,11000,suggested))
        val p=repo.preview(request("PAYMENT",4000,"yo"))
        assertTrue(runCatching {repo.confirmPayment(p,mapOf(a.id to 1000L))}.isFailure);assertEquals(s,repo.state())
        val manual=mapOf(a.id to 4000L);repo.confirmPayment(p,manual);repo.confirmPayment(p,manual)
        assertEquals(6000L,repo.state().balance(cash));assertEquals("Pago",repo.state().operations.single {it.kind=="PAYMENT"}.concept)
        val d=repo.preview(request("DIRECT",1000,"yo"));assertTrue(validBankPayment(d.effective,"bank","yo",null,true,1000,mapOf(a.id to 1000L)))
        repo.confirmPayment(d,mapOf(a.id to 1000L));assertEquals(6000L,repo.state().balance(cash))
        assertEquals(s.nodes.single {it.id==shared.id},db.nodeDao().getById(shared.id))
        assertEquals(s.nodes.single {it.id==unowned.id},db.nodeDao().getById(unowned.id))
    }
    @Test fun strictConfirmationKeepsStaleChecksRollbackAndAnnul()=runBlocking<Unit> {
        repo.confirm(repo.preview(request("INCOME",10000)))
        val n=NodeRepository(db).createNode("tasks",null,"Pago",obligation=Obligation(5000,"CLP"),responsibleIds=setOf("yo"))
        val p=repo.preview(request("PAYMENT",5000,"yo"));val before=repo.state()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_payment_panel BEFORE INSERT ON bank_allocations BEGIN SELECT RAISE(ABORT,'fixture'); END")
        assertTrue(runCatching {repo.confirmPayment(p,mapOf(n.id to 5000L))}.isFailure);assertEquals(before,repo.state())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_payment_panel")
        repo.confirmPayment(p,mapOf(n.id to 5000L));assertTrue(db.nodeDao().getById(n.id)!!.isCompleted)
        NodeRepository(db).setTaskStatus(n.id,com.r0ybt.arachn0de.domain.model.TaskStatus.PENDING)
        repo.annul(p.request.id);assertEquals(10000L,repo.state().balance(cash));assertFalse(db.nodeDao().getById(n.id)!!.isCompleted)
        val stale=repo.preview(request("DIRECT",1000,"yo"));NodeRepository(db).updateNode(n.id,"Cambio")
        val current=repo.state();assertTrue(runCatching {repo.confirmPayment(stale,mapOf(n.id to 1000L))}.isFailure);assertEquals(current,repo.state())
    }
}
