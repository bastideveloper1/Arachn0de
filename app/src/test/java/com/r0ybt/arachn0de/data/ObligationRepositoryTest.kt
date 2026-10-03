package com.r0ybt.arachn0de.data

import android.database.sqlite.SQLiteConstraintException
import com.r0ybt.arachn0de.data.local.*
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
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24,28])
class ObligationRepositoryTest {
    private lateinit var db: Arachn0deDatabase
    private lateinit var nodes: NodeRepository
    private lateinit var people: PersonRepository
    private lateinit var project: String
    private val amount = Obligation(50000,"CLP")
    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("arachn0de.db"); db = Arachn0deDatabase.create(context)
        nodes = NodeRepository(db) { 100L }; people = PersonRepository(db, AvatarStore(context))
        project = ProjectRepository(db.projectDao()).createProject("Project").id
    }
    @After fun cleanup() { db.close(); RuntimeEnvironment.getApplication().deleteDatabase("arachn0de.db") }
    private fun invalid(block: suspend () -> Unit) = runBlocking { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }
    private fun sqlRejected(sql: String) { try { db.openHelper.writableDatabase.execSQL(sql); fail("Expected SQLite rejection") } catch (_: SQLiteConstraintException) {} }

    @Test fun capabilityChangesKeepIdentityDatesCompletionResponsibilitiesAndProjections() = runBlocking {
        val task = nodes.createNode(project,null,"Bill","Details",startAt=1,dueAt=20)
        people.save("p","Roy",null); people.setResponsiblePeople(task.id,setOf("p"))
        nodes.setCompleted(task.id,true)
        val before = nodes.getNode(task.id)!!
        assertTrue(nodes.updateLeaf(task.id,before.title,before.description,1,20,amount))
        assertEquals(before.copy(obligation=amount),nodes.getNode(task.id))
        nodes.setCompleted(task.id,false)
        val tree = nodes.observeAllState().first()
        assertEquals(listOf(task.id),AttentionSnapshot(tree,100).tasks.map { it.id })
        assertEquals(amount,CalendarSnapshot(tree,TimeZone.getTimeZone("UTC")).tasksByDay.values.flatten().single().obligation)
        assertEquals(1,tree.projectProgressById.getValue(project).total)
        assertEquals("p",people.observeAssignments(project).first().getValue(task.id).single().id)
        invalid { nodes.updateLeaf(task.id,"Bill","Details",1,20,null) }
        nodes.updateLeaf(task.id,"Bill","Details",1,20,null,removeObligation=true)
        assertNull(nodes.getNode(task.id)!!.obligation)
        nodes.updateLeaf(task.id,"Bill","Details",1,20,Obligation(1050,"USD"))
        invalid { nodes.convertPurpose(task.id,NodePurpose.NOTE) }
        nodes.convertPurpose(task.id,NodePurpose.NOTE,removeObligation=true)
        assertNull(nodes.getNode(task.id)!!.obligation); assertEquals(20L,nodes.getNode(task.id)!!.dueAt)
        nodes.convertPurpose(task.id,NodePurpose.ACTION); assertNull(nodes.getNode(task.id)!!.obligation)
        nodes.deleteNode(task.id); assertTrue(people.observeAssignments(project).first().isEmpty()); assertNotNull(db.personDao().get("p"))
    }
    @Test fun obligationsBlockChildrenAndLayerAndNoteActivationButMoveAndReorderPreserveMoney() = runBlocking {
        val bill = nodes.createNode(project,null,"Bill",obligation=amount,creationId="bill")
        assertEquals(bill,nodes.createNode(project,null,"Bill",obligation=amount,creationId="bill"))
        try { nodes.createNode(project,null,"Bill",obligation=Obligation(1,"USD"),creationId="bill"); fail() } catch (_: IllegalStateException) {}
        val sibling = nodes.createNode(project,null,"Sibling")
        invalid { nodes.createNode(project,bill.id,"Child") }
        invalid { nodes.moveNode(sibling.id,bill.id) }
        invalid { nodes.createNode(project,null,"Note",purpose=NodePurpose.NOTE,obligation=amount) }
        nodes.reorderNodeTo(bill.id,null,sibling.id); assertEquals(amount,nodes.getNode(bill.id)!!.obligation)
        val parent = nodes.createNode(project,null,"Parent")
        nodes.moveNode(bill.id,parent.id); assertEquals(amount,nodes.getNode(bill.id)!!.obligation)
        assertFalse(nodes.updateLeaf(parent.id,"Parent","",null,null,amount))
        nodes.setCompleted(bill.id,true); nodes.setCompleted(bill.id,false); assertEquals(amount,nodes.getNode(bill.id)!!.obligation)
        nodes.deleteNode(bill.id); assertFalse(nodes.getNode(parent.id)!!.hasChildren)
    }
    @Test fun sqliteGuardsPairTypePositiveAmountPurposeLayerAndParent() = runBlocking {
        val bill = nodes.createNode(project,null,"Bill",obligation=amount,creationId="bill")
        val child = nodes.createNode(project,null,"Other",creationId="other")
        sqlRejected("UPDATE nodes SET parentId='bill' WHERE id='other'")
        try { db.nodeDao().insert(NodeEntity("child",project,bill.id,"Child","",false,0,1,1)); fail() } catch (_: SQLiteConstraintException) {}
        for (change in listOf("amountMinor=NULL","currencyCode=NULL","amountMinor=0","amountMinor=-1","amountMinor=1.5","currencyCode='usd'","purpose='NOTE'")) sqlRejected("UPDATE nodes SET $change WHERE id='bill'")
        nodes.moveNode(child.id,null)
        val parent = nodes.createNode(project,null,"Parent",creationId="parent")
        nodes.moveNode(child.id,parent.id)
        sqlRejected("UPDATE nodes SET amountMinor=1,currencyCode='CLP' WHERE id='parent'")
        nodes.convertPurpose(child.id,NodePurpose.NOTE)
        sqlRejected("UPDATE nodes SET amountMinor=1,currencyCode='CLP' WHERE id='other'")
    }
    @Test fun numberedMonthlyFinancialBatchIsExactIdempotentAndAtomicWithAssignments() = runBlocking {
        people.save("p","Roy",null)
        val utc = TimeZone.getTimeZone("UTC")
        val first = GregorianCalendar(utc).apply { clear();set(2026,Calendar.OCTOBER,15,12,0) }.timeInMillis
        val specs = NodeBatchGenerator.generate(NodeBatchParameters("Cuota",3,NumberingMode.SUFFIX,purpose=NodePurpose.ACTION,temporalRule=BatchTemporalRule.MONTHLY,firstDueAt=first,obligation=amount),utc)
        assertEquals(listOf("Cuota 1","Cuota 2","Cuota 3"),specs.map { it.title })
        invalid { nodes.createBatch(project,null,"mixed",listOf(specs[0],specs[1].copy(obligation=null))) }
        invalid { nodes.createBatch(project,null,"different",listOf(specs[0],specs[1].copy(obligation=Obligation(1,"USD")))) }
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER reject_second BEFORE INSERT ON nodes WHEN NEW.title='Cuota 2' BEGIN SELECT RAISE(ABORT,'test'); END")
        try { nodes.createBatch(project,null,"batch",specs,setOf("p")); fail() } catch (_: SQLiteConstraintException) {}
        assertTrue(nodes.getProjectNodes(project).isEmpty()); assertTrue(people.observeAssignments(project).first().isEmpty())
        sql.execSQL("DROP TRIGGER reject_second")
        val batch = nodes.createBatch(project,null,"batch",specs,setOf("p"))
        assertEquals(batch,nodes.createBatch(project,null,"batch",specs,setOf("p")))
        assertTrue(batch.all { it.obligation==amount })
        assertEquals(listOf(10,11,12),batch.map { GregorianCalendar(utc).apply { timeInMillis=it.dueAt!! }.get(Calendar.MONTH)+1 })
        assertEquals(3,people.observeAssignments(project).first().size)
        try { nodes.createBatch(project,null,"batch",specs.map { it.copy(obligation=Obligation(1,"USD")) },setOf("p")); fail() } catch (_: IllegalStateException) {}
        invalid { NodeBatchGenerator.generate(NodeBatchParameters("Note",2,purpose=NodePurpose.NOTE,obligation=amount),utc) }
        assertEquals(10, sql.version)
    }
}
