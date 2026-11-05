package com.r0ybt.arachn0de.bank

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class BankUnifiedPaymentTest {
    private lateinit var app:Application
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:BankRepository
    private lateinit var nodes:NodeRepository
    @Before fun setup()=runBlocking<Unit> {
        app=ApplicationProvider.getApplicationContext();app.deleteDatabase("arachn0de.db")
        db=Arachn0deDatabase.create(app);repo=BankRepository(db);nodes=NodeRepository(db)
        ProjectRepository(db.projectDao(),db).createProject("Tarjetas",creationId="tasks")
    }
    @After fun close() {db.close()}
    private fun direct(amount:Long,root:String="bank",create:Boolean=false)=BankRequest(rootId=root,contextAccountId=null,kind="DIRECT",amount=amount,day=20000,concept="",createRoot=create)
    @Test fun successiveUnidentifiedPaymentsCreateBankOnlyOnCommitKeepOriginalAndStateAndRoundTrip()=runBlocking<Unit> {
        val n=nodes.createNode("tasks",null,"Deuda",obligation=Obligation(100000,"CLP"))
        val first=repo.preview(direct(20000,create=true))
        assertTrue(repo.state().roots.isEmpty()) // cancelling the first preview creates nothing
        val untouched=repo.state()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_first_payment BEFORE INSERT ON bank_allocations BEGIN SELECT RAISE(ABORT,'fixture'); END")
        assertTrue(runCatching {repo.confirmPayment(first,mapOf(n.id to 20000L))}.isFailure)
        assertEquals(untouched,repo.state())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_first_payment")
        repo.confirmPayment(first,mapOf(n.id to 20000L));repo.confirmPayment(first,mapOf(n.id to 20000L))
        assertEquals(1,repo.state().operations.size);assertTrue(repo.state().accounts.isEmpty())
        for(amount in listOf(30000L,50000L)) repo.confirmPayment(repo.preview(direct(amount)),mapOf(n.id to amount))
        val paid=repo.state();assertEquals(100000L,paid.paid(n.id));assertEquals(0L,paid.remaining(paid.nodes.single()))
        assertEquals(100000L,paid.nodes.single().amountMinor);assertTrue(paid.nodes.single().isCompleted)
        assertTrue(paid.allocations.all {it.sourcePersonId==null && it.personId==null && it.stateAt==null})
        assertEquals(3,paid.history("bank",null).size)
        assertTrue(runCatching {repo.confirmPayment(repo.preview(direct(1)),mapOf(n.id to 1L))}.isFailure)
        val backup=BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory))
        val bytes=BackupJson.encode(backup.snapshot());assertEquals(58,JSONObject(String(bytes)).getInt("dataVersion"))
        val decoded=BackupJson.decode(bytes);backup.restore(decoded);assertEquals(paid,repo.state())
        // An older reader contract cannot silently accept the new nulls.
        val malformed=JSONObject(String(bytes)).put("dataVersion",57).toString().toByteArray()
        assertTrue(runCatching {BackupJson.decode(malformed)}.isFailure);assertEquals(paid,repo.state())
    }
    @Test fun bankSourceBalanceHistoryStaleAndRollback()=runBlocking<Unit> {
        db.personDao().insert(PersonEntity("owner","Titular",null))
        repo.createRoot("Banco","","bank");repo.createAccount("bank","Cuenta","owner")
        val account=repo.state().accounts.single().id
        repo.confirm(repo.preview(BankRequest(rootId="bank",contextAccountId=account,kind="INCOME",amount=60000,day=20000,concept="Ingreso",destinationAccountId=account)))
        val n=nodes.createNode("tasks",null,"Deuda sin responsable",obligation=Obligation(100000,"CLP"))
        fun payment(amount:Long)=BankRequest(rootId="bank",contextAccountId=account,kind="PAYMENT",amount=amount,day=20000,concept="",sourceAccountId=account)
        assertTrue(runCatching {repo.preview(payment(60001))}.isFailure)
        val p=repo.preview(payment(20000));repo.confirmPayment(p,mapOf(n.id to 20000L));repo.confirmPayment(p,mapOf(n.id to 20000L))
        assertEquals(40000L,repo.state().balance(account))
        repo.confirmPayment(repo.preview(direct(30000)),mapOf(n.id to 30000L))
        assertEquals(40000L,repo.state().balance(account));assertEquals(50000L,repo.state().remaining(repo.state().nodes.single {it.id==n.id}))
        assertEquals(listOf("DIRECT"),repo.state().history("bank",null).map {it.kind})
        assertEquals(setOf("INCOME","PAYMENT"),repo.state().history("bank",account).map {it.kind}.toSet())
        val atomic=repo.preview(payment(10000));val before=repo.state()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_unified BEFORE INSERT ON bank_allocations BEGIN SELECT RAISE(ABORT,'fixture'); END")
        assertTrue(runCatching {repo.confirmPayment(atomic,mapOf(n.id to 10000L))}.isFailure);assertEquals(before,repo.state())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_unified")
        nodes.updateNode(n.id,"Renombrada");assertTrue(runCatching {repo.confirmPayment(atomic,mapOf(n.id to 10000L))}.isFailure)
        val preview=repo.previewAccountDeletion(account)
        assertFalse(preview.operationIds.contains(repo.state().operations.single {it.kind=="DIRECT"}.id))
    }
    @Test fun correctionsPreserveAbonosAndNewStatesAreGuardedWithoutChangingHistoricalStates()=runBlocking<Unit> {
        repo.createRoot("Banco","","bank")
        val n=nodes.createNode("tasks",null,"Deuda",obligation=Obligation(10000,"CLP"))
        repo.confirmPayment(repo.preview(direct(4000)),mapOf(n.id to 4000L));val history=repo.state().operations
        assertTrue(runCatching {nodes.setTaskStatus(n.id,TaskStatus.COMPLETED)}.isFailure)
        assertTrue(runCatching {nodes.updateLeaf(n.id,n.title,"",null,null,Obligation(3999,"CLP"))}.isFailure)
        nodes.updateLeaf(n.id,n.title,"",null,null,Obligation(12000,"CLP"))
        assertEquals(history,repo.state().operations);assertEquals(8000L,repo.state().remaining(repo.state().nodes.single()))
        repo.confirmPayment(repo.preview(direct(8000)),mapOf(n.id to 8000L));assertTrue(repo.state().nodes.single().isCompleted)
        nodes.setTaskStatus(n.id,TaskStatus.COMPLETED)
        assertTrue(runCatching {nodes.updateLeaf(n.id,n.title,"",null,null,Obligation(13000,"CLP"))}.isFailure)
        assertTrue(runCatching {repo.annul(history.single().id)}.isFailure)
        assertTrue(repo.state().nodes.single().isCompleted)
    }
    @Test fun correctingAnExplicitHistoricalSplitPreservesItsOriginalAllocations()=runBlocking<Unit> {
        db.personDao().insert(PersonEntity("p","Persona",null))
        repo.createRoot("Banco","","bank");repo.createAccount("bank","Cuenta","p")
        val account=repo.state().accounts.single().id
        val a=nodes.createNode("tasks",null,"A",obligation=Obligation(10000,"CLP"),responsibleIds=setOf("p"))
        val b=nodes.createNode("tasks",null,"B",obligation=Obligation(10000,"CLP"),responsibleIds=setOf("p"))
        val old=BankOperation("legacy","bank",account,"DIRECT",5000,20000,"Histórico",null,null,"p","p",null,false,1)
        val original=listOf(BankAllocation("a",old.id,a.id,2000,"p","Persona","p","Persona",a.title,null,null,null,2),
            BankAllocation("b",old.id,b.id,3000,"p","Persona","p","Persona",b.title,null,null,null,2))
        db.bankDao().insertOperations(listOf(old));db.bankDao().insertAllocations(original);repo.state().validate()
        val request=direct(6000).copy(contextAccountId=account,sourcePersonId="p",personId="p",correctsId=old.id)
        repo.confirmPayment(repo.preview(request),mapOf(a.id to 2500L,b.id to 3500L))
        assertEquals(original,repo.state().allocations.filter {it.operationId==old.id})
        assertEquals(old.copy(voided=true),repo.state().operations.single {it.id==old.id})
        assertEquals(2500L,repo.state().paid(a.id));assertEquals(3500L,repo.state().paid(b.id))
        assertTrue(repo.state().nodes.filter {it.purpose=="ACTION"}.none {it.isCompleted})
    }
    @Test fun failureWhenCompletingRollsBackPaymentBankAndEventsAndRetryCompletesWithInheritedThreeStates()=runBlocking<Unit> {
        db.openHelper.writableDatabase.execSQL("UPDATE projects SET verificationEnabled=1 WHERE id='tasks'")
        val n=nodes.createNode("tasks",null,"Tres estados heredados",obligation=Obligation(1000,"CLP"))
        val original=repo.state();val events=db.nodeEventDao().all()
        val preview=repo.preview(direct(1000,create=true))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_total_state BEFORE UPDATE OF isCompleted ON nodes WHEN NEW.isCompleted=1 BEGIN SELECT RAISE(ABORT,'fixture completion'); END")
        assertTrue(runCatching {repo.confirmPayment(preview,mapOf(n.id to 1000L))}.isFailure)
        assertEquals(original,repo.state());assertEquals(events,db.nodeEventDao().all())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_total_state")
        repo.confirmPayment(preview,mapOf(n.id to 1000L));repo.confirmPayment(preview,mapOf(n.id to 1000L))
        val saved=db.nodeDao().getById(n.id)!!
        assertTrue(saved.isCompleted);assertFalse(saved.awaitingVerification);assertNull(saved.taskStateCount)
        assertEquals(3,nodes.taskStateCount(n.id));assertEquals(1,db.nodeEventDao().all().size-events.size)
        assertEquals(1000L,saved.amountMinor);assertEquals(0L,repo.state().remaining(saved))
    }
    @Test fun completingWithANewPaymentPreservesHistoricalAllocationMarkers()=runBlocking<Unit> {
        repo.createRoot("Banco","","bank")
        val n=nodes.createNode("tasks",null,"Con historial",obligation=Obligation(1000,"CLP"))
        val old=BankOperation("historical","bank",null,"DIRECT",300,20000,"Anterior",null,null,null,null,null,false,1)
        val allocation=BankAllocation("historical-part",old.id,n.id,300,null,null,null,null,n.title,null,null,1,2)
        db.bankDao().insertOperations(listOf(old));db.bankDao().insertAllocations(listOf(allocation))
        repo.confirmPayment(repo.preview(direct(700)),mapOf(n.id to 700L))
        assertEquals(old,repo.state().operations.single {it.id==old.id})
        assertEquals(allocation,repo.state().allocations.single {it.id==allocation.id})
        assertTrue(db.nodeDao().getById(n.id)!!.isCompleted)
    }
    @Test fun historicalBackupsKeepBankReferencesCurrenciesAndRows()=runBlocking<Unit> {
        val backup=BackupRepository(db,app,BackupAvatarFiles(app,BackupFixture::syncDirectory))
        val preBank=BackupJson.encode(backup.snapshot())
        db.personDao().insert(PersonEntity("p","Persona",null))
        repo.createRoot("Banco","","bank");repo.createAccount("bank","Cuenta","p")
        val account=repo.state().accounts.single().id
        val n=nodes.createNode("tasks",null,"Histórica",obligation=Obligation(10000,"CLP"),responsibleIds=setOf("p"))
        val request=direct(3000).copy(contextAccountId=account,personId="p",sourcePersonId="p")
        repo.confirmPayment(repo.preview(request),mapOf(n.id to 3000L))
        val usd=nodes.createNode("tasks",null,"Moneda histórica")
        db.openHelper.writableDatabase.execSQL("UPDATE nodes SET amountMinor=2500,currencyCode='USD' WHERE id=?",arrayOf(usd.id))
        val historical=repo.state();val oldBytes=BackupJson.encode(backup.snapshot())
        assertEquals(49,JSONObject(String(oldBytes)).getInt("dataVersion"))
        backup.restore(BackupJson.decode(oldBytes));assertEquals(historical,repo.state())
        // Exercise the immediately preceding reader version as well as the original bank format.
        val withEffort=backup.snapshot().copy(nodes=backup.snapshot().nodes.map {if(it.id==n.id)it.copy(effort=2) else it})
        val v57=BackupJson.encode(withEffort);assertEquals(57,JSONObject(String(v57)).getInt("dataVersion"))
        backup.restore(BackupJson.decode(v57));assertEquals(historical.operations,repo.state().operations);assertEquals(historical.allocations,repo.state().allocations)
        assertEquals("USD",repo.state().nodes.single {it.id==usd.id}.currencyCode)
        assertTrue(runCatching {repo.confirmPayment(repo.preview(direct(100)),mapOf(usd.id to 100L))}.isFailure)
        backup.restore(BackupJson.decode(preBank));assertTrue(repo.state().operations.isEmpty());assertTrue(repo.state().roots.isEmpty())
    }
}
