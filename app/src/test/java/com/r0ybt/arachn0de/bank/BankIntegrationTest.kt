package com.r0ybt.arachn0de.bank

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(application=Application::class,sdk=[28])
class BankIntegrationTest {
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:BankRepository
    private lateinit var nodes:NodeRepository
    private lateinit var context:Application
    private lateinit var cash:String
    private lateinit var box:String
    @Before fun setup()=runBlocking {
        context=ApplicationProvider.getApplicationContext();context.deleteDatabase("arachn0de.db")
        db=Arachn0deDatabase.create(context);repo=BankRepository(db,now={10000});nodes=NodeRepository(db) {1000}
        listOf("yo","madre","padre").forEach {db.personDao().insert(PersonEntity(it,it,null))}
        repo.createRoot("Mis cajas","","bank")
        ProjectRepository(db.projectDao(),db).createProject("Obligaciones",creationId="debts")
        repo.createAccount("bank","Personal","yo");repo.createAccount("bank","Pagos","yo")
        cash=repo.state().accounts.single {a->repo.state().nodes.single {it.id==a.id}.title=="Personal"}.id
        box=repo.state().accounts.single {it.id!=cash}.id
    }
    @After fun close() {db.close()}
    private suspend fun bill(amount:Long,person:String="yo",id:String=java.util.UUID.randomUUID().toString())=nodes.createNode("debts",null,id,creationId=id,obligation=Obligation(amount,"CLP"),responsibleIds=setOf(person))
    private fun request(kind:String,amount:Long,person:String?=null,source:String?=null,destination:String?=null,corrects:String?=null)=BankRequest(rootId="bank",contextAccountId=cash,kind=kind,amount=amount,day=20000,concept="Registro",sourceAccountId=source,destinationAccountId=destination,sourcePersonId=if(kind=="DIRECT")person else null,personId=person,correctsId=corrects)
    private suspend fun record(r:BankRequest)=repo.preview(r).let {p->
        if(r.kind in setOf("PAYMENT","DIRECT")) {
            val n=p.effective.candidates(r.personId).first {p.effective.eligible(it,r.personId) && p.effective.remaining(it)>=r.amount}
            repo.confirmPayment(p,mapOf(n.id to r.amount))
        } else repo.confirm(p)
    }

