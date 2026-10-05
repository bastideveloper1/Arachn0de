package com.r0ybt.arachn0de.data

import com.r0ybt.arachn0de.data.local.Arachn0deDatabase
import com.r0ybt.arachn0de.data.local.AvatarStore
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigInteger
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[24,28])
class FinancialRepositoryTest {
    private lateinit var db: Arachn0deDatabase
    private lateinit var nodes: NodeRepository
    private lateinit var people: PersonRepository
    private lateinit var projects: ProjectRepository
    private lateinit var p: String
    private val zone=TimeZone.getTimeZone("UTC")
    private val due=GregorianCalendar(zone).apply { clear();set(2026,Calendar.OCTOBER,15,12,0) }.timeInMillis
    @Before fun setup() = runBlocking {
        val context=RuntimeEnvironment.getApplication(); context.deleteDatabase("arachn0de.db")
        db=Arachn0deDatabase.create(context); nodes=NodeRepository(db){100}; people=PersonRepository(db,AvatarStore(context)); projects=ProjectRepository(db.projectDao()){100}
        p=projects.createProject("Project").id
    }
    @After fun cleanup() { db.close();RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db") }
    private suspend fun financial(period: FinancialPeriod=FinancialPeriod.ALL,person: String?=null)=FinancialSnapshot(
        nodes.observeAllState().first(),people.observeAllAssignments().first(),FinancialSelection(period,person),CalendarMonth(2026,10),zone)

    @Test fun individualCreationCommitsResponsibilitiesAtomicallyAndRetriesSafely() = runBlocking {
        people.save("r", "Roy", null); people.save("s", "Scarlett", null)
        val bill = nodes.createNode(p, null, "Bill", creationId = "bill", obligation = Obligation(50000, "CLP"), responsibleIds = setOf("r", "s"))
        assertEquals(setOf("r", "s"), db.personDao().assignmentIds(bill.id).toSet())
        assertEquals(bill, nodes.createNode(p, null, "Bill", creationId = "bill", obligation = bill.obligation, responsibleIds = setOf("r", "s")))
        assertTrue(runCatching { nodes.createNode(p, null, "Bill", creationId = "bill", obligation = bill.obligation) }.isFailure)
        assertTrue(runCatching { nodes.createNode(p, null, "Invalid", creationId = "invalid", obligation = bill.obligation, responsibleIds = setOf("missing")) }.isFailure)
        assertNull(nodes.getNode("invalid"))
        assertEquals(1, nodes.getProjectNodes(p).size)
    }

    @Test fun existingFlowsUpdateMoneyCompletionResponsibilityDatesStructureAndDeletesWithoutProjectionWrites() = runBlocking {
        val root=nodes.createNode(p,null,"Root", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val inner=nodes.createNode(p,root.id,"Inner", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val bill=nodes.createNode(p,inner.id,"Bill",dueAt=due,obligation=Obligation(50000,"CLP"))
        people.save("r","Roy",null); people.save("s","Scarlett",null); people.setResponsiblePeople(bill.id,setOf("r","s"))
        assertEquals(1,financial().byNodeId.getValue(root.id).count)
        assertEquals(1,financial(person="r").summary.count); assertEquals(1,financial(person="s").summary.count)
        nodes.setCompleted(bill.id,true); assertEquals(FinancialState.COMPLETED,financial().summary.state)
        nodes.setCompleted(bill.id,false); assertEquals(FinancialState.PENDING,financial().summary.state)
        nodes.updateLeaf(bill.id,"Bill","",null,due,Obligation(1050,"USD"))
        assertEquals(setOf("USD"),financial().summary.byCurrency.keys)
        nodes.updateNodeWithDates(bill.id,"Bill","",null,null)
        assertEquals(0,financial(FinancialPeriod.THIS_MONTH).summary.count); assertEquals(1,financial().summary.count)
        nodes.moveNode(bill.id,null)
        assertEquals(0,financial().byNodeId.getValue(root.id).count)
        assertEquals(listOf(bill.id),financial().pathTo(bill.id).map { it.id })
        people.save("r","Renamed",null,isNew=false)
        assertTrue(financial().responsibleByNode.getValue(bill.id).any { it.name=="Renamed" })
        people.setResponsiblePeople(bill.id,setOf("s")); assertEquals(0,financial(person="r").summary.count)
        val before=nodes.getProjectNodes(p)
        financial(); financial(FinancialPeriod.THIS_MONTH,"s")
        assertEquals(before,nodes.getProjectNodes(p))
        nodes.convertPurpose(bill.id,NodePurpose.NOTE,removeObligation=true)
        assertEquals(0,financial().summary.count)
        nodes.convertPurpose(bill.id,NodePurpose.ACTION)
        nodes.updateLeaf(bill.id,"Bill","",null,due,Obligation(2000,"EUR"))
        nodes.deleteNode(bill.id); assertEquals(0,financial().summary.count)
        assertEquals(18, db.openHelper.readableDatabase.version)
    }
    @Test fun batchesUseSameScopeAggregationAndProjectDeletionDoesNotAffectOtherCurrencies() = runBlocking {
        val root=nodes.createNode(p,null,"Root", purpose = com.r0ybt.arachn0de.domain.model.NodePurpose.LAYER)
        val specs=NodeBatchGenerator.generate(NodeBatchParameters("Cuota",3,NumberingMode.SUFFIX,temporalRule=BatchTemporalRule.MONTHLY,
            firstDueAt=due,obligation=Obligation(50000,"CLP")),zone)
        val batch=nodes.createBatch(p,root.id,"batch",specs)
        nodes.setCompleted(batch[1].id,true)
        val all=financial()
        assertEquals(BigInteger.valueOf(150000),all.byProjectId.getValue(p).byCurrency.getValue("CLP").totalMinor)
        assertEquals(BigInteger.valueOf(100000),all.byNodeId.getValue(root.id).byCurrency.getValue("CLP").pendingMinor)
        assertEquals(1,financial(FinancialPeriod.THIS_MONTH).summary.count)
        assertEquals(1,financial(FinancialPeriod.NEXT_MONTH).summary.count)
        val q=projects.createProject("Other").id
        nodes.createNode(q,null,"Other",obligation=Obligation(1050,"USD"))
        assertEquals(setOf("CLP","USD"),financial().summary.byCurrency.keys)
        projects.deleteProject(p); assertEquals(setOf("USD"),financial().summary.byCurrency.keys)
    }
}
