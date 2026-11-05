package com.r0ybt.arachn0de.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.r0ybt.arachn0de.backup.*
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.defaults.*
import com.r0ybt.arachn0de.domain.model.*
import com.r0ybt.arachn0de.templates.*
import com.r0ybt.arachn0de.ui.state.CreationDefaultsDraftFactory
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClpPaymentPolicyTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db:Arachn0deDatabase
    private lateinit var nodes:NodeRepository
    @Before fun setup()=runBlocking<Unit> {
        context.deleteDatabase("arachn0de.db");db=Arachn0deDatabase.create(context);nodes=NodeRepository(db)
        ProjectRepository(db.projectDao(),db).createProject("Núcleo",creationId="p")
    }
    @After fun close() {db.close()}
    private fun legacy(id:String="old")=NodeEntity(id,"p",null,"Histórica","Texto",false,0,1,1,amountMinor=1050,currencyCode="USD")
    private fun backup()=BackupRepository(db,context,BackupAvatarFiles(context,BackupFixture::syncDirectory))

    @Test fun newPaymentsAndBatchesAreClpOnlyAndRejectionsAreAtomic()=runBlocking<Unit> {
        assertTrue(runCatching {nodes.createNode("p",null,"USD",obligation=Obligation(100,"USD"))}.isFailure)
        val task=nodes.createNode("p",null,"CLP",obligation=Obligation(15000,"CLP"))
        assertTrue(nodes.updateLeaf(task.id,task.title,"",null,null,Obligation(16000,"CLP")))
        val before=db.backupDao().nodes()
        assertTrue(runCatching {nodes.updateLeaf(task.id,task.title,"",null,null,Obligation(16000,"USD"))}.isFailure)
        val specs=NodeBatchGenerator.generate(NodeBatchParameters("Cuota",2,NumberingMode.SUFFIX,obligation=Obligation(100,"USD")),TimeZone.getTimeZone("UTC"))
        assertTrue(runCatching {nodes.createBatch("p",null,"bad",specs)}.isFailure)
        assertEquals(before,db.backupDao().nodes())
        assertEquals(2,nodes.createBatch("p",null,"good",specs.map {it.copy(obligation=Obligation(100,"CLP"))}).size)
    }

    @Test fun historicalMoneyIsReadOnlyButOtherPropertiesAndBackupRemainUsable()=runBlocking<Unit> {
        db.nodeDao().insert(legacy())
        val old=db.nodeDao().getById("old")!!
        for(next in listOf(null,Obligation(2000,"USD"),Obligation(1050,"CLP"))) {
            assertTrue(runCatching {nodes.updateLeaf(old.id,old.title,old.description,null,null,next,true)}.isFailure)
            assertEquals(old,db.nodeDao().getById(old.id))
        }
        assertTrue(runCatching {nodes.convertPurpose(old.id,NodePurpose.NOTE,true)}.isFailure)
        assertTrue(nodes.updateLeaf(old.id,"Renombrada","Otro texto",null,null,Obligation(1050,"USD")))
        val snapshot=backup().snapshot()
        val decoded=BackupJson.decode(BackupJson.encode(snapshot))
        backup().restore(decoded)
        assertEquals(snapshot.nodes,db.backupDao().nodes())
        assertEquals(1050L,nodes.getNode(old.id)!!.obligation!!.amountMinor)
        assertEquals("USD",nodes.getNode(old.id)!!.obligation!!.currencyCode)
        assertTrue(db.bankDao().operations().isEmpty());assertTrue(db.bankDao().allocations().isEmpty())
    }

    @Test fun historicalRecurrenceDoesNotEmitOrAdvanceAndDoesNotBlockClp()=runBlocking<Unit> {
        val day=RecurrenceSchedule.parse("2020-01-01")
        val old=RecurrenceRuleEntity("old-rule","p",null,"Histórica","",1050,"USD",day,"MONTHLY",1,day,0,"ACTIVE","UTC",0,null)
        val current=old.copy(id="clp-rule",currencyCode="CLP",amountMinor=15000)
        assertTrue(runCatching {nodes.recurrence.create(old)}.isFailure)
        db.recurrenceDao().insert(old)
        nodes.recurrence.create(current)
        assertFalse(nodes.recurrence.materializeDue())
        assertEquals(old,db.recurrenceDao().get(old.id))
        assertEquals(listOf(current.id),db.recurrenceDao().occurrences().map {it.ruleId})
        assertTrue(runCatching {nodes.recurrence.editTemplate(old.id,"p",null,old.title,"",Obligation(2000,"USD"),emptySet())}.isFailure)
        val snapshot=backup().snapshot()
        backup().restore(BackupJson.decode(BackupJson.encode(snapshot)))
        assertEquals(old,db.recurrenceDao().get(old.id))
        assertEquals(snapshot.recurrenceOccurrences,db.recurrenceDao().occurrences())
    }

    @Test fun historicalDefaultsArePreservedButNewDraftsUseClp()=runBlocking<Unit> {
        val template=SavedTemplateEntity("legacy-template","Histórica",SavedTemplateCodec.encode(SavedTaskConfiguration("Histórica",amount=1050,currency="USD")))
        db.savedTemplateDao().insert(template)
        val configuration=CreationDefaults(currency=DefaultValue.Own("EUR"),obligation=DefaultValue.Own(true))
        nodes.creationDefaults.save(DefaultsScope.Project("p"),configuration)
        val effective=nodes.creationDefaults.resolve("p",null)
        assertEquals("EUR",effective.currency)
        val draft=CreationDefaultsDraftFactory.create(null,effective,0,TimeZone.getTimeZone("UTC"))
        assertTrue(draft.financialEnabled);assertEquals("CLP",draft.currencyCode);assertEquals("",draft.amountText)
        val snapshot=backup().snapshot()
        backup().restore(BackupJson.decode(BackupJson.encode(snapshot)))
        assertEquals("EUR",nodes.creationDefaults.resolve("p",null).currency)
        assertEquals(template,db.savedTemplateDao().get(template.id))
    }
}