    @Test fun incomeTransferPaymentsDirectAndDifferentPeople()=runBlocking<Unit> {
        record(request("INCOME",100000,destination=cash))
        record(request("TRANSFER",30000,source=cash,destination=box))
        assertEquals(70000L,repo.state().balance(cash));assertEquals(30000L,repo.state().balance(box))
        val own=bill(50000);record(request("PAYMENT",20000,"yo",cash))
        assertEquals(50000L,repo.state().balance(cash));assertEquals(30000L,repo.state().remaining(db.nodeDao().getById(own.id)!!))
        val projection=FinancialSnapshot(NodeTreeSnapshot(listOf(db.nodeDao().getById(own.id)!!.toNode(false))),emptyMap(),FinancialSelection(FinancialPeriod.ALL),CalendarMonth(2026,10),java.util.TimeZone.getTimeZone("UTC"),mapOf(own.id to 20000L))
        assertEquals(java.math.BigInteger.valueOf(30000),projection.summary.byCurrency.getValue("CLP").pendingMinor)
        assertEquals(FinancialState.PARTIAL,projection.summary.state)
        val mother=bill(50000,"madre");record(request("DIRECT",30000,"madre"))
        assertEquals(20000L,repo.state().remaining(db.nodeDao().getById(mother.id)!!));assertEquals(50000L,repo.state().balance(cash))
        val father=bill(10000,"padre");record(request("PAYMENT",10000,"padre",cash))
        val part=repo.state().allocations.single {it.nodeId==father.id}
        assertEquals("yo",part.sourcePersonId);assertEquals("padre",part.personId)
        assertEquals(40000L,repo.state().balance(cash));assertTrue(db.nodeDao().getById(father.id)!!.isCompleted)
    }
    @Test fun deterministicSuggestionManualPartialSharedAndCancellation()=runBlocking<Unit> {
        val a=bill(8000,id="a");val b=bill(7000,id="b");val c=bill(15000,id="c")
        val shared=nodes.createNode("debts",null,"Compartida",obligation=Obligation(5000,"CLP"),responsibleIds=setOf("yo","madre"))
        val unowned=nodes.createNode("debts",null,"Sin responsable",obligation=Obligation(4000,"CLP"))
        val original=repo.state();val preview=repo.preview(request("DIRECT",20000,"yo"))
        assertEquals(original,repo.state()) // Cancelling a preview writes nothing.
        assertTrue(preview.suggested.isEmpty())
        assertTrue(original.suggest("yo",20000).isNotEmpty()) // Pure legacy helper is never used for new previews.
        assertFalse(preview.suggested.containsKey(shared.id));assertFalse(preview.suggested.containsKey(unowned.id))
        assertTrue(preview.effective.candidates("yo").any {it.id==shared.id})
        assertTrue(runCatching {repo.confirm(preview,mapOf(shared.id to 1000L))}.isFailure)
        assertEquals(original,repo.state())
        assertTrue(runCatching {repo.confirm(preview,mapOf(a.id to 4000L,b.id to 3000L,c.id to 13000L))}.isFailure)
        repo.confirmPayment(repo.preview(request("DIRECT",4000,"yo")),mapOf(a.id to 4000L))
        assertEquals(4000L,repo.state().remaining(db.nodeDao().getById(a.id)!!))
        assertEquals(7000L,repo.state().remaining(db.nodeDao().getById(b.id)!!))
        assertEquals(15000L,repo.state().remaining(db.nodeDao().getById(c.id)!!))
        assertEquals(original.nodes.single {it.id==shared.id},db.nodeDao().getById(shared.id))
    }
    @Test fun insufficientStaleRepeatedPaymentsAndAtomicFailure()=runBlocking<Unit> {
        record(request("INCOME",10000,destination=cash));val bill=bill(20000)
        val initial=repo.state()
        assertTrue(runCatching {repo.preview(request("PAYMENT",15000,"yo",cash))}.isFailure)
        assertTrue(runCatching {repo.preview(request("EXPENSE",15000,source=cash))}.isFailure)
        assertTrue(runCatching {repo.preview(request("TRANSFER",15000,source=cash,destination=box))}.isFailure)
        assertEquals(initial,repo.state())
        val p=repo.preview(request("PAYMENT",5000,"yo",cash));val concurrent=repo.preview(request("PAYMENT",5000,"yo",cash));repo.confirmPayment(p,mapOf(bill.id to 5000L));repo.confirmPayment(p,mapOf(bill.id to 5000L))
        assertTrue(runCatching {repo.confirm(concurrent)}.isFailure)
        assertEquals(5000L,repo.state().balance(cash));assertEquals(5000L,repo.state().paid(bill.id))
        assertTrue(runCatching {repo.confirm(repo.preview(request("DIRECT",5000,"yo")),mapOf(bill.id to 5001L))}.isFailure)
        val stale=repo.preview(request("DIRECT",1000,"yo"));nodes.updateNode(bill.id,"Modificada")
        val before=repo.state();assertTrue(runCatching {repo.confirm(stale)}.isFailure);assertEquals(before,repo.state())
        // Fail after the operation insert: transaction must roll back money, allocation and state.
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_bank_test BEFORE INSERT ON bank_allocations BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        val atomic=repo.preview(request("PAYMENT",5000,"yo",cash));assertTrue(runCatching {repo.confirmPayment(atomic,mapOf(bill.id to 5000L))}.isFailure)
        assertEquals(before,repo.state());db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_bank_test")
    }
    @Test fun annulCorrectionStatesAmountAndHistoryGuards()=runBlocking<Unit> {
        val income=record(request("INCOME",100000,destination=cash));val n=bill(30000)
        val partial=record(request("PAYMENT",20000,"yo",cash));record(request("PAYMENT",10000,"yo",cash))
        assertTrue(db.nodeDao().getById(n.id)!!.isCompleted)
        nodes.setTaskStatus(n.id,TaskStatus.PENDING)
        repo.annul(partial);assertFalse(db.nodeDao().getById(n.id)!!.isCompleted)
        assertEquals(20000L,repo.state().remaining(db.nodeDao().getById(n.id)!!))
        assertTrue(runCatching {repo.annul(income)}.isFailure)
        val old=record(request("EXPENSE",5000,source=cash))
        val correction=repo.preview(request("EXPENSE",7000,source=cash,corrects=old));repo.confirm(correction)
        assertTrue(repo.state().operations.single {it.id==old}.voided)
        assertEquals(83000L,repo.state().balance(cash))
        assertTrue(runCatching {nodes.updateLeaf(n.id,n.title,"",null,null,Obligation(5000,"CLP"))}.isFailure)
        assertTrue(runCatching {nodes.deleteNode(n.id)}.isFailure)
        val clone=nodes.createNode("debts",null,"Copia",obligation=Obligation(30000,"CLP"),responsibleIds=setOf("yo"))
        assertEquals(0L,repo.state().paid(clone.id))
        assertTrue(runCatching {nodes.setTaskStatus(n.id,TaskStatus.COMPLETED)}.isFailure)
        assertTrue(repo.state().candidates("yo").any {it.id==n.id})
        val verify=bill(1000,"madre");db.nodeDao().setTaskStateCount(verify.id,3,1000)
        val full=record(request("DIRECT",1000,"madre"));assertFalse(db.nodeDao().getById(verify.id)!!.awaitingVerification)
        nodes.setTaskStatus(verify.id,TaskStatus.PENDING)
        repo.annul(full);assertFalse(db.nodeDao().getById(verify.id)!!.awaitingVerification)
        val parent=nodes.createNode("debts",null,"Capa con historial",purpose=NodePurpose.LAYER)
        val child=nodes.createNode("debts",parent.id,"Obligación hija",obligation=Obligation(1000,"CLP"),responsibleIds=setOf("padre"))
        record(request("DIRECT",1000,"padre"))
        assertTrue(runCatching {nodes.deleteNode(parent.id)}.isFailure)
        assertNotNull(db.nodeDao().getById(child.id))
        assertTrue(runCatching {ProjectRepository(db.projectDao(),db).deleteProject("bank")}.isFailure)
        assertTrue(runCatching {nodes.deleteNode(cash)}.isFailure)
    }
    @Test fun backupRoundTripValidationHistoricalDefaultsAndOccurrenceIsolation()=runBlocking<Unit> {
        fun occurrenceId(day:Long)=java.util.UUID.nameUUIDFromBytes("recurrence:electricity:$day".toByteArray(Charsets.UTF_8)).toString()
        val first=bill(25000,id=occurrenceId(20000));val second=bill(25000,id=occurrenceId(20031))
        db.recurrenceDao().insert(RecurrenceRuleEntity("electricity","debts",null,"Electricidad","",25000,"CLP",20000,"MONTHLY",1,null,2,"ACTIVE","UTC",0,null))
        db.recurrenceDao().record(RecurrenceOccurrenceEntity("electricity",20000,first.id));db.recurrenceDao().record(RecurrenceOccurrenceEntity("electricity",20031,second.id))
        val preview=repo.preview(request("DIRECT",25000,"yo"));repo.confirm(preview,mapOf(first.id to 25000L))
        assertEquals(25000L,repo.state().remaining(db.nodeDao().getById(second.id)!!))
        val prohibited=RecurrenceRuleEntity("invalid-bank-rule","bank",cash,"No corresponde","",1000,"CLP",20000,"MONTHLY",1,null,0,"ACTIVE","UTC",0,null)
        assertTrue(runCatching {db.recurrenceDao().insert(prohibited)}.isFailure)
        val backup=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))
        val snapshot=backup.snapshot();val encoded=BackupJson.encode(snapshot)
        assertEquals(49,org.json.JSONObject(String(encoded)).getInt("dataVersion"))
        val decoded=BackupJson.decode(encoded);val saved=repo.state()
        backup.restore(decoded);assertEquals(saved,repo.state())
        val bad=decoded.copy(bankOperations=decoded.bankOperations.map {it.copy(amount=it.amount+1)})
        assertTrue(runCatching {backup.restore(bad)}.isFailure);assertEquals(saved,repo.state())
        val legacy=BackupJson.decode(BackupJson.encode(BackupFixture.empty()))
        assertTrue(legacy.bankOperations.isEmpty());assertTrue(legacy.bankRoots.isEmpty())
    }
    @Test fun additiveMigrationPreservesExistingRows()=runBlocking<Unit> {
        db.close();context.deleteDatabase("arachn0de.db")
        val helper=androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name("arachn0de.db")
                .callback(object:androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(52) {
                    override fun onCreate(sql:androidx.sqlite.db.SupportSQLiteDatabase) {
                        val entities=org.json.JSONObject(javaClass.classLoader!!.getResourceAsStream("com.r0ybt.arachn0de.data.local.Arachn0deDatabase/52.json")!!.bufferedReader().use {it.readText()}).getJSONObject("database").getJSONArray("entities")
                        repeat(entities.length()) {i->val e=entities.getJSONObject(i);val table=e.getString("tableName")
                            sql.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",table))
                            val indexes=e.optJSONArray("indices") ?: org.json.JSONArray()
                            repeat(indexes.length()) {j->sql.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))}
                        }
                    }
                    override fun onUpgrade(sql:androidx.sqlite.db.SupportSQLiteDatabase,old:Int,new:Int)=error("unexpected")
                }).build())
        helper.writableDatabase.execSQL("INSERT INTO projects(id,name,description,position,createdAt,updatedAt) VALUES('legacy','Original','Conservar',0,1,2)")
        helper.close();db=Arachn0deDatabase.create(context)
        assertEquals("Conservar",db.backupDao().projects().single().description)
        assertTrue(db.bankDao().roots().isEmpty());assertEquals(55,db.openHelper.writableDatabase.version)
    }
}
