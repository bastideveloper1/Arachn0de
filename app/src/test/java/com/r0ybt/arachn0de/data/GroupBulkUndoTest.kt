package com.r0ybt.arachn0de.data

import androidx.room.Room
import com.r0ybt.arachn0de.data.local.*
import com.r0ybt.arachn0de.data.repository.*
import com.r0ybt.arachn0de.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[24,28])
class GroupBulkUndoTest {
    private lateinit var db:Arachn0deDatabase
    private lateinit var repo:NodeRepository
    @Before fun setup() { db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),Arachn0deDatabase::class.java).allowMainThreadQueries().build();repo=NodeRepository(db) { 1000 };runBlocking {
        db.projectDao().insert(ProjectEntity("p","Project","",0,1,1));db.personDao().insert(PersonEntity("person","Person",null))
    } }
    @After fun close() { db.close() }
    private suspend fun batch(id:String="g",count:Int=12)=repo.createBatch("p",null,id,NodeBatchGenerator.generate(NodeBatchParameters("Cuota",count,NumberingMode.SUFFIX,1,description="Base",temporalRule=BatchTemporalRule.MONTHLY,firstDueAt=1769853600000,obligation=Obligation(10000,"CLP"),priority=Priority.LOW),java.util.TimeZone.getTimeZone("UTC")),setOf("person"))
    @Test fun groupIdentitySurvivesMovesEditsAndPartialDeletionAndIndividualHasNone()=runBlocking {
        val group=batch();assertTrue(group.all { it.creationGroupId=="g" });assertTrue(batch("h",2).all { it.creationGroupId=="h" })
        val parent=repo.createNode("p",null,"Parent");assertNull(parent.creationGroupId)
        assertTrue(repo.moveNode(group[0].id,parent.id));assertTrue(repo.updateNode(group[0].id,"Changed"));assertTrue(repo.deleteNode(group[1].id))
        assertEquals(11,repo.groupMembers("g").size);assertEquals("g",repo.getNode(group[0].id)!!.creationGroupId)
    }
    @Test fun undoNormalAndBatchRemoveOnlyExactNewNodesRelationsAndEvents()=runBlocking {
        repeat(3) { repo.createNode("p",null,"Old $it") }
        val tag=repo.tags.create("tag")
        val normal=repo.createNodeWithUndo("p",null,"New","", "normal",null,null,NodePurpose.ACTION,Obligation(25000,"CLP"),setOf("person"),setOf(tag.id),Priority.HIGH)
        assertEquals(4,db.nodeEventDao().all().size);assertTrue(repo.undoCreation(normal));assertEquals(3,repo.getProjectNodes("p").size)
        val nodes=batch();val undo=repo.captureCreation(nodes.map { it.id });assertTrue(repo.undoCreation(undo));assertEquals(3,repo.getProjectNodes("p").size)
        assertEquals(3,db.nodeEventDao().all().size);assertTrue(db.personDao().assignmentIds("normal").isEmpty());assertTrue(db.tagDao().nodeIds("normal").isEmpty())
    }
    @Test fun undoRefusesMutationsAndNewChildrenAndRollsBackOnDeleteFailure()=runBlocking {
        val nodes=batch(count=3);val undo=repo.captureCreation(nodes.map { it.id })
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER undo_fail BEFORE DELETE ON nodes WHEN OLD.id='${nodes.last().id}' BEGIN SELECT RAISE(ABORT,'fail'); END")
        assertTrue(runCatching { repo.undoCreation(undo) }.isFailure);assertEquals(3,repo.getProjectNodes("p").size)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER undo_fail")
        repo.tags.assignNode(nodes.first().id,setOf(repo.tags.create("new tag").id));assertFalse(repo.undoCreation(undo));assertEquals(3,repo.getProjectNodes("p").size)
        val leaf=repo.createNode("p",null,"Leaf");val token=repo.captureCreation(listOf(leaf.id));repo.createNode("p",leaf.id,"Child");assertFalse(repo.undoCreation(token))
    }
    @Test fun deleteNormalizesParentChildAndPreservesExternalSiblingsAndRollsBack()=runBlocking {
        val parent=repo.createNode("p",null,"Parent");val child=repo.createNode("p",parent.id,"Child");val note=repo.createNode("p",null,"Note",purpose=NodePurpose.NOTE)
        val sibling=repo.createNode("p",null,"Sibling")
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER delete_fail BEFORE DELETE ON nodes WHEN OLD.id='${note.id}' BEGIN SELECT RAISE(ABORT,'fail'); END")
        assertTrue(runCatching { repo.deleteSelected("p",setOf(parent.id,child.id,note.id)) }.isFailure);assertEquals(4,repo.getProjectNodes("p").size);assertEquals(parent.id,repo.getNode(child.id)!!.parentId)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER delete_fail")
        repo.deleteSelected("p",setOf(parent.id,child.id,note.id));assertEquals(listOf(sibling.id),repo.getProjectNodes("p").map { it.id });assertEquals(1,db.nodeEventDao().all().size)
    }
    @Test fun movesNormalizeRootsPreserveRelativeOrderAndGroupAndRejectCyclesAtomically()=runBlocking {
        val group=batch(count=3);val destination=repo.createNode("p",null,"Destination")
        repo.moveSelected("p",setOf(group[2].id,group[0].id),destination.id)
        assertEquals(listOf(group[0].id,group[2].id),repo.observeProjectState("p").first().childrenOf(destination.id).map { it.id })
        assertEquals("g",repo.getNode(group[0].id)!!.creationGroupId)
        assertTrue(runCatching { repo.moveSelected("p",setOf(destination.id,group[1].id),group[0].id) }.isFailure);assertNull(repo.getNode(group[1].id)!!.parentId)
        val note=repo.createNode("p",null,"Note",purpose=NodePurpose.NOTE);assertTrue(runCatching { repo.moveSelected("p",setOf(group[1].id),note.id) }.isFailure)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER move_fail BEFORE UPDATE OF parentId ON nodes WHEN OLD.id='${group[2].id}' BEGIN SELECT RAISE(ABORT,'fail'); END")
        assertTrue(runCatching { repo.moveSelected("p",setOf(group[0].id,group[2].id),null) }.isFailure);assertEquals(destination.id,repo.getNode(group[0].id)!!.parentId)
    }
    @Test fun amountOnlyPatchPreservesEveryIndividualVariationIncludingCurrency()=runBlocking {
        val nodes=batch(count=3);repo.setCompleted(nodes[0].id,true)
        repo.updateEditor(nodes[0].id,nodes[0].title,"Different",null,nodes[0].dueAt,Obligation(10000,"USD"),false,true,emptySet(),Priority.HIGH)
        val before=repo.getProjectNodes("p");val source=repo.getNode(nodes[1].id)!!;val events=db.nodeEventDao().all()
        repo.updateGroup(source.id,SharedNodePatch(amount=FieldChange(12000L)),nodes.map { it.id }.toSet(),source.title,source.description,source.startAt,source.dueAt,true,Obligation(12000,"CLP"),false,emptySet(),source.priority,setOf("person"))
        before.forEach { old -> val changed=repo.getNode(old.id)!!;assertEquals(old.copy(obligation=Obligation(12000,old.obligation!!.currencyCode),updatedAt=changed.updatedAt),changed) };assertEquals(events,db.nodeEventDao().all())
    }
    @Test fun multiplePatchesReachMovedExistingMembersAndRollbackRelationships()=runBlocking {
        val nodes=batch();repo.deleteNode(nodes.last().id);val parent=repo.createNode("p",null,"Parent");repo.moveNode(nodes[0].id,parent.id)
        val before=repo.getProjectNodes("p");val tag=repo.tags.create("tag");val source=repo.getNode(nodes[1].id)!!;val ids=repo.groupMembers("g").map { it.id }.toSet()
        val patch=SharedNodePatch(description="New",amount=FieldChange(12000L),priority=Priority.MEDIUM,tags=setOf(tag.id),responsibleIds=emptySet())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER patch_fail BEFORE INSERT ON node_tag WHEN NEW.nodeId='${nodes[2].id}' BEGIN SELECT RAISE(ABORT,'fail'); END")
        suspend fun apply()=repo.updateGroup(source.id,patch,ids,source.title,"New",source.startAt,source.dueAt,true,Obligation(12000,"CLP"),false,setOf(tag.id),Priority.MEDIUM,emptySet())
        assertTrue(runCatching { apply() }.isFailure);assertEquals(before,repo.getProjectNodes("p"));assertTrue(db.tagDao().nodeTags().isEmpty());assertEquals(setOf("person"),db.personDao().assignmentIds(source.id).toSet())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER patch_fail");assertEquals(11,apply())
        repo.groupMembers("g").forEach { assertEquals("New",it.description);assertEquals(Priority.MEDIUM,it.priority);assertEquals(12000L,it.obligation!!.amountMinor);assertEquals(setOf(tag.id),db.tagDao().nodeIds(it.id).toSet());assertTrue(db.personDao().assignmentIds(it.id).isEmpty()) }
        assertEquals(parent.id,repo.getNode(nodes[0].id)!!.parentId);assertNull(repo.getNode(nodes.last().id))
    }
    @Test fun twelveMembersReceiveAllEligibleChangesWhileTitleAndDatesStayIndividual()=runBlocking {
        val nodes=batch();val tag=repo.tags.create("Shared")
        db.personDao().insert(PersonEntity("new-person","New",null))
        val source=nodes[3];val events=db.nodeEventDao().all()
        val patch=SharedNodePatch(description="Shared",amount=FieldChange(12000L),currency=FieldChange("USD"),priority=Priority.MEDIUM,tags=setOf(tag.id),responsibleIds=setOf("new-person"))
        assertEquals(12,repo.updateGroup(source.id,patch,nodes.map { it.id }.toSet(),"Individual title","Shared",null,source.dueAt!!+1000,true,Obligation(12000,"USD"),false,setOf(tag.id),Priority.MEDIUM,setOf("new-person")))
        nodes.forEach { before -> val after=repo.getNode(before.id)!!
            assertEquals("Shared",after.description);assertEquals(Obligation(12000,"USD"),after.obligation);assertEquals(Priority.MEDIUM,after.priority)
            assertEquals(setOf(tag.id),db.tagDao().nodeIds(after.id).toSet());assertEquals(setOf("new-person"),db.personDao().assignmentIds(after.id).toSet())
            assertEquals(if(before.id==source.id) "Individual title" else before.title,after.title)
            assertEquals(if(before.id==source.id) before.dueAt!!+1000 else before.dueAt,after.dueAt)
            assertEquals(before.position,after.position);assertEquals(before.isCompleted,after.isCompleted)
        }
        assertEquals(events,db.nodeEventDao().all())
    }
    @Test fun incompatibleMembersAndStaleMembershipRejectWholePatch()=runBlocking {
        val nodes=batch(count=3);val source=nodes[0];val ids=nodes.map { it.id }.toSet()
        repo.convertPurpose(nodes[1].id,NodePurpose.NOTE,removeObligation=true)
        assertTrue(runCatching { repo.updateGroup(source.id,SharedNodePatch(priority=Priority.HIGH),ids,source.title,source.description,null,source.dueAt,true,source.obligation,false,emptySet(),Priority.HIGH,setOf("person")) }.isFailure)
        assertEquals(Priority.LOW,repo.getNode(source.id)!!.priority)
        repo.deleteNode(nodes[2].id)
        assertTrue(runCatching { repo.updateGroup(source.id,SharedNodePatch(description="New"),ids,source.title,"New",null,source.dueAt,true,source.obligation,false,emptySet(),Priority.LOW,setOf("person")) }.isFailure)
    }
}
